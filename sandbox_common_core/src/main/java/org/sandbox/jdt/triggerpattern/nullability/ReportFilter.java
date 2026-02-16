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
package org.sandbox.jdt.triggerpattern.nullability;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Filters scored match entries based on configurable criteria such as minimum
 * trivial-change score, severity levels, and null-status exclusions.
 *
 * @since 1.2.6
 */
public class ReportFilter {

	private int minTrivialChange;
	private Set<MatchSeverity> includedSeverities;
	private boolean excludeNonNull;

	/**
	 * Creates a default filter that passes all entries.
	 */
	public ReportFilter() {
		this.minTrivialChange = 0;
		this.includedSeverities = EnumSet.allOf(MatchSeverity.class);
		this.excludeNonNull = false;
	}

	/**
	 * Sets the minimum trivial-change score (inclusive).
	 *
	 * @param minTrivialChange minimum score (0-10)
	 * @return this filter for chaining
	 */
	public ReportFilter withMinTrivialChange(int minTrivialChange) {
		this.minTrivialChange = minTrivialChange;
		return this;
	}

	/**
	 * Sets the severity levels to include.
	 *
	 * @param severities the severity levels to include
	 * @return this filter for chaining
	 */
	public ReportFilter withSeverities(Set<MatchSeverity> severities) {
		this.includedSeverities = EnumSet.copyOf(severities);
		return this;
	}

	/**
	 * Excludes all entries with {@link NullStatus#NON_NULL}.
	 *
	 * @param exclude {@code true} to exclude non-null entries
	 * @return this filter for chaining
	 */
	public ReportFilter withExcludeNonNull(boolean exclude) {
		this.excludeNonNull = exclude;
		return this;
	}

	/**
	 * Applies this filter to a list of scored entries.
	 *
	 * @param entries the entries to filter
	 * @return a new list containing only entries that pass the filter
	 */
	public List<ScoredMatchEntry> apply(List<ScoredMatchEntry> entries) {
		List<ScoredMatchEntry> result = new ArrayList<>();
		for (ScoredMatchEntry entry : entries) {
			if (passes(entry)) {
				result.add(entry);
			}
		}
		return result;
	}

	/**
	 * Tests whether a single entry passes this filter.
	 *
	 * @param entry the entry to test
	 * @return {@code true} if the entry passes
	 */
	public boolean passes(ScoredMatchEntry entry) {
		MatchScore score = entry.score();
		if (score.trivialChange() < minTrivialChange) {
			return false;
		}
		if (!includedSeverities.contains(score.severity())) {
			return false;
		}
		if (excludeNonNull && score.nullStatus() == NullStatus.NON_NULL) {
			return false;
		}
		return true;
	}
}
