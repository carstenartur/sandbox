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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;

/**
 * Utility for executing Node.js/npm commands.
 */
public class NodeExecutor {

	private static final int TIMEOUT_SECONDS = 30;
	private static final ILog LOG = Platform.getLog(NodeExecutor.class);

	/**
	 * Check if Node.js is available on the system.
	 */
	public static boolean isNodeAvailable() {
		try {
			ProcessBuilder pb = new ProcessBuilder("node", "--version"); //$NON-NLS-1$ //$NON-NLS-2$
			Process process = pb.start();
			boolean finished = process.waitFor(5, TimeUnit.SECONDS);
			return finished && process.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Check if npx is available.
	 */
	public static boolean isNpxAvailable() {
		try {
			ProcessBuilder pb = new ProcessBuilder("npx", "--version"); //$NON-NLS-1$ //$NON-NLS-2$
			Process process = pb.start();
			boolean finished = process.waitFor(5, TimeUnit.SECONDS);
			return finished && process.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Execute an npx command and return the output.
	 */
	public static ExecutionResult executeNpx(String... args) throws IOException, InterruptedException {
		String[] command = new String[args.length + 1];
		command[0] = "npx"; //$NON-NLS-1$
		System.arraycopy(args, 0, command, 1, args.length);

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(false);

		Process process = pb.start();

		String stdout = readStream(process.getInputStream());
		String stderr = readStream(process.getErrorStream());

		boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
			throw new IOException("Process timed out after " + TIMEOUT_SECONDS + " seconds"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return new ExecutionResult(process.exitValue(), stdout, stderr);
	}

	private static String readStream(InputStream is) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append("\n"); //$NON-NLS-1$
			}
			return sb.toString();
		}
	}

	/**
	 * Result of a command execution.
	 */
	public static class ExecutionResult {
		public final int exitCode;
		public final String stdout;
		public final String stderr;

		public ExecutionResult(int exitCode, String stdout, String stderr) {
			this.exitCode = exitCode;
			this.stdout = stdout;
			this.stderr = stderr;
		}

		public boolean isSuccess() {
			return exitCode == 0;
		}
	}
}
