/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.wizard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.jobs.Job;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.RuleExampleSuite;

class HintFileCreationTest {

	private IProject project;
	private final RuleExampleSuite suite = new RuleExampleSuite(RuleExampleSuite.CURRENT_VERSION, List.of(
			new RuleExampleSuite.Example("Saved edit", "src/p/Example.java",
					"package p; class Example { int n = 0; }\n",
					"package p; class Example { int n = 1; }\n", true)));

	@BeforeEach
	void createProject() throws CoreException {
		project = ResourcesPlugin.getWorkspace().getRoot().getProject("rule-example-save-" + System.nanoTime());
		project.create(null);
		project.open(null);
	}

	@AfterEach
	void deleteProject() throws CoreException {
		if (project != null && project.exists()) {
			project.delete(true, true, null);
		}
	}

	@Test
	void storesCompleteRuleAndExactExamplesTogetherWithoutVerificationClaims() throws Exception {
		String dsl = "<!id: team.complete>\n<!tags: ä, team>\n$x.old()\n=> $x.modern()\n;;\n";
		HintFileCreation.create(project, "complete.sandbox-hint", dsl, suite, new NullProgressMonitor());
		assertEquals(dsl, read("complete.sandbox-hint"));
		assertEquals(suite, RuleExampleSuite.fromJson(read("complete.sandbox-hint.examples.json")));
		assertFalse(read("complete.sandbox-hint.examples.json").contains("passed"));
	}

	@Test
	void existingSidecarIsPreservedAndNoHintIsCreated() throws Exception {
		write("complete.sandbox-hint.examples.json", "existing evidence");
		assertThrows(CoreException.class, () -> HintFileCreation.create(project, "complete.sandbox-hint",
				"rule", suite, new NullProgressMonitor()));
		assertFalse(project.getFile("complete.sandbox-hint").exists());
		assertEquals("existing evidence", read("complete.sandbox-hint.examples.json"));
	}

	@Test
	void existingRuleIsPreservedAndNoSidecarIsCreated() throws Exception {
		write("complete.sandbox-hint", "existing rule");
		assertThrows(CoreException.class, () -> HintFileCreation.create(project, "complete.sandbox-hint",
				"replacement", suite, new NullProgressMonitor()));
		assertEquals("existing rule", read("complete.sandbox-hint"));
		assertFalse(project.getFile("complete.sandbox-hint.examples.json").exists());
	}

	@Test
	void cancellationAfterHintCreationRollsBackOnlyTheNewFiles() throws Exception {
		write("unrelated.txt", "keep");
		NullProgressMonitor monitor = new NullProgressMonitor() {
			@Override
			public void worked(int work) {
				setCanceled(true);
			}
		};
		assertThrows(OperationCanceledException.class, () -> HintFileCreation.create(project,
				"complete.sandbox-hint", "rule", suite, monitor));
		assertFalse(project.getFile("complete.sandbox-hint").exists());
		assertFalse(project.getFile("complete.sandbox-hint.examples.json").exists());
		assertEquals("keep", read("unrelated.txt"));
	}

	@Test
	void cancellationAfterBothCreationsRollsBackBothNewFiles() {
		NullProgressMonitor monitor = new NullProgressMonitor() {
			private int completed;

			@Override
			public void worked(int work) {
				completed += work;
				setCanceled(completed >= 2);
			}
		};
		assertThrows(OperationCanceledException.class, () -> HintFileCreation.create(project,
				"complete.sandbox-hint", "rule", suite, monitor));
		assertFalse(project.getFile("complete.sandbox-hint").exists());
		assertFalse(project.getFile("complete.sandbox-hint.examples.json").exists());
	}

	@Test
	void sidecarCreatedAfterPreflightSurvivesFailureAndTheNewHintIsRemoved() throws Exception {
		NullProgressMonitor monitor = new NullProgressMonitor() {
			private boolean injected;

			@Override
			public void worked(int work) {
				if (injected) {
					return;
				}
				injected = true;
				try {
					write("complete.sandbox-hint.examples.json", "new independent evidence");
				} catch (CoreException exception) {
					throw new AssertionError(exception);
				}
			}
		};
		var schedulingRule = Job.getJobManager().currentRule();
		assertThrows(CoreException.class, () -> HintFileCreation.create(project,
				"complete.sandbox-hint", "rule", suite, monitor));
		assertEquals(schedulingRule, Job.getJobManager().currentRule(), "Failed creation must release its workspace rule");
		assertFalse(project.getFile("complete.sandbox-hint").exists());
		assertEquals("new independent evidence", read("complete.sandbox-hint.examples.json"));
	}

	@Test
	void creatingAPlainRuleDoesNotTouchAnUnselectedSidecar() throws Exception {
		write("complete.sandbox-hint.examples.json", "keep evidence");
		HintFileCreation.create(project, "complete.sandbox-hint", "rule", null, new NullProgressMonitor());
		assertEquals("rule", read("complete.sandbox-hint"));
		assertEquals("keep evidence", read("complete.sandbox-hint.examples.json"));
		assertTrue(project.getFile("complete.sandbox-hint").exists());
	}

	private void write(String name, String contents) throws CoreException {
		project.getFile(name).create(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)), false, null);
	}

	private String read(String name) throws Exception {
		IFile file = project.getFile(name);
		try (var stream = file.getContents()) {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
