/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.internal.ui.fix.SimplifyPlatformStatusCleanUpCore;

/**
 * Actual bound cleanup, diagnostics, preview/apply/undo and runtime, for two
 * target APIs.
 */
@ResourceLock(Resources.SYSTEM_OUT)
class StatusDiagnosticPreservationTest {
	@TempDir
	Path temporary;
	private IProject project;

	static String status(boolean available) {
		return "package org.eclipse.core.runtime; public class Status implements IStatus {"
				+ "public static int calls; public static String log=\"\"; "
				+ "public Status(int s,String id,int c,String m,Throwable t){calls++;log+=m;}"
				+ "public Status(int s,Class<?> id,int c,String m,Throwable t){calls++;log+=m;}"
				+ "public Status(int s,String id,String m,Throwable t){this(s,id,0,m,t);}"
				+ (available ? "public Status(int s,Class<?> id,String m,Throwable t){this(s,id,0,m,t);}" : "")
				+ "public static IStatus error(String m){return new Status(4,\"test.bundle\",0,m,null);}"
				+ "public static IStatus error(String m,Throwable t){return new Status(4,\"test.bundle\",0,m,t);}"
				+ "public static IStatus warning(String m){return error(m);}"
				+ "public static IStatus warning(String m,Throwable t){return error(m,t);}"
				+ "public static IStatus info(String m){return error(m);} }";
	}

	static final String ISTATUS = "package org.eclipse.core.runtime; public interface IStatus {int OK=0,ERROR=4,WARNING=2,INFO=1;}";
	static final String MULTI = "package org.eclipse.core.runtime; public class MultiStatus extends Status {public MultiStatus(String id,int c,String m,Throwable t){super(0,id,c,m,t);}}";

	record Scenario(String name, boolean available, String members, boolean change, String absent, String present) {
		@Override
		public String toString() {
			return name;
		}
	}

	static final String CALL = "new Status(IStatus.ERROR,\"other.bundle\",CODE,\"value\",null)";

	static List<Scenario> cases() {
		return List.of(
				new Scenario("private", true,
						"private static final int CODE=0; public static Object run(){return "
								+ CALL + ";}",
						true, "intCODE=", "newStatus(IStatus.ERROR,\"other.bundle\",\"value\",null)"),
				new Scenario("public", true,
						"public static final int CODE=0; public static Object run(){return " + CALL + ";}", true, "",
						"intCODE=0"),
				new Scenario("retained", true,
						"private static final int CODE=0; public static int code(){return CODE;} public static Object run(){return "
								+ CALL + ";}",
						true, "", "intCODE=0"),
				new Scenario("two-uses", true,
						"private static final int CODE=0; public static Object run(){" + CALL + ";return " + CALL
								+ ";}",
						true, "intCODE=", ""),
				new Scenario("cascade", true,
						"private static final int ROOT=0; private static final int CODE=ROOT; public static Object run(){return "
								+ CALL + ";}",
						true, "intROOT=", ""),
				new Scenario("siblings", true,
						"private static final int KEEP=5,CODE=0; public static int keep(){return KEEP;} public static Object run(){return "
								+ CALL + ";}",
						true, "CODE=", "KEEP=5"),
				new Scenario("unrelated", true,
						"private static final int UNUSED=9; private static final int CODE=0; public static Object run(){return "
								+ CALL + ";}",
						true, "CODE=", "UNUSED=9"),
				new Scenario("multi", true,
						"private static final int CODE=0; public static Object run(){return new MultiStatus(\"id\",CODE,\"m\",null);}",
						true, "intCODE=", "IStatus.OK"),
				new Scenario("factory-unused", true,
						"static String message(){return \"value\";} public static Object run(){ IStatus unused=new Status(IStatus.ERROR,\"test.bundle\",IStatus.OK,message(),null); return Status.log;}",
						true, "IStatusunused=", "Status.error(message())"),
				new Scenario("factory-used", true,
						"public static Object run(){ IStatus used=new Status(IStatus.ERROR,\"test.bundle\",IStatus.OK,\"value\",null); return used;}",
						true, "newStatus(", "IStatusused="),
				new Scenario("class-missing", false,
						"public static Object run(){return new Status(IStatus.ERROR,Example.class,IStatus.OK,\"value\",null);}",
						false, "", ""),
				new Scenario("class-present", true,
						"public static Status run(){return new Status(IStatus.ERROR,Example.class,IStatus.OK,\"value\",null);}",
						true, "IStatus.OK,", "newStatus(IStatus.ERROR,Example.class,\"value\",null)"),
				new Scenario("nonzero", true,
						"private static final int CODE=42; public static Object run(){return " + CALL + ";}", false, "",
						""),
				new Scenario("factory-constant", true,
						"private static final String ID=\"test.bundle\"; public static Object run(){return new Status(IStatus.ERROR,ID,IStatus.OK,\"value\",null);}",
						true, "StringID=", "Status.error(\"value\")"),
				new Scenario("factory-multi-local", true,
						"public static Object run(){ IStatus unused=new Status(IStatus.ERROR,\"test.bundle\",IStatus.OK,\"first\",null), used=new Status(IStatus.ERROR,\"other.bundle\",IStatus.OK,\"second\",null); return used;}",
						true, "", "\"second\""),
				new Scenario("both-siblings", true,
						"private static final int CODE=0,SECOND=0; public static Object run(){" + CALL + ";return "
								+ CALL.replace("CODE", "SECOND") + ";}",
						true, "finalint", ""),
				new Scenario("doc-reference", true,
						"private static final int CODE=0; /** @see #CODE */ public static Object run(){return " + CALL
								+ ";}",
						false, "", "intCODE=0"),
				new Scenario("constant-name-shadow", true,
						"private static final int CODE=0; static class Inner { static int CODE=12; } public static Object run(){System.out.print(Inner.CODE);return "
								+ CALL + ";}",
						true, "finalintCODE=", "staticintCODE=12"));
	}

	static Stream<Scenario> scenarios() {
		return cases().stream();
	}

	@AfterEach
	void tearDown() throws Exception {
		if (project != null && project.exists()) {
			project.delete(true, true, null);
		}
	}

	@ParameterizedTest
	@MethodSource("scenarios")
	void preservesDiagnosticsAndEvaluation(Scenario scenario) throws Exception {
		project = ResourcesPlugin.getWorkspace().getRoot().getProject("StatusDiagnostics" + System.nanoTime());
		project.create(null);
		project.open(null);
		var description = project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, null);
		var javaProject = JavaCore.create(project);
		var folder = project.getFolder("src");
		folder.create(true, true, null);
		javaProject.setRawClasspath(
				new IClasspathEntry[] { JavaCore.newSourceEntry(folder.getFullPath()),
						JavaCore.newContainerEntry(
								new org.eclipse.core.runtime.Path("org.eclipse.jdt.launching.JRE_CONTAINER")) },
				project.getFullPath().append("bin"), null);
		var options = javaProject.getOptions(false);
		JavaCore.setComplianceOptions(JavaCore.VERSION_9, options);
		options.put(JavaCore.COMPILER_PB_UNUSED_PRIVATE_MEMBER, JavaCore.WARNING);
		options.put(JavaCore.COMPILER_PB_UNUSED_LOCAL, JavaCore.WARNING);
		javaProject.setOptions(options);
		var sourceRoot = javaProject.getPackageFragmentRoot(folder);
		// Model exactly the API of the target project, not the installed host bundle.
		var api = sourceRoot.createPackageFragment("org.eclipse.core.runtime", false, null);
		api.createCompilationUnit("IStatus.java", ISTATUS, false, null);
		api.createCompilationUnit("Status.java", status(scenario.available()), false, null);
		api.createCompilationUnit("MultiStatus.java", MULTI, false, null);
		if (!scenario.name().startsWith("class-")) {
			var meta = project.getFolder("META-INF");
			meta.create(true, true, null);
			try (var input = new ByteArrayInputStream(
					("Manifest-Version: 1.0\n" + "Bundle-SymbolicName: test.bundle\n\n")
							.getBytes(StandardCharsets.UTF_8))) {
				meta.getFile("MANIFEST.MF").create(input, true, null);
			}
		}
		String before = "package test1; import org.eclipse.core.runtime.IStatus; "
				+ "import org.eclipse.core.runtime.Status; import org.eclipse.core.runtime.MultiStatus; "
				+ "public class Example {" + scenario.members() + "}";
		ICompilationUnit unit = sourceRoot.createPackageFragment("test1", false, null)
				.createCompilationUnit("Example.java", before, false, null);
		var root = parse(unit);
		assertTrue(Arrays.stream(root.getProblems()).noneMatch(problem -> problem.isError()),
				Arrays.toString(root.getProblems()));
		var baseline = diagnostics(root);
		var fix = fix(unit, root);
		assertEquals(scenario.change(), fix != null, "target API must control positive conversion availability");
		if (fix == null) {
			assertEquals(before, unit.getSource());
			return;
		}
		String after;
		var change = fix.createChange(null);
		try {
			String preview = change.getPreviewContent(null);
			assertEquals(preview, change.getPreviewContent(null));
			assertEquals(before, unit.getSource());
			var undo = change.perform(null);
			assertNotNull(undo);
			try {
				after = unit.getSource();
				assertNotEquals(before, after);
				assertEquals(preview, after);
				diagnostics(parse(unit)).forEach(
						(key, count) -> assertTrue(count <= baseline.getOrDefault(key, 0L), key + "\n" + after));
				String compact = after.replaceAll("\\s+", "");
				assertTrue(scenario.absent().isEmpty() || !compact.contains(scenario.absent()), after);
				assertTrue(compact.contains(scenario.present()), after);
				assertNull(fix(unit, parse(unit)), "second cleanup must be a no-op");
			} finally {
				var redo = undo.perform(null);
				if (redo != null) {
					redo.dispose();
				}
				undo.dispose();
			}
			assertEquals(before, unit.getSource());
			assertEquals(baseline, diagnostics(parse(unit)));
		} finally {
			change.dispose();
		}
		assertEquals(execute(before, scenario.available(), temporary.resolve("before")),
				execute(after, scenario.available(), temporary.resolve("after")));
	}

	private static ICleanUpFix fix(ICompilationUnit unit, CompilationUnit root) throws Exception {
		return new SimplifyPlatformStatusCleanUpCore(Map.of("cleanup.simplify_status_creation", "true"))
				.createFix(new CleanUpContext(unit, root));
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}

	private static Map<String, Long> diagnostics(CompilationUnit root) {
		return Arrays
				.stream(root.getProblems()).filter(
						problem -> problem.isError() || problem.isWarning())
				.collect(Collectors.groupingBy(problem -> problem.isError() + ":" + problem.getID() + ":"
						+ Arrays.toString(problem.getArguments()), Collectors.counting()));
	}

	private static String execute(String source, boolean available, Path directory) throws Exception {
		Files.createDirectories(directory);
		var sources = List.of(Files.writeString(directory.resolve("Example.java"), source, StandardCharsets.UTF_8),
				Files.writeString(directory.resolve("Status.java"), status(available), StandardCharsets.UTF_8),
				Files.writeString(directory.resolve("IStatus.java"), ISTATUS, StandardCharsets.UTF_8),
				Files.writeString(directory.resolve("MultiStatus.java"), MULTI, StandardCharsets.UTF_8));
		var compiler = ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler);
		var diagnostics = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
		try (var manager = compiler.getStandardFileManager(diagnostics, java.util.Locale.ROOT,
				StandardCharsets.UTF_8)) {
			assertTrue(
					compiler.getTask(null, manager, diagnostics,
							List.of("--release", "9", "-proc:none", "-classpath", directory.toString(), "-d",
									directory.toString()),
							null, manager.getJavaFileObjectsFromPaths(sources)).call(),
					diagnostics.getDiagnostics().toString());
		}
		try (var loader = new URLClassLoader(new URL[] { directory.toUri().toURL() }, null)) {
			var output = new ByteArrayOutputStream();
			PrintStream original = System.out;
			try (var capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
				System.setOut(capture);
				loader.loadClass("test1.Example").getMethod("run").invoke(null);
			} finally {
				System.setOut(original);
			}
			Class<?> status = loader.loadClass("org.eclipse.core.runtime.Status");
			return status.getField("calls").get(null) + ":" + status.getField("log").get(null) + ":"
					+ output.toString(StandardCharsets.UTF_8);
		}
	}
}
