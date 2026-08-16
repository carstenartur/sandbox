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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Focused regression tests for the Maven/JUnit repository policy.
 *
 * @since 1.3.4
 */
public class RepositoryPolicyRulesTest {

	private static final String EXCEPTION_HEADING = "Repository policy exception"; //$NON-NLS-1$

	@TempDir
	Path temporaryDirectory;

	@Test
	public void testNewPythonFileFailsEvenWhenAddedToAllowlist() throws Exception {
		Path repositoryDirectory = temporaryDirectory.resolve("python-file"); //$NON-NLS-1$
		try (Git git = Git.init().setDirectory(repositoryDirectory.toFile()).call()) {
			write(repositoryDirectory, "README.md", "base\n"); //$NON-NLS-1$ //$NON-NLS-2$
			RevCommit base = commit(git, "Base"); //$NON-NLS-1$
			write(repositoryDirectory, "tools/validate.py", "print('not allowed')\n"); //$NON-NLS-1$ //$NON-NLS-2$
			RevCommit head = commit(git, "Add Python automation"); //$NON-NLS-1$

			RepositoryPolicy.Configuration configuration = configuration(
					Set.of("tools/validate.py"), Set.of(), 1500, 2000); //$NON-NLS-1$
			RepositoryPolicy.PolicyReport report = RepositoryPolicy.evaluate(git.getRepository(),
					base.getId(), head.getId(), configuration, Optional.empty());

			assertFalse(report.isCompliant());
			assertTrue(report.format().contains(
					"New Python automation file is forbidden: tools/validate.py")); //$NON-NLS-1$
		}
	}

	@Test
	public void testNewPythonWorkflowInvocationFailsInsideAllowlistedWorkflow() throws Exception {
		Path repositoryDirectory = temporaryDirectory.resolve("python-workflow"); //$NON-NLS-1$
		String workflow = ".github/workflows/ci.yml"; //$NON-NLS-1$
		try (Git git = Git.init().setDirectory(repositoryDirectory.toFile()).call()) {
			write(repositoryDirectory, workflow, """
					name: CI
					on: push
					jobs:
					  verify:
					    runs-on: ubuntu-latest
					    steps:
					      - run: mvn verify
					"""); //$NON-NLS-1$
			RevCommit base = commit(git, "Base workflow"); //$NON-NLS-1$
			write(repositoryDirectory, workflow, """
					name: CI
					on: push
					jobs:
					  verify:
					    runs-on: ubuntu-latest
					    steps:
					      - run: mvn verify
					      - run: |+ # keep trailing newlines
					          echo checking
					          python3 qa/check.py
					"""); //$NON-NLS-1$
			RevCommit head = commit(git, "Add Python workflow step"); //$NON-NLS-1$

			RepositoryPolicy.Configuration configuration = configuration(Set.of(), Set.of(workflow),
					1500, 2000);
			RepositoryPolicy.PolicyReport report = RepositoryPolicy.evaluate(git.getRepository(),
					base.getId(), head.getId(), configuration, Optional.empty());

			assertFalse(report.isCompliant());
			assertTrue(report.format().contains("New Python workflow invocation is forbidden in " //$NON-NLS-1$
					+ workflow));
		}
	}

	@Test
	public void testExistingPythonInvocationIsNotNewWhenNearbyYamlChanges() throws Exception {
		Path repositoryDirectory = temporaryDirectory.resolve("existing-python-workflow"); //$NON-NLS-1$
		String workflow = ".github/workflows/legacy.yml"; //$NON-NLS-1$
		try (Git git = Git.init().setDirectory(repositoryDirectory.toFile()).call()) {
			write(repositoryDirectory, workflow, """
					name: Legacy
					on: push
					jobs:
					  verify:
					    runs-on: ubuntu-latest
					    steps:
					      - run: |
					          echo before
					          python3 qa/check.py
					"""); //$NON-NLS-1$
			RevCommit base = commit(git, "Base legacy workflow"); //$NON-NLS-1$
			write(repositoryDirectory, workflow, """
					name: Legacy
					on: push
					jobs:
					  verify:
					    runs-on: ubuntu-latest
					    steps:
					      - run: |
					          echo after
					          python3 qa/check.py
					"""); //$NON-NLS-1$
			RevCommit head = commit(git, "Change adjacent shell command"); //$NON-NLS-1$

			RepositoryPolicy.Configuration configuration = configuration(Set.of(), Set.of(workflow),
					1500, 2000);
			RepositoryPolicy.PolicyReport report = RepositoryPolicy.evaluate(git.getRepository(),
					base.getId(), head.getId(), configuration, Optional.empty());

			assertTrue(report.isCompliant(), report::format);
		}
	}

	@Test
	public void testExistingSetupPythonVersionUpgradeIsNotANewInvocation() throws Exception {
		Path repositoryDirectory = temporaryDirectory.resolve("setup-python-upgrade"); //$NON-NLS-1$
		String workflow = ".github/workflows/legacy.yml"; //$NON-NLS-1$
		try (Git git = Git.init().setDirectory(repositoryDirectory.toFile()).call()) {
			write(repositoryDirectory, workflow, """
					name: Legacy
					on: push
					jobs:
					  verify:
					    runs-on: ubuntu-latest
					    steps:
					      - uses: actions/setup-python@v5
					"""); //$NON-NLS-1$
			RevCommit base = commit(git, "Base legacy workflow"); //$NON-NLS-1$
			write(repositoryDirectory, workflow, """
					name: Legacy
					on: push
					jobs:
					  verify:
					    runs-on: ubuntu-latest
					    steps:
					      - uses: actions/setup-python@v6
					"""); //$NON-NLS-1$
			RevCommit head = commit(git, "Upgrade existing setup-python action"); //$NON-NLS-1$

			RepositoryPolicy.Configuration configuration = configuration(Set.of(), Set.of(workflow),
					1500, 2000);
			RepositoryPolicy.PolicyReport report = RepositoryPolicy.evaluate(git.getRepository(),
					base.getId(), head.getId(), configuration, Optional.empty());

			assertTrue(report.isCompliant(), report::format);
		}
	}

	@Test
	public void testStaleLegacyAllowlistEntryMustBeRemoved() throws Exception {
		Path repositoryDirectory = temporaryDirectory.resolve("stale-allowlist"); //$NON-NLS-1$
		try (Git git = Git.init().setDirectory(repositoryDirectory.toFile()).call()) {
			write(repositoryDirectory, "README.md", "base\n"); //$NON-NLS-1$ //$NON-NLS-2$
			RevCommit head = commit(git, "Base"); //$NON-NLS-1$
			RepositoryPolicy.Configuration configuration = configuration(
					Set.of("removed-legacy.py"), Set.of(), 1500, 2000); //$NON-NLS-1$

			RepositoryPolicy.PolicyReport report = RepositoryPolicy.evaluate(git.getRepository(),
					head.getId(), head.getId(), configuration, Optional.empty());

			assertFalse(report.isCompliant());
			assertTrue(report.format().contains(
					"Python automation allowlist entry is stale and must be removed: removed-legacy.py")); //$NON-NLS-1$
		}
	}

	@Test
	public void testOversizedSliceReportsPerFileTotals() throws Exception {
		Path repositoryDirectory = temporaryDirectory.resolve("oversized"); //$NON-NLS-1$
		try (Git git = Git.init().setDirectory(repositoryDirectory.toFile()).call()) {
			write(repositoryDirectory, "src/large.txt", "base\n"); //$NON-NLS-1$ //$NON-NLS-2$
			RevCommit base = commit(git, "Base"); //$NON-NLS-1$
			String expanded = IntStream.rangeClosed(1, 30)
					.mapToObj(index -> "line-" + index) //$NON-NLS-1$
					.collect(Collectors.joining("\n", "", "\n")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			write(repositoryDirectory, "src/large.txt", expanded); //$NON-NLS-1$
			RevCommit head = commit(git, "Large review slice"); //$NON-NLS-1$

			RepositoryPolicy.Configuration configuration = configuration(Set.of(), Set.of(), 10, 20);
			RepositoryPolicy.PolicyReport report = RepositoryPolicy.evaluate(git.getRepository(),
					base.getId(), head.getId(), configuration, Optional.empty());

			assertFalse(report.isCompliant());
			assertTrue(report.changedLines() > 20);
			assertTrue(report.format().contains("hard stop of 20")); //$NON-NLS-1$
			assertTrue(report.format().contains("src/large.txt")); //$NON-NLS-1$
		}
	}

	@Test
	public void testSubstantiveExceptionAllowsIndivisibleOversizedSlice() throws Exception {
		Path repositoryDirectory = temporaryDirectory.resolve("documented-exception"); //$NON-NLS-1$
		try (Git git = Git.init().setDirectory(repositoryDirectory.toFile()).call()) {
			write(repositoryDirectory, "src/large.txt", "base\n"); //$NON-NLS-1$ //$NON-NLS-2$
			RevCommit base = commit(git, "Base"); //$NON-NLS-1$
			String expanded = IntStream.rangeClosed(1, 30)
					.mapToObj(index -> "line-" + index) //$NON-NLS-1$
					.collect(Collectors.joining("\n", "", "\n")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			write(repositoryDirectory, "src/large.txt", expanded); //$NON-NLS-1$
			RevCommit head = commit(git, "Documented indivisible slice"); //$NON-NLS-1$

			RepositoryPolicy.Configuration configuration = configuration(Set.of(), Set.of(), 10, 20);
			String explanation = "The generated compatibility table and its matching verifier must " //$NON-NLS-1$
					+ "change atomically; splitting them would create an unreviewable invalid intermediate state."; //$NON-NLS-1$
			RepositoryPolicy.PolicyReport report = RepositoryPolicy.evaluate(git.getRepository(),
					base.getId(), head.getId(), configuration, Optional.of(explanation));

			assertTrue(report.isCompliant(), report::format);
		}
	}

	@Test
	public void testExceptionSectionMustContainSubstantiveExplanation() {
		String shortBody = "## " + EXCEPTION_HEADING + "\nToo short."; //$NON-NLS-1$ //$NON-NLS-2$
		String longBody = "## " + EXCEPTION_HEADING + "\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "This change contains one generated protocol table and its exact verifier; " //$NON-NLS-1$
				+ "they cannot be split without making the intermediate commit invalid."; //$NON-NLS-1$

		assertTrue(RepositoryPolicy.extractException(shortBody, EXCEPTION_HEADING, 80).isEmpty());
		assertTrue(RepositoryPolicy.extractException(longBody, EXCEPTION_HEADING, 80).isPresent());
	}

	private static RepositoryPolicy.Configuration configuration(Set<String> pythonFiles,
			Set<String> pythonWorkflows, int maxChangedLines, int hardStopChangedLines) {
		return new RepositoryPolicy.Configuration(maxChangedLines, hardStopChangedLines, 80,
				EXCEPTION_HEADING, pythonFiles, pythonWorkflows);
	}

	private static void write(Path repository, String relativePath, String contents) throws Exception {
		Path file = repository.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, contents, StandardCharsets.UTF_8);
	}

	private static RevCommit commit(Git git, String message) throws Exception {
		git.add().addFilepattern(".").call(); //$NON-NLS-1$
		return git.commit()
				.setMessage(message)
				.setAuthor("Sandbox policy test", "sandbox@example.invalid") //$NON-NLS-1$ //$NON-NLS-2$
				.setCommitter("Sandbox policy test", "sandbox@example.invalid") //$NON-NLS-1$ //$NON-NLS-2$
				.call();
	}
}
