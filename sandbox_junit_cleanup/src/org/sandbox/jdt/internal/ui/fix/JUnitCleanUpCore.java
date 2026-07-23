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
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitMigrationPlan;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitMultiFilePlanner;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitScopeCandidateDetector;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;

/** Core cleanup implementation for JUnit 3/4 to Jupiter migration. */
public class JUnitCleanUpCore extends AbstractPlannedMultiFileCleanUp<JUnitMigrationPlan> {

	private final Map<IJavaProject, Set<String>> pendingExpandedScopes= new HashMap<>();
	private final Map<IJavaProject, Set<String>> verifiedClosedScopes= new HashMap<>();
	private final Set<IJavaProject> rejectedScopes= new HashSet<>();

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
			return MultiFileCleanUpPlanResult.noPlan();
		}
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
		Boolean closedScope= consumeClosedScopeDecision(project, compilationUnits);
		boolean migrateExternalResourceRules= fixes.contains(JUnitCleanUpFixCore.RULEEXTERNALRESOURCE);
		return closedScope == null
				? JUnitMultiFilePlanner.create(project, compilationUnits, migrateExternalResourceRules, monitor)
				: JUnitMultiFilePlanner.create(project, compilationUnits, migrateExternalResourceRules,
						closedScope.booleanValue(), monitor);
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
		Set<CompilationUnitRewriteOperationWithSourceRange> operations= new LinkedHashSet<>();
		Set<ASTNode> sharedNodesProcessed= new HashSet<>();
		plan.addOperationsFor(context.getCompilationUnit(), compilationUnit, operations, sharedNodesProcessed);
		computeFixSet.forEach(i -> i.findOperations(compilationUnit, operations, sharedNodesProcessed));
		if (operations.isEmpty()) {
			return null;
		}
		return new CompilationUnitRewriteOperationsFixCore(JUnitCleanUpFix_refactor, compilationUnit,
				operations.toArray(new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[0]));
	}

	@Override
	protected Collection<ICompilationUnit> discoverAdditionalCompilationUnits(IJavaProject project,
			Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) throws CoreException {
		if (!computeFixSet().contains(JUnitCleanUpFixCore.RULEEXTERNALRESOURCE)) {
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
		JUnitScopeCandidateDetector.SearchSeeds seeds=
				JUnitScopeCandidateDetector.findSearchSeeds(project, currentScope, monitor);
		if (!seeds.candidateFound()) {
			clearScopeDecision(project);
			return List.of();
		}

		List<ICompilationUnit> allowedUnits= JavaProjectCompilationUnits.collect(project, currentScope,
				SourceRootPolicy.TEST_ROOTS_AND_SELECTED_SUPPORT);
		List<ICompilationUnit> requiredUnits;
		if (!seeds.complete()) {
			requiredUnits= allowedUnits;
		} else {
			RelatedCompilationUnitSearch.Result related= RelatedCompilationUnitSearch.findReferences(project,
					seeds.elements(), currentScope, allowedUnits, monitor);
			if (!related.complete()) {
				clearScopeDecision(project);
				rejectedScopes.add(project);
				return List.of();
			}
			requiredUnits= related.compilationUnits();
		}
		return registerRequiredScope(project, currentHandles, requiredUnits);
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result= new ArrayList<>();
		if (isEnabled(JUNIT_CLEANUP) || isEnabled(JUNIT3_CLEANUP)) {
			result.add(Messages.format(JUnitCleanUp_description, new Object[] { String.join(",", //$NON-NLS-1$
					computeFixSet().stream().map(JUnitCleanUpFixCore::toString).collect(Collectors.toList())) }));
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder sb= new StringBuilder();
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
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH, JUnitCleanUpFixCore.RUNWITH),
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
		return fixSetCombined;
	}

	private EnumSet<JUnitCleanUpFixCore> allOfJunit4() {
		EnumSet<JUnitCleanUpFixCore> allOf= EnumSet.allOf(JUnitCleanUpFixCore.class);
		allOf.remove(JUnitCleanUpFixCore.TEST3);
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
