/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.core.cleanupapp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.tools.ToolProvider;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Import must expose persisted external class folders before the first cleanup parse. */
class CodeCleanupApplicationWrapperPersistedClasspathTest {

	@TempDir
	Path temporary;

	@Test
	void persistedExternalClassesAreVisibleDuringTheProtectedDelegateRead() throws Exception {
		Path dependencySource= Files.createDirectories(temporary.resolve("dependency-source/external")) //$NON-NLS-1$
				.resolve("Dependency.java"); //$NON-NLS-1$
		Files.writeString(dependencySource,
				"package external; public class Dependency { public int value() { return 1; } }", StandardCharsets.UTF_8); //$NON-NLS-1$
		Path externalClasses= Files.createDirectories(temporary.resolve("external-classes")); //$NON-NLS-1$
		var compiler= ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler, "A full JDK is required"); //$NON-NLS-1$
		assertEquals(0, compiler.run(null, null, null, "--release", "17", "-d", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				externalClasses.toString(), dependencySource.toString()));
		assertTrue(Files.isRegularFile(externalClasses.resolve("external/Dependency.class"))); //$NON-NLS-1$

		String projectName= "PersistedClasspath-" + System.nanoTime(); //$NON-NLS-1$
		Path projectDirectory= Files.createDirectories(temporary.resolve("persisted-project")); //$NON-NLS-1$
		Path descriptionFile= projectDirectory.resolve(".project"); //$NON-NLS-1$
		Files.writeString(descriptionFile, """
				<?xml version="1.0" encoding="UTF-8"?>
				<projectDescription>
				  <name>$PROJECT</name><comment></comment><projects></projects>
				  <buildSpec><buildCommand><name>org.eclipse.jdt.core.javabuilder</name><arguments></arguments></buildCommand></buildSpec>
				  <natures><nature>org.eclipse.jdt.core.javanature</nature></natures>
				</projectDescription>
				""".replace("$PROJECT", projectName), StandardCharsets.UTF_8); //$NON-NLS-1$ //$NON-NLS-2$
		String library= org.eclipse.core.runtime.Path.fromOSString(externalClasses.toString()).toPortableString()
				.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		Path classpathFile= projectDirectory.resolve(".classpath"); //$NON-NLS-1$
		Files.writeString(classpathFile, """
				<?xml version="1.0" encoding="UTF-8"?>
				<classpath>
				  <classpathentry kind="src" path="src"/>
				  <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
				  <classpathentry kind="lib" path="$LIBRARY"/>
				  <classpathentry kind="output" path="bin"/>
				</classpath>
				""".replace("$LIBRARY", library), StandardCharsets.UTF_8); //$NON-NLS-1$ //$NON-NLS-2$
		byte[] persistedClasspath= Files.readAllBytes(classpathFile);
		Path source= Files.createDirectories(projectDirectory.resolve("src")).resolve("Sample.java"); //$NON-NLS-1$ //$NON-NLS-2$
		String original= "import external.Dependency; public class Sample{Dependency dependency=new Dependency();Object object=dependency;int value(){return dependency.value();}}\n"; //$NON-NLS-1$
		Files.writeString(source, original, StandardCharsets.UTF_8);
		Path configuration= Files.writeString(temporary.resolve("cleanup.properties"), //$NON-NLS-1$
				"cleanup.format_source_code=true\n", StandardCharsets.ISO_8859_1); //$NON-NLS-1$
		Path patch= temporary.resolve("cleanup.patch"); //$NON-NLS-1$
		String[] arguments= { "--import-project", projectDirectory.toString(), "-config", configuration.toString(), //$NON-NLS-1$ //$NON-NLS-2$
				"--mode", "apply", "--patch", patch.toString(), source.toString() }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		var root= ResourcesPlugin.getWorkspace().getRoot();
		IProject project= root.getProject(projectName);
		assertFalse(project.exists(), "The fixture must not pre-initialize the Java project in the workspace"); //$NON-NLS-1$
		AtomicInteger reads= new AtomicInteger();
		AtomicBoolean parsed= new AtomicBoolean();
		IApplicationContext context= (IApplicationContext) Proxy.newProxyInstance(IApplicationContext.class.getClassLoader(),
				new Class<?>[] { IApplicationContext.class }, (proxy, method, parameters) -> {
					if ("getArguments".equals(method.getName())) { //$NON-NLS-1$
						if (reads.incrementAndGet() == 2) {
							assertTrue(project.isOpen());
							var rule= Job.getJobManager().currentRule();
							assertTrue(rule != null && rule.contains(root), "Import and first parse must share the workspace rule"); //$NON-NLS-1$
							assertNotNull(Display.getCurrent(), "Cleanup must execute on the owning SWT thread"); //$NON-NLS-1$
							var unit= JavaCore.createCompilationUnitFrom(project.getFile("src/Sample.java")); //$NON-NLS-1$
							var parser= ASTParser.newParser(AST.getJLSLatest());
							parser.setSource(unit);
							parser.setResolveBindings(true);
							parser.setBindingsRecovery(false);
							CompilationUnit ast= (CompilationUnit) parser.createAST(new NullProgressMonitor());
							TypeDeclaration declaration= (TypeDeclaration) ast.types().getFirst();
							var object= declaration.getFields()[1].getType().resolveBinding();
							assertNotNull(object, "java.lang.Object must resolve independently of the external folder"); //$NON-NLS-1$
							assertFalse(object.isRecovered());
							assertEquals("java.lang.Object", object.getQualifiedName()); //$NON-NLS-1$
							var dependency= declaration.getFields()[0].getType().resolveBinding();
							assertNotNull(dependency, "The persisted external class folder must be visible before cleanup parses source"); //$NON-NLS-1$
							assertFalse(dependency.isRecovered());
							assertEquals("external.Dependency", dependency.getQualifiedName()); //$NON-NLS-1$
							parsed.set(true);
						}
						return Map.of(IApplicationContext.APPLICATION_ARGS, arguments.clone());
					}
					return null;
				});
		try {
			Object result= new CodeCleanupApplicationWrapper().start(context);
			assertEquals(Integer.valueOf(CodeCleanupApplication.EXIT_OK), result);
			assertTrue(parsed.get());
			assertNotEquals(original, Files.readString(source, StandardCharsets.UTF_8));
			assertTrue(Files.size(patch) > 0);
			assertArrayEquals(persistedClasspath, Files.readAllBytes(classpathFile));
		} finally {
			if (project.exists()) project.delete(true, true, new NullProgressMonitor());
		}
	}
}
