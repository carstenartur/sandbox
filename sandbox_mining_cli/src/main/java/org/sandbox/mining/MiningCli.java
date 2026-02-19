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
package org.sandbox.mining;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuards;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;
import org.sandbox.mining.config.MiningConfig;
import org.sandbox.mining.config.RepoEntry;
import org.sandbox.mining.git.RepoCloner;
import org.sandbox.mining.report.JsonReporter;
import org.sandbox.mining.report.MarkdownReporter;
import org.sandbox.mining.report.MiningReport;
import org.sandbox.mining.scanner.SourceScanner;
import org.sandbox.mining.scanner.StandaloneAstParser;

/**
 * CLI entry point for the refactoring mining tool.
 *
 * <p>Usage:
 * <pre>
 * java -jar sandbox-mining-cli.jar [options]
 *   --config &lt;path&gt;    Path to repos.yml configuration file
 *   --hints &lt;dir&gt;       Directory with .sandbox-hint files (overrides config)
 *   --repo &lt;url&gt;        Single repo to scan (ad-hoc mode, overrides config)
 *   --output &lt;dir&gt;      Output directory for reports (default: mining-results)
 *   --format &lt;fmt&gt;      Report format: markdown|json|both (default: both)
 *   --dry-run            Only count matches, don't generate candidate files
 * </pre>
 */
public class MiningCli {

	private static final String DEFAULT_OUTPUT = "mining-results";
	private static final String FORMAT_MARKDOWN = "markdown";
	private static final String FORMAT_JSON = "json";
	private static final String FORMAT_BOTH = "both";

	/** Map of bundled hint names to their classpath resource paths. */
	private static final Map<String, String> BUNDLED_HINTS = Map.of(
		"collections", "/org/sandbox/jdt/triggerpattern/internal/collections.sandbox-hint",
		"modernize-java11", "/org/sandbox/jdt/triggerpattern/internal/modernize-java11.sandbox-hint",
		"modernize-java9", "/org/sandbox/jdt/triggerpattern/internal/modernize-java9.sandbox-hint",
		"performance", "/org/sandbox/jdt/triggerpattern/internal/performance.sandbox-hint"
	);

	public static void main(String[] args) {
		try {
			int exitCode = run(args);
			System.exit(exitCode);
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	static int run(String[] args) throws IOException, GitAPIException, HintParseException {
		// Parse arguments
		String configPath = null;
		String hintsDir = null;
		String repoUrl = null;
		String outputDir = DEFAULT_OUTPUT;
		String format = FORMAT_BOTH;
		boolean dryRun = false;

		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			case "--config":
				configPath = args[++i];
				break;
			case "--hints":
				hintsDir = args[++i];
				break;
			case "--repo":
				repoUrl = args[++i];
				break;
			case "--output":
				outputDir = args[++i];
				break;
			case "--format":
				format = args[++i];
				break;
			case "--dry-run":
				dryRun = true;
				break;
			case "--help":
				printUsage();
				return 0;
			default:
				System.err.println("Unknown option: " + args[i]);
				printUsage();
				return 1;
			}
		}

		// Initialize guard functions
		initializeGuards();

		// Load configuration
		MiningConfig config;
		if (configPath != null) {
			config = MiningConfig.parse(Path.of(configPath));
		} else {
			config = new MiningConfig();
		}

		// Override with ad-hoc repo if specified
		List<RepoEntry> repos;
		if (repoUrl != null) {
			RepoEntry entry = new RepoEntry(repoUrl, "main", List.of());
			repos = List.of(entry);
		} else {
			repos = config.getRepositories();
		}

		if (repos.isEmpty()) {
			System.err.println("No repositories to scan. Use --config or --repo.");
			return 1;
		}

		// Load hint files
		List<HintFile> hintFiles = loadHintFiles(config, hintsDir);
		if (hintFiles.isEmpty()) {
			System.err.println("No hint files found. Check your configuration.");
			return 1;
		}

		System.out.println("Mining with " + hintFiles.size() + " hint file(s) against " + repos.size() + " repository(ies).");

		// Set up scanner
		StandaloneAstParser astParser = new StandaloneAstParser();
		SourceScanner scanner = new SourceScanner(astParser, config.getMaxFilesPerRepo());
		RepoCloner cloner = new RepoCloner();

		// Process each repository
		MiningReport totalReport = new MiningReport();

		for (RepoEntry repo : repos) {
			String repoName = extractRepoName(repo.getUrl());
			System.out.println("Scanning: " + repoName + " ...");

			Path tempDir = Files.createTempDirectory("mining-" + repoName);
			try {
				cloner.shallowClone(repo.getUrl(), repo.getBranch(), tempDir);
				MiningReport repoReport = scanner.scan(repoName, tempDir, repo.getPaths(), hintFiles);
				totalReport.merge(repoReport);

				int matchCount = repoReport.getMatches().size();
				System.out.println("  Found " + matchCount + " match(es) in " + repoReport.getFileCounts().getOrDefault(repoName, 0) + " file(s).");
			} catch (Exception e) {
				System.err.println("  Error scanning " + repoName + ": " + e.getMessage());
				totalReport.addError(repoName, e.getMessage());
				totalReport.addFileCount(repoName, 0);
			} finally {
				deleteDirectory(tempDir);
			}
		}

		// Generate reports
		if (!dryRun) {
			Path output = Path.of(outputDir);
			writeReports(totalReport, output, format);
		}

		System.out.println("\nTotal: " + totalReport.getMatches().size() + " match(es) found.");
		return 0;
	}

	private static void initializeGuards() {
		Map<String, GuardFunction> guards = new HashMap<>();
		BuiltInGuards.registerAll(guards);
		GuardFunctionResolverHolder.setResolver(guards::get);
	}

	private static List<HintFile> loadHintFiles(MiningConfig config, String hintsDirOverride)
			throws IOException, HintParseException {
		List<HintFile> hintFiles = new ArrayList<>();
		HintFileParser parser = new HintFileParser();

		if (hintsDirOverride != null) {
			// Load all .sandbox-hint files from the given directory
			Path hintsPath = Path.of(hintsDirOverride);
			if (Files.isDirectory(hintsPath)) {
				try (var stream = Files.walk(hintsPath)) {
					List<Path> hintPaths = stream.filter(p -> p.toString().endsWith(".sandbox-hint")).toList();
					for (Path p : hintPaths) {
						String content = Files.readString(p, StandardCharsets.UTF_8);
						hintFiles.add(parser.parse(content));
					}
				}
			}
			return hintFiles;
		}

		// Load hints from configuration
		for (String hint : config.getHints()) {
			if (hint.startsWith("bundled:")) {
				String name = hint.substring("bundled:".length());
				HintFile hf = loadBundledHint(parser, name);
				if (hf != null) {
					hintFiles.add(hf);
				}
			} else if (hint.startsWith("path:")) {
				String path = hint.substring("path:".length());
				Path p = Path.of(path);
				if (Files.isRegularFile(p)) {
					String content = Files.readString(p, StandardCharsets.UTF_8);
					hintFiles.add(parser.parse(content));
				} else {
					System.err.println("Warning: Hint file not found: " + path);
				}
			} else {
				// Try as bundled first, then as path
				HintFile hf = loadBundledHint(parser, hint);
				if (hf == null) {
					Path p = Path.of(hint);
					if (Files.isRegularFile(p)) {
						String content = Files.readString(p, StandardCharsets.UTF_8);
						hf = parser.parse(content);
					}
				}
				if (hf != null) {
					hintFiles.add(hf);
				}
			}
		}

		// If no hints configured, load all bundled hints
		if (hintFiles.isEmpty()) {
			for (String name : BUNDLED_HINTS.keySet()) {
				HintFile hf = loadBundledHint(parser, name);
				if (hf != null) {
					hintFiles.add(hf);
				}
			}
		}

		return hintFiles;
	}

	private static HintFile loadBundledHint(HintFileParser parser, String name) {
		String resourcePath = BUNDLED_HINTS.get(name);
		if (resourcePath == null) {
			System.err.println("Warning: Unknown bundled hint: " + name);
			return null;
		}
		try (InputStream is = MiningCli.class.getResourceAsStream(resourcePath)) {
			if (is == null) {
				System.err.println("Warning: Bundled hint resource not found: " + resourcePath);
				return null;
			}
			try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
				return parser.parse(reader);
			}
		} catch (IOException e) {
			System.err.println("Warning: Error loading bundled hint " + name + ": " + e.getMessage());
			return null;
		} catch (HintParseException e) {
			System.err.println("Warning: Error parsing bundled hint " + name + ": " + e.getMessage());
			return null;
		}
	}

	static String extractRepoName(String url) {
		if (url == null) {
			return "unknown";
		}
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

	private static void writeReports(MiningReport report, Path outputDir, String format) throws IOException {
		switch (format) {
		case FORMAT_MARKDOWN:
			new MarkdownReporter().write(report, outputDir);
			System.out.println("Report written to " + outputDir.resolve("report.md"));
			break;
		case FORMAT_JSON:
			new JsonReporter().write(report, outputDir);
			System.out.println("Report written to " + outputDir.resolve("report.json"));
			break;
		case FORMAT_BOTH:
		default:
			new MarkdownReporter().write(report, outputDir);
			new JsonReporter().write(report, outputDir);
			System.out.println("Reports written to " + outputDir);
			break;
		}
	}

	private static void deleteDirectory(Path dir) {
		try {
			if (Files.exists(dir)) {
				try (var stream = Files.walk(dir)) {
					stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
						try {
							Files.delete(p);
						} catch (IOException e) {
							// Ignore cleanup errors
						}
					});
				}
			}
		} catch (IOException e) {
			// Ignore cleanup errors
		}
	}

	private static void printUsage() {
		System.out.println("Usage: java -jar sandbox-mining-cli.jar [options]");
		System.out.println();
		System.out.println("Options:");
		System.out.println("  --config <path>    Path to repos.yml configuration file");
		System.out.println("  --hints <dir>      Directory with .sandbox-hint files (overrides config)");
		System.out.println("  --repo <url>       Single repo to scan (ad-hoc mode, overrides config)");
		System.out.println("  --output <dir>     Output directory for reports (default: mining-results)");
		System.out.println("  --format <fmt>     Report format: markdown|json|both (default: both)");
		System.out.println("  --dry-run          Only count matches, don't generate candidate files");
		System.out.println("  --help             Show this help message");
	}
}
