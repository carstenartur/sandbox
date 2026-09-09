/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitMultiFilePlanner;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitTestTypeInventory;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.triggerpattern.api.ExecutionTreeSnapshot;
import org.sandbox.jdt.triggerpattern.api.ExecutionTreeSnapshot.Node;
import org.sandbox.jdt.triggerpattern.api.ExecutionTreeSnapshot.NodeKind;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;

/** Real JDT finder, JUnit 4 loader, Jupiter loader and atomic workspace changes. */
public class ParameterizedCoordinatedExecutionTest {
	@RegisterExtension
	final Context context= new Context();
	private IPackageFragment pack;
	private static final String IMPORTS= """
			package execution;
			import org.junit.Test;
			import org.junit.runner.RunWith;
			import org.junit.runners.Parameterized;
			import org.junit.runners.Parameterized.Parameters;
			import org.junit.runners.Parameterized.Parameter;
			""";
	private static final String PROVIDER= """
			@Parameters(name="{index}: ''{1}''={0}")
			public static Object[][] data() { return new Object[][] {{1,"one"},{2,"two"}}; }
			""";
	private static final String METHODS= """
			private int state;
			public static int constructed;
			public static int completed;
			@org.junit.BeforeClass public static void start() { constructed = 0; completed = 0; }
			@org.junit.Before public void before() { org.junit.Assert.assertEquals(1, ++state); }
			@org.junit.After public void after() { org.junit.Assert.assertEquals(1, state); completed++; }
			@org.junit.AfterClass public static void finish() {
				org.junit.Assert.assertEquals(4, constructed);
				org.junit.Assert.assertEquals(4, completed);
			}
			@Test public void aa() { org.junit.Assert.assertTrue(value > 0); }
			@Test public void z() { org.junit.Assert.assertEquals(value == 1 ? "one" : "two", label); }
			""";

	@BeforeEach
	void setup() throws CoreException {
		pack= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH)
				.createPackageFragment("execution", true, null); //$NON-NLS-1$
		assertNotNull(context.getJavaProject().findType("org.junit.jupiter.params.ParameterizedClass"), //$NON-NLS-1$
				"The supported Eclipse target must supply ParameterizedClass");
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
	}

	@ParameterizedTest
	@ValueSource(strings= { "constructor", "fields", "inherited", "failures", "duplicate-names" })
	void preservesRuntimeDiscoveryRowsNamesResultsAndLifecycle(String shape) throws CoreException {
		ICompilationUnit[] units= fixture(shape);
		ICompilationUnit test= units[units.length - 1];
		Map<String, String> original= sources(units);
		var inventory= JUnitTestTypeInventory.capture(context.getJavaProject(), null).typeHandles();
		ExecutionTreeSnapshot before= JUnitRuntimeTestTree.capture(test.findPrimaryType(), JUnitRuntimeTestTree.TestKind.JUNIT4, true);
		assertEquals(!shape.equals("failures"), before.successful(), before.toString());
		List<Observation> baseline= observations(before, false);
		assertEquals(4, baseline.size(), before.toString());
		assertTrue(baseline.get(0).displayName().contains("[0:") || shape.equals("duplicate-names"), baseline.toString());
		context.apply(units);
		assertNotEquals(original, sources(units), "The coordinated migration must execute");
		assertTrue(test.getSource().contains("ParameterizedClass"), test.getSource());
		assertEquals(inventory, JUnitTestTypeInventory.capture(context.getJavaProject(), null).typeHandles());
		ExecutionTreeSnapshot after= JUnitRuntimeTestTree.capture(test.findPrimaryType(), JUnitRuntimeTestTree.TestKind.JUNIT5, true);
		assertEquals(before.successful(), after.successful(), after.toString());
		assertEquals(baseline, observations(after, true), after.toString());
		Map<String, String> converted= sources(units);
		RefactoringCore.getUndoManager().performUndo(null, null);
		assertEquals(original, sources(units), "One undo must restore every participating source");
		context.apply(units);
		assertEquals(converted, sources(units));
		context.apply(units);
		assertEquals(converted, sources(units), "A second cleanup pass must be stable");
	}

	@ParameterizedTest
	@ValueSource(strings= { "static-initializer", "static-field", "empty-rows", "timeout", "shared-base", "rule" })
	void rejectedExecutionContractsLeaveEverySourceUntouched(String reason) throws CoreException {
		ICompilationUnit[] units= fixture("inherited");
		ICompilationUnit data= units[0];
		ICompilationUnit base= units[1];
		ICompilationUnit test= units[2];
		switch (reason) {
		case "static-initializer" -> data.getBuffer().setContents(data.getSource().replace("public class Data {", "public class Data { static { System.setProperty(\"provider\", \"initialized\"); }"));
		case "static-field" -> data.getBuffer().setContents(data.getSource().replace("public class Data {", "public class Data { static Object state = new Object();"));
		case "empty-rows" -> data.getBuffer().setContents(data.getSource().replace("{{1,\"one\"},{2,\"two\"}}", "{}"));
		case "timeout" -> base.getBuffer().setContents(base.getSource().replace("@Test public void aa", "@Test(timeout=100) public void aa"));
		case "shared-base" -> unit("Sibling", "public class Sibling extends Base { }");
		case "rule" -> test.getBuffer().setContents(test.getSource().replace("extends Base {", "extends Base { @org.junit.Rule public org.junit.rules.TestName name = new org.junit.rules.TestName();"));
		default -> throw new AssertionError(reason);
		}
		ICompilationUnit[] scope= pack.getCompilationUnits();
		Map<String, String> original= sources(scope);
		context.apply(scope);
		assertEquals(original, sources(scope), reason);
	}

	@Test
	void staleProviderBodyAndAddedSourceAreRejectedBeforeCreatingEdits() throws CoreException {
		ICompilationUnit[] units= fixture("constructor");
		ICompilationUnit test= units[0];
		var options= new JUnitMultiFilePlanner.PlanningOptions(false, false, true, true);
		var plan= JUnitMultiFilePlanner.createCoordinated(context.getJavaProject(), units, options, true, null).plan();
		assertTrue(plan.hasCoordinatedChanges());
		String original= test.getSource();
		test.getBuffer().setContents(original.replace("{1,\"one\"}", "{3,\"one\"}"));
		assertThrows(CoreException.class, () -> plan.addOperationsFor(test, parse(test), new LinkedHashSet<>(), new LinkedHashSet<>()));
		assertEquals(original.replace("{1,\"one\"}", "{3,\"one\"}"), test.getSource());
		test.getBuffer().setContents(original);
		unit("Added", "public class Added { }");
		assertThrows(CoreException.class, () -> plan.addOperationsFor(test, parse(test), new LinkedHashSet<>(), new LinkedHashSet<>()));
		assertEquals(original, test.getSource());
	}

	@Test
	void partialSelectionCannotRewriteAnInheritedTest() throws CoreException {
		ICompilationUnit[] units= fixture("inherited");
		var result= JUnitMultiFilePlanner.createCoordinated(context.getJavaProject(), new ICompilationUnit[] { units[2] },
				new JUnitMultiFilePlanner.PlanningOptions(false, false, true, true), false, null);
		assertFalse(result.plan().hasCoordinatedChanges());
		assertEquals("PARAMETERIZED_INCOMPLETE_SCOPE", result.diagnostics().candidates().get(0).reasonCode());
	}

	private ICompilationUnit[] fixture(String shape) throws CoreException {
		String fields= "@Parameter(1) public String label; @Parameter public int value;";
		if (shape.equals("inherited")) {
			ICompilationUnit data= unit("Data", "public class Data { public static Object[][] rows() { return new Object[][] {{1,\"one\"},{2,\"two\"}}; } }");
			ICompilationUnit base= unit("Base", "public abstract class Base {" + fields + " public Base() { constructed++; }"
					+ PROVIDER.replace("return new Object[][] {{1,\"one\"},{2,\"two\"}};", "return Data.rows();") + METHODS + "}");
			return new ICompilationUnit[] { data, base, unit("Sample", "@RunWith(Parameterized.class) public class Sample extends Base { }") };
		}
		String injection= shape.equals("fields") ? fields + "public Sample() { constructed++; }"
				: "private final int value; private final String label; public Sample(int value, String label) { constructed++; this.value = value; this.label = label; }";
		String methods= shape.equals("failures") ? METHODS.replace("org.junit.Assert.assertTrue(value > 0);", "org.junit.Assert.fail(\"intentional:\" + value);") : METHODS;
		String provider= shape.equals("duplicate-names") ? PROVIDER.replace("{index}: ''{1}''={0}", "duplicate") : PROVIDER;
		return new ICompilationUnit[] { unit("Sample", "@RunWith(Parameterized.class) public class Sample {" + injection + provider + methods + "}") };
	}

	private ICompilationUnit unit(String name, String body) throws CoreException {
		return pack.createCompilationUnit(name + ".java", IMPORTS + body, false, null);
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}

	private static Map<String, String> sources(ICompilationUnit[] units) throws CoreException {
		Map<String, String> sources= new LinkedHashMap<>();
		for (ICompilationUnit unit : units) {
			sources.put(unit.getHandleIdentifier(), unit.getSource());
		}
		return sources;
	}

	private record Observation(String testClass, String displayName, String result) { }

	private static List<Observation> observations(ExecutionTreeSnapshot tree, boolean jupiter) {
		List<Observation> result= new ArrayList<>();
		for (Node root : tree.roots()) {
			observations(root, "", jupiter, result);
		}
		return result;
	}

	private static void observations(Node node, String row, boolean jupiter, List<Observation> result) {
		String currentRow= node.kind() == NodeKind.CONTAINER && node.displayName().startsWith("[") ? node.displayName() : row;
		if (node.kind() == NodeKind.TEST) {
			String method= node.attributes().get("testMethod");
			String name= jupiter ? node.displayName().replaceFirst("\\(\\)$", "") + currentRow : method;
			result.add(new Observation(node.attributes().get("testClass"), name, node.result()));
		}
		for (Node child : node.children()) {
			observations(child, currentRow, jupiter, result);
		}
	}

	static final class Context extends AbstractEclipseJava {
		Context() {
			super("testresources/rtstubs_17.jar", JavaCore.VERSION_17); //$NON-NLS-1$
		}

		void apply(ICompilationUnit[] units) throws CoreException {
			var status= performRefactoring(units, null);
			assertFalse(status.hasError(), status.toString());
			for (ICompilationUnit unit : units) {
				assertNoCompilationError(unit);
			}
		}
	}
}
