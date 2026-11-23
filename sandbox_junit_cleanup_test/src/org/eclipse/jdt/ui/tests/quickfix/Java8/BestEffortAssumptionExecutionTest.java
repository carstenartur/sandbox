/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0: https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.junit.model.ITestElement.Result;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix.JUnitMigrationOptions;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.JUnitCleanUpCore;
import org.sandbox.jdt.triggerpattern.api.ExecutionTreeSnapshot.Node;
import org.sandbox.jdt.triggerpattern.api.ExecutionTreeSnapshot.NodeKind;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Actual Apply must preserve assumption aborts in retained Vintage consumers. */
class BestEffortAssumptionExecutionTest {

	@RegisterExtension
	final EclipseJava17 provider= new EclipseJava17();
	@RegisterExtension
	final EclipseJava17 consumer= new EclipseJava17();
	@RegisterExtension
	final EclipseJava17 independent= new EclipseJava17();
	@TempDir
	Path binaryClasses;

	@ParameterizedTest
	@ValueSource(strings= { "selected", "expanded", "helper-first", "consumer-first", "unselected", "read-only", "inherited", "timeout", "binary", "runner",
			"generated-public", "derived-protected", "generated-multistatement" })
	void preservesAbortOutcomesAcrossTheCoordinatedSourceGroup(String selection) throws Exception {
		IPackageFragment helpers= junitPackage(provider);
		boolean local= List.of("selected", "expanded", "inherited", "timeout", "binary", "runner").contains(selection);
		IPackageFragment callers= local ? helpers : junitPackage(consumer);
		if (selection.startsWith("generated-") || selection.startsWith("derived-")) {
			boolean derived= selection.startsWith("derived-");
			var source= AbstractEclipseJava.addSourceContainer(consumer.getJavaProject(),
					derived ? "derived-tests" : "generated-test-sources");
			if (derived) source.getResource().setDerived(true, null);
			callers= source.createPackageFragment("assumptions", false, null);
		}
		if (!local) {
			AbstractEclipseJava.addToClasspath(consumer.getJavaProject(),
					JavaCore.newProjectEntry(provider.getJavaProject().getPath()));
		}
		ICompilationUnit helper= unit(helpers, "Checks", """
				public class Checks {
					static void trueCheck(boolean value) { org.junit.Assume.assumeTrue(value); }
					static void falseCheck(boolean value) { org.junit.Assume.assumeFalse(value); }
				}
				""");
		if (selection.endsWith("public") || selection.endsWith("protected")) {
			helper.getBuffer().setContents(helper.getSource().replace("static void",
					(selection.endsWith("public") ? "public" : "protected") + " static void"));
		} else if (selection.endsWith("multistatement")) {
			helper.getBuffer().setContents(helper.getSource().replace("{ org.junit.Assume",
					"{ org.junit.Assert.assertTrue(true); org.junit.Assume"));
		}
		helper.save(null, true);
		ICompilationUnit retained= unit(callers, "Retained", """
				public class Retained {
					@org.junit.Rule public org.junit.rules.TestRule rule = new org.junit.rules.TestName();
					@org.junit.Test public void control() { org.junit.Assert.assertTrue(true); }
					@org.junit.Test public void directTrue() { org.junit.Assume.assumeTrue(false); }
					@org.junit.Test public void directFalse() { org.junit.Assume.assumeFalse(true); }
					@org.junit.Test public void helperTrue() { Checks.trueCheck(false); }
					@org.junit.Test public void helperFalse() { Checks.falseCheck(true); }
				}
				""");
		ICompilationUnit jupiter= unit(helpers, "JupiterConsumer", """
				public class JupiterConsumer {
					@org.junit.Test public void control() {
						org.junit.jupiter.api.Assumptions.assumeTrue(!Boolean.getBoolean("assumption.fail"));
						org.junit.Assert.assertTrue(true);
					}
					@org.junit.Test public void helperTrue() { Checks.trueCheck(false); }
					@org.junit.Test public void helperFalse() { Checks.falseCheck(true); }
				}
				""");
		List<ICompilationUnit> selected= new ArrayList<>(List.of(helper, retained, jupiter));
		if (selection.equals("runner")) {
			retained.getBuffer().setContents(retained.getSource()
					.replace("\t@org.junit.Rule public org.junit.rules.TestRule rule = new org.junit.rules.TestName();\n", "")
					.replace("@org.junit.Test ", "")
					.replace("public class Retained", "@org.junit.runner.RunWith(MethodRunner.class) public class Retained"));
			retained.save(null, true);
			selected.add(unit(helpers, "MethodRunner", """
					public class MethodRunner extends org.junit.runners.BlockJUnit4ClassRunner {
						public MethodRunner(Class<?> type) throws org.junit.runners.model.InitializationError { super(type); }
						@Override protected java.util.List<org.junit.runners.model.FrameworkMethod> computeTestMethods() {
							return java.util.Arrays.stream(getTestClass().getJavaClass().getDeclaredMethods())
									.map(org.junit.runners.model.FrameworkMethod::new).toList();
						}
					}
					"""));
		}
		List<ICompilationUnit> affected= new ArrayList<>(selected);
		if (selection.equals("timeout")) {
			retained.getBuffer().setContents(retained.getSource()
					.replace("\t@org.junit.Rule public org.junit.rules.TestRule rule = new org.junit.rules.TestName();\n", "")
					.replace("@org.junit.Test", "@org.junit.Test(timeout=1000)"));
			retained.save(null, true);
		}
		if (selection.equals("inherited") || selection.equals("binary")) {
			String inherited= retained.getSource().replace("public class Retained", "public abstract class Base")
					.replace("\t@org.junit.Rule public org.junit.rules.TestRule rule = new org.junit.rules.TestName();\n", "");
			ICompilationUnit base= helpers.createCompilationUnit("Base.java", inherited, false, null);
			retained.getBuffer().setContents("package assumptions; public class Retained extends Base {}\n");
			retained.save(null, true);
			if (selection.equals("binary")) {
				ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, null);
				Path compiled= ResourcesPlugin.getWorkspace().getRoot().getFile(provider.getJavaProject()
						.getOutputLocation().append("assumptions/Base.class")).getLocation().toFile().toPath();
				Files.createDirectories(binaryClasses.resolve("assumptions"));
				Files.copy(compiled, binaryClasses.resolve("assumptions/Base.class"));
				Files.writeString(binaryClasses.resolve("Base.java.before"), inherited);
				base.delete(true, null);
				AbstractEclipseJava.addToClasspath(provider.getJavaProject(), JavaCore.newLibraryEntry(
						org.eclipse.core.runtime.Path.fromOSString(binaryClasses.toString()), null, null));
			} else {
				affected.add(base);
			}
		}
		var cleanup= cleanup();
		List<List<ICompilationUnit>> groups= new ArrayList<>();
		if (local) {
			if (selection.equals("expanded")) {
				List<ICompilationUnit> expanded= new ArrayList<>(List.of(helper));
				expanded.addAll(cleanup.expandCleanUpScope(provider.getJavaProject(), expanded, null));
				assertTrue(expanded.containsAll(affected), expanded.toString());
				groups.add(expanded.stream().distinct().toList());
			} else {
				groups.add(selected);
			}
		} else {
			groups.add(List.of(helper, jupiter));
			if (selection.equals("helper-first")) groups.add(List.of(retained));
			if (selection.equals("consumer-first")) groups.addFirst(List.of(retained));
		}
		var resource= consumer.getJavaProject().getProject();
		var attributes= resource.getResourceAttributes();
		try {
			if (selection.equals("read-only")) {
				var readOnly= resource.getResourceAttributes();
				readOnly.setReadOnly(true);
				resource.setResourceAttributes(readOnly);
			}
			assertCompiles(affected);
			assertOutcomes(retained, "junit-vintage", 4);
			applyAndUndo(cleanup, groups, affected, () -> {
				exportSources(selection, affected);
				assertOutcomes(retained, "junit-vintage", 4);
				assertOutcomes(jupiter, "junit-jupiter", 2);
				assertTrue(helper.getSource().contains("org.junit.Assume"), helper.getSource());
				assertTrue(jupiter.getSource().contains("Assumptions.assumeFalse("), jupiter.getSource());
				assertTrue(jupiter.getSource().contains("org.junit.jupiter.api.Assertions"), jupiter.getSource());
			});
		} finally {
			resource.setResourceAttributes(attributes);
		}
		// Reuse the instance after per-project postconditions: no retained consumer in this group.
		assertClosedGroupMigrates(cleanup, selection + "-closed", "legacy");
	}

	@ParameterizedTest
	@ValueSource(strings= { "legacy", "jupiter", "timeout" })
	void closedGroupsStillMigrateAssumptions(String mode) throws Exception {
		assertClosedGroupMigrates(cleanup(mode.equals("timeout")), "closed-" + mode, mode);
	}

	private void assertClosedGroupMigrates(JUnitCleanUpCore cleanup, String scenario, String mode) throws Exception {
		ICompilationUnit closed= unit(junitPackage(independent), "Closed", """
				import org.junit.Test;
				public class Closed {
					@Test public void control() { org.junit.Assert.assertTrue(true); }
					@Test public void directTrue() { org.junit.Assume.assumeTrue(false); }
					@Test public void directFalse() { org.junit.Assume.assumeFalse(true); }
				}
				""".replace("org.junit.Test", mode.equals("jupiter") ? "org.junit.jupiter.api.Test" : "org.junit.Test")
				.replace("@Test", mode.equals("timeout") ? "@Test(timeout=1000)" : "@Test"));
		applyAndUndo(cleanup, List.of(List.of(closed)), List.of(closed), () -> {
			exportSources(scenario, List.of(closed));
			assertOutcomes(closed, "junit-jupiter", 2);
			assertFalse(closed.getSource().contains("org.junit.Assume"), closed.getSource());
			assertTrue(closed.getSource().contains("org.junit.jupiter.api.Assumptions"), closed.getSource());
		});
	}

	private static JUnitCleanUpCore cleanup() {
		return cleanup(false);
	}

	private static JUnitCleanUpCore cleanup(boolean timeouts) {
		Map<String, String> options= new LinkedHashMap<>();
		for (String option : List.of(MYCleanUpConstants.JUNIT_CLEANUP, JUnitMigrationOptions.BEST_EFFORT,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST, MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME, MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME_OPTIMIZATION,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE)) options.put(option, CleanUpOptions.TRUE);
		if (timeouts) options.put(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_TIMEOUT, CleanUpOptions.TRUE);
		return new JUnitCleanUpCore(options);
	}

	private static IPackageFragment junitPackage(EclipseJava17 context) throws Exception {
		var root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
		// Runner fixtures need the complete native JCL, including ParentRunner's Lock API.
		var project= context.getJavaProject();
		project.setRawClasspath(Stream.of(project.getRawClasspath()).map(entry ->
				entry.getPath().lastSegment().startsWith("rtstubs_") ? JavaRuntime.getDefaultJREContainerEntry() : entry)
				.toArray(IClasspathEntry[]::new), null);
		return root.createPackageFragment("assumptions", false, null);
	}

	private static ICompilationUnit unit(IPackageFragment pack, String name, String body) throws Exception {
		return pack.createCompilationUnit(name + ".java", "package assumptions;\n" + body, false, null);
	}

	@FunctionalInterface
	private interface AppliedAssertions {
		void check() throws Exception;
	}

	private static void applyAndUndo(JUnitCleanUpCore cleanup, List<List<ICompilationUnit>> groups,
			List<ICompilationUnit> affected, AppliedAssertions assertions) throws Exception {
		assertCompiles(affected);
		Map<ICompilationUnit, String> originals= new LinkedHashMap<>();
		for (ICompilationUnit unit : affected) originals.put(unit, unit.getSource());
		var projects= affected.stream().map(ICompilationUnit::getJavaProject).distinct().toList();
		var options= projects.stream().map(project -> project.getOptions(false)).toList();
		List<List<IClasspathEntry>> classpaths= new ArrayList<>();
		for (var project : projects) classpaths.add(List.of(project.getRawClasspath()));
		var diagnostics= CompilerDiagnosticAssertions.capture(affected.toArray(ICompilationUnit[]::new));
		var change= new CompositeChange("Retained assumption execution");
		var monitor= new NullProgressMonitor();
		try {
			for (List<ICompilationUnit> group : groups) {
				// Stock JDT runs preconditions, fixes and postconditions inside its project loop.
				var status= cleanup.checkPreConditions(group.getFirst().getJavaProject(), group.toArray(ICompilationUnit[]::new), monitor);
				assertFalse(status.hasFatalError(), status.toString());
				for (ICompilationUnit unit : group) {
					var fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
					if (fix != null) change.add(fix.createChange(monitor));
				}
				assertFalse(cleanup.checkPostConditions(monitor).hasError());
			}
			change.initializeValidationData(monitor);
			var undo= change.perform(monitor);
			assertNotNull(undo);
			try {
				diagnostics.assertNoNewDiagnostics();
				assertions.check();
			} finally {
				var redo= undo.perform(monitor);
				if (redo != null) redo.dispose();
				undo.dispose();
			}
		} finally {
			change.dispose();
			cleanup.checkPostConditions(monitor);
			for (var original : originals.entrySet()) assertEquals(original.getValue(), original.getKey().getSource());
			assertEquals(options, projects.stream().map(project -> project.getOptions(false)).toList());
			for (int index= 0; index < projects.size(); index++) {
				assertEquals(classpaths.get(index), List.of(projects.get(index).getRawClasspath()));
			}
			diagnostics.assertRestored();
		}
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		var parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}

	private static void assertCompiles(List<ICompilationUnit> units) {
		for (ICompilationUnit unit : units) {
			var problems= parse(unit).getProblems();
			assertTrue(Stream.of(problems).noneMatch(problem -> problem.isError()), List.of(problems).toString());
		}
	}

	private static void assertOutcomes(ICompilationUnit unit, String engine, int aborted) throws Exception {
		var tree= JUnitRuntimeTestTree.capture(unit.findPrimaryType(), JUnitRuntimeTestTree.TestKind.JUNIT5);
		var tests= tree.roots().stream().flatMap(BestEffortAssumptionExecutionTest::descendants)
				.filter(node -> node.kind() == NodeKind.TEST).toList();
		assertEquals(aborted + 1, tests.size(), tree.toString());
		assertTrue(tests.stream().allMatch(node -> node.attributes().get("uniqueId").contains("[engine:" + engine + "]")), tree.toString());
		assertEquals(0, tests.stream().filter(node -> node.result().equals(Result.ERROR.toString()) || node.result().equals(Result.FAILURE.toString())).count(), tree.toString());
		assertEquals(aborted, tests.stream().filter(node -> node.attributes().get("assumptionFailure").equals("true")).count(), tree.toString());
		assertEquals(1, tests.stream().filter(node -> node.result().equals("OK")).count(), tree.toString());
		assertEquals(0, tests.stream().filter(node -> node.result().equals(Result.IGNORED.toString())
				&& !Boolean.parseBoolean(node.attributes().get("assumptionFailure"))).count(), tree.toString());
	}

	private static Stream<Node> descendants(Node node) {
		return Stream.concat(Stream.of(node), node.children().stream().flatMap(BestEffortAssumptionExecutionTest::descendants));
	}

	private void exportSources(String scenario, List<ICompilationUnit> units) throws Exception {
		String directory= System.getenv("SANDBOX_ASSUMPTION_EVIDENCE");
		if (directory != null) {
			Path target= Files.createDirectories(Path.of(directory, scenario));
			for (ICompilationUnit unit : units) Files.writeString(target.resolve(unit.getElementName()), unit.getSource());
			if (scenario.equals("binary")) {
				Files.createDirectories(target.resolve("assumptions"));
				Files.copy(binaryClasses.resolve("assumptions/Base.class"), target.resolve("assumptions/Base.class"));
				Files.copy(binaryClasses.resolve("Base.java.before"), target.resolve("Base.java.before"));
			}
		}
	}
}
