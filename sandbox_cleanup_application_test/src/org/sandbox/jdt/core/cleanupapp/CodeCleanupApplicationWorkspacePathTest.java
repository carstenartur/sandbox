/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.core.cleanupapp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Physical path aliases must not lose or arbitrarily choose a Java project context. */
class CodeCleanupApplicationWorkspacePathTest {

	private static final String ORIGINAL= "class Sample{int value(){return 1;}}\n"; //$NON-NLS-1$
	private final NullProgressMonitor monitor= new NullProgressMonitor();
	@TempDir
	Path temporary;
	private IProject project;
	private IProject otherProject;

	@BeforeEach
	void createProject() throws Exception {
		// Platform temporary directories may themselves be symbolic links.
		temporary= temporary.toRealPath();
		project= javaProject();
	}

	@AfterEach
	void deleteProjects() throws Exception {
		if (otherProject != null && otherProject.exists()) otherProject.delete(true, true, monitor);
		if (project != null && project.exists()) project.delete(true, true, monitor);
	}

	@ParameterizedTest
	@ValueSource(strings = { "apply", "check", "diff" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	void aSourceAliasKeepsTheImportedWorkspaceMappingAndSnapshot(String mode) throws Exception {
		assumeTrue(Platform.OS_LINUX.equals(Platform.getOS()), "This fixture uses unprivileged Linux symbolic links"); //$NON-NLS-1$
		IFile source= project.getFile("src/Sample.java"); //$NON-NLS-1$
		try (var input= new ByteArrayInputStream(ORIGINAL.getBytes(StandardCharsets.UTF_8))) {
			source.create(input, true, monitor);
		}
		Path alias= Files.createSymbolicLink(temporary.resolve("project-alias"), project.getLocation().toFile().toPath()); //$NON-NLS-1$
		Path input= alias.resolve("src/Sample.java"); //$NON-NLS-1$
		assertNotEquals(input.toFile().getAbsoluteFile(), input.toFile().getCanonicalFile());
		Path patch= temporary.resolve("alias.patch"); //$NON-NLS-1$

		Object result= run(alias.toString(), input, mode, patch);

		assertEquals(Integer.valueOf("apply".equals(mode) ? CodeCleanupApplication.EXIT_OK //$NON-NLS-1$
				: CodeCleanupApplication.EXIT_CHANGES), result);
		assertTrue(Files.size(patch) > 0);
		assertTrue(Files.readString(patch, StandardCharsets.UTF_8).contains("src/Sample.java")); //$NON-NLS-1$
		if ("apply".equals(mode)) { //$NON-NLS-1$
			assertNotEquals(ORIGINAL, Files.readString(input, StandardCharsets.UTF_8));
		} else {
			assertArrayEquals(ORIGINAL.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(input));
		}
	}

	@Test
	void aUniqueLinkedSourceRetainsItsWorkspaceContext() throws Exception {
		Path input= Files.writeString(temporary.resolve("Sample.java"), ORIGINAL, StandardCharsets.UTF_8); //$NON-NLS-1$
		link(project, input);
		assertEquals(1, ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(input.toUri()).length);
		Path patch= temporary.resolve("linked.patch"); //$NON-NLS-1$

		Object result= run(project.getLocation().toOSString(), input, "apply", patch); //$NON-NLS-1$

		assertEquals(Integer.valueOf(CodeCleanupApplication.EXIT_OK), result);
		assertNotEquals(ORIGINAL, Files.readString(input, StandardCharsets.UTF_8));
		assertTrue(Files.readString(patch, StandardCharsets.UTF_8).contains("src/Sample.java")); //$NON-NLS-1$
	}

	@Test
	void multipleLinkedProjectContextsAreNotChosenArbitrarily() throws Exception {
		Path input= Files.writeString(temporary.resolve("Sample.java"), ORIGINAL, StandardCharsets.UTF_8); //$NON-NLS-1$
		link(project, input);
		otherProject= javaProject();
		link(otherProject, input);
		assertEquals(2, ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(input.toUri()).length);
		Path patch= temporary.resolve("ambiguous.patch"); //$NON-NLS-1$

		Object result= run(project.getLocation().toOSString(), input, "apply", patch); //$NON-NLS-1$

		assertEquals(Integer.valueOf(CodeCleanupApplication.EXIT_ERROR), result);
		assertArrayEquals(ORIGINAL.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(input));
		assertEquals(0, Files.size(patch));
	}

	@ParameterizedTest
	@ValueSource(strings = { "Sample.java", "Other.java" }) //$NON-NLS-1$ //$NON-NLS-2$
	void aSourceAliasDoesNotHideAnotherLinkedProjectContext(String otherFileName) throws Exception {
		assumeTrue(Platform.OS_LINUX.equals(Platform.getOS()), "This fixture uses unprivileged Linux symbolic links"); //$NON-NLS-1$
		Path physical= Files.writeString(temporary.resolve("Sample.java"), ORIGINAL, StandardCharsets.UTF_8); //$NON-NLS-1$
		Path alias= Files.createSymbolicLink(temporary.resolve("source-alias"), temporary); //$NON-NLS-1$
		Path input= alias.resolve("Sample.java"); //$NON-NLS-1$
		link(project, input);
		otherProject= javaProject();
		IFile second= otherProject.getFile("src/" + otherFileName); //$NON-NLS-1$
		second.createLink(input.toUri(), IResource.NONE, monitor);
		IFile first= project.getFile("src/Sample.java"); //$NON-NLS-1$
		assertNotEquals(first, second);
		assertTrue(first.isAccessible());
		assertTrue(second.isAccessible());
		assertTrue(Files.isSameFile(first.getLocation().toFile().toPath(), second.getLocation().toFile().toPath()));
		assertTrue(Files.isSameFile(physical, first.getLocation().toFile().toPath()));
		Path patch= temporary.resolve("ambiguous-alias.patch"); //$NON-NLS-1$

		Object result= run(project.getLocation().toOSString(), input, "apply", patch); //$NON-NLS-1$

		assertEquals(Integer.valueOf(CodeCleanupApplication.EXIT_ERROR), result);
		assertArrayEquals(ORIGINAL.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(input));
		assertEquals(0, Files.size(patch));
	}

	private IProject javaProject() throws Exception {
		IProject created= ResourcesPlugin.getWorkspace().getRoot().getProject("CleanupPath-" + System.nanoTime()); //$NON-NLS-1$
		created.create(monitor);
		created.open(monitor);
		var description= created.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		created.setDescription(description, monitor);
		var source= created.getFolder("src"); //$NON-NLS-1$
		source.create(true, true, monitor);
		JavaCore.create(created).setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(source.getFullPath()) },
				created.getFullPath().append("bin"), monitor); //$NON-NLS-1$
		return created;
	}

	private void link(IProject owner, Path file) throws Exception {
		owner.getFile("src/Sample.java").createLink(file.toUri(), IResource.NONE, monitor); //$NON-NLS-1$
	}

	private Object run(String importDirectory, Path input, String mode, Path patch) throws Exception {
		Path configuration= Files.writeString(temporary.resolve("cleanup.properties"), //$NON-NLS-1$
				"cleanup.format_source_code=true\n", StandardCharsets.ISO_8859_1); //$NON-NLS-1$
		String[] arguments= { "--import-project", importDirectory, "-config", configuration.toString(), //$NON-NLS-1$ //$NON-NLS-2$
				"--mode", mode, "--patch", patch.toString(), input.toString() }; //$NON-NLS-1$ //$NON-NLS-2$
		IApplicationContext context= (IApplicationContext) Proxy.newProxyInstance(IApplicationContext.class.getClassLoader(),
				new Class<?>[] { IApplicationContext.class }, (proxy, method, parameters) ->
						"getArguments".equals(method.getName()) ? Map.of(IApplicationContext.APPLICATION_ARGS, arguments.clone()) : null); //$NON-NLS-1$
		return new CodeCleanupApplicationWrapper().start(context);
	}
}
