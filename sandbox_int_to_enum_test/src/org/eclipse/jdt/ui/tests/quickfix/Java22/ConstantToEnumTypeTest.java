/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java22;

import static org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions.performAndCheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;

import org.sandbox.jdt.internal.corext.fix.IntToEnumCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.IntToEnumCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;

/** Native cleanup changes, compilation, runtime equivalence and complete undo. */
public class ConstantToEnumTypeTest {

	@RegisterExtension
	final TestContext context= new TestContext();
	@TempDir
	Path temporary;
	private final List<ICompilationUnit> workingCopies= new ArrayList<>();

	static class TestContext extends AbstractEclipseJava {
		TestContext() { super("testresources/rtstubs_22.jar", JavaCore.VERSION_22); }
		void verify(ICompilationUnit unit) throws Exception { assertNoCompilationError(unit); }
	}

	record Scenario(String type, String first, String second, String conditionA, String conditionB) {
		Scenario(String type, String first, String second) {
			this(type, first, second, "state == STATUS_A", "STATUS_B == state");
		}

		String source(boolean coordinated) {
			String visibility= coordinated ? "" : "private ";
			return """
					package test1;
					public class Example {
					    VISIBILITYstatic final STATE_TYPE STATUS_A = FIRST_VALUE;
					    VISIBILITYstatic final STATE_TYPE STATUS_B = SECOND_VALUE;
					    VISIBILITYstatic int process(STATE_TYPE state) {
					        if (CONDITION_A) return 17;
					        else if (CONDITION_B) return 29;
					        return 41;
					    }
					    public static String run() { return process(STATUS_A) + ":" + process(STATUS_B); }
					}
					""".replace("VISIBILITY", visibility).replace("STATE_TYPE", type)
					.replace("FIRST_VALUE", first).replace("SECOND_VALUE", second)
					.replace("CONDITION_A", conditionA).replace("CONDITION_B", conditionB);
		}
	}

	static Stream<Scenario> scenarios() {
		return Stream.of(
				new Scenario("byte", "-128", "127"),
				new Scenario("short", "-32768", "32767"),
				new Scenario("char", "'\\0'", "'\\uffff'"),
				new Scenario("int", "-2147483648", "2147483647"),
				new Scenario("long", "-9223372036854775808L", "9223372036854775807L"),
				new Scenario("long", "0L", "0x1_0000_0000L"),
				new Scenario("String", "\"wait\" + \"ing\"", "\"done\""),
				new Scenario("java.lang.String", "\"a\"", "\"b\"", "state.equals(STATUS_A)", "STATUS_B.equals(state)"),
				new Scenario("String", "\"a\"", "\"b\"", "java.util.Objects.equals(state, STATUS_A)", "java.util.Objects.equals(STATUS_B, state)"),
				new Scenario("String", "\"a\"", "\"b\"", "((state) != (STATUS_A))", "((STATUS_B) != (state))"),
				new Scenario("long", "0L", "1L", "state != STATUS_A", "STATUS_B != state"));
	}

	@AfterEach
	void discardWorkingCopies() throws Exception {
		for (ICompilationUnit unit : workingCopies) unit.discardWorkingCopy();
	}

	@ParameterizedTest
	@MethodSource("scenarios")
	void localPreviewApplyRuntimeAndUndo(Scenario scenario) throws Exception {
		verifyMigration(scenario, false, true);
	}

	@ParameterizedTest
	@MethodSource("scenarios")
	void coordinatedPreviewApplyRuntimeAndUndo(Scenario scenario) throws Exception {
		verifyMigration(scenario, true, true);
	}

	@ParameterizedTest
	@MethodSource("scenarios")
	void localPreviewPreservesRuntime(Scenario scenario) throws Exception {
		verifyMigration(scenario, false, false);
	}

	@ParameterizedTest
	@MethodSource("scenarios")
	void coordinatedPreviewPreservesRuntime(Scenario scenario) throws Exception {
		verifyMigration(scenario, true, false);
	}

	static Stream<String> indexedBodies() {
		return Stream.of("""
				int[] values = { 31, 43 };
				if (state == STATUS_A) return values[state];
				else if (state == STATUS_B) return values[(state)] + 10;
				return -1;
				""", """
				int[] values = { 31, 43 };
				switch (state) {
				case STATUS_A: return values[state];
				case STATUS_B: return values[(state)] + 10;
				default: return -1;
				}
				""", """
				if (state == STATUS_B) return new int[] { 31, 43 }[state] + 10;
				else if (state == STATUS_A) return new int[] { 31, 43 }[state];
				return -1;
				""");
	}

	@ParameterizedTest
	@MethodSource("indexedBodies")
	void privateOrdinalArrayIndex(String body) throws Exception {
		String source= """
				package test1;
				public class Example {
				    private static final int STATUS_A = 0, STATUS_B = 1;
				    private static int process(int state) {
				        BODY
				    }
				    public static String run() { return process(STATUS_A) + ":" + process(STATUS_B); }
				}
				class Example$Unrelated {}
				""".replace("BODY", body);
		String preview= verifyMigration(source, false, true);
		assertTrue(preview.contains("state.ordinal()"), preview);
	}

	@ParameterizedTest
	@MethodSource("indexedBodies")
	void nestedOwnerKeepsQualifiedEnumAtEnclosingAndSiblingCallSites(String body) throws Exception {
		String source= """
				package test1;
				public class Example {
				    static class Base { enum Status { A } }
				    static class Owner {
				        private static final int STATUS_A = 0, STATUS_B = 1;
				        private static int process(int state) {
				            BODY
				        }
				        static class Child extends Base {
				            static int call() { return process(STATUS_A); }
				        }
				    }
				    static class Caller {
				        enum Status { B }
				        static int call() { return Owner.process(Example.Owner.STATUS_B); }
				    }
				    public static String run() {
				        return Owner.process(Owner.STATUS_A) + ":" + Caller.call() + ":" + Owner.Child.call();
				    }
				}
				""".replace("BODY", body);
		String preview= verifyMigration(source, false, true);
		assertEquals("31:53:31", execute(new String[] { preview }, false, "preview"));
	}

	@ParameterizedTest
	@ValueSource(strings= {"same unit", "primary", "secondary", "unsaved secondary", "binary", "member"})
	void skipsLocalTableMigrationWhenExistingTypeOwnsTheEnumBinaryName(String location) throws Exception {
		String alias= "class Example$Status {}";
		String source= new Scenario("int", "0", "1").source(false)
				.replace("return 17;", "return new int[] { 17, 29 }[state];");
		if ("same unit".equals(location)) {
			source+= "\n" + alias;
		} else if ("member".equals(location)) {
			source= source.replace("class Example {",
					"class Example { static class Inner$Status {} static class Inner {") + "}";
		} else if ("binary".equals(location)) {
			addBinaryAlias();
		} else if ("unsaved secondary".equals(location)) {
			var sibling= unit("Other.java", "package test1; class Other {}");
			sibling.getBuffer().setContents("package test1; class Other {} " + alias);
			assertTrue(sibling.hasUnsavedChanges());
		} else {
			String name= "primary".equals(location) ? "Example$Status.java" : "Other.java";
			unit(name, "package test1; " + alias);
		}
		ICompilationUnit owner= unit("Example.java", source);
		var cleanup= cleanup(false);
		cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { owner }, null);
		assertNull(cleanup.createFix(new CleanUpContext(owner, parse(owner))),
				"The existing type already owns the proposed enum class file");
		assertEquals(source, owner.getSource());
	}

	private void addBinaryAlias() throws Exception {
		Path file= temporary.resolve("Example$Status.java");
		Files.writeString(file, "package test1; public class Example$Status {}", StandardCharsets.UTF_8);
		assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
				"--release", "22", "-d", temporary.toString(), file.toString()));
		String entry= "test1/Example$Status.class";
		Path library= temporary.resolve("existing-domain.jar");
		try (var jar= new JarOutputStream(Files.newOutputStream(library))) {
			jar.putNextEntry(new JarEntry(entry));
			Files.copy(temporary.resolve(entry), jar);
		}
		var project= context.getJavaProject();
		List<IClasspathEntry> classpath= new ArrayList<>(List.of(project.getRawClasspath()));
		classpath.add(JavaCore.newLibraryEntry(org.eclipse.core.runtime.IPath.fromOSString(library.toString()), null, null));
		project.setRawClasspath(classpath.toArray(IClasspathEntry[]::new), null);
	}

	private void verifyMigration(Scenario scenario, boolean coordinated, boolean apply) throws Exception {
		verifyMigration(scenario.source(coordinated), coordinated, apply);
	}

	private String verifyMigration(String source, boolean coordinated, boolean apply) throws Exception {
		List<ICompilationUnit> units= new ArrayList<>();
		units.add(unit("Example.java", source));
		if (coordinated) units.add(unit("Client.java", """
				package test1;
				public class Client {
				    public static String run() { return Example.process(Example.STATUS_B) + ":" + Example.process(Example.STATUS_A); }
				}
				"""));
		String[] originals= sources(units);
		String expected= execute(originals, coordinated, "original");
		IntToEnumCleanUpCore cleanup= cleanup(coordinated);
		assertFalse(cleanup.checkPreConditions(context.getJavaProject(), units.toArray(ICompilationUnit[]::new), null).hasError());
		CompositeChange changes= new CompositeChange("Typed enum migration");
		List<String> previews= new ArrayList<>();
		try {
			for (ICompilationUnit unit : units) {
				var fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
				assertNotNull(fix, source);
				var change= fix.createChange(null);
				String preview= change.getPreviewContent(null);
				assertTrue(preview.contains("Status"));
				previews.add(preview);
				changes.add(change);
			}
			for (int i= 0; i < units.size(); i++) assertEquals(originals[i], units.get(i).getSource());
			if (!apply) {
				assertEquals(expected, execute(previews.toArray(String[]::new), coordinated, "preview"));
				return previews.get(0);
			}
			changes.initializeValidationData(null);
			assertFalse(changes.isValid(null).hasFatalError());
			Change undo= performAndCheck(changes, new NullProgressMonitor(), units.toArray(ICompilationUnit[]::new));
			assertNotNull(undo);
			try {
				for (ICompilationUnit unit : units) context.verify(unit);
				assertEquals(expected, execute(sources(units), coordinated, "converted"));
				assertTrue(units.get(0).getSource().contains("enum Status"));
				assertFalse(units.get(0).getSource().contains("STATUS_A"));
				IntToEnumCleanUpCore repeated= cleanup(coordinated);
				repeated.checkPreConditions(context.getJavaProject(), units.toArray(ICompilationUnit[]::new), null);
				for (ICompilationUnit unit : units) assertNull(repeated.createFix(new CleanUpContext(unit, parse(unit))));
				undo.initializeValidationData(null);
				Change redo= undo.perform(new NullProgressMonitor());
				if (redo != null) redo.dispose();
				for (int i= 0; i < units.size(); i++) assertEquals(originals[i], units.get(i).getSource());
			} finally { undo.dispose(); }
		} finally { changes.dispose(); }
		return previews.get(0);
	}

	static Stream<String> rejectedSources() {
		Scenario strings= new Scenario("String", "\"a\"", "\"b\"");
		String source= strings.source(false);
		String indexed= new Scenario("int", "0", "1").source(false)
				.replace("return 17;", "return new int[] {17, 29}[state];");
		String table= indexed.replace("if (state == STATUS_A)", "int[] table = {17, 29}; if (state == STATUS_A)")
				.replace("new int[] {17, 29}[state]", "table[state]");
		return Stream.of(
				table.replace("table[state]", "table[state]++"),
				table.replace("return table[state];", "{ System.out.println(table); return table[state]; }"),
				table.replace("int[] table = {17, 29};", "int[] table = external;")
						.replace("public class Example {", "public class Example { public static int[] external;"),
				indexed.replace("int STATUS_", "byte STATUS_").replace("process(int state)", "process(byte state)"),
				indexed.replace("int STATUS_B = 1", "int STATUS_B = 2"),
				indexed.replace("int STATUS_B = 1", "int STATUS_B = 0").replace("int STATUS_A = 0", "int STATUS_A = 1"),
				indexed.replace("return 29;", "return state;"),
				indexed.replace("[state]", "[state + 1]"),
				indexed.replace("process(STATUS_B)", "process(3)"),
				indexed.replace("public static String run()", """
						static class Caller extends Base {
						    static int call(int Example, int test1) { return Example + test1 + process(STATUS_A); }
						}
						public static String run()""") + "\nclass Base { enum Status { A } }\n",
				source.replace("public static String run()", "static Status other() { return new Status(); } public static String run()")
						+ "\nclass Status {}\n",
				source.replace("if (state == STATUS_A)", "int Status = 0; if (state == STATUS_A)"),
				source.replace("process(STATUS_A)", "process(null)"),
				source.replace("\"b\"", "null"),
				source.replace("process(STATUS_A)", "process(new String(STATUS_A))"),
				source.replace("\"b\"", "\"a\" + \"\""),
				source.replace("state == STATUS_A", "state.equalsIgnoreCase(STATUS_A)"),
				source.replace("return 41;", "return state.length();"),
				source.replace("private static int process", "public static int process"),
				source.replace("private static final", "public static final"),
				source.replace("process(STATUS_A)", "process(receiver().STATUS_A)")
						.replace("public static String run()", "static Example receiver() { throw new IllegalStateException(); } public static String run()"),
				new Scenario("long", "1L", "0x1L").source(false),
				new Scenario("long", "0L", "1L").source(false).replace("long STATUS_A = 0L", "int STATUS_A = 0"),
				new Scenario("Long", "Long.valueOf(1)", "Long.valueOf(2)").source(false),
				new Scenario("double", "0.0", "-0.0").source(false),
				new Scenario("double", "0.0 / 0.0", "1.0").source(false),
				new Scenario("float", "1.0f", "2.0f").source(false),
				new Scenario("boolean", "false", "true").source(false),
				new Scenario("Object", "new Object()", "new Object()").source(false));
	}

	@ParameterizedTest
	@MethodSource("rejectedSources")
	void leavesUnprovenStateDomainsUnchanged(String source) throws Exception {
		ICompilationUnit unit= unit("Example.java", source);
		IntToEnumCleanUpCore cleanup= cleanup(false);
		cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, null);
		assertNull(cleanup.createFix(new CleanUpContext(unit, parse(unit))), source);
		assertEquals(source, unit.getSource());
	}

	@ParameterizedTest
	@MethodSource("rejectedSources")
	void rejectsUnprovenCoordinatedDomains(String source) throws Exception {
		ICompilationUnit owner= unit("Example.java", source.replace("private static", "static"));
		ICompilationUnit client= unit("Client.java", "package test1; class Client { int run() { return Example.process(Example.STATUS_B); } }");
		IntToEnumCleanUpCore cleanup= cleanup(true);
		cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { owner, client }, null);
		assertNull(cleanup.createFix(new CleanUpContext(owner, parse(owner))), source);
		assertNull(cleanup.createFix(new CleanUpContext(client, parse(client))), source);
	}

	record StaleMutation(Scenario scenario, String before, String after) { }

	static Stream<StaleMutation> staleMutations() {
		Scenario strings= new Scenario("String", "\"a\"", "\"b\"");
		Scenario longs= new Scenario("long", "0L", "1L");
		return Stream.of(
				new StaleMutation(strings, "public class Example {", "class Status {} public class Example { static Status other = new Status();"),
				new StaleMutation(longs, "STATUS_B = 1L", "STATUS_B = 2L"),
				new StaleMutation(longs, "return 41;", "return (int) state;"),
				new StaleMutation(strings, "STATUS_B = \"b\"", "STATUS_B = \"a\""),
				new StaleMutation(strings, "return 41;", "return state.length();"),
				new StaleMutation(strings, "static int process", "public static int process"),
				new StaleMutation(strings, "static final String STATUS_A", "public static final String STATUS_A"));
	}

	@ParameterizedTest
	@MethodSource("staleMutations")
	void rejectsChangedValuesStateUsesAndVisibilityAtomically(StaleMutation mutation) throws Exception {
		String original= mutation.scenario().source(true);
		ICompilationUnit owner= unit("Example.java", original);
		ICompilationUnit client= unit("Client.java", "package test1; class Client { int run() { return Example.process(Example.STATUS_B); } }");
		IntToEnumCleanUpCore cleanup= cleanup(true);
		assertFalse(cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { owner, client }, null).hasError());
		String changed= original.replace(mutation.before(), mutation.after());
		assertFalse(changed.equals(original));
		owner.getBuffer().setContents(changed);
		context.verify(owner);
		assertThrows(CoreException.class, () -> cleanup.createFix(new CleanUpContext(owner, parse(owner))));
		assertNull(cleanup.createFix(new CleanUpContext(client, parse(client))), "A stale owner must invalidate all caller changes");
		assertEquals(changed, owner.getSource());
	}

	ICompilationUnit unit(String name, String source) throws Exception {
		ICompilationUnit unit= context.getSourceFolder().createPackageFragment("test1", false, null)
				.createCompilationUnit(name, source, false, null);
		unit.becomeWorkingCopy(null);
		workingCopies.add(unit);
		context.verify(unit);
		return unit;
	}

	static IntToEnumCleanUpCore cleanup(boolean coordinated) {
		return new IntToEnumCleanUpCore(Map.of(MYCleanUpConstants.INT_TO_ENUM_CLEANUP, CleanUpOptions.TRUE,
				IntToEnumCleanUpOptions.PROJECT_WIDE, coordinated ? CleanUpOptions.TRUE : CleanUpOptions.FALSE));
	}

	static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}

	static String[] sources(List<ICompilationUnit> units) throws Exception {
		String[] result= new String[units.size()];
		for (int i= 0; i < units.size(); i++) result[i]= units.get(i).getSource();
		return result;
	}

	private String execute(String[] sources, boolean coordinated, String directory) throws Exception {
		Path output= Files.createDirectories(temporary.resolve(directory));
		List<String> arguments= new ArrayList<>(List.of("--release", "21", "-d", output.toString()));
		for (int i= 0; i < sources.length; i++) {
			Path file= output.resolve(i == 0 ? "Example.java" : "Client.java");
			Files.writeString(file, sources[i], StandardCharsets.UTF_8);
			arguments.add(file.toString());
		}
		var compiler= ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler, "Runtime equivalence requires a JDK");
		assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)), String.join("\n", sources));
		try (URLClassLoader loader= new URLClassLoader(new URL[] { output.toUri().toURL() }, ClassLoader.getPlatformClassLoader())) {
			return (String) loader.loadClass(coordinated ? "test1.Client" : "test1.Example").getMethod("run").invoke(null);
		}
	}
}
