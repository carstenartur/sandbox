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
		Process process = null;
		try {
			ProcessBuilder pb = new ProcessBuilder("node", "--version"); //$NON-NLS-1$ //$NON-NLS-2$
			process = pb.start();
			boolean finished = process.waitFor(5, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				return false;
			}
			return process.exitValue() == 0;
		} catch (IOException | InterruptedException e) {
			if (process != null) {
				process.destroyForcibly();
			}
			return false;
		}
	}

	/**
	 * Check if npx is available.
	 */
	public static boolean isNpxAvailable() {
		Process process = null;
		try {
			ProcessBuilder pb = new ProcessBuilder("npx", "--version"); //$NON-NLS-1$ //$NON-NLS-2$
			process = pb.start();
			boolean finished = process.waitFor(5, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				return false;
			}
			return process.exitValue() == 0;
		} catch (IOException | InterruptedException e) {
			if (process != null) {
				process.destroyForcibly();
			}
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
		try (StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream());
			 StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream())) {
			// Use StreamGobbler to read streams concurrently and avoid deadlock
			outputGobbler.start();
			errorGobbler.start();

			boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				throw new IOException("Process timed out after " + TIMEOUT_SECONDS + " seconds"); //$NON-NLS-1$ //$NON-NLS-2$
			}

			outputGobbler.join(1000);
			errorGobbler.join(1000);

			return new ExecutionResult(process.exitValue(), outputGobbler.getOutput(), errorGobbler.getOutput());
		} finally {
			process.destroy();
		}
	}

	/**
	 * Helper class to read stream output in a separate thread to avoid deadlock.
	 * Implements AutoCloseable to ensure the input stream is closed even if the thread doesn't run.
	 */
	private static class StreamGobbler extends Thread implements AutoCloseable {
		private final InputStream inputStream;
		private final StringBuilder output = new StringBuilder();
		private volatile boolean streamConsumed = false;

		StreamGobbler(InputStream inputStream) {
			this.inputStream = inputStream;
		}

		@Override
		public void run() {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
				streamConsumed = true;
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line).append("\n"); //$NON-NLS-1$
				}
			} catch (IOException e) {
				LOG.log(new org.eclipse.core.runtime.Status(org.eclipse.core.runtime.IStatus.WARNING, 
						"sandbox_css_cleanup", "Error reading stream", e)); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		@Override
		public void close() {
			// If run() was never called or didn't complete, close the stream explicitly
			if (!streamConsumed) {
				try {
					inputStream.close();
				} catch (IOException e) {
					// Ignore close errors - stream will be closed when process is destroyed anyway
				}
			}
		}

		String getOutput() {
			return output.toString();
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
