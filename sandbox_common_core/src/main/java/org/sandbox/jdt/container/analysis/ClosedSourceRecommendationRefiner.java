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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractAssessment;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractProperty;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;

/**
 * Replaces the provisional signature assessment after a complete caller-to-parameter
 * source closure has been proven.
 */
public final class ClosedSourceRecommendationRefiner {

	/**
	 * Returns a recommendation whose signature property reflects the coordinated
	 * source migration, or empty when the supplied component is not the supported
	 * closed direct-call topology.
	 */
	public Optional<ContainerRecommendation> refine(
			ContainerFlowComponent component,
			ContainerRecommendation recommendation) {
		Objects.requireNonNull(component, "component"); //$NON-NLS-1$
		Objects.requireNonNull(recommendation, "recommendation"); //$NON-NLS-1$
		if (!supported(component, recommendation)) {
			return Optional.empty();
		}

		Map<ContractProperty, ContractAssessment> assessments=
				new EnumMap<>(ContractProperty.class);
		for (ContractAssessment assessment : recommendation.assessments()) {
			ContractAssessment previous= assessments.putIfAbsent(
					assessment.property(), assessment);
			if (previous != null && !previous.equals(assessment)) {
				return Optional.empty();
			}
		}
		if (!preserved(assessments, ContractProperty.ALIASING)
				|| !preserved(assessments, ContractProperty.CONCURRENCY)) {
			return Optional.empty();
		}
		assessments.put(
				ContractProperty.SIGNATURES,
				new ContractAssessment(
						ContractProperty.SIGNATURES,
						Preservation.PRESERVED,
						"The local declaration, exact source parameter and all known callers form one closed atomic migration component.")); //$NON-NLS-1$

		List<ContractAssessment> ordered= new ArrayList<>(assessments.values());
		ordered.sort(Comparator.comparingInt(assessment -> assessment.property().ordinal()));
		return Optional.of(new ContainerRecommendation(
				recommendation.sourceProfile(),
				recommendation.targetContract(),
				recommendation.rule(),
				Confidence.HIGH,
				recommendation.automationLevel(),
				ordered));
	}

	private static boolean supported(
			ContainerFlowComponent component,
			ContainerRecommendation recommendation) {
		if (component.closureStatus() != ClosureStatus.LOCAL_CLOSED
				|| !component.diagnostics().isEmpty()
				|| component.nodes().size() != 2
				|| component.edges().size() != 1
				|| recommendation.sourceProfile().completeness()
						!= AnalysisCompleteness.FLOW_COMPLETE) {
			return false;
		}
		var edge= component.edges().get(0);
		return edge.kind() == EdgeKind.ARGUMENT_TO_PARAMETER
				&& component.node(edge.sourceNodeId())
						.filter(node -> node.kind() == NodeKind.LOCAL_VARIABLE)
						.filter(node -> node.bindingKey().equals(
								recommendation.sourceProfile().identity().bindingKey()))
						.isPresent()
				&& component.node(edge.targetNodeId())
						.filter(node -> node.kind() == NodeKind.PARAMETER)
						.isPresent();
	}

	private static boolean preserved(
			Map<ContractProperty, ContractAssessment> assessments,
			ContractProperty property) {
		ContractAssessment assessment= assessments.get(property);
		return assessment != null && assessment.preservation() == Preservation.PRESERVED;
	}
}
