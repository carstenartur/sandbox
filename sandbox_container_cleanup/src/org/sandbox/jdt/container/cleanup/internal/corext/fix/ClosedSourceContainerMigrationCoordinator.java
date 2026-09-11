/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.cleanup.internal.corext.fix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

import org.sandbox.jdt.cleanup.multifile.ContainerFlowScopeSearch;
import org.sandbox.jdt.cleanup.multifile.JavaProjectCompilationUnits;
import org.sandbox.jdt.cleanup.multifile.SourceRootPolicy;
import org.sandbox.jdt.container.analysis.AppendOnlyArraySeedDetector;
import org.sandbox.jdt.container.analysis.ClosedFlowArrayUsageRefiner;
import org.sandbox.jdt.container.analysis.ClosedSourceParameterMigrationPlanner;
import org.sandbox.jdt.container.analysis.ContainerBridgePolicyPlanner;
import org.sandbox.jdt.container.analysis.ContainerContractInferrer;
import org.sandbox.jdt.container.analysis.ContainerFlowComponentAssembler;
import org.sandbox.jdt.container.analysis.ContainerFlowContinuationDetector;
import org.sandbox.jdt.container.analysis.ContainerFlowContinuationLinker;
import org.sandbox.jdt.container.analysis.ContainerFlowSearchSeedExtractor;
import org.sandbox.jdt.container.analysis.ContainerMigrationReadinessPlanner;
import org.sandbox.jdt.container.analysis.ContainerSignatureAtomicityPlanner;
import org.sandbox.jdt.container.analysis.LocalArrayUsageAnalyzer;
import org.sandbox.jdt.container.analysis.LocalContainerFlowGraphBuilder;
import org.sandbox.jdt.container.api.ClosedSourceParameterMigrationPlan;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan;
import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationDiagnostic;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationKind;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationRoot;
import org.sandbox.jdt.container.api.ContainerFlowGraph;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan;

/**
 * Bridges the existing semantic container-analysis layers to the Eclipse cleanup
 * lifecycle for the first closed-source parameter migration slice.
 *
 * <p>This class deliberately performs no rewrite. It discovers one append-array
 * candidate, closes its method/override/caller search scope, rebuilds the complete
 * flow from fresh binding-resolved ASTs and delegates semantic, signature, bridge,
 * readiness and rewrite-plan decisions to the existing reusable planners.</p>
 */
final class ClosedSourceContainerMigrationCoordinator {

	private final AppendOnlyArraySeedDetector seedDetector= new AppendOnlyArraySeedDetector();
	private final LocalArrayUsageAnalyzer usageAnalyzer= new LocalArrayUsageAnalyzer();
	private final LocalContainerFlowGraphBuilder graphBuilder= new LocalContainerFlowGraphBuilder();
	private final ContainerFlowSearchSeedExtractor searchSeedExtractor= new ContainerFlowSearchSeedExtractor();
	private final ContainerFlowContinuationDetector continuationDetector= new ContainerFlowContinuationDetector();
	private final ContainerFlowComponentAssembler componentAssembler= new ContainerFlowComponentAssembler();
	private final ContainerFlowContinuationLinker continuationLinker= new ContainerFlowContinuationLinker();
	private final ClosedFlowArrayUsageRefiner usageRefiner= new ClosedFlowArrayUsageRefiner();
	private final ContainerContractInferrer contractInferrer= new ContainerContractInferrer();
	private final ContainerSignatureAtomicityPlanner signaturePlanner= new ContainerSignatureAtomicityPlanner();
	private final ContainerBridgePolicyPlanner bridgePlanner= new ContainerBridgePolicyPlanner();
	private final ContainerMigrationReadinessPlanner readinessPlanner= new ContainerMigrationReadinessPlanner();
	private final ClosedSourceParameterMigrationPlanner rewritePlanner= new ClosedSourceParameterMigrationPlanner();

	Discovery discover(IJavaProject project, Collection<ICompilationUnit> currentScope,
			IProgressMonitor monitor) throws CoreException {
		checkCanceled(monitor);
		List<ICompilationUnit> scope= normalize(project, currentScope);
		if (scope.isEmpty()) {
			return Discovery.none();
		}
		Map<String, CompilationUnit> roots= parse(project, scope, monitor);
		return discoverPrepared(project, scope, roots, monitor).discovery();
	}

	private PreparedDiscovery discoverPrepared(IJavaProject project,
			List<ICompilationUnit> scope, Map<String, CompilationUnit> roots,
			IProgressMonitor monitor) throws CoreException {
		CandidateSelection selection= selectCandidate(scope, roots);
		if (!selection.complete()) {
			return new PreparedDiscovery(
					Discovery.rejected(selection.candidateId(), selection.ownerHandle(),
							selection.reasonCode(), selection.message()),
					selection);
		}
		if (selection.candidate().isEmpty()) {
			return new PreparedDiscovery(Discovery.none(), selection);
		}
		Candidate candidate= selection.candidate().orElseThrow();
		ContainerFlowSearchPlan searchPlan= searchSeedExtractor.extract(candidate.localGraph());
		if (searchPlan.isEmpty()) {
			return new PreparedDiscovery(Discovery.none(), selection);
		}

		List<ICompilationUnit> allowedUnits= JavaProjectCompilationUnits.collect(
				List.of(project), scope, SourceRootPolicy.PRODUCTION_WITH_DEPENDENT_TESTS);
		ContainerFlowScopeSearch.Result result= ContainerFlowScopeSearch.findRelatedUnits(
				project, searchPlan, scope, allowedUnits, monitor);
		if (!result.complete()) {
			return new PreparedDiscovery(
					Discovery.rejected(candidate.candidateId(), candidate.unitHandle(),
							"SOURCE_CLOSURE_INCOMPLETE", joinReasons(result.rejectionReasons())), //$NON-NLS-1$
					selection);
		}
		Set<ICompilationUnit> required= new LinkedHashSet<>(scope);
		required.add(candidate.unit());
		required.addAll(result.compilationUnits());
		return new PreparedDiscovery(
				Discovery.found(candidate.candidateId(), candidate.unitHandle(),
						new ArrayList<>(required), result.resolvedPlan()),
				selection);
	}

	Planning plan(IJavaProject project, ICompilationUnit[] compilationUnits,
			IProgressMonitor monitor) throws CoreException {
		checkCanceled(monitor);
		List<ICompilationUnit> scope= normalize(project, List.of(compilationUnits));
		if (scope.isEmpty()) {
			return Planning.none();
		}
		Map<String, CompilationUnit> roots= parse(project, scope, monitor);
		PreparedDiscovery prepared= discoverPrepared(project, scope, roots, monitor);
		Discovery discovery= prepared.discovery();
		if (!discovery.candidateFound()) {
			return Planning.none();
		}
		if (!discovery.complete()) {
			return Planning.rejected(discovery.candidateId(), discovery.ownerHandle(),
					discovery.reasonCode(), discovery.message(), discovery.requiredHandles());
		}
		Set<String> currentHandles= handles(scope);
		if (!currentHandles.containsAll(discovery.requiredHandles())) {
			return Planning.rejected(discovery.candidateId(), discovery.ownerHandle(),
					"SOURCE_CLOSURE_NOT_SELECTED", //$NON-NLS-1$
					"The coordinated cleanup scope does not contain every required source compilation unit.", //$NON-NLS-1$
					discovery.requiredHandles());
		}

		CandidateSelection selection= prepared.selection();
		if (!selection.complete() || selection.candidate().isEmpty()) {
			return Planning.rejected(discovery.candidateId(), discovery.ownerHandle(),
					"CANDIDATE_CHANGED", //$NON-NLS-1$
					selection.message().isBlank()
							? "The append-array candidate could not be re-established from the selected scope." //$NON-NLS-1$
							: selection.message(), discovery.requiredHandles());
		}
		Candidate candidate= selection.candidate().orElseThrow();
		if (!candidate.candidateId().equals(discovery.candidateId())) {
			return Planning.rejected(discovery.candidateId(), discovery.ownerHandle(),
					"CANDIDATE_CHANGED", //$NON-NLS-1$
					"The unique coordinated candidate changed while the scope was being closed.", //$NON-NLS-1$
					discovery.requiredHandles());
		}

		ResolvedContainerFlowSearchPlan resolved= discovery.resolvedPlan();
		List<ContinuationRoot> continuationRoots= new ArrayList<>();
		List<ContinuationDiagnostic> continuationDiagnostics= new ArrayList<>();
		List<ContainerFlowGraph> fragments= new ArrayList<>();
		fragments.add(candidate.localGraph());
		Map<String, ContainerUsageProfile> parameterProfilesByKey= new LinkedHashMap<>();

		for (ICompilationUnit unit : scope) {
			String unitHandle= primaryHandle(unit);
			CompilationUnit root= roots.get(unitHandle);
			if (root == null) {
				continue;
			}
			ContainerFlowContinuationPlan continuations= continuationDetector.detect(
					root, unitHandle, resolved);
			continuationRoots.addAll(continuations.roots());
			continuationDiagnostics.addAll(continuations.diagnostics());
			for (ContinuationRoot continuation : continuations.roots()) {
				ContainerUsageProfile profile= usageAnalyzer.analyze(root, continuation.profile());
				if (continuation.kind() == ContinuationKind.PARAMETER_DECLARATION) {
					String profileKey= continuation.compilationUnitHandle() + '|'
							+ profile.identity().bindingKey();
					parameterProfilesByKey.putIfAbsent(profileKey, profile);
				}
				fragments.add(graphBuilder.build(root, profile));
			}
		}
		ContainerFlowContinuationPlan continuations= new ContainerFlowContinuationPlan(
				deduplicateRoots(continuationRoots), continuationDiagnostics);
		if (!continuations.complete()) {
			return Planning.rejected(candidate.candidateId(), candidate.unitHandle(),
					"FLOW_CONTINUATION_INCOMPLETE", //$NON-NLS-1$
					joinReasons(continuations.diagnostics().stream()
							.map(ContinuationDiagnostic::message).toList()),
					discovery.requiredHandles());
		}

		ContainerFlowComponent component= componentAssembler.assemble(fragments);
		component= continuationLinker.link(component, continuations, resolved);
		if (component.closureStatus() != ClosureStatus.LOCAL_CLOSED) {
			return Planning.rejected(candidate.candidateId(), candidate.unitHandle(),
					"FLOW_COMPONENT_NOT_CLOSED", //$NON-NLS-1$
					"The assembled container flow is not a complete closed-source component: " //$NON-NLS-1$
							+ component.closureStatus(), discovery.requiredHandles());
		}

		List<ContainerUsageProfile> parameterProfiles= List.copyOf(parameterProfilesByKey.values());
		ContainerUsageProfile refined= usageRefiner.refine(candidate.unitHandle(),
				candidate.localProfile(), component, parameterProfiles);
		Optional<ContainerRecommendation> recommendation= contractInferrer.infer(refined);
		if (recommendation.isEmpty()) {
			return Planning.rejected(candidate.candidateId(), candidate.unitHandle(),
					"TARGET_CONTRACT_NOT_PROVEN", //$NON-NLS-1$
					"The closed flow does not prove a supported target container contract.", //$NON-NLS-1$
					discovery.requiredHandles());
		}

		ContainerSignatureMigrationPlan signatures= signaturePlanner.planClosedSource(
				component, resolved, recommendation.get());
		ContainerBridgePolicyPlan bridges= bridgePlanner.plan(signatures, recommendation.get());
		ContainerMigrationReadiness readiness= readinessPlanner.plan(
				component, recommendation.get(), signatures, bridges);
		List<ContainerUsageProfile> profiles= new ArrayList<>(parameterProfiles.size() + 1);
		profiles.add(refined);
		profiles.addAll(parameterProfiles);
		ClosedSourceParameterMigrationPlan.PlanningResult aggregate= rewritePlanner.plan(
				component, signatures, recommendation.get(), readiness, profiles);
		if (!aggregate.ready()) {
			String message= joinReasons(aggregate.diagnostics().stream()
					.map(ClosedSourceParameterMigrationPlan.PlanningDiagnostic::message).toList());
			String reason= aggregate.diagnostics().isEmpty()
					? "PARAMETER_REWRITE_REJECTED" //$NON-NLS-1$
					: aggregate.diagnostics().get(0).kind().name();
			return Planning.rejected(candidate.candidateId(), candidate.unitHandle(),
					reason, message, discovery.requiredHandles());
		}
		return Planning.accepted(candidate.candidateId(), candidate.unitHandle(),
				aggregate.plan().orElseThrow(), discovery.requiredHandles());
	}

	private CandidateSelection selectCandidate(List<ICompilationUnit> scope,
			Map<String, CompilationUnit> roots) {
		List<Candidate> candidates= new ArrayList<>();
		for (ICompilationUnit unit : scope) {
			CompilationUnit root= roots.get(primaryHandle(unit));
			if (root == null) {
				continue;
			}
			for (ContainerUsageProfile seed : seedDetector.findSeeds(root)) {
				ContainerUsageProfile local= usageAnalyzer.analyze(root, seed);
				ContainerFlowGraph graph= graphBuilder.build(root, local);
				ContainerFlowSearchPlan search= searchSeedExtractor.extract(graph);
				if (!search.isEmpty()) {
					candidates.add(new Candidate(unit, root, seed, local, graph));
				}
			}
		}
		candidates.sort(Comparator.comparing(Candidate::candidateId));
		if (candidates.isEmpty()) {
			return CandidateSelection.none();
		}
		if (candidates.size() > 1) {
			Candidate first= candidates.get(0);
			return CandidateSelection.rejected(first.candidateId(), first.unitHandle(),
					"MULTIPLE_COORDINATED_CANDIDATES", //$NON-NLS-1$
					"This first coordinated cleanup slice requires exactly one append-array candidate; " //$NON-NLS-1$
							+ candidates.size() + " were found in the selected scope."); //$NON-NLS-1$
		}
		return CandidateSelection.found(candidates.get(0));
	}

	private static List<ContinuationRoot> deduplicateRoots(List<ContinuationRoot> roots) {
		Map<String, ContinuationRoot> unique= new LinkedHashMap<>();
		for (ContinuationRoot root : roots) {
			unique.putIfAbsent(root.stableKey(), root);
		}
		return List.copyOf(unique.values());
	}

	private static String joinReasons(Collection<String> reasons) {
		return reasons.stream().filter(reason -> reason != null && !reason.isBlank())
				.distinct().sorted().reduce((left, right) -> left + "; " + right)
				.orElse("The coordinated source closure could not be proven."); //$NON-NLS-1$
	}

	private static Map<String, CompilationUnit> parse(IJavaProject project,
			List<ICompilationUnit> units, IProgressMonitor monitor) {
		Map<String, CompilationUnit> roots= new LinkedHashMap<>();
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(project);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(project));
		parser.createASTs(units.toArray(ICompilationUnit[]::new), new String[0], new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				roots.put(primaryHandle(source), ast);
			}
		}, monitor);
		return roots;
	}

	private static List<ICompilationUnit> normalize(IJavaProject project,
			Collection<ICompilationUnit> units) {
		Map<String, ICompilationUnit> unique= new LinkedHashMap<>();
		for (ICompilationUnit unit : units) {
			if (unit != null && unit.exists() && project.equals(unit.getJavaProject())) {
				ICompilationUnit primary= unit.getPrimary();
				ICompilationUnit canonical= primary == null ? unit : primary;
				unique.put(canonical.getHandleIdentifier(), canonical);
			}
		}
		return List.copyOf(unique.values());
	}

	private static Set<String> handles(Collection<ICompilationUnit> units) {
		Set<String> result= new LinkedHashSet<>();
		for (ICompilationUnit unit : units) {
			result.add(primaryHandle(unit));
		}
		return Set.copyOf(result);
	}

	private static String primaryHandle(ICompilationUnit unit) {
		ICompilationUnit primary= unit.getPrimary();
		return (primary == null ? unit : primary).getHandleIdentifier();
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}

	private record Candidate(ICompilationUnit unit, CompilationUnit root,
			ContainerUsageProfile seed, ContainerUsageProfile localProfile,
			ContainerFlowGraph localGraph) {
		String unitHandle() {
			return primaryHandle(unit);
		}

		String candidateId() {
			return unitHandle() + '|' + seed.identity().stableId();
		}
	}

	private record PreparedDiscovery(Discovery discovery, CandidateSelection selection) {
	}

	record Discovery(boolean candidateFound, boolean complete, String candidateId,
			String ownerHandle, String reasonCode, String message,
			List<ICompilationUnit> requiredUnits, ResolvedContainerFlowSearchPlan resolvedPlan) {

		Discovery {
			requiredUnits= List.copyOf(requiredUnits);
		}

		static Discovery none() {
			return new Discovery(false, true, "", "", "", "", List.of(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					new ResolvedContainerFlowSearchPlan(List.of()));
		}

		static Discovery rejected(String candidateId, String ownerHandle,
				String reasonCode, String message) {
			return new Discovery(true, false, candidateId, ownerHandle, reasonCode, message,
					List.of(), new ResolvedContainerFlowSearchPlan(List.of()));
		}

		static Discovery found(String candidateId, String ownerHandle,
				List<ICompilationUnit> requiredUnits, ResolvedContainerFlowSearchPlan resolvedPlan) {
			return new Discovery(true, true, candidateId, ownerHandle, "", "", //$NON-NLS-1$ //$NON-NLS-2$
					requiredUnits, resolvedPlan);
		}

		List<String> requiredHandles() {
			return requiredUnits.stream()
					.map(ClosedSourceContainerMigrationCoordinator::primaryHandle)
					.toList();
		}
	}

	record Planning(Optional<ClosedSourceParameterMigrationPlan> plan,
			boolean candidateFound, boolean complete, String candidateId,
			String ownerHandle, String reasonCode, String message,
			List<String> requiredHandles) {

		Planning {
			plan= java.util.Objects.requireNonNull(plan);
			requiredHandles= List.copyOf(requiredHandles);
		}

		static Planning none() {
			return new Planning(Optional.empty(), false, true, "", "", "", "", List.of()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

		static Planning rejected(String candidateId, String ownerHandle,
				String reasonCode, String message, List<String> requiredHandles) {
			return new Planning(Optional.empty(), true, false, candidateId, ownerHandle,
					reasonCode, message, requiredHandles);
		}

		static Planning accepted(String candidateId, String ownerHandle,
				ClosedSourceParameterMigrationPlan plan, List<String> requiredHandles) {
			return new Planning(Optional.of(plan), true, true, candidateId, ownerHandle,
					"TRANSFORMED", //$NON-NLS-1$
					"Migrate the closed append-array caller and complete parameter override family atomically.", //$NON-NLS-1$
					requiredHandles);
		}
	}

	private record CandidateSelection(Optional<Candidate> candidate, boolean complete,
			String candidateId, String ownerHandle, String reasonCode, String message) {
		static CandidateSelection none() {
			return new CandidateSelection(Optional.empty(), true, "", "", "", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

		static CandidateSelection found(Candidate candidate) {
			return new CandidateSelection(Optional.of(candidate), true, candidate.candidateId(),
					candidate.unitHandle(), "", ""); //$NON-NLS-1$ //$NON-NLS-2$
		}

		static CandidateSelection rejected(String candidateId, String ownerHandle,
				String reasonCode, String message) {
			return new CandidateSelection(Optional.empty(), false, candidateId, ownerHandle,
					reasonCode, message);
		}
	}
}
