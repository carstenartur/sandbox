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

import java.util.Objects;

/**
 * Machine-readable ownership and overlap information for one container rule.
 *
 * @param ruleId stable rule identifier
 * @param source source representation
 * @param target target representation
 * @param ownership overlap classification
 * @param existingTransformation existing owner when applicable
 * @param rationale reason for the classification
 */
public record ContainerRuleDescriptor(
		String ruleId,
		ContainerShape source,
		ContainerShape target,
		RuleOwnership ownership,
		String existingTransformation,
		String rationale) {

	public ContainerRuleDescriptor {
		ruleId= requiredText(ruleId, "ruleId"); //$NON-NLS-1$
		Objects.requireNonNull(source, "source"); //$NON-NLS-1$
		Objects.requireNonNull(target, "target"); //$NON-NLS-1$
		Objects.requireNonNull(ownership, "ownership"); //$NON-NLS-1$
		existingTransformation= existingTransformation == null
				? "" : existingTransformation.strip(); //$NON-NLS-1$
		rationale= requiredText(rationale, "rationale"); //$NON-NLS-1$
		if (ownership == RuleOwnership.DUPLICATE && existingTransformation.isEmpty()) {
			throw new IllegalArgumentException(
					"A duplicate rule must name the existing transformation"); //$NON-NLS-1$
		}
	}

	/** Returns whether this rule may own a new semantic recommendation. */
	public boolean mayRecommend() {
		return ownership != RuleOwnership.DUPLICATE;
	}

	private static String requiredText(String value, String fieldName) {
		String text= Objects.requireNonNull(value, fieldName).strip();
		if (text.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must not be empty"); //$NON-NLS-1$
		}
		return text;
	}

	public enum RuleOwnership {
		DUPLICATE,
		COMPLEMENT,
		NOVEL
	}
}
