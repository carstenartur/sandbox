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
package org.sandbox.mining.gemini;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.sandbox.mining.gemini.category.CategoryManager;
import org.sandbox.mining.gemini.config.MiningConfig;
import org.sandbox.mining.gemini.config.MiningState;
import org.sandbox.mining.gemini.config.RepoEntry;
import org.sandbox.mining.gemini.dsl.DslValidator;
import org.sandbox.mining.gemini.gemini.CommitEvaluation;
import org.sandbox.mining.gemini.gemini.DslContextCollector;
import org.sandbox.mining.gemini.gemini.GeminiClient;
import org.sandbox.mining.gemini.gemini.GeminiPromptBuilder;
import org.sandbox.mining.gemini.gemini.GeminiPromptBuilder.CommitData;
import org.sandbox.mining.gemini.git.CommitWalker;
import org.sandbox.mining.gemini.git.DiffExtractor;
import org.sandbox.mining.gemini.git.RepoCloner;
import org.sandbox.mining.gemini.report.GithubPagesGenerator;
import org.sandbox.mining.gemini.report.JsonReporter;
import org.sandbox.mining.gemini.report.ReportAggregator;
import org.sandbox.mining.gemini.report.StatisticsCollector;

/**
 * CLI entry point for Gemini-AI-powered commit analysis.
 *
 * <p>Analyzes Eclipse project commits using the Google Gemini API and
 * generates reports for TriggerPattern DSL mining.</p>
 *
 * <p>Usage: java -jar sandbox-mining-gemini.jar [options]</p>
 */
public class GeminiMiningCli {

	private static final String OPT_CONFIG = "--config"; //$NON-NLS-1$
	private static final String OPT_STATE = "--state"; //$NON-NLS-1$
	private static final String OPT_SANDBOX_ROOT = "--sandbox-root"; //$NON-NLS-1$
	private static final String OPT_BATCH_SIZE = "--batch-size"; //$NON-NLS-1$
	private static final String OPT_OUTPUT = "--output"; //$NON-NLS-1$
	private static final String OPT_COMMITS_PER_REQUEST = "--commits-per-request"; //$NON-NLS-1$

	private static final int DEFAULT_BATCH_SIZE = 500;
	private static final int DEFAULT_COMMITS_PER_REQUEST = 4;

	/**
	 * Commits with fewer diff lines than this are typically trivial (typo fixes,
	 * import-only changes) and are skipped to avoid wasting API quota.
	 */
	private static final int MAX_USEFUL_DIFF_LINES = 300;

	public static void main(String[] args) {
		try {
			new GeminiMiningCli().run(args);
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Runs the mining process with the given arguments.
	 *
	 * @param args CLI arguments
	 * @throws IOException     if an I/O error occurs
	 * @throws GitAPIException if a Git operation fails
	 */
	public void run(String[] args) throws IOException, GitAPIException {
		Path configPath = null;
		Path statePath = null;
		Path sandboxRoot = Path.of("."); //$NON-NLS-1$
		int batchSize = DEFAULT_BATCH_SIZE;
		int commitsPerRequest = DEFAULT_COMMITS_PER_REQUEST;
		Path outputDir = Path.of("output"); //$NON-NLS-1$

		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			case OPT_CONFIG:
				configPath = Path.of(requireArg(args, ++i, OPT_CONFIG));
				break;
			case OPT_STATE:
				statePath = Path.of(requireArg(args, ++i, OPT_STATE));
				break;
			case OPT_SANDBOX_ROOT:
				sandboxRoot = Path.of(requireArg(args, ++i, OPT_SANDBOX_ROOT));
				break;
			case OPT_BATCH_SIZE:
				batchSize = Integer.parseInt(requireArg(args, ++i, OPT_BATCH_SIZE));
				break;
			case OPT_COMMITS_PER_REQUEST:
				commitsPerRequest = Integer.parseInt(requireArg(args, ++i, OPT_COMMITS_PER_REQUEST));
				if (commitsPerRequest < 1) {
					throw new IllegalArgumentException(
							"--commits-per-request must be >= 1 but was " + commitsPerRequest); //$NON-NLS-1$
				}
				break;
			case OPT_OUTPUT:
				outputDir = Path.of(requireArg(args, ++i, OPT_OUTPUT));
				break;
			default:
				System.err.println("Unknown option: " + args[i]); //$NON-NLS-1$
				printUsage();
				return;
			}
		}

		if (configPath == null) {
			configPath = sandboxRoot.resolve(".github/refactoring-mining/repos.yml"); //$NON-NLS-1$
		}
		if (statePath == null) {
			statePath = sandboxRoot.resolve(".github/refactoring-mining/state.json"); //$NON-NLS-1$
		}

		System.out.println("=== Sandbox Mining Gemini ==="); //$NON-NLS-1$
		System.out.println("Config: " + configPath); //$NON-NLS-1$
		System.out.println("State:  " + statePath); //$NON-NLS-1$
		System.out.println("Output: " + outputDir); //$NON-NLS-1$

		MiningConfig config = MiningConfig.parse(configPath);
		MiningState state = MiningState.load(statePath);
		CategoryManager categoryManager = new CategoryManager();
		DslContextCollector dslCollector = new DslContextCollector();
		String dslContext = dslCollector.collectContext(sandboxRoot);
		GeminiClient geminiClient = new GeminiClient();
		GeminiPromptBuilder promptBuilder = new GeminiPromptBuilder();
		DslValidator validator = new DslValidator();
		StatisticsCollector stats = new StatisticsCollector();
		ReportAggregator aggregator = new ReportAggregator();

		Path workDir = Files.createTempDirectory("gemini-mining-"); //$NON-NLS-1$
		try {
			processRepositories(config, state, statePath, workDir, batchSize, commitsPerRequest,
					geminiClient, promptBuilder, dslContext, categoryManager, validator, stats, aggregator);
		} finally {
			deleteDirectory(workDir);
		}

		// Generate output
		Files.createDirectories(outputDir);
		JsonReporter jsonReporter = new JsonReporter();
		jsonReporter.writeEvaluations(aggregator.getAllEvaluations(), outputDir);
		jsonReporter.writeStatistics(stats, outputDir);

		GithubPagesGenerator pagesGenerator = new GithubPagesGenerator();
		pagesGenerator.generate(aggregator.getAllEvaluations(), stats, outputDir);

		state.save(statePath);

		System.out.println("=== Mining complete ===");
		System.out.println("Processed: " + stats.getTotalProcessed() + " commits");
		System.out.println("Relevant:  " + stats.getRelevant());
		System.out.println("Output:    " + outputDir.toAbsolutePath());
	}

	private void processRepositories(MiningConfig config, MiningState state, Path statePath,
			Path workDir, int batchSize, int commitsPerRequest,
			GeminiClient geminiClient, GeminiPromptBuilder promptBuilder,
			String dslContext, CategoryManager categoryManager, DslValidator validator,
			StatisticsCollector stats, ReportAggregator aggregator) throws IOException, GitAPIException {
		RepoCloner cloner = new RepoCloner();

		for (RepoEntry repo : config.getRepositories()) {
			if (!geminiClient.hasRemainingQuota()) {
				System.out.println("Daily API quota exhausted (" + geminiClient.getDailyRequestCount() //$NON-NLS-1$
						+ "/" + GeminiClient.MAX_DAILY_REQUESTS //$NON-NLS-1$
						+ " requests used). Stopping. Will resume from current position on next run."); //$NON-NLS-1$
				return;
			}
			System.out.println("Processing: " + repo.getUrl()); //$NON-NLS-1$
			Path repoDir = workDir.resolve(repoDirectoryName(repo.getUrl()));
			cloner.cloneRepo(repo.getUrl(), repo.getBranch(), repoDir);

			String lastCommit = state.getLastProcessedCommit(repo.getUrl());
			try (CommitWalker walker = new CommitWalker(repoDir);
					DiffExtractor diffExtractor = new DiffExtractor(repoDir,
							config.getMaxDiffLinesPerCommit(), repo.getPaths(),
							config.getMaxFilesPerCommit())) {
				List<RevCommit> batch = walker.nextBatch(lastCommit, config.getStartDate(), batchSize);
				while (!batch.isEmpty()) {
					// Group commits into sub-batches for API calls
					for (int i = 0; i < batch.size(); i += commitsPerRequest) {
						if (!geminiClient.hasRemainingQuota()) {
							System.out.println("Daily API quota exhausted (" //$NON-NLS-1$
									+ geminiClient.getDailyRequestCount()
									+ "/" + GeminiClient.MAX_DAILY_REQUESTS //$NON-NLS-1$
									+ " requests used). Stopping. Will resume from current position on next run."); //$NON-NLS-1$
							return;
						}
						int end = Math.min(i + commitsPerRequest, batch.size());
						List<RevCommit> subBatch = batch.subList(i, end);
						processBatch(subBatch, repo, diffExtractor, state, statePath,
								geminiClient, promptBuilder, dslContext, categoryManager,
								validator, stats, aggregator, config.getMinDiffLinesPerCommit());
					}
					batch = walker.nextBatch(batch.get(batch.size() - 1).getName(),
							config.getStartDate(), batchSize);
				}
			}

			System.out.println("  Completed: " + repo.getUrl()); //$NON-NLS-1$
		}
	}

	private void processBatch(List<RevCommit> commits, RepoEntry repo,
			DiffExtractor diffExtractor, MiningState state, Path statePath,
			GeminiClient geminiClient, GeminiPromptBuilder promptBuilder,
			String dslContext, CategoryManager categoryManager, DslValidator validator,
			StatisticsCollector stats, ReportAggregator aggregator,
			int minDiffLines) throws IOException {
		// Collect commit data, skipping blank diffs and size-filtered commits
		List<CommitData> commitDataList = new ArrayList<>();
		List<RevCommit> includedCommits = new ArrayList<>();
		List<RevCommit> skippedCommits = new ArrayList<>();

		for (RevCommit commit : commits) {
			String diff = diffExtractor.extractDiff(commit);
			int lineCount = diff.split("\n", -1).length; //$NON-NLS-1$
			if (diff.isBlank()) {
				skippedCommits.add(commit);
			} else if (lineCount < minDiffLines) {
				System.out.println("  Skipping commit " + commit.getName().substring(0, 7) //$NON-NLS-1$
						+ " (diff too small: " + lineCount + " lines)"); //$NON-NLS-1$ //$NON-NLS-2$
				skippedCommits.add(commit);
			} else if (lineCount > MAX_USEFUL_DIFF_LINES) {
				System.out.println("  Skipping commit " + commit.getName().substring(0, 7) //$NON-NLS-1$
						+ " (diff too large: " + lineCount + " lines)"); //$NON-NLS-1$ //$NON-NLS-2$
				skippedCommits.add(commit);
			} else {
				commitDataList.add(new CommitData(commit.getName(), commit.getFullMessage(), diff));
				includedCommits.add(commit);
			}
		}

		// Update state for skipped commits immediately
		for (RevCommit commit : skippedCommits) {
			state.updateLastProcessedCommit(repo.getUrl(), commit.getName());
		}

		if (!commitDataList.isEmpty()) {
			List<String> hashes = commitDataList.stream().map(CommitData::commitHash).toList();
			List<String> messages = commitDataList.stream().map(CommitData::commitMessage).toList();
			String prompt = promptBuilder.buildBatchPrompt(dslContext,
					categoryManager.getCategoriesJson(), commitDataList);
			List<CommitEvaluation> evaluations = geminiClient.evaluateBatch(prompt, hashes,
					messages, repo.getUrl());

			// If we did not get one evaluation per included commit, treat this batch
			// as failed for non-skipped commits so they can be retried later.
			if (evaluations == null || evaluations.size() != includedCommits.size()) {
				System.out.println("  Incomplete batch evaluation for repository " + repo.getUrl() //$NON-NLS-1$
						+ "; will retry non-evaluated commits in a future run."); //$NON-NLS-1$
				// Note: skipped commits were already advanced in state above.
				state.save(statePath);
				return;
			}

			for (int j = 0; j < includedCommits.size(); j++) {
				RevCommit commit = includedCommits.get(j);
				CommitEvaluation evaluation = evaluations.get(j);
				if (evaluation == null) {
					System.out.println("  Missing evaluation for commit " + commit.getName() //$NON-NLS-1$
							+ "; stopping batch to retry remaining commits later."); //$NON-NLS-1$
					break;
				}
				handleEvaluation(evaluation, commit, validator, categoryManager, stats, aggregator);
				state.updateLastProcessedCommit(repo.getUrl(), commit.getName());
			}
		}

		// Save state after each batch for resume safety
		state.save(statePath);
	}

	private void handleEvaluation(CommitEvaluation evaluation, RevCommit commit,
			DslValidator validator, CategoryManager categoryManager,
			StatisticsCollector stats, ReportAggregator aggregator) {
		if (evaluation.dslRule() != null && !evaluation.dslRule().isBlank()) {
			var validation = validator.validate(evaluation.dslRule());
			if (!validation.valid()) {
				System.out.println("  Invalid DSL rule for " + commit.getName() //$NON-NLS-1$
						+ ": " + validation.message()); //$NON-NLS-1$
				if (Boolean.parseBoolean(System.getenv("GEMINI_DEBUG"))) { //$NON-NLS-1$
					System.out.println("  --- DSL rule begin ---"); //$NON-NLS-1$
					System.out.println(evaluation.dslRule());
					System.out.println("  --- DSL rule end ---"); //$NON-NLS-1$
				}
			}
		}
		if (evaluation.isNewCategory() && evaluation.category() != null) {
			categoryManager.addCategory(evaluation.category());
		}
		stats.record(evaluation);
		aggregator.add(evaluation);
	}

	static String repoDirectoryName(String url) {
		String name = url;
		if (name.endsWith(".git")) {
			name = name.substring(0, name.length() - 4);
		}
		int lastSlash = name.lastIndexOf('/');
		if (lastSlash >= 0) {
			name = name.substring(lastSlash + 1);
		}
		return name;
	}

	private static void printUsage() {
		System.out.println("Usage: java -jar sandbox-mining-gemini.jar [options]"); //$NON-NLS-1$
		System.out.println("Options:"); //$NON-NLS-1$
		System.out.println("  --config <path>              Path to repos.yml config file"); //$NON-NLS-1$
		System.out.println("  --state <path>               Path to state.json file"); //$NON-NLS-1$
		System.out.println("  --sandbox-root <path>        Root of sandbox repository"); //$NON-NLS-1$
		System.out.println("  --batch-size <n>             Number of commits per batch (default: 500)"); //$NON-NLS-1$
		System.out.println("  --commits-per-request <n>    Commits grouped into one API call (default: 4)"); //$NON-NLS-1$
		System.out.println("  --output <path>              Output directory (default: output)"); //$NON-NLS-1$
	}

	private static String requireArg(String[] args, int index, String option) {
		if (index >= args.length) {
			throw new IllegalArgumentException("Option " + option + " requires a value");
		}
		return args[index];
	}

	private static void deleteDirectory(Path dir) throws IOException {
		if (Files.exists(dir)) {
			try (var walk = Files.walk(dir)) {
				walk.sorted(java.util.Comparator.reverseOrder())
						.forEach(p -> {
							try {
								Files.deleteIfExists(p);
							} catch (IOException e) {
								// best effort cleanup
							}
						});
			}
		}
	}
}
