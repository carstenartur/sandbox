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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;

/**
 * Utility for executing Node.js/npm commands.
 */
public class NodeExecutor {

	static final String NODE_HOME_PROPERTY = "sandbox.css.node.home"; //$NON-NLS-1$
	static final String NODE_MODULES_PROPERTY = "sandbox.css.node.modules"; //$NON-NLS-1$

	private static final int AVAILABILITY_TIMEOUT_SECONDS = 5;
	private static final int TIMEOUT_SECONDS = 30;
	private static final ILog LOG = Platform.getLog(NodeExecutor.class);

	enum Tool {
		PRETTIER("prettier", Path.of("prettier", "bin", "prettier.cjs")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		STYLELINT("stylelint", Path.of("stylelint", "bin", "stylelint.mjs")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		private final String npxCommand;
		private final Path moduleScript;

		Tool(String npxCommand, Path moduleScript) {
			this.npxCommand = npxCommand;
			this.moduleScript = moduleScript;
		}
	}

	/**
	 * Check if Node.js is available on the configured toolchain or on {@code PATH}.
	 */
	public static boolean isNodeAvailable() {
		return commandSucceeds(command(nodeCommand(), "--version")); //$NON-NLS-1$
	}

	/**
	 * Check if npx is available on the configured toolchain or on {@code PATH}.
	 */
	public static boolean isNpxAvailable() {
		return commandSucceeds(command(npxCommand(), "--version")); //$NON-NLS-1$
	}

	static boolean isToolAvailable(Tool tool) {
		try {
			return executeTool(tool, "--version").isSuccess(); //$NON-NLS-1$
		} catch (IOException | InterruptedException | IllegalStateException e) {
			return false;
		}
	}

	static ExecutionResult executeNode(String... args) throws IOException, InterruptedException {
		return executeInternal(null, command(nodeCommand(), args), TIMEOUT_SECONDS);
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

	static ExecutionResult executeTool(Tool tool, String... args) throws IOException, InterruptedException {
		return executeInternal(null, toolCommand(tool, args), TIMEOUT_SECONDS);
	}

	static ExecutionResult executeToolWithInput(Tool tool, String input, String... args)
			throws IOException, InterruptedException {
		return executeInternal(input == null ? "" : input, toolCommand(tool, args), TIMEOUT_SECONDS); //$NON-NLS-1$
	}

	static List<String> configuredToolCommand(Tool tool, Path nodeExecutable, Path nodeModules,
			String... args) throws IOException {
		Path normalizedNode = nodeExecutable.toAbsolutePath().normalize();
		if (!Files.isRegularFile(normalizedNode)) {
			throw new IOException("Configured Node.js executable does not exist: " + normalizedNode); //$NON-NLS-1$
		}
		Path script = nodeModules.resolve(tool.moduleScript).toAbsolutePath().normalize();
		if (!Files.isRegularFile(script)) {
			throw new IOException("Configured Node.js package entry point does not exist: " + script); //$NON-NLS-1$
		}
		List<String> command = new ArrayList<>(args.length + 2);
		command.add(normalizedNode.toString());
		command.add(script.toString());
		command.addAll(Arrays.asList(args));
		return List.copyOf(command);
	}

	private static ExecutionResult executeNpxInternal(String input, String... args)
			throws IOException, InterruptedException {
		return executeInternal(input, command(npxCommand(), args), TIMEOUT_SECONDS);
	}

	private static List<String> toolCommand(Tool tool, String... args) throws IOException {
		String nodeHome = configured(NODE_HOME_PROPERTY);
		String nodeModules = configured(NODE_MODULES_PROPERTY);
		if ((nodeHome == null) != (nodeModules == null)) {
			throw new IllegalStateException("Both " + NODE_HOME_PROPERTY + " and " //$NON-NLS-1$ //$NON-NLS-2$
					+ NODE_MODULES_PROPERTY + " must be configured together"); //$NON-NLS-1$
		}
		if (nodeHome != null) {
			return configuredToolCommand(tool, nodeExecutable(Path.of(nodeHome)), Path.of(nodeModules), args);
		}

		List<String> command = new ArrayList<>(args.length + 2);
		command.add(npxCommand());
		command.add(tool.npxCommand);
		command.addAll(Arrays.asList(args));
		return List.copyOf(command);
	}

	private static List<String> command(String executable, String... args) {
		List<String> command = new ArrayList<>(args.length + 1);
		command.add(executable);
		command.addAll(Arrays.asList(args));
		return List.copyOf(command);
	}

	private static String nodeCommand() {
		String nodeHome = configured(NODE_HOME_PROPERTY);
		return nodeHome == null ? "node" : nodeExecutable(Path.of(nodeHome)).toString(); //$NON-NLS-1$
	}

	private static String npxCommand() {
		String nodeHome = configured(NODE_HOME_PROPERTY);
		if (nodeHome == null) {
			return "npx"; //$NON-NLS-1$
		}
		return Path.of(nodeHome).resolve(isWindows() ? "npx.cmd" : "npx").toString(); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static Path nodeExecutable(Path nodeHome) {
		return nodeHome.resolve(isWindows() ? "node.exe" : "node"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static String configured(String property) {
		String value = System.getProperty(property);
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static boolean commandSucceeds(List<String> command) {
		try {
			return executeInternal(null, command, AVAILABILITY_TIMEOUT_SECONDS).isSuccess();
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	private static ExecutionResult executeInternal(String input, List<String> command, int timeoutSeconds)
			throws IOException, InterruptedException {
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
				finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				process.destroyForcibly();
				Thread.currentThread().interrupt();
				throw e;
			}
			if (!finished) {
				process.destroyForcibly();
				throw new IOException("Process timed out after " + timeoutSeconds + " seconds: " //$NON-NLS-1$ //$NON-NLS-2$
						+ String.join(" ", command)); //$NON-NLS-1$
			}

			awaitCompleteOutput(outputGobbler, errorGobbler);
			return new ExecutionResult(process.exitValue(), outputGobbler.getOutput(), errorGobbler.getOutput());
		} finally {
			process.destroy();
		}
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
