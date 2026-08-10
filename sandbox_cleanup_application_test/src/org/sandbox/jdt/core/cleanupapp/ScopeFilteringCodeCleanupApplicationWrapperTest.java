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
package org.sandbox.jdt.core.cleanupapp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests deterministic expansion of conventional main/test source trees. */
class ScopeFilteringCodeCleanupApplicationWrapperTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void testScopeFindsNestedTestSourcesFromProjectRoot() throws Exception {
		Path project= createProject();

		String[] filtered= ScopeFilteringCodeCleanupApplicationWrapper.filterArgumentsForScope(
				new String[] { "--scope", "test", "--config", "cleanup.properties", project.toString() }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		assertContains(filtered, project.resolve("src/test/java/ExampleTest.java")); //$NON-NLS-1$
		assertNotContains(filtered, project.resolve("src/main/java/Example.java")); //$NON-NLS-1$
		assertTrue(Arrays.asList(filtered).contains("test")); //$NON-NLS-1$
	}

	@Test
	void mainScopeExcludesNestedTestSources() throws Exception {
		Path project= createProject();

		String[] filtered= ScopeFilteringCodeCleanupApplicationWrapper.filterArgumentsForScope(
				new String[] { "--scope", "main", "--config", "cleanup.properties", project.toString() }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		assertContains(filtered, project.resolve("src/main/java/Example.java")); //$NON-NLS-1$
		assertNotContains(filtered, project.resolve("src/test/java/ExampleTest.java")); //$NON-NLS-1$
	}

	@Test
	void explicitTestDirectoryRemainsSupported() throws Exception {
		Path project= createProject();
		Path testRoot= project.resolve("src/test"); //$NON-NLS-1$

		String[] filtered= ScopeFilteringCodeCleanupApplicationWrapper.filterArgumentsForScope(
				new String[] { "--scope", "test", "--source", testRoot.toString(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						"--config", "cleanup.properties" }); //$NON-NLS-1$ //$NON-NLS-2$

		assertContains(filtered, project.resolve("src/test/java/ExampleTest.java")); //$NON-NLS-1$
		assertTrue(Arrays.asList(filtered).contains("--source")); //$NON-NLS-1$
	}

	@Test
	void ancestorDirectoryNamedTestDoesNotReclassifyMainSources() throws Exception {
		Path project= createProject(temporaryDirectory.resolve("test/workspace/example-project")); //$NON-NLS-1$

		String[] filtered= ScopeFilteringCodeCleanupApplicationWrapper.filterArgumentsForScope(
				new String[] { "--scope", "main", "--config", "cleanup.properties", project.toString() }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		assertContains(filtered, project.resolve("src/main/java/Example.java")); //$NON-NLS-1$
		assertNotContains(filtered, project.resolve("src/test/java/ExampleTest.java")); //$NON-NLS-1$
	}

	@Test
	void packageDirectoryNamedTestUnderMainRemainsMainSource() throws Exception {
		Path project= createProject();
		Path mainPackageSource= project.resolve("src/main/java/org/example/test/Helper.java"); //$NON-NLS-1$
		Files.createDirectories(mainPackageSource.getParent());
		Files.writeString(mainPackageSource, "package org.example.test; class Helper {}", //$NON-NLS-1$
				StandardCharsets.UTF_8);

		String[] main= ScopeFilteringCodeCleanupApplicationWrapper.filterArgumentsForScope(
				new String[] { "--scope", "main", "--config", "cleanup.properties", project.toString() }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		String[] tests= ScopeFilteringCodeCleanupApplicationWrapper.filterArgumentsForScope(
				new String[] { "--scope", "test", "--config", "cleanup.properties", project.toString() }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		assertContains(main, mainPackageSource);
		assertNotContains(tests, mainPackageSource);
	}

	@Test
	void missingArgumentsFailWithActionableDiagnostic() {
		IllegalArgumentException exception= assertThrows(IllegalArgumentException.class,
				() -> ScopeFilteringCodeCleanupApplicationWrapper.filterArgumentsForScope(null));

		assertEquals("Application arguments are unavailable.", exception.getMessage()); //$NON-NLS-1$
	}

	@Test
	void missingFailureMessageUsesActionableDiagnostic() {
		String expected= "Cleanup application failed without a diagnostic message."; //$NON-NLS-1$

		assertEquals(expected, CodeCleanupApplicationWrapper.failureMessage(null));
		assertEquals(expected, CodeCleanupApplicationWrapper.failureMessage(" ")); //$NON-NLS-1$
		assertEquals("details", CodeCleanupApplicationWrapper.failureMessage("details")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void bothScopeLeavesCallerArgumentsUntouched() throws Exception {
		String[] arguments= { "--scope", "both", "--config", "cleanup.properties", "project" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

		assertArrayEquals(arguments,
				ScopeFilteringCodeCleanupApplicationWrapper.filterArgumentsForScope(arguments));
	}

	private Path createProject() throws Exception {
		return createProject(temporaryDirectory.resolve("example-project")); //$NON-NLS-1$
	}

	private static Path createProject(Path project) throws Exception {
		Files.createDirectories(project.resolve("src/main/java")); //$NON-NLS-1$
		Files.createDirectories(project.resolve("src/test/java")); //$NON-NLS-1$
		Files.writeString(project.resolve(".project"), "<projectDescription/>", StandardCharsets.UTF_8); //$NON-NLS-1$ //$NON-NLS-2$
		Files.writeString(project.resolve("src/main/java/Example.java"), //$NON-NLS-1$
				"class Example {}", StandardCharsets.UTF_8); //$NON-NLS-1$
		Files.writeString(project.resolve("src/test/java/ExampleTest.java"), //$NON-NLS-1$
				"class ExampleTest {}", StandardCharsets.UTF_8); //$NON-NLS-1$
		return project;
	}

	private static void assertContains(String[] values, Path expected) {
		assertTrue(Arrays.asList(values).contains(expected.toFile().getPath()),
				() -> "Expected filtered arguments to contain " + expected + ": " + Arrays.toString(values)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void assertNotContains(String[] values, Path unexpected) {
		assertFalse(Arrays.asList(values).contains(unexpected.toFile().getPath()),
				() -> "Did not expect filtered arguments to contain " + unexpected + ": " //$NON-NLS-1$ //$NON-NLS-2$
						+ Arrays.toString(values));
	}
}
