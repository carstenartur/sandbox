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
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ElementDomain;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;

/**
 * Produces explainable semantic target contracts without creating source edits.
 */
public final class ContainerContractInferrer {

	/**
	 * Infers the first supported target contract.
	 *
	 * <p>Local completeness is sufficient for reporting, but deliberately insufficient
	 * for interactive or automatic execution. Project-wide flow, aliases, signatures
	 * and concurrency still require the planner described by the multi-file roadmap.</p>
	 */
	public Optional<ContainerRecommendation> infer(ContainerUsageProfile profile) {
		Objects.requireNonNull(profile, "profile"); //$NON-NLS-1$
		if (!isAppendArraySequence(profile)) {
			return Optional.empty();
		}

		TargetContainerContract target= new TargetContainerContract(
				ContainerShape.LIST,
				profile.orderRequirement(),
				profile.uniquenessRequirement(),
				Mutability.MUTABLE,
				profile.nullContract(),
				rationale(profile));

		return Optional.of(new ContainerRecommendation(
				profile,
				target,
				ContainerRuleRegistry.arrayAppendSequence(),
				Confidence.MEDIUM,
				AutomationLevel.REPORT_ONLY,
				assessments(profile)));
	}

	private static boolean isAppendArraySequence(ContainerUsageProfile profile) {
		return profile.currentShape() == ContainerShape.ARRAY
				&& profile.completeness() == AnalysisCompleteness.LOCAL_USAGE_COMPLETE
				&& profile.access().append()
				&& profile.elementDomain() != ElementDomain.PRIMITIVE;
	}

	private static String rationale(ContainerUsageProfile profile) {
		return switch (profile.orderRequirement()) {
			case POSITIONAL ->
				"The growing reference array is used as a mutable positional sequence."; //$NON-NLS-1$
			case ENCOUNTER ->
				"The growing reference array is traversed as an ordered mutable sequence."; //$NON-NLS-1$
			case SORTED ->
				"The growing reference array carries a sorted sequence contract that a later planner must preserve."; //$NON-NLS-1$
			case NONE ->
				"The growing reference array is used as a mutable sequence without an observed order requirement."; //$NON-NLS-1$
			case UNKNOWN ->
				"The growing reference array behaves as a mutable sequence, while project-wide order remains unresolved."; //$NON-NLS-1$
		};
	}

	private static List<ContractAssessment> assessments(ContainerUsageProfile profile) {
		return List.of(
				assessment(
						ContractProperty.ORDER,
						profile.orderRequirement() == OrderRequirement.UNKNOWN
								? Preservation.REQUIRES_PROOF : Preservation.PRESERVED,
						profile.orderRequirement() == OrderRequirement.UNKNOWN
								? "A list preserves sequence order, but the required project-wide order is not known yet." //$NON-NLS-1$
								: "A list can preserve the observed array order contract."), //$NON-NLS-1$
				assessment(
						ContractProperty.UNIQUENESS,
						profile.uniquenessRequirement() == UniquenessRequirement.DUPLICATES_ALLOWED
								? Preservation.PRESERVED : Preservation.REQUIRES_PROOF,
						profile.uniquenessRequirement() == UniquenessRequirement.DUPLICATES_ALLOWED
								? "Arrays and lists both allow duplicate elements." //$NON-NLS-1$
								: "Any external or manually enforced uniqueness rule still needs a flow proof."), //$NON-NLS-1$
				assessment(
						ContractProperty.MUTABILITY,
						Preservation.PRESERVED,
						"A mutable list can preserve the observed build and update operations."), //$NON-NLS-1$
				assessment(
						ContractProperty.NULLS,
						profile.nullContract() == NullContract.UNKNOWN
								? Preservation.REQUIRES_PROOF : Preservation.PRESERVED,
						profile.nullContract() == NullContract.UNKNOWN
								? "Null insertion and observation have not yet been classified." //$NON-NLS-1$
								: "The later implementation must select matching null behavior."), //$NON-NLS-1$
				assessment(
						ContractProperty.ALIASING,
						Preservation.REQUIRES_PROOF,
						"Local analysis found no alias, but fields, callers and returned values are not closed yet."), //$NON-NLS-1$
				assessment(
						ContractProperty.SIGNATURES,
						Preservation.REQUIRES_PROOF,
						"Parameters, returns, overrides and callers require a project-wide signature plan."), //$NON-NLS-1$
				assessment(
						ContractProperty.CONCURRENCY,
						Preservation.REQUIRES_PROOF,
						"Thread exposure and compound operations require the concurrency-specific analysis.")); //$NON-NLS-1$
	}

	private static ContractAssessment assessment(
			ContractProperty property,
			Preservation preservation,
			String explanation) {
		return new ContractAssessment(property, preservation, explanation);
	}
}
