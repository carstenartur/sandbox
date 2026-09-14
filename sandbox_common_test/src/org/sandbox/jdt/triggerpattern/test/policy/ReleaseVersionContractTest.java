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

import org.junit.jupiter.api.Test;

/** Guards explicit version changes for POMs without the central Tycho parent. */
public class ReleaseVersionContractTest {

	private static final String WORKFLOW = ".github/workflows/deploy-release.yml";
	private static final List<String> STANDALONE_POMS = List.of(
			"sandbox_cleanup_cli_dist/pom.xml", "sandbox-maven-plugin/pom.xml", "sandbox_oomph/pom.xml");
	private static final List<Transition> TRANSITIONS = List.of(
			new Transition("Set and verify stable project version", "RELEASE_VERSION"),
			new Transition("Prepare next development iteration pull request", "NEXT_SNAPSHOT"));

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
	public void omissionFromEitherTransitionCannotBeHiddenByTheOther() throws IOException {
		String workflow = workflow();
		for (Transition transition : TRANSITIONS) {
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
		String workflow = workflow();
		for (Transition transition : TRANSITIONS) {
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
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
		while (current != null) {
			if (Files.isRegularFile(current.resolve("pom.xml"))
					&& Files.isRegularFile(current.resolve(WORKFLOW))) {
				return Files.readString(current.resolve(WORKFLOW), StandardCharsets.UTF_8);
			}
			current = current.getParent();
		}
		throw new IOException("Cannot locate the Sandbox repository root");
	}
}
