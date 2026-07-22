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

/** Verifies Java lambda-capture preconditions for enhanced-for conversions. */
public class LambdaCaptureSafetyTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	void existingTargetReassignedAfterLoopIsNotCaptured() throws CoreException {
		assertNoChange("""
				package test1;
				import java.util.*;
				class MyTest {
					void process(List<String> source, List<String> target) {
						for (String item : source) {
							target.add(item.toUpperCase());
						}
						target = new ArrayList<>();
						System.out.println(target);
					}
				}
				""");
	}

	@Test
	void additionalCollectCaptureReassignedAfterLoopBlocksConversion() throws CoreException {
		assertNoChange("""
				package test1;
				import java.util.*;
				class MyTest {
					void process(List<String> source) {
						String suffix = "!";
						List<String> target = new ArrayList<>();
						for (String item : source) {
							target.add(item + suffix);
						}
						suffix = "?";
						System.out.println(target);
					}
				}
				""");
	}

	@Test
	void forEachCaptureReassignedAfterLoopBlocksConversion() throws CoreException {
		assertNoChange("""
				package test1;
				import java.util.*;
				class MyTest {
					void process(List<String> source) {
						String prefix = "item=";
						for (String item : source) {
							System.out.println(prefix + item);
						}
						prefix = "changed=";
					}
				}
				""");
	}

	private void assertNoChange(String source) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("MyTest.java", source, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}
}
