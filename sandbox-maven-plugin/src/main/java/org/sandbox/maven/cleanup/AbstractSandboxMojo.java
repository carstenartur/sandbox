/*******************************************************************************
 * Copyright (c) 2024 Carsten Hammer.
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
package org.sandbox.maven.cleanup;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;

/**
 * Base class for Sandbox cleanup Maven plugin goals.
 * Resolves the CLI tool and invokes it with the appropriate arguments.
 */
public abstract class AbstractSandboxMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	private MavenSession session;

	@Component
	private ProjectDependenciesResolver dependencies;

	/**
	 * Version of the sandbox-cleanup-cli tool to use.
	 */
	@Parameter(property = "sandbox.toolVersion", defaultValue = "${plugin.version}")
	protected String toolVersion;

	/**
	 * URL or local path to the tool distribution archive.
	 * If not specified, the tool will be downloaded from GitHub Releases.
	 */
	@Parameter(property = "sandbox.toolSource")
	protected String toolSource;

	/**
	 * Path to the cleanup configuration properties file.
	 */
	@Parameter(property = "sandbox.configFile", required = true)
	protected File configFile;

	/**
	 * Scope filter: main, test, or both.
	 */
	@Parameter(property = "sandbox.scope", defaultValue = "both")
	protected String scope;

	/**
	 * Output file for unified diff patch (optional).
	 */
	@Parameter(property = "sandbox.patchFile")
	protected File patchFile;

	/**
	 * Output file for JSON report (optional).
	 */
	@Parameter(property = "sandbox.reportFile")
	protected File reportFile;

	/**
	 * Whether to fail the build when changes are detected (for check goal).
	 */
	@Parameter(property = "sandbox.failOnChanges", defaultValue = "true")
	protected boolean failOnChanges;

	/**
	 * Directory to cache downloaded tool distributions.
	 */
	@Parameter(property = "sandbox.cacheDir", defaultValue = "${user.home}/.sandbox-cleanup/cache")
	protected File cacheDir;

	/**
	 * Source directory to process.
	 */
	@Parameter(property = "sandbox.sourceDir", defaultValue = "${project.basedir}")
	protected File sourceDir;

	/**
	 * Enable verbose output.
	 */
	@Parameter(property = "sandbox.verbose", defaultValue = "false")
	protected boolean verbose;

	/** Exit code: changes detected. */
	protected static final int EXIT_CHANGES = 2;

	/**
	 * Return the cleanup mode for this goal (apply, check, or diff).
	 */
	protected abstract String getMode();

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		Path toolHome = resolveToolHome();
		if (hasCallerWorkspace(System.getenv())) {
			// The caller owns the existing workspace, including its imported projects and classpaths.
			runCleanup(toolHome, new MavenCleanupProject(null, List.of(sourceDir.toPath().toAbsolutePath()), scope), null);
			return;
		}
		try (var run = new CleanupRun()) {
			boolean emitsPatch = "diff".equals(getMode()) || patchFile != null;
			var input = MavenCleanupProject.prepare(project, sourceDir.toPath(), scope, run.directory, emitsPatch,
					includeTests -> MavenCleanupClasspath.resolve(project, session.getRepositorySession(), dependencies, includeTests));
			runCleanup(toolHome, input, run.directory.resolve("workspace"));
		} catch (IOException | DependencyResolutionRequiredException e) {
			throw new MojoExecutionException("Cannot prepare or close the cleanup workspace: " + e.getMessage(), e);
		}
	}

	static boolean hasCallerWorkspace(Map<String, String> environment) {
		return !environment.getOrDefault("SANDBOX_WORKSPACE", "").isEmpty()
				|| !environment.getOrDefault("SANDBOX_CLEANUP_WORKSPACE", "").isEmpty();
	}

	private void runCleanup(Path toolHome, MavenCleanupProject input, Path workspace)
			throws MojoExecutionException, MojoFailureException {
		List<String> command = buildCommand(toolHome, input);
		getLog().info("Running sandbox-cleanup in " + getMode() + " mode (Maven scope: " + scope + ")");
		if (verbose) {
			getLog().info("Command: " + String.join(" ", command));
		}
		handleExitCode(invokeProcess(command, workspace));
	}

	/**
	 * Resolve the tool home directory (download if necessary).
	 */
	private Path resolveToolHome() throws MojoExecutionException {
		if (toolSource != null && !toolSource.isEmpty()) {
			Path sourcePath = Path.of(toolSource);
			if (Files.isDirectory(sourcePath)) {
				return sourcePath;
			}
			// Download and extract
			return downloadAndExtract(toolSource);
		}

		// Try to find a locally installed tool
		Path localTool = cacheDir.toPath().resolve("sandbox-cleanup-cli-" + toolVersion);
		if (Files.isDirectory(localTool)) {
			getLog().info("Using cached tool: " + localTool);
			return localTool;
		}

		// Auto-download from GitHub releases
		String downloadUrl = "https://github.com/carstenartur/sandbox/releases/download/v"
				+ toolVersion + "/sandbox-cleanup-cli-" + toolVersion + "-dist.tar.gz";
		return downloadAndExtract(downloadUrl);
	}

	private Path downloadAndExtract(String url) throws MojoExecutionException {
		try {
			Files.createDirectories(cacheDir.toPath());
			Path archivePath = cacheDir.toPath().resolve("sandbox-cleanup-cli-" + toolVersion + ".tar.gz");

			if (!Files.exists(archivePath)) {
				getLog().info("Downloading tool from: " + url);
				try (InputStream in = URI.create(url).toURL().openStream()) {
					Files.copy(in, archivePath, StandardCopyOption.REPLACE_EXISTING);
				}
			}

			Path extractDir = cacheDir.toPath().resolve("sandbox-cleanup-cli-" + toolVersion);
			if (!Files.isDirectory(extractDir)) {
				Files.createDirectories(extractDir);
				// Note: Requires tar command (Unix/Mac/WSL). Windows users should set toolSource to a local directory.
				ProcessBuilder pb = new ProcessBuilder("tar", "-xzf",
						archivePath.toString(), "-C", extractDir.toString(), "--strip-components=1");
				pb.inheritIO();
				int rc = pb.start().waitFor();
				if (rc != 0) {
					throw new MojoExecutionException("Failed to extract tool archive, exit code: " + rc
							+ ". On Windows, use -Dsandbox.toolSource=<local-directory> instead.");
				}
			}
			return extractDir;
		} catch (IOException | InterruptedException e) {
			throw new MojoExecutionException("Failed to download/extract tool", e);
		}
	}

	/**
	 * Build the command line for invoking the tool.
	 */
	private List<String> buildCommand(Path toolHome, MavenCleanupProject input) {
		List<String> cmd = new ArrayList<>();
		Path launcher = toolHome.resolve("bin/sandbox-cleanup");
		cmd.add(launcher.toString());

		cmd.add("--config");
		cmd.add(configFile.getAbsolutePath());

		cmd.add("--mode");
		cmd.add(getMode());

		if (input.directory() != null) {
			cmd.add("--import-project");
			cmd.add(input.directory().toString());
		}
		if (input.cliScope() != null && !input.cliScope().isEmpty()) {
			cmd.add("--scope");
			cmd.add(input.cliScope());
		}

		if (patchFile != null) {
			cmd.add("--patch");
			cmd.add(patchFile.getAbsolutePath());
		}

		if (reportFile != null) {
			cmd.add("--report");
			cmd.add(reportFile.getAbsolutePath());
		}

		if (verbose) {
			cmd.add("--verbose");
		}

		for (Path source : input.sources()) {
			cmd.add("--source");
			cmd.add(source.toString());
		}

		return cmd;
	}

	/**
	 * Invoke the tool as a subprocess and return the exit code.
	 */
	private int invokeProcess(List<String> command, Path workspace) throws MojoExecutionException {
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.inheritIO();
			pb.directory(sourceDir.isDirectory() ? sourceDir : project.getBasedir());
			if (workspace != null) {
				pb.environment().put("SANDBOX_WORKSPACE", workspace.toString());
			}
			Process process = pb.start();
			try {
				return process.waitFor();
			} catch (InterruptedException e) {
				process.destroyForcibly().onExit().join();
				Thread.currentThread().interrupt();
				throw new MojoExecutionException("Interrupted while running cleanup", e);
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to execute cleanup tool", e);
		}
	}

	private static final class CleanupRun implements AutoCloseable {
		final Path directory;

		CleanupRun() throws IOException {
			directory = Files.createTempDirectory("sandbox-maven-cleanup-");
		}

		@Override
		public void close() throws IOException {
			// Files.walk does not follow symbolic links, including any created by Eclipse.
			try (var paths = Files.walk(directory)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
					Files.delete(path);
				}
			}
		}
	}

	/**
	 * Handle the tool exit code. Subclasses override for goal-specific behavior.
	 */
	protected void handleExitCode(int exitCode) throws MojoExecutionException, MojoFailureException {
		if (exitCode == 0) {
			getLog().info("Cleanup completed successfully, no changes needed.");
		} else if (exitCode == EXIT_CHANGES) {
			String msg = "Cleanup detected changes that need to be applied.";
			if (failOnChanges) {
				throw new MojoFailureException(msg);
			}
			getLog().warn(msg);
		} else {
			throw new MojoExecutionException("Cleanup tool failed with exit code: " + exitCode);
		}
	}
}
