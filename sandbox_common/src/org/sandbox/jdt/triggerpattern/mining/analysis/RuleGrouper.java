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

	/** Confidence boost per additional occurrence when aggregating grouped rules. */
	private static final double OCCURRENCE_BOOST_PER_INSTANCE = 0.02;

	/** Maximum total boost from multiple occurrences. */
	private static final double MAX_OCCURRENCE_BOOST = 0.1;

	/** Absolute upper bound for any confidence value. */
	private static final double MAX_CONFIDENCE = 1.0;

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

		Map<String, Integer> placeholderIndices = new LinkedHashMap<>();
		for (int i = 0; i < rule.placeholderNames().size(); i++) {
			String name = rule.placeholderNames().get(i);
			placeholderIndices.putIfAbsent(name, Integer.valueOf(i));
		}

		for (Map.Entry<String, Integer> entry : placeholderIndices.entrySet()) {
			String positional = "$_" + entry.getValue(); //$NON-NLS-1$
			src = src.replace(entry.getKey(), positional);
			repl = repl.replace(entry.getKey(), positional);
		}
		return src + " => " + repl + " :: " + rule.kind(); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private double aggregateConfidence(List<InferredRule> group) {
		double maxConfidence = group.stream()
				.mapToDouble(InferredRule::confidence).max().orElse(0.0);
		// Boost confidence slightly for each additional occurrence
		double boost = Math.min(MAX_OCCURRENCE_BOOST, OCCURRENCE_BOOST_PER_INSTANCE * (group.size() - 1));
		return Math.min(MAX_CONFIDENCE, maxConfidence + boost);
	}
}
