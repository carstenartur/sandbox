/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.wizard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.triggerpattern.eclipse.ProjectRuleExamples;
import org.sandbox.jdt.triggerpattern.api.RuleExampleSuite;
import org.sandbox.jdt.triggerpattern.api.RuleExampleSuite.Example;
import org.sandbox.jdt.triggerpattern.api.RuleExampleVerifier;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Uses a real source folder and sibling type to exercise project-relative evidence. */
class RuleExamplesProjectTest {

	@RegisterExtension
	EclipseJava22 context = new EclipseJava22();

	@Test
	void relativeSourcePathResolvesTheActualWorkspaceUnitAndSiblingType() throws Exception {
		ICompilationUnit unit = createUnit();
		var project = context.getJavaProject();
		String relative = unit.getResource().getProjectRelativePath().toPortableString();
		var ast = ProjectRuleExamples.parseSource(project, project.getOptions(true), relative,
				unit.getSource(), new NullProgressMonitor());
		assertTrue(ast.getAST().hasResolvedBindings());
		assertFalse(Arrays.stream(ast.getProblems()).anyMatch(problem -> problem.isError()),
				() -> Arrays.toString(ast.getProblems()));
		var type = (TypeDeclaration) ast.types().getFirst();
		assertEquals("examples.Sample", type.resolveBinding().getQualifiedName());
		assertEquals(unit.findPrimaryType(), type.resolveBinding().getJavaElement());
	}

	@Test
	void fullUnitExamplesReplayWithProjectBindingsAndLeaveTheSourceUntouched() throws Exception {
		ICompilationUnit unit = createUnit();
		var project = context.getJavaProject();
		String relative = unit.getResource().getProjectRelativePath().toPortableString();
		String before = unit.getSource();
		String negative = before.replace("1 + 2", "4");
		var suite = new RuleExampleSuite(RuleExampleSuite.CURRENT_VERSION, List.of(
				new Example("Positive", relative, before, before.replace("1 + 2", "3"), true),
				new Example("Counterexample", relative, negative, negative, false)));
		Map<String, String> options = project.getOptions(true);
		var verifier = new RuleExampleVerifier(options, (name, source) -> ProjectRuleExamples.parseSource(
				project, options, name, source, new NullProgressMonitor()));
		var report = verifier.verify("<!binding-policy: required>\n1 + 2\n=> 3\n;;\n", suite);
		assertTrue(report.passed(), report.toString());
		assertEquals(before, unit.getSource());
	}

	@Test
	void generatedRuleMustPassCurrentExamplesBeforeEitherFileIsWritten() throws Exception {
		ICompilationUnit unit = createUnit();
		var destination = context.getJavaProject().getProject();
		String path = unit.getResource().getProjectRelativePath().toPortableString();
		String before = unit.getSource();
		String unchanged = before.replace("1 + 2", "4");
		Example positive = new Example("Positive", path, before, before.replace("1 + 2", "3"), true);
		var positives = new RuleExampleSuite(RuleExampleSuite.CURRENT_VERSION, List.of(positive));
		var both = new RuleExampleSuite(RuleExampleSuite.CURRENT_VERSION, List.of(positive,
				new Example("Counterexample", path, unchanged, unchanged, false)));
		String rule = "<!binding-policy: required>\n1 + 2\n=> 3\n;;\n";
		String file = "generated.sandbox-hint";
		var monitor = new NullProgressMonitor();
		assertThrows(CoreException.class, () -> HintFileCreation.create(destination, file, rule, null, true, monitor));
		assertThrows(CoreException.class, () -> HintFileCreation.create(destination, file, rule, positives, true, monitor));
		assertThrows(CoreException.class, () -> HintFileCreation.create(destination, file,
				rule.replace("=> 3", "=> 5"), both, true, monitor));
		assertFalse(destination.getFile(file).exists());
		assertFalse(destination.getFile(file + ".examples.json").exists());
		HintFileCreation.create(destination, file, rule, both, true, monitor);
		assertTrue(destination.getFile(file).exists());
		assertTrue(destination.getFile(file + ".examples.json").exists());
		assertEquals(before, unit.getSource());
	}

	@Test
	void absoluteAndEscapingUnitNamesAreRejectedBeforeParsing() {
		var project = context.getJavaProject();
		for (String invalid : List.of("/Other/src/Sample.java", "../Sample.java", "src/../Sample.java",
				"src//Sample.java", "src/./Sample.java", "src\\Sample.java", "C:/Sample.java")) {
			assertThrows(IllegalArgumentException.class, () -> ProjectRuleExamples.parseSource(project,
					project.getOptions(true), invalid, "class Sample {}", new NullProgressMonitor()), invalid);
		}
	}

	@Test
	void generatedJUnitTestReplaysSavedEvidenceAndFailsOnChangedRule(@TempDir Path classes) throws Exception {
		ICompilationUnit unit = createUnit();
		var project = context.getJavaProject().getProject();
		String before = unit.getSource();
		String relative = unit.getResource().getProjectRelativePath().toPortableString();
		String negative = before.replace("1 + 2", "4");
		var suite = new RuleExampleSuite(RuleExampleSuite.CURRENT_VERSION, List.of(
				new Example("Addition", relative, before, before.replace("1 + 2", "3"), true),
				new Example("Counterexample", relative, negative, negative, false)));
		String name = "verified rule.sandbox-hint";
		String rule = "<!binding-policy: required>\n1 + 2\n=> 3\n;;\n";
		HintFileCreation.create(project, name, rule, suite, new NullProgressMonitor());
		var hint = project.getFile(name);
		Path file = Files.writeString(classes.resolve("RuleRegressionTest.java"),
				RuleRegressionTestSource.forHint(hint.getFullPath().toPortableString()), StandardCharsets.UTF_8);
		var classpath = new java.util.ArrayList<String>();
		for (var bundle : Platform.getBundle("sandbox_triggerpattern").getBundleContext().getBundles()) {
			var location = FileLocator.getBundleFileLocation(bundle);
			if (location.isPresent()) {
				classpath.add(location.get().getAbsolutePath());
			}
		}
		assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
				"-proc:none", "-cp", String.join(java.io.File.pathSeparator, classpath), "-d", classes.toString(), file.toString()));
		try (var loader = new URLClassLoader(new java.net.URL[] { classes.toUri().toURL() }, getClass().getClassLoader())) {
			var test = loader.loadClass("RuleRegressionTest");
			var constructor = test.getDeclaredConstructor();
			constructor.setAccessible(true);
			var method = test.getDeclaredMethod("replaysSavedExamples");
			method.setAccessible(true);
			method.invoke(constructor.newInstance());
			try (var changed = new java.io.ByteArrayInputStream(
					rule.replace("=> 3", "=> 4").getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
				hint.setContents(changed, true, false, null);
			}
			var failed = assertThrows(InvocationTargetException.class, () -> method.invoke(constructor.newInstance()));
			assertTrue(failed.getCause() instanceof AssertionError, failed.toString());
		}
	}

	private ICompilationUnit createUnit() throws Exception {
		var pack = context.getSourceFolder().createPackageFragment("examples", false, null);
		pack.createCompilationUnit("Helper.java", "package examples; class Helper { static void touch() {} }", false, null);
		return pack.createCompilationUnit("Sample.java",
				"package examples; public class Sample { int value() { Helper.touch(); return 1 + 2; } }", false, null);
	}
}
