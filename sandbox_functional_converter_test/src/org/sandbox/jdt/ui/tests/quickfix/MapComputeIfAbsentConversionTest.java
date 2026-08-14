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

/** Regression coverage for issue #1476. */
public class MapComputeIfAbsentConversionTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	void differentMapDoesNotBlockForEachConversion() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("E.java", //$NON-NLS-1$
				"""
				package test;

				import java.util.List;
				import java.util.Map;

				public class E {
					public void convert(List<String> list, Map<String, String> map) {
						for (String item : list) {
							map.computeIfAbsent(item, key -> "value");
						}
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] {
				"""
				package test;

				import java.util.List;
				import java.util.Map;

				public class E {
					public void convert(List<String> list, Map<String, String> map) {
						list.forEach(item -> map.computeIfAbsent(item, key -> "value"));
					}
				}
				""" }, null);
	}

	@Test
	void backingMapMutationStillBlocksMapViewConversion() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("E.java", //$NON-NLS-1$
				"""
				package test;

				import java.util.Map;

				public class E {
					public void retain(Map<String, String> map) {
						for (Map.Entry<String, String> entry : map.entrySet()) {
							map.computeIfAbsent(entry.getKey(), key -> "value");
						}
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}
}
