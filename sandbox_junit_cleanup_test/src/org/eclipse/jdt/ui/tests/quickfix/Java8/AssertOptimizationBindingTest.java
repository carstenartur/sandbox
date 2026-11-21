/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.junit.JUnitCore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Regressions for Jupiter assertion shapes found in the pinned SWT tests. */
public class AssertOptimizationBindingTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();
	private IPackageFragment pack;

	@BeforeEach
	void setup() throws CoreException {
		pack= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH)
				.createPackageFragment("test", false, null); //$NON-NLS-1$
	}

	static Stream<Arguments> renamedAssertions() {
		return Stream.of(
				Arguments.of("assertTrue(left == right, \"identity\")", "assertSame(left, right, \"identity\")"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("assertFalse(left == right)", "assertNotSame(left, right)"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("assertTrue(value != 0)", "assertNotEquals(value, 0)"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("assertTrue(!condition)", "assertFalse(condition)"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("assertFalse(!condition)", "assertTrue(condition)"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("assertTrue(left == null, \"message\")", "assertNull(left, \"message\")"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("assertTrue(left == null, () -> \"message\")", "assertNull(left, () -> \"message\")"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("assertFalse(left == null, \"message\")", "assertNotNull(left, \"message\")"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("assertTrue(left.equals(right))", "assertEquals(right, left)")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@ParameterizedTest
	@MethodSource("renamedAssertions") //$NON-NLS-1$
	void renamedStaticAssertionsCompileAndKeepTheirArguments(String original, String expected) throws CoreException {
		ICompilationUnit unit= unit(original.substring(0, original.indexOf('(')), original, ""); //$NON-NLS-1$
		assertInvocation(apply(unit), expected);
	}

	@Test
	void preservesStringExpectedValueAndTrailingMessage() throws CoreException {
		ICompilationUnit unit= unit("assertEquals", //$NON-NLS-1$
				"assertEquals(\"expected\", String.valueOf(value), \"message\")", ""); //$NON-NLS-1$ //$NON-NLS-2$
		String original= unit.getSource();
		apply(unit);
		assertEquals(original, unit.getSource());
	}

	@Test
	void reordersOnlyExpectedAndActualForStringValues() throws CoreException {
		ICompilationUnit unit= unit("assertEquals", //$NON-NLS-1$
				"assertEquals(String.valueOf(value), \"expected\", \"message\")", ""); //$NON-NLS-1$ //$NON-NLS-2$
		assertInvocation(apply(unit), "assertEquals(\"expected\", String.valueOf(value), \"message\")"); //$NON-NLS-1$
	}

	@Test
	void keepsAnOriginalStaticImportWhenAnotherCallStillNeedsIt() throws CoreException {
		ICompilationUnit unit= unit("assertTrue", "assertTrue(left == right); assertTrue(condition)", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals(List.of("assertSame", "assertTrue"), //$NON-NLS-1$ //$NON-NLS-2$
				assertionCalls(apply(unit)).stream().map(call -> call.getName().getIdentifier()).toList());
	}

	@Test
	void generatedAssertionDoesNotBindToALocalMethodWithTheSameName() throws CoreException {
		ICompilationUnit unit= unit("assertTrue", "assertTrue(left == right)", //$NON-NLS-1$ //$NON-NLS-2$
				"void assertSame(Object first, Object second) {}\n"); //$NON-NLS-1$
		assertInvocation(apply(unit), "assertSame(left, right)"); //$NON-NLS-1$
	}

	private ICompilationUnit unit(String importedMethod, String invocation, String extraMember) throws CoreException {
		String source= """
				package test;
				import static org.junit.jupiter.api.Assertions.$method;
				public class Example {
					void run(Object left, Object right, int value, boolean condition) {
						$invocation;
					}
					$member
				}
				""".replace("$method", importedMethod).replace("$invocation", invocation) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				.replace("$member", extraMember); //$NON-NLS-1$
		return pack.createCompilationUnit("Example.java", source, false, null); //$NON-NLS-1$
	}

	private CompilationUnit apply(ICompilationUnit unit) throws CoreException {
		CompilationUnit root= parse(unit);
		assertTrue(Arrays.stream(root.getProblems()).noneMatch(problem -> problem.isError()),
				() -> Arrays.toString(root.getProblems()));
		var operations= new LinkedHashSet<CompilationUnitRewriteOperationWithSourceRange>();
		JUnitCleanUpFixCore.ASSERT_OPTIMIZATION.findOperations(root, operations, new HashSet<>());
		if (!operations.isEmpty()) {
			var fix= new CompilationUnitRewriteOperationsFixCore("Optimize assertions", root, //$NON-NLS-1$
					operations.toArray(CompilationUnitRewriteOperationWithSourceRange[]::new));
			CompilerDiagnosticAssertions.performAndCheck(fix.createChange(null), new NullProgressMonitor(), unit);
		}
		return parse(unit);
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}

	private static List<MethodInvocation> assertionCalls(CompilationUnit root) {
		List<MethodInvocation> calls= new ArrayList<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				IMethodBinding binding= node.resolveMethodBinding();
				if (binding != null && "org.junit.jupiter.api.Assertions".equals( //$NON-NLS-1$
						binding.getDeclaringClass().getQualifiedName())) {
					calls.add(node);
				}
				return true;
			}
		});
		return calls;
	}

	private static void assertInvocation(CompilationUnit root, String expected) {
		List<MethodInvocation> calls= assertionCalls(root);
		assertEquals(1, calls.size(), root.toString());
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_EXPRESSION);
		parser.setSource(expected.toCharArray());
		MethodInvocation expectedCall= (MethodInvocation) parser.createAST(null);
		MethodInvocation actual= calls.getFirst();
		assertEquals(expectedCall.getName().getIdentifier(), actual.getName().getIdentifier());
		assertEquals(expectedCall.arguments().size(), actual.arguments().size());
		for (int index= 0; index < expectedCall.arguments().size(); index++) {
			assertTrue(((ASTNode) actual.arguments().get(index)).subtreeMatch(new ASTMatcher(), expectedCall.arguments().get(index)),
					actual.toString());
		}
	}
}
