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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Verifies that observations before a loop prevent accumulator replacement. */
class ConcreteAccumulatorObservationSafetyTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	void aliasBeforeLoopPreservesTheOriginalAccumulator() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					ArrayList<String> copy(List<String> source) {
						ArrayList<String> result = new ArrayList<>();
						ArrayList<String> alias = result;
						for (String item : source) {
							result.add(item);
						}
						return alias;
					}
				}
				""", """
				package test;
				import java.util.*;
				class E {
					ArrayList<String> copy(List<String> source) {
						ArrayList<String> result = new ArrayList<>();
						ArrayList<String> alias = result;
						source.forEach(item -> result.add(item));
						return alias;
					}
				}
				""");
	}

	@Test
	void readBeforeLoopPreservesTheOriginalAccumulator() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					ArrayList<String> copy(List<String> source) {
						ArrayList<String> result = new ArrayList<>();
						System.out.println(result.size());
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				""", """
				package test;
				import java.util.*;
				class E {
					ArrayList<String> copy(List<String> source) {
						ArrayList<String> result = new ArrayList<>();
						System.out.println(result.size());
						source.forEach(item -> result.add(item));
						return result;
					}
				}
				""");
	}

	private void assertExpected(String source, String expected) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("E.java", source, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] { expected }, null);
	}
}
