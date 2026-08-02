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
package org.sandbox.jdt.ui.tests.quickfix;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Verifies a linear multi-stage map/filter conversion. */
class LoopRefactoringComplexChainTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	/**
	 * Tests a transformation with multiple named intermediate values.
	 *
	 * <p>The loop model must prove and render the complete
	 * {@code map -> map -> filter} chain without leaking an earlier pipeline
	 * variable.</p>
	 *
	 * @see FunctionalLoopComplexPatternTest#test_MergingOperations()
	 */
	@Test
	@DisplayName("Complex chain: multiple transformations")
	void testComplexChain() throws CoreException {
		String input= """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<Integer> numbers) {
						for (Integer num : numbers) {
							int doubled = num * 2;
							int plusTen = doubled + 10;
							if (plusTen < 100) {
								System.out.println(plusTen);
							}
						}
					}
				}
				""";

		String expected= """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<Integer> numbers) {
						numbers.stream().map(num -> num * 2).map(doubled -> doubled + 10).filter(plusTen -> (plusTen < 100))
								.forEachOrdered(plusTen -> System.out.println(plusTen));
					}
				}
				""";

		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("MyTest.java", input, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] { expected }, null);
	}

	/**
	 * Keeps the declarations together when the terminal still needs an earlier
	 * intermediate value that would no longer exist after successive map operations.
	 */
	@Test
	@DisplayName("Complex chain: stale intermediate falls back to block forEach")
	void testStaleIntermediateVariableFallsBackToBlockForEach() throws CoreException {
		String input= """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<Integer> numbers) {
						for (Integer num : numbers) {
							int doubled = num * 2;
							int plusTen = doubled + 10;
							System.out.println(doubled);
						}
					}
				}
				""";

		String expected= """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<Integer> numbers) {
						numbers.forEach(num -> {
							int doubled = num * 2;
							int plusTen = doubled + 10;
							System.out.println(doubled);
						});
					}
				}
				""";

		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("MyTest.java", input, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] { expected }, null);
	}
}
