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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups similar {@link InferredRule} instances that represent the same
 * refactoring pattern detected across multiple occurrences.
 *
 * <p>Two rules are considered similar when they share the same source and
 * replacement patterns (after normalizing placeholder names).</p>
 *
 * @since 1.2.6
 */
public class RuleGrouper {

	/**
	 * Groups a list of inferred rules by their normalized pattern signature.
	 *
	 * @param rules the rules to group
	 * @return a list of {@link RuleGroup} instances
	 */
	public List<RuleGroup> groupSimilar(List<InferredRule> rules) {
		Map<String, List<InferredRule>> groups = new LinkedHashMap<>();

		for (InferredRule rule : rules) {
			String key = normalizeKey(rule);
			groups.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
		}

		List<RuleGroup> result = new ArrayList<>();
		for (List<InferredRule> group : groups.values()) {
			InferredRule representative = group.get(0);
			double aggregated = aggregateConfidence(group);
			result.add(new RuleGroup(representative, List.copyOf(group),
					group.size(), aggregated));
		}
		return result;
	}

	private String normalizeKey(InferredRule rule) {
		// Normalize by replacing concrete placeholder names with positional markers
		String src = rule.sourcePattern();
		String repl = rule.replacementPattern();
		for (String name : rule.placeholderNames()) {
			String positional = "$_" + rule.placeholderNames().indexOf(name); //$NON-NLS-1$
			src = src.replace(name, positional);
			repl = repl.replace(name, positional);
		}
		return src + " => " + repl + " :: " + rule.kind(); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private double aggregateConfidence(List<InferredRule> group) {
		double maxConfidence = group.stream()
				.mapToDouble(InferredRule::confidence).max().orElse(0.0);
		// Boost confidence slightly for each additional occurrence
		double boost = Math.min(0.1, 0.02 * (group.size() - 1));
		return Math.min(1.0, maxConfidence + boost);
	}
}
