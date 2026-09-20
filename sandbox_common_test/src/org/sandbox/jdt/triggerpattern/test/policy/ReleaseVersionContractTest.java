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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Guards explicit version changes for POMs without the central Tycho parent. */
public class ReleaseVersionContractTest {

	private static final String WORKFLOW = ".github/workflows/deploy-release.yml";
	private static final String RECOVERY_WORKFLOW = ".github/workflows/recover-release-handoff.yml";
	private static final List<Transition> RECOVERY_TRANSITIONS = List.of(
			new Transition("Create protected-main handoff pull requests", "NEXT_SNAPSHOT"));
	private static final List<String> STANDALONE_POMS = List.of(
			"sandbox_cleanup_cli_dist/pom.xml", "sandbox-maven-plugin/pom.xml", "sandbox_oomph/pom.xml");
	private static final List<Transition> TRANSITIONS = List.of(
			new Transition("Set and verify stable project version", "RELEASE_VERSION"),
			new Transition("Prepare next development iteration pull request", "NEXT_SNAPSHOT"));
	private static final Pattern HANDOFF_DIFF_CHECK_COMMAND = Pattern.compile(
			"(?m)^[ \\t]*(git -c core\\.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --check)[ \\t]*$");

	private record Transition(String step, String variable) {
	}

	@Test
	public void stableReleaseUpdatesEveryStandalonePom() throws IOException {
		assertTransition(workflow(), TRANSITIONS.getFirst());
	}

	@Test
	public void nextDevelopmentVersionUpdatesEveryStandalonePom() throws IOException {
		assertTransition(workflow(), TRANSITIONS.getLast());
	}

	@Test
	public void nextDevelopmentStepUsesCrAtEolWhitespacePolicy() throws IOException {
		handoffDiffCheckCommand(workflowStep(workflow(), TRANSITIONS.getLast().step()));
	}

	@Test
	public void omissionFromEitherTransitionCannotBeHiddenByTheOther() throws IOException {
		assertMissingCommands(workflow(), TRANSITIONS);
	}

	private static void assertMissingCommands(String workflow, List<Transition> transitions) {
		for (Transition transition : transitions) {
			String step = workflowStep(workflow, transition.step());
			for (String pom : STANDALONE_POMS) {
				Matcher command = versionCommand(pom, transition.variable()).matcher(step);
				assertTrue(command.find(), transition.step() + ": " + pom);
				String changed = workflow.replace(step, step.replace(command.group(), ""));
				assertThrows(AssertionError.class, () -> assertTransition(changed, transition), pom);
			}
		}
	}

	@Test
	public void commentedOrWrongVersionCommandsDoNotCount() throws IOException {
		assertInvalidCommands(workflow(), TRANSITIONS);
	}

	private static void assertInvalidCommands(String workflow, List<Transition> transitions) {
		for (Transition transition : transitions) {
			String step = workflowStep(workflow, transition.step());
			for (String pom : STANDALONE_POMS) {
				Matcher command = versionCommand(pom, transition.variable()).matcher(step);
				assertTrue(command.find(), transition.step() + ": " + pom);
				String original = command.group();
				for (String invalid : List.of(original.replace("mvn -f", "# mvn -f"),
						original.replace("$" + transition.variable(), "$CURRENT_VERSION"))) {
					String changed = workflow.replace(step, step.replace(original, invalid));
					assertThrows(AssertionError.class, () -> assertTransition(changed, transition), pom);
				}
			}
		}
	}

	@Test
	public void recoveryHandoffUpdatesEveryStandalonePom() throws IOException {
		assertTransition(workflow(RECOVERY_WORKFLOW), RECOVERY_TRANSITIONS.getFirst());
	}

	@Test
	public void recoveryCannotOmitAnyStandaloneUpdate() throws IOException {
		assertMissingCommands(workflow(RECOVERY_WORKFLOW), RECOVERY_TRANSITIONS);
	}

	@Test
	public void recoveryRejectsCommentedOrWrongVersionUpdates() throws IOException {
		assertInvalidCommands(workflow(RECOVERY_WORKFLOW), RECOVERY_TRANSITIONS);
	}

	private static void assertTransition(String workflow, Transition transition) {
		String step = workflowStep(workflow, transition.step());
		int verification = step.indexOf("actual=$(mvn");
		assertTrue(verification >= 0, "Missing version verification in " + transition.step());
		for (String pom : STANDALONE_POMS) {
			Matcher command = versionCommand(pom, transition.variable()).matcher(step);
			assertTrue(command.find(), transition.step() + " must update " + pom);
			assertTrue(command.end() < verification, pom + " must be updated before version verification");
			assertTrue(!command.find(), "Duplicate version update for " + pom);
		}
	}

	@Test
	public void handoffWhitespaceCheckAcceptsCleanLfAndCrLfButRejectsWhitespaceRegressions(@TempDir Path temporaryDirectory)
			throws Exception {
		String command = handoffDiffCheckCommand(workflowStep(workflow(), TRANSITIONS.getLast().step()));
		assertEquals(0, runVersionTransitionScenario(temporaryDirectory, "lf-clean", command, "\n", "", false).exitCode());
		assertEquals(0, runVersionTransitionScenario(temporaryDirectory, "crlf-clean", command, "\r\n", "", false).exitCode());

		CommandResult crlfSpace = runVersionTransitionScenario(temporaryDirectory, "crlf-space", command, "\r\n", " ",
				false);
		assertTrue(crlfSpace.exitCode() != 0, crlfSpace.output());
		assertTrue(crlfSpace.output().contains("trailing whitespace"), crlfSpace.output());

		CommandResult crlfTab = runVersionTransitionScenario(temporaryDirectory, "crlf-tab", command, "\r\n", "\t", false);
		assertTrue(crlfTab.exitCode() != 0, crlfTab.output());
		assertTrue(crlfTab.output().contains("trailing whitespace"), crlfTab.output());

		CommandResult lfSpace = runVersionTransitionScenario(temporaryDirectory, "lf-space", command, "\n", " ", false);
		assertTrue(lfSpace.exitCode() != 0, lfSpace.output());
		assertTrue(lfSpace.output().contains("trailing whitespace"), lfSpace.output());

		CommandResult lfBlankAtEof = runVersionTransitionScenario(temporaryDirectory, "lf-blank-at-eof", command, "\n",
				"", true);
		assertTrue(lfBlankAtEof.exitCode() != 0, lfBlankAtEof.output());
		assertTrue(lfBlankAtEof.output().contains("new blank line at EOF"), lfBlankAtEof.output());
	}

	private static String handoffDiffCheckCommand(String step) {
		Matcher command = HANDOFF_DIFF_CHECK_COMMAND.matcher(step);
		assertTrue(command.find(), "Missing executable handoff whitespace check command");
		String executable = command.group(1);
		assertTrue(!command.find(), "Duplicate handoff whitespace check command");
		return executable;
	}

	private static CommandResult runVersionTransitionScenario(Path temporaryDirectory, String name, String command,
			String lineEnding, String versionLineSuffix, boolean appendBlankLineAtEof) throws Exception {
		Path repository = Files.createDirectory(temporaryDirectory.resolve(name));
		runSuccessful(repository, List.of("git", "init"));
		runSuccessful(repository, List.of("git", "config", "user.name", "Sandbox Test"));
		runSuccessful(repository, List.of("git", "config", "user.email", "sandbox@example.invalid"));
		runSuccessful(repository, List.of("git", "config", "core.autocrlf", "false"));
		Path pom = repository.resolve("pom.xml");
		writePom(pom, "1.3.5", lineEnding, "", false);
		runSuccessful(repository, List.of("git", "add", "pom.xml"));
		runSuccessful(repository, List.of("git", "commit", "-m", "initial"));
		writePom(pom, "1.3.6-SNAPSHOT", lineEnding, versionLineSuffix, appendBlankLineAtEof);
		return run(repository, List.of("bash", "-lc", command));
	}

	private static void writePom(Path pom, String version, String lineEnding, String versionLineSuffix,
			boolean appendBlankLineAtEof) throws IOException {
		String content = "<project>" + lineEnding + "  <modelVersion>4.0.0</modelVersion>" + lineEnding
				+ "  <groupId>org.sandbox</groupId>" + lineEnding
				+ "  <artifactId>sandbox-release-whitespace-fixture</artifactId>" + lineEnding + "  <version>" + version
				+ "</version>" + versionLineSuffix + lineEnding + "</project>" + lineEnding;
		Files.writeString(pom, appendBlankLineAtEof ? content + lineEnding : content, StandardCharsets.UTF_8);
	}

	private static CommandResult run(Path directory, List<String> command) throws Exception {
		Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
		try {
			assertTrue(process.waitFor(30, TimeUnit.SECONDS), String.join(" ", command));
			return new CommandResult(process.exitValue(),
					new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
		} finally {
			process.destroyForcibly();
		}
	}

	private static void runSuccessful(Path directory, List<String> command) throws Exception {
		CommandResult result = run(directory, command);
		assertEquals(0, result.exitCode(), result.output());
	}

	private record CommandResult(int exitCode, String output) {
	}

	private static Pattern versionCommand(String pom, String variable) {
		// Match a real, complete shell command in this step, not an example or comment.
		return Pattern.compile("(?m)^[ \\t]*mvn -f " + Pattern.quote(pom)
				+ " versions:set[ \\t]+\\\\\\r?\\n[ \\t]*"
				+ Pattern.quote("-DnewVersion=\"$" + variable + "\" -DgenerateBackupPoms=false")
				+ "[ \\t]*$");
	}

	private static String workflowStep(String workflow, String name) {
		Pattern header = Pattern.compile("(?m)^      - name: " + Pattern.quote(name) + "\\r?$");
		Matcher match = header.matcher(workflow);
		assertTrue(match.find(), "Missing workflow step: " + name);
		int start = match.start();
		int contentStart = match.end();
		assertTrue(!match.find(), "Duplicate workflow step: " + name);
		Matcher next = Pattern.compile("(?m)^      - (?:name:|uses:)").matcher(workflow);
		int end = next.find(contentStart) ? next.start() : workflow.length();
		String step = workflow.substring(start, end);
		assertEquals(1, step.lines().filter(line -> line.equals("        run: |")).count(),
				"Version transition must contain one executable run block");
		return step;
	}

	private static String workflow() throws IOException {
		return workflow(WORKFLOW);
	}

	private static String workflow(String workflowPath) throws IOException {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
		while (current != null) {
			if (Files.isRegularFile(current.resolve("pom.xml"))
					&& Files.isRegularFile(current.resolve(workflowPath))) {
				return Files.readString(current.resolve(workflowPath), StandardCharsets.UTF_8);
			}
			current = current.getParent();
		}
		throw new IOException("Cannot locate the Sandbox repository root");
	}
}
