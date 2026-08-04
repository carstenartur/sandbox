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
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;

/** Infers explainable, report-only target contracts from complete local profiles. */
public final class ContainerContractInferrer {

	/**
	 * Infers the first semantic migration family: dynamically appended reference arrays
	 * to mutable sequence contracts.
	 */
	public Optional<ContainerRecommendation> infer(ContainerUsageProfile profile) {
		Objects.requireNonNull(profile, "profile"); //$NON-NLS-1$
		if (!eligible(profile)) {
			return Optional.empty();
		}

		NullContract targetNullContract= profile.nullContract() == NullContract.UNKNOWN
				? NullContract.ALLOWED
				: profile.nullContract();
		TargetContainerContract target= new TargetContainerContract(
				ContainerShape.LIST,
				profile.orderRequirement(),
				profile.uniquenessRequirement(),
				Mutability.MUTABLE,
				targetNullContract,
				"Use a dynamically growing mutable sequence instead of repeatedly copying an array."); //$NON-NLS-1$

		List<ContractAssessment> assessments= new ArrayList<>();
		assessments.add(orderAssessment(profile));
		assessments.add(new ContractAssessment(
				ContractProperty.UNIQUENESS,
				Preservation.PRESERVED,
				"An array and a list both retain duplicate elements; any existing uniqueness guard remains in the surrounding code.")); //$NON-NLS-1$
		assessments.add(new ContractAssessment(
				ContractProperty.MUTABILITY,
				Preservation.PRESERVED,
				"The proposed list remains mutable during the same local construction and use phase.")); //$NON-NLS-1$
		assessments.add(new ContractAssessment(
				ContractProperty.NULLS,
				Preservation.PRESERVED,
				"Reference arrays and the proposed ArrayList representation both permit null elements; existing validation remains unchanged.")); //$NON-NLS-1$
		assessments.add(aliasingAssessment(profile));
		assessments.add(concurrencyAssessment(profile));
		assessments.add(signatureAssessment(profile));

		Confidence confidence= strictlyLocalProof(profile)
				? Confidence.HIGH
				: Confidence.MEDIUM;
		return Optional.of(new ContainerRecommendation(
				profile,
				target,
				ContainerRuleRegistry.arrayAppendSequence(),
				confidence,
				AutomationLevel.REPORT_ONLY,
				assessments));
	}

	private static boolean eligible(ContainerUsageProfile profile) {
		if (profile.completeness() != AnalysisCompleteness.LOCAL_USAGE_COMPLETE
				|| profile.currentShape() != ContainerShape.ARRAY
				|| profile.elementDomain() != ElementDomain.REFERENCE
						&& profile.elementDomain() != ElementDomain.ENUM
				|| !profile.access().append()
				|| profile.access().positionalInsert()
				|| profile.access().positionalRemove()) {
			return false;
		}
		return profile.orderRequirement() == OrderRequirement.ENCOUNTER
				|| profile.orderRequirement() == OrderRequirement.POSITIONAL;
	}

	private static ContractAssessment orderAssessment(ContainerUsageProfile profile) {
		if (profile.orderRequirement() == OrderRequirement.POSITIONAL) {
			return new ContractAssessment(
					ContractProperty.ORDER,
					Preservation.PRESERVED,
					"A list retains the observed positional and encounter-order contract."); //$NON-NLS-1$
		}
		return new ContractAssessment(
				ContractProperty.ORDER,
				Preservation.PRESERVED,
				"A list preserves the observed encounter order of appended elements."); //$NON-NLS-1$
	}

	private static ContractAssessment aliasingAssessment(ContainerUsageProfile profile) {
		if (profile.escapeLevel() == EscapeLevel.LOCAL
				&& profile.aliasingContract() == AliasingContract.NO_OBSERVED_ALIAS) {
			return new ContractAssessment(
					ContractProperty.ALIASING,
					Preservation.PRESERVED,
					"Every local use was classified and no alias, identity observation, or external publication was found."); //$NON-NLS-1$
		}
		return new ContractAssessment(
				ContractProperty.ALIASING,
				Preservation.REQUIRES_PROOF,
				"Project-wide flow must prove that replacing the array object does not change observable alias or identity behavior."); //$NON-NLS-1$
	}

	private static ContractAssessment concurrencyAssessment(ContainerUsageProfile profile) {
		if (profile.escapeLevel() == EscapeLevel.LOCAL
				&& profile.concurrency().exposure() == ThreadExposure.THREAD_CONFINED) {
			return new ContractAssessment(
					ContractProperty.CONCURRENCY,
					Preservation.PRESERVED,
					"All uses stay in the declaring method body and no lambda, nested type, method boundary, or publication path captures the value."); //$NON-NLS-1$
		}
		return new ContractAssessment(
				ContractProperty.CONCURRENCY,
				Preservation.REQUIRES_PROOF,
				"Thread exposure, synchronization and publication semantics must be closed before selecting a concrete collection implementation."); //$NON-NLS-1$
	}

	private static ContractAssessment signatureAssessment(ContainerUsageProfile profile) {
		if (profile.escapeLevel() == EscapeLevel.LOCAL) {
			return new ContractAssessment(
					ContractProperty.SIGNATURES,
					Preservation.PRESERVED,
					"The represented value is a local variable and no method, constructor, field, or override signature changes are required."); //$NON-NLS-1$
		}
		return new ContractAssessment(
				ContractProperty.SIGNATURES,
				Preservation.REQUIRES_PROOF,
				"Fields, parameters, return values, callers and override families must be migrated atomically before execution."); //$NON-NLS-1$
	}

	private static boolean strictlyLocalProof(ContainerUsageProfile profile) {
		return profile.escapeLevel() == EscapeLevel.LOCAL
				&& profile.aliasingContract() == AliasingContract.NO_OBSERVED_ALIAS
				&& profile.concurrency().exposure() == ThreadExposure.THREAD_CONFINED;
	}
}
