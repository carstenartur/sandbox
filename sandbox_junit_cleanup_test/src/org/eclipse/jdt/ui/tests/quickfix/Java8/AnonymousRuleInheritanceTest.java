/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the
 * Eclipse Public License 2.0: https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.JUnitCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** The anonymous ProjectTestSetup shape from JDT UI must retain its inherited Rule contract. */
@SuppressWarnings("nls")
class AnonymousRuleInheritanceTest {

	@RegisterExtension
	final EclipseJava17 context= new EclipseJava17();
	private IPackageFragment pack;

	@BeforeEach
	void setup() throws Exception {
		pack= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH)
				.createPackageFragment("test", false, null);
		AbstractEclipseJava.addToClasspath(context.getJavaProject(),
				JavaCore.newContainerEntry(JUnitCore.JUNIT5_CONTAINER_PATH));
		var options= context.getJavaProject().getOptions(false);
		options.put(JavaCore.COMPILER_PB_MAX_PER_UNIT, "0");
		options.put(JavaCore.COMPILER_PB_MISSING_JAVADOC_COMMENTS_OVERRIDING, JavaCore.ENABLED);
		options.put(JavaCore.COMPILER_PB_MISSING_JAVADOC_COMMENTS_VISIBILITY, JavaCore.PUBLIC);
		context.getJavaProject().setOptions(options);
	}

	static Stream<Arguments> inheritedRules() {
		return Stream.of(Arguments.of("Rule", "", false), Arguments.of("ClassRule", "static ", false),
				Arguments.of("Rule", "", true), Arguments.of("ClassRule", "static ", true));
	}

	@ParameterizedTest
	@MethodSource("inheritedRules")
	void preservesInheritedLifecycleAndHelperMethods(String annotation, String fieldModifier, boolean bestEffort) throws Exception {
		String baseSource= """
				package test;
				import org.junit.rules.ExternalResource;
				public class ProjectSetup extends ExternalResource {
					private Object project;
					@Override protected void before() throws Throwable { project = new Object(); }
					@Override protected void after() { project = null; }
					public Object getProject() { return project; }
				}
				""";
		String subjectSource= """
				package test;
				import org.junit.$annotation;
				import org.junit.Assert;
				import org.junit.Test;
				public class Subject {
					private static Object observed;
					@$annotation public $modifier ProjectSetup resource = new ProjectSetup() {
						@Override protected void before() throws Throwable {
							super.before();
							observed = getProject();
						}
					};
					@Test public void checksResource() { Assert.assertNotNull(observed); }
				}
				""".replace("$annotation", annotation).replace("$modifier", fieldModifier);
		ICompilationUnit base= pack.createCompilationUnit("ProjectSetup.java", baseSource, false, null);
		ICompilationUnit subject= pack.createCompilationUnit("Subject.java", subjectSource, false, null);
		List<String> generated= applyAndUndo(bestEffort, base, subject);
		assertTrue(generated.get(0).contains("extends ExternalResource"), generated.toString());
		assertTrue(generated.get(0).contains("protected void before()"), generated.toString());
		assertTrue(generated.get(0).contains("protected void after()"), generated.toString());
		assertTrue(generated.get(1).contains("import org.junit." + annotation + ";"), generated.toString());
		assertTrue(generated.get(1).contains("import org.junit.Test;"), generated.toString());
		assertTrue(generated.get(1).contains("super.before();"), generated.toString());
		assertTrue(generated.get(1).contains("getProject()"), generated.toString());
		assertFalse(generated.get(1).contains("org.junit.jupiter.api.Test"), generated.toString());
		if (!bestEffort) assertEquals(List.of(baseSource, subjectSource), generated);
	}

	@ParameterizedTest
	@ValueSource(strings= { "Rule", "ClassRule" })
	void stillMigratesDirectAnonymousResources(String annotation) throws Exception {
		String source= """
				package test;
				import org.junit.$annotation;
				import org.junit.Test;
				import org.junit.rules.ExternalResource;
				public class Subject {
					private static int events;
					@$annotation public $modifier ExternalResource resource = new ExternalResource() {
						@Override protected void before() { events++; }
						@Override protected void after() { events--; }
					};
					@Test public void checksResource() { System.out.println(events); }
				}
				""".replace("$annotation", annotation).replace("$modifier", "ClassRule".equals(annotation) ? "static " : "");
		ICompilationUnit subject= pack.createCompilationUnit("Subject.java", source, false, null);
		String generated= applyAndUndo(false, subject).getFirst();
		assertTrue(generated.contains("@RegisterExtension"), generated);
		assertTrue(generated.contains("org.junit.jupiter.api.Test"), generated);
		assertTrue(generated.contains("events++"), generated);
		assertTrue(generated.contains("events--"), generated);
	}

	@ParameterizedTest
	@ValueSource(booleans= { true, false })
	void documentsNewPublicCallbackContractsWhenRequired(boolean documentOverrides) throws Exception {
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_MISSING_JAVADOC_COMMENTS_OVERRIDING,
				documentOverrides ? JavaCore.ENABLED : JavaCore.DISABLED);
		ICompilationUnit resource= pack.createCompilationUnit("Resource.java", """
				package test;
				import org.junit.rules.ExternalResource;
				public class Resource extends ExternalResource {
					private int events;
					@Override protected void before() { events++; }
					@Override protected void after() { events--; }
					public int events() { return events; }
				}
				""", false, null);
		String generated= applyAndUndo(false, resource).getFirst();
		assertTrue(generated.contains("beforeEach"), generated);
		assertTrue(generated.contains("afterEach"), generated);
		assertEquals(documentOverrides, generated.contains("{@inheritDoc}"), generated);
	}

	private List<String> applyAndUndo(boolean bestEffort, ICompilationUnit... units) throws Exception {
		List<String> original= new ArrayList<>();
		for (ICompilationUnit unit : units) {
			original.add(unit.getSource());
			CompilationUnit ast= parse(unit);
			assertTrue(Arrays.stream(ast.getProblems()).noneMatch(IProblem::isError),
					() -> Arrays.toString(ast.getProblems()));
		}
		var diagnostics= CompilerDiagnosticAssertions.capture(units);
		JUnitCleanUpCore cleanup= new JUnitCleanUpCore(Map.of(
				MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT, CleanUpOptions.TRUE,
				"cleanup.junitcleanup_best_effort", Boolean.toString(bestEffort)));
		CompositeChange change= new CompositeChange("Anonymous Rule contract");
		NullProgressMonitor monitor= new NullProgressMonitor();
		try {
			var status= cleanup.checkPreConditions(context.getJavaProject(), units, null);
			assertFalse(status.hasFatalError(), status.toString());
			for (ICompilationUnit unit : units) {
				var fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
				if (fix != null) change.add(fix.createChange(null));
			}
			change.initializeValidationData(monitor);
			var undo= change.perform(monitor);
			assertNotNull(undo);
			try {
				diagnostics.assertNoNewDiagnostics();
				List<String> generated= new ArrayList<>();
				for (ICompilationUnit unit : units) generated.add(unit.getSource());
				return generated;
			} finally {
				var redo= undo.perform(monitor);
				if (redo != null) redo.dispose();
				undo.dispose();
			}
		} finally {
			change.dispose();
			cleanup.checkPostConditions(null);
			for (int index= 0; index < units.length; index++) assertEquals(original.get(index), units[index].getSource());
			diagnostics.assertRestored();
		}
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}
}
