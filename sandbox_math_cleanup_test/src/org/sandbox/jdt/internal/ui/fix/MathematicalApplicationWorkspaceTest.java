/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ISaveContext;
import org.eclipse.core.resources.ISaveParticipant;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Exercises the actual application and workspace save participants, not a mock workspace. */
class MathematicalApplicationWorkspaceTest {
	@TempDir
	Path temporary;
	private final NullProgressMonitor monitor = new NullProgressMonitor();
	private final AtomicInteger completedSaves = new AtomicInteger();
	private final AtomicInteger preparedSaves = new AtomicInteger();
	private IWorkspace workspace;
	private IProject project;
	private Path configuration;
	private Path report;
	private String participantId;
	private boolean rejectSave;

	@BeforeEach
	void createFixture() throws Exception {
		workspace = ResourcesPlugin.getWorkspace();
		project = workspace.getRoot().getProject("MathematicsSave-" + UUID.randomUUID()); //$NON-NLS-1$
		project.create(monitor);
		project.open(monitor);
		var description = project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, monitor);
		project.getFolder("bin").create(true, true, monitor); //$NON-NLS-1$
		JavaCore.create(project).setRawClasspath(new IClasspathEntry[0],
				project.getFolder("bin").getFullPath(), monitor); //$NON-NLS-1$
		project.getFile("state.txt").create(new ByteArrayInputStream(new byte[] { 1 }), true, monitor); //$NON-NLS-1$
		configuration = Files.writeString(temporary.resolve("math.properties"), //$NON-NLS-1$
				"cleanup.mathematics=true", StandardCharsets.UTF_8); //$NON-NLS-1$
		report = temporary.resolve("report.json"); //$NON-NLS-1$
		participantId = "sandbox_math_cleanup_test.save." + UUID.randomUUID(); //$NON-NLS-1$
		workspace.addSaveParticipant(participantId, new ISaveParticipant() {
			@Override
			public void prepareToSave(ISaveContext context) throws CoreException {
				if (context.getKind() == ISaveContext.FULL_SAVE) {
					preparedSaves.incrementAndGet();
					if (rejectSave) {
						throw new CoreException(Status.error("Injected workspace save failure")); //$NON-NLS-1$
					}
				}
			}
			@Override
			public void saving(ISaveContext context) { }
			@Override
			public void doneSaving(ISaveContext context) {
				if (context.getKind() == ISaveContext.FULL_SAVE) completedSaves.incrementAndGet();
			}
			@Override
			public void rollback(ISaveContext context) { }
		});
	}

	@AfterEach
	void removeFixture() throws Exception {
		if (participantId != null) workspace.removeSaveParticipant(participantId);
		if (project != null && project.exists()) project.delete(true, true, monitor);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void successfulCommandsCompleteAFullSaveBeforeReturning(boolean apply) throws Exception {
		assertEquals(IApplication.EXIT_OK, invoke(apply));
		assertEquals(1, preparedSaves.get(), "Even analysis-only completion must prepare a full workspace save"); //$NON-NLS-1$
		assertEquals(1, completedSaves.get(), "A checkpoint or project save is not a full workspace save"); //$NON-NLS-1$
		assertEquals("SUCCESS", new ObjectMapper().readTree(report.toFile()).path("status").asText()); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(project.getFile("state.txt").exists()); //$NON-NLS-1$
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void saveFailuresCannotBeReportedAsSuccessfulCommands(boolean apply) throws Exception {
		rejectSave = true;
		assertEquals(Integer.valueOf(1), invoke(apply));
		assertEquals(1, preparedSaves.get());
		assertEquals(0, completedSaves.get());
		var result = new ObjectMapper().readTree(report.toFile());
		assertEquals("FAILURE", result.path("status").asText()); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(result.path("rollbackComplete").isNull(), "Workspace save failure must not claim a source rollback"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void rejectedArgumentsDoNotPerformAFullSave() throws Exception {
		Files.writeString(configuration, "unknown.mathematics.option=true", StandardCharsets.UTF_8); //$NON-NLS-1$
		assertEquals(Integer.valueOf(1), invoke(false));
		assertEquals(0, preparedSaves.get());
		assertFalse(Files.exists(report));
	}

	private Object invoke(boolean apply) {
		List<String> arguments = new ArrayList<>(List.of("--project", project.getName(), //$NON-NLS-1$
				"--config", configuration.toString(), "--report", report.toString())); //$NON-NLS-1$ //$NON-NLS-2$
		if (apply) arguments.add("--apply"); //$NON-NLS-1$
		var context = (IApplicationContext) Proxy.newProxyInstance(IApplicationContext.class.getClassLoader(),
				new Class<?>[] { IApplicationContext.class }, (proxy, method, values) -> {
					if (method.getName().equals("getArguments")) { //$NON-NLS-1$
						return Map.of(IApplicationContext.APPLICATION_ARGS, arguments.toArray(String[]::new));
					}
					throw new AssertionError(method);
				});
		return new MathematicalApplication().start(context);
	}
}
