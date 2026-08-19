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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.internal.corext.fix.helper.JdtUiInheritedTestsRunnerMigration;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Regression coverage for the JDT UI custom inherited-test runner. */
public class JdtUiInheritedTestsRunnerMigrationTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
		AbstractEclipseJava.addToClasspath(context.getJavaProject(),
				JavaCore.newContainerEntry(JUnitCore.JUNIT6_CONTAINER_PATH));
	}

	@Test
	public void rejectsAnnotationsThatAreNotDeclaredDirectlyOnAType() {
		AST ast= AST.newAST(AST.JLS17, false);
		TypeDeclaration type= ast.newTypeDeclaration();
		MethodDeclaration method= ast.newMethodDeclaration();
		MarkerAnnotation annotation= ast.newMarkerAnnotation();
		annotation.setTypeName(ast.newSimpleName("RunWith")); //$NON-NLS-1$
		method.modifiers().add(annotation);
		type.bodyDeclarations().add(method);

		var assessment= JdtUiInheritedTestsRunnerMigration.assess(annotation,
				"org.eclipse.jdt.ui.tests.CustomBaseRunner"); //$NON-NLS-1$

		assertEquals("NOT_JDT_UI_INHERITED_TEST_RUNNER", assessment.reasonCode()); //$NON-NLS-1$
	}

	@Test
	public void materializesSuppressionAndCompilesAgainstJUnit6() throws CoreException {
		IPackageFragment support= root.createPackageFragment("org.eclipse.jdt.ui.tests", true, null); //$NON-NLS-1$
		IPackageFragment tests= root.createPackageFragment("org.eclipse.jdt.ui.tests.refactoring", true, null); //$NON-NLS-1$

		ICompilationUnit marker= support.createCompilationUnit("IgnoreInheritedTests.java", //$NON-NLS-1$
				"""
				package org.eclipse.jdt.ui.tests;

				import static java.lang.annotation.ElementType.TYPE;
				import static java.lang.annotation.RetentionPolicy.RUNTIME;

				import java.lang.annotation.Inherited;
				import java.lang.annotation.Retention;
				import java.lang.annotation.Target;

				@Retention(RUNTIME)
				@Target(TYPE)
				@Inherited
				public @interface IgnoreInheritedTests {
				}
				""", false, null);
		ICompilationUnit filter= support.createCompilationUnit("InheritedTestsFilter.java", //$NON-NLS-1$
				"""
				package org.eclipse.jdt.ui.tests;

				import org.junit.runner.Description;
				import org.junit.runner.manipulation.Filter;

				public class InheritedTestsFilter extends Filter {
					@Override
					public boolean shouldRun(Description description) {
						Class<?> clazz= description.getTestClass();
						String methodName= description.getMethodName();
						if (clazz.isAnnotationPresent(IgnoreInheritedTests.class)) {
							try {
								return clazz.getDeclaredMethod(methodName) != null;
							} catch (Exception e) {
								return false;
							}
						}
						return true;
					}

					@Override
					public String describe() {
						return "Filter inherited tests";
					}
				}
				""", false, null);
		ICompilationUnit runner= support.createCompilationUnit("CustomBaseRunner.java", //$NON-NLS-1$
				"""
				package org.eclipse.jdt.ui.tests;

				import org.junit.runner.manipulation.NoTestsRemainException;
				import org.junit.runners.BlockJUnit4ClassRunner;
				import org.junit.runners.model.InitializationError;

				public class CustomBaseRunner extends BlockJUnit4ClassRunner {
					public CustomBaseRunner(Class<?> klass) throws InitializationError {
						super(klass);
						try {
							this.filter(new InheritedTestsFilter());
						} catch (NoTestsRemainException e) {
							throw new IllegalStateException("No tests remain after filtering", e);
						}
					}
				}
				""", false, null);
		ICompilationUnit base= tests.createCompilationUnit("BaseTests.java", //$NON-NLS-1$
				"""
				package org.eclipse.jdt.ui.tests.refactoring;

				import org.junit.Test;

				public class BaseTests {
					@Test
					public void testSelected() throws Exception {
					}

					@Test
					public void testInherited() throws Exception {
					}
				}
				""", false, null);
		ICompilationUnit version= tests.createCompilationUnit("VersionTests.java", //$NON-NLS-1$
				"""
				package org.eclipse.jdt.ui.tests.refactoring;

				import org.eclipse.jdt.ui.tests.CustomBaseRunner;
				import org.eclipse.jdt.ui.tests.IgnoreInheritedTests;
				import org.junit.Test;
				import org.junit.runner.RunWith;

				@IgnoreInheritedTests
				@RunWith(CustomBaseRunner.class)
				public class VersionTests extends BaseTests {
					@Override
					@Test
					public void testSelected() throws Exception {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH);
		context.assertRefactoringResultAsExpectedNormalizingWhitespace(
				new ICompilationUnit[] { marker, filter, runner, base, version },
				new String[] {
						marker.getSource(),
						filter.getSource(),
						runner.getSource(),
						"""
						package org.eclipse.jdt.ui.tests.refactoring;

						import org.junit.jupiter.api.Test;

						public class BaseTests {
							@Test
							public void testSelected() throws Exception {
							}

							@Test
							public void testInherited() throws Exception {
							}
						}
						""",
						"""
						package org.eclipse.jdt.ui.tests.refactoring;

						import org.junit.jupiter.api.Test;

						public class VersionTests extends BaseTests {
							@Override
							@Test
							public void testSelected() throws Exception {
							}

							@Override
							public void testInherited() throws Exception {
								super.testInherited();
							}
						}
						""" }, null);
	}
}
