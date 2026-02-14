/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.mining.analysis;

import java.util.List;

/**
 * A group of similar {@link InferredRule} instances that represent the same
 * refactoring pattern detected across multiple occurrences.
 *
 * @param generalizedRule     the most general form of the rule
 * @param instances           the individual rules that were grouped
 * @param occurrenceCount     total number of occurrences
 * @param aggregatedConfidence combined confidence (higher when more instances agree)
 * @since 1.2.6
 */
public record RuleGroup(
		InferredRule generalizedRule,
		List<InferredRule> instances,
		int occurrenceCount,
		double aggregatedConfidence) {
}
