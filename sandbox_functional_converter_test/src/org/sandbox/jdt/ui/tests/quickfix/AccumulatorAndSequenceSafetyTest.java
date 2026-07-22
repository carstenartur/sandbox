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

/** Regression tests for accumulator type and multi-loop sequencing safety. */
public class AccumulatorAndSequenceSafetyTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	void enhancedForDoesNotReplaceConcreteListAccumulator() throws CoreException {
		assertNoChange("""
				package test;
				import java.util.*;
				class E {
					ArrayList<String> copy(List<String> source) {
						ArrayList<String> result = new ArrayList<>();
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				""");
	}

	@Test
	void enhancedForDoesNotReplaceConcreteSetAccumulator() throws CoreException {
		assertNoChange("""
				package test;
				import java.util.*;
				class E {
					TreeSet<String> copy(List<String> source) {
						TreeSet<String> result = new TreeSet<>();
						for (String item : source) {
							result.add(item);
						}
						return result;
					}
				}
				""");
	}

	@Test
	void iteratorDoesNotReplaceConcreteListAccumulator() throws CoreException {
		assertNoChange("""
				package test;
				import java.util.*;
				class E {
					ArrayList<String> copy(List<String> source) {
						ArrayList<String> result = new ArrayList<>();
						Iterator<String> iterator = source.iterator();
						while (iterator.hasNext()) {
							String item = iterator.next();
							result.add(item);
						}
						return result;
					}
				}
				""");
	}

	@Test
	void iteratorDoesNotReplaceConcreteSetAccumulator() throws CoreException {
		assertNoChange("""
				package test;
				import java.util.*;
				class E {
					TreeSet<String> copy(List<String> source) {
						TreeSet<String> result = new TreeSet<>();
						Iterator<String> iterator = source.iterator();
						while (iterator.hasNext()) {
							String item = iterator.next();
							result.add(item);
						}
						return result;
					}
				}
				""");
	}

	@Test
	void consecutiveLoopsRemainSequentialAfterFreshAccumulatorMerge() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					List<String> copy(List<String> first, List<String> second) {
						List<String> result = new ArrayList<>();
						for (String item : first) {
							result.add(item);
						}
						for (String item : second) {
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
					List<String> copy(List<String> first, List<String> second) {
						List<String> result = first.stream().collect(Collectors.toList());
						second.forEach(item -> result.add(item));
						return result;
					}
				}
				""");
	}

	@Test
	void consecutiveLoopsPreserveExistingTargetIdentity() throws CoreException {
		assertExpected("""
				package test;
				import java.util.*;
				class E {
					void append(List<String> target, List<String> first, List<String> second) {
						for (String item : first) {
							target.add(item);
						}
						for (String item : second) {
							target.add(item);
						}
					}
				}
				""", """
				package test;
				import java.util.*;
				class E {
					void append(List<String> target, List<String> first, List<String> second) {
						first.forEach(item -> target.add(item));
						second.forEach(item -> target.add(item));
					}
				}
				""");
	}

	private void assertNoChange(String source) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("E.java", source, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	private void assertExpected(String source, String expected) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("E.java", source, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] { expected }, null);
	}
}
