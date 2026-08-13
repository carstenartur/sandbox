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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Regression tests for precedence between dedicated and generic JUnit rule migrations. */
public class RuleCleanupPrecedenceTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	@Test
	public void migratesTemporaryFolderWhenGenericExternalResourceCleanupIsAlsoEnabled() throws CoreException {
		IPackageFragment pack= createJUnit4And5Package();
		ICompilationUnit unit= pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import java.io.File;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.TemporaryFolder;

				public class MyTest {
					@Rule
					public TemporaryFolder folder = new TemporaryFolder();

					@Test
					public void createsFile() throws Exception {
						File file = folder.newFile("test.txt");
					}
				}
				""", false, null); //$NON-NLS-1$

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] {
				"""
				package test;
				import java.io.File;
				import java.nio.file.Files;
				import java.nio.file.Path;

				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.io.TempDir;

				public class MyTest {
					@TempDir
					public Path folder;

					@Test
					public void createsFile() throws Exception {
						File file = Files.createFile(folder.resolve("test.txt")).toFile();
					}
				}
				"""
		}, null);
	}

	@Test
	public void migratesTimeoutWhenGenericExternalResourceCleanupIsAlsoEnabled() throws CoreException {
		IPackageFragment pack= createJUnit4And5Package();
		ICompilationUnit unit= pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.Timeout;

				public class MyTest {
					@Rule
					public Timeout timeout = Timeout.seconds(5);

					@Test
					public void runs() {
					}
				}
				""", false, null); //$NON-NLS-1$

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETIMEOUT);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] {
				"""
				package test;
				import java.util.concurrent.TimeUnit;

				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.Timeout;

				@Timeout(value = 5, unit = TimeUnit.SECONDS)
				public class MyTest {
					@Test
					public void runs() {
					}
				}
				"""
		}, null);
	}

	private IPackageFragment createJUnit4And5Package() throws CoreException {
		IPackageFragmentRoot root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
		AbstractEclipseJava.addToClasspath(context.getJavaProject(),
				JavaCore.newContainerEntry(JUnitCore.JUNIT5_CONTAINER_PATH));
		return root.createPackageFragment("test", true, null); //$NON-NLS-1$
	}
}
