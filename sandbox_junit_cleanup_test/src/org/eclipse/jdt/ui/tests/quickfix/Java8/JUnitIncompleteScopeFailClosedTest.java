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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.JUnitCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Ensures incomplete coordinated JUnit scopes cannot fall back to partial local rewrites. */
public class JUnitIncompleteScopeFailClosedTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void omittedSuiteMemberPreventsEveryLocalRewrite() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		String referencedSource= """
				package test;

				import org.junit.Before;

				public class ReferencedTest {
					@Before
					public void setup() {
					}
				}
				""";
		ICompilationUnit referenced= pack.createCompilationUnit("ReferencedTest.java", referencedSource, false, null); //$NON-NLS-1$
		String suiteSource= """
				package test;

				import org.junit.runner.RunWith;
				import org.junit.runners.Suite;

				@RunWith(Suite.class)
				@Suite.SuiteClasses(ReferencedTest.class)
				public class AllTests {
				}
				""";
		ICompilationUnit suite= pack.createCompilationUnit("AllTests.java", suiteSource, false, null); //$NON-NLS-1$
		JUnitCleanUpCore cleanup= new JUnitCleanUpCore(Map.of(
				MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE, CleanUpOptions.TRUE));
		NullProgressMonitor monitor= new NullProgressMonitor();

		Collection<ICompilationUnit> additions= cleanup.expandCleanUpScope(suite.getJavaProject(), List.of(suite), monitor);
		assertEquals(List.of(referenced.getHandleIdentifier()),
				additions.stream().map(ICompilationUnit::getHandleIdentifier).toList());

		RefactoringStatus status= cleanup.checkPreConditions(suite.getJavaProject(),
				new ICompilationUnit[] { suite }, monitor);
		assertTrue(status.hasFatalError(), () -> "Incomplete scope must be fatal: " + status); //$NON-NLS-1$
		assertTrue(status.toString().contains("incomplete"), () -> "Missing scope diagnostic: " + status); //$NON-NLS-1$ //$NON-NLS-2$
		assertNull(cleanup.createFix(new CleanUpContext(suite, parse(suite))));
		assertEquals(suiteSource, suite.getBuffer().getContents());
		assertEquals(referencedSource, referenced.getBuffer().getContents());
		cleanup.checkPostConditions(monitor);
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setSource(unit);
		parser.setProject(unit.getJavaProject());
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		return (CompilationUnit) parser.createAST(null);
	}
}
