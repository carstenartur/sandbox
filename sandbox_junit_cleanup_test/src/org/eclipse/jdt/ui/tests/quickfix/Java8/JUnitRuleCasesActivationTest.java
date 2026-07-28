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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Re-runs the historical rule-case matrix as an active regression suite. */
class JUnitRuleCasesActivationTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	IPackageFragmentRoot root;

	@BeforeEach
	void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@ParameterizedTest
	@EnumSource(MigrationRulesToExtensionsTest.RuleCases.class)
	void migratesEveryDocumentedRuleCase(MigrationRulesToExtensionsTest.RuleCases testCase) throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("MyTest.java", testCase.given, true, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETIMEOUT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEERRORCOLLECTOR);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit },
				new String[] { testCase.expected }, null);
	}
}
