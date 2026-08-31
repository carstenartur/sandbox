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
package org.sandbox.jdt.internal.css.core;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.sandbox.jdt.internal.css.CSSCleanupPlugin;
import org.sandbox.jdt.internal.css.preferences.CSSPreferenceConstants;

/** Runs Stylelint to validate and fix CSS files. */
public class StylelintRunner {

	private static final int LINT_PROBLEMS_EXIT_CODE = 2;

	/** Validate a CSS file using Stylelint and the configured config file. */
	public static CSSValidationResult validate(IFile file) throws Exception {
		String configPath = configuredStylelintConfig();
		return validate(file, configPath);
	}

	static CSSValidationResult validate(IFile file, String configPath) throws Exception {
		if (!isStylelintAvailable()) {
			throw new IllegalStateException("Stylelint is not available in the configured Node.js toolchain"); //$NON-NLS-1$
		}

		Path filePath = localPath(file);
		List<String> args = buildValidateArguments(filePath.toString(), configPath);
		NodeExecutor.ExecutionResult result = NodeExecutor.executeTool(
				NodeExecutor.Tool.STYLELINT, args.toArray(String[]::new));
		String report = extractJsonReport(result.stderr);
		if (report == null) {
			report = extractJsonReport(result.stdout);
		}

		CSSValidationResult validation;
		if (report != null) {
			try {
				validation = parseStylelintOutput(report);
			} catch (IllegalArgumentException e) {
				throw new IOException("Could not parse Stylelint JSON output: " + e.getMessage() //$NON-NLS-1$
						+ diagnosticSuffix(result), e);
			}
		} else {
			validation = new CSSValidationResult(true, List.of());
		}

		if (result.exitCode != 0 && result.exitCode != LINT_PROBLEMS_EXIT_CODE) {
			throw new IOException(toolFailure("Stylelint", result)); //$NON-NLS-1$
		}
		if (result.exitCode == LINT_PROBLEMS_EXIT_CODE && validation.getIssues().isEmpty()) {
			throw new IOException("Stylelint reported lint problems but returned no JSON diagnostics" //$NON-NLS-1$
					+ diagnosticSuffix(result));
		}
		return validation;
	}

	/** Fix CSS issues using Stylelint --fix and the configured config file. */
	public static String fix(IFile file) throws IOException, InterruptedException {
		String configPath = configuredStylelintConfig();
		return fix(file, configPath);
	}

	static String fix(IFile file, String configPath) throws IOException, InterruptedException {
		if (!isStylelintAvailable()) {
			throw new IllegalStateException("Stylelint is not available in the configured Node.js toolchain"); //$NON-NLS-1$
		}

		Path filePath = localPath(file);
		String originalContent = Files.readString(filePath, StandardCharsets.UTF_8);
		List<String> args = buildFixArguments(filePath.toString(), configPath);
		NodeExecutor.ExecutionResult result = NodeExecutor.executeToolWithInput(
				NodeExecutor.Tool.STYLELINT, originalContent, args.toArray(String[]::new));

		if (result.exitCode != 0 && result.exitCode != LINT_PROBLEMS_EXIT_CODE) {
			throw new IOException(toolFailure("Stylelint --fix", result)); //$NON-NLS-1$
		}
		return result.stdout.isEmpty() ? originalContent : result.stdout;
	}

	static List<String> buildValidateArguments(String filePath, String configPath) {
		List<String> args = new ArrayList<>(List.of(filePath, "--formatter", "json")); //$NON-NLS-1$ //$NON-NLS-2$
		appendConfig(args, configPath);
		return List.copyOf(args);
	}

	static List<String> buildFixArguments(String filePath, String configPath) {
		List<String> args = new ArrayList<>(List.of(
				"--stdin", "--stdin-filename", filePath, "--fix")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		appendConfig(args, configPath);
		return List.copyOf(args);
	}

	static CSSValidationResult parseStylelintOutput(String jsonOutput) {
		Object parsed = SimpleJsonParser.parse(jsonOutput);
		if (!(parsed instanceof List<?> results)) {
			throw new IllegalArgumentException("Expected a Stylelint JSON result array"); //$NON-NLS-1$
		}

		List<CSSValidationResult.Issue> issues = new ArrayList<>();
		for (Object resultValue : results) {
			if (!(resultValue instanceof Map<?, ?> result)) {
				continue;
			}
			collectWarnings(result.get("warnings"), issues); //$NON-NLS-1$
			collectSpecialIssues(result.get("parseErrors"), "error", "parse-error", issues); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			collectSpecialIssues(result.get("invalidOptionWarnings"), "error", "invalid-option", issues); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		return new CSSValidationResult(issues.isEmpty(), issues);
	}

	static String extractJsonReport(String output) {
		if (output == null || output.isBlank()) {
			return null;
		}
		for (int start = output.indexOf('['); start >= 0; start = output.indexOf('[', start + 1)) {
			for (int end = output.lastIndexOf(']'); end > start; end = output.lastIndexOf(']', end - 1)) {
				String candidate = output.substring(start, end + 1);
				try {
					if (SimpleJsonParser.parse(candidate) instanceof List<?>) {
						return candidate;
					}
				} catch (IllegalArgumentException e) {
					// stderr can contain notices before the formatter payload.
				}
			}
		}
		return null;
	}

	/** Check if Stylelint is available. */
	public static boolean isStylelintAvailable() {
		return NodeExecutor.isToolAvailable(NodeExecutor.Tool.STYLELINT);
	}

	private static void collectWarnings(Object warningValue, List<CSSValidationResult.Issue> issues) {
		if (!(warningValue instanceof List<?> warnings)) {
			return;
		}
		for (Object warningValueEntry : warnings) {
			if (!(warningValueEntry instanceof Map<?, ?> warning)) {
				continue;
			}
			issues.add(new CSSValidationResult.Issue(
					integer(warning.get("line"), 1), //$NON-NLS-1$
					integer(warning.get("column"), 1), //$NON-NLS-1$
					string(warning.get("severity"), "warning"), //$NON-NLS-1$ //$NON-NLS-2$
					string(warning.get("rule"), "stylelint"), //$NON-NLS-1$ //$NON-NLS-2$
					string(warning.get("text"), "Stylelint problem"))); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static void collectSpecialIssues(Object value, String severity, String rule,
			List<CSSValidationResult.Issue> issues) {
		if (!(value instanceof List<?> entries)) {
			return;
		}
		for (Object entryValue : entries) {
			if (entryValue instanceof String text && !text.isBlank()) {
				issues.add(new CSSValidationResult.Issue(1, 1, severity, rule, text));
				continue;
			}
			if (!(entryValue instanceof Map<?, ?> entry)) {
				continue;
			}
			issues.add(new CSSValidationResult.Issue(
					integer(entry.get("line"), 1), //$NON-NLS-1$
					integer(entry.get("column"), 1), //$NON-NLS-1$
					severity,
					rule,
					string(entry.get("text"), string(entry.get("message"), "Stylelint problem")))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
	}

	private static int integer(Object value, int fallback) {
		if (value instanceof BigDecimal number) {
			try {
				return number.intValueExact();
			} catch (ArithmeticException e) {
				return fallback;
			}
		}
		return fallback;
	}

	private static String string(Object value, String fallback) {
		return value instanceof String text && !text.isBlank() ? text : fallback;
	}

	private static void appendConfig(List<String> args, String configPath) {
		if (configPath != null && !configPath.isBlank()) {
			args.add("--config"); //$NON-NLS-1$
			args.add(configPath);
		}
	}

	private static String configuredStylelintConfig() {
		String configured = CSSCleanupPlugin.getDefault().getPreferenceStore()
				.getString(CSSPreferenceConstants.STYLELINT_CONFIG);
		return configured == null ? "" : configured.trim(); //$NON-NLS-1$
	}

	private static Path localPath(IFile file) throws IOException {
		if (file.getLocation() == null) {
			throw new IOException("CSS file has no local filesystem location: " + file.getFullPath()); //$NON-NLS-1$
		}
		return file.getLocation().toFile().toPath();
	}

	private static String toolFailure(String tool, NodeExecutor.ExecutionResult result) {
		String detail = !result.stderr.isBlank() ? result.stderr.trim() : result.stdout.trim();
		return detail.isBlank()
				? tool + " failed with exit code " + result.exitCode //$NON-NLS-1$
				: tool + " failed with exit code " + result.exitCode + ": " + detail; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String diagnosticSuffix(NodeExecutor.ExecutionResult result) {
		String detail = !result.stderr.isBlank() ? result.stderr.trim() : result.stdout.trim();
		return detail.isBlank() ? "" : ". Tool output: " + detail; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
