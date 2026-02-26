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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks how often AI-inferred rules actually match in a project.
 *
 * <p>This class maintains per-rule match counts and provides suggestions
 * based on usage:</p>
 * <ul>
 *   <li><b>0 matches</b> &rarr; suggest removing the rule</li>
 *   <li><b>Many matches</b> (above {@link #promotionThreshold}) &rarr;
 *       suggest promoting from "ai-inferred" to "manual"</li>
 * </ul>
 *
 * <p>This class is Eclipse-independent and can be used from CLI tools or
 * plugin code alike. Thread safety is provided by {@link ConcurrentHashMap}
 * and {@link AtomicInteger}.</p>
 *
 * @since 1.3.2
 */
public final class RuleUsageTracker {

	/** Default threshold above which a rule should be promoted. */
	private static final int DEFAULT_PROMOTION_THRESHOLD = 5;

	private final Map<String, AtomicInteger> matchCounts = new ConcurrentHashMap<>();
	private int promotionThreshold = DEFAULT_PROMOTION_THRESHOLD;

	/**
	 * Creates a new rule usage tracker with the default promotion threshold.
	 */
	public RuleUsageTracker() {
		// default
	}

	/**
	 * Records a match for the given rule ID.
	 *
	 * @param ruleId the hint file or rule ID that matched
	 */
	public void recordMatch(String ruleId) {
		matchCounts.computeIfAbsent(ruleId, k -> new AtomicInteger(0)).incrementAndGet();
	}

	/**
	 * Records multiple matches for the given rule ID.
	 *
	 * @param ruleId the hint file or rule ID that matched
	 * @param count  the number of matches to record
	 */
	public void recordMatches(String ruleId, int count) {
		if (count > 0) {
			matchCounts.computeIfAbsent(ruleId, k -> new AtomicInteger(0)).addAndGet(count);
		}
	}

	/**
	 * Returns the number of matches recorded for the given rule ID.
	 *
	 * @param ruleId the hint file or rule ID
	 * @return the match count, or 0 if no matches have been recorded
	 */
	public int getMatchCount(String ruleId) {
		AtomicInteger count = matchCounts.get(ruleId);
		return count != null ? count.get() : 0;
	}

	/**
	 * Returns all tracked rule IDs and their match counts.
	 *
	 * @return unmodifiable map of rule ID to match count
	 */
	public Map<String, Integer> getAllMatchCounts() {
		Map<String, Integer> result = new ConcurrentHashMap<>();
		for (Map.Entry<String, AtomicInteger> entry : matchCounts.entrySet()) {
			result.put(entry.getKey(), entry.getValue().get());
		}
		return Collections.unmodifiableMap(result);
	}

	/**
	 * Returns the rule IDs that have zero matches (candidates for removal).
	 *
	 * <p>Only considers rules that have been registered via
	 * {@link #trackRule(String)} or had at least one previous recording
	 * (which was then reset).</p>
	 *
	 * @param inferredRuleIds the set of known AI-inferred rule IDs to check
	 * @return list of rule IDs with zero matches
	 */
	public List<String> getRulesWithNoMatches(List<String> inferredRuleIds) {
		List<String> noMatches = new ArrayList<>();
		for (String ruleId : inferredRuleIds) {
			if (getMatchCount(ruleId) == 0) {
				noMatches.add(ruleId);
			}
		}
		return noMatches;
	}

	/**
	 * Returns the rule IDs whose match count exceeds the
	 * {@link #getPromotionThreshold() promotion threshold}
	 * (candidates for promotion to manual rules).
	 *
	 * @param inferredRuleIds the set of known AI-inferred rule IDs to check
	 * @return list of rule IDs exceeding the promotion threshold
	 */
	public List<String> getRulesForPromotion(List<String> inferredRuleIds) {
		List<String> candidates = new ArrayList<>();
		for (String ruleId : inferredRuleIds) {
			if (getMatchCount(ruleId) >= promotionThreshold) {
				candidates.add(ruleId);
			}
		}
		return candidates;
	}

	/**
	 * Returns the current promotion threshold.
	 *
	 * @return the minimum number of matches required for promotion suggestion
	 */
	public int getPromotionThreshold() {
		return promotionThreshold;
	}

	/**
	 * Sets the promotion threshold.
	 *
	 * @param threshold the minimum number of matches required for promotion
	 *                  suggestion (must be &gt; 0)
	 */
	public void setPromotionThreshold(int threshold) {
		if (threshold > 0) {
			this.promotionThreshold = threshold;
		}
	}

	/**
	 * Registers a rule for tracking without recording any matches yet.
	 *
	 * <p>This is useful when loading inferred rules so they appear in
	 * {@link #getRulesWithNoMatches(List)} even if no scan has been performed.</p>
	 *
	 * @param ruleId the hint file or rule ID
	 */
	public void trackRule(String ruleId) {
		matchCounts.putIfAbsent(ruleId, new AtomicInteger(0));
	}

	/**
	 * Resets all tracked match counts.
	 */
	public void reset() {
		matchCounts.clear();
	}

	/**
	 * Suggestion type for a tracked rule.
	 */
	public enum Suggestion {
		/** Rule has zero matches and should be considered for removal. */
		REMOVE,
		/** Rule exceeds the promotion threshold and should be promoted to manual. */
		PROMOTE,
		/** Rule has matches but below the threshold &mdash; keep as-is. */
		KEEP
	}

	/**
	 * Returns the suggestion for a given rule based on its match count.
	 *
	 * @param ruleId the hint file or rule ID
	 * @return the suggestion for this rule
	 */
	public Suggestion getSuggestion(String ruleId) {
		int count = getMatchCount(ruleId);
		if (count == 0) {
			return Suggestion.REMOVE;
		}
		if (count >= promotionThreshold) {
			return Suggestion.PROMOTE;
		}
		return Suggestion.KEEP;
	}
}
