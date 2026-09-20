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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Bundle;

class CleanupPatchLifecycleTest {

	@TempDir
	Path temporaryDirectory;

	private final NullProgressMonitor monitor= new NullProgressMonitor();
	private IProject project;

	@BeforeEach
	void createProject() throws Exception {
		project= ResourcesPlugin.getWorkspace().getRoot()
				.getProject("CleanupPatchLifecycle-" + System.nanoTime()); //$NON-NLS-1$
		project.create(monitor);
		project.open(monitor);
		var description= project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, monitor);
		var javaProject= JavaCore.create(project);
		IFolder sourceFolder= project.getFolder("src"); //$NON-NLS-1$
		sourceFolder.create(true, true, monitor);
		javaProject.setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(sourceFolder.getFullPath()),
				JavaCore.newContainerEntry(new org.eclipse.core.runtime.Path("org.eclipse.jdt.launching.JRE_CONTAINER")) }, //$NON-NLS-1$
				project.getFullPath().append("bin"), monitor); //$NON-NLS-1$
	}

	@AfterEach
	void deleteProject() throws Exception {
		if (project != null && project.exists()) {
			project.delete(true, true, monitor);
		}
	}

	@Test
	void localApplyPatchTracksCurrentRunAndClearsNoOpOutput() throws Exception {
		IFile source= createSource("src/test/LocalPatch.java", //$NON-NLS-1$
				"""
				package test;
				public class LocalPatch{
				void run(){
				System.out.println("local");
				}
				}
				""");
		byte[] original= read(source);
		Path patch= temporaryDirectory.resolve("local.patch"); //$NON-NLS-1$
		Path config= writeConfig("cleanup.format_source_code=true\n"); //$NON-NLS-1$

		Object firstResult= new CodeCleanupApplication().start(new TestApplicationContext(
				"-config", config.toString(), "--mode", "apply", "--patch", patch.toString(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				source.getLocation().toFile().getAbsolutePath()));

		assertEquals(IApplication.EXIT_OK, firstResult);
		byte[] firstPatch= Files.readAllBytes(patch);
		assertTrue(firstPatch.length > 0);
		assertTrue(new String(firstPatch, StandardCharsets.ISO_8859_1).contains("--- a/src/test/LocalPatch.java")); //$NON-NLS-1$
		assertNotEquals(new String(original, StandardCharsets.UTF_8),
				new String(read(source), StandardCharsets.UTF_8));

		Object secondResult= new CodeCleanupApplication().start(new TestApplicationContext(
				"-config", config.toString(), "--mode", "apply", "--patch", patch.toString(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				source.getLocation().toFile().getAbsolutePath()));

		assertEquals(IApplication.EXIT_OK, secondResult);
		assertArrayEquals(new byte[0], Files.readAllBytes(patch));
	}

	@Test
	void projectWideApplyPatchTracksCurrentRunAndClearsNoOpOutput() throws Exception {
		IFile source= createSource("src/test/ProjectWidePatch.java", //$NON-NLS-1$
				"""
				package test;
				public class ProjectWidePatch{
				int value(){
				return 1;
				}
				}
				""");
		byte[] original= read(source);
		Path patch= temporaryDirectory.resolve("project-wide.patch"); //$NON-NLS-1$
		Path report= temporaryDirectory.resolve("project-wide-report.json"); //$NON-NLS-1$
		Path config= writeConfig("cleanup.format_source_code=true\n"); //$NON-NLS-1$

		Object firstResult= new ProjectWideCodeCleanupApplication().start(new TestApplicationContext(
				"--project", project.getName(), "--config", config.toString(), "--report", report.toString(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"--patch", patch.toString(), "--mode", "apply")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		assertEquals(Integer.valueOf(CodeCleanupApplication.EXIT_OK), firstResult);
		byte[] firstPatch= Files.readAllBytes(patch);
		assertTrue(firstPatch.length > 0);
		assertTrue(new String(firstPatch, StandardCharsets.ISO_8859_1)
				.contains("--- a/src/test/ProjectWidePatch.java")); //$NON-NLS-1$
		assertNotEquals(new String(original, StandardCharsets.UTF_8),
				new String(read(source), StandardCharsets.UTF_8));

		Object secondResult= new ProjectWideCodeCleanupApplication().start(new TestApplicationContext(
				"--project", project.getName(), "--config", config.toString(), "--report", report.toString(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"--patch", patch.toString(), "--mode", "apply")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		assertEquals(Integer.valueOf(CodeCleanupApplication.EXIT_OK), secondResult);
		assertArrayEquals(new byte[0], Files.readAllBytes(patch));
	}

	private Path writeConfig(String content) throws Exception {
		return Files.writeString(temporaryDirectory.resolve("cleanup.properties"), content, StandardCharsets.ISO_8859_1); //$NON-NLS-1$
	}

	private IFile createSource(String relativePath, String source) throws Exception {
		IFile file= project.getFile(relativePath);
		if (file.getParent() instanceof IFolder folder && !folder.exists()) {
			createFolder(folder);
		}
		try (ByteArrayInputStream input= new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8))) {
			file.create(input, true, monitor);
		}
		return file;
	}

	private void createFolder(IFolder folder) throws Exception {
		if (folder.getParent() instanceof IFolder parent && !parent.exists()) {
			createFolder(parent);
		}
		folder.create(true, true, monitor);
	}

	private static byte[] read(IFile file) throws Exception {
		try (var input= file.getContents()) {
			return input.readAllBytes();
		}
	}

	private static final class TestApplicationContext implements IApplicationContext {
		private final String[] arguments;

		TestApplicationContext(String... arguments) {
			this.arguments= arguments.clone();
		}

		@Override
		@SuppressWarnings({ "rawtypes", "unchecked" })
		public Map getArguments() {
			Map result= new HashMap();
			result.put(APPLICATION_ARGS, arguments.clone());
			return result;
		}

		@Override
		public void applicationRunning() {
			// Nothing to do for tests.
		}

		@Override
		public String getBrandingApplication() {
			return null;
		}

		@Override
		public Bundle getBrandingBundle() {
			return null;
		}

		@Override
		public String getBrandingDescription() {
			return null;
		}

		@Override
		public String getBrandingId() {
			return null;
		}

		@Override
		public String getBrandingName() {
			return null;
		}

		@Override
		public String getBrandingProperty(String key) {
			return null;
		}

		@Override
		public void setResult(Object result, IApplication application) {
			// Result asserted directly by the tests.
		}
	}
}
