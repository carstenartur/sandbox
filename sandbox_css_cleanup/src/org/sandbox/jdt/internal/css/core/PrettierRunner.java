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
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

/**
 * Runs Prettier to format CSS files.
 */
public class PrettierRunner {

	private static final ILog LOG = Platform.getLog(PrettierRunner.class);

	/**
	 * Format a CSS file using Prettier.
	 * 
	 * @param file the CSS file to format
	 * @return the formatted content, or null if formatting failed
	 * @throws IOException if an I/O error occurs
	 * @throws InterruptedException if the thread is interrupted
	 */
	public static String format(IFile file) throws IOException, InterruptedException {
		if (!NodeExecutor.isNpxAvailable()) {
			throw new IllegalStateException("npx is not available. Please install Node.js."); //$NON-NLS-1$
		}

		Path filePath = file.getLocation().toFile().toPath();
		String originalContent = Files.readString(filePath, StandardCharsets.UTF_8);

		// Use prettier with stdin/stdout to avoid file modification during processing
		ProcessBuilder pb = new ProcessBuilder(
				"npx", "prettier", //$NON-NLS-1$ //$NON-NLS-2$
				"--parser", "css", //$NON-NLS-1$ //$NON-NLS-2$
				"--stdin-filepath", file.getName() //$NON-NLS-1$
		);

		Process process = pb.start();

		// Write content to stdin
		try (OutputStream os = process.getOutputStream()) {
			os.write(originalContent.getBytes(StandardCharsets.UTF_8));
		}

		// Read formatted output
		String formatted;
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append("\n"); //$NON-NLS-1$
			}
			formatted = sb.toString();
		}

		// Read any errors
		String errors;
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append("\n"); //$NON-NLS-1$
			}
			errors = sb.toString();
		}

		boolean finished = process.waitFor(30, TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
			LOG.log(new Status(IStatus.WARNING, "sandbox_css_cleanup", //$NON-NLS-1$
					"Prettier timed out after 30 seconds")); //$NON-NLS-1$
			return null;
		}

		int exitCode = process.exitValue();

		if (exitCode != 0) {
			LOG.log(new Status(IStatus.WARNING, "sandbox_css_cleanup", //$NON-NLS-1$
					"Prettier failed: " + errors)); //$NON-NLS-1$
			return null;
		}

		return formatted;
	}

	/**
	 * Check if Prettier is available (installed globally or via npx).
	 */
	public static boolean isPrettierAvailable() {
		try {
			NodeExecutor.ExecutionResult result = NodeExecutor.executeNpx("prettier", "--version"); //$NON-NLS-1$ //$NON-NLS-2$
			return result.isSuccess();
		} catch (Exception e) {
			return false;
		}
	}
}
