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
package org.sandbox.mining.core.report;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;

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
	private RunMetadata runMetadata;
	private TimeWindow timeWindow;

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

		// Daily progress tracking — use the evaluation's date if available, else today
		String today = (evaluation.evaluatedAt() != null)
				? LocalDate.ofInstant(evaluation.evaluatedAt(), ZoneOffset.UTC).toString()
				: LocalDate.now().toString();
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

	public RunMetadata getRunMetadata() {
		return runMetadata;
	}

	public void setRunMetadata(RunMetadata runMetadata) {
		this.runMetadata = runMetadata;
	}

	/**
	 * Rebuilds a {@link StatisticsCollector} from a list of evaluations.
	 *
	 * @param evaluations the evaluations to rebuild from
	 * @return a new {@link StatisticsCollector} reflecting the given evaluations
	 */
	public static StatisticsCollector rebuildFrom(List<CommitEvaluation> evaluations) {
		StatisticsCollector stats = new StatisticsCollector();
		for (CommitEvaluation eval : evaluations) {
			stats.record(eval);
		}
		return stats;
	}

	/**
	 * Convenience method to record run metadata from raw values.
	 */
	public void recordRunMetadata(String startedAt, String completedAt, long durationSeconds,
			String llmProvider, String llmModel, int batchSize, int commitsPerRequest,
			int apiCallsMade, int deferredCommits, int permanentlySkipped) {
		RunMetadata meta = new RunMetadata();
		meta.startedAt = startedAt;
		meta.completedAt = completedAt;
		meta.durationSeconds = durationSeconds;
		meta.llmProvider = llmProvider;
		meta.llmModel = llmModel;
		meta.batchSize = batchSize;
		meta.commitsPerRequest = commitsPerRequest;
		meta.apiCallsMade = apiCallsMade;
		meta.deferredCommits = deferredCommits;
		meta.permanentlySkipped = permanentlySkipped;
		this.runMetadata = meta;
	}

	public TimeWindow getTimeWindow() {
		return timeWindow;
	}

	/**
	 * Computes the time window (earliest and latest evaluatedAt) from a list of evaluations.
	 */
	public void computeTimeWindow(List<CommitEvaluation> evaluations) {
		if (evaluations == null || evaluations.isEmpty()) {
			return;
		}
		Instant earliest = null;
		Instant latest = null;
		Instant earliestCommit = null;
		Instant latestCommit = null;
		for (CommitEvaluation eval : evaluations) {
			Instant at = eval.evaluatedAt();
			if (at != null) {
				if (earliest == null || at.isBefore(earliest)) {
					earliest = at;
				}
				if (latest == null || at.isAfter(latest)) {
					latest = at;
				}
			}
			Instant cd = eval.commitDate();
			if (cd != null) {
				if (earliestCommit == null || cd.isBefore(earliestCommit)) {
					earliestCommit = cd;
				}
				if (latestCommit == null || cd.isAfter(latestCommit)) {
					latestCommit = cd;
				}
			}
		}
		if (earliest != null) {
			this.timeWindow = new TimeWindow(earliest.toString(), latest.toString(),
					earliestCommit != null ? earliestCommit.toString() : null,
					latestCommit != null ? latestCommit.toString() : null);
		}
	}

	/**
	 * Run-level metadata captured at the end of a mining run.
	 */
	public static class RunMetadata {
		private String startedAt;
		private String completedAt;
		private long durationSeconds;
		private String llmProvider;
		private String llmModel;
		private int batchSize;
		private int commitsPerRequest;
		private int apiCallsMade;
		private int deferredCommits;
		private int permanentlySkipped;

		public String getStartedAt() { return startedAt; }
		public String getCompletedAt() { return completedAt; }
		public long getDurationSeconds() { return durationSeconds; }
		public String getLlmProvider() { return llmProvider; }
		public String getLlmModel() { return llmModel; }
		public int getBatchSize() { return batchSize; }
		public int getCommitsPerRequest() { return commitsPerRequest; }
		public int getApiCallsMade() { return apiCallsMade; }
		public int getDeferredCommits() { return deferredCommits; }
		public int getPermanentlySkipped() { return permanentlySkipped; }
	}

	/**
	 * Time window of evaluated commits.
	 */
	public static class TimeWindow {
		private final String earliestEvaluation;
		private final String latestEvaluation;
		private final String earliestCommitDate;
		private final String latestCommitDate;

		public TimeWindow(String earliestEvaluation, String latestEvaluation,
				String earliestCommitDate, String latestCommitDate) {
			this.earliestEvaluation = earliestEvaluation;
			this.latestEvaluation = latestEvaluation;
			this.earliestCommitDate = earliestCommitDate;
			this.latestCommitDate = latestCommitDate;
		}

		public String getEarliestEvaluation() { return earliestEvaluation; }
		public String getLatestEvaluation() { return latestEvaluation; }
		public String getEarliestCommitDate() { return earliestCommitDate; }
		public String getLatestCommitDate() { return latestCommitDate; }
	}
}
