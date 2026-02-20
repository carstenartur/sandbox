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

	private static final String OPT_CONFIG = "--config";
	private static final String OPT_STATE = "--state";
	private static final String OPT_SANDBOX_ROOT = "--sandbox-root";
	private static final String OPT_BATCH_SIZE = "--batch-size";
	private static final String OPT_OUTPUT = "--output";

	private static final int DEFAULT_BATCH_SIZE = 500;

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
		Path sandboxRoot = Path.of(".");
		int batchSize = DEFAULT_BATCH_SIZE;
		Path outputDir = Path.of("output");

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
			case OPT_OUTPUT:
				outputDir = Path.of(requireArg(args, ++i, OPT_OUTPUT));
				break;
			default:
				System.err.println("Unknown option: " + args[i]);
				printUsage();
				return;
			}
		}

		if (configPath == null) {
			configPath = sandboxRoot.resolve(".github/refactoring-mining/repos.yml");
		}
		if (statePath == null) {
			statePath = sandboxRoot.resolve(".github/refactoring-mining/state.json");
		}

		System.out.println("=== Sandbox Mining Gemini ===");
		System.out.println("Config: " + configPath);
		System.out.println("State:  " + statePath);
		System.out.println("Output: " + outputDir);

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

		Path workDir = Files.createTempDirectory("gemini-mining-");
		try {
			processRepositories(config, state, workDir, batchSize, geminiClient,
					promptBuilder, dslContext, categoryManager, validator, stats, aggregator);
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

	private void processRepositories(MiningConfig config, MiningState state, Path workDir,
			int batchSize, GeminiClient geminiClient, GeminiPromptBuilder promptBuilder,
			String dslContext, CategoryManager categoryManager, DslValidator validator,
			StatisticsCollector stats, ReportAggregator aggregator) throws IOException, GitAPIException {
		RepoCloner cloner = new RepoCloner();

		for (RepoEntry repo : config.getRepositories()) {
			System.out.println("Processing: " + repo.getUrl());
			Path repoDir = workDir.resolve(repoDirectoryName(repo.getUrl()));
			cloner.cloneRepo(repo.getUrl(), repo.getBranch(), repoDir);

			String lastCommit = state.getLastProcessedCommit(repo.getUrl());
			try (CommitWalker walker = new CommitWalker(repoDir);
					DiffExtractor diffExtractor = new DiffExtractor(repoDir, config.getMaxDiffLinesPerCommit(),
							repo.getPaths())) {
				List<RevCommit> batch = walker.nextBatch(lastCommit, config.getStartDate(), batchSize);
				while (!batch.isEmpty()) {
					for (RevCommit commit : batch) {
						boolean success = processCommit(commit, repo, diffExtractor, geminiClient,
								promptBuilder, dslContext, categoryManager, validator, stats, aggregator);
						if (success) {
							state.updateLastProcessedCommit(repo.getUrl(), commit.getName());
						}
					}
					batch = walker.nextBatch(batch.get(batch.size() - 1).getName(),
							config.getStartDate(), batchSize);
				}
			}

			System.out.println("  Completed: " + repo.getUrl());
		}
	}

	private boolean processCommit(RevCommit commit, RepoEntry repo, DiffExtractor diffExtractor,
			GeminiClient geminiClient, GeminiPromptBuilder promptBuilder, String dslContext,
			CategoryManager categoryManager, DslValidator validator,
			StatisticsCollector stats, ReportAggregator aggregator) {
		try {
			String diff = diffExtractor.extractDiff(commit);
			if (diff.isBlank()) {
				return true;
			}
			String prompt = promptBuilder.buildPrompt(dslContext, categoryManager.getCategoriesJson(), diff,
					commit.getFullMessage());
			CommitEvaluation evaluation = geminiClient.evaluate(prompt, commit.getName(),
					commit.getFullMessage(), repo.getUrl());
			if (evaluation == null) {
				return true;
			}

			// Validate DSL rule if present
			if (evaluation.dslRule() != null && !evaluation.dslRule().isBlank()) {
				var validation = validator.validate(evaluation.dslRule());
				if (!validation.valid()) {
					System.out.println("  Invalid DSL rule for " + commit.getName()
							+ ": " + validation.message());
					if (Boolean.parseBoolean(System.getenv("GEMINI_DEBUG"))) {
						System.out.println("  --- DSL rule begin ---");
						System.out.println(evaluation.dslRule());
						System.out.println("  --- DSL rule end ---");
					}
				}
			}

			if (evaluation.isNewCategory() && evaluation.category() != null) {
				categoryManager.addCategory(evaluation.category());
			}

			stats.record(evaluation);
			aggregator.add(evaluation);
			return true;
		} catch (IOException e) {
			System.err.println("  Error processing commit " + commit.getName() + ": " + e.getMessage());
			return false;
		}
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
		System.out.println("Usage: java -jar sandbox-mining-gemini.jar [options]");
		System.out.println("Options:");
		System.out.println("  --config <path>        Path to repos.yml config file");
		System.out.println("  --state <path>         Path to state.json file");
		System.out.println("  --sandbox-root <path>  Root of sandbox repository");
		System.out.println("  --batch-size <n>       Number of commits per batch (default: 500)");
		System.out.println("  --output <path>        Output directory (default: output)");
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
