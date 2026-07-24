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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CleanupInputCollectorTest {

	@TempDir
	Path temporaryDirectory;

	private final CleanupInputCollector collector= new CleanupInputCollector();

	@Test
	void recursivelyCollectsJavaSourcesInStableOrder() throws IOException {
		Path project= Files.createDirectory(temporaryDirectory.resolve("project")); //$NON-NLS-1$
		createSource(project.resolve("src/main/java/z/B.java")); //$NON-NLS-1$
		createSource(project.resolve("src/main/java/a/A.java")); //$NON-NLS-1$
		createSource(project.resolve("src/main/resources/application.properties")); //$NON-NLS-1$

		CleanupInputCollector.Result result= collector.collect(List.of(project.toFile()),
				CodeCleanupApplication.CleanupScope.BOTH);

		assertEquals(List.of("src/main/java/a/A.java", "src/main/java/z/B.java"), relativePaths(project, result)); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(result.diagnostics().isEmpty());
	}

	@Test
	void canonicalizesAndDeduplicatesOverlappingInputs() throws IOException {
		Path project= Files.createDirectory(temporaryDirectory.resolve("project")); //$NON-NLS-1$
		Path source= createSource(project.resolve("src/main/java/A.java")); //$NON-NLS-1$
		File equivalentPath= project.resolve("src/main/../main/java/A.java").toFile(); //$NON-NLS-1$

		CleanupInputCollector.Result result= collector.collect(
				List.of(project.toFile(), source.toFile(), equivalentPath),
				CodeCleanupApplication.CleanupScope.BOTH);

		assertEquals(List.of("src/main/java/A.java"), relativePaths(project, result)); //$NON-NLS-1$
		assertTrue(result.diagnostics().isEmpty());
	}

	@Test
	void appliesMainTestAndBothFiltersToNestedSourceLayouts() throws IOException {
		Path project= Files.createDirectory(temporaryDirectory.resolve("project")); //$NON-NLS-1$
		createSource(project.resolve("src/main/java/Main.java")); //$NON-NLS-1$
		createSource(project.resolve("src/test/java/UnitTest.java")); //$NON-NLS-1$
		createSource(project.resolve("src/testFixtures/java/Fixture.java")); //$NON-NLS-1$
		createSource(project.resolve("src/integration-test/java/IntegrationTest.java")); //$NON-NLS-1$

		CleanupInputCollector.Result main= collector.collect(List.of(project.toFile()),
				CodeCleanupApplication.CleanupScope.MAIN);
		CleanupInputCollector.Result test= collector.collect(List.of(project.toFile()),
				CodeCleanupApplication.CleanupScope.TEST);
		CleanupInputCollector.Result both= collector.collect(List.of(project.toFile()),
				CodeCleanupApplication.CleanupScope.BOTH);

		assertEquals(List.of("src/main/java/Main.java"), relativePaths(project, main)); //$NON-NLS-1$
		assertEquals(List.of(
				"src/integration-test/java/IntegrationTest.java", //$NON-NLS-1$
				"src/test/java/UnitTest.java", //$NON-NLS-1$
				"src/testFixtures/java/Fixture.java"), relativePaths(project, test)); //$NON-NLS-1$
		assertEquals(List.of(
				"src/integration-test/java/IntegrationTest.java", //$NON-NLS-1$
				"src/main/java/Main.java", //$NON-NLS-1$
				"src/test/java/UnitTest.java", //$NON-NLS-1$
				"src/testFixtures/java/Fixture.java"), relativePaths(project, both)); //$NON-NLS-1$
	}

	@Test
	void recordsNullAndMissingInputsWithoutAbortingOtherEntries() throws IOException {
		Path valid= createSource(temporaryDirectory.resolve("Valid.java")); //$NON-NLS-1$
		Path missing= temporaryDirectory.resolve("Missing.java"); //$NON-NLS-1$

		CleanupInputCollector.Result result= collector.collect(
				Arrays.asList(null, missing.toFile(), valid.toFile()),
				CodeCleanupApplication.CleanupScope.BOTH);

		assertEquals(List.of(valid.toRealPath().toFile()), result.files());
		assertEquals(2, result.diagnostics().size());
		assertEquals("", result.diagnostics().get(0).input()); //$NON-NLS-1$
		assertEquals(missing.toString(), result.diagnostics().get(1).input());
	}

	@Test
	void reportsNullInputCollectionAndRejectsNullScope() {
		CleanupInputCollector.Result result= collector.collect(null, CodeCleanupApplication.CleanupScope.BOTH);

		assertTrue(result.files().isEmpty());
		assertEquals(1, result.diagnostics().size());
		assertThrows(NullPointerException.class, () -> collector.collect(List.of(), null));
	}

	@Test
	void recognizesOnlyCompleteTestPathSegments() {
		assertTrue(CleanupInputCollector.isTestPath(Path.of("project/src/test/java/A.java"))); //$NON-NLS-1$
		assertTrue(CleanupInputCollector.isTestPath(Path.of("project/src/testFixtures/java/A.java"))); //$NON-NLS-1$
		assertFalse(CleanupInputCollector.isTestPath(Path.of("project/src/main/java/Contest.java"))); //$NON-NLS-1$
		assertFalse(CleanupInputCollector.isTestPath(Path.of("tests/project/src/main/java/A.java"))); //$NON-NLS-1$
	}

	private static Path createSource(Path path) throws IOException {
		Files.createDirectories(Objects.requireNonNull(path.getParent()));
		return Files.writeString(path, "class A {}\n"); //$NON-NLS-1$
	}

	private static List<String> relativePaths(Path project, CleanupInputCollector.Result result) throws IOException {
		Path canonicalProject= project.toRealPath();
		return result.files().stream()
				.map(File::toPath)
				.map(canonicalProject::relativize)
				.map(Path::toString)
				.map(path -> path.replace(File.separatorChar, '/'))
				.toList();
	}
}
