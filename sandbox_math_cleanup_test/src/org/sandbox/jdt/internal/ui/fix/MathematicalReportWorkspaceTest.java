/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Native workspace coverage: a selected project's units cannot protect others. */
class MathematicalReportWorkspaceTest {
	@TempDir
	Path temporary;
	private final NullProgressMonitor monitor = new NullProgressMonitor();
	private IWorkspace workspace;
	private IProject project;
	private Path configuration;

	@BeforeEach
	void createProject() throws Exception {
		workspace = ResourcesPlugin.getWorkspace();
		configuration = Files.writeString(temporary.resolve("math.properties"), //$NON-NLS-1$
				"cleanup.mathematics=true", StandardCharsets.UTF_8); //$NON-NLS-1$
		project = workspace.getRoot().getProject("ReportPathGuard-" + UUID.randomUUID()); //$NON-NLS-1$
		project.create(monitor);
		project.open(monitor);
	}

	@AfterEach
	void deleteProject() throws Exception {
		if (project != null && project.exists()) {
			project.delete(true, true, new NullProgressMonitor());
		}
	}

	@Test
	void protectsResourcesOfAProjectAbsentFromTheAnalyzedUnits() throws Exception {
		Path metadata = project.getFile(".project").getLocation().toFile().toPath(); //$NON-NLS-1$
		String before = Files.readString(metadata, StandardCharsets.UTF_8);
		reject(metadata);
		assertEquals(before, Files.readString(metadata, StandardCharsets.UTF_8));
	}

	@Test
	void protectsClosedProjectRoots() throws Exception {
		Path report = project.getLocation().toFile().toPath().resolve("new/report.json"); //$NON-NLS-1$
		project.close(monitor);
		reject(report);
		assertFalse(Files.exists(report.getParent()));
	}

	@Test
	void protectsWorkspaceMetadataNotRepresentedByAProject() throws Exception {
		Path report = workspace.getRoot().getLocation().toFile().toPath()
				.resolve(".metadata/report-path-probe-" + UUID.randomUUID()).resolve("report.json"); //$NON-NLS-1$ //$NON-NLS-2$
		reject(report);
		assertFalse(Files.exists(report.getParent()));
	}

	@Test
	void protectsNewFilesWithinAnEclipseLinkedFolder() throws Exception {
		Path external = Files.createDirectory(temporary.resolve("linked-content")); //$NON-NLS-1$
		project.getFolder("linked").createLink(external.toUri(), IResource.NONE, monitor); //$NON-NLS-1$
		Path report = external.resolve("new/report.json"); //$NON-NLS-1$
		reject(report);
		assertFalse(Files.exists(report.getParent()));
	}

	@Test
	void protectsAnEclipseLinkedFileWithoutChangingIt() throws Exception {
		Path external = Files.writeString(temporary.resolve("linked.txt"), "keep", StandardCharsets.UTF_8); //$NON-NLS-1$ //$NON-NLS-2$
		project.getFile("linked.txt").createLink(external.toUri(), IResource.NONE, monitor); //$NON-NLS-1$
		reject(external);
		assertEquals("keep", Files.readString(external, StandardCharsets.UTF_8)); //$NON-NLS-1$
	}

	@Test
	void cancellationStopsResourceTraversalWithoutProducingAReport() throws Exception {
		project.getFile("resource.txt").create(new ByteArrayInputStream(new byte[0]), true, monitor); //$NON-NLS-1$
		Path report = temporary.resolve("reports/new/report.json"); //$NON-NLS-1$
		monitor.setCanceled(true);
		assertThrows(OperationCanceledException.class, () -> check(report));
		assertFalse(Files.exists(report.getParent()));
	}

	private void reject(Path report) {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> check(report));
		assertTrue(failure.getMessage().startsWith("The report must be outside"), failure::getMessage); //$NON-NLS-1$
	}

	private void check(Path report) throws Exception {
		MathematicalArguments arguments = MathematicalArguments.parse(new String[] {
				"--project", "analyzed-project", "--config", configuration.toString(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"--report", report.toString() }); //$NON-NLS-1$
		// Deliberately empty: protection must come from the workspace inventory,
		// not the explicitly supplied compilation units of the analyzed project.
		MathematicalApplication.protectReport(arguments, workspace, List.of(), monitor);
	}
}
