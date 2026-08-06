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
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionStatus;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan.EditKind;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.BridgeFeasibility;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PlanningStatus;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PositionKind;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.SignatureAtomicityGroup;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.SignatureMember;
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
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;
import org.sandbox.jdt.container.api.UsageEvidence;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

class ContainerParameterRewritePlannerTest {

	private static final String UNIT= "=project/src<test{Sample.java"; //$NON-NLS-1$
	private static final String METHOD= "method-handle"; //$NON-NLS-1$
	private static final String BINDING= "parameter-binding"; //$NON-NLS-1$

	private final ContainerParameterRewritePlanner planner=
			new ContainerParameterRewritePlanner();

	@Test
	void plansLengthAndEncounterIterationForOneClosedParameter() {
		Fixture fixture= fixture(profile(false));

		var result= planner.plan(
				fixture.component(),
				fixture.signaturePlan(),
				fixture.group(),
				fixture.member(),
				fixture.profile(),
				fixture.readiness());

		assertTrue(result.ready());
		var plan= result.plan().orElseThrow();
		assertEquals(UNIT, plan.compilationUnitHandle());
		assertEquals(METHOD, plan.methodJavaElementHandle());
		assertEquals(BINDING, plan.parameterBindingKey());
		assertEquals(0, plan.parameterIndex());
		assertEquals("java.util.List", plan.targetInterfaceType()); //$NON-NLS-1$
		assertEquals(1, plan.edits().stream()
				.filter(edit -> edit.kind() == EditKind.REPLACE_LENGTH_WITH_SIZE)
				.count());
		assertEquals(1, plan.edits().stream()
				.filter(edit -> edit.kind() == EditKind.VERIFY_ENCOUNTER_ITERATION)
				.count());
	}

	@Test
	void indexedParameterUseRejectsTheCompleteRewrite() {
		Fixture fixture= fixture(profile(true));

		var result= planner.plan(
				fixture.component(),
				fixture.signaturePlan(),
				fixture.group(),
				fixture.member(),
				fixture.profile(),
				fixture.readiness());

		assertFalse(result.ready());
		assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
				diagnostic.kind() == DiagnosticKind.UNSUPPORTED_PARAMETER_USAGE));
	}

	private static Fixture fixture(ContainerUsageProfile profile) {
		TargetContainerContract target= target();
		FlowNode node= new FlowNode(
				"parameter:consume:0", //$NON-NLS-1$
				NodeKind.PARAMETER,
				BINDING,
				"method-key", //$NON-NLS-1$
				UNIT,
				METHOD,
				0,
				true,
				profile.identity().sourceStart(),
				profile.identity().sourceLength());
		ContainerFlowComponent component= new ContainerFlowComponent(
				node.stableId(), List.of(node), List.of(), ClosureStatus.LOCAL_CLOSED, List.of());
		SignatureMember member= new SignatureMember(
				METHOD, node.ownerKey(), UNIT, node.stableId());
		SignatureAtomicityGroup group= new SignatureAtomicityGroup(
				"consume:parameter:0", //$NON-NLS-1$
				PositionKind.PARAMETER,
				0,
				List.of(member),
				BridgeFeasibility.OVERLOAD_POSSIBLE_POLICY_REQUIRED,
				"The closed source declaration is changed directly."); //$NON-NLS-1$
		ContainerSignatureMigrationPlan signaturePlan= new ContainerSignatureMigrationPlan(
				target,
				List.of(group),
				PlanningStatus.CLOSED_SOURCE_AUTOMATIC,
				List.of());
		ContainerMigrationReadiness readiness= new ContainerMigrationReadiness(
				target, ExecutionStatus.AUTOMATIC, List.of());
		return new Fixture(component, signaturePlan, group, member, profile, readiness);
	}

	private static ContainerUsageProfile profile(boolean indexedRead) {
		List<UsageEvidence> evidence= indexedRead
				? List.of(
						new UsageEvidence(Kind.REFERENCE_COMPONENT,
								"Reference component", 10, 6), //$NON-NLS-1$
						new UsageEvidence(Kind.INDEXED_READ,
								"Indexed read", 30, 9), //$NON-NLS-1$
						new UsageEvidence(Kind.LOCAL_USAGE_COMPLETE,
								"Complete parameter use", 10, 6)) //$NON-NLS-1$
				: List.of(
						new UsageEvidence(Kind.REFERENCE_COMPONENT,
								"Reference component", 10, 6), //$NON-NLS-1$
						new UsageEvidence(Kind.ARRAY_LENGTH_READ,
								"Length read", 30, 13), //$NON-NLS-1$
						new UsageEvidence(Kind.ENCOUNTER_ITERATION,
								"Encounter iteration", 50, 6), //$NON-NLS-1$
						new UsageEvidence(Kind.LOCAL_USAGE_COMPLETE,
								"Complete parameter use", 10, 6)); //$NON-NLS-1$
		return new ContainerUsageProfile(
				new ContainerIdentity(BINDING, "values", 10, 6), //$NON-NLS-1$
				ContainerShape.ARRAY,
				ElementDomain.REFERENCE,
				new AccessProfile(indexedRead, false, false, false, false, false, false),
				indexedRead ? OrderRequirement.POSITIONAL : OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				MutationLifecycle.CONTINUOUSLY_MUTABLE,
				NullContract.UNKNOWN,
				AliasingContract.NO_OBSERVED_ALIAS,
				EscapeLevel.METHOD_BOUNDARY,
				ConcurrencyProfile.unknown(),
				AnalysisCompleteness.LOCAL_USAGE_COMPLETE,
				evidence);
	}

	private static TargetContainerContract target() {
		return new TargetContainerContract(
				ContainerShape.LIST,
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				Mutability.MUTABLE,
				NullContract.UNKNOWN,
				"Use a dynamic sequence contract."); //$NON-NLS-1$
	}

	private record Fixture(
			ContainerFlowComponent component,
			ContainerSignatureMigrationPlan signaturePlan,
			SignatureAtomicityGroup group,
			SignatureMember member,
			ContainerUsageProfile profile,
			ContainerMigrationReadiness readiness) {
	}
}
