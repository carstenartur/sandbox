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

	private static final String DEFAULT_OUTPUT = "mining-results"; //$NON-NLS-1$
	private static final String FORMAT_MARKDOWN = "markdown"; //$NON-NLS-1$
	private static final String FORMAT_JSON = "json"; //$NON-NLS-1$
	private static final String FORMAT_BOTH = "both"; //$NON-NLS-1$
	private static final String COMPILER_SOURCE_OPTION = "org.eclipse.jdt.core.compiler.source"; //$NON-NLS-1$

	/** Map of bundled hint names to their classpath resource paths. */
	private static final Map<String, String> BUNDLED_HINTS = Map.ofEntries(
			Map.entry("arrays", "/org/sandbox/jdt/triggerpattern/internal/arrays.sandbox-hint"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("collection-performance", "/org/sandbox/jdt/triggerpattern/internal/collection-performance.sandbox-hint"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("collections", "/org/sandbox/jdt/triggerpattern/internal/collections.sandbox-hint"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("deprecations", "/org/sandbox/jdt/triggerpattern/internal/deprecations.sandbox-hint"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("deprecated-api", "/org/sandbox/jdt/triggerpattern/internal/deprecations.sandbox-hint"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("io-performance", "/org/sandbox/jdt/triggerpattern/internal/io-performance.sandbox-hint"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("modernize-java11", "/org/sandbox/jdt/triggerpattern/internal/modernize-java11.sandbox-hint"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("modernize-java9", "/org/sandbox/jdt/triggerpattern/internal/modernize-java9.sandbox-hint"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("performance", "/org/sandbox/jdt/triggerpattern/internal/performance.sandbox-hint"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("probable-bugs", "/org/sandbox/jdt/triggerpattern/internal/probable-bugs.sandbox-hint"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("stream-performance", "/org/sandbox/jdt/triggerpattern/internal/stream-performance.sandbox-hint"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("string-equals", "/org/sandbox/jdt/triggerpattern/internal/string-equals.sandbox-hint"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("string-isblank", "/org/sandbox/jdt/triggerpattern/internal/string-isblank.sandbox-hint"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("try-with-resources", "/org/sandbox/jdt/triggerpattern/internal/try-with-resources.sandbox-hint")); //$NON-NLS-1$ //$NON-NLS-2$

	public static void main(String[] args) {
		try {
			int exitCode = run(args);
			System.exit(exitCode);
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage()); //$NON-NLS-1$
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
			case "--config": //$NON-NLS-1$
				configPath = args[++i];
				break;
			case "--hints": //$NON-NLS-1$
				hintsDir = args[++i];
				break;
			case "--repo": //$NON-NLS-1$
				repoUrl = args[++i];
				break;
			case "--output": //$NON-NLS-1$
				outputDir = args[++i];
				break;
			case "--format": //$NON-NLS-1$
				format = args[++i];
				break;
			case "--dry-run": //$NON-NLS-1$
				dryRun = true;
				break;
			case "--help": //$NON-NLS-1$
				printUsage();
				return 0;
			default:
				System.err.println("Unknown option: " + args[i]); //$NON-NLS-1$
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
		Map<String, String> compilerOptions = Map.of(COMPILER_SOURCE_OPTION, config.getSourceVersion());

		// Override with ad-hoc repo if specified
		List<RepoEntry> repos;
		if (repoUrl != null) {
			RepoEntry entry = new RepoEntry(repoUrl, "main", List.of()); //$NON-NLS-1$
			repos = List.of(entry);
		} else {
			repos = config.getRepositories();
		}

		if (repos.isEmpty()) {
			System.err.println("No repositories to scan. Use --config or --repo."); //$NON-NLS-1$
			return 1;
		}

		// Load hint files
		List<HintFile> hintFiles = loadHintFiles(config, hintsDir);
		if (hintFiles.isEmpty()) {
			System.err.println("No hint files found. Check your configuration."); //$NON-NLS-1$
			return 1;
		}

		System.out.println("Mining with " + hintFiles.size() + " hint file(s) against " + repos.size() //$NON-NLS-1$ //$NON-NLS-2$
				+ " repository(ies), source level " + config.getSourceVersion() + '.'); //$NON-NLS-1$

		// Set up scanner
		StandaloneAstParser astParser = new StandaloneAstParser();
		SourceScanner scanner = new SourceScanner(astParser, config.getMaxFilesPerRepo());
		RepoCloner cloner = new RepoCloner();

		// Process each repository
		MiningReport totalReport = new MiningReport();

		for (RepoEntry repo : repos) {
			String repoName = extractRepoName(repo.getUrl());
			System.out.println("Scanning: " + repoName + " ..."); //$NON-NLS-1$ //$NON-NLS-2$

			Path tempDir = Files.createTempDirectory("mining-" + repoName); //$NON-NLS-1$
			try {
				cloner.shallowClone(repo.getUrl(), repo.getBranch(), tempDir);
				MiningReport repoReport = scanner.scan(repoName, tempDir, repo.getPaths(), hintFiles, compilerOptions);
				totalReport.merge(repoReport);

				int matchCount = repoReport.getMatches().size();
				System.out.println("  Found " + matchCount + " match(es) in " //$NON-NLS-1$ //$NON-NLS-2$
						+ repoReport.getFileCounts().getOrDefault(repoName, 0) + " file(s)."); //$NON-NLS-1$
			} catch (Exception e) {
				System.err.println("  Error scanning " + repoName + ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
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

		System.out.println("\nTotal: " + totalReport.getMatches().size() + " match(es) found."); //$NON-NLS-1$ //$NON-NLS-2$
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
					List<Path> hintPaths = stream.filter(p -> p.toString().endsWith(".sandbox-hint")).toList(); //$NON-NLS-1$
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
			if (hint.startsWith("bundled:")) { //$NON-NLS-1$
				String name = hint.substring("bundled:".length()); //$NON-NLS-1$
				HintFile hf = loadBundledHint(parser, name);
				if (hf != null) {
					hintFiles.add(hf);
				}
			} else if (hint.startsWith("path:")) { //$NON-NLS-1$
				String path = hint.substring("path:".length()); //$NON-NLS-1$
				Path p = Path.of(path);
				if (Files.isRegularFile(p)) {
					String content = Files.readString(p, StandardCharsets.UTF_8);
					hintFiles.add(parser.parse(content));
				} else {
					System.err.println("Warning: Hint file not found: " + path); //$NON-NLS-1$
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

		// If no hints configured, load all bundled hints known to the mining CLI.
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
			System.err.println("Warning: Unknown bundled hint: " + name); //$NON-NLS-1$
			return null;
		}
		try (InputStream is = MiningCli.class.getResourceAsStream(resourcePath)) {
			if (is == null) {
				System.err.println("Warning: Bundled hint resource not found: " + resourcePath); //$NON-NLS-1$
				return null;
			}
			try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
				return parser.parse(reader);
			}
		} catch (IOException e) {
			System.err.println("Warning: Error loading bundled hint " + name + ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			return null;
		} catch (HintParseException e) {
			System.err.println("Warning: Error parsing bundled hint " + name + ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			return null;
		}
	}

	static String extractRepoName(String url) {
		if (url == null) {
			return "unknown"; //$NON-NLS-1$
		}
		String name = url;
		if (name.endsWith(".git")) { //$NON-NLS-1$
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
			System.out.println("Report written to " + outputDir.resolve("report.md")); //$NON-NLS-1$ //$NON-NLS-2$
			break;
		case FORMAT_JSON:
			new JsonReporter().write(report, outputDir);
			System.out.println("Report written to " + outputDir.resolve("report.json")); //$NON-NLS-1$ //$NON-NLS-2$
			break;
		case FORMAT_BOTH:
		default:
			new MarkdownReporter().write(report, outputDir);
			new JsonReporter().write(report, outputDir);
			System.out.println("Reports written to " + outputDir); //$NON-NLS-1$
			break;
		}
	}

	private static void deleteDirectory(Path dir) {
		if (dir == null || !Files.exists(dir)) {
			return;
		}
		try {
			Files.walk(dir)
					.sorted((a, b) -> b.compareTo(a)) // delete children first
					.forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (IOException e) {
							// ignore cleanup errors
						}
					});
		} catch (IOException e) {
			// ignore cleanup errors
		}
	}

	private static void printUsage() {
		System.out.println("Usage: java -jar sandbox-mining-cli.jar [options]"); //$NON-NLS-1$
		System.out.println("  --config <path>    Path to repos.yml configuration file"); //$NON-NLS-1$
		System.out.println("  --hints <dir>      Directory with .sandbox-hint files"); //$NON-NLS-1$
		System.out.println("  --repo <url>       Single repo to scan"); //$NON-NLS-1$
		System.out.println("  --output <dir>     Output directory (default: mining-results)"); //$NON-NLS-1$
		System.out.println("  --format <fmt>     markdown|json|both (default: both)"); //$NON-NLS-1$
		System.out.println("  --dry-run          Only count matches"); //$NON-NLS-1$
	}
}
