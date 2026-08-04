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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowComponent.LocatedFlowDiagnostic;
import org.sandbox.jdt.container.api.ContainerFlowComponent.LocatedFlowEdge;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationKind;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationRoot;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.Relationship;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchKind;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.ResolvedSearchTarget;

/**
 * Connects newly assembled local continuation roots to the canonical boundary nodes
 * that initiated workspace scope expansion.
 *
 * <p>The linker never parses stable identifiers. Parameter and return positions are
 * selected from the exact resolved search target, using the Java-model handle and
 * signature index retained by the search layer.</p>
 */
public final class ContainerFlowContinuationLinker {

	/** Links all supported continuation roots and recomputes component closure. */
	public ContainerFlowComponent link(
			ContainerFlowComponent component,
			ContainerFlowContinuationPlan continuations,
			ResolvedContainerFlowSearchPlan resolvedPlan) {
		Objects.requireNonNull(component, "component"); //$NON-NLS-1$
		Objects.requireNonNull(continuations, "continuations"); //$NON-NLS-1$
		Objects.requireNonNull(resolvedPlan, "resolvedPlan"); //$NON-NLS-1$

		Map<String, FlowNode> nodesById= new LinkedHashMap<>();
		for (FlowNode node : component.nodes()) {
			nodesById.put(node.stableId(), node);
		}
		List<LocatedFlowEdge> edges= new ArrayList<>(component.edges());
		Set<String> edgeKeys= new LinkedHashSet<>();
		component.edges().forEach(edge -> edgeKeys.add(edgeKey(edge)));
		List<LocatedFlowDiagnostic> diagnostics= new ArrayList<>(component.diagnostics());
		Set<String> diagnosticKeys= new LinkedHashSet<>();
		component.diagnostics().forEach(diagnostic -> diagnosticKeys.add(diagnosticKey(diagnostic)));
		boolean rejected= !continuations.complete();

		for (ContinuationRoot root : continuations.roots()) {
			Optional<FlowNode> continuationNode= findContinuationNode(component.nodes(), root);
			Optional<ResolvedSearchTarget> target= findTarget(resolvedPlan, root);
			if (continuationNode.isEmpty()) {
				rejected= true;
				addDiagnostic(diagnostics, diagnosticKeys, root,
						"The continuation binding is not present in the assembled flow component."); //$NON-NLS-1$
				continue;
			}
			if (target.isEmpty()) {
				rejected= true;
				addDiagnostic(diagnostics, diagnosticKeys, root,
						"No exact resolved search target matches the continuation root."); //$NON-NLS-1$
				continue;
			}
			Optional<FlowNode> boundaryNode= findBoundaryNode(
					component.nodes(), nodesById, root, target.get(), continuationNode.get());
			if (boundaryNode.isEmpty()) {
				rejected= true;
				addDiagnostic(diagnostics, diagnosticKeys, root,
						"The canonical signature or field boundary is not present in the component."); //$NON-NLS-1$
				continue;
			}

			if (root.relationship() == Relationship.SAME_NODE) {
				if (!continuationNode.get().stableId().equals(boundaryNode.get().stableId())) {
					rejected= true;
					addDiagnostic(diagnostics, diagnosticKeys, root,
							"A same-node continuation was not canonicalized to its boundary node."); //$NON-NLS-1$
				}
				continue;
			}

			String sourceId= root.relationship() == Relationship.ROOT_TO_BOUNDARY
					? continuationNode.get().stableId()
					: boundaryNode.get().stableId();
			String targetId= root.relationship() == Relationship.ROOT_TO_BOUNDARY
					? boundaryNode.get().stableId()
					: continuationNode.get().stableId();
			LocatedFlowEdge edge= new LocatedFlowEdge(
					root.compilationUnitHandle(),
					sourceId,
					targetId,
					root.transferKind(),
					root.profile().identity().sourceStart(),
					root.profile().identity().sourceLength());
			if (edgeKeys.add(edgeKey(edge))) {
				edges.add(edge);
			}
		}

		ClosureStatus status= closureStatus(component, continuations, rejected);
		if (status == ClosureStatus.LOCAL_CLOSED) {
			diagnostics.removeIf(diagnostic ->
					diagnostic.kind() == DiagnosticKind.SCOPE_EXPANSION_REQUIRED);
		}
		return new ContainerFlowComponent(
				component.rootNodeId(),
				component.nodes(),
				edges,
				status,
				diagnostics);
	}

	private static Optional<FlowNode> findContinuationNode(
			List<FlowNode> nodes,
			ContinuationRoot root) {
		List<FlowNode> matches= nodes.stream()
				.filter(node -> node.bindingKey().equals(root.profile().identity().bindingKey()))
				.filter(node -> node.compilationUnitHandle().equals(root.compilationUnitHandle()))
				.toList();
		return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
	}

	private static Optional<ResolvedSearchTarget> findTarget(
			ResolvedContainerFlowSearchPlan plan,
			ContinuationRoot root) {
		return plan.targets().stream()
				.filter(target -> target.sourceNodeId().equals(root.boundaryNodeId()))
				.filter(target -> target.javaElementHandle().equals(root.exactTargetHandle()))
				.filter(target -> targetMatchesKind(target, root.kind()))
				.sorted(Comparator.comparing(target -> target.searchKind().ordinal()))
				.findFirst();
	}

	private static boolean targetMatchesKind(
			ResolvedSearchTarget target,
			ContinuationKind kind) {
		return switch (kind) {
			case FIELD -> target.searchKind() == SearchKind.FIELD_REFERENCES;
			case PARAMETER_DECLARATION ->
					target.searchKind() != SearchKind.METHOD_CALLERS
							&& target.signatureIndex() >= 0;
			case CALL_ARGUMENT -> target.searchKind() == SearchKind.METHOD_CALLERS
					&& target.signatureIndex() >= 0;
			case RETURN_EXPRESSION -> target.searchKind() != SearchKind.METHOD_CALLERS
					&& target.signatureIndex() < 0;
			case RETURN_CONSUMER -> target.searchKind() == SearchKind.METHOD_CALLERS
					&& target.signatureIndex() < 0;
		};
	}

	private static Optional<FlowNode> findBoundaryNode(
			List<FlowNode> nodes,
			Map<String, FlowNode> nodesById,
			ContinuationRoot root,
			ResolvedSearchTarget target,
			FlowNode continuationNode) {
		if (root.relationship() == Relationship.SAME_NODE) {
			return Optional.of(continuationNode);
		}
		FlowNode exactId= nodesById.get(root.boundaryNodeId());
		if (exactId != null && compatibleBoundary(exactId, target)) {
			return Optional.of(exactId);
		}
		List<FlowNode> matches= nodes.stream()
				.filter(node -> node.javaElementHandle().equals(target.javaElementHandle()))
				.filter(node -> compatibleBoundary(node, target))
				.toList();
		return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
	}

	private static boolean compatibleBoundary(
			FlowNode node,
			ResolvedSearchTarget target) {
		if (target.searchKind() == SearchKind.FIELD_REFERENCES) {
			return node.kind() == NodeKind.FIELD;
		}
		if (target.signatureIndex() >= 0) {
			return (node.kind() == NodeKind.PARAMETER
					|| node.kind() == NodeKind.EXTERNAL_PARAMETER)
					&& node.signatureIndex() == target.signatureIndex();
		}
		return node.kind() == NodeKind.RETURN_POSITION;
	}

	private static ClosureStatus closureStatus(
			ContainerFlowComponent component,
			ContainerFlowContinuationPlan continuations,
			boolean rejected) {
		if (component.closureStatus() == ClosureStatus.REJECTED || rejected) {
			return ClosureStatus.REJECTED;
		}
		if (component.closureStatus() == ClosureStatus.EXTERNAL_BOUNDARY) {
			return ClosureStatus.EXTERNAL_BOUNDARY;
		}
		boolean unresolvedSourceBoundary= component.nodes().stream()
				.anyMatch(node -> (node.kind() == NodeKind.PARAMETER
						|| node.kind() == NodeKind.RETURN_POSITION)
						&& !node.sourceResolved());
		return unresolvedSourceBoundary || !continuations.complete()
				? ClosureStatus.REQUIRES_SCOPE_EXPANSION
				: ClosureStatus.LOCAL_CLOSED;
	}

	private static void addDiagnostic(
			List<LocatedFlowDiagnostic> diagnostics,
			Set<String> keys,
			ContinuationRoot root,
			String message) {
		LocatedFlowDiagnostic diagnostic= new LocatedFlowDiagnostic(
				root.compilationUnitHandle(),
				DiagnosticKind.UNCLASSIFIED_FLOW,
				message,
				root.profile().identity().sourceStart(),
				root.profile().identity().sourceLength());
		if (keys.add(diagnosticKey(diagnostic))) {
			diagnostics.add(diagnostic);
		}
	}

	private static String edgeKey(LocatedFlowEdge edge) {
		return edge.compilationUnitHandle() + '|' + edge.sourceNodeId() + '|'
				+ edge.targetNodeId() + '|' + edge.kind() + '|' + edge.sourceStart();
	}

	private static String diagnosticKey(LocatedFlowDiagnostic diagnostic) {
		return diagnostic.compilationUnitHandle() + '|' + diagnostic.kind() + '|'
				+ diagnostic.message() + '|' + diagnostic.sourceStart();
	}
}
