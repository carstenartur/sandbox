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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowComponent.LocatedFlowEdge;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AliasingContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AtomicityRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.EscapeLevel;
import org.sandbox.jdt.container.api.ContainerUsageProfile.IterationSemantics;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.SynchronizationKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ContainerUsageProfile.WorkloadShape;
import org.sandbox.jdt.container.api.UsageEvidence;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

/**
 * Reclassifies exact method-argument escapes after the multi-file flow is closed.
 *
 * <p>The first refinement accepts only one local source and source-resolved parameter
 * nodes joined by {@link EdgeKind#ARGUMENT_TO_PARAMETER}. Each parameter must also
 * have a complete, rejection-free usage profile. Assignments, fields, returns,
 * captures and every unmatched escape remain rejected.</p>
 */
public final class ClosedFlowArrayUsageRefiner {

	private static final Set<Kind> REJECTION_EVIDENCE= EnumSet.of(
			Kind.UNSUPPORTED_CONTINUATION,
			Kind.CAPTURED_USAGE,
			Kind.UNSAFE_ESCAPE,
			Kind.ARRAY_IDENTITY,
			Kind.UNCLASSIFIED_USAGE,
			Kind.UNRESOLVED_BINDING,
			Kind.REJECTION_BOUNDARY);

	/** Refines one rejected local profile from exact closed-flow evidence. */
	public ContainerUsageProfile refine(
			String compilationUnitHandle,
			ContainerUsageProfile localProfile,
			ContainerFlowComponent component,
			List<ContainerUsageProfile> parameterProfiles) {
		String unitHandle= requiredText(
				compilationUnitHandle, "compilationUnitHandle"); //$NON-NLS-1$
		Objects.requireNonNull(localProfile, "localProfile"); //$NON-NLS-1$
		Objects.requireNonNull(component, "component"); //$NON-NLS-1$
		parameterProfiles= List.copyOf(
				Objects.requireNonNull(parameterProfiles, "parameterProfiles")); //$NON-NLS-1$
		if (localProfile.currentShape() != ContainerShape.ARRAY) {
			throw new IllegalArgumentException(
					"ClosedFlowArrayUsageRefiner requires an array profile"); //$NON-NLS-1$
		}

		List<UsageEvidence> evidence= new ArrayList<>(localProfile.evidence());
		List<FlowNode> sourceNodes= component.nodes().stream()
				.filter(node -> node.compilationUnitHandle().equals(unitHandle))
				.filter(node -> node.bindingKey().equals(
						localProfile.identity().bindingKey()))
				.toList();
		if (sourceNodes.size() != 1) {
			return rejected(localProfile, evidence,
					"Closed flow does not contain one exact local source node"); //$NON-NLS-1$
		}

		FlowNode sourceNode= sourceNodes.get(0);
		if (!eligibleComponent(component, sourceNode, parameterProfiles)) {
			return rejected(localProfile, evidence,
					"Closed flow or parameter usage evidence is incomplete"); //$NON-NLS-1$
		}

		Set<SourceRange> allowedTransfers= HashSet.newHashSet(component.edges().size());
		for (LocatedFlowEdge edge : component.outgoing(sourceNode.stableId())) {
			if (edge.kind() != EdgeKind.ARGUMENT_TO_PARAMETER
					|| !edge.compilationUnitHandle().equals(unitHandle)) {
				return rejected(localProfile, evidence,
						"Closed flow contains an unsupported transfer kind"); //$NON-NLS-1$
			}
			allowedTransfers.add(new SourceRange(edge.sourceStart(), edge.sourceLength()));
		}
		if (allowedTransfers.isEmpty()) {
			return rejected(localProfile, evidence,
					"No closed argument-to-parameter transfer was found"); //$NON-NLS-1$
		}

		Set<SourceRange> discharged= HashSet.newHashSet(allowedTransfers.size());
		List<UsageEvidence> refined= new ArrayList<>(evidence.size() + 1);
		for (UsageEvidence item : evidence) {
			SourceRange range= new SourceRange(item.sourceStart(), item.sourceLength());
			if (item.kind() == Kind.UNSAFE_ESCAPE && allowedTransfers.contains(range)) {
				discharged.add(range);
				refined.add(new UsageEvidence(
						Kind.FLOW_CONTINUATION_ROOT,
						"Method argument transfer is covered by the closed source flow", //$NON-NLS-1$
						item.sourceStart(), item.sourceLength()));
			} else if (item.kind() != Kind.LOCAL_USAGE_COMPLETE) {
				refined.add(item);
			}
		}
		if (!discharged.equals(allowedTransfers)
				|| refined.stream().anyMatch(item -> REJECTION_EVIDENCE.contains(item.kind()))) {
			return rejected(localProfile, refined,
					"Not every rejected escape is explained by the closed source flow"); //$NON-NLS-1$
		}

		refined.add(new UsageEvidence(
				Kind.LOCAL_USAGE_COMPLETE,
				"Every local operation and closed-flow transfer was classified", //$NON-NLS-1$
				localProfile.identity().sourceStart(),
				localProfile.identity().sourceLength()));
		refined.sort(Comparator.comparingInt(UsageEvidence::sourceStart)
				.thenComparing(item -> item.kind().ordinal()));
		return new ContainerUsageProfile(
				localProfile.identity(),
				localProfile.currentShape(),
				localProfile.elementDomain(),
				localProfile.access(),
				aggregateOrder(localProfile, parameterProfiles),
				localProfile.uniquenessRequirement(),
				localProfile.mutationLifecycle(),
				localProfile.nullContract(),
				AliasingContract.NO_OBSERVED_ALIAS,
				EscapeLevel.METHOD_BOUNDARY,
				threadConfined(),
				AnalysisCompleteness.FLOW_COMPLETE,
				refined);
	}

	private static boolean eligibleComponent(
			ContainerFlowComponent component,
			FlowNode sourceNode,
			List<ContainerUsageProfile> parameterProfiles) {
		if (component.closureStatus() != ClosureStatus.LOCAL_CLOSED
				|| !component.diagnostics().isEmpty()
				|| sourceNode.kind() != NodeKind.LOCAL_VARIABLE
				|| !sourceNode.sourceResolved()
				|| component.nodes().stream().filter(node ->
						node.kind() == NodeKind.LOCAL_VARIABLE).count() != 1
				|| component.nodes().stream().anyMatch(node -> !node.sourceResolved()
						|| node.kind() != NodeKind.LOCAL_VARIABLE
								&& node.kind() != NodeKind.PARAMETER)
				|| component.edges().stream().anyMatch(edge ->
						edge.kind() != EdgeKind.ARGUMENT_TO_PARAMETER
								|| !edge.sourceNodeId().equals(sourceNode.stableId()))) {
			return false;
		}
		List<FlowNode> parameters= component.nodes().stream()
				.filter(node -> node.kind() == NodeKind.PARAMETER)
				.toList();
		if (parameters.isEmpty() || parameters.size() != parameterProfiles.size()) {
			return false;
		}
		for (FlowNode parameter : parameters) {
			List<ContainerUsageProfile> matches= parameterProfiles.stream()
					.filter(profile -> profile.identity().bindingKey().equals(
							parameter.bindingKey()))
					.toList();
			if (matches.size() != 1 || !safeParameterProfile(matches.get(0))) {
				return false;
			}
		}
		return component.edges().stream().allMatch(edge -> component.node(edge.targetNodeId())
				.map(node -> node.kind() == NodeKind.PARAMETER)
				.orElse(false));
	}

	private static boolean safeParameterProfile(ContainerUsageProfile profile) {
		return profile.currentShape() == ContainerShape.ARRAY
				&& profile.escapeLevel() == EscapeLevel.METHOD_BOUNDARY
				&& profile.aliasingContract() == AliasingContract.NO_OBSERVED_ALIAS
				&& (profile.completeness() == AnalysisCompleteness.LOCAL_USAGE_COMPLETE
						|| profile.completeness() == AnalysisCompleteness.FLOW_COMPLETE)
				&& profile.evidence().stream().noneMatch(evidence ->
						REJECTION_EVIDENCE.contains(evidence.kind()));
	}

	private static OrderRequirement aggregateOrder(
			ContainerUsageProfile localProfile,
			List<ContainerUsageProfile> parameterProfiles) {
		boolean positional= false;
		boolean sorted= false;
		boolean encounter= false;
		boolean none= false;
		boolean unknown= false;
		List<ContainerUsageProfile> profiles= new ArrayList<>(parameterProfiles.size() + 1);
		profiles.add(localProfile);
		profiles.addAll(parameterProfiles);
		for (ContainerUsageProfile profile : profiles) {
			switch (profile.orderRequirement()) {
				case POSITIONAL -> positional= true;
				case SORTED -> sorted= true;
				case ENCOUNTER -> encounter= true;
				case NONE -> none= true;
				case UNKNOWN -> unknown= true;
			}
		}
		if (positional) {
			return OrderRequirement.POSITIONAL;
		}
		if (sorted) {
			return OrderRequirement.SORTED;
		}
		if (encounter) {
			return OrderRequirement.ENCOUNTER;
		}
		return none && !unknown ? OrderRequirement.NONE : OrderRequirement.UNKNOWN;
	}

	private static ContainerUsageProfile rejected(
			ContainerUsageProfile source,
			List<UsageEvidence> evidence,
			String message) {
		List<UsageEvidence> rejected= new ArrayList<>(evidence.size() + 1);
		rejected.addAll(evidence);
		rejected.add(new UsageEvidence(
				Kind.REJECTION_BOUNDARY,
				message,
				source.identity().sourceStart(),
				source.identity().sourceLength()));
		rejected.sort(Comparator.comparingInt(UsageEvidence::sourceStart)
				.thenComparing(item -> item.kind().ordinal()));
		return new ContainerUsageProfile(
				source.identity(),
				source.currentShape(),
				source.elementDomain(),
				source.access(),
				source.orderRequirement(),
				source.uniquenessRequirement(),
				source.mutationLifecycle(),
				source.nullContract(),
				AliasingContract.UNKNOWN,
				source.escapeLevel(),
				ConcurrencyProfile.unknown(),
				AnalysisCompleteness.REJECTED,
				rejected);
	}

	private static ConcurrencyProfile threadConfined() {
		return new ConcurrencyProfile(
				ThreadExposure.THREAD_CONFINED,
				SynchronizationKind.NONE,
				IterationSemantics.LIVE,
				AtomicityRequirement.INDIVIDUAL_OPERATIONS,
				WorkloadShape.UNKNOWN);
	}

	private static String requiredText(String value, String fieldName) {
		String text= Objects.requireNonNull(value, fieldName).strip();
		if (text.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must not be empty"); //$NON-NLS-1$
		}
		return text;
	}

	private record SourceRange(int start, int length) {
		private SourceRange {
			if (start < 0 || length < 0) {
				throw new IllegalArgumentException("Source range must not be negative"); //$NON-NLS-1$
			}
		}
	}
}
