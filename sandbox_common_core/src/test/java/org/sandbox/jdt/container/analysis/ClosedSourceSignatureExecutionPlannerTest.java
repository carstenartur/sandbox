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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan;
import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchKind;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionStatus;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.AutomationLevel;
import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractAssessment;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractProperty;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerRuleDescriptor;
import org.sandbox.jdt.container.api.ContainerRuleDescriptor.RuleOwnership;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PlanningStatus;
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
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.ResolvedSearchTarget;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.TargetKind;
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;

class ClosedSourceSignatureExecutionPlannerTest {

	@Test
	void closedSourceParameterNeedsNoBridgeAndReachesAutomaticReadiness() {
		FlowNode parameter= new FlowNode(
				"parameter:consume:0", //$NON-NLS-1$
				NodeKind.PARAMETER,
				"parameter-binding", //$NON-NLS-1$
				"method-key", //$NON-NLS-1$
				"=project/src<test{Sample.java", //$NON-NLS-1$
				"method-handle", //$NON-NLS-1$
				0,
				true,
				10,
				6);
		ContainerFlowComponent component= new ContainerFlowComponent(
				parameter.stableId(),
				List.of(parameter),
				List.of(),
				ClosureStatus.LOCAL_CLOSED,
				List.of());
		ResolvedContainerFlowSearchPlan resolved= new ResolvedContainerFlowSearchPlan(
				List.of(new ResolvedSearchTarget(
						parameter.stableId(),
						SearchKind.METHOD_DECLARATION,
						TargetKind.METHOD,
						parameter.bindingKey(),
						parameter.ownerKey(),
						parameter.javaElementHandle(),
						0,
						"Rewrite the closed parameter declaration"))); //$NON-NLS-1$
		ContainerRecommendation recommendation= recommendation();

		ContainerSignatureMigrationPlan signatures=
				new ContainerSignatureAtomicityPlanner().planClosedSource(
						component, resolved, recommendation);
		ContainerBridgePolicyPlan bridges=
				new ContainerBridgePolicyPlanner().plan(signatures, recommendation);
		ContainerMigrationReadiness readiness=
				new ContainerMigrationReadinessPlanner().plan(
						component, recommendation, signatures, bridges);

		assertEquals(PlanningStatus.CLOSED_SOURCE_AUTOMATIC, signatures.status());
		assertEquals(1, signatures.groups().size());
		assertEquals(ContainerBridgePolicyPlan.PlanningStatus.NO_BRIDGE_NEEDED,
				bridges.status());
		assertEquals(ExecutionStatus.AUTOMATIC, readiness.status());
		assertTrue(readiness.blockers().isEmpty());
	}

	private static ContainerRecommendation recommendation() {
		TargetContainerContract target= new TargetContainerContract(
				ContainerShape.LIST,
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				Mutability.MUTABLE,
				NullContract.UNKNOWN,
				"Use a dynamic sequence contract."); //$NON-NLS-1$
		ContainerRuleDescriptor rule= new ContainerRuleDescriptor(
				"semantic.array.append.sequence", //$NON-NLS-1$
				ContainerShape.ARRAY,
				ContainerShape.LIST,
				RuleOwnership.NOVEL,
				"", //$NON-NLS-1$
				"The migration changes representation and signatures."); //$NON-NLS-1$
		return new ContainerRecommendation(
				sourceProfile(),
				target,
				rule,
				Confidence.HIGH,
				AutomationLevel.REPORT_ONLY,
				List.of(
						preserved(ContractProperty.ORDER),
						preserved(ContractProperty.UNIQUENESS),
						preserved(ContractProperty.MUTABILITY),
						preserved(ContractProperty.NULLS),
						preserved(ContractProperty.ALIASING),
						preserved(ContractProperty.CONCURRENCY),
						preserved(ContractProperty.SIGNATURES)));
	}

	private static ContainerUsageProfile sourceProfile() {
		return new ContainerUsageProfile(
				new ContainerIdentity("parameter-binding", "values", 10, 6), //$NON-NLS-1$ //$NON-NLS-2$
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
				AnalysisCompleteness.FLOW_COMPLETE,
				List.of());
	}

	private static ContractAssessment preserved(ContractProperty property) {
		return new ContractAssessment(
				property,
				Preservation.PRESERVED,
				property + " is preserved by the closed-source migration."); //$NON-NLS-1$
	}
}
