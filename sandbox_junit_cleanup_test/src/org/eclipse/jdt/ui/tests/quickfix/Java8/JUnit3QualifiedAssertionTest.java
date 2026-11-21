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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.ltk.core.refactoring.Change;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;
import org.sandbox.jdt.ui.tests.quickfix.rules.JUnitMigrationFixtureClasspath;

/** Qualifier shapes used by the pinned JDT Core JUnit 3 tests. */
public class JUnit3QualifiedAssertionTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();
	private IPackageFragment pack;

	@BeforeEach
	void setup() throws CoreException {
		pack= JUnitMigrationFixtureClasspath.createJUnit3And5Root(context)
				.createPackageFragment("test", false, null); //$NON-NLS-1$
	}

	static Stream<Arguments> qualifiers() {
		return Stream.of(
				Arguments.of("", "", false, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("TestCase.", "", false, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("junit.framework.TestCase.", "", false, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("junit.framework.Assert.", "", false, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("Assert.", "import junit.framework.Assert;", false, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("Assert.", "import junit.framework.Assert;", true, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("junit.framework.Assert.", "", true, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("Assert.", "import junit.framework.Assert;", false, true)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@ParameterizedTest
	@MethodSource("qualifiers") //$NON-NLS-1$
	void replacesTheWholeQualifierAndPreservesJupiterBindings(String qualifier, String imports,
			boolean collision, boolean retainImport) throws CoreException {
		String original= """
				package test;
				import junit.framework.TestCase;
				%s
				public class Example extends TestCase {
					public void testValue() {
						%sassertEquals("message", 2, 1 + 1);
					}
					%s
					%s
				}
				""".formatted(imports, qualifier,
				collision ? "static class Assertions { static void assertEquals(int e, int a, String m) {} }" : "", //$NON-NLS-1$ //$NON-NLS-2$
				retainImport ? "public Class<?> assertionType() { return Assert.class; }" : ""); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit unit= pack.createCompilationUnit("Example.java", original, false, null); //$NON-NLS-1$
		CompilationUnit before= parse(unit);
		assertTrue(Arrays.stream(before.getProblems()).noneMatch(problem -> problem.isError()),
				() -> Arrays.toString(before.getProblems()));
		var operations= operations(before);
		assertFalse(operations.isEmpty(), original);
		var fix= new CompilationUnitRewriteOperationsFixCore("Migrate JUnit 3", before, //$NON-NLS-1$
				operations.toArray(CompilationUnitRewriteOperationWithSourceRange[]::new));
		Change undo= CompilerDiagnosticAssertions.performAndCheck(
				fix.createChange(null), new NullProgressMonitor(), unit);
		CompilationUnit after= parse(unit);
		assertNull(((TypeDeclaration) after.types().getFirst()).getSuperclassType());
		List<MethodInvocation> assertions= new ArrayList<>();
		after.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation invocation) {
				if ("assertEquals".equals(invocation.getName().getIdentifier())) { //$NON-NLS-1$
					assertions.add(invocation);
				}
				return true;
			}
		});
		assertEquals(1, assertions.size(), unit.getSource());
		MethodInvocation assertion= assertions.getFirst();
		assertNotNull(assertion.resolveMethodBinding(), unit.getSource());
		assertEquals("org.junit.jupiter.api.Assertions", //$NON-NLS-1$
				assertion.resolveMethodBinding().getDeclaringClass().getQualifiedName());
		List<?> arguments= assertion.arguments();
		assertEquals(List.of("2", "1 + 1", "\"message\""), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				arguments.stream().map(Object::toString).toList());
		assertEquals(retainImport, unit.getSource().contains("import junit.framework.Assert;")); //$NON-NLS-1$
		assertTrue(operations(after).isEmpty(), "Migration must be idempotent"); //$NON-NLS-1$
		assertNotNull(undo);
		undo.perform(new NullProgressMonitor());
		assertEquals(original, unit.getSource());
	}

	private static LinkedHashSet<CompilationUnitRewriteOperationWithSourceRange> operations(CompilationUnit root) {
		var operations= new LinkedHashSet<CompilationUnitRewriteOperationWithSourceRange>();
		JUnitCleanUpFixCore.TEST3.findOperations(root, operations, new HashSet<>());
		return operations;
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}
}
