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
import org.sandbox.jdt.container.analysis.ClosedReturnArrayUses;
import org.sandbox.jdt.container.analysis.ContainerLocalRewritePlanner;
import org.sandbox.jdt.container.analysis.ClosedSourceParameterMigrationPlanner;
import org.sandbox.jdt.container.analysis.ConstructorOwnedArrayField;
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
import org.sandbox.jdt.container.api.ClosedSourceContainerPlan;
import org.sandbox.jdt.container.api.ClosedSourceReturnMigrationPlan;
import org.sandbox.jdt.container.api.ClosedSourceReturnMigrationPlan.Consumer;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PositionKind;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PlanningStatus;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan;
import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationDiagnostic;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationKind;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationRoot;
import org.sandbox.jdt.container.api.ContainerFlowGraph;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan.EditKind;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan.ParameterEdit;
import org.sandbox.jdt.container.api.UsageEvidence;
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

		if (component.nodes().stream().anyMatch(node -> node.kind() == NodeKind.RETURN_POSITION)) {
			var returnPlan= planReturn(candidate, component, resolved, roots);
			return returnPlan.isPresent()
					? Planning.accepted(candidate.candidateId(), candidate.unitHandle(), returnPlan.get(), discovery.requiredHandles())
					: Planning.rejected(candidate.candidateId(), candidate.unitHandle(), "RETURN_CLOSURE_NOT_PROVEN", //$NON-NLS-1$
							"The return declaration or a caller observes an unsupported array operation.", discovery.requiredHandles()); //$NON-NLS-1$
		}

		List<ContainerUsageProfile> parameterProfiles= List.copyOf(parameterProfilesByKey.values());
		Optional<OwnedStorage> storage= ownedStorage(candidate, component, parameterProfiles);
		if (storage.isPresent()) {
			component= storage.get().parameterComponent();
			parameterProfiles= List.of(storage.get().parameterProfile());
		}
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
				storage.map(proof -> proof.complete(aggregate.plan().orElseThrow()))
						.orElseGet(() -> aggregate.plan().orElseThrow()), discovery.requiredHandles());
	}

	/** Discharges only the constructor-to-field edge, after proving every use of that field. */
	private static Optional<OwnedStorage> ownedStorage(Candidate candidate, ContainerFlowComponent component,
			List<ContainerUsageProfile> parameters) {
		if (parameters.size() != 1 || component.nodes().size() != 3 || component.edges().size() != 2
				|| !component.diagnostics().isEmpty() || component.nodes().stream().anyMatch(node ->
						!node.sourceResolved() || !candidate.unitHandle().equals(node.compilationUnitHandle()))) {
			return Optional.empty();
		}
		var transfers= component.edges().stream().filter(edge -> edge.kind() == EdgeKind.ARGUMENT_TO_PARAMETER).toList();
		var stores= component.edges().stream().filter(edge -> edge.kind() == EdgeKind.ASSIGNMENT).toList();
		if (transfers.size() != 1 || stores.size() != 1) {
			return Optional.empty();
		}
		var transfer= transfers.getFirst();
		var store= stores.getFirst();
		var parameter= parameters.getFirst();
		var parameterNode= component.node(transfer.targetNodeId()).orElseThrow();
		var local= component.node(transfer.sourceNodeId()).orElseThrow();
		var field= component.node(store.targetNodeId()).orElseThrow();
		if (local.kind() != NodeKind.LOCAL_VARIABLE || !local.bindingKey().equals(candidate.localProfile().identity().bindingKey())
				|| !transfer.targetNodeId().equals(store.sourceNodeId()) || field.kind() != NodeKind.FIELD
				|| parameterNode.kind() != NodeKind.PARAMETER || !parameterNode.bindingKey().equals(parameter.identity().bindingKey())) {
			return Optional.empty();
		}
		var storage= ConstructorOwnedArrayField.analyze(candidate.root(), parameterNode.bindingKey(),
				transfer.sourceStart(), transfer.sourceLength());
		if (storage.isEmpty() || !field.bindingKey().equals(storage.get().bindingKey()) || !storage.get().edits().contains(
				new ParameterEdit(EditKind.VERIFY_CONSTRUCTOR_STORE, store.sourceStart(), store.sourceLength()))) {
			return Optional.empty();
		}
		// The constructor body consists solely of the proven store. Its parameter has no other use.
		var boundary= new UsageEvidence(UsageEvidence.Kind.FLOW_CONTINUATION_ROOT,
				"Terminal constructor argument stored in a private final field with verified readers", //$NON-NLS-1$
				store.sourceStart(), store.sourceLength());
		var verified= new ContainerUsageProfile(parameter.identity(), parameter.currentShape(), parameter.elementDomain(),
				parameter.access(), parameter.orderRequirement(), parameter.uniquenessRequirement(), parameter.mutationLifecycle(),
				parameter.nullContract(), ContainerUsageProfile.AliasingContract.NO_OBSERVED_ALIAS,
				ContainerUsageProfile.EscapeLevel.METHOD_BOUNDARY, parameter.concurrency(),
				ContainerUsageProfile.AnalysisCompleteness.FLOW_COMPLETE, List.of(boundary));
		var signatureComponent= new ContainerFlowComponent(local.stableId(), List.of(local, parameterNode),
				List.of(transfer), ClosureStatus.LOCAL_CLOSED, List.of());
		return Optional.of(new OwnedStorage(storage.get(), signatureComponent, verified));
	}

	private record OwnedStorage(ConstructorOwnedArrayField storage, ContainerFlowComponent parameterComponent,
			ContainerUsageProfile parameterProfile) {
		ClosedSourceParameterMigrationPlan complete(ClosedSourceParameterMigrationPlan plan) {
			var parameter= plan.parameterPlans().getFirst();
			List<ParameterEdit> edits= new ArrayList<>(parameter.edits());
			edits.addAll(storage.edits());
			var withStorage= new ContainerParameterRewritePlan(parameter.compilationUnitHandle(), parameter.methodJavaElementHandle(),
					parameter.parameterBindingKey(), parameter.parameterIndex(), parameter.targetInterfaceType(),
					parameter.targetContract(), edits, storage.bindingKey());
			return new ClosedSourceParameterMigrationPlan(plan.targetContract(), plan.callerPlan(), withStorage);
		}
	}

	private Optional<ClosedSourceReturnMigrationPlan> planReturn(Candidate candidate, ContainerFlowComponent component,
			ResolvedContainerFlowSearchPlan resolved, Map<String, CompilationUnit> roots) {
		var returns= component.nodes().stream().filter(node -> node.kind() == NodeKind.RETURN_POSITION).toList();
		if (returns.size() != 1 || !component.diagnostics().isEmpty()) {
			return Optional.empty();
		}
		FlowNode signature= returns.getFirst();
		var type= ClosedReturnArrayUses.producer(candidate.root(), signature.ownerKey(), candidate.localProfile().identity().bindingKey());
		var recommendation= contractInferrer.infer(candidate.localProfile());
		if (type.isEmpty() || recommendation.isEmpty()) {
			return Optional.empty();
		}
		var groups= signaturePlanner.plan(component, resolved, recommendation.get());
		if (groups.status() != PlanningStatus.REPORT_ONLY || groups.groups().size() != 1 || !groups.diagnostics().isEmpty()
				|| groups.groups().getFirst().positionKind() != PositionKind.RETURN || groups.groups().getFirst().members().size() != 1
				|| !groups.groups().getFirst().members().getFirst().flowNodeId().equals(signature.stableId())) {
			return Optional.empty();
		}
		List<Consumer> consumers= new ArrayList<>();
		for (var entry : roots.entrySet()) {
			var callers= ClosedReturnArrayUses.consumers(entry.getValue(), entry.getKey(), signature.ownerKey());
			if (callers.isEmpty()) {
				return Optional.empty();
			}
			consumers.addAll(callers.get());
		}
		consumers.sort(Comparator.comparing(Consumer::unitHandle).thenComparingInt(c -> c.call().start()));
		Set<String> consumerKeys= new LinkedHashSet<>();
		consumers.forEach(consumer -> consumerKeys.add(consumer.localBindingKey()));
		var producers= component.nodes().stream().filter(node -> node.bindingKey().equals(candidate.seed().identity().bindingKey())).toList();
		if (consumers.isEmpty() || producers.size() != 1 || consumers.size() != consumerKeys.size()
				|| component.nodes().size() != consumers.size() + 2 || component.edges().size() != consumers.size() + 1) {
			return Optional.empty();
		}
		FlowNode producer= producers.getFirst();
		for (var edge : component.edges()) {
			FlowNode target= component.node(edge.targetNodeId()).orElseThrow();
			if (edge.kind() == EdgeKind.RETURN_TO_METHOD && edge.sourceNodeId().equals(producer.stableId())
					&& edge.targetNodeId().equals(signature.stableId())) {
				continue;
			}
			if (edge.kind() != EdgeKind.INITIALIZER || !edge.sourceNodeId().equals(signature.stableId())
					|| target.kind() != NodeKind.LOCAL_VARIABLE || !consumerKeys.remove(target.bindingKey())) {
				return Optional.empty();
			}
		}
		if (!consumerKeys.isEmpty()) {
			return Optional.empty();
		}
		// Direct source-closed signature change; Java cannot preserve a same-name return-only overload.
		var direct= new ContainerSignatureMigrationPlan(groups.targetContract(), groups.groups(), PlanningStatus.CLOSED_SOURCE_AUTOMATIC, List.of());
		var bridges= bridgePlanner.plan(direct, recommendation.get());
		var readiness= readinessPlanner.plan(component, recommendation.get(), direct, bridges);
		var local= new ContainerFlowComponent(producer.stableId(), List.of(producer), List.of(), ClosureStatus.LOCAL_CLOSED, List.of());
		return new ContainerLocalRewritePlanner().plan(local, recommendation.get(), readiness).plan()
				.map(plan -> new ClosedSourceReturnMigrationPlan(plan, signature.javaElementHandle(), signature.ownerKey(), type.get(), consumers));
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

	record Planning(Optional<ClosedSourceContainerPlan> plan,
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
				ClosedSourceContainerPlan plan, List<String> requiredHandles) {
			return new Planning(Optional.of(plan), true, true, candidateId, ownerHandle,
					"TRANSFORMED", //$NON-NLS-1$
					plan instanceof ClosedSourceReturnMigrationPlan
							? "Migrate the internal return declaration and all source callers directly. A return-only compatibility overload is impossible." //$NON-NLS-1$
							: "Migrate the closed append-array caller and complete parameter override family atomically.", //$NON-NLS-1$
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
