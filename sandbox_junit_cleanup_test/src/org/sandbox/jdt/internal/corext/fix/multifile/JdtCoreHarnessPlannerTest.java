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
package org.sandbox.jdt.internal.corext.fix.multifile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.internal.corext.fix.multifile.JdtCoreHarnessInventory.Family;
import org.sandbox.jdt.internal.corext.fix.multifile.JdtCoreHarnessInventory.FamilyKind;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Active binding-resolved classification tests for the JDT Core custom harness. */
public class JdtCoreHarnessPlannerTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
	}

	@Test
	public void classifiesDirectSuiteStateAndComplianceFamiliesSeparately() throws Exception {
		ICompilationUnit performance= unit("org.eclipse.test.performance", "PerformanceTestCase.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package org.eclipse.test.performance;
				public class PerformanceTestCase extends junit.framework.TestCase {
					public PerformanceTestCase() {}
					public PerformanceTestCase(String name) { super(name); }
				}
				""");
		ICompilationUnit harness= unit("org.eclipse.jdt.core.tests.junit.extension", "TestCase.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package org.eclipse.jdt.core.tests.junit.extension;
				import java.util.List;
				import junit.framework.Test;
				import junit.framework.TestSuite;
				import org.eclipse.test.performance.PerformanceTestCase;
				public class TestCase extends PerformanceTestCase {
					public TestCase(String name) { setName(name); }
					public static Test buildTestSuite(Class<?> type) { return new TestSuite(type); }
					public static List<?> buildTestsList(Class<?> type, int depth, long ordering) { return List.of(); }
				}
				""");
		ICompilationUnit direct= unit("direct", "DirectTests.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package direct;
				import junit.framework.Test;
				import org.eclipse.jdt.core.tests.junit.extension.TestCase;
				public class DirectTests extends TestCase {
					public DirectTests(String name) { super(name); }
					public static Test suite() { return buildTestSuite(DirectTests.class); }
					public void testOne() {}
				}
				""");
		ICompilationUnit suiteBase= unit("org.eclipse.jdt.core.tests.model", "SuiteOfTestCases.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package org.eclipse.jdt.core.tests.model;
				import org.eclipse.jdt.core.tests.junit.extension.TestCase;
				public class SuiteOfTestCases extends TestCase {
					public SuiteOfTestCases(String name) { super(name); }
					public void setUpSuite() throws Exception {}
					public void tearDownSuite() throws Exception {}
				}
				""");
		ICompilationUnit model= unit("model", "ModelTests.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package model;
				import org.eclipse.jdt.core.tests.model.SuiteOfTestCases;
				public class ModelTests extends SuiteOfTestCases {
					public ModelTests(String name) { super(name); }
					public void testStateful() {}
				}
				""");
		ICompilationUnit compilerBase= unit("org.eclipse.jdt.core.tests.util", "AbstractCompilerTest.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package org.eclipse.jdt.core.tests.util;
				import org.eclipse.jdt.core.tests.junit.extension.TestCase;
				public abstract class AbstractCompilerTest extends TestCase {
					public AbstractCompilerTest(String name) { super(name); }
					public static junit.framework.Test buildAllCompliancesTestSuite(Class<?> type) { return null; }
				}
				""");
		ICompilationUnit compiler= unit("compiler", "CompilerTests.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package compiler;
				import org.eclipse.jdt.core.tests.util.AbstractCompilerTest;
				public class CompilerTests extends AbstractCompilerTest {
					public CompilerTests(String name) { super(name); }
					public void testAtCompliance() {}
				}
				""");

		ICompilationUnit[] units= { performance, harness, direct, suiteBase, model, compilerBase, compiler };
		JdtCoreHarnessPlanner.Result result= JdtCoreHarnessPlanner.create(context.getJavaProject(), units,
				parse(units), true, null);

		assertEquals(3, result.inventory().families().size());
		Family directFamily= family(result, "direct.DirectTests"); //$NON-NLS-1$
		assertEquals(FamilyKind.DIRECT_TEST_CASE, directFamily.kind());
		assertTrue(directFamily.directSliceApplicable());
		assertEquals("JDT_CORE_DIRECT_SLICE_APPLICABLE", directFamily.reasonCode()); //$NON-NLS-1$

		Family modelFamily= family(result, "model.ModelTests"); //$NON-NLS-1$
		assertEquals(FamilyKind.SUITE_STATE, modelFamily.kind());
		assertFalse(modelFamily.directSliceApplicable());
		assertEquals("JDT_CORE_SUITE_STATE_REQUIRED", modelFamily.reasonCode()); //$NON-NLS-1$

		Family compilerFamily= family(result, "compiler.CompilerTests"); //$NON-NLS-1$
		assertEquals(FamilyKind.COMPILER_COMPLIANCE, compilerFamily.kind());
		assertFalse(compilerFamily.directSliceApplicable());
		assertEquals("JDT_CORE_COMPLIANCE_MATRIX_REQUIRED", compilerFamily.reasonCode()); //$NON-NLS-1$
	}

	@Test
	public void rejectsDirectFamilyReferencedByAggregateSuite() throws Exception {
		ICompilationUnit performance= unit("org.eclipse.test.performance", "PerformanceTestCase.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package org.eclipse.test.performance;
				public class PerformanceTestCase extends junit.framework.TestCase {
					public PerformanceTestCase() {}
					public PerformanceTestCase(String name) { super(name); }
				}
				""");
		ICompilationUnit harness= unit("org.eclipse.jdt.core.tests.junit.extension", "TestCase.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package org.eclipse.jdt.core.tests.junit.extension;
				public class TestCase extends org.eclipse.test.performance.PerformanceTestCase {
					public TestCase(String name) { setName(name); }
					public static junit.framework.Test buildTestSuite(Class<?> type) { return new junit.framework.TestSuite(type); }
				}
				""");
		ICompilationUnit direct= unit("direct", "DirectTests.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package direct;
				public class DirectTests extends org.eclipse.jdt.core.tests.junit.extension.TestCase {
					public DirectTests(String name) { super(name); }
					public void testOne() {}
				}
				""");
		ICompilationUnit aggregate= unit("aggregate", "AllTests.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package aggregate;
				public class AllTests {
					public static junit.framework.Test suite() {
						return org.eclipse.jdt.core.tests.junit.extension.TestCase.buildTestSuite(direct.DirectTests.class);
					}
				}
				""");

		ICompilationUnit[] units= { performance, harness, direct, aggregate };
		JdtCoreHarnessPlanner.Result result= JdtCoreHarnessPlanner.create(context.getJavaProject(), units,
				parse(units), true, null);
		Family family= family(result, "direct.DirectTests"); //$NON-NLS-1$
		assertFalse(family.directSliceApplicable());
		assertEquals("JDT_CORE_AGGREGATE_SUITE_REQUIRED", family.reasonCode()); //$NON-NLS-1$
		assertTrue(family.relatedCompilationUnitHandles().contains(aggregate.getHandleIdentifier()));
	}

	private ICompilationUnit unit(String packageName, String fileName, String source) throws CoreException {
		IPackageFragment fragment= root.createPackageFragment(packageName, true, null);
		return fragment.createCompilationUnit(fileName, source, false, null);
	}

	private Map<String, CompilationUnit> parse(ICompilationUnit[] units) {
		Map<String, CompilationUnit> roots= new LinkedHashMap<>();
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(context.getJavaProject());
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(context.getJavaProject()));
		parser.createASTs(units, new String[0], new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				roots.put(source.getPrimary().getHandleIdentifier(), ast);
			}
		}, null);
		return roots;
	}

	private static Family family(JdtCoreHarnessPlanner.Result result, String typeName) {
		return result.inventory().families().stream().filter(candidate -> typeName.equals(candidate.typeName()))
				.findFirst().orElseThrow();
	}
}
