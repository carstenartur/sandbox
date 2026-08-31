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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;

/**
 * Utility for executing Node.js/npm commands.
 */
public class NodeExecutor {

	static final String NODE_INSTALL_DIRECTORY_PROPERTY = "sandbox.css.node.installDirectory"; //$NON-NLS-1$
	static final String NODE_MODULES_DIRECTORY_PROPERTY = "sandbox.css.node.modulesDirectory"; //$NON-NLS-1$

	private static final String PRETTIER_COMMAND = "prettier"; //$NON-NLS-1$
	private static final String STYLELINT_COMMAND = "stylelint"; //$NON-NLS-1$
	private static final int TIMEOUT_SECONDS = 30;
	private static final ILog LOG = Platform.getLog(NodeExecutor.class);

	/**
	 * Check if Node.js is available on the system or in the Maven-owned test toolchain.
	 */
	public static boolean isNodeAvailable() {
		return isCommandAvailable(List.of(nodeExecutable(
				System.getProperty("os.name"), System.getProperty(NODE_INSTALL_DIRECTORY_PROPERTY)), //$NON-NLS-1$
				"--version")); //$NON-NLS-1$
	}

	/**
	 * Check if the npm command runner or the Maven-owned CSS tools are available.
	 */
	public static boolean isNpxAvailable() {
		String installDirectory = System.getProperty(NODE_INSTALL_DIRECTORY_PROPERTY);
		String modulesDirectory = System.getProperty(NODE_MODULES_DIRECTORY_PROPERTY);
		if (hasPinnedConfiguration(installDirectory, modulesDirectory)) {
			if (!hasText(installDirectory) || !hasText(modulesDirectory)) {
				return false;
			}
			Path modules = Path.of(modulesDirectory);
			return isNodeAvailable()
					&& Files.isRegularFile(toolScript(modules, PRETTIER_COMMAND))
					&& Files.isRegularFile(toolScript(modules, STYLELINT_COMMAND));
		}
		return isCommandAvailable(List.of(npxExecutable(System.getProperty("os.name")), "--version")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** Execute an npx command and return stdout/stderr without providing stdin. */
	public static ExecutionResult executeNpx(String... args) throws IOException, InterruptedException {
		return executeNpxInternal(null, args);
	}

	/** Execute an npx command with UTF-8 input supplied on stdin. */
	public static ExecutionResult executeNpxWithInput(String input, String... args)
			throws IOException, InterruptedException {
		return executeNpxInternal(input == null ? "" : input, args); //$NON-NLS-1$
	}

	private static ExecutionResult executeNpxInternal(String input, String... args)
			throws IOException, InterruptedException {
		List<String> command = buildNpxCommand(
				System.getProperty("os.name"), //$NON-NLS-1$
				System.getProperty(NODE_INSTALL_DIRECTORY_PROPERTY),
				System.getProperty(NODE_MODULES_DIRECTORY_PROPERTY),
				args);

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(false);

		Process process = pb.start();
		try (StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream());
				StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream())) {
			outputGobbler.start();
			errorGobbler.start();

			try (OutputStream processInput = process.getOutputStream()) {
				if (input != null) {
					processInput.write(input.getBytes(StandardCharsets.UTF_8));
				}
			}

			boolean finished;
			try {
				finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				process.destroyForcibly();
				Thread.currentThread().interrupt();
				throw e;
			}
			if (!finished) {
				process.destroyForcibly();
				throw new IOException("Process timed out after " + TIMEOUT_SECONDS + " seconds"); //$NON-NLS-1$ //$NON-NLS-2$
			}

			awaitCompleteOutput(outputGobbler, errorGobbler);
			return new ExecutionResult(process.exitValue(), outputGobbler.getOutput(), errorGobbler.getOutput());
		} finally {
			process.destroy();
		}
	}

	static List<String> buildNpxCommand(String osName, String installDirectory, String modulesDirectory,
			String... args) {
		if (args == null || args.length == 0 || !hasText(args[0])) {
			throw new IllegalArgumentException("An npm command is required"); //$NON-NLS-1$
		}

		boolean pinned = hasPinnedConfiguration(installDirectory, modulesDirectory);
		if (!pinned) {
			List<String> command = new ArrayList<>(args.length + 1);
			command.add(npxExecutable(osName));
			command.addAll(List.of(args));
			return command;
		}
		if (!hasText(installDirectory) || !hasText(modulesDirectory)) {
			throw new IllegalStateException(
					"Both Maven-owned Node toolchain directories must be configured together"); //$NON-NLS-1$
		}

		List<String> command = new ArrayList<>(args.length + 1);
		command.add(nodeExecutable(osName, installDirectory));
		command.add(toolScript(Path.of(modulesDirectory), args[0]).toString());
		for (int index = 1; index < args.length; index++) {
			command.add(args[index]);
		}
		return command;
	}

	static String npxExecutable(String osName) {
		return isWindows(osName) ? "npx.cmd" : "npx"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	static String nodeExecutable(String osName, String installDirectory) {
		if (!hasText(installDirectory)) {
			return "node"; //$NON-NLS-1$
		}
		String executable = isWindows(osName) ? "node.exe" : "node"; //$NON-NLS-1$ //$NON-NLS-2$
		return Path.of(installDirectory).resolve("node").resolve(executable).toString(); //$NON-NLS-1$
	}

	static Path toolScript(Path nodeModulesDirectory, String command) {
		return switch (command) {
			case PRETTIER_COMMAND -> nodeModulesDirectory.resolve(PRETTIER_COMMAND).resolve("bin") //$NON-NLS-1$
					.resolve("prettier.cjs"); //$NON-NLS-1$
			case STYLELINT_COMMAND -> nodeModulesDirectory.resolve(STYLELINT_COMMAND).resolve("bin") //$NON-NLS-1$
					.resolve("stylelint.mjs"); //$NON-NLS-1$
			default -> throw new IllegalArgumentException("Unsupported pinned npm command: " + command); //$NON-NLS-1$
		};
	}

	private static boolean isCommandAvailable(List<String> command) {
		Process process = null;
		try {
			process = new ProcessBuilder(command).start();
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
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return false;
		}
	}

	private static boolean hasPinnedConfiguration(String installDirectory, String modulesDirectory) {
		return hasText(installDirectory) || hasText(modulesDirectory);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static boolean isWindows(String osName) {
		return osName != null && osName.toLowerCase(Locale.ROOT).startsWith("windows"); //$NON-NLS-1$
	}

	private static void awaitCompleteOutput(StreamGobbler outputGobbler, StreamGobbler errorGobbler)
			throws InterruptedException {
		try {
			// The process has exited, so both streams will reach EOF. Do not return
			// partially consumed JSON merely because a fixed join timeout elapsed.
			outputGobbler.join();
			errorGobbler.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw e;
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
			setDaemon(true);
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
			if (!streamConsumed) {
				try {
					inputStream.close();
				} catch (IOException e) {
					// Process destruction also closes the stream.
				}
			}
		}

		String getOutput() {
			return output.toString();
		}
	}

	/** Result of a command execution. */
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
