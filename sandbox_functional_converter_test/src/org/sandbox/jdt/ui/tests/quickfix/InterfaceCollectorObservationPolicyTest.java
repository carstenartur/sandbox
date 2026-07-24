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

/** Verifies when an interface-declared accumulator requires its concrete factory. */
class InterfaceCollectorObservationPolicyTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	void deadInterfaceAccumulatorKeepsTheGeneralCollector() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					void copy(List<String> source) {
						List<String> result = new ArrayList<>();
						for (String item : source) {
							result.add(item);
						}
					}
				}
				""", """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					void copy(List<String> source) {
						List<String> result = source.stream().collect(Collectors.toList());
					}
				}
				""");
	}

	@Test
	void deadSetInterfacePreservesTreeSetInsertionChecks() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					void copy(List<Object> source) {
						Set<Object> result = new TreeSet<>();
						for (Object item : source) {
							result.add(item);
						}
					}
				}
				""", """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					void copy(List<Object> source) {
						Set<Object> result = source.stream().collect(Collectors.toCollection(java.util.TreeSet::new));
					}
				}
				""");
	}

	@Test
	void returnedInterfaceAccumulatorPreservesArrayList() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					List<String> copy(List<String> source) {
						List<String> result = new ArrayList<>();
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				""", """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					List<String> copy(List<String> source) {
						List<String> result = source.stream().collect(Collectors.toCollection(java.util.ArrayList::new));
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
