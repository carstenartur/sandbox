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

/**
 * Stable, AST-free value-flow graph used to plan project-wide container migrations.
 *
 * @param rootNodeId stable identifier of the candidate root
 * @param nodes graph nodes in deterministic discovery order
 * @param edges graph edges in deterministic discovery order
 * @param closureStatus current closure classification
 * @param diagnostics source-backed boundary and rejection explanations
 */
public record ContainerFlowGraph(
		String rootNodeId,
		List<FlowNode> nodes,
		List<FlowEdge> edges,
		ClosureStatus closureStatus,
		List<FlowDiagnostic> diagnostics) {

	public ContainerFlowGraph {
		rootNodeId= requiredText(rootNodeId, "rootNodeId"); //$NON-NLS-1$
		nodes= List.copyOf(Objects.requireNonNull(nodes, "nodes")); //$NON-NLS-1$
		edges= List.copyOf(Objects.requireNonNull(edges, "edges")); //$NON-NLS-1$
		Objects.requireNonNull(closureStatus, "closureStatus"); //$NON-NLS-1$
		diagnostics= List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics")); //$NON-NLS-1$
		validateGraph(rootNodeId, nodes, edges);
	}

	/** Returns one node by stable identifier. */
	public Optional<FlowNode> node(String stableId) {
		return nodes.stream().filter(candidate -> candidate.stableId().equals(stableId)).findFirst();
	}

	/** Returns outgoing edges in deterministic discovery order. */
	public List<FlowEdge> outgoing(String stableId) {
		return edges.stream().filter(edge -> edge.sourceNodeId().equals(stableId)).toList();
	}

	private static void validateGraph(
			String rootNodeId,
			List<FlowNode> nodes,
			List<FlowEdge> edges) {
		Set<String> identifiers= new HashSet<>();
		for (FlowNode node : nodes) {
			if (!identifiers.add(node.stableId())) {
				throw new IllegalArgumentException("Duplicate flow node id: " + node.stableId()); //$NON-NLS-1$
			}
		}
		if (!identifiers.contains(rootNodeId)) {
			throw new IllegalArgumentException("Flow root is not present in node list"); //$NON-NLS-1$
		}
		for (FlowEdge edge : edges) {
			if (!identifiers.contains(edge.sourceNodeId())
					|| !identifiers.contains(edge.targetNodeId())) {
				throw new IllegalArgumentException(
						"Flow edge references an unknown node: " + edge); //$NON-NLS-1$
			}
		}
	}

	/**
	 * One declaration, signature position or external boundary in the value flow.
	 *
	 * @param signatureIndex zero-based parameter index, or {@code -1} when the node
	 *                       is not a parameter position
	 */
	public record FlowNode(
			String stableId,
			NodeKind kind,
			String bindingKey,
			String ownerKey,
			String compilationUnitHandle,
			int signatureIndex,
			boolean sourceResolved,
			int sourceStart,
			int sourceLength) {

		public FlowNode {
			stableId= requiredText(stableId, "stableId"); //$NON-NLS-1$
			Objects.requireNonNull(kind, "kind"); //$NON-NLS-1$
			bindingKey= optionalText(bindingKey);
			ownerKey= optionalText(ownerKey);
			compilationUnitHandle= optionalText(compilationUnitHandle);
			if (signatureIndex < -1) {
				throw new IllegalArgumentException("signatureIndex must be -1 or a parameter index"); //$NON-NLS-1$
			}
			validateRange(sourceStart, sourceLength);
		}

		/** Returns whether this node represents one concrete parameter position. */
		public boolean isParameterPosition() {
			return signatureIndex >= 0;
		}
	}

	/** One directed transfer of the represented container value. */
	public record FlowEdge(
			String sourceNodeId,
			String targetNodeId,
			EdgeKind kind,
			int sourceStart,
			int sourceLength) {

		public FlowEdge {
			sourceNodeId= requiredText(sourceNodeId, "sourceNodeId"); //$NON-NLS-1$
			targetNodeId= requiredText(targetNodeId, "targetNodeId"); //$NON-NLS-1$
			Objects.requireNonNull(kind, "kind"); //$NON-NLS-1$
			validateRange(sourceStart, sourceLength);
		}
	}

	/** One explanation why the currently analyzed scope is or is not closed. */
	public record FlowDiagnostic(
			DiagnosticKind kind,
			String message,
			int sourceStart,
			int sourceLength) {

		public FlowDiagnostic {
			Objects.requireNonNull(kind, "kind"); //$NON-NLS-1$
			message= requiredText(message, "message"); //$NON-NLS-1$
			validateRange(sourceStart, sourceLength);
		}
	}

	public enum NodeKind {
		LOCAL_VARIABLE,
		FIELD,
		PARAMETER,
		RETURN_POSITION,
		EXTERNAL_PARAMETER,
		UNKNOWN_BOUNDARY
	}

	public enum EdgeKind {
		ASSIGNMENT,
		INITIALIZER,
		ARGUMENT_TO_PARAMETER,
		RETURN_TO_METHOD
	}

	public enum ClosureStatus {
		LOCAL_CLOSED,
		REQUIRES_SCOPE_EXPANSION,
		EXTERNAL_BOUNDARY,
		REJECTED
	}

	public enum DiagnosticKind {
		SCOPE_EXPANSION_REQUIRED,
		EXTERNAL_OR_BINARY_TARGET,
		UNRESOLVED_BINDING,
		UNCLASSIFIED_FLOW
	}

	private static String requiredText(String value, String fieldName) {
		String text= Objects.requireNonNull(value, fieldName).strip();
		if (text.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must not be empty"); //$NON-NLS-1$
		}
		return text;
	}

	private static String optionalText(String value) {
		return value == null ? "" : value.strip(); //$NON-NLS-1$
	}

	private static void validateRange(int sourceStart, int sourceLength) {
		if (sourceStart < 0 || sourceLength < 0) {
			throw new IllegalArgumentException("Source range must not be negative"); //$NON-NLS-1$
		}
	}
}
