/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** Protects the ownership boundary between Usage View and Help SWTBot tests. */
public class EclipseHelpTestModuleBoundaryTest {

	private static final Set<String> HELP_TEST_SOURCES= Set.of(
			"CoordinatedJUnitPreviewSWTBotScenario.java", //$NON-NLS-1$
			"EclipseHelpScreenshotEvidenceTest.java", //$NON-NLS-1$
			"EclipseHelpStructureTest.java", //$NON-NLS-1$
			"EclipseHelpTestModuleBoundaryTest.java", //$NON-NLS-1$
			"FocusedCleanupConfigurationScreenshots.java", //$NON-NLS-1$
			"JUnitBestEffortCleanupDialogSWTBotTest.java", //$NON-NLS-1$
			"SandboxAtomicPreviewPatchedJdtSWTBotTest.java", //$NON-NLS-1$
			"SandboxCheckout.java", //$NON-NLS-1$
			"SandboxHelpScreenshotsMergeGateSWTBotTest.java", //$NON-NLS-1$
			"SandboxHelpScreenshotsSWTBotTest.java"); //$NON-NLS-1$

	private static final Set<String> USAGE_VIEW_TEST_SOURCES= Set.of(
			"JavaHelperViewSWTBotTest.java", //$NON-NLS-1$
			"JavaHelperViewTest.java"); //$NON-NLS-1$

	@Test
	public void usageViewTestPluginContainsOnlyUsageViewTests() throws Exception {
		Path repository= SandboxCheckout.locate(null);
		Path sources= repository.resolve(
				"sandbox_usage_view_test/src/org/sandbox/jdt/ui/helper/views"); //$NON-NLS-1$
		assertEquals(USAGE_VIEW_TEST_SOURCES, javaSources(sources),
				"Help and cleanup scenarios must not drift back into the Usage View test fragment"); //$NON-NLS-1$
	}

	@Test
	public void helpSwtBotPluginOwnsEverySharedHelpScenario() throws Exception {
		Path repository= SandboxCheckout.locate(null);
		Path sources= repository.resolve(
				"sandbox_eclipse_help_swtbot_test/src/org/sandbox/jdt/ui/helper/views"); //$NON-NLS-1$
		assertEquals(HELP_TEST_SOURCES, javaSources(sources));

		String manifest= read(repository,
				"sandbox_eclipse_help_swtbot_test/META-INF/MANIFEST.MF"); //$NON-NLS-1$
		assertTrue(manifest.contains(
				"Bundle-SymbolicName: sandbox_eclipse_help_swtbot_test;singleton:=true")); //$NON-NLS-1$
		assertFalse(manifest.contains("Fragment-Host:"), //$NON-NLS-1$
				"The Help SWTBot module must remain independent of every product bundle"); //$NON-NLS-1$
		assertFalse(read(repository, "sandbox_eclipse_help_swtbot_test/pom.xml") //$NON-NLS-1$
				.contains("sandbox_usage_view"), //$NON-NLS-1$
				"The Help SWTBot module must not use Usage View as a test host"); //$NON-NLS-1$
	}

	@Test
	public void reactorsAndWorkflowsUseTheDedicatedModule() throws Exception {
		Path repository= SandboxCheckout.locate(null);
		assertTrue(read(repository, "pom.xml") //$NON-NLS-1$
				.contains("<module>sandbox_eclipse_help_swtbot_test</module>")); //$NON-NLS-1$

		String helpBuild= read(repository, "sandbox_help_build/pom.xml"); //$NON-NLS-1$
		assertTrue(helpBuild.contains(
				"<module>../sandbox_eclipse_help_swtbot_test</module>")); //$NON-NLS-1$
		assertFalse(helpBuild.contains("<module>../sandbox_usage_view_test</module>")); //$NON-NLS-1$
		assertFalse(helpBuild.contains("<module>../sandbox_usage_view</module>")); //$NON-NLS-1$

		assertWorkflowUsesDedicatedModule(repository,
				".github/workflows/eclipse-help-screenshots.yml"); //$NON-NLS-1$
		assertWorkflowUsesDedicatedModule(repository,
				".github/workflows/patched-jdt-ui-atomic-help-screenshot.yml"); //$NON-NLS-1$
	}

	private static void assertWorkflowUsesDedicatedModule(Path repository, String relativePath)
			throws IOException {
		String workflow= read(repository, relativePath);
		assertTrue(workflow.contains(
				"sandbox_eclipse_help_swtbot_test/target/surefire-reports"), //$NON-NLS-1$
				relativePath);
		assertFalse(workflow.contains("sandbox_usage_view_test/target"), relativePath); //$NON-NLS-1$
	}

	private static Set<String> javaSources(Path directory) throws IOException {
		try (Stream<Path> paths= Files.list(directory)) {
			return paths.filter(Files::isRegularFile)
					.map(EclipseHelpTestModuleBoundaryTest::fileName)
					.filter(name -> name.endsWith(".java")) //$NON-NLS-1$
					.collect(Collectors.toUnmodifiableSet());
		}
	}

	private static String fileName(Path path) {
		Path fileName= path.getFileName();
		if (fileName == null) {
			throw new IllegalArgumentException("Directory entry has no file name: " + path); //$NON-NLS-1$
		}
		return fileName.toString();
	}

	private static String read(Path repository, String relativePath) throws IOException {
		return Files.readString(repository.resolve(relativePath), StandardCharsets.UTF_8);
	}
}
