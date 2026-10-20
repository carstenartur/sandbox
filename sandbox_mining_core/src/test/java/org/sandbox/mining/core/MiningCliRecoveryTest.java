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
package org.sandbox.mining.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation.TrafficLight;
import org.sandbox.jdt.triggerpattern.llm.LlmClient;
import org.sandbox.mining.core.candidate.CandidateStatus;
import org.sandbox.mining.core.candidate.CandidateStore;
import org.sandbox.mining.core.candidate.MiningCandidate;
import org.sandbox.mining.core.config.MiningState;
import org.sandbox.mining.core.report.JsonReporter;

import com.google.gson.JsonParser;

class MiningCliRecoveryTest {

	@TempDir
	Path tempDir;

	@Test
	void timeoutKeepsCompletedCandidatesAndResumesAtTheFailedBatch() throws Exception {
		Fixture fixture = createRepository(5);
		HttpTimeoutException failure = new HttpTimeoutException("request timed out");
		AtomicInteger calls = new AtomicInteger();
		StubLlmClient client = new StubLlmClient((hashes, messages, repo) -> {
			if (calls.incrementAndGet() == 2) {
				// The completed batch must already be durable before the next API call.
				assertEquals(fixture.hashes().get(1), fixture.loadState().getLastProcessedCommit(repo));
				assertEquals(Set.copyOf(fixture.hashes().subList(0, 2)), fixture.candidateCommits());
				throw failure;
			}
			return evaluations(hashes, messages, repo);
		});
		ByteArrayOutputStream log = new ByteArrayOutputStream();
		PrintStream originalOut = System.out;
		try (PrintStream captured = new PrintStream(log)) {
			System.setOut(captured);
			assertSame(failure, assertThrows(IOException.class,
					() -> new MiningCli(provider -> client).run(fixture.arguments(2))));
		} finally {
			System.setOut(originalOut);
		}

		assertFalse(log.toString().contains("=== Mining complete ==="));
		assertEquals(fixture.hashes().subList(0, 2), fixture.evaluatedCommits());
		assertEquals(fixture.hashes().get(1), fixture.loadState().getLastProcessedCommit(fixture.repoUrl()));
		assertEquals(2, fixture.loadState().getGlobalTotalProcessed());
		var statistics = JsonParser.parseString(Files.readString(fixture.output().resolve("statistics.json")))
				.getAsJsonObject();
		assertEquals(2, statistics.get("totalProcessed").getAsInt());

		StubLlmClient resumed = new StubLlmClient(MiningCliRecoveryTest::evaluations);
		new MiningCli(provider -> resumed).run(fixture.arguments(2));
		assertEquals(fixture.hashes().subList(2, 5), resumed.requestedCommits());
		assertEquals(fixture.hashes(), fixture.evaluatedCommits());
		assertEquals(Set.copyOf(fixture.hashes()), fixture.candidateCommits());
		assertEquals(5, fixture.loadState().getGlobalTotalProcessed());
	}

	@Test
	void partialResponseKeepsCompletedResultsWithoutAdvancingAcrossTheGap() throws Exception {
		Fixture fixture = createRepository(5);
		StubLlmClient client = new StubLlmClient((hashes, messages, repo) -> {
			var complete = evaluations(hashes, messages, repo);
			return List.of(complete.get(0), complete.get(2));
		});

		assertThrows(IOException.class, () -> new MiningCli(provider -> client).run(fixture.arguments(4)));

		List<String> completed = List.of(fixture.hashes().get(0), fixture.hashes().get(2));
		assertEquals(completed, fixture.evaluatedCommits());
		assertEquals(Set.copyOf(completed), fixture.candidateCommits());
		MiningState state = fixture.loadState();
		assertEquals(fixture.hashes().get(0), state.getLastProcessedCommit(fixture.repoUrl()));
		assertEquals(1, state.getGlobalTotalProcessed());
		assertEquals(Set.of(fixture.hashes().get(1), fixture.hashes().get(3)),
				state.getRepoState(fixture.repoUrl()).getDeferredCommits().stream()
						.map(MiningState.DeferredCommit::getHash).collect(Collectors.toSet()));
		assertEquals(fixture.hashes().subList(0, 4), client.requestedCommits(),
				"A later batch must not move the checkpoint past an unevaluated commit");
	}

	@Test
	void restartReusesResultsAfterAGapWithoutReplacingCandidatesOrCountingThemTwice() throws Exception {
		Fixture fixture = createRepository(5);
		StubLlmClient partial = new StubLlmClient((hashes, messages, repo) -> {
			var complete = evaluations(hashes, messages, repo);
			return List.of(complete.get(0), complete.get(2));
		});
		partial.truncated = true;
		assertThrows(IOException.class, () -> new MiningCli(provider -> partial).run(fixture.arguments(4)));

		String retainedHash = fixture.hashes().get(2);
		JsonReporter reporter = new JsonReporter();
		CommitEvaluation retainedEvaluation = reporter.loadExistingEvaluations(fixture.output().resolve("evaluations.json"))
				.stream().filter(evaluation -> retainedHash.equals(evaluation.commitHash())).findFirst().orElseThrow();
		CandidateStore store = new CandidateStore(tempDir.resolve("mining-candidates"));
		MiningCandidate retainedCandidate = store.loadAll().stream()
				.filter(candidate -> retainedHash.equals(candidate.getSourceCommit())).findFirst().orElseThrow();
		retainedCandidate.setStatus(CandidateStatus.READY_FOR_REVIEW);
		store.save(retainedCandidate);
		Path retainedFile = store.getStoreDir().resolve(retainedCandidate.toFileName());
		String retainedJson = Files.readString(retainedFile);

		StubLlmClient resumed = new StubLlmClient(MiningCliRecoveryTest::evaluations);
		new MiningCli(provider -> resumed).run(fixture.arguments(4));

		assertEquals(List.of(fixture.hashes().get(1), fixture.hashes().get(3), fixture.hashes().get(4)),
				resumed.requestedCommits(), "Already completed evaluations must not be requested again");
		List<CommitEvaluation> allEvaluations = reporter.loadExistingEvaluations(fixture.output().resolve("evaluations.json"));
		assertEquals(5, allEvaluations.size());
		assertEquals(Set.copyOf(fixture.hashes()), allEvaluations.stream()
				.map(CommitEvaluation::commitHash).collect(Collectors.toSet()));
		assertEquals(retainedEvaluation, allEvaluations.stream()
				.filter(evaluation -> retainedHash.equals(evaluation.commitHash())).findFirst().orElseThrow());
		assertEquals(retainedJson, Files.readString(retainedFile), "Keep candidate review status and revision intact");
		assertEquals(5, store.loadAll().size());
		assertEquals(5, fixture.loadState().getGlobalTotalProcessed());
		assertEquals(5, fixture.loadState().getRepoState(fixture.repoUrl()).getCategoryHitCounts().get("Collections"));
		var statistics = JsonParser.parseString(Files.readString(fixture.output().resolve("statistics.json")))
				.getAsJsonObject();
		assertEquals(3, statistics.get("totalProcessed").getAsInt(),
				"Run statistics count newly evaluated commits, without recounting cached results");
		assertEquals(3, statistics.get("relevant").getAsInt());
	}

	@Test
	void emptyResponseCheckpointsOnlyTheLeadingSkippedCommits() throws Exception {
		Fixture fixture = createRepository(3);
		Path keywords = tempDir.resolve("keywords.txt");
		Files.writeString(keywords, "change 1\nchange 2\n");
		List<String> arguments = new ArrayList<>(List.of(fixture.arguments(3)));
		arguments.addAll(List.of("--keyword-filter", keywords.toString()));
		StubLlmClient client = new StubLlmClient((hashes, messages, repo) -> List.of());

		assertThrows(IOException.class,
				() -> new MiningCli(provider -> client).run(arguments.toArray(String[]::new)));

		assertEquals(fixture.hashes().get(0), fixture.loadState().getLastProcessedCommit(fixture.repoUrl()));
		assertEquals(1, fixture.loadState().getGlobalTotalProcessed());
		assertTrue(fixture.evaluatedCommits().isEmpty());
		assertTrue(fixture.candidateCommits().isEmpty());
	}

	@Test
	void candidateWriteFailureDoesNotAdvanceThePersistedCheckpoint() throws Exception {
		Fixture fixture = createRepository(3);
		Files.writeString(tempDir.resolve("mining-candidates"), "blocks candidate directory creation");
		StubLlmClient client = new StubLlmClient(MiningCliRecoveryTest::evaluations);

		assertThrows(IOException.class, () -> new MiningCli(provider -> client).run(fixture.arguments(2)));

		assertNull(fixture.loadState().getLastProcessedCommit(fixture.repoUrl()));
		assertEquals(0, fixture.loadState().getGlobalTotalProcessed());
		assertEquals(fixture.hashes().subList(0, 2), client.requestedCommits());
	}

	@Test
	void cachedEvaluationStillRequiresItsCandidateToBeSavedBeforeAdvancing() throws Exception {
		Fixture fixture = createRepository(2);
		Files.writeString(tempDir.resolve("mining-candidates"), "blocks candidate directory creation");
		StubLlmClient first = new StubLlmClient(MiningCliRecoveryTest::evaluations);
		assertThrows(IOException.class, () -> new MiningCli(provider -> first).run(fixture.arguments(2)));
		assertEquals(fixture.hashes(), fixture.evaluatedCommits());

		StubLlmClient resumed = new StubLlmClient(MiningCliRecoveryTest::evaluations);
		assertThrows(IOException.class, () -> new MiningCli(provider -> resumed).run(fixture.arguments(2)));

		assertTrue(resumed.requestedCommits().isEmpty(), "The previous response is available for reuse");
		assertNull(fixture.loadState().getLastProcessedCommit(fixture.repoUrl()));
		assertEquals(0, fixture.loadState().getGlobalTotalProcessed());
	}

	private Fixture createRepository(int commitCount) throws Exception {
		Path source = tempDir.resolve("source");
		List<String> hashes = new ArrayList<>();
		try (Git git = Git.init().setDirectory(source.toFile()).setInitialBranch("main").call()) {
			for (int i = 0; i < commitCount; i++) {
				Files.writeString(source.resolve("Example.java"),
						"class Example { int value() { return " + i + "; } }\n");
				git.add().addFilepattern("Example.java").call();
				PersonIdent author = new PersonIdent("Test", "test@example.com",
						Date.from(Instant.parse("2024-01-01T00:00:00Z").plusSeconds(i)), TimeZone.getTimeZone("UTC"));
				hashes.add(git.commit().setMessage("change " + i).setAuthor(author).setCommitter(author).call().getName());
			}
		}
		Path config = tempDir.resolve("repos.yml");
		Files.writeString(config, """
				mining:
				  min-diff-lines-per-commit: 1
				  max-diff-lines-per-commit: 500
				  repositories:
				    - url: '%s'
				      branch: main
				""".formatted(source));
		return new Fixture(source, config, tempDir.resolve("state.json"), tempDir.resolve("output"), hashes);
	}

	private record Fixture(Path source, Path config, Path state, Path output, List<String> hashes) {
		String repoUrl() { return source.toString(); }
		MiningState loadState() throws IOException { return MiningState.load(state); }
		String[] arguments(int commitsPerRequest) {
			return new String[] { "--config", config.toString(), "--state", state.toString(),
					"--output", output.toString(), "--sandbox-root", source.getParent().toString(),
					"--commits-per-request", Integer.toString(commitsPerRequest) };
		}
		List<String> evaluatedCommits() {
			return new JsonReporter().loadExistingEvaluations(output.resolve("evaluations.json"))
					.stream().map(CommitEvaluation::commitHash).toList();
		}
		Set<String> candidateCommits() throws IOException {
			List<MiningCandidate> candidates = new CandidateStore(source.getParent().resolve("mining-candidates")).loadAll();
			assertTrue(candidates.stream().allMatch(candidate -> candidate.getStatus() == CandidateStatus.DSL_VALID));
			return candidates.stream().map(MiningCandidate::getSourceCommit).collect(Collectors.toSet());
		}
	}

	private static List<CommitEvaluation> evaluations(List<String> hashes, List<String> messages, String repo) {
		List<CommitEvaluation> results = new ArrayList<>();
		for (int i = 0; i < hashes.size(); i++) {
			results.add(new CommitEvaluation(hashes.get(i), messages.get(i), repo, Instant.now(), null,
					true, null, false, null, 8, 7, 3, TrafficLight.GREEN, "Collections", false, null, true,
					"java.util.Collections.emptyList() :: sourceVersionGE(9)\n=> java.util.List.of()\n;;\n",
					"collections.sandbox-hint", null, null, "Replace emptyList", null,
					"class T { Object m() { return java.util.Collections.emptyList(); } }",
					"class T { Object m() { return java.util.List.of(); } }",
					"class T { Object m() { return java.util.Collections.emptySet(); } }"));
		}
		return results;
	}

	@FunctionalInterface
	private interface BatchEvaluator {
		List<CommitEvaluation> evaluate(List<String> hashes, List<String> messages, String repo) throws IOException;
	}

	private static final class StubLlmClient implements LlmClient {
		private final BatchEvaluator evaluator;
		private final List<String> requested = new ArrayList<>();
		private Duration maxFailureDuration;
		private int calls;
		private boolean truncated;
		StubLlmClient(BatchEvaluator evaluator) { this.evaluator = evaluator; }
		List<String> requestedCommits() { return List.copyOf(requested); }
		@Override public List<CommitEvaluation> evaluateBatch(String prompt, List<String> hashes,
				List<String> messages, String repo) throws IOException {
			calls++;
			requested.addAll(hashes);
			return evaluator.evaluate(hashes, messages, repo);
		}
		@Override public CommitEvaluation evaluate(String prompt, String hash, String message, String repo) {
			throw new UnsupportedOperationException();
		}
		@Override public boolean hasRemainingQuota() { return true; }
		@Override public boolean isApiUnavailable() { return false; }
		@Override public int getDailyRequestCount() { return calls; }
		@Override public Duration getMaxFailureDuration() { return maxFailureDuration; }
		@Override public void setMaxFailureDuration(Duration duration) { maxFailureDuration = duration; }
		@Override public boolean wasLastResponseTruncated() { return truncated; }
		@Override public String getModel() { return "local-test"; }
		@Override public void close() { }
	}
}
