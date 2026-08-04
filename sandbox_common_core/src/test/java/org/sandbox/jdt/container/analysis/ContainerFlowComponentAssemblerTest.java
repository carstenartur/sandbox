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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowGraph;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowDiagnostic;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowEdge;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;

class ContainerFlowComponentAssemblerTest {

	private final ContainerFlowComponentAssembler assembler=
			new ContainerFlowComponentAssembler();

	@Test
	void unresolvedParameterBoundaryMergesWithSourceDeclaration() {
		FlowNode callerValue= local("caller-value", "caller-binding", "Caller.java", "caller-handle", 10); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		FlowNode boundary= parameter(
				"boundary", "", "method-key", "", "method-handle", 0, false, 20); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		ContainerFlowGraph caller= graph(
				callerValue,
				List.of(callerValue, boundary),
				List.of(new FlowEdge(
						callerValue.stableId(), boundary.stableId(),
						EdgeKind.ARGUMENT_TO_PARAMETER, 20, 5)),
				ClosureStatus.REQUIRES_SCOPE_EXPANSION,
				List.of(new FlowDiagnostic(
						DiagnosticKind.SCOPE_EXPANSION_REQUIRED,
						"Parameter source is outside the fragment", 20, 5))); //$NON-NLS-1$

		FlowNode sourceParameter= parameter(
				"source-parameter", "parameter-binding", "method-key", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"Callee.java", "method-handle", 0, true, 30); //$NON-NLS-1$ //$NON-NLS-2$
		FlowNode calleeAlias= local(
				"callee-alias", "alias-binding", "Callee.java", "alias-handle", 40); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		ContainerFlowGraph callee= graph(
				sourceParameter,
				List.of(sourceParameter, calleeAlias),
				List.of(new FlowEdge(
						sourceParameter.stableId(), calleeAlias.stableId(),
						EdgeKind.INITIALIZER, 40, 8)),
				ClosureStatus.LOCAL_CLOSED,
				List.of());

		ContainerFlowComponent component= assembler.assemble(List.of(caller, callee));

		List<FlowNode> parameters= component.nodes().stream()
				.filter(node -> node.kind() == NodeKind.PARAMETER)
				.toList();
		assertEquals(1, parameters.size());
		FlowNode mergedParameter= parameters.get(0);
		assertTrue(mergedParameter.sourceResolved());
		assertEquals("parameter-binding", mergedParameter.bindingKey()); //$NON-NLS-1$
		assertEquals("Callee.java", mergedParameter.compilationUnitHandle()); //$NON-NLS-1$
		assertEquals("method-handle", mergedParameter.javaElementHandle()); //$NON-NLS-1$
		assertEquals(0, mergedParameter.signatureIndex());
		assertEquals(2, component.edges().size());
		assertTrue(component.edges().stream().anyMatch(edge ->
				edge.targetNodeId().equals(mergedParameter.stableId())
						&& edge.compilationUnitHandle().equals("Caller.java"))); //$NON-NLS-1$
		assertTrue(component.edges().stream().anyMatch(edge ->
				edge.sourceNodeId().equals(mergedParameter.stableId())
						&& edge.compilationUnitHandle().equals("Callee.java"))); //$NON-NLS-1$
		assertEquals("Caller.java", component.diagnostics().get(0).compilationUnitHandle()); //$NON-NLS-1$
	}

	@Test
	void externalParameterBoundaryRemainsExternal() {
		FlowNode value= local("value", "value-binding", "Caller.java", "value-handle", 5); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		FlowNode external= new FlowNode(
				"external", NodeKind.EXTERNAL_PARAMETER, "", "binary-method", "Caller.java", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"binary-handle", 0, false, 15, 4); //$NON-NLS-1$
		ContainerFlowGraph fragment= graph(
				value,
				List.of(value, external),
				List.of(new FlowEdge(value.stableId(), external.stableId(),
						EdgeKind.ARGUMENT_TO_PARAMETER, 15, 4)),
				ClosureStatus.EXTERNAL_BOUNDARY,
				List.of());

		ContainerFlowComponent component= assembler.assemble(List.of(fragment));

		assertEquals(ClosureStatus.EXTERNAL_BOUNDARY, component.closureStatus());
		assertTrue(component.nodes().stream()
				.anyMatch(node -> node.kind() == NodeKind.EXTERNAL_PARAMETER));
	}

	@Test
	void conflictingResolvedParameterBindingsRejectComponent() {
		FlowNode first= parameter(
				"first", "binding-one", "method-key", "First.java", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"method-handle", 0, true, 1); //$NON-NLS-1$
		FlowNode second= parameter(
				"second", "binding-two", "method-key", "Second.java", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"method-handle", 0, true, 2); //$NON-NLS-1$

		ContainerFlowComponent component= assembler.assemble(List.of(
				graph(first, List.of(first), List.of(), ClosureStatus.LOCAL_CLOSED, List.of()),
				graph(second, List.of(second), List.of(), ClosureStatus.LOCAL_CLOSED, List.of())));

		assertEquals(ClosureStatus.REJECTED, component.closureStatus());
		assertTrue(component.diagnostics().stream()
				.anyMatch(diagnostic -> diagnostic.message().contains("Conflicting declarations"))); //$NON-NLS-1$
	}

	@Test
	void identicalLocatedEdgesAreDeduplicatedButDifferentUnitsRemainDistinct() {
		FlowNode source= local("source", "source-binding", "A.java", "source-handle", 1); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		FlowNode target= local("target", "target-binding", "A.java", "target-handle", 2); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		FlowEdge edge= new FlowEdge(source.stableId(), target.stableId(), EdgeKind.ASSIGNMENT, 10, 3);
		ContainerFlowGraph first= graph(
				source, List.of(source, target), List.of(edge, edge),
				ClosureStatus.LOCAL_CLOSED, List.of());
		FlowNode sourceInOtherUnit= local(
				"source-other", "source-binding", "B.java", "source-handle", 1); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		FlowNode targetInOtherUnit= local(
				"target-other", "target-binding", "B.java", "target-handle", 2); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		ContainerFlowGraph second= graph(
				sourceInOtherUnit,
				List.of(sourceInOtherUnit, targetInOtherUnit),
				List.of(new FlowEdge(
						sourceInOtherUnit.stableId(), targetInOtherUnit.stableId(),
						EdgeKind.ASSIGNMENT, 10, 3)),
				ClosureStatus.LOCAL_CLOSED,
				List.of());

		ContainerFlowComponent component= assembler.assemble(List.of(first, second));

		assertEquals(2, component.edges().size());
		assertEquals(List.of("A.java", "B.java"), //$NON-NLS-1$ //$NON-NLS-2$
				component.edges().stream()
						.map(ContainerFlowComponent.LocatedFlowEdge::compilationUnitHandle)
						.sorted()
						.toList());
	}

	@Test
	void componentCollectionsAreImmutable() {
		FlowNode root= local("root", "root-binding", "Root.java", "root-handle", 1); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		ContainerFlowComponent component= assembler.assemble(List.of(
				graph(root, List.of(root), List.of(), ClosureStatus.LOCAL_CLOSED, List.of())));

		assertThrows(UnsupportedOperationException.class,
				() -> component.nodes().add(root));
		assertThrows(UnsupportedOperationException.class,
				() -> component.diagnostics().clear());
		assertFalse(component.node(component.rootNodeId()).isEmpty());
	}

	@Test
	void emptyFragmentListAndMissingUnitHandleAreRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> assembler.assemble(List.of()));
		FlowNode root= new FlowNode(
				"root", NodeKind.LOCAL_VARIABLE, "binding", "", "", "", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
				-1, true, 0, 1);
		ContainerFlowGraph fragment= graph(
				root, List.of(root), List.of(), ClosureStatus.LOCAL_CLOSED, List.of());

		assertThrows(IllegalArgumentException.class,
				() -> assembler.assemble(List.of(fragment)));
	}

	private static ContainerFlowGraph graph(
			FlowNode root,
			List<FlowNode> nodes,
			List<FlowEdge> edges,
			ClosureStatus status,
			List<FlowDiagnostic> diagnostics) {
		return new ContainerFlowGraph(root.stableId(), nodes, edges, status, diagnostics);
	}

	private static FlowNode local(
			String stableId,
			String bindingKey,
			String unitHandle,
			String elementHandle,
			int sourceStart) {
		return new FlowNode(
				stableId, NodeKind.LOCAL_VARIABLE, bindingKey, "", unitHandle, //$NON-NLS-1$
				elementHandle, -1, true, sourceStart, 1);
	}

	private static FlowNode parameter(
			String stableId,
			String bindingKey,
			String ownerKey,
			String unitHandle,
			String elementHandle,
			int parameterIndex,
			boolean sourceResolved,
			int sourceStart) {
		return new FlowNode(
				stableId, NodeKind.PARAMETER, bindingKey, ownerKey, unitHandle,
				elementHandle, parameterIndex, sourceResolved, sourceStart, 1);
	}
}
