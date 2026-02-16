/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.mining.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates mining results from scanning repositories.
 */
public class MiningReport {

	/**
	 * A single match found during scanning.
	 */
	public record MatchEntry(String repoName, String hintFile, String ruleName, String filePath, int line,
			String matchedCode, String suggestedReplacement) {
	}

	private final List<MatchEntry> matches = new ArrayList<>();
	private final Map<String, Integer> fileCounts = new LinkedHashMap<>();

	/**
	 * Adds a match entry to the report.
	 */
	public void addMatch(String repoName, String hintFile, String ruleName, String filePath, int line,
			String matchedCode, String suggestedReplacement) {
		matches.add(new MatchEntry(repoName, hintFile, ruleName, filePath, line, matchedCode, suggestedReplacement));
	}

	/**
	 * Records the number of files scanned for a repository.
	 */
	public void addFileCount(String repoName, int count) {
		fileCounts.merge(repoName, count, Integer::sum);
	}

	/**
	 * Merges another report into this one.
	 */
	public void merge(MiningReport other) {
		matches.addAll(other.matches);
		other.fileCounts.forEach((k, v) -> fileCounts.merge(k, v, Integer::sum));
	}

	public List<MatchEntry> getMatches() {
		return matches;
	}

	public Map<String, Integer> getFileCounts() {
		return fileCounts;
	}

	/**
	 * Returns matches grouped by repository name.
	 */
	public Map<String, List<MatchEntry>> getMatchesByRepo() {
		Map<String, List<MatchEntry>> byRepo = new LinkedHashMap<>();
		for (MatchEntry entry : matches) {
			byRepo.computeIfAbsent(entry.repoName(), k -> new ArrayList<>()).add(entry);
		}
		return byRepo;
	}

	/**
	 * Returns the number of distinct rules that matched.
	 */
	public long getDistinctRuleCount(String repoName) {
		return matches.stream().filter(m -> m.repoName().equals(repoName)).map(MatchEntry::ruleName).distinct().count();
	}

	/**
	 * Returns true if the report has any matches.
	 */
	public boolean hasMatches() {
		return !matches.isEmpty();
	}
}
