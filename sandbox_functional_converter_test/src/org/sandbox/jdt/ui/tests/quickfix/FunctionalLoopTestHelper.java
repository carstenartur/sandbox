/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;

/**
 * Helper utilities for functional loop conversion tests.
 * 
 * <p>
 * This class provides common functionality used across all functional loop
 * conversion test classes, reducing code duplication and providing consistent
 * test execution patterns.
 * </p>
 * 
 * @see org.sandbox.jdt.internal.ui.fix.UseFunctionalLoopCleanUp
 */
public class FunctionalLoopTestHelper {

	/**
	 * Creates a test compilation unit from source code.
	 * 
	 * @param context     the Eclipse Java test context
	 * @param packageName the package name for the compilation unit
	 * @param className   the class name (without .java extension)
	 * @param source      the Java source code
	 * @return the created compilation unit
	 * @throws CoreException if the compilation unit cannot be created
	 */
	public static ICompilationUnit createTestUnit(AbstractEclipseJava context, String packageName, String className,
			String source) throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment(packageName, false, null);
		return pack.createCompilationUnit(className + ".java", source, false, null);
	}

	/**
	 * Parses AST and reports any problems (for debugging).
	 * 
	 * <p>
	 * This method is useful for debugging test failures. It parses the compilation
	 * unit and prints any AST problems to System.err.
	 * </p>
	 * 
	 * @param cu the compilation unit to check
	 */
	public static void reportASTProblems(ICompilationUnit cu) {
		ASTParser parser = ASTParser.newParser(AST.JLS22);
		parser.setSource(cu);
		parser.setResolveBindings(true);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

		IProblem[] problems = astRoot.getProblems();
		if (problems.length > 0) {
			System.err.println("AST Problems found in: " + cu.getElementName());
			for (IProblem problem : problems) {
				System.err.println("  " + problem.toString());
			}
		}
	}

	/**
	 * Asserts that a cleanup produces the expected refactoring.
	 * 
	 * @param context  the Eclipse Java test context
	 * @param cu       the compilation unit to refactor
	 * @param expected the expected source code after refactoring
	 * @throws CoreException if the refactoring fails
	 */
	public static void assertCleanup(AbstractEclipseJava context, ICompilationUnit cu, String expected)
			throws CoreException {
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Asserts that a cleanup does not change the source code.
	 * 
	 * @param context the Eclipse Java test context
	 * @param cu      the compilation unit to check
	 * @throws CoreException if the check fails
	 */
	public static void assertNoChange(AbstractEclipseJava context, ICompilationUnit cu) throws CoreException {
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
