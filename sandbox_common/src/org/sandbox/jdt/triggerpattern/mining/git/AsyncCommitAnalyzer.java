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
package org.sandbox.jdt.triggerpattern.mining.git;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.sandbox.jdt.triggerpattern.mining.analysis.CommitAnalysisListener;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitAnalysisResult;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitAnalysisResult.AnalysisStatus;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitInfo;
import org.sandbox.jdt.triggerpattern.mining.analysis.DiffHunkRefiner;
import org.sandbox.jdt.triggerpattern.mining.analysis.CodeChangePair;
import org.sandbox.jdt.triggerpattern.mining.analysis.FileDiff;
import org.sandbox.jdt.triggerpattern.mining.analysis.InferredRule;
import org.sandbox.jdt.triggerpattern.mining.analysis.RuleGrouper;
import org.sandbox.jdt.triggerpattern.mining.analysis.RuleGroup;
import org.sandbox.jdt.triggerpattern.mining.analysis.RuleInferenceEngine;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Analyzes Git commits asynchronously to infer transformation rules.
 *
 * <p>Each commit is analyzed in a background thread. Results are reported
 * via the {@link CommitAnalysisListener} callback interface. The analyzer
 * uses a bounded thread pool to limit parallelism.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * AsyncCommitAnalyzer analyzer = new AsyncCommitAnalyzer(gitProvider, repoPath, listener);
 * List&lt;CompletableFuture&lt;CommitAnalysisResult&gt;&gt; futures = analyzer.analyzeAll(commits);
 * // ... later ...
 * analyzer.shutdown();
 * </pre>
 *
 * @since 1.2.6
 */
public class AsyncCommitAnalyzer {

	/** Default maximum number of parallel analysis threads. */
	private static final int DEFAULT_MAX_PARALLELISM = 4;

	private final GitHistoryProvider gitProvider;
	private final Path repositoryPath;
	private final CommitAnalysisListener listener;
	private final ExecutorService executor;
	private final RuleInferenceEngine engine;
	private final DiffHunkRefiner refiner;
	private final RuleGrouper grouper;

	/**
	 * Creates a new analyzer with default parallelism.
	 *
	 * @param gitProvider    the git history provider
	 * @param repositoryPath path to the repository
	 * @param listener       callback for analysis status updates (may be {@code null})
	 */
	public AsyncCommitAnalyzer(GitHistoryProvider gitProvider, Path repositoryPath,
			CommitAnalysisListener listener) {
		this(gitProvider, repositoryPath, listener, DEFAULT_MAX_PARALLELISM);
	}

	/**
	 * Creates a new analyzer with the specified parallelism.
	 *
	 * @param gitProvider    the git history provider
	 * @param repositoryPath path to the repository
	 * @param listener       callback for analysis status updates (may be {@code null})
	 * @param maxParallelism maximum number of parallel analysis threads
	 */
	public AsyncCommitAnalyzer(GitHistoryProvider gitProvider, Path repositoryPath,
			CommitAnalysisListener listener, int maxParallelism) {
		this.gitProvider = gitProvider;
		this.repositoryPath = repositoryPath;
		this.listener = listener;
		this.executor = Executors.newFixedThreadPool(maxParallelism);
		this.engine = new RuleInferenceEngine();
		this.refiner = new DiffHunkRefiner();
		this.grouper = new RuleGrouper();
	}

	/**
	 * Starts asynchronous analysis of a single commit.
	 *
	 * @param commit the commit to analyze
	 * @return a future holding the analysis result
	 */
	public CompletableFuture<CommitAnalysisResult> analyzeCommit(CommitInfo commit) {
		return CompletableFuture.supplyAsync(() -> doAnalyze(commit), executor);
	}

	/**
	 * Starts asynchronous analysis of multiple commits.
	 *
	 * @param commits the commits to analyze
	 * @return list of futures, one per commit
	 */
	public List<CompletableFuture<CommitAnalysisResult>> analyzeAll(List<CommitInfo> commits) {
		List<CompletableFuture<CommitAnalysisResult>> futures = new ArrayList<>();
		for (CommitInfo commit : commits) {
			futures.add(analyzeCommit(commit));
		}
		return futures;
	}

	/**
	 * Shuts down the executor, rejecting new tasks. Already submitted tasks will
	 * complete.
	 */
	public void shutdown() {
		executor.shutdown();
	}

	/**
	 * Performs the actual analysis for a single commit.
	 *
	 * @param commit the commit metadata
	 * @return the analysis result
	 */
	CommitAnalysisResult doAnalyze(CommitInfo commit) {
		String commitId = commit.id();
		long startTime = System.nanoTime();

		if (listener != null) {
			listener.onAnalysisStarted(commitId);
		}

		try {
			List<FileDiff> diffs = gitProvider.getDiffs(repositoryPath, commitId);

			if (diffs.isEmpty()) {
				Duration elapsed = Duration.ofNanos(System.nanoTime() - startTime);
				CommitAnalysisResult result = new CommitAnalysisResult(
						commitId, AnalysisStatus.NO_RULES, List.of(), elapsed);
				if (listener != null) {
					listener.onAnalysisComplete(commitId, List.of());
				}
				return result;
			}

			List<InferredRule> allRules = new ArrayList<>();

			for (FileDiff diff : diffs) {
				List<CodeChangePair> pairs = refiner.refineToStatements(diff);
				for (CodeChangePair pair : pairs) {
					Optional<InferredRule> rule = engine.inferRule(pair);
					rule.ifPresent(allRules::add);
				}
			}

			// Group similar rules to boost confidence
			if (allRules.size() > 1) {
				List<RuleGroup> groups = grouper.groupSimilar(allRules);
				allRules = new ArrayList<>();
				for (RuleGroup group : groups) {
					allRules.add(group.generalizedRule());
				}
			}

			Duration elapsed = Duration.ofNanos(System.nanoTime() - startTime);
			AnalysisStatus status = allRules.isEmpty()
					? AnalysisStatus.NO_RULES : AnalysisStatus.DONE;

			CommitAnalysisResult result = new CommitAnalysisResult(
					commitId, status, List.copyOf(allRules), elapsed);

			if (listener != null) {
				listener.onAnalysisComplete(commitId, List.copyOf(allRules));
			}

			return result;

		} catch (Exception e) {
			Duration elapsed = Duration.ofNanos(System.nanoTime() - startTime);
			CommitAnalysisResult result = new CommitAnalysisResult(
					commitId, AnalysisStatus.FAILED, List.of(), elapsed);
			if (listener != null) {
				listener.onAnalysisFailed(commitId, e);
			}
			return result;
		}
	}
}
