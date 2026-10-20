/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.wizard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
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
		var ast = RuleExamplesDialog.parseSource(project, project.getOptions(true), relative,
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
		var verifier = new RuleExampleVerifier(options, (name, source) -> RuleExamplesDialog.parseSource(
				project, options, name, source, new NullProgressMonitor()));
		var report = verifier.verify("<!binding-policy: required>\n1 + 2\n=> 3\n;;\n", suite);
		assertTrue(report.passed(), report.toString());
		assertEquals(before, unit.getSource());
	}

	@Test
	void absoluteAndEscapingUnitNamesAreRejectedBeforeParsing() {
		var project = context.getJavaProject();
		for (String invalid : List.of("/Other/src/Sample.java", "../Sample.java", "src/../Sample.java",
				"src//Sample.java", "src/./Sample.java", "src\\Sample.java", "C:/Sample.java")) {
			assertThrows(IllegalArgumentException.class, () -> RuleExamplesDialog.parseSource(project,
					project.getOptions(true), invalid, "class Sample {}", new NullProgressMonitor()), invalid);
		}
	}

	private ICompilationUnit createUnit() throws Exception {
		var pack = context.getSourceFolder().createPackageFragment("examples", false, null);
		pack.createCompilationUnit("Helper.java", "package examples; class Helper { static void touch() {} }", false, null);
		return pack.createCompilationUnit("Sample.java",
				"package examples; public class Sample { int value() { Helper.touch(); return 1 + 2; } }", false, null);
	}
}
