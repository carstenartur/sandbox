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
package org.eclipse.jgit.storage.hibernate.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.storage.hibernate.entity.JavaBlobIndex;

/**
 * Utility for combining results from multiple search sources using Reciprocal
 * Rank Fusion (RRF).
 * <p>
 * RRF assigns scores based on rank position rather than raw scores, making it
 * effective for combining results from heterogeneous sources (e.g., full-text
 * search and vector similarity search) that produce scores on different
 * scales.
 * </p>
 * <p>
 * The RRF formula is: {@code score(d) = Σ 1 / (k + rank(d))} where {@code k}
 * is a constant (default 60) and the sum is over all result lists.
 * </p>
 *
 * @see <a href="https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf">
 *      Reciprocal Rank Fusion outperforms Condorcet and individual Rank
 *      Learning Methods (Cormack et al., 2009)</a>
 */
public class RankFusionUtil {

	/** Default RRF constant k (from the original paper). */
	private static final int DEFAULT_K = 60;

	private RankFusionUtil() {
		// utility class
	}

	/**
	 * Combine two result lists using Reciprocal Rank Fusion.
	 * <p>
	 * Results appearing in both lists receive a higher fused score. The
	 * returned list is ordered by descending RRF score and truncated to
	 * {@code topK} results.
	 * </p>
	 *
	 * @param semanticResults
	 *            results from semantic (vector) search, ordered by relevance
	 * @param fulltextResults
	 *            results from full-text search, ordered by relevance
	 * @param topK
	 *            maximum number of results to return
	 * @return fused results ordered by combined RRF score
	 */
	@SuppressWarnings("boxing")
	public static List<JavaBlobIndex> reciprocalRankFusion(
			List<JavaBlobIndex> semanticResults,
			List<JavaBlobIndex> fulltextResults, int topK) {
		Map<Long, ScoredEntry> scoreMap = new LinkedHashMap<>();

		// Score semantic results
		for (int rank = 0; rank < semanticResults.size(); rank++) {
			JavaBlobIndex entry = semanticResults.get(rank);
			Long id = entry.getId();
			if (id == null) {
				continue;
			}
			double rrfScore = 1.0 / (DEFAULT_K + rank + 1);
			scoreMap.computeIfAbsent(id, k -> new ScoredEntry(entry))
					.addScore(rrfScore);
		}

		// Score full-text results
		for (int rank = 0; rank < fulltextResults.size(); rank++) {
			JavaBlobIndex entry = fulltextResults.get(rank);
			Long id = entry.getId();
			if (id == null) {
				continue;
			}
			double rrfScore = 1.0 / (DEFAULT_K + rank + 1);
			scoreMap.computeIfAbsent(id, k -> new ScoredEntry(entry))
					.addScore(rrfScore);
		}

		// Sort by combined score and return top K
		List<ScoredEntry> sorted = new ArrayList<>(scoreMap.values());
		sorted.sort(Comparator
				.comparingDouble(ScoredEntry::getScore).reversed());

		List<JavaBlobIndex> result = new ArrayList<>();
		for (int i = 0; i < Math.min(topK, sorted.size()); i++) {
			result.add(sorted.get(i).getEntry());
		}
		return result;
	}

	/**
	 * Internal holder for an entry and its accumulated RRF score.
	 */
	private static class ScoredEntry {
		private final JavaBlobIndex entry;

		private double score;

		ScoredEntry(JavaBlobIndex entry) {
			this.entry = entry;
		}

		void addScore(double additionalScore) {
			this.score += additionalScore;
		}

		double getScore() {
			return score;
		}

		JavaBlobIndex getEntry() {
			return entry;
		}
	}
}
