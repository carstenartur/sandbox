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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowComponent.LocatedFlowDiagnostic;
import org.sandbox.jdt.container.api.ContainerFlowComponent.LocatedFlowEdge;
import org.sandbox.jdt.container.api.ContainerFlowGraph;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowDiagnostic;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowEdge;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;

/**
 * Assembles compilation-unit-local flow fragments into one canonical component.
 *
 * <p>Signature boundary nodes and their later source declarations use different local
 * binding keys. They are therefore coalesced by semantic signature identity rather
 * than by fragment-local node identifier.</p>
 */
public final class ContainerFlowComponentAssembler {

	/** Assembles fragments in deterministic input order. */
	public ContainerFlowComponent assemble(List<ContainerFlowGraph> fragments) {
		Objects.requireNonNull(fragments, "fragments"); //$NON-NLS-1$
		if (fragments.isEmpty()) {
			throw new IllegalArgumentException("At least one flow fragment is required"); //$NON-NLS-1$
		}

		Map<String, MutableNode> nodes= new LinkedHashMap<>();
		List<LocatedFlowEdge> edges= new ArrayList<>();
		Set<String> edgeKeys= new LinkedHashSet<>();
		List<LocatedFlowDiagnostic> diagnostics= new ArrayList<>();
		Set<String> diagnosticKeys= new LinkedHashSet<>();
		ClosureStatus status= ClosureStatus.LOCAL_CLOSED;
		String componentRoot= null;

		for (int fragmentIndex= 0; fragmentIndex < fragments.size(); fragmentIndex++) {
			ContainerFlowGraph fragment= Objects.requireNonNull(
					fragments.get(fragmentIndex), "fragment"); //$NON-NLS-1$
			String fragmentUnit= compilationUnitHandle(fragment);
			Map<String, String> remappedIds= new LinkedHashMap<>();

			for (FlowNode node : fragment.nodes()) {
				String canonicalId= canonicalId(node);
				remappedIds.put(node.stableId(), canonicalId);
				MutableNode merged= nodes.computeIfAbsent(
						canonicalId, ignored -> new MutableNode(canonicalId, node));
				if (merged.merge(node)) {
					status= ClosureStatus.REJECTED;
					addDiagnostic(
							diagnostics,
							diagnosticKeys,
							fragmentUnit,
							DiagnosticKind.UNCLASSIFIED_FLOW,
							"Conflicting declarations map to the same canonical flow node: " //$NON-NLS-1$
									+ canonicalId,
							node.sourceStart(),
							node.sourceLength());
				}
			}

			if (fragmentIndex == 0) {
				componentRoot= remappedIds.get(fragment.rootNodeId());
			}

			for (FlowEdge edge : fragment.edges()) {
				String source= remappedIds.get(edge.sourceNodeId());
				String target= remappedIds.get(edge.targetNodeId());
				if (source == null || target == null) {
					status= ClosureStatus.REJECTED;
					addDiagnostic(
							diagnostics,
							diagnosticKeys,
							fragmentUnit,
							DiagnosticKind.UNCLASSIFIED_FLOW,
							"A fragment edge cannot be mapped to canonical component nodes.", //$NON-NLS-1$
							edge.sourceStart(),
							edge.sourceLength());
					continue;
				}
				String edgeUnit= edgeCompilationUnit(fragment, edge, fragmentUnit);
				LocatedFlowEdge located= new LocatedFlowEdge(
						edgeUnit,
						source,
						target,
						edge.kind(),
						edge.sourceStart(),
						edge.sourceLength());
				String key= edgeKey(located);
				if (edgeKeys.add(key)) {
					edges.add(located);
				}
			}

			for (FlowDiagnostic diagnostic : fragment.diagnostics()) {
				addDiagnostic(
						diagnostics,
						diagnosticKeys,
						fragmentUnit,
						diagnostic.kind(),
						diagnostic.message(),
						diagnostic.sourceStart(),
						diagnostic.sourceLength());
			}
			status= moreSevere(status, fragment.closureStatus());
		}

		List<FlowNode> canonicalNodes= nodes.values().stream()
				.map(MutableNode::toFlowNode)
				.toList();
		for (FlowNode node : canonicalNodes) {
			if (node.kind() == NodeKind.EXTERNAL_PARAMETER) {
				status= moreSevere(status, ClosureStatus.EXTERNAL_BOUNDARY);
			} else if ((node.kind() == NodeKind.PARAMETER
					|| node.kind() == NodeKind.RETURN_POSITION)
					&& !node.sourceResolved()) {
				status= moreSevere(status, ClosureStatus.REQUIRES_SCOPE_EXPANSION);
			}
		}

		if (componentRoot == null || !nodes.containsKey(componentRoot)) {
			throw new IllegalArgumentException("The first fragment root cannot be canonicalized"); //$NON-NLS-1$
		}
		return new ContainerFlowComponent(
				componentRoot,
				canonicalNodes,
				edges,
				status,
				diagnostics);
	}

	private static String canonicalId(FlowNode node) {
		return switch (node.kind()) {
			case PARAMETER, EXTERNAL_PARAMETER ->
					canonicalParameterId(node);
			case RETURN_POSITION -> node.ownerKey().isBlank()
					? "return-local:" + node.stableId() //$NON-NLS-1$
					: "return:" + node.ownerKey(); //$NON-NLS-1$
			case FIELD -> "field:" + preferredIdentity(node); //$NON-NLS-1$
			case LOCAL_VARIABLE -> "local:" + preferredIdentity(node); //$NON-NLS-1$
			case UNKNOWN_BOUNDARY -> "unknown:" + node.stableId(); //$NON-NLS-1$
		};
	}

	private static String canonicalParameterId(FlowNode node) {
		if (node.ownerKey().isBlank() || node.signatureIndex() < 0) {
			return "parameter-local:" + node.stableId(); //$NON-NLS-1$
		}
		return "parameter:" + node.ownerKey() + ':' + node.signatureIndex(); //$NON-NLS-1$
	}

	private static String preferredIdentity(FlowNode node) {
		if (!node.javaElementHandle().isBlank()) {
			return node.javaElementHandle();
		}
		if (!node.bindingKey().isBlank()) {
			return node.bindingKey();
		}
		return node.compilationUnitHandle() + ':' + node.stableId();
	}

	private static String compilationUnitHandle(ContainerFlowGraph fragment) {
		FlowNode root= fragment.node(fragment.rootNodeId()).orElseThrow();
		if (!root.compilationUnitHandle().isBlank()) {
			return root.compilationUnitHandle();
		}
		return fragment.nodes().stream()
				.map(FlowNode::compilationUnitHandle)
				.filter(handle -> !handle.isBlank())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"A multi-file flow fragment requires a compilation-unit handle")); //$NON-NLS-1$
	}

	private static String edgeCompilationUnit(
			ContainerFlowGraph fragment,
			FlowEdge edge,
			String fallback) {
		return fragment.node(edge.sourceNodeId())
				.map(FlowNode::compilationUnitHandle)
				.filter(handle -> !handle.isBlank())
				.orElse(fallback);
	}

	private static String edgeKey(LocatedFlowEdge edge) {
		return edge.compilationUnitHandle() + '|' + edge.sourceNodeId() + '|'
				+ edge.targetNodeId() + '|' + edge.kind() + '|' + edge.sourceStart();
	}

	private static void addDiagnostic(
			List<LocatedFlowDiagnostic> diagnostics,
			Set<String> keys,
			String compilationUnitHandle,
			DiagnosticKind kind,
			String message,
			int sourceStart,
			int sourceLength) {
		LocatedFlowDiagnostic diagnostic= new LocatedFlowDiagnostic(
				compilationUnitHandle,
				kind,
				message,
				sourceStart,
				sourceLength);
		String key= compilationUnitHandle + '|' + kind + '|' + message + '|' + sourceStart;
		if (keys.add(key)) {
			diagnostics.add(diagnostic);
		}
	}

	private static ClosureStatus moreSevere(ClosureStatus left, ClosureStatus right) {
		return rank(right) > rank(left) ? right : left;
	}

	private static int rank(ClosureStatus status) {
		return switch (status) {
			case LOCAL_CLOSED -> 0;
			case REQUIRES_SCOPE_EXPANSION -> 1;
			case EXTERNAL_BOUNDARY -> 2;
			case REJECTED -> 3;
		};
	}

	private static final class MutableNode {

		private final String canonicalId;
		private NodeKind kind;
		private String bindingKey;
		private String ownerKey;
		private String compilationUnitHandle;
		private String javaElementHandle;
		private int signatureIndex;
		private boolean sourceResolved;
		private int sourceStart;
		private int sourceLength;

		MutableNode(String canonicalId, FlowNode initial) {
			this.canonicalId= canonicalId;
			kind= initial.kind();
			bindingKey= initial.bindingKey();
			ownerKey= initial.ownerKey();
			compilationUnitHandle= initial.compilationUnitHandle();
			javaElementHandle= initial.javaElementHandle();
			signatureIndex= initial.signatureIndex();
			sourceResolved= initial.sourceResolved();
			sourceStart= initial.sourceStart();
			sourceLength= initial.sourceLength();
		}

		boolean merge(FlowNode incoming) {
			boolean conflict= false;
			NodeKind mergedKind= mergeKind(kind, incoming.kind());
			if (mergedKind == null) {
				conflict= true;
			} else {
				kind= mergedKind;
			}
			TextMerge binding= mergeText(bindingKey, incoming.bindingKey());
			TextMerge owner= mergeText(ownerKey, incoming.ownerKey());
			TextMerge element= mergeText(javaElementHandle, incoming.javaElementHandle());
			conflict|= binding.conflict() || owner.conflict() || element.conflict();
			bindingKey= binding.value();
			ownerKey= owner.value();
			javaElementHandle= element.value();

			if (signatureIndex < 0) {
				signatureIndex= incoming.signatureIndex();
			} else if (incoming.signatureIndex() >= 0
					&& signatureIndex != incoming.signatureIndex()) {
				conflict= true;
			}
			if (!sourceResolved && incoming.sourceResolved()) {
				compilationUnitHandle= incoming.compilationUnitHandle();
				sourceStart= incoming.sourceStart();
				sourceLength= incoming.sourceLength();
			}
			sourceResolved|= incoming.sourceResolved();
			return conflict;
		}

		FlowNode toFlowNode() {
			return new FlowNode(
					canonicalId,
					kind,
					bindingKey,
					ownerKey,
					compilationUnitHandle,
					javaElementHandle,
					signatureIndex,
					sourceResolved,
					sourceStart,
					sourceLength);
		}

		private static NodeKind mergeKind(NodeKind current, NodeKind incoming) {
			if (current == incoming) {
				return current;
			}
			if ((current == NodeKind.PARAMETER && incoming == NodeKind.EXTERNAL_PARAMETER)
					|| (current == NodeKind.EXTERNAL_PARAMETER && incoming == NodeKind.PARAMETER)) {
				return NodeKind.EXTERNAL_PARAMETER;
			}
			return null;
		}

		private static TextMerge mergeText(String current, String incoming) {
			if (current.isBlank()) {
				return new TextMerge(incoming, false);
			}
			if (incoming.isBlank() || current.equals(incoming)) {
				return new TextMerge(current, false);
			}
			return new TextMerge(current, true);
		}
	}

	private record TextMerge(String value, boolean conflict) {
	}
}
