/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
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
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

/** Infers an explicit enum-membership set contract from a proven ordinal table. */
public final class EnumIndexedMembershipContractInferrer {

	/** Returns a report-only semantic set recommendation for a complete local table. */
	public Optional<ContainerRecommendation> infer(ContainerUsageProfile profile) {
		Objects.requireNonNull(profile, "profile"); //$NON-NLS-1$
		if (!eligible(profile)) {
			return Optional.empty();
		}

		TargetContainerContract target= new TargetContainerContract(
				ContainerShape.SET,
				OrderRequirement.NONE,
				UniquenessRequirement.REQUIRED,
				Mutability.MUTABLE,
				NullContract.REJECTED,
				"Represent enum membership explicitly instead of coupling storage to ordinal positions."); //$NON-NLS-1$

		return Optional.of(new ContainerRecommendation(
				profile,
				target,
				ContainerRuleRegistry.enumIndexedMembershipSet(),
				Confidence.HIGH,
				AutomationLevel.REPORT_ONLY,
				List.of(
						assessment(ContractProperty.ORDER, Preservation.PRESERVED,
								"Only membership is observed; neither numeric position nor encounter order is part of the proven contract."), //$NON-NLS-1$
						assessment(ContractProperty.UNIQUENESS, Preservation.PRESERVED,
								"Each enum constant owns exactly one boolean slot, which is equivalent to unique set membership."), //$NON-NLS-1$
						assessment(ContractProperty.MUTABILITY, Preservation.PRESERVED,
								"Literal true/false writes map to adding or removing the same enum member."), //$NON-NLS-1$
						assessment(ContractProperty.NULLS, Preservation.REQUIRES_PROOF,
								"A future rewrite must preserve the original null-key exception behavior of ordinal() for every membership query and update."), //$NON-NLS-1$
						assessment(ContractProperty.ALIASING, Preservation.PRESERVED,
								"The complete local proof found no alias, escape, identity observation or array publication."), //$NON-NLS-1$
						assessment(ContractProperty.SIGNATURES, Preservation.PRESERVED,
								"The membership table is a local variable, so no external declaration or signature changes are required."), //$NON-NLS-1$
						assessment(ContractProperty.CONCURRENCY, Preservation.PRESERVED,
								"The local table is proven thread-confined and is not captured.")))); //$NON-NLS-1$
	}

	private static boolean eligible(ContainerUsageProfile profile) {
		return profile.completeness() == AnalysisCompleteness.LOCAL_USAGE_COMPLETE
				&& profile.currentShape() == ContainerShape.ARRAY
				&& profile.elementDomain() == ElementDomain.PRIMITIVE
				&& profile.access().indexedRead()
				&& profile.access().indexedWrite()
				&& profile.access().membershipQuery()
				&& profile.orderRequirement() == OrderRequirement.NONE
				&& profile.uniquenessRequirement() == UniquenessRequirement.REQUIRED
				&& profile.escapeLevel() == EscapeLevel.LOCAL
				&& profile.aliasingContract() == AliasingContract.NO_OBSERVED_ALIAS
				&& profile.concurrency().exposure() == ThreadExposure.THREAD_CONFINED
				&& hasEvidence(profile, Kind.ENUM_CARDINALITY)
				&& hasEvidence(profile, Kind.ENUM_ORDINAL_INDEX)
				&& hasEvidence(profile, Kind.ENUM_MEMBERSHIP_QUERY)
				&& (hasEvidence(profile, Kind.ENUM_MEMBERSHIP_ENABLE)
						|| hasEvidence(profile, Kind.ENUM_MEMBERSHIP_DISABLE));
	}

	private static boolean hasEvidence(ContainerUsageProfile profile, Kind kind) {
		return profile.evidence().stream().anyMatch(evidence -> evidence.kind() == kind);
	}

	private static ContractAssessment assessment(
			ContractProperty property,
			Preservation preservation,
			String explanation) {
		return new ContractAssessment(property, preservation, explanation);
	}
}
