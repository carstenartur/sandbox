/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowComponent.LocatedFlowDiagnostic;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationKind;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationRoot;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.Relationship;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchKind;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AccessProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AliasingContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ContainerIdentity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ElementDomain;
import org.sandbox.jdt.container.api.ContainerUsageProfile.EscapeLevel;
import org.sandbox.jdt.container.api.ContainerUsageProfile.MutationLifecycle;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.ResolvedSearchTarget;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.TargetKind;

class ContainerFlowContinuationLinkerTest {

	private final ContainerFlowContinuationLinker linker=
			new ContainerFlowContinuationLinker();

	@Test
	void linksCallerArgumentToResolvedParameterAndClosesComponent() {
		FlowNode argument= local("local:argument", "argument-binding", "Caller.java", 10); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		FlowNode parameter= parameter("parameter:method:0", "parameter-binding", //$NON-NLS-1$ //$NON-NLS-2$
				"method-key", "Callee.java", "method-handle", 0, true, 20); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		ContainerFlowComponent component= component(
				argument,
				List.of(argument, parameter),
				ClosureStatus.REQUIRES_SCOPE_EXPANSION);
		ContinuationRoot root= new ContinuationRoot(
				parameter.stableId(),
				ContinuationKind.CALL_ARGUMENT,
				Relationship.ROOT_TO_BOUNDARY,
				EdgeKind.ARGUMENT_TO_PARAMETER,
				"Caller.java", //$NON-NLS-1$
				"method-handle", //$NON-NLS-1$
				profile("argument-binding", "argument", 10)); //$NON-NLS-1$ //$NON-NLS-2$

		ContainerFlowComponent linked= linker.link(
				component,
				new ContainerFlowContinuationPlan(List.of(root), List.of()),
				new ResolvedContainerFlowSearchPlan(List.of(
						methodTarget(parameter.stableId(), SearchKind.METHOD_CALLERS, 0))));

		assertEquals(ClosureStatus.LOCAL_CLOSED, linked.closureStatus());
		assertEquals(1, linked.edges().size());
		assertEquals(argument.stableId(), linked.edges().get(0).sourceNodeId());
		assertEquals(parameter.stableId(), linked.edges().get(0).targetNodeId());
		assertTrue(linked.diagnostics().isEmpty());
	}

	@Test
	void linksReturnBoundaryToResultConsumer() {
		FlowNode returned= new FlowNode(
				"return:method-key", NodeKind.RETURN_POSITION, "", "method-key", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"Producer.java", "method-handle", -1, true, 5, 3); //$NON-NLS-1$ //$NON-NLS-2$
		FlowNode result= local("local:result", "result-binding", "Caller.java", 30); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		ContainerFlowComponent component= component(
				returned, List.of(returned, result), ClosureStatus.REQUIRES_SCOPE_EXPANSION);
		ContinuationRoot root= new ContinuationRoot(
				returned.stableId(),
				ContinuationKind.RETURN_CONSUMER,
				Relationship.BOUNDARY_TO_ROOT,
				EdgeKind.INITIALIZER,
				"Caller.java", //$NON-NLS-1$
				"method-handle", //$NON-NLS-1$
				profile("result-binding", "result", 30)); //$NON-NLS-1$ //$NON-NLS-2$

		ContainerFlowComponent linked= linker.link(
				component,
				new ContainerFlowContinuationPlan(List.of(root), List.of()),
				new ResolvedContainerFlowSearchPlan(List.of(
						methodTarget(returned.stableId(), SearchKind.METHOD_CALLERS, -1))));

		assertEquals(ClosureStatus.LOCAL_CLOSED, linked.closureStatus());
		assertEquals(returned.stableId(), linked.edges().get(0).sourceNodeId());
		assertEquals(result.stableId(), linked.edges().get(0).targetNodeId());
	}

	@Test
	void sameNodeContinuationAddsNoSyntheticEdge() {
		FlowNode parameter= parameter("parameter:method:0", "parameter-binding", //$NON-NLS-1$ //$NON-NLS-2$
				"method-key", "Callee.java", "method-handle", 0, true, 20); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		ContinuationRoot root= new ContinuationRoot(
				parameter.stableId(),
				ContinuationKind.PARAMETER_DECLARATION,
				Relationship.SAME_NODE,
				null,
				"Callee.java", //$NON-NLS-1$
				"method-handle", //$NON-NLS-1$
				profile("parameter-binding", "input", 20)); //$NON-NLS-1$ //$NON-NLS-2$

		ContainerFlowComponent linked= linker.link(
				component(parameter, List.of(parameter), ClosureStatus.REQUIRES_SCOPE_EXPANSION),
				new ContainerFlowContinuationPlan(List.of(root), List.of()),
				new ResolvedContainerFlowSearchPlan(List.of(
						methodTarget(parameter.stableId(),
								SearchKind.METHOD_OVERRIDE_FAMILY, 0))));

		assertEquals(ClosureStatus.LOCAL_CLOSED, linked.closureStatus());
		assertTrue(linked.edges().isEmpty());
	}

	@Test
	void missingResolvedTargetRejectsInsteadOfGuessing() {
		FlowNode argument= local("local:argument", "argument-binding", "Caller.java", 10); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		ContinuationRoot root= new ContinuationRoot(
				"parameter:missing:0", //$NON-NLS-1$
				ContinuationKind.CALL_ARGUMENT,
				Relationship.ROOT_TO_BOUNDARY,
				EdgeKind.ARGUMENT_TO_PARAMETER,
				"Caller.java", //$NON-NLS-1$
				"method-handle", //$NON-NLS-1$
				profile("argument-binding", "argument", 10)); //$NON-NLS-1$ //$NON-NLS-2$

		ContainerFlowComponent linked= linker.link(
				component(argument, List.of(argument), ClosureStatus.REQUIRES_SCOPE_EXPANSION),
				new ContainerFlowContinuationPlan(List.of(root), List.of()),
				ResolvedContainerFlowSearchPlan.empty());

		assertEquals(ClosureStatus.REJECTED, linked.closureStatus());
		assertTrue(linked.diagnostics().stream()
				.anyMatch(diagnostic -> diagnostic.message().contains("No exact resolved"))); //$NON-NLS-1$
	}

	private static ContainerFlowComponent component(
			FlowNode root,
			List<FlowNode> nodes,
			ClosureStatus status) {
		List<LocatedFlowDiagnostic> diagnostics= status == ClosureStatus.REQUIRES_SCOPE_EXPANSION
				? List.of(new LocatedFlowDiagnostic(
						root.compilationUnitHandle(),
						DiagnosticKind.SCOPE_EXPANSION_REQUIRED,
						"Scope expansion is required", //$NON-NLS-1$
						root.sourceStart(),
						root.sourceLength()))
				: List.of();
		return new ContainerFlowComponent(
				root.stableId(), nodes, List.of(), status, diagnostics);
	}

	private static FlowNode local(
			String id,
			String bindingKey,
			String unit,
			int start) {
		return new FlowNode(
				id, NodeKind.LOCAL_VARIABLE, bindingKey, "", unit, //$NON-NLS-1$
				"element:" + bindingKey, -1, true, start, 1); //$NON-NLS-1$
	}

	private static FlowNode parameter(
			String id,
			String bindingKey,
			String ownerKey,
			String unit,
			String methodHandle,
			int index,
			boolean resolved,
			int start) {
		return new FlowNode(
				id, NodeKind.PARAMETER, bindingKey, ownerKey, unit,
				methodHandle, index, resolved, start, 1);
	}

	private static ResolvedSearchTarget methodTarget(
			String sourceNodeId,
			SearchKind kind,
			int signatureIndex) {
		return new ResolvedSearchTarget(
				sourceNodeId,
				kind,
				TargetKind.METHOD,
				"parameter-binding", //$NON-NLS-1$
				"method-key", //$NON-NLS-1$
				"method-handle", //$NON-NLS-1$
				signatureIndex,
				"Continue method flow"); //$NON-NLS-1$
	}

	private static ContainerUsageProfile profile(
			String bindingKey,
			String name,
			int start) {
		return new ContainerUsageProfile(
				new ContainerIdentity(bindingKey, name, start, 1),
				ContainerShape.ARRAY,
				ElementDomain.REFERENCE,
				new AccessProfile(false, false, false, false, false, false, false),
				OrderRequirement.UNKNOWN,
				UniquenessRequirement.UNKNOWN,
				MutationLifecycle.UNKNOWN,
				NullContract.UNKNOWN,
				AliasingContract.UNKNOWN,
				EscapeLevel.LOCAL,
				ConcurrencyProfile.unknown(),
				AnalysisCompleteness.LOCAL_SEED,
				List.of());
	}
}
