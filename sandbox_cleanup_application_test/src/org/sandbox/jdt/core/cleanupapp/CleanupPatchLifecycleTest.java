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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.text.edits.ReplaceEdit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.osgi.framework.Bundle;

import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;

class CleanupPatchLifecycleTest {

	@TempDir
	Path temporaryDirectory;

	private final NullProgressMonitor monitor= new NullProgressMonitor();
	private IProject project;
	private IProject deploymentProject;

	@BeforeEach
	void createProject() throws Exception {
		createProject(null);
	}

	private void createProject(Path location) throws Exception {
		project= ResourcesPlugin.getWorkspace().getRoot()
				.getProject("CleanupPatchLifecycle-" + System.nanoTime()); //$NON-NLS-1$
		var initial= project.getWorkspace().newProjectDescription(project.getName());
		if (location != null) initial.setLocationURI(location.toUri());
		project.create(initial, monitor);
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
		if (deploymentProject != null && deploymentProject.exists()) {
			deploymentProject.delete(true, true, monitor);
		}
		if (project != null && project.exists()) {
			project.delete(true, true, monitor);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "\n", "\r\n" }) //$NON-NLS-1$ //$NON-NLS-2$
	void localApplyPatchTracksCurrentRunAndClearsNoOpOutput(String lineDelimiter) throws Exception {
		IFile source= createSource("src/test/LocalPatch.java", //$NON-NLS-1$
				"""
				package test;
				public class LocalPatch{
				void run(){
				System.out.println("local");
				}
				}
				""".replace("\n", lineDelimiter)); //$NON-NLS-1$
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
		assertPatchRoundTrip(source, original, patch);

		Object secondResult= new CodeCleanupApplication().start(new TestApplicationContext(
				"-config", config.toString(), "--mode", "apply", "--patch", patch.toString(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				source.getLocation().toFile().getAbsolutePath()));

		assertEquals(IApplication.EXIT_OK, secondResult);
		assertArrayEquals(new byte[0], Files.readAllBytes(patch));
	}

	@ParameterizedTest
	@ValueSource(strings = { "\n", "\r\n" }) //$NON-NLS-1$ //$NON-NLS-2$
	void projectWideApplyPatchTracksCurrentRunAndClearsNoOpOutput(String lineDelimiter) throws Exception {
		IFile source= createSource("src/test/ProjectWidePatch.java", //$NON-NLS-1$
				"""
				package test;
				public class ProjectWidePatch{
				int value(){
				return 1;
				}
				}
				""".replace("\n", lineDelimiter)); //$NON-NLS-1$
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
		assertPatchRoundTrip(source, original, patch);

		Object secondResult= new ProjectWideCodeCleanupApplication().start(new TestApplicationContext(
				"--project", project.getName(), "--config", config.toString(), "--report", report.toString(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"--patch", patch.toString(), "--mode", "apply")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		assertEquals(Integer.valueOf(CodeCleanupApplication.EXIT_OK), secondResult);
		assertArrayEquals(new byte[0], Files.readAllBytes(patch));
	}

	@Test
	void batchesKeepChangedTestHierarchyAndSuiteTogether() throws Exception {
		var javaProject= JavaCore.create(project);
		var hierarchy= createTestHierarchy();
		var base= hierarchy.base();
		var child= hierarchy.child();
		var independent= hierarchy.independent();
		var descriptor= hierarchy.descriptor();
		Map<IFile, byte[]> original= new HashMap<>();
		for (IFile file : List.of(base, child, hierarchy.suite(), independent, descriptor, hierarchy.discovery())) {
			original.put(file, read(file));
		}
		Path config= hierarchy.config();
		Path report= temporaryDirectory.resolve("batches.json");
		var diagnostics= hierarchy.diagnostics();
		assertEquals(3, discoverTestMethods(false));
		var args= new ArrayList<>(List.of("--project", project.getName(), "--config", config.toString(),
				"--report", report.toString(), "--batch-max-files", "1"));
		var app= new ProjectWideCodeCleanupApplication();
		assertEquals(2, app.start(new TestApplicationContext(args.toArray(String[]::new))));
		var batches= JsonParser.parseString(Files.readString(report)).getAsJsonObject().getAsJsonArray("batches");
		assertEquals(2, batches.size(), batches.toString());
		var component= batches.get(0).getAsJsonObject();
		String independentId= batches.get(1).getAsJsonObject().get("id").getAsString();
		assertEquals(List.of("src/sample/All.java", "src/sample/Base.java", "src/sample/Child.java"),
				component.getAsJsonArray("files").asList().stream().map(JsonElement::getAsString).toList());
		assertTrue(component.get("oversized").getAsBoolean());
		assertEquals(List.of("META-INF/MANIFEST.MF"), component.getAsJsonArray("prerequisites").asList()
				.stream().map(JsonElement::getAsString).toList());
		for (var entry : original.entrySet()) {
			assertArrayEquals(entry.getValue(), read(entry.getKey()));
		}
		args.addAll(List.of("--batch", component.get("id").getAsString(), "--batch-receipt",
				component.get("receipt").getAsString(), "--mode", "apply"));
		assertEquals(0, app.start(new TestApplicationContext(args.toArray(String[]::new))));
		assertArrayEquals(original.get(independent), read(independent));
		assertTrue(new String(read(child), StandardCharsets.UTF_8).contains("jupiter"));
		assertTrue(new String(read(descriptor), StandardCharsets.UTF_8).contains("org.junit.platform.suite.api"));
		diagnostics.assertNoNewDiagnostics();
		assertEquals(3, discoverTestMethods(true));
		var remainingArgs= args.subList(0, args.indexOf("--batch"));
		assertEquals(2, app.start(new TestApplicationContext(remainingArgs.toArray(String[]::new))));
		var remaining= JsonParser.parseString(Files.readString(report)).getAsJsonObject().getAsJsonArray("batches");
		assertEquals(1, remaining.size(), remaining.toString());
		assertEquals(independentId, remaining.get(0).getAsJsonObject().get("id").getAsString());
		RefactoringCore.getUndoManager().performUndo(null, monitor);
		for (var entry : original.entrySet()) {
			assertArrayEquals(entry.getValue(), read(entry.getKey()), entry.getKey().getName());
		}
		assertArrayEquals(hierarchy.classpath(), javaProject.getRawClasspath());
		diagnostics.assertRestored();
		assertEquals(3, discoverTestMethods(false));
		byte[] revised= (new String(original.get(base), StandardCharsets.UTF_8) + "// revision\n").getBytes(StandardCharsets.UTF_8);
		base.setContents(new ByteArrayInputStream(revised), true, false, monitor);
		assertEquals(1, app.start(new TestApplicationContext(args.toArray(String[]::new))));
		assertTrue(Files.readString(report).contains("receipt"), Files.readString(report));
		assertArrayEquals(revised, read(base));
		assertArrayEquals(original.get(child), read(child));
		assertArrayEquals(original.get(descriptor), read(descriptor));
	}

	@Test
	void unchangedSharedHelperDoesNotMergeIndependentCallers() throws Exception {
		junitClasspath();
		createSource("src/probe/Helper.java", "package probe; public class Helper { public static int value() { return 7; } }\n");
		for (String name : List.of("First", "Second")) {
			createSource("src/probe/" + name + ".java", "package probe; import org.junit.Test; public class " + name +
					"{ @Test public void read(){org.junit.Assert.assertEquals(7, Helper.value());} }\n");
		}
		Path report= temporaryDirectory.resolve("independent.json");
		Path config= writeConfig("cleanup.junitcleanup=true\ncleanup.junitcleanup_4_test=true\n");
		var app= new ProjectWideCodeCleanupApplication();
		var args= new ArrayList<>(List.of("--project", project.getName(), "--config", config.toString(),
				"--report", report.toString(), "--batch-max-files", "1"));
		assertEquals(2, app.start(new TestApplicationContext(args.toArray(String[]::new))));
		var reportJson= JsonParser.parseString(Files.readString(report)).getAsJsonObject();
		var batches= reportJson.getAsJsonArray("batches");
		assertEquals(2, batches.size(), batches.toString());
		for (var batch : batches) {
			assertEquals(1, batch.getAsJsonObject().getAsJsonArray("files").size());
		}
		args.set(args.size() - 1, "3");
		assertEquals(2, app.start(new TestApplicationContext(args.toArray(String[]::new))));
		var larger= JsonParser.parseString(Files.readString(report)).getAsJsonObject().getAsJsonArray("batches");
		assertEquals(1, larger.size(), larger.toString());
		assertEquals(List.of("src/probe/First.java", "src/probe/Second.java"),
				larger.get(0).getAsJsonObject().getAsJsonArray("files").asList().stream().map(JsonElement::getAsString).toList());
	}

	@Test
	void ordinaryClassLiteralsLeaveIndependentJUnitTestsInSeparateBatches() throws Exception {
		junitClasspath();
		for (String name : List.of("Anchor", "First", "Second")) {
			createSource("src/probe/" + name + ".java", "package probe; import org.junit.Test; public class " + name
					+ " { @Test public void reads() { org.junit.Assert.assertEquals(\"Anchor\", Anchor.class.getSimpleName()); } }");
		}
		Path report= temporaryDirectory.resolve("literal-batches.json");
		Path config= writeConfig("cleanup.junitcleanup=true\ncleanup.junitcleanup_4_test=true\n");
		assertEquals(2, new ProjectWideCodeCleanupApplication().start(new TestApplicationContext(new String[] {
				"--project", project.getName(), "--config", config.toString(), "--report", report.toString(), "--batch-max-files", "1" })));
		assertEquals(3, JsonParser.parseString(Files.readString(report)).getAsJsonObject().getAsJsonArray("batches").size());
	}

	@Test
	void aNewCliInvocationRequiresReviewOfChangedProjectInputs() throws Exception {
		junitClasspath();
		var helper= createSource("src/probe/Helper.java", "package probe; public class Helper { static int value() { return 7; } }");
		var test= createSource("src/probe/Example.java",
				"package probe; import org.junit.Test; public class Example { @Test public void checks() { org.junit.Assert.assertEquals(7, Helper.value()); } }");
		var before= read(test);
		var report= temporaryDirectory.resolve("review.json");
		var config= writeConfig("cleanup.junitcleanup=true\ncleanup.junitcleanup_4_test=true\n");
		var args= new ArrayList<>(List.of("--project", project.getName(), "--config", config.toString(), "--report", report.toString(), "--batch-max-files", "1"));
		var app= new ProjectWideCodeCleanupApplication();
		assertEquals(2, app.start(new TestApplicationContext(args.toArray(String[]::new))));
		var batch= JsonParser.parseString(Files.readString(report)).getAsJsonObject().getAsJsonArray("batches").get(0).getAsJsonObject();
		var changed= new String(read(helper), StandardCharsets.UTF_8).replace("return 7", "return 8").getBytes(StandardCharsets.UTF_8);
		helper.setContents(new ByteArrayInputStream(changed), true, false, monitor);
		args.addAll(List.of("--mode", "apply", "--batch", batch.get("id").getAsString(), "--batch-receipt", batch.get("receipt").getAsString()));
		assertEquals(1, app.start(new TestApplicationContext(args.toArray(String[]::new))));
		assertTrue(Files.readString(report).contains("receipt is stale"));
		assertArrayEquals(before, read(test));
		assertArrayEquals(changed, read(helper));
	}

	@ParameterizedTest
	@ValueSource(strings = { "method", "constructor", "constructor-reference", "lambda", "implicit-default", "unchanged-caller",
			"javadoc-method", "javadoc-field", "markdown-method", "annotation-implicit",
			"annotation-default-marker", "annotation-default-named", "annotation-default-new", "anonymous-constructor", "enum-switch", "enum-switch-statement",
			"overload", "inherited-overload", "imported-overload", "inherited-default" })
	void introducedCallToNewDeclarationStaysInOneNativeBatch(String shape) throws Exception {
		String[] names= { "Api", "Caller", "Other" };
		String[] before= {
				"package probe; public class Api {}",
				"package probe; public class Caller { int get() { return 1; } }",
				"package probe; public class Other { int get() { return 1; } }"
		};
		String[] after= {
				"package probe; public class Api { static int value() { return 1; } }",
				"package probe; public class Caller { int get() { return Api.value(); } }",
				"package probe; public class Other { int get() { return 2; } }"
		};
		String required= "value";
		switch (shape) {
		case "constructor", "constructor-reference" -> {
			after[0]= "package probe; public class Api { Api(int i) { if (i < 0) throw new IllegalArgumentException(); } }";
			after[1]= shape.equals("constructor")
					? "package probe; public class Caller { Api get() { return new Api(1); } }"
					: "package probe; public class Caller { java.util.function.IntFunction<Api> get() { return Api::new; } }";
			required= "Api";
			before[2]= "package probe; public class Other { java.util.function.IntFunction<String> get() { return x -> null; } }";
			after[2]= before[2].replace("null", "String.valueOf(x)");
		}
		case "annotation-implicit" -> {
			before[0]= "package probe; public @interface Api {}";
			after[0]= "package probe; public @interface Api { /** @return the value */ int value(); }";
			before[1]= "package probe; @Api public class Caller {}";
			after[1]= "package probe; @Api(1) public class Caller {}";
		}
		case "annotation-default-marker", "annotation-default-named", "annotation-default-new" -> {
			boolean added= shape.equals("annotation-default-new");
			String stable= "int other() default 0; int spare() default 0;";
			before[0]= "package probe; public @interface Api { " + stable + (added ? "" : " /** @return the value */ int value() default 1;") + " }";
			after[0]= "package probe; public @interface Api { " + stable + " /** @return the value */ int value(); }";
			before[1]= "package probe; " + (shape.equals("annotation-default-named") ? "@Api(other=2)" : "@Api") + " public class Caller {}";
			after[1]= "package probe; public class Caller {}";
			required= "Api";
		}
		case "javadoc-method", "javadoc-field", "markdown-method" -> {
			boolean field= shape.equals("javadoc-field");
			String member= field ? "/** The value. */ public static final int VALUE = 7;"
					: "/** @return the value */ public int value() { return 7; }";
			String api= "package probe; public class Api { public int left() { return 0; } ";
			String contract= "package probe; public interface Caller { default int right() { return 0; } ";
			before[0]= api + member + " }";
			after[0]= api + "}";
			before[1]= contract + "}";
			after[1]= contract + (field ? member.replace("public static final ", "") : member.replace("public int", "default int")) + " }";
			String doc= shape.equals("markdown-method") ? "/// See [#value()].\n" : "/** See {@link #" + (field ? "VALUE" : "value()") + "}. */\n";
			before[2]= "package probe;\n" + doc + "public class Other extends Api implements Caller {}";
			after[2]= before[2];
			required= field ? "VALUE" : "value";
		}
		case "inherited-default" -> {
			String member= "/** @return the value */ default int value() { return 7; }";
			before[0]= "package probe; public interface Api { default int left() { return 1; } " + member + " }";
			after[0]= before[0].replace(member, "");
			before[1]= "package probe; public interface Caller { }";
			after[1]= before[1].replace("{ }", "{ " + member + " }");
			before[2]= "package probe; public class Other implements Api, Caller {}";
			after[2]= before[2];
		}
		case "overload", "inherited-overload", "imported-overload" -> {
			String original= "static int value(String s) { return s == null ? 0 : s.length(); }";
			String added= "static int value(Integer n) { return n.intValue(); }";
			String api= switch (shape) {
			case "overload" -> "class Api { " + original;
			case "inherited-overload" -> "class Api extends Other {";
			default -> "class Api { static int other() { return 1; }";
			};
			before[0]= "package probe; public " + api + " }";
			after[0]= "package probe; public " + api + " " + added + " }";
			boolean imported= shape.equals("imported-overload");
			String imports= imported ? "import static probe.Api.*; import static probe.Other.*; " : "";
			String call= imported ? "other() + value(null)" : "Api.value(null)";
			before[1]= "package probe; " + imports + "public class Caller { int read() { return " + call + "; } }";
			after[1]= before[1].replace("value(null)", "value(String.valueOf(7))");
			if (!shape.equals("overload")) before[2]= after[2]= "package probe; public class Other { " + original + " }";
		}
		case "enum-switch", "enum-switch-statement" -> {
			before[0]= "package probe; public enum Api { /** First. */ A, /** Second. */ B }";
			after[0]= before[0].replace("B }", "B, /** Third. */ C }");
			before[1]= "package probe; public class Caller { int read() { return switch (Other.current()) { case A -> 1; case B -> 2; }; } }";
			after[1]= before[1].replace("case B -> 2;", "case B -> 2; default -> 3;");
			if (shape.equals("enum-switch-statement")) {
				before[1]= "package probe; public class Caller { int read() { int n = 0; switch (Other.current()) { case A -> n=1; case B -> n=2; } return n; } }";
				after[1]= before[1].replace("case B -> n=2;", "case B -> n=2; default -> n=3;");
			}
			before[2]= "package probe; public class Other { static Api current() { return Api.A; } int get() { return 1; } }";
			after[2]= before[2].replace("return 1", "return 2");
			required= "reference to changed C";
		}
		case "anonymous-constructor" -> {
			before[0]= "package probe; public class Api { Api() {} }";
			after[0]= "package probe; public class Api { Api(int n) { if (n < 0) throw new IllegalArgumentException(); } }";
			before[1]= "package probe; public class Caller { Api call() { return new Api() {}; } }";
			after[1]= before[1].replace("new Api()", "new Api(1)");
			required= "Api";
		}
		case "implicit-default" -> {
			before[1]= "package probe; public class Caller { Api call() { return new Api(); } }";
			after[0]= "package probe; public class Api { Api(int n) { if (n < 0) throw new IllegalArgumentException(); } }";
			after[1]= before[1].replace("new Api()", "null");
			required= "Api";
		}
		case "unchanged-caller" -> {
			String moved= "/** @return the current value */ public static int value() { return 7; }";
			String first= "package probe; public class Api { public static int left() { return 0; } ";
			String second= "package probe; public class Caller { public static int right() { return 0; } ";
			before[0]= first + moved + " }";
			after[0]= first + "}";
			before[1]= second + "}";
			after[1]= second + moved + " }";
			before[2]= "package probe; import static probe.Api.*; import static probe.Caller.*; public class Other { int get() { return left() + right() + value(); } }";
			after[2]= before[2];
		}
		case "lambda" -> {
			before[0]= "package probe; public interface Api { int get(int n); }";
			after[0]= "package probe; public interface Api { int get(int a, int b); }";
			before[1]= "package probe; public class Caller { Api call() { return n -> n; } }";
			after[1]= "package probe; public class Caller { Api call() { return (a, b) -> a + b; } }";
			required= "get";
		}
		default -> { }
		}
		var projectOptions= JavaCore.create(project).getOptions(false);
		JavaCore.setComplianceOptions(shape.equals("markdown-method") ? JavaCore.VERSION_25 : JavaCore.VERSION_17, projectOptions);
		if (shape.startsWith("enum-switch")) projectOptions.put(JavaCore.COMPILER_PB_INCOMPLETE_ENUM_SWITCH, JavaCore.ERROR);
		if (shape.startsWith("javadoc") || shape.startsWith("markdown")) {
			projectOptions.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, JavaCore.ENABLED);
			projectOptions.put(JavaCore.COMPILER_PB_INVALID_JAVADOC, JavaCore.ERROR);
			projectOptions.put(JavaCore.COMPILER_PB_INVALID_JAVADOC_TAGS, JavaCore.ENABLED);
			projectOptions.put(JavaCore.COMPILER_PB_INVALID_JAVADOC_TAGS_VISIBILITY, JavaCore.PRIVATE);
		}
		JavaCore.create(project).setOptions(projectOptions);
		var inputs= new ArrayList<ProjectWideCodeCleanupApplication.SourceSnapshot>();
		var nativeChange= new CompositeChange("Introduce API");
		for (int i= 0; i < names.length; i++) {
			String path= "src/probe/" + names[i] + ".java";
			var file= createSource(path, before[i]);
			var unit= JavaCore.createCompilationUnitFrom(file);
			inputs.add(new ProjectWideCodeCleanupApplication.SourceSnapshot(unit, file, file.getLocation().toFile().toPath(), path, read(file)));
			if (before[i].equals(after[i])) continue;
			var edit= new CompilationUnitChange(names[i], unit);
			edit.setEdit(new ReplaceEdit(0, before[i].length(), after[i]));
			nativeChange.add(edit);
		}
		var diagnostics= CompilerDiagnosticAssertions.capture(inputs.stream()
				.map(ProjectWideCodeCleanupApplication.SourceSnapshot::unit).toArray(ICompilationUnit[]::new));
		if (shape.startsWith("javadoc") || shape.startsWith("markdown") || shape.startsWith("annotation-default") || shape.equals("anonymous-constructor")
				|| shape.startsWith("enum-switch") || shape.endsWith("overload") || shape.equals("inherited-default")) {
			int changeIndex= shape.equals("inherited-default") ? 1 : 0;
			var api= inputs.get(changeIndex);
			api.file().setContents(new ByteArrayInputStream(after[changeIndex].getBytes(StandardCharsets.UTF_8)), true, false, monitor);
			try {
				var failure= org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, diagnostics::assertNoNewDiagnostics);
				String witness= before[2].equals(after[2]) && !shape.endsWith("overload") ? "Other.java" : "Caller.java";
				assertTrue(failure.getMessage().contains(witness + " gained compiler diagnostic: ERROR"), failure.getMessage());
			} finally {
				api.file().setContents(new ByteArrayInputStream(api.before()), true, false, monitor);
			}
			diagnostics.assertRestored();
		}
		var plan= MigrationBatches.plan(JavaCore.create(project), nativeChange, inputs, List.of(), List.of(), 1, Map.of(), null, monitor);
		assertEquals(before[2].equals(after[2]) ? 1 : 2, plan.batches().size());
		var pair= plan.batches().getFirst();
		assertEquals(List.of("src/probe/Api.java", "src/probe/Caller.java"), pair.files());
		assertTrue(String.join("\n", pair.dependencies()).contains(required), pair.dependencies().toString());
		plan.select(pair.id(), pair.receipt(), true, monitor);
		diagnostics.assertNoNewDiagnostics();
		assertEquals(after[0], inputs.get(0).unit().getSource());
		assertEquals(after[1], inputs.get(1).unit().getSource());
		assertEquals(before[2], inputs.get(2).unit().getSource());
		RefactoringCore.getUndoManager().performUndo(null, monitor);
		for (var source : inputs) assertArrayEquals(source.before(), read(source.file()));
		diagnostics.assertRestored();
	}

	@ParameterizedTest
	@ValueSource(strings = { "source", "options", "classpath", "dirty-manifest", "descriptor-charset" })
	void rejectsChangesToTheWorkspaceContextAfterPlanning(String changed) throws Exception {
		var javaProject= JavaCore.create(project);
		var file= createSource("src/probe/Api.java", "package probe; public class Api {}");
		var manifest= createSource("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nBundle-SymbolicName: tests\r\n");
		var input= new ProjectWideCodeCleanupApplication.SourceSnapshot(JavaCore.createCompilationUnitFrom(file),
				file, file.getLocation().toFile().toPath(), file.getProjectRelativePath().toString(), read(file));
		var descriptor= new ProjectWideCodeCleanupApplication.ResourceSnapshot(manifest, manifest.getLocation().toFile().toPath(),
				manifest.getProjectRelativePath().toString(), read(manifest));
		var edit= new CompilationUnitChange("Api", input.unit());
		edit.setEdit(new ReplaceEdit(0, input.before().length, "package probe; public final class Api {}"));
		var plan= MigrationBatches.plan(javaProject, new CompositeChange("API", new org.eclipse.ltk.core.refactoring.Change[] { edit }),
				List.of(input), List.of(descriptor), List.of(), 1, Map.of(), null, monitor);
		var manager= org.eclipse.core.filebuffers.FileBuffers.getTextFileBufferManager();
		var location= org.eclipse.core.filebuffers.LocationKind.IFILE;
		try {
			switch (changed) {
			case "source" -> createSource("src/probe/Another.java", "package probe; class Another {}");
			case "options" -> javaProject.setOption(JavaCore.COMPILER_PB_UNUSED_IMPORT, JavaCore.ERROR);
			case "classpath" -> javaProject.setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(project.getFullPath().append("src")) }, monitor);
			case "descriptor-charset" -> manifest.setCharset(manifest.getCharset().equalsIgnoreCase("UTF-8") ? "ISO-8859-1" : "UTF-8", monitor);
			case "dirty-manifest" -> {
				manager.connect(manifest.getFullPath(), location, monitor);
				manager.getTextFileBuffer(manifest.getFullPath(), location).getDocument().set("unsaved");
			}
			}
			var batch= plan.batches().getFirst();
			org.junit.jupiter.api.Assertions.assertThrows(org.eclipse.core.runtime.CoreException.class,
					() -> plan.select(batch.id(), batch.receipt(), true, monitor));
			assertArrayEquals(input.before(), read(file));
			assertArrayEquals(descriptor.before(), read(manifest));
		} finally {
			if (changed.equals("dirty-manifest")) manager.disconnect(manifest.getFullPath(), location, monitor);
		}
	}

	private record TestHierarchy(IFile base, IFile child, IFile suite, IFile independent, IFile discovery,
			IFile descriptor, IClasspathEntry[] classpath, Path config) {
		CompilerDiagnosticAssertions.Snapshot diagnostics() {
			return CompilerDiagnosticAssertions.capture(Stream.of(base, child, suite, independent, discovery)
					.map(JavaCore::createCompilationUnitFrom).toArray(ICompilationUnit[]::new));
		}
	}

	private TestHierarchy createTestHierarchy() throws Exception {
		var classpath= junitClasspath();
		IFile base= createSource("src/sample/Base.java", """
				package sample;
				import org.junit.Test;
				public abstract class Base { @Test public void inherited() {} }
				""");
		IFile child= createSource("src/sample/Child.java", """
				package sample;
				import org.junit.Test;
				public class Child extends Base { @Test public void child() {} }
				""");
		IFile suite= createSource("src/sample/All.java", """
				package sample;
				@org.junit.runner.RunWith(org.junit.runners.Suite.class)
				@org.junit.runners.Suite.SuiteClasses(Child.class)
				public class All {}
				""");
		IFile independent= createSource("src/sample/Independent.java", """
				package sample;
				import org.junit.Test;
				public class Independent { @Test public void independent() {} }
				""");
		IFile discovery= createSource("src/check/Discovery.java", """
				package check;
				import org.junit.runner.Request;
				import org.junit.platform.engine.discovery.DiscoverySelectors;
				import org.junit.platform.launcher.TestIdentifier;
				import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
				import org.junit.platform.launcher.core.LauncherFactory;
				public class Discovery {
					public static long count(boolean migrated) throws NoSuchMethodException {
						var member = sample.Independent.class;
						long independent = member.getDeclaredMethod("independent").isAnnotationPresent(org.junit.Test.class)
								? Request.aClass(member).getRunner().testCount() : discovered(member, "junit-jupiter");
						return independent + (migrated ? discovered(sample.All.class, "junit-platform-suite")
								: Request.aClass(sample.All.class).getRunner().testCount());
					}
					private static long discovered(Class<?> test, String engine) {
						var request = LauncherDiscoveryRequestBuilder.request()
								.selectors(DiscoverySelectors.selectClass(test))
								.filters(org.junit.platform.launcher.EngineFilter.includeEngines(engine)).build();
						return LauncherFactory.create().discover(request).countTestIdentifiers(TestIdentifier::isTest);
					}
				}
				""");
		IFile descriptor= createSource("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nBundle-SymbolicName: tests\r\n");
		Path config= writeConfig("cleanup.junitcleanup=true\ncleanup.junitcleanup_4_test=true\ncleanup.junitcleanup_4_runwith=true\n");
		return new TestHierarchy(base, child, suite, independent, discovery, descriptor, classpath, config);
	}

	@Test
	void wholeProjectMigrationPreservesDiscoveredTestCount() throws Exception {
		var hierarchy= createTestHierarchy();
		var diagnostics= hierarchy.diagnostics();
		assertEquals(3, discoverTestMethods(false));
		assertEquals(0, new ProjectWideCodeCleanupApplication().start(new TestApplicationContext(
				"--project", project.getName(), "--config", hierarchy.config().toString(), "--mode", "apply",
				"--report", temporaryDirectory.resolve("suite-apply.json").toString())));
		assertArrayEquals(hierarchy.classpath(), JavaCore.create(project).getRawClasspath());
		assertTrue(new String(read(hierarchy.descriptor()), StandardCharsets.UTF_8).contains("org.junit.platform.suite.api"));
		diagnostics.assertNoNewDiagnostics();
		assertEquals(3, discoverTestMethods(true));
	}

	private IClasspathEntry[] junitClasspath() throws Exception {
		var javaProject= JavaCore.create(project);
		var options= javaProject.getOptions(false);
		JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
		javaProject.setOptions(options);
		var initialClasspath= new IClasspathEntry[] {
				JavaCore.newSourceEntry(project.getFullPath().append("src")),
				JavaCore.newContainerEntry(new org.eclipse.core.runtime.Path("org.eclipse.jdt.launching.JRE_CONTAINER")),
				JavaCore.newContainerEntry(new org.eclipse.core.runtime.Path("org.eclipse.jdt.junit.JUNIT_CONTAINER/4")),
				JavaCore.newContainerEntry(new org.eclipse.core.runtime.Path("org.eclipse.jdt.junit.JUNIT_CONTAINER/5"))
		};
		javaProject.setRawClasspath(initialClasspath, project.getFullPath().append("bin"), monitor);
		return javaProject.getRawClasspath();
	}

	private long discoverTestMethods(boolean migratedHierarchy) throws Exception {
		project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
		var urls= new ArrayList<URL>();
		urls.add(project.getFolder("bin").getLocation().toFile().toURI().toURL());
		for (IClasspathEntry entry : JavaCore.create(project).getResolvedClasspath(true)) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) urls.add(entry.getPath().toFile().toURI().toURL());
		}
		// The fixture uses its resolved JDT libraries, independent of Tycho's provider version.
		var thread= Thread.currentThread();
		var previous= thread.getContextClassLoader();
		try (var loader= new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
			thread.setContextClassLoader(loader);
			return (Long) loader.loadClass("check.Discovery").getMethod("count", boolean.class).invoke(null, migratedHierarchy);
		} finally {
			thread.setContextClassLoader(previous);
		}
	}

	private void assertPatchRoundTrip(IFile source, byte[] original, Path patch) throws Exception {
		byte[] cleaned= read(source);
		try (var input= new ByteArrayInputStream(original)) {
			source.setContents(input, true, false, monitor);
		}
		runGitApply(patch, true);
		assertArrayEquals(original, read(source), "git apply --check must be read-only"); //$NON-NLS-1$
		runGitApply(patch, false);
		source.refreshLocal(IResource.DEPTH_ZERO, monitor);
		assertArrayEquals(cleaned, read(source), "patch must reproduce the actual CLI cleanup bytes"); //$NON-NLS-1$
	}

	@ParameterizedTest
	@ValueSource(strings = { "check", "apply" }) //$NON-NLS-1$ //$NON-NLS-2$
	void projectWideHonorsConfiguredMethodReuseMinimum(String mode) throws Exception {
		IFile source= createSource("src/test/Repeated.java", //$NON-NLS-1$
				"""
				package test;
				public class Repeated {
					public void first(String value) {
						String text = value.trim();
						text = text.toLowerCase();
						System.out.println(text);
					}
					public void second(String input) {
						String text = input.trim();
						text = text.toLowerCase();
						System.out.println(text);
					}
				}
				"""); //$NON-NLS-1$
		byte[] original= read(source);
		Path patch= temporaryDirectory.resolve("minimum.patch"); //$NON-NLS-1$
		Path report= temporaryDirectory.resolve("minimum-report.json"); //$NON-NLS-1$
		Path config= writeConfig("""
				cleanup.method_reuse=true
				cleanup.method_reuse.minimum_statements=3
				"""); //$NON-NLS-1$
		var application= new ProjectWideCodeCleanupApplication();

		Object candidateResult= application.start(new TestApplicationContext(
				"--project", project.getName(), "--config", config.toString(), "--report", report.toString(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"--patch", patch.toString(), "--mode", "check")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals(2, candidateResult, Files.readString(report));
		assertTrue(Files.readString(patch).contains("extractedSequence")); //$NON-NLS-1$
		assertArrayEquals(original, read(source));

		Files.writeString(config, """
				cleanup.method_reuse=true
				cleanup.method_reuse.minimum_statements=4
				""", StandardCharsets.UTF_8); //$NON-NLS-1$
		Object result= application.start(new TestApplicationContext(
				"--project", project.getName(), "--config", config.toString(), "--report", report.toString(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"--patch", patch.toString(), "--mode", mode)); //$NON-NLS-1$ //$NON-NLS-2$

		assertEquals(0, result, Files.readString(report));
		assertArrayEquals(original, read(source), "Three statements must not meet a four-statement minimum"); //$NON-NLS-1$
		assertArrayEquals(new byte[0], Files.readAllBytes(patch));
	}

	private void runGitApply(Path patch, boolean checkOnly) throws Exception {
		runGitApply(patch, checkOnly, project.getLocation().toFile().toPath());
	}

	private void runGitApply(Path patch, boolean checkOnly, Path root) throws Exception {
		// Verify patch bytes, independently of the runner's checkout conversion policy.
		ProcessBuilder builder= checkOnly
				? new ProcessBuilder("git", "-c", "core.autocrlf=false", "-c", "core.eol=lf", "apply", "--check", "--", patch.toString()) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
				: new ProcessBuilder("git", "-c", "core.autocrlf=false", "-c", "core.eol=lf", "apply", "--", patch.toString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
		Path log= temporaryDirectory.resolve(checkOnly ? "git-check.log" : "git-apply.log"); //$NON-NLS-1$ //$NON-NLS-2$
		builder.directory(root.toFile());
		builder.redirectErrorStream(true);
		builder.redirectOutput(log.toFile());
		Process process= builder.start();
		try {
			assertTrue(process.waitFor(30, TimeUnit.SECONDS), "git apply did not terminate"); //$NON-NLS-1$
			assertEquals(0, process.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
		} finally {
			if (process.isAlive()) {
				process.destroyForcibly();
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "META-INF/MANIFEST.MF", "pom.xml" })
	void platformSuiteDependencySharesTheJavaCheckAndApplyTransaction(String descriptorPath) throws Exception {
		IFolder sourceRoot= project.getFolder("src/test/java");
		createFolder(sourceRoot);
		var javaProject= JavaCore.create(project);
		javaProject.setRawClasspath(new IClasspathEntry[] {
				JavaCore.newSourceEntry(sourceRoot.getFullPath()),
				JavaCore.newContainerEntry(new org.eclipse.core.runtime.Path("org.eclipse.jdt.launching.JRE_CONTAINER")),
				JavaCore.newContainerEntry(new org.eclipse.core.runtime.Path("org.eclipse.jdt.junit.JUNIT_CONTAINER/4"))
		}, project.getFullPath().append("bin"), monitor);
		IFile source= createSource("src/test/java/probe/AllTests.java", """
				package probe;
				import org.junit.runner.RunWith;
				import org.junit.runners.Suite;
				@RunWith(Suite.class)
				@Suite.SuiteClasses({})
				public class AllTests {}
				""");
		boolean maven= "pom.xml".equals(descriptorPath);
		IFile descriptor= createSource(descriptorPath, maven ? PlatformSuiteDependenciesTest.MODEL
				: "Manifest-Version: 1.0\r\nBundle-SymbolicName: tests\r\n");
		String expectedDependency= maven ? "junit-platform-suite" : "org.junit.platform.suite.api";
		byte[] originalSource= read(source);
		byte[] originalDescriptor= read(descriptor);
		Path patch= temporaryDirectory.resolve("suite.patch");
		Path report= temporaryDirectory.resolve("suite.json");
		Path config= writeConfig("cleanup.junitcleanup=true\ncleanup.junitcleanup_4_runwith=true\ncleanup.junitcleanup_4_suite=true\n");
		String[] args= { "--project", project.getName(), "--config", config.toString(), "--report", report.toString(),
				"--patch", patch.toString(), "--mode", "check" };
		var app= new ProjectWideCodeCleanupApplication();
		assertEquals(Integer.valueOf(2), app.start(new TestApplicationContext(args)));
		assertArrayEquals(originalSource, read(source));
		assertArrayEquals(originalDescriptor, read(descriptor));
		String predicted= Files.readString(patch);
		assertTrue(predicted.contains(expectedDependency), predicted);
		assertTrue(predicted.contains(descriptorPath), predicted);
		args[args.length - 1]= "apply";
		assertEquals(Integer.valueOf(0), app.start(new TestApplicationContext(args)));
		assertEquals(predicted, Files.readString(patch));
		assertTrue(new String(read(descriptor), StandardCharsets.UTF_8).contains(expectedDependency));
		assertTrue(new String(read(source), StandardCharsets.UTF_8).contains("org.junit.platform.suite.api.Suite"));
		if (maven) {
			// A fresh apply on an unmanaged POM must roll back the Java change too.
			source.setContents(new ByteArrayInputStream(originalSource), true, false, monitor);
			String unmanaged= PlatformSuiteDependenciesTest.MODEL.replace("junit-bom", "other-bom");
			descriptor.setContents(new ByteArrayInputStream(unmanaged.getBytes(StandardCharsets.UTF_8)), true, false, monitor);
			assertEquals(Integer.valueOf(1), app.start(new TestApplicationContext(args)));
			assertArrayEquals(originalSource, read(source));
			assertEquals(unmanaged, new String(read(descriptor), StandardCharsets.UTF_8));
			assertTrue(Files.readString(report).contains("Cannot plan the JUnit Platform Suite dependency"));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "closed", "batched-retirement", "generated", "custom-engine", "pde-consumer", "resource", "legacy-test", "reflection", "stale-binary", "mixed-suite" })
	void retiresVintageOnlyForAClosedStandaloneTestDeployment(String scope) throws Exception {
		IFolder root= project.getFolder("src/test/java");
		createFolder(root);
		JavaCore.create(project).setRawClasspath(new IClasspathEntry[] {
				JavaCore.newSourceEntry(root.getFullPath()),
				JavaCore.newContainerEntry(new org.eclipse.core.runtime.Path("org.eclipse.jdt.launching.JRE_CONTAINER")),
				JavaCore.newContainerEntry(new org.eclipse.core.runtime.Path("org.eclipse.jdt.junit.JUNIT_CONTAINER/5"))
		}, project.getFullPath().append("bin"), monitor);
		IFile suite= createSource("src/test/java/probe/AllTests.java", """
				package probe;
				import org.junit.runner.RunWith;
				import org.junit.runners.Suite;
				@RunWith(Suite.class)
				@Suite.SuiteClasses({ ExampleTest.class })
				public class AllTests {}
				""");
		String member= """
				package probe;
				import org.junit.Test;
				public class ExampleTest {
					@Test public void adds() { org.junit.jupiter.api.Assertions.assertEquals(4, 4); }
				}
				""";
		createSource("src/test/java/probe/ExampleTest.java",
				"mixed-suite".equals(scope) ? member.replace("org.junit.Test", "org.junit.jupiter.api.Test") : member);
		String model= PlatformSuiteDependenciesTest.MODEL.replace(
				"<artifactId>junit-vintage-engine</artifactId></dependency>",
				"<artifactId>junit-vintage-engine</artifactId><scope>test</scope></dependency>")
				.replace("    <!-- Retain", """
					    <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
					    <dependency><groupId>junit</groupId><artifactId>junit</artifactId><version>4.13.2</version><scope>test</scope></dependency>
					""" + "    <!-- Retain");
		switch (scope) {
		case "batched-retirement" -> createSource("src/test/java/probe/OtherTest.java", member.replace("ExampleTest", "OtherTest"));
		case "legacy-test" -> createSource("src/test/java/probe/LegacyTest.java",
				"package probe; public class LegacyTest extends junit.framework.TestCase { public void testOld() {} }");
		case "reflection" -> createSource("src/test/java/probe/ReflectiveTest.java",
				"package probe; public class ReflectiveTest { @org.junit.jupiter.api.Test void run() throws Exception { Class.forName(\"junit.framework.TestCase\"); } }");
		case "stale-binary" -> {
			// This fixture deliberately leaves an orphan in JDT's output. A concurrent
			// auto-build may delete it before the deployment check observes it.
			var description= project.getDescription();
			description.setBuildSpec(new org.eclipse.core.resources.ICommand[0]);
			project.setDescription(description, monitor);
			org.eclipse.core.runtime.jobs.Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, monitor);
			createSource("bin/probe/OldTest.class", "stale output");
		}
		case "generated" -> createSource("target/generated-test-sources/probe/GeneratedTest.java",
				"package probe; public class GeneratedTest extends junit.framework.TestCase {}");
		case "custom-engine" -> model= model.replace("  </dependencies>", """
				      <dependency><groupId>example</groupId><artifactId>external-tests</artifactId><scope>test</scope></dependency>
				    </dependencies>""");
		case "pde-consumer" -> createSource("feature.xml", "<feature><plugin id=\"legacy.tests\"/></feature>");
		case "resource" -> createSource("src/test/resources/junit-platform.properties", "junit.platform.discovery.includeEngines=vintage");
		default -> { }
		}
		IFile pom= createSource("pom.xml", model);
		byte[] beforePom= read(pom);
		byte[] beforeSuite= read(suite);
		Path config= writeConfig("cleanup.junitcleanup=true\ncleanup.junitcleanup_4_runwith=true\ncleanup.junitcleanup_4_suite=true\ncleanup.junitcleanup_4_test=true\n");
		Path patch= temporaryDirectory.resolve("retire.patch");
		Path report= temporaryDirectory.resolve("retire.json");
		String[] args= { "--project", project.getName(), "--config", config.toString(), "--report", report.toString(),
				"--patch", patch.toString(), "--mode", "check" };
		var app= new ProjectWideCodeCleanupApplication();
		if (scope.equals("batched-retirement")) {
			var inputs= ProjectWideCodeCleanupApplication.snapshots(JavaCore.create(project));
			var descriptor= new ProjectWideCodeCleanupApplication.ResourceSnapshot(pom, pom.getLocation().toFile().toPath(), "pom.xml", beforePom);
			var batchArgs= new ArrayList<>(List.of(args));
			batchArgs.addAll(List.of("--batch-max-files", "1"));
			assertEquals(2, app.start(new TestApplicationContext(batchArgs.toArray(String[]::new))));
			var batches= JsonParser.parseString(Files.readString(report)).getAsJsonObject().getAsJsonArray("batches");
			assertEquals(1, batches.size(), batches.toString());
			var batch= batches.get(0).getAsJsonObject();
			assertEquals(3, batch.getAsJsonArray("files").size());
			assertTrue(batch.getAsJsonArray("dependencies").toString().contains("Vintage"));
			batchArgs.set(batchArgs.indexOf("check"), "apply");
			batchArgs.addAll(List.of("--batch", batch.get("id").getAsString(), "--batch-receipt", batch.get("receipt").getAsString()));
			assertEquals(0, app.start(new TestApplicationContext(batchArgs.toArray(String[]::new))));
			assertTrue(!Files.readString(pom.getLocation().toFile().toPath()).contains("<artifactId>junit-vintage-engine</artifactId>"));
			var exactPreview= new CompositeChange("Reviewed standalone deployment");
			for (var source : inputs) {
				var edit= new CompilationUnitChange(source.relativePath(), source.unit());
				edit.setEdit(new ReplaceEdit(0, new String(source.before(), StandardCharsets.UTF_8).length(), source.unit().getSource()));
				exactPreview.add(edit);
			}
			RefactoringCore.getUndoManager().performUndo(null, monitor);
			var planned= MigrationBatches.plan(JavaCore.create(project), exactPreview, inputs, List.of(descriptor), List.of(), 1, Map.of(), null, monitor);
			assertEquals(1, planned.batches().size());
			var reviewed= planned.batches().getFirst();
			var newResource= createSource("src/test/resources/junit-platform.properties", "junit.platform.discovery.includeEngines=vintage");
			org.junit.jupiter.api.Assertions.assertThrows(org.eclipse.core.runtime.CoreException.class,
					() -> planned.select(reviewed.id(), reviewed.receipt(), true, monitor));
			assertTrue(newResource.exists());
			assertArrayEquals(beforeSuite, read(suite));
			assertArrayEquals(beforePom, read(pom));
			return;
		}
		assertEquals(2, app.start(new TestApplicationContext(args)));
		assertArrayEquals(beforePom, read(pom));
		assertArrayEquals(beforeSuite, read(suite));
		String predicted= Files.readString(patch);
		args[args.length - 1]= "apply";
		assertEquals(0, app.start(new TestApplicationContext(args)));
		assertEquals(predicted, Files.readString(patch));
		String after= Files.readString(pom.getLocation().toFile().toPath());
		assertTrue(after.contains("junit-platform-suite"));
		assertEquals(!scope.equals("closed"), after.contains("<artifactId>junit-vintage-engine</artifactId>"), after);
		assertEquals(!scope.equals("closed"), after.contains("<artifactId>junit</artifactId>"), after);
		args[args.length - 1]= "check";
		assertEquals(0, app.start(new TestApplicationContext(args)));
		assertArrayEquals(new byte[0], Files.readAllBytes(patch));
	}

	private Path writeConfig(String content) throws Exception {
		return Files.writeString(temporaryDirectory.resolve("cleanup.properties"), content, StandardCharsets.ISO_8859_1); //$NON-NLS-1$
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void includesExplicitPdeTestDeploymentInTheSuiteTransaction(boolean sibling) throws Exception {
		if (sibling) {
			deleteProject();
			createProject(temporaryDirectory.resolve("tests-dir"));
		}
		IFolder sourceRoot= project.getFolder("src");
		var javaProject= JavaCore.create(project);
		javaProject.setRawClasspath(new IClasspathEntry[] {
				JavaCore.newSourceEntry(sourceRoot.getFullPath()),
				JavaCore.newContainerEntry(new org.eclipse.core.runtime.Path("org.eclipse.jdt.launching.JRE_CONTAINER")),
				JavaCore.newContainerEntry(new org.eclipse.core.runtime.Path("org.eclipse.jdt.junit.JUNIT_CONTAINER/5"))
		}, project.getFullPath().append("bin"), monitor);
		IFile suite= createSource("src/probe/AllTests.java", """
				package probe;
				@org.junit.runner.RunWith(org.junit.runners.Suite.class)
				@org.junit.runners.Suite.SuiteClasses(ExampleTest.class)
				public class AllTests {}
				""");
		IFile member= createSource("src/probe/ExampleTest.java", """
				package probe;
				import org.junit.Test;
				public class ExampleTest {
					@Test public void adds() { org.junit.jupiter.api.Assertions.assertEquals(4, 4); }
				}
				""");
		IFile manifest= createSource("META-INF/MANIFEST.MF", """
				Manifest-Version: 1.0
				Bundle-ManifestVersion: 2
				Bundle-SymbolicName: probe.tests
				Bundle-Version: 1.0.0
				Bundle-RequiredExecutionEnvironment: JavaSE-17
				Import-Package: org.junit,org.junit.runner,org.junit.runners,org.junit.jupiter.api

				""");
		IFile build= createSource("build.properties", "source.. = src/\r\nbin.includes = META-INF/,.\r\nadditional.bundles = junit-vintage-engine\r\n");
		IProject owner= project;
		if (sibling) {
			deploymentProject= project.getWorkspace().getRoot().getProject(project.getName() + "-deployment");
			var description= project.getWorkspace().newProjectDescription(deploymentProject.getName());
			description.setLocationURI(temporaryDirectory.resolve("feature-dir").toUri());
			deploymentProject.create(description, monitor);
			deploymentProject.open(monitor);
			owner= deploymentProject;
		}
		IFile feature= createSource(owner, "deployment/feature.xml", """
				<feature id="probe.test.feature" version="1.0.0">
				  <plugin id="probe.tests" version="0.0.0"/>
				  <plugin id="junit-vintage-engine" version="0.0.0"/>
				</feature>
				""");
		IFile target= createSource(owner, "deployment/test.target", """
				<target name="JUnit tests"><locations><location type="InstallableUnit" includeSource="false" includeMode="planner">
				  <repository location="https://download.eclipse.org/releases/2026-09/"/>
				  <unit id="org.eclipse.equinox.launcher" version="0.0.0"/>
				  <unit id="org.eclipse.equinox.simpleconfigurator" version="0.0.0"/>
				  <unit id="org.eclipse.core.runtime" version="0.0.0"/>
				  <unit id="junit-jupiter-engine" version="5.14.4"/>
				  <unit id="junit-platform-launcher" version="1.14.4"/>
				  <unit id="junit-vintage-engine" version="0.0.0"/>
				</location></locations></target>
				""");
		var tracked= java.util.List.of(suite, member, manifest, build, feature, target);
		Map<IFile, byte[]> original= new HashMap<>();
		for (IFile file : tracked) {
			original.put(file, read(file));
		}
		Path patch= temporaryDirectory.resolve("pde.patch");
		Path report= temporaryDirectory.resolve("pde.json");
		Path config= writeConfig("cleanup.junitcleanup=true\ncleanup.junitcleanup_4_runwith=true\ncleanup.junitcleanup_4_suite=true\ncleanup.junitcleanup_4_test=true\n");
		String[] args= { "--project", project.getName(), "--config", config.toString(), "--report", report.toString(),
				"--patch", patch.toString(), "--pde-deployment", feature.getFullPath().toPortableString(),
				"--pde-deployment", target.getFullPath().toPortableString(), "--mode", "check" };
		var app= new ProjectWideCodeCleanupApplication();
		assertEquals(2, app.start(new TestApplicationContext(args)));
		String predicted= Files.readString(patch);
		for (IFile file : tracked) {
			assertArrayEquals(original.get(file), read(file));
			String path= (sibling ? (project.equals(file.getProject()) ? "tests-dir/" : "feature-dir/") : "") + file.getProjectRelativePath().toPortableString();
			assertTrue(predicted.contains("--- a/" + path), predicted);
		}
		if (sibling) {
			var batchArgs= new ArrayList<>(List.of(args));
			batchArgs.addAll(List.of("--batch-max-files", "1"));
			assertEquals(2, app.start(new TestApplicationContext(batchArgs.toArray(String[]::new))));
			runGitApply(patch, true, temporaryDirectory);
		}
		args[args.length - 1]= "apply";
		assertEquals(0, app.start(new TestApplicationContext(args)));
		assertEquals(predicted, Files.readString(patch));
		if (!sibling) {
			PdeDeploymentReplay.export(original);
		}
		for (IFile file : java.util.List.of(feature, target, build)) {
			String content= new String(read(file), StandardCharsets.UTF_8);
			assertTrue(content.contains("junit-platform-suite-api"), content);
			assertTrue(content.contains("junit-platform-suite-engine"), content);
			assertTrue(content.contains("junit-vintage-engine"), content);
		}
		assertTrue(new String(read(target), StandardCharsets.UTF_8)
				.contains("id=\"junit-platform-suite-engine\" version=\"1.14.4\""));
		args[args.length - 1]= "check";
		assertEquals(0, app.start(new TestApplicationContext(args)));
		assertEquals("", Files.readString(patch));

		// An explicitly pinned target cannot authorize adding unconstrained suite units.
		for (IFile file : tracked) {
			file.setContents(new ByteArrayInputStream(original.get(file)), true, false, monitor);
		}
		String pinned= new String(original.get(target), StandardCharsets.UTF_8)
				.replace("id=\"junit-vintage-engine\" version=\"0.0.0\"", "id=\"junit-vintage-engine\" version=\"5.14.4\"");
		target.setContents(new ByteArrayInputStream(pinned.getBytes(StandardCharsets.UTF_8)), true, false, monitor);
		args[args.length - 1]= "apply";
		assertEquals(1, app.start(new TestApplicationContext(args)));
		assertTrue(Files.readString(report).contains("unpinned Vintage unit"));
		for (IFile file : tracked) {
			assertArrayEquals(file.equals(target) ? pinned.getBytes(StandardCharsets.UTF_8) : original.get(file), read(file));
		}

		String unpinned= new String(original.get(target), StandardCharsets.UTF_8)
				.replace("id=\"junit-platform-launcher\" version=\"1.14.4\"", "id=\"junit-platform-launcher\" version=\"0.0.0\"");
		target.setContents(new ByteArrayInputStream(unpinned.getBytes(StandardCharsets.UTF_8)), true, false, monitor);
		assertEquals(1, app.start(new TestApplicationContext(args)));
		assertTrue(Files.readString(report).contains("launcher release version"));
		for (IFile file : tracked) {
			assertArrayEquals(file.equals(target) ? unpinned.getBytes(StandardCharsets.UTF_8) : original.get(file), read(file));
		}
	}

	private IFile createSource(String relativePath, String source) throws Exception {
		return createSource(project, relativePath, source);
	}

	private IFile createSource(IProject owner, String relativePath, String source) throws Exception {
		IFile file= owner.getFile(relativePath);
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
