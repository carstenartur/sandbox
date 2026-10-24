/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0: https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Executes the actual release-notes workflow block with local Git history. */
class ReleaseNotesContractTest {

	@TempDir
	Path repository;

	@Test
	void publishedSideBranchReleaseExcludesPreviouslyReleasedChanges() throws Exception {
		initialize();
		commit("already published change");
		git("checkout", "-b", "stable");
		commit("Release version 1.3.6");
		git("tag", "v1.3.6");
		git("checkout", "main");
		commit("new fix for 1.3.7");
		assertEquals("v1.3.2", git("describe", "--tags", "--abbrev=0", "HEAD").trim());
		Result result = generate("v1.3.6", 0);
		assertEquals(0, result.exitCode(), result.output());
		assertEquals("- new fix for 1.3.7\n", notes());
	}

	@Test
	void noPublishedReleaseRetainsInitialMessage() throws Exception {
		initialize();
		commit("unreleased change");
		assertEquals(0, generate("", 0).exitCode());
		assertEquals("Initial verified release.\n", notes());
	}

	@Test
	void emptyPublishedRangeRetainsNoChangesMessage() throws Exception {
		initialize();
		assertEquals(0, generate("v1.3.2", 0).exitCode());
		assertEquals("No user-visible changes recorded.\n", notes());
	}

	@Test
	void releaseApiFailureStopsInsteadOfInventingFirstRelease() throws Exception {
		initialize();
		Result result = generate("", 7);
		assertEquals(7, result.exitCode(), result.output());
		assertFalse(Files.exists(repository.resolve("release-notes.md")));
	}

	@Test
	void missingPublishedTagStopsInsteadOfReportingNoChanges() throws Exception {
		initialize();
		Result result = generate("v9.9.9", 0);
		assertTrue(result.exitCode() != 0, result.output());
		assertFalse(notes().contains("No user-visible changes recorded."));
	}

	private void initialize() throws Exception {
		git("init", "-b", "main");
		git("config", "user.name", "Sandbox Test");
		git("config", "user.email", "sandbox@example.invalid");
		git("config", "core.autocrlf", "false");
		commit("initial");
		git("tag", "v1.3.2");
	}

	private void commit(String message) throws Exception {
		git("-c", "commit.gpgsign=false", "commit", "--allow-empty", "-m", message);
	}

	private String git(String... arguments) throws Exception {
		var command = new java.util.ArrayList<String>();
		command.add("git");
		command.addAll(List.of(arguments));
		Result result = run(command, Map.of());
		assertEquals(0, result.exitCode(), result.output());
		return result.output();
	}

	private Result generate(String publishedTag, int apiExitCode) throws Exception {
		// Only replace the network boundary. Git, Bash, redirections and the
		// production workflow block execute unchanged. Reject calls that could
		// select a draft/prerelease or fetch multiple ambiguous baselines.
		String remote = """
				gh() {
				  [[ "$#" == 10 && "$1" == release && "$2" == list
				     && "$3" == --exclude-drafts && "$4" == --exclude-pre-releases
				     && "$5" == --limit && "$6" == 1 && "$7" == --json
				     && "$8" == tagName && "$9" == --jq
				     && "${10}" == '.[0].tagName // empty' ]] || return 91
				  printf '%s' "$TEST_PUBLISHED_TAG"
				  return "$TEST_API_EXIT_CODE"
				}
				""";
		return run(List.of(bash(), "-c", remote + workflowBlock()), Map.of(
				"STARTING_SHA", git("rev-parse", "HEAD").trim(),
				"TEST_PUBLISHED_TAG", publishedTag, "TEST_API_EXIT_CODE", Integer.toString(apiExitCode)));
	}

	private String bash() throws Exception {
		if (java.io.File.separatorChar != '\\') {
			return "bash";
		}
		// Choose Git for Windows' native Bash; PATH may resolve bash to WSL.
		Path directory = Path.of(git("--exec-path").trim());
		while (directory != null) {
			Path candidate = directory.resolve("bin/bash.exe");
			if (Files.isRegularFile(candidate)) {
				return candidate.toString();
			}
			directory = directory.getParent();
		}
		throw new IOException("Cannot find Git for Windows Bash");
	}

	private static String workflowBlock() throws IOException {
		Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (directory != null) {
			Path workflow = directory.resolve(".github/workflows/deploy-release.yml");
			if (Files.isRegularFile(workflow)) {
				String text = Files.readString(workflow, StandardCharsets.UTF_8).replace("\r\n", "\n");
				int step = text.indexOf("      - name: Generate release notes\n");
				assertTrue(step >= 0, "Missing release-notes step");
				int end = text.indexOf("\n      - ", step + 1);
				int run = text.indexOf("        run: |\n", step);
				assertTrue(run > step && end > run, "Missing release-notes run block");
				return text.substring(run + "        run: |\n".length(), end)
						.lines().map(line -> line.isBlank() ? "" : line.substring(10))
						.collect(java.util.stream.Collectors.joining("\n", "", "\n"));
			}
			directory = directory.getParent();
		}
		throw new IOException("Cannot locate release workflow");
	}

	private String notes() throws IOException {
		return Files.readString(repository.resolve("release-notes.md"), StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	private Result run(List<String> command, Map<String, String> environment) throws Exception {
		ProcessBuilder builder = new ProcessBuilder(command).directory(repository.toFile()).redirectErrorStream(true);
		builder.environment().putAll(environment);
		Process process = builder.start();
		try {
			assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Timed out: " + command.getFirst());
			return new Result(process.exitValue(), new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
		} finally {
			process.destroyForcibly();
		}
	}

	private record Result(int exitCode, String output) {
	}
}
