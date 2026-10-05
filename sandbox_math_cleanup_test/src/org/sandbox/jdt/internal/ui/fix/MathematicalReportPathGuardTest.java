/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MathematicalReportPathGuardTest {
	@TempDir
	Path temporary;

	@Test
	void rejectsAnotherProjectsMetadataWithoutChangingIt() throws Exception {
		Path project = Files.createDirectories(temporary.resolve("other-project")); //$NON-NLS-1$
		Path metadata = Files.writeString(project.resolve(".project"), "<projectDescription/>", StandardCharsets.UTF_8); //$NON-NLS-1$ //$NON-NLS-2$
		assertThrows(IllegalArgumentException.class,
				() -> MathematicalReportPathGuard.requireSafe(metadata, List.of(), List.of(project)));
		assertEquals("<projectDescription/>", Files.readString(metadata, StandardCharsets.UTF_8)); //$NON-NLS-1$
	}

	@Test
	void rejectsWorkspaceMetadata() throws Exception {
		Path metadata = Files.createDirectories(temporary.resolve("workspace/.metadata")); //$NON-NLS-1$
		Path report = metadata.resolve(".plugins/example/state.json"); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class,
				() -> MathematicalReportPathGuard.requireSafe(report, List.of(), List.of(metadata)));
		assertFalse(Files.exists(report.getParent()));
	}

	@Test
	void rejectsNewFilesUnderProtectedProjects() throws Exception {
		Path project = Files.createDirectories(temporary.resolve("project")); //$NON-NLS-1$
		Path report = project.resolve("new/sub/report.json"); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class,
				() -> MathematicalReportPathGuard.requireSafe(report, List.of(), List.of(project)));
		assertFalse(Files.exists(project.resolve("new"))); //$NON-NLS-1$
	}

	@Test
	void protectsLocationsEvenBeforeTheProjectDirectoryExists() {
		Path project = temporary.resolve("closed-or-unavailable-project"); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class, () -> MathematicalReportPathGuard.requireSafe(
				project.resolve(".classpath"), List.of(), List.of(project))); //$NON-NLS-1$
		assertFalse(Files.exists(project));
	}

	@Test
	void protectsConfigurationOutsideProjects() throws Exception {
		Path configuration = Files.writeString(temporary.resolve("options.properties"), "enabled=true", StandardCharsets.UTF_8); //$NON-NLS-1$ //$NON-NLS-2$
		assertThrows(IllegalArgumentException.class, () -> MathematicalReportPathGuard.requireSafe(
				configuration, List.of(configuration), List.of()));
		assertEquals("enabled=true", Files.readString(configuration, StandardCharsets.UTF_8)); //$NON-NLS-1$
	}

	@Test
	void normalizesDotSegmentsBeforeComparingPaths() throws Exception {
		Path project = Files.createDirectories(temporary.resolve("project")); //$NON-NLS-1$
		Path report = temporary.resolve("outside/../project/.classpath"); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class,
				() -> MathematicalReportPathGuard.requireSafe(report, List.of(), List.of(project)));
	}

	@Test
	void allowsExternalReportsWithoutCreatingDirectories() throws Exception {
		Path project = Files.createDirectories(temporary.resolve("project")); //$NON-NLS-1$
		Path report = temporary.resolve("reports/new/report.json"); //$NON-NLS-1$
		assertDoesNotThrow(() -> MathematicalReportPathGuard.requireSafe(report, List.of(), List.of(project)));
		assertFalse(Files.exists(report.getParent()));
	}

	@Test
	void doesNotConfuseACommonNamePrefixWithAnAncestor() throws Exception {
		Path project = Files.createDirectories(temporary.resolve("project")); //$NON-NLS-1$
		assertDoesNotThrow(() -> MathematicalReportPathGuard.requireSafe(
				temporary.resolve("project-reports/report.json"), List.of(), List.of(project))); //$NON-NLS-1$
	}

	@Test
	void allowsUpdatingAnExistingExternalReportWithoutModifyingItDuringPreflight() throws Exception {
		Path project = Files.createDirectories(temporary.resolve("project")); //$NON-NLS-1$
		Path report = Files.writeString(temporary.resolve("report.json"), "{}", StandardCharsets.UTF_8); //$NON-NLS-1$ //$NON-NLS-2$
		assertDoesNotThrow(() -> MathematicalReportPathGuard.requireSafe(report, List.of(), List.of(project)));
		assertEquals("{}", Files.readString(report, StandardCharsets.UTF_8)); //$NON-NLS-1$
	}

	@Test
	void rejectsDirectoriesAndFilesystemRoots() {
		assertThrows(IllegalArgumentException.class,
				() -> MathematicalReportPathGuard.requireSafe(temporary, List.of(), List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> MathematicalReportPathGuard.requireSafe(temporary.toAbsolutePath().getRoot(), List.of(), List.of()));
	}
}
