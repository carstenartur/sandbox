/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.wizard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** A compiling replacement must still call the API named by the rule. */
public class QualifiedReplacementBindingTest {

	@RegisterExtension
	EclipseJava22 context= new EclipseJava22();

	@Test
	public void parameterShadowDoesNotChangeTheReplacementMethodBinding() throws Exception {
		assertQualifiedApiBinding("void value(Shadow Collections) { old(); }");
	}

	@Test
	public void fieldShadowDoesNotChangeTheReplacementMethodBinding() throws Exception {
		assertQualifiedApiBinding("Shadow Collections = new Shadow(); void value() { old(); }");
	}

	private void assertQualifiedApiBinding(String member) throws Exception {
		String source= "package trial; public class Sample { "
				+ "static class Shadow { Object emptyList() { return \"shadow\"; } } "
				+ member + " void old() {} void consume(Object value) {} }";
		ICompilationUnit unit= context.getSourceFolder().createPackageFragment("trial", false, null)
				.createCompilationUnit("Sample.java", source, true, null);
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: qualified-api>\nold();\n=> consume(java.util.Collections.emptyList());\n;;\n");
		var status= refactoring.checkInitialConditions(null);
		assertFalse(status.hasFatalError(), status::toString);
		CompositeChange change= (CompositeChange) refactoring.createChange(null);
		assertEquals(1, change.getChildren().length, refactoring.getExplanation());
		String preview= ((CompilationUnitChange) change.getChildren()[0]).getPreviewContent(null);

		ICompilationUnit workingCopy= unit.getWorkingCopy(null);
		try {
			workingCopy.getBuffer().setContents(preview);
			ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
			parser.setSource(workingCopy);
			parser.setResolveBindings(true);
			CompilationUnit parsed= (CompilationUnit) parser.createAST(null);
			assertEquals(0, Arrays.stream(parsed.getProblems()).filter(IProblem::isError).count(), preview);
			List<IMethodBinding> calls= new ArrayList<>();
			parsed.accept(new ASTVisitor() {
				@Override
				public boolean visit(MethodInvocation invocation) {
					if ("emptyList".equals(invocation.getName().getIdentifier())) {
						IMethodBinding binding= invocation.resolveMethodBinding();
						assertNotNull(binding, preview);
						calls.add(binding);
					}
					return true;
				}
			});
			assertEquals(1, calls.size(), preview);
			assertEquals("java.util.Collections", calls.getFirst().getDeclaringClass().getQualifiedName(), preview);
		} finally {
			workingCopy.discardWorkingCopy();
			change.dispose();
		}
	}
}
