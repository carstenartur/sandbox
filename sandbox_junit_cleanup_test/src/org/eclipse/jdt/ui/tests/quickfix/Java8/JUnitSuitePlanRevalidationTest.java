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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;

import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitMigrationPlan;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitMultiFilePlanner;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitSuiteMigration;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.JUnitCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Immutable-plan and stale-plan tests for coordinated JUnit suites. */
public class JUnitSuitePlanRevalidationTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
		AbstractEclipseJava.addToClasspath(context.getJavaProject(),
				JavaCore.newContainerEntry(JUnitCore.JUNIT5_CONTAINER_PATH));
	}

	@Test
	public void closedPlanStoresOrderedSuiteSourceTargets() throws CoreException {
		Fixture fixture= fixture();
		MultiFileCleanUpPlanResult<JUnitMigrationPlan> result= JUnitMultiFilePlanner.create(
				fixture.suite().getJavaProject(), new ICompilationUnit[] { fixture.suite(), fixture.first() },
				false, true, true, new NullProgressMonitor());

		assertFalse(result.status().hasFatalError(), () -> result.status().toString());
		assertEquals(1, result.plan().suiteMigrations().size());
		JUnitSuiteMigration migration= result.plan().suiteMigrations().get(0);
		assertEquals(fixture.suite().getPrimary().getHandleIdentifier(), migration.suiteCompilationUnitHandle());
		assertEquals(List.of(fixture.first().getPrimary().getHandleIdentifier()),
				migration.referencedCompilationUnitHandles());
		assertEquals(1, migration.referencedTypeBindingKeys().size());
	}

	@Test
	public void changedSuiteTargetInvalidatesPlanBeforeLocalRewrite() throws CoreException {
		Fixture fixture= fixture();
		JUnitCleanUpCore cleanup= new JUnitCleanUpCore(Map.of(
				MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH, CleanUpOptions.TRUE));
		NullProgressMonitor monitor= new NullProgressMonitor();

		Collection<ICompilationUnit> additions= cleanup.expandCleanUpScope(
				fixture.suite().getJavaProject(), List.of(fixture.suite()), monitor);
		assertEquals(List.of(fixture.first().getPrimary().getHandleIdentifier()),
				additions.stream().map(unit -> unit.getPrimary().getHandleIdentifier()).toList());
		assertTrue(cleanup.expandCleanUpScope(fixture.suite().getJavaProject(),
				List.of(fixture.suite(), fixture.first()), monitor).isEmpty());
		assertFalse(cleanup.checkPreConditions(fixture.suite().getJavaProject(),
				new ICompilationUnit[] { fixture.suite(), fixture.first() }, monitor).hasFatalError());

		String changed= fixture.suiteSource().replace("FirstTest.class", "SecondTest.class"); //$NON-NLS-1$ //$NON-NLS-2$
		fixture.suite().getBuffer().setContents(changed);
		fixture.suite().save(null, true);

		CoreException error= assertThrows(CoreException.class,
				() -> cleanup.createFix(new CleanUpContext(fixture.suite(), parse(fixture.suite()))));
		assertTrue(error.getMessage().contains("suite plan is stale"), error::getMessage); //$NON-NLS-1$
		assertEquals(changed, fixture.suite().getBuffer().getContents());
		assertEquals(fixture.firstSource(), fixture.first().getBuffer().getContents());
		assertEquals(fixture.secondSource(), fixture.second().getBuffer().getContents());
		cleanup.checkPostConditions(monitor);
	}

	private Fixture fixture() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		String firstSource= """
				package test;

				import org.junit.Test;

				public class FirstTest {
					@Test
					public void first() {
					}
				}
				""";
		ICompilationUnit first= pack.createCompilationUnit("FirstTest.java", firstSource, false, null); //$NON-NLS-1$
		String secondSource= """
				package test;

				import org.junit.Test;

				public class SecondTest {
					@Test
					public void second() {
					}
				}
				""";
		ICompilationUnit second= pack.createCompilationUnit("SecondTest.java", secondSource, false, null); //$NON-NLS-1$
		String suiteSource= """
				package test;

				import org.junit.runner.RunWith;
				import org.junit.runners.Suite;

				@RunWith(Suite.class)
				@Suite.SuiteClasses(FirstTest.class)
				public class AllTests {
				}
				""";
		ICompilationUnit suite= pack.createCompilationUnit("AllTests.java", suiteSource, false, null); //$NON-NLS-1$
		return new Fixture(suite, first, second, suiteSource, firstSource, secondSource);
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

	private record Fixture(ICompilationUnit suite, ICompilationUnit first, ICompilationUnit second,
			String suiteSource, String firstSource, String secondSource) {
	}
}
