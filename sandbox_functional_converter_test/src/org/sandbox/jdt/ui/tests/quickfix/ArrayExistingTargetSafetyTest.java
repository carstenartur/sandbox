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

/** Verifies AST-preserving array conversion into existing collection targets. */
public class ArrayExistingTargetSafetyTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	public void parameterTargetPreservesIdentityAndFollowingStatements() throws CoreException {
		assertConversion("""
				package test1;

				import java.util.List;

				class MyTest {
					void process(String[] items, List<String> target) {
						System.out.println("before");
						for (String item : items) {
							target.add(item);
						}
						System.out.println(target);
					}
				}
				""", """
				package test1;

				import java.util.Arrays;
				import java.util.List;

				class MyTest {
					void process(String[] items, List<String> target) {
						System.out.println("before");
						Arrays.stream(items).forEachOrdered(item -> target.add(item));
						System.out.println(target);
					}
				}
				""");
	}

	@Test
	public void mappedCollectUsesOriginalArrayLoopBody() throws CoreException {
		assertConversion("""
				package test1;

				import java.util.List;

				class MyTest {
					void process(String[] items, List<String> target) {
						for (String item : items) {
							target.add(item.toUpperCase());
						}
					}
				}
				""", """
				package test1;

				import java.util.Arrays;
				import java.util.List;

				class MyTest {
					void process(String[] items, List<String> target) {
						Arrays.stream(items).forEachOrdered(item -> target.add(item.toUpperCase()));
					}
				}
				""");
	}

	@Test
	public void finalFieldTargetRemainsTheSameObject() throws CoreException {
		assertConversion("""
				package test1;

				import java.util.ArrayList;
				import java.util.List;

				class MyTest {
					private final List<String> target = new ArrayList<>();

					void process(String[] items) {
						for (String item : items) {
							target.add(item);
						}
					}
				}
				""", """
				package test1;

				import java.util.ArrayList;
				import java.util.Arrays;
				import java.util.List;

				class MyTest {
					private final List<String> target = new ArrayList<>();

					void process(String[] items) {
						Arrays.stream(items).forEachOrdered(item -> target.add(item));
					}
				}
				""");
	}

	@Test
	public void inlineCommentInOriginalBodyIsPreserved() throws CoreException {
		assertConversion("""
				package test1;

				import java.util.List;

				class MyTest {
					void process(String[] items, List<String> target) {
						for (String item : items) {
							target.add(/* keep mapping rationale */ item.toUpperCase());
						}
					}
				}
				""", """
				package test1;

				import java.util.Arrays;
				import java.util.List;

				class MyTest {
					void process(String[] items, List<String> target) {
						Arrays.stream(items).forEachOrdered(item -> target.add(/* keep mapping rationale */ item.toUpperCase()));
					}
				}
				""");
	}

	@Test
	public void nonEffectivelyFinalCaptureStillBlocksArrayConversion() throws CoreException {
		String input= """
				package test1;

				import java.util.List;

				class MyTest {
					void process(String[] items, List<String> target) {
						String suffix = "!";
						for (String item : items) {
							target.add(item + suffix);
						}
						suffix = "?";
					}
				}
				""";

		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("MyTest.java", input, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	private void assertConversion(String input, String expected) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("MyTest.java", input, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] { expected }, null);
	}
}
