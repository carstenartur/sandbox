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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationDiagnostic;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationKind;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationRoot;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.Relationship;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
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

class ContainerFlowContinuationPlanTest {

	@Test
	void completePlanContainsImmutableContinuationRoots() {
		ContinuationRoot root= new ContinuationRoot(
				"parameter:method:0", //$NON-NLS-1$
				ContinuationKind.CALL_ARGUMENT,
				Relationship.ROOT_TO_BOUNDARY,
				EdgeKind.ARGUMENT_TO_PARAMETER,
				"Caller.java", //$NON-NLS-1$
				"method-handle", //$NON-NLS-1$
				profile("values")); //$NON-NLS-1$
		ContainerFlowContinuationPlan plan=
				new ContainerFlowContinuationPlan(List.of(root), List.of());

		assertTrue(plan.complete());
		assertThrows(UnsupportedOperationException.class,
				() -> plan.roots().clear());
	}

	@Test
	void diagnosticMakesPlanIncomplete() {
		ContinuationDiagnostic diagnostic= new ContinuationDiagnostic(
				DiagnosticKind.METHOD_REFERENCE,
				"Caller.java", //$NON-NLS-1$
				"method-handle", //$NON-NLS-1$
				"Method reference requires target-type analysis", //$NON-NLS-1$
				12,
				8);
		ContainerFlowContinuationPlan plan=
				new ContainerFlowContinuationPlan(List.of(), List.of(diagnostic));

		assertFalse(plan.complete());
	}

	@Test
	void validatesRelationshipAndTransferKind() {
		assertThrows(IllegalArgumentException.class,
				() -> new ContinuationRoot(
						"field:values", //$NON-NLS-1$
						ContinuationKind.FIELD,
						Relationship.SAME_NODE,
						EdgeKind.ASSIGNMENT,
						"Owner.java", //$NON-NLS-1$
						"field-handle", //$NON-NLS-1$
						profile("values"))); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class,
				() -> new ContinuationRoot(
						"return:method", //$NON-NLS-1$
						ContinuationKind.RETURN_CONSUMER,
						Relationship.BOUNDARY_TO_ROOT,
						null,
						"Caller.java", //$NON-NLS-1$
						"method-handle", //$NON-NLS-1$
						profile("result"))); //$NON-NLS-1$
	}

	@Test
	void rejectsDuplicateRoots() {
		ContinuationRoot root= new ContinuationRoot(
				"field:values", //$NON-NLS-1$
				ContinuationKind.FIELD,
				Relationship.SAME_NODE,
				null,
				"Owner.java", //$NON-NLS-1$
				"field-handle", //$NON-NLS-1$
				profile("values")); //$NON-NLS-1$

		assertThrows(IllegalArgumentException.class,
				() -> new ContainerFlowContinuationPlan(List.of(root, root), List.of()));
	}

	private static ContainerUsageProfile profile(String name) {
		return new ContainerUsageProfile(
				new ContainerIdentity("binding:" + name, name, 1, name.length()), //$NON-NLS-1$
				ContainerShape.ARRAY,
				ElementDomain.REFERENCE,
				new AccessProfile(false, false, false, false, false, false, false),
				OrderRequirement.UNKNOWN,
				UniquenessRequirement.UNKNOWN,
				MutationLifecycle.UNKNOWN,
				NullContract.UNKNOWN,
				AliasingContract.UNKNOWN,
				EscapeLevel.LOCAL,
				ConcurrencyProfile.unknown(),
				AnalysisCompleteness.LOCAL_SEED,
				List.of());
	}
}
