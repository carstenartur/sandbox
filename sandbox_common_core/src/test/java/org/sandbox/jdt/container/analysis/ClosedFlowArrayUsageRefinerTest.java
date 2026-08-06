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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowComponent.LocatedFlowEdge;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractProperty;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
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
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.UsageEvidence;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

class ClosedFlowArrayUsageRefinerTest {

	private static final String UNIT= "=project/src<test{Sample.java"; //$NON-NLS-1$
	private static final int TRANSFER_START= 80;
	private static final int TRANSFER_LENGTH= 6;

	private final ClosedFlowArrayUsageRefiner refiner=
			new ClosedFlowArrayUsageRefiner();

	@Test
	void dischargesOnlyTheExactClosedArgumentTransfer() {
		ContainerUsageProfile refined= refiner.refine(
				UNIT,
				rejectedProfile(),
				component(TRANSFER_START, TRANSFER_LENGTH),
				List.of(parameterProfile()));

		assertEquals(AnalysisCompleteness.FLOW_COMPLETE, refined.completeness());
		assertEquals(AliasingContract.NO_OBSERVED_ALIAS, refined.aliasingContract());
		assertEquals(EscapeLevel.METHOD_BOUNDARY, refined.escapeLevel());
		assertEquals(ThreadExposure.THREAD_CONFINED,
				refined.concurrency().exposure());
		assertFalse(refined.evidence().stream().anyMatch(evidence ->
				evidence.kind() == Kind.UNSAFE_ESCAPE));
		assertTrue(refined.evidence().stream().anyMatch(evidence ->
				evidence.kind() == Kind.FLOW_CONTINUATION_ROOT));

		var recommendation= new ContainerContractInferrer().infer(refined).orElseThrow();
		assertEquals(Preservation.PRESERVED,
				assessment(recommendation, ContractProperty.ALIASING));
		assertEquals(Preservation.PRESERVED,
				assessment(recommendation, ContractProperty.CONCURRENCY));
	}

	@Test
	void mismatchedTransferRangeRemainsRejected() {
		ContainerUsageProfile refined= refiner.refine(
				UNIT,
				rejectedProfile(),
				component(TRANSFER_START + 1, TRANSFER_LENGTH),
				List.of(parameterProfile()));

		assertEquals(AnalysisCompleteness.REJECTED, refined.completeness());
		assertTrue(refined.evidence().stream().anyMatch(evidence ->
				evidence.kind() == Kind.REJECTION_BOUNDARY));
	}

	@Test
	void rejectedParameterUsageCannotBeOverriddenByAClosedFlow() {
		ContainerUsageProfile refined= refiner.refine(
				UNIT,
				rejectedProfile(),
				component(TRANSFER_START, TRANSFER_LENGTH),
				List.of(capturedParameterProfile()));

		assertEquals(AnalysisCompleteness.REJECTED, refined.completeness());
		assertEquals(AliasingContract.UNKNOWN, refined.aliasingContract());
		assertTrue(refined.evidence().stream().anyMatch(evidence ->
				evidence.kind() == Kind.REJECTION_BOUNDARY));
	}

	private static Preservation assessment(
			org.sandbox.jdt.container.api.ContainerRecommendation recommendation,
			ContractProperty property) {
		return recommendation.assessments().stream()
				.filter(assessment -> assessment.property() == property)
				.findFirst()
				.orElseThrow()
				.preservation();
	}

	private static ContainerUsageProfile rejectedProfile() {
		return new ContainerUsageProfile(
				new ContainerIdentity("local-binding", "values", 10, 6), //$NON-NLS-1$ //$NON-NLS-2$
				ContainerShape.ARRAY,
				ElementDomain.REFERENCE,
				new AccessProfile(false, false, true, false, false, false, false),
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				MutationLifecycle.CONTINUOUSLY_MUTABLE,
				NullContract.UNKNOWN,
				AliasingContract.UNKNOWN,
				EscapeLevel.LOCAL,
				ConcurrencyProfile.unknown(),
				AnalysisCompleteness.REJECTED,
				List.of(
						new UsageEvidence(Kind.REFERENCE_COMPONENT,
								"Reference component", 10, 6), //$NON-NLS-1$
						new UsageEvidence(Kind.ARRAY_GROWTH,
								"Array grows by one", 30, 20), //$NON-NLS-1$
						new UsageEvidence(Kind.APPEND_WRITE,
								"Tail slot receives the new value", 55, 20), //$NON-NLS-1$
						new UsageEvidence(Kind.UNSAFE_ESCAPE,
								"Array is passed to another method", //$NON-NLS-1$
								TRANSFER_START, TRANSFER_LENGTH)));
	}

	private static ContainerUsageProfile parameterProfile() {
		return new ContainerUsageProfile(
				new ContainerIdentity("parameter-binding", "values", 100, 6), //$NON-NLS-1$ //$NON-NLS-2$
				ContainerShape.ARRAY,
				ElementDomain.REFERENCE,
				new AccessProfile(false, false, false, false, false, false, false),
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				MutationLifecycle.CONTINUOUSLY_MUTABLE,
				NullContract.UNKNOWN,
				AliasingContract.NO_OBSERVED_ALIAS,
				EscapeLevel.METHOD_BOUNDARY,
				ConcurrencyProfile.unknown(),
				AnalysisCompleteness.LOCAL_USAGE_COMPLETE,
				List.of(
						new UsageEvidence(Kind.FLOW_CONTINUATION_ROOT,
								"Exact parameter continuation", 100, 6), //$NON-NLS-1$
						new UsageEvidence(Kind.ARRAY_LENGTH_READ,
								"Array length is observed", 120, 13), //$NON-NLS-1$
						new UsageEvidence(Kind.ENCOUNTER_ITERATION,
								"Array is traversed in encounter order", 145, 6), //$NON-NLS-1$
						new UsageEvidence(Kind.LOCAL_USAGE_COMPLETE,
								"Every parameter use was classified", 100, 6))); //$NON-NLS-1$
	}

	private static ContainerUsageProfile capturedParameterProfile() {
		return new ContainerUsageProfile(
				new ContainerIdentity("parameter-binding", "values", 100, 6), //$NON-NLS-1$ //$NON-NLS-2$
				ContainerShape.ARRAY,
				ElementDomain.REFERENCE,
				new AccessProfile(false, false, false, false, false, false, false),
				OrderRequirement.UNKNOWN,
				UniquenessRequirement.UNKNOWN,
				MutationLifecycle.UNKNOWN,
				NullContract.UNKNOWN,
				AliasingContract.UNKNOWN,
				EscapeLevel.METHOD_BOUNDARY,
				ConcurrencyProfile.unknown(),
				AnalysisCompleteness.REJECTED,
				List.of(new UsageEvidence(
						Kind.CAPTURED_USAGE,
						"Parameter is captured by a lambda", 120, 6))); //$NON-NLS-1$
	}

	private static ContainerFlowComponent component(int start, int length) {
		FlowNode local= new FlowNode(
				"local:values", //$NON-NLS-1$
				NodeKind.LOCAL_VARIABLE,
				"local-binding", //$NON-NLS-1$
				"caller-key", //$NON-NLS-1$
				UNIT,
				"local-handle", //$NON-NLS-1$
				-1,
				true,
				10,
				6);
		FlowNode parameter= new FlowNode(
				"parameter:consume:0", //$NON-NLS-1$
				NodeKind.PARAMETER,
				"parameter-binding", //$NON-NLS-1$
				"consume-key", //$NON-NLS-1$
				UNIT,
				"consume-handle", //$NON-NLS-1$
				0,
				true,
				100,
				6);
		LocatedFlowEdge edge= new LocatedFlowEdge(
				UNIT,
				local.stableId(),
				parameter.stableId(),
				EdgeKind.ARGUMENT_TO_PARAMETER,
				start,
				length);
		return new ContainerFlowComponent(
				local.stableId(),
				List.of(local, parameter),
				List.of(edge),
				ClosureStatus.LOCAL_CLOSED,
				List.of());
	}
}
