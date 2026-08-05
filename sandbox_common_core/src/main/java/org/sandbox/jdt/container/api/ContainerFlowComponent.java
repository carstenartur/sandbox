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
package org.sandbox.jdt.container.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;

/**
 * One canonical value-flow component assembled from several compilation-unit-local
 * {@link ContainerFlowGraph} fragments.
 *
 * <p>Unlike a local fragment, every edge and diagnostic carries the compilation-unit
 * handle needed to interpret its source range. The component remains immutable and
 * contains no AST nodes.</p>
 */
public record ContainerFlowComponent(
		String rootNodeId,
		List<FlowNode> nodes,
		List<LocatedFlowEdge> edges,
		ClosureStatus closureStatus,
		List<LocatedFlowDiagnostic> diagnostics) {

	public ContainerFlowComponent {
		rootNodeId= requiredText(rootNodeId, "rootNodeId"); //$NON-NLS-1$
		nodes= List.copyOf(Objects.requireNonNull(nodes, "nodes")); //$NON-NLS-1$
		edges= List.copyOf(Objects.requireNonNull(edges, "edges")); //$NON-NLS-1$
		Objects.requireNonNull(closureStatus, "closureStatus"); //$NON-NLS-1$
		diagnostics= List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics")); //$NON-NLS-1$
		validate(rootNodeId, nodes, edges);
	}

	/** Returns one canonical node by identifier. */
	public Optional<FlowNode> node(String stableId) {
		return nodes.stream().filter(candidate -> candidate.stableId().equals(stableId)).findFirst();
	}

	/** Returns outgoing edges in deterministic assembly order. */
	public List<LocatedFlowEdge> outgoing(String stableId) {
		return edges.stream().filter(edge -> edge.sourceNodeId().equals(stableId)).toList();
	}

	/** One flow transfer together with the source unit containing the expression. */
	public record LocatedFlowEdge(
			String compilationUnitHandle,
			String sourceNodeId,
			String targetNodeId,
			EdgeKind kind,
			int sourceStart,
			int sourceLength) {

		public LocatedFlowEdge {
			compilationUnitHandle= requiredText(compilationUnitHandle, "compilationUnitHandle"); //$NON-NLS-1$
			sourceNodeId= requiredText(sourceNodeId, "sourceNodeId"); //$NON-NLS-1$
			targetNodeId= requiredText(targetNodeId, "targetNodeId"); //$NON-NLS-1$
			Objects.requireNonNull(kind, "kind"); //$NON-NLS-1$
			validateRange(sourceStart, sourceLength);
		}
	}

	/** One closure or rejection explanation together with its source unit. */
	public record LocatedFlowDiagnostic(
			String compilationUnitHandle,
			DiagnosticKind kind,
			String message,
			int sourceStart,
			int sourceLength) {

		public LocatedFlowDiagnostic {
			compilationUnitHandle= requiredText(compilationUnitHandle, "compilationUnitHandle"); //$NON-NLS-1$
			Objects.requireNonNull(kind, "kind"); //$NON-NLS-1$
			message= requiredText(message, "message"); //$NON-NLS-1$
			validateRange(sourceStart, sourceLength);
		}
	}

	private static void validate(
			String rootNodeId,
			List<FlowNode> nodes,
			List<LocatedFlowEdge> edges) {
		Set<String> identifiers= new HashSet<>();
		for (FlowNode node : nodes) {
			if (!identifiers.add(node.stableId())) {
				throw new IllegalArgumentException("Duplicate component node id: " + node.stableId()); //$NON-NLS-1$
			}
		}
		if (!identifiers.contains(rootNodeId)) {
			throw new IllegalArgumentException("Component root is not present in node list"); //$NON-NLS-1$
		}
		for (LocatedFlowEdge edge : edges) {
			if (!identifiers.contains(edge.sourceNodeId())
					|| !identifiers.contains(edge.targetNodeId())) {
				throw new IllegalArgumentException(
						"Component edge references an unknown node: " + edge); //$NON-NLS-1$
			}
		}
	}

	private static String requiredText(String value, String fieldName) {
		String text= Objects.requireNonNull(value, fieldName).strip();
		if (text.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must not be empty"); //$NON-NLS-1$
		}
		return text;
	}

	private static void validateRange(int sourceStart, int sourceLength) {
		if (sourceStart < 0 || sourceLength < 0) {
			throw new IllegalArgumentException("Source range must not be negative"); //$NON-NLS-1$
		}
	}
}
