/*******************************************************************************
 * Copyright (c) 2021, 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.JUNIT3_CLEANUP;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.JUNIT_CLEANUP;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.JUnitCleanUpFix_refactor;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.JUnitCleanUp_description;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.sandbox.jdt.cleanup.multifile.AbstractPlannedMultiFileCleanUp;
import org.sandbox.jdt.cleanup.multifile.JavaProjectCompilationUnits;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.cleanup.multifile.RelatedCompilationUnitSearch;
import org.sandbox.jdt.cleanup.multifile.SourceRootPolicy;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.JUnitMigrationOptions;
import org.sandbox.jdt.internal.corext.fix.helper.RuleImportCleanupSupport;
import org.sandbox.jdt.internal.corext.fix.helper.lib.InheritedLifecycleMethodRefactorer;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyScopeDetector;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitLifecycleScopeDetector;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitBestEffortSupport;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitBestEffortSupport.Analysis;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitMigrationPlan;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitMultiFilePlanner;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitScopeCandidateDetector;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;

/** Core cleanup implementation for JUnit 3/4 to Jupiter migration. */
public class JUnitCleanUpCore extends AbstractPlannedMultiFileCleanUp<JUnitMigrationPlan> {

	private final Map<IJavaProject, Set<String>> pendingExpandedScopes= new HashMap<>();
	private final Map<IJavaProject, Set<String>> verifiedClosedScopes= new HashMap<>();
	private final Set<IJavaProject> rejectedScopes= new HashSet<>();
	private final Map<IJavaProject, Analysis> migrationAnalyses= new HashMap<>();

	public JUnitCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public JUnitCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(JUNIT_CLEANUP) || isEnabled(JUNIT3_CLEANUP);
	}

	@Override
	protected MultiFileCleanUpPlanResult<JUnitMigrationPlan> createPlan(IJavaProject project,
			ICompilationUnit[] compilationUnits, IProgressMonitor monitor) throws CoreException {
		EnumSet<JUnitCleanUpFixCore> fixes= computeFixSet();
		if (!(isEnabled(JUNIT_CLEANUP) || isEnabled(JUNIT3_CLEANUP)) || fixes.isEmpty()) {
			migrationAnalyses.remove(project);
			return MultiFileCleanUpPlanResult.noPlan();
		}
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}

		boolean junit4Enabled= isEnabled(JUNIT_CLEANUP);
		boolean bestEffort= junit4Enabled && isEnabled(JUnitMigrationOptions.BEST_EFFORT);
		Boolean closedScope= consumeClosedScopeDecision(project, compilationUnits);
		boolean migrateExternalResources= fixes.contains(JUnitCleanUpFixCore.RULEEXTERNALRESOURCE);
		JUnitMultiFilePlanner.PlanningOptions planningOptions=
				new JUnitMultiFilePlanner.PlanningOptions(
						migrateExternalResources,
						fixes.contains(JUnitCleanUpFixCore.TEST3),
						fixes.contains(JUnitCleanUpFixCore.PARAMETERIZED));

		// Parse and classify the complete project scope before the strict per-file gap
		// analysis. On a newly imported Eclipse project this first coordinated parse
		// materializes the Java model and bindings. Running the gap analysis before it
		// made check and apply race against classpath initialization and could therefore
		// disagree about whether an unsupported runner or rule must keep a file atomic.
		MultiFileCleanUpPlanResult<JUnitMigrationPlan> result= closedScope == null
				? JUnitMultiFilePlanner.createCoordinated(project, compilationUnits, planningOptions, monitor)
				: JUnitMultiFilePlanner.createCoordinated(project, compilationUnits, planningOptions,
						closedScope.booleanValue(), monitor);

		Analysis analysis= junit4Enabled
				? JUnitBestEffortSupport.analyze(project, compilationUnits, fixes, monitor)
				: Analysis.empty();
		if (bestEffort && migrateExternalResources && analysis.disableCoordinatedExternalResource()) {
			planningOptions= new JUnitMultiFilePlanner.PlanningOptions(
					false,
					fixes.contains(JUnitCleanUpFixCore.TEST3),
					fixes.contains(JUnitCleanUpFixCore.PARAMETERIZED));
			result= closedScope == null
					? JUnitMultiFilePlanner.createCoordinated(project, compilationUnits, planningOptions, monitor)
					: JUnitMultiFilePlanner.createCoordinated(project, compilationUnits, planningOptions,
							closedScope.booleanValue(), monitor);
		}
		if (!bestEffort && result.plan() != null && !analysis.gaps().isEmpty()) {
			Set<String> blockedRuleUnits= analysis.gaps().stream()
					.map(JUnitBestEffortSupport.Gap::ownerCompilationUnitHandle)
					.collect(Collectors.toCollection(LinkedHashSet::new));
			JUnitMigrationPlan compatiblePlan=
					result.plan().withJUnit4CompatibilityForBlockedRuleUnits(blockedRuleUnits);
			if (compatiblePlan != result.plan()) {
				result= new MultiFileCleanUpPlanResult<>(compatiblePlan, result.status(),
						result.metrics(), result.diagnostics());
			}
		}
		migrationAnalyses.put(project, analysis);
		return result;
	}

	@Override
	protected ICleanUpFix createFixForPlan(JUnitMigrationPlan plan, CleanUpContext context) throws CoreException {
		if (!plan.contains(context.getCompilationUnit())) {
			return null;
		}
		CompilationUnit compilationUnit= context.getAST();
		if (compilationUnit == null) {
			return null;
		}
		EnumSet<JUnitCleanUpFixCore> computeFixSet= computeFixSet();
		if (!(isEnabled(JUNIT_CLEANUP) || isEnabled(JUNIT3_CLEANUP)) || computeFixSet.isEmpty()) {
			return null;
		}

		boolean bestEffort= isEnabled(JUnitMigrationOptions.BEST_EFFORT) && isEnabled(JUNIT_CLEANUP);
		Analysis analysis= migrationAnalyses.getOrDefault(
				context.getCompilationUnit().getJavaProject(), Analysis.empty());
		List<JUnitBestEffortSupport.Gap> localGaps= analysis.gapsFor(context.getCompilationUnit());
		if (!bestEffort && !localGaps.isEmpty()) {
			// Strict mode remains atomic for migration edits. The only permitted
			// compatibility change makes inherited JUnit 4 virtual lifecycle dispatch
			// explicit when this compilation unit must stay on JUnit 4.
			Set<CompilationUnitRewriteOperationWithSourceRange> compatibilityOperations=
					new LinkedHashSet<>();
			Set<ASTNode> compatibilityNodes= new HashSet<>();
			if (computeFixSet.contains(JUnitCleanUpFixCore.BEFORE)) {
				InheritedLifecycleMethodRefactorer.addInheritedLifecycleOverrides(compilationUnit,
						compatibilityOperations, compatibilityNodes,
						Set.of(JUnitConstants.ORG_JUNIT_BEFORE,
								JUnitConstants.ORG_JUNIT_JUPITER_API_BEFORE_EACH),
						JUnitConstants.ORG_JUNIT_BEFORE);
			}
			if (computeFixSet.contains(JUnitCleanUpFixCore.AFTER)) {
				InheritedLifecycleMethodRefactorer.addInheritedLifecycleOverrides(compilationUnit,
						compatibilityOperations, compatibilityNodes,
						Set.of(JUnitConstants.ORG_JUNIT_AFTER,
								JUnitConstants.ORG_JUNIT_JUPITER_API_AFTER_EACH),
						JUnitConstants.ORG_JUNIT_AFTER);
			}
			if (compatibilityOperations.isEmpty()) {
				return null;
			}
			return new CompilationUnitRewriteOperationsFixCore(JUnitCleanUpFix_refactor, compilationUnit,
					compatibilityOperations.toArray(
							new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[0]));
		}

		boolean preserveExecutionContract= bestEffort && !localGaps.isEmpty();
		EnumSet<JUnitCleanUpFixCore> effectiveFixSet= preserveExecutionContract
				? JUnitBestEffortSupport.independentlySafeFixes(computeFixSet)
				: computeFixSet;
		Set<CompilationUnitRewriteOperationWithSourceRange> operations= new LinkedHashSet<>();
		Set<ASTNode> sharedNodesProcessed= new HashSet<>();
		if (!preserveExecutionContract) {
			plan.addOperationsFor(context.getCompilationUnit(), compilationUnit, operations, sharedNodesProcessed);
		}
		effectiveFixSet.forEach(i -> i.findOperations(compilationUnit, operations, sharedNodesProcessed));
		RuleImportCleanupSupport.addIfSafe(compilationUnit, effectiveFixSet, operations);
		if (bestEffort) {
			JUnitBestEffortSupport.addMarkerOperation(compilationUnit, localGaps, operations);
		}
		if (operations.isEmpty()) {
			return null;
		}
		return new CompilationUnitRewriteOperationsFixCore(JUnitCleanUpFix_refactor, compilationUnit,
				operations.toArray(new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[0]));
	}

	/** Returns difficult-construct analysis captured during the latest project plan. */
	protected Analysis getMigrationAnalysis(IJavaProject project) {
		return migrationAnalyses.getOrDefault(project, Analysis.empty());
	}

	@Override
	protected Collection<ICompilationUnit> discoverAdditionalCompilationUnits(IJavaProject project,
			Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) throws CoreException {
		EnumSet<JUnitCleanUpFixCore> fixes= computeFixSet();
		boolean migrateExternalResourceRules= fixes.contains(JUnitCleanUpFixCore.RULEEXTERNALRESOURCE);
		boolean followSuiteMembership= isEnabled(JUNIT_CLEANUP)
				&& fixes.stream().anyMatch(fix -> fix != JUnitCleanUpFixCore.JUNIT6_COMPATIBILITY);
		boolean migrateJUnit3Hierarchies= fixes.contains(JUnitCleanUpFixCore.TEST3);
		Set<String> lifecycleAnnotations= lifecycleAnnotations(fixes);
		boolean migrateLifecycleHierarchies= !lifecycleAnnotations.isEmpty();
		if (!migrateExternalResourceRules && !followSuiteMembership && !migrateJUnit3Hierarchies
				&& !migrateLifecycleHierarchies) {
			return List.of();
		}
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}

		Set<String> currentHandles= handles(currentScope);
		Set<String> pendingScope= pendingExpandedScopes.remove(project);
		if (pendingScope != null && currentHandles.containsAll(pendingScope)) {
			verifiedClosedScopes.put(project, pendingScope);
			return List.of();
		}
		rejectedScopes.remove(project);
		JUnitScopeCandidateDetector.SearchSeeds standardSeeds= JUnitScopeCandidateDetector.findSearchSeeds(project,
				currentScope, migrateExternalResourceRules, followSuiteMembership, monitor);
		JUnitScopeCandidateDetector.SearchSeeds junit3Seeds= migrateJUnit3Hierarchies
				? JUnit3HierarchyScopeDetector.findSearchSeeds(project, currentScope, monitor)
				: new JUnitScopeCandidateDetector.SearchSeeds(false, true, List.of(), List.of());
		JUnitScopeCandidateDetector.SearchSeeds lifecycleSeeds= migrateLifecycleHierarchies
				? JUnitLifecycleScopeDetector.findSearchSeeds(project, currentScope,
						lifecycleAnnotations, monitor)
				: new JUnitScopeCandidateDetector.SearchSeeds(false, true, List.of(), List.of());
		JUnitScopeCandidateDetector.SearchSeeds seeds=
				mergeSeeds(mergeSeeds(standardSeeds, junit3Seeds), lifecycleSeeds);
		if (!seeds.candidateFound()) {
			clearScopeDecision(project);
			return List.of();
		}

		List<IJavaProject> coordinatedProjects= coordinatedProjects(project, seeds);
		if (!JavaProjectCompilationUnits.readOnlyProjects(coordinatedProjects).isEmpty()) {
			clearScopeDecision(project);
			rejectedScopes.add(project);
			return List.of();
		}
		List<ICompilationUnit> allowedUnits= JavaProjectCompilationUnits.collect(coordinatedProjects, currentScope,
				SourceRootPolicy.TEST_ROOTS_AND_SELECTED_SUPPORT);
		List<ICompilationUnit> requiredUnits;
		if (!seeds.complete()) {
			requiredUnits= allowedUnits;
		} else {
			Set<ICompilationUnit> required= new LinkedHashSet<>(seeds.directCompilationUnits());
			if (!seeds.elements().isEmpty()) {
				RelatedCompilationUnitSearch.Result related= RelatedCompilationUnitSearch.findReferences(
						coordinatedProjects, seeds.elements(), currentScope, allowedUnits, monitor);
				if (!related.complete()) {
					clearScopeDecision(project);
					rejectedScopes.add(project);
					return List.of();
				}
				required.addAll(related.compilationUnits());
			}
			requiredUnits= new ArrayList<>(required);
		}
		return registerRequiredScope(project, currentHandles, requiredUnits);
	}

	/**
	 * Returns every project a coordinated JUnit migration may have to modify: the
	 * cleaned project, the projects declaring the shared fixtures or base classes,
	 * and all projects that transitively reference them.
	 */
	private static List<IJavaProject> coordinatedProjects(IJavaProject project,
			JUnitScopeCandidateDetector.SearchSeeds seeds) {
		Set<IJavaProject> declaring= new LinkedHashSet<>();
		declaring.add(project);
		for (IJavaElement element : seeds.elements()) {
			IJavaProject owner= element == null ? null : element.getJavaProject();
			if (owner != null && owner.exists()) {
				declaring.add(owner);
			}
		}
		declaring.addAll(JavaProjectCompilationUnits.owningProjects(seeds.directCompilationUnits()));
		return JavaProjectCompilationUnits.withReferencingProjects(declaring);
	}


	private static Set<String> lifecycleAnnotations(EnumSet<JUnitCleanUpFixCore> fixes) {
		Set<String> result= new LinkedHashSet<>();
		if (fixes.contains(JUnitCleanUpFixCore.BEFORE)) {
			result.add(JUnitConstants.ORG_JUNIT_BEFORE);
			result.add(JUnitConstants.ORG_JUNIT_JUPITER_API_BEFORE_EACH);
		}
		if (fixes.contains(JUnitCleanUpFixCore.AFTER)) {
			result.add(JUnitConstants.ORG_JUNIT_AFTER);
			result.add(JUnitConstants.ORG_JUNIT_JUPITER_API_AFTER_EACH);
		}
		if (fixes.contains(JUnitCleanUpFixCore.BEFORECLASS)) {
			result.add(JUnitConstants.ORG_JUNIT_BEFORECLASS);
			result.add(JUnitConstants.ORG_JUNIT_JUPITER_API_BEFORE_ALL);
		}
		if (fixes.contains(JUnitCleanUpFixCore.AFTERCLASS)) {
			result.add(JUnitConstants.ORG_JUNIT_AFTERCLASS);
			result.add(JUnitConstants.ORG_JUNIT_JUPITER_API_AFTER_ALL);
		}
		return Set.copyOf(result);
	}

	private static JUnitScopeCandidateDetector.SearchSeeds mergeSeeds(
			JUnitScopeCandidateDetector.SearchSeeds first,
			JUnitScopeCandidateDetector.SearchSeeds second) {
		Set<IJavaElement> elements= new LinkedHashSet<>(first.elements());
		elements.addAll(second.elements());
		Set<ICompilationUnit> units= new LinkedHashSet<>(first.directCompilationUnits());
		units.addAll(second.directCompilationUnits());
		return new JUnitScopeCandidateDetector.SearchSeeds(
				first.candidateFound() || second.candidateFound(),
				first.complete() && second.complete(), new ArrayList<>(elements), new ArrayList<>(units));
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result= new ArrayList<>();
		if (isEnabled(JUNIT_CLEANUP) || isEnabled(JUNIT3_CLEANUP)) {
			result.add(Messages.format(JUnitCleanUp_description, new Object[] { String.join(",", //$NON-NLS-1$
					computeFixSet().stream().map(JUnitCleanUpFixCore::toString).collect(Collectors.toList())) }));
			if (isEnabled(JUnitMigrationOptions.BEST_EFFORT)) {
				result.add("Migrate independently safe JUnit constructs and add @todo scaffolds for manual completion"); //$NON-NLS-1$
			}
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder sb= new StringBuilder();
		if (isEnabled(JUnitMigrationOptions.BEST_EFFORT)) {
			sb.append("// Best-effort mode: unsupported constructs stay in place and receive an @todo scaffold.") //$NON-NLS-1$
					.append(System.lineSeparator())
					.append("// The resulting project may require manual completion before its tests run.") //$NON-NLS-1$
					.append(System.lineSeparator()).append(System.lineSeparator());
		}
		EnumSet<JUnitCleanUpFixCore> computeFixSet= computeFixSet();
		boolean first= true;
		for (JUnitCleanUpFixCore e : allOfJunit4()) {
			if (!first) {
				sb.append("// ─── "); //$NON-NLS-1$
				sb.append(e.toString());
				sb.append(" ───").append(System.lineSeparator()); //$NON-NLS-1$
			}
			sb.append(e.getPreview(computeFixSet.contains(e)));
			first= false;
		}
		return sb.toString();
	}

	private EnumSet<JUnitCleanUpFixCore> computeFixSet() {
		EnumSet<JUnitCleanUpFixCore> fixSetJunit4= isEnabled(JUNIT_CLEANUP)
				? allOfJunit4()
				: EnumSet.noneOf(JUnitCleanUpFixCore.class);
		EnumSet<JUnitCleanUpFixCore> fixSetJunit3= isEnabled(JUNIT3_CLEANUP)
				? allOfJunit3()
				: EnumSet.noneOf(JUnitCleanUpFixCore.class);
		Map<String, JUnitCleanUpFixCore> cleanupMappings= Map.ofEntries(
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT, JUnitCleanUpFixCore.ASSERT),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION, JUnitCleanUpFixCore.ASSERT_OPTIMIZATION),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME, JUnitCleanUpFixCore.ASSUME),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME_OPTIMIZATION, JUnitCleanUpFixCore.ASSUME_OPTIMIZATION),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER, JUnitCleanUpFixCore.AFTER),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE, JUnitCleanUpFixCore.BEFORE),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS, JUnitCleanUpFixCore.AFTERCLASS),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS, JUnitCleanUpFixCore.BEFORECLASS),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST, JUnitCleanUpFixCore.TEST),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_TIMEOUT, JUnitCleanUpFixCore.TEST_TIMEOUT),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_EXPECTED, JUnitCleanUpFixCore.TEST_EXPECTED),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_3_TEST, JUnitCleanUpFixCore.TEST3),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE, JUnitCleanUpFixCore.IGNORE),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_CATEGORY, JUnitCleanUpFixCore.CATEGORY),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_FIX_METHOD_ORDER, JUnitCleanUpFixCore.FIX_METHOD_ORDER),
				Map.entry(JUnitMigrationOptions.JUNIT6_COMPATIBILITY, JUnitCleanUpFixCore.JUNIT6_COMPATIBILITY),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH, JUnitCleanUpFixCore.RUNWITH),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE, JUnitCleanUpFixCore.SUITEMETHOD),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE, JUnitCleanUpFixCore.EXTERNALRESOURCE),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER, JUnitCleanUpFixCore.RULETEMPORARYFOLDER),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME, JUnitCleanUpFixCore.RULETESTNAME),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE, JUnitCleanUpFixCore.RULEEXTERNALRESOURCE),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETIMEOUT, JUnitCleanUpFixCore.RULETIMEOUT),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXPECTEDEXCEPTION, JUnitCleanUpFixCore.RULEEXPECTEDEXCEPTION),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEERRORCOLLECTOR, JUnitCleanUpFixCore.RULEERRORCOLLECTOR),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED, JUnitCleanUpFixCore.PARAMETERIZED),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS, JUnitCleanUpFixCore.LOSTTESTS),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_THROWINGRUNNABLE, JUnitCleanUpFixCore.THROWINGRUNNABLE));
		EnumSet<JUnitCleanUpFixCore> fixSetCombined= EnumSet.copyOf(fixSetJunit4);
		fixSetCombined.addAll(fixSetJunit3);
		cleanupMappings.forEach((config, fix) -> {
			if (!isEnabled(config)) {
				fixSetCombined.remove(fix);
			}
		});
		if (isEnabled(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_EXPECTED)
				&& isEnabled(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_TIMEOUT)) {
			fixSetCombined.add(JUnitCleanUpFixCore.TEST_EXPECTED_TIMEOUT);
		}
		if (fixSetCombined.contains(JUnitCleanUpFixCore.EXTERNALRESOURCE)
				|| fixSetCombined.contains(JUnitCleanUpFixCore.RULEEXTERNALRESOURCE)) {
			fixSetCombined.add(JUnitCleanUpFixCore.EXTERNALRESOURCE);
			fixSetCombined.add(JUnitCleanUpFixCore.RULEEXTERNALRESOURCE);
		}
		return fixSetCombined;
	}

	private EnumSet<JUnitCleanUpFixCore> allOfJunit4() {
		EnumSet<JUnitCleanUpFixCore> allOf= EnumSet.allOf(JUnitCleanUpFixCore.class);
		allOf.remove(JUnitCleanUpFixCore.TEST3);
		allOf.remove(JUnitCleanUpFixCore.TEST_EXPECTED_TIMEOUT);
		return allOf;
	}

	private EnumSet<JUnitCleanUpFixCore> allOfJunit3() {
		return EnumSet.of(JUnitCleanUpFixCore.TEST3);
	}

	private Collection<ICompilationUnit> registerRequiredScope(IJavaProject project,
			Set<String> currentHandles, Collection<ICompilationUnit> requiredUnits) {
		Set<String> requiredHandles= handles(requiredUnits);
		if (currentHandles.containsAll(requiredHandles)) {
			verifiedClosedScopes.put(project, requiredHandles);
			pendingExpandedScopes.remove(project);
			return List.of();
		}
		pendingExpandedScopes.put(project, requiredHandles);
		verifiedClosedScopes.remove(project);
		return requiredUnits;
	}

	private Boolean consumeClosedScopeDecision(IJavaProject project, ICompilationUnit[] compilationUnits) {
		if (rejectedScopes.remove(project)) {
			clearScopeDecision(project);
			return Boolean.FALSE;
		}
		Set<String> expected= verifiedClosedScopes.remove(project);
		Set<String> pending= pendingExpandedScopes.remove(project);
		if (expected == null) {
			expected= pending;
		}
		return expected == null ? null : Boolean.valueOf(handles(List.of(compilationUnits)).containsAll(expected));
	}

	private void clearScopeDecision(IJavaProject project) {
		pendingExpandedScopes.remove(project);
		verifiedClosedScopes.remove(project);
	}

	private static Set<String> handles(Collection<ICompilationUnit> units) {
		if (units == null || units.isEmpty()) {
			return Set.of();
		}
		return units.stream()
				.filter(java.util.Objects::nonNull)
				.map(ICompilationUnit::getPrimary)
				.map(ICompilationUnit::getHandleIdentifier)
				.collect(Collectors.toSet());
	}
}
