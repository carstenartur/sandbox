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
package org.sandbox.mining.gemini.report;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sandbox.mining.gemini.gemini.CommitEvaluation;
import org.sandbox.mining.gemini.gemini.CommitEvaluation.TrafficLight;

/**
 * Tracks statistical counts for the mining process.
 *
 * <p>Records counts of total processed commits, relevant/irrelevant commits,
 * duplicates, traffic light distribution, per-repository statistics,
 * and daily progress tracking.</p>
 */
public class StatisticsCollector {

	private int totalProcessed;
	private int relevant;
	private int irrelevant;
	private int duplicates;
	private int green;
	private int yellow;
	private int red;
	private final Map<String, Integer> irrelevantReasons = new LinkedHashMap<>();
	private final Map<String, RepoStatistics> perRepository = new LinkedHashMap<>();
	private final List<DailyProgress> dailyProgress = new ArrayList<>();

	/**
	 * Per-repository statistics.
	 */
	public static class RepoStatistics {
		private int totalProcessed;
		private int relevant;
		private int green;
		private int yellow;
		private int red;

		public void record(CommitEvaluation evaluation) {
			totalProcessed++;
			if (evaluation.relevant()) {
				relevant++;
			}
			switch (evaluation.trafficLight()) {
			case GREEN -> green++;
			case YELLOW -> yellow++;
			case RED -> red++;
			case NOT_APPLICABLE -> { /* no counter */ }
			}
		}

		public int getTotalProcessed() { return totalProcessed; }
		public int getRelevant() { return relevant; }
		public int getGreen() { return green; }
		public int getYellow() { return yellow; }
		public int getRed() { return red; }
	}

	/**
	 * Daily progress entry.
	 */
	public static class DailyProgress {
		private final String date;
		private int processed;
		private int relevant;

		public DailyProgress(String date) {
			this.date = date;
		}

		public String getDate() { return date; }
		public int getProcessed() { return processed; }
		public int getRelevant() { return relevant; }
	}

	/**
	 * Records a single evaluation in the statistics.
	 *
	 * @param evaluation the evaluation to record
	 */
	public void record(CommitEvaluation evaluation) {
		totalProcessed++;

		if (evaluation.relevant()) {
			relevant++;
		} else {
			irrelevant++;
			if (evaluation.irrelevantReason() != null) {
				irrelevantReasons.merge(evaluation.irrelevantReason(), 1, Integer::sum);
			}
		}

		if (evaluation.isDuplicate()) {
			duplicates++;
		}

		switch (evaluation.trafficLight()) {
		case GREEN -> green++;
		case YELLOW -> yellow++;
		case RED -> red++;
		case NOT_APPLICABLE -> { /* no counter */ }
		}

		// Per-repository tracking
		if (evaluation.repoUrl() != null) {
			perRepository.computeIfAbsent(evaluation.repoUrl(), k -> new RepoStatistics())
					.record(evaluation);
		}

		// Daily progress tracking
		String today = LocalDate.now().toString();
		DailyProgress todayProgress = dailyProgress.stream()
				.filter(d -> d.getDate().equals(today))
				.findFirst()
				.orElseGet(() -> {
					DailyProgress dp = new DailyProgress(today);
					dailyProgress.add(dp);
					return dp;
				});
		todayProgress.processed++;
		if (evaluation.relevant()) {
			todayProgress.relevant++;
		}
	}

	public int getTotalProcessed() {
		return totalProcessed;
	}

	public int getRelevant() {
		return relevant;
	}

	public int getIrrelevant() {
		return irrelevant;
	}

	public int getDuplicates() {
		return duplicates;
	}

	public int getGreen() {
		return green;
	}

	public int getYellow() {
		return yellow;
	}

	public int getRed() {
		return red;
	}

	public Map<String, Integer> getIrrelevantReasons() {
		return Map.copyOf(irrelevantReasons);
	}

	public Map<String, RepoStatistics> getPerRepository() {
		return Map.copyOf(perRepository);
	}

	public List<DailyProgress> getDailyProgress() {
		return List.copyOf(dailyProgress);
	}
}
