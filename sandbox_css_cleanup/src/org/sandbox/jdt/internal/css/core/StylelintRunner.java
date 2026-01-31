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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

/**
 * Runs Stylelint to validate CSS files.
 */
public class StylelintRunner {

	private static final ILog LOG = Platform.getLog(StylelintRunner.class);

	/**
	 * Validate a CSS file using Stylelint.
	 * 
	 * @param file the CSS file to validate
	 * @return validation results
	 */
	public static CSSValidationResult validate(IFile file) throws Exception {
		if (!NodeExecutor.isNpxAvailable()) {
			throw new IllegalStateException("npx is not available. Please install Node.js."); //$NON-NLS-1$
		}

		Path filePath = file.getLocation().toFile().toPath();

		// Run stylelint with JSON output for easier parsing
		NodeExecutor.ExecutionResult result = NodeExecutor.executeNpx(
				"stylelint", //$NON-NLS-1$
				filePath.toString(),
				"--formatter", "json" //$NON-NLS-1$ //$NON-NLS-2$
		);

		return parseStylelintOutput(result.stdout, result.exitCode);
	}

	/**
	 * Fix CSS issues automatically using Stylelint --fix.
	 * 
	 * @param file the CSS file to fix
	 * @return the fixed content, or original content if fixing failed
	 * @throws IOException if an I/O error occurs
	 * @throws InterruptedException if the thread is interrupted
	 */
	public static String fix(IFile file) throws IOException, InterruptedException {
		if (!NodeExecutor.isNpxAvailable()) {
			throw new IllegalStateException("npx is not available. Please install Node.js."); //$NON-NLS-1$
		}

		Path filePath = file.getLocation().toFile().toPath();
		String originalContent = Files.readString(filePath, StandardCharsets.UTF_8);

		// Run stylelint with --fix via stdin/stdout
		ProcessBuilder pb = new ProcessBuilder(
				"npx", "stylelint", //$NON-NLS-1$ //$NON-NLS-2$
				"--fix", //$NON-NLS-1$
				"--stdin-filename", file.getName() //$NON-NLS-1$
		);

		Process process = pb.start();

		try (OutputStream os = process.getOutputStream()) {
			os.write(originalContent.getBytes(StandardCharsets.UTF_8));
		}

		String fixed;
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append("\n"); //$NON-NLS-1$
			}
			fixed = sb.toString();
		}

		String errorOutput;
		try (BufferedReader errorReader = new BufferedReader(
				new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
			StringBuilder errorSb = new StringBuilder();
			String errorLine;
			while ((errorLine = errorReader.readLine()) != null) {
				errorSb.append(errorLine).append("\n"); //$NON-NLS-1$
			}
			errorOutput = errorSb.toString();
		}

		boolean finished = process.waitFor(30, TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
			LOG.log(new Status(IStatus.WARNING, "sandbox_css_cleanup", //$NON-NLS-1$
					"stylelint --fix timed out after 30 seconds")); //$NON-NLS-1$
			return originalContent;
		}

		int exitCode = process.exitValue();

		if (exitCode != 0) {
			LOG.log(new Status(IStatus.WARNING, "sandbox_css_cleanup", //$NON-NLS-1$
					"stylelint --fix failed with exit code " + exitCode + " and error output:\n" + errorOutput)); //$NON-NLS-1$ //$NON-NLS-2$
			return originalContent;
		}

		return fixed.isEmpty() ? originalContent : fixed;
	}

	private static CSSValidationResult parseStylelintOutput(String jsonOutput, int exitCode) {
		List<CSSValidationResult.Issue> issues = new ArrayList<>();

		// Simple parsing - in production use proper JSON parser
		if (exitCode == 0) {
			return new CSSValidationResult(true, issues);
		}

		// Parse JSON array of results
		// Each result has: source, warnings[], errored
		// Each warning has: line, column, rule, severity, text

		try {
			// Simplified parsing - extract line/column/message
			// In real implementation, use Gson or Jackson
			if (jsonOutput.contains("\"errored\":true")) { //$NON-NLS-1$
				issues.add(new CSSValidationResult.Issue(
						1, 1, "error", "stylelint", "CSS validation errors found. Run stylelint for details." //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				));
			}
		} catch (Exception e) {
			LOG.log(new Status(IStatus.WARNING, "sandbox_css_cleanup", //$NON-NLS-1$
					"Failed to parse stylelint output", e)); //$NON-NLS-1$
		}

		return new CSSValidationResult(false, issues);
	}

	/**
	 * Check if Stylelint is available.
	 */
	public static boolean isStylelintAvailable() {
		try {
			NodeExecutor.ExecutionResult result = NodeExecutor.executeNpx("stylelint", "--version"); //$NON-NLS-1$ //$NON-NLS-2$
			return result.isSuccess();
		} catch (Exception e) {
			return false;
		}
	}
}
