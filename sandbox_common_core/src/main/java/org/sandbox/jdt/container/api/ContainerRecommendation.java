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

import java.util.List;
import java.util.Objects;

/**
 * Explainable, immutable recommendation produced before rewrite planning.
 *
 * @param sourceProfile analyzed current contract
 * @param targetContract proposed semantic target
 * @param rule rule ownership and overlap information
 * @param confidence confidence supported by the current analysis scope
 * @param automationLevel permitted execution level
 * @param assessments preservation assessment by contract property
 */
public record ContainerRecommendation(
		ContainerUsageProfile sourceProfile,
		TargetContainerContract targetContract,
		ContainerRuleDescriptor rule,
		Confidence confidence,
		AutomationLevel automationLevel,
		List<ContractAssessment> assessments) {

	public ContainerRecommendation {
		Objects.requireNonNull(sourceProfile, "sourceProfile"); //$NON-NLS-1$
		Objects.requireNonNull(targetContract, "targetContract"); //$NON-NLS-1$
		Objects.requireNonNull(rule, "rule"); //$NON-NLS-1$
		Objects.requireNonNull(confidence, "confidence"); //$NON-NLS-1$
		Objects.requireNonNull(automationLevel, "automationLevel"); //$NON-NLS-1$
		assessments= List.copyOf(Objects.requireNonNull(assessments, "assessments")); //$NON-NLS-1$
		if (!rule.mayRecommend()) {
			throw new IllegalArgumentException("Duplicate rules cannot own recommendations"); //$NON-NLS-1$
		}
	}

	/** Returns whether source rewriting is currently permitted. */
	public boolean isExecutable() {
		return automationLevel != AutomationLevel.REPORT_ONLY;
	}

	/** One preservation statement for a semantic property. */
	public record ContractAssessment(
			ContractProperty property,
			Preservation preservation,
			String explanation) {

		public ContractAssessment {
			Objects.requireNonNull(property, "property"); //$NON-NLS-1$
			Objects.requireNonNull(preservation, "preservation"); //$NON-NLS-1$
			explanation= Objects.requireNonNull(explanation, "explanation").strip(); //$NON-NLS-1$
			if (explanation.isEmpty()) {
				throw new IllegalArgumentException("explanation must not be empty"); //$NON-NLS-1$
			}
		}
	}

	public enum Confidence {
		LOW,
		MEDIUM,
		HIGH
	}

	public enum AutomationLevel {
		REPORT_ONLY,
		INTERACTIVE,
		AUTOMATIC
	}

	public enum ContractProperty {
		ORDER,
		UNIQUENESS,
		MUTABILITY,
		NULLS,
		ALIASING,
		SIGNATURES,
		CONCURRENCY
	}

	public enum Preservation {
		PRESERVED,
		REQUIRES_PROOF,
		CHANGED,
		UNKNOWN
	}
}
