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

/** Verifies that array conversions preserve writes to existing targets. */
public class ArrayExistingTargetSafetyTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	public void existingParameterTargetPreservesMappingCommentsAndSurroundingStatements() throws CoreException {
		assertExpected("""
				package test1;

				import java.util.List;

				class MyTest {
					void process(String[] items, List<String> target) {
						System.out.println("before");
						for (String item : items) {
							// Preserve the mapping and the target identity.
							target.add(item.toUpperCase());
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
						Arrays.stream(items).forEachOrdered(item -> {
							// Preserve the mapping and the target identity.
							target.add(item.toUpperCase());
						});
						System.out.println(target);
					}
				}
				""");
	}

	@Test
	public void existingFieldTargetKeepsObjectIdentity() throws CoreException {
		assertExpected("""
				package test1;

				import java.util.ArrayList;
				import java.util.List;

				class MyTest {
					private final List<String> target = new ArrayList<>();

					void append(String[] items) {
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

					void append(String[] items) {
						Arrays.stream(items).forEachOrdered(item -> target.add(item));
					}
				}
				""");
	}

	@Test
	public void supportedPrimitiveArrayWithMatchingLoopVariableConverts() throws CoreException {
		assertExpected("""
				package test1;

				import java.util.List;

				class MyTest {
					void append(int[] items, List<Integer> target) {
						for (int item : items) {
							target.add(item);
						}
					}
				}
				""", """
				package test1;

				import java.util.Arrays;
				import java.util.List;

				class MyTest {
					void append(int[] items, List<Integer> target) {
						Arrays.stream(items).forEachOrdered(item -> target.add(item));
					}
				}
				""");
	}

	@Test
	public void boxedLoopVariableOverPrimitiveArrayRemainsUnchanged() throws CoreException {
		assertNoChange("""
				package test1;

				import java.util.List;

				class MyTest {
					void append(int[] items, List<Integer> target) {
						for (Integer item : items) {
							target.add(item);
						}
					}
				}
				""");
	}

	@Test
	public void primitiveArrayWithoutArraysStreamOverloadRemainsUnchanged() throws CoreException {
		assertNoChange("""
				package test1;

				import java.util.List;

				class MyTest {
					void append(byte[] items, List<Byte> target) {
						for (byte item : items) {
							target.add(item);
						}
					}
				}
				""");
	}

	@Test
	public void targetReassignedAfterLoopRemainsUnchanged() throws CoreException {
		assertNoChange("""
				package test1;

				import java.util.ArrayList;
				import java.util.List;

				class MyTest {
					List<String> process(String[] items, List<String> target) {
						for (String item : items) {
							target.add(item);
						}
						target = new ArrayList<>();
						return target;
					}
				}
				""");
	}

	private void assertExpected(String source, String expected) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("MyTest.java", source, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] { expected }, null);
	}

	private void assertNoChange(String source) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("MyTest.java", source, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}
}
