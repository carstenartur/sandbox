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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitAnalysisListener;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitAnalysisResult;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitAnalysisResult.AnalysisStatus;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitInfo;
import org.sandbox.jdt.triggerpattern.mining.analysis.DiffHunk;
import org.sandbox.jdt.triggerpattern.mining.analysis.FileDiff;
import org.sandbox.jdt.triggerpattern.mining.analysis.InferredRule;
import org.sandbox.jdt.triggerpattern.mining.git.AsyncCommitAnalyzer;
import org.sandbox.jdt.triggerpattern.mining.git.GitHistoryProvider;

/**
 * Tests for {@link AsyncCommitAnalyzer}.
 */
public class AsyncCommitAnalyzerTest {

	private AsyncCommitAnalyzer analyzer;

	@AfterEach
	public void tearDown() {
		if (analyzer != null) {
			analyzer.shutdown();
		}
	}

	@Test
	public void testAnalyzeCommitWithNoJavaFiles() throws Exception {
		GitHistoryProvider mockProvider = createMockProvider(List.of());

		analyzer = new AsyncCommitAnalyzer(mockProvider, Path.of("."), null); //$NON-NLS-1$

		CommitInfo commit = createCommit("abc123", "Update README"); //$NON-NLS-1$ //$NON-NLS-2$
		CompletableFuture<CommitAnalysisResult> future = analyzer.analyzeCommit(commit);

		CommitAnalysisResult result = future.get(10, TimeUnit.SECONDS);

		assertEquals("abc123", result.commitId()); //$NON-NLS-1$
		assertEquals(AnalysisStatus.NO_RULES, result.status());
		assertTrue(result.inferredRules().isEmpty());
		assertNotNull(result.analysisTime());
	}

	@Test
	public void testAnalyzeCommitWithJavaChange() throws Exception {
		String before = """
				class Test {
				    void method() {
				        String s = new String(bytes, "UTF-8");
				    }
				}
				"""; //$NON-NLS-1$
		String after = """
				class Test {
				    void method() {
				        String s = new String(bytes, StandardCharsets.UTF_8);
				    }
				}
				"""; //$NON-NLS-1$

		DiffHunk hunk = new DiffHunk(3, 1, 3, 1,
				"        String s = new String(bytes, \"UTF-8\");", //$NON-NLS-1$
				"        String s = new String(bytes, StandardCharsets.UTF_8);"); //$NON-NLS-1$
		FileDiff diff = new FileDiff("Test.java", before, after, List.of(hunk)); //$NON-NLS-1$

		GitHistoryProvider mockProvider = createMockProvider(List.of(diff));

		analyzer = new AsyncCommitAnalyzer(mockProvider, Path.of("."), null); //$NON-NLS-1$

		CommitInfo commit = createCommit("def456", "Fix encoding"); //$NON-NLS-1$ //$NON-NLS-2$
		CompletableFuture<CommitAnalysisResult> future = analyzer.analyzeCommit(commit);

		CommitAnalysisResult result = future.get(10, TimeUnit.SECONDS);

		assertEquals("def456", result.commitId()); //$NON-NLS-1$
		// Either DONE (rules found) or NO_RULES depending on hunk refinement success
		assertTrue(result.status() == AnalysisStatus.DONE
				|| result.status() == AnalysisStatus.NO_RULES,
				"Status should be DONE or NO_RULES, was: " + result.status()); //$NON-NLS-1$
	}

	@Test
	public void testAnalyzeCommitWithFailure() throws Exception {
		GitHistoryProvider failingProvider = new GitHistoryProvider() {
			@Override
			public List<CommitInfo> getHistory(Path repositoryPath, int maxCommits) {
				throw new RuntimeException("Git error"); //$NON-NLS-1$
			}

			@Override
			public List<FileDiff> getDiffs(Path repositoryPath, String commitId) {
				throw new RuntimeException("Git error"); //$NON-NLS-1$
			}

			@Override
			public String getFileContent(Path repositoryPath, String commitId, String filePath) {
				return null;
			}
		};

		analyzer = new AsyncCommitAnalyzer(failingProvider, Path.of("."), null); //$NON-NLS-1$

		CommitInfo commit = createCommit("ghi789", "Broken commit"); //$NON-NLS-1$ //$NON-NLS-2$
		CompletableFuture<CommitAnalysisResult> future = analyzer.analyzeCommit(commit);

		CommitAnalysisResult result = future.get(10, TimeUnit.SECONDS);

		assertEquals(AnalysisStatus.FAILED, result.status());
	}

	@Test
	public void testListenerCallbacks() throws Exception {
		GitHistoryProvider mockProvider = createMockProvider(List.of());

		List<String> startedCommits = new ArrayList<>();
		List<String> completedCommits = new ArrayList<>();
		List<String> failedCommits = new ArrayList<>();

		CommitAnalysisListener testListener = new CommitAnalysisListener() {
			@Override
			public void onAnalysisStarted(String commitId) {
				startedCommits.add(commitId);
			}

			@Override
			public void onAnalysisComplete(String commitId, List<InferredRule> rules) {
				completedCommits.add(commitId);
			}

			@Override
			public void onAnalysisFailed(String commitId, Exception error) {
				failedCommits.add(commitId);
			}
		};

		analyzer = new AsyncCommitAnalyzer(mockProvider, Path.of("."), testListener); //$NON-NLS-1$

		CommitInfo commit = createCommit("abc", "Test commit"); //$NON-NLS-1$ //$NON-NLS-2$
		CompletableFuture<CommitAnalysisResult> future = analyzer.analyzeCommit(commit);
		future.get(10, TimeUnit.SECONDS);

		assertTrue(startedCommits.contains("abc"), "Listener should be notified of start"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(completedCommits.contains("abc"), "Listener should be notified of completion"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(failedCommits.isEmpty(), "No failures expected"); //$NON-NLS-1$
	}

	@Test
	public void testAnalyzeAllMultipleCommits() throws Exception {
		GitHistoryProvider mockProvider = createMockProvider(List.of());

		analyzer = new AsyncCommitAnalyzer(mockProvider, Path.of("."), null); //$NON-NLS-1$

		List<CommitInfo> commits = List.of(
				createCommit("aaa", "First"), //$NON-NLS-1$ //$NON-NLS-2$
				createCommit("bbb", "Second"), //$NON-NLS-1$ //$NON-NLS-2$
				createCommit("ccc", "Third")); //$NON-NLS-1$ //$NON-NLS-2$

		List<CompletableFuture<CommitAnalysisResult>> futures = analyzer.analyzeAll(commits);

		assertEquals(3, futures.size(), "Should have one future per commit"); //$NON-NLS-1$

		for (CompletableFuture<CommitAnalysisResult> future : futures) {
			CommitAnalysisResult result = future.get(10, TimeUnit.SECONDS);
			assertNotNull(result);
		}
	}

	@Test
	public void testFailureListenerCallback() throws Exception {
		GitHistoryProvider failingProvider = new GitHistoryProvider() {
			@Override
			public List<CommitInfo> getHistory(Path repositoryPath, int maxCommits) {
				return List.of();
			}

			@Override
			public List<FileDiff> getDiffs(Path repositoryPath, String commitId) {
				throw new RuntimeException("Simulated failure"); //$NON-NLS-1$
			}

			@Override
			public String getFileContent(Path repositoryPath, String commitId, String filePath) {
				return null;
			}
		};

		List<String> failedCommits = new ArrayList<>();
		CommitAnalysisListener testListener = new CommitAnalysisListener() {
			@Override
			public void onAnalysisStarted(String commitId) {
			}

			@Override
			public void onAnalysisComplete(String commitId, List<InferredRule> rules) {
			}

			@Override
			public void onAnalysisFailed(String commitId, Exception error) {
				failedCommits.add(commitId);
			}
		};

		analyzer = new AsyncCommitAnalyzer(failingProvider, Path.of("."), testListener); //$NON-NLS-1$

		CommitInfo commit = createCommit("fail1", "Will fail"); //$NON-NLS-1$ //$NON-NLS-2$
		CompletableFuture<CommitAnalysisResult> future = analyzer.analyzeCommit(commit);
		future.get(10, TimeUnit.SECONDS);

		assertFalse(failedCommits.isEmpty(), "Failure listener should be called"); //$NON-NLS-1$
		assertTrue(failedCommits.contains("fail1")); //$NON-NLS-1$
	}

	// ---- helpers ----

	private CommitInfo createCommit(String id, String message) {
		return new CommitInfo(id, id.substring(0, Math.min(7, id.length())),
				message, "test-author", LocalDateTime.now(), 1); //$NON-NLS-1$
	}

	private GitHistoryProvider createMockProvider(List<FileDiff> diffs) {
		return new GitHistoryProvider() {
			@Override
			public List<CommitInfo> getHistory(Path repositoryPath, int maxCommits) {
				return List.of();
			}

			@Override
			public List<FileDiff> getDiffs(Path repositoryPath, String commitId) {
				return diffs;
			}

			@Override
			public String getFileContent(Path repositoryPath, String commitId, String filePath) {
				return null;
			}
		};
	}
}
