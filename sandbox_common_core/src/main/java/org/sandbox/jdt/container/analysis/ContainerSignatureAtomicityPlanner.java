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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchKind;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.BridgeFeasibility;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PlanningStatus;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PositionKind;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.SignatureAtomicityGroup;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.SignatureDiagnostic;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.SignatureMember;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.ResolvedSearchTarget;

/**
 * Derives report-only atomic method-signature groups from a closed container flow.
 *
 * <p>The planner does not claim semantic bridge safety. It records only the Java-level
 * coexistence constraint: an array parameter and a collection parameter may form
 * distinct overloads, while an array return and a collection return cannot coexist
 * under the same method name and parameter list.</p>
 */
public final class ContainerSignatureAtomicityPlanner {

	/** Builds an immutable signature plan for one closed flow recommendation. */
	public ContainerSignatureMigrationPlan plan(
			ContainerFlowComponent component,
			ResolvedContainerFlowSearchPlan resolvedPlan,
			ContainerRecommendation recommendation) {
		Objects.requireNonNull(component, "component"); //$NON-NLS-1$
		Objects.requireNonNull(resolvedPlan, "resolvedPlan"); //$NON-NLS-1$
		Objects.requireNonNull(recommendation, "recommendation"); //$NON-NLS-1$

		List<SignatureDiagnostic> diagnostics= new ArrayList<>();
		if (component.closureStatus() != ClosureStatus.LOCAL_CLOSED) {
			diagnostics.add(new SignatureDiagnostic(
					DiagnosticKind.FLOW_NOT_CLOSED,
					component.rootNodeId(),
					"", //$NON-NLS-1$
					"Signature planning requires a closed source flow component.")); //$NON-NLS-1$
			return new ContainerSignatureMigrationPlan(
					recommendation.targetContract(),
					List.of(),
					PlanningStatus.REJECTED,
					diagnostics);
		}

		Map<GroupKey, List<ResolvedSearchTarget>> targetsByGroup= new LinkedHashMap<>();
		for (ResolvedSearchTarget target : resolvedPlan.targets()) {
			if (!declarationTarget(target)) {
				continue;
			}
			GroupKey key= new GroupKey(target.sourceNodeId(), target.signatureIndex());
			targetsByGroup.computeIfAbsent(key, ignored -> new ArrayList<>()).add(target);
		}
		if (targetsByGroup.isEmpty()) {
			return new ContainerSignatureMigrationPlan(
					recommendation.targetContract(),
					List.of(),
					PlanningStatus.NO_SIGNATURE_CHANGE,
					List.of());
		}

		List<SignatureAtomicityGroup> groups= new ArrayList<>();
		for (Map.Entry<GroupKey, List<ResolvedSearchTarget>> entry : targetsByGroup.entrySet()) {
			GroupKey key= entry.getKey();
			Map<String, SignatureMember> membersByHandle= new LinkedHashMap<>();
			for (ResolvedSearchTarget target : entry.getValue()) {
				if (target.javaElementHandle().isBlank()) {
					diagnostics.add(new SignatureDiagnostic(
							DiagnosticKind.MISSING_METHOD_HANDLE,
							target.sourceNodeId(),
							"", //$NON-NLS-1$
							"An exact method declaration handle is required.")); //$NON-NLS-1$
					continue;
				}
				List<FlowNode> matches= matchingNodes(component, target);
				if (matches.isEmpty()) {
					diagnostics.add(new SignatureDiagnostic(
							DiagnosticKind.MISSING_SIGNATURE_NODE,
							target.sourceNodeId(),
							target.javaElementHandle(),
							"The resolved method has no matching signature node in the flow component.")); //$NON-NLS-1$
					continue;
				}
				if (matches.size() > 1) {
					diagnostics.add(new SignatureDiagnostic(
							DiagnosticKind.AMBIGUOUS_SIGNATURE_NODE,
							target.sourceNodeId(),
							target.javaElementHandle(),
							"Several flow nodes match the same method signature position.")); //$NON-NLS-1$
					continue;
				}
				FlowNode node= matches.get(0);
				membersByHandle.putIfAbsent(target.javaElementHandle(), new SignatureMember(
						target.javaElementHandle(),
						node.ownerKey(),
						node.compilationUnitHandle(),
						node.stableId()));
			}
			if (!membersByHandle.isEmpty()) {
				PositionKind position= key.signatureIndex() >= 0
						? PositionKind.PARAMETER : PositionKind.RETURN;
				BridgeFeasibility bridge= position == PositionKind.PARAMETER
						? BridgeFeasibility.OVERLOAD_POSSIBLE_POLICY_REQUIRED
						: BridgeFeasibility.SAME_NAME_RETURN_BRIDGE_IMPOSSIBLE;
				String explanation= position == PositionKind.PARAMETER
						? "Old and new parameter types can coexist as overloads, but any adapter must prove order, aliasing, mutation, null and duplicate semantics." //$NON-NLS-1$
						: "Java cannot retain the old and new signatures under the same name because methods cannot be overloaded solely by return type."; //$NON-NLS-1$
				List<SignatureMember> members= new ArrayList<>(membersByHandle.values());
				members.sort(Comparator.comparing(SignatureMember::javaElementHandle));
				groups.add(new SignatureAtomicityGroup(
						groupId(key),
						position,
						key.signatureIndex(),
						members,
						bridge,
						explanation));
			}
		}
		groups.sort(Comparator.comparing(SignatureAtomicityGroup::groupId));
		return new ContainerSignatureMigrationPlan(
				recommendation.targetContract(),
				groups,
				diagnostics.isEmpty() ? PlanningStatus.REPORT_ONLY : PlanningStatus.REJECTED,
				diagnostics);
	}

	private static boolean declarationTarget(ResolvedSearchTarget target) {
		return target.searchKind() == SearchKind.METHOD_DECLARATION
				|| target.searchKind() == SearchKind.METHOD_OVERRIDE_FAMILY;
	}

	private static List<FlowNode> matchingNodes(
			ContainerFlowComponent component,
			ResolvedSearchTarget target) {
		return component.nodes().stream()
				.filter(node -> node.javaElementHandle().equals(target.javaElementHandle()))
				.filter(node -> target.signatureIndex() >= 0
						? (node.kind() == NodeKind.PARAMETER
								|| node.kind() == NodeKind.EXTERNAL_PARAMETER)
								&& node.signatureIndex() == target.signatureIndex()
						: node.kind() == NodeKind.RETURN_POSITION)
				.toList();
	}

	private static String groupId(GroupKey key) {
		return key.sourceNodeId() + (key.signatureIndex() >= 0
				? ":parameter:" + key.signatureIndex() //$NON-NLS-1$
				: ":return"); //$NON-NLS-1$
	}

	private record GroupKey(String sourceNodeId, int signatureIndex) {
		private GroupKey {
			sourceNodeId= Objects.requireNonNull(sourceNodeId, "sourceNodeId"); //$NON-NLS-1$
		}
	}
}
