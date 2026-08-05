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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.AutomationLevel;
import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractAssessment;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractProperty;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AliasingContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ElementDomain;
import org.sandbox.jdt.container.api.ContainerUsageProfile.EscapeLevel;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;

/** Infers the first manually-unique sequence to ordered-set contract. */
public final class UniqueSequenceContractInferrer {

	/** Returns an explainable report-only recommendation for a proven local profile. */
	public Optional<ContainerRecommendation> infer(ContainerUsageProfile profile) {
		Objects.requireNonNull(profile, "profile"); //$NON-NLS-1$
		if (!eligible(profile)) {
			return Optional.empty();
		}

		TargetContainerContract target= new TargetContainerContract(
				ContainerShape.SET,
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.REQUIRED,
				Mutability.MUTABLE,
				profile.nullContract(),
				"Use an encounter-ordered mutable set for a sequence whose insertions already suppress duplicates."); //$NON-NLS-1$

		return Optional.of(new ContainerRecommendation(
				profile,
				target,
				ContainerRuleRegistry.uniqueSequenceSet(),
				Confidence.HIGH,
				AutomationLevel.REPORT_ONLY,
				List.of(
						assessment(
								ContractProperty.ORDER,
								"LinkedHashSet preserves the observed encounter order."), //$NON-NLS-1$
						assessment(
								ContractProperty.UNIQUENESS,
								"Every insertion is guarded by a membership test for the same stable value."), //$NON-NLS-1$
						assessment(
								ContractProperty.MUTABILITY,
								"The target remains mutable during the same local use phase."), //$NON-NLS-1$
						assessment(
								ContractProperty.NULLS,
								"ArrayList and LinkedHashSet both permit one null element; an unknown application policy remains unknown."), //$NON-NLS-1$
						assessment(
								ContractProperty.ALIASING,
								"Every local use was classified and no alias or publication was found."), //$NON-NLS-1$
						assessment(
								ContractProperty.SIGNATURES,
								"The represented value is local, so no signature changes are required."), //$NON-NLS-1$
						assessment(
								ContractProperty.CONCURRENCY,
								"The value is proven thread-confined and is not captured.")))); //$NON-NLS-1$
	}

	private static boolean eligible(ContainerUsageProfile profile) {
		return profile.completeness() == AnalysisCompleteness.LOCAL_USAGE_COMPLETE
				&& profile.currentShape() == ContainerShape.LIST
				&& (profile.elementDomain() == ElementDomain.REFERENCE
						|| profile.elementDomain() == ElementDomain.ENUM)
				&& profile.access().append()
				&& profile.access().membershipQuery()
				&& !profile.access().hasPositionalSemantics()
				&& profile.orderRequirement() == OrderRequirement.ENCOUNTER
				&& profile.uniquenessRequirement() == UniquenessRequirement.REQUIRED
				&& profile.escapeLevel() == EscapeLevel.LOCAL
				&& profile.aliasingContract() == AliasingContract.NO_OBSERVED_ALIAS
				&& profile.concurrency().exposure() == ThreadExposure.THREAD_CONFINED;
	}

	private static ContractAssessment assessment(
			ContractProperty property,
			String explanation) {
		return new ContractAssessment(
				property, Preservation.PRESERVED, explanation);
	}
}
