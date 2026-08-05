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

import java.util.List;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractProperty;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AccessProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AliasingContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AtomicityRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ContainerIdentity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ElementDomain;
import org.sandbox.jdt.container.api.ContainerUsageProfile.EscapeLevel;
import org.sandbox.jdt.container.api.ContainerUsageProfile.IterationSemantics;
import org.sandbox.jdt.container.api.ContainerUsageProfile.MutationLifecycle;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.SynchronizationKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.WorkloadShape;

class ContainerContractInferrerNullContractTest {

	@Test
	void unknownNullPolicyRemainsUnknownWhileRepresentationCapabilityIsPreserved() {
		ContainerUsageProfile profile= new ContainerUsageProfile(
				new ContainerIdentity("Lsample;.values", "values", 0, 6), //$NON-NLS-1$ //$NON-NLS-2$
				ContainerShape.ARRAY,
				ElementDomain.REFERENCE,
				new AccessProfile(false, true, true, false, false, false, false),
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				MutationLifecycle.CONTINUOUSLY_MUTABLE,
				NullContract.UNKNOWN,
				AliasingContract.NO_OBSERVED_ALIAS,
				EscapeLevel.LOCAL,
				new ConcurrencyProfile(
						ThreadExposure.THREAD_CONFINED,
						SynchronizationKind.NONE,
						IterationSemantics.LIVE,
						AtomicityRequirement.INDIVIDUAL_OPERATIONS,
						WorkloadShape.WRITE_MOSTLY),
				AnalysisCompleteness.LOCAL_USAGE_COMPLETE,
				List.of());

		ContainerRecommendation recommendation= new ContainerContractInferrer()
				.infer(profile)
				.orElseThrow();

		assertEquals(NullContract.UNKNOWN,
				recommendation.targetContract().nullContract());
		assertEquals(Preservation.PRESERVED,
				preservation(recommendation, ContractProperty.NULLS));
	}

	private static Preservation preservation(
			ContainerRecommendation recommendation,
			ContractProperty property) {
		return recommendation.assessments().stream()
				.filter(assessment -> assessment.property() == property)
				.findFirst()
				.orElseThrow()
				.preservation();
	}
}
