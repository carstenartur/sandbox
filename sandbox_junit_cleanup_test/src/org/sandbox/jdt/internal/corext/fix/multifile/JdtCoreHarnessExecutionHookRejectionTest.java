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

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.internal.corext.fix.multifile.JdtCoreHarnessInventory.Family;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Verifies that JUnit 3 execution hooks and inherited harness state cannot be flattened. */
public class JdtCoreHarnessExecutionHookRejectionTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
	}

	@Test
	public void rejectsNameResultAndCountHooks() throws Exception {
		ICompilationUnit harness= unit("org.eclipse.jdt.core.tests.junit.extension", "TestCase.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package org.eclipse.jdt.core.tests.junit.extension;
				public class TestCase extends junit.framework.TestCase {
					public TestCase(String name) { super(name); }
					public static junit.framework.Test buildTestSuite(Class<?> type) {
						return new junit.framework.TestSuite(type);
					}
				}
				""");
		ICompilationUnit createResult= family("CreateResultTests.java", "CreateResultTests", //$NON-NLS-1$ //$NON-NLS-2$
				"protected junit.framework.TestResult createResult() { return super.createResult(); }"); //$NON-NLS-1$
		ICompilationUnit count= family("CountTests.java", "CountTests", //$NON-NLS-1$ //$NON-NLS-2$
				"public int countTestCases() { return 1; }"); //$NON-NLS-1$
		ICompilationUnit getName= family("GetNameTests.java", "GetNameTests", //$NON-NLS-1$ //$NON-NLS-2$
				"public String getName() { return super.getName(); }"); //$NON-NLS-1$
		ICompilationUnit setName= family("SetNameTests.java", "SetNameTests", //$NON-NLS-1$ //$NON-NLS-2$
				"public void setName(String name) { super.setName(name); }"); //$NON-NLS-1$

		ICompilationUnit[] units= { harness, createResult, count, getName, setName };
		JdtCoreHarnessPlanner.Result result= JdtCoreHarnessPlanner.create(context.getJavaProject(), units,
				parse(units), true, null);

		assertEquals(4, result.inventory().families().size());
		for (Family family : result.inventory().families()) {
			assertFalse(family.directSliceApplicable(), family::typeName);
			assertEquals("JDT_CORE_LIFECYCLE_OR_RUN_HOOK_REQUIRED", family.reasonCode()); //$NON-NLS-1$
		}
		assertEquals(0, result.directMigrations().size());
	}

	@Test
	public void rejectsInheritedHarnessFieldNotRepresentedByBridge() throws Exception {
		ICompilationUnit harness= unit("org.eclipse.jdt.core.tests.junit.extension", "TestCase.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package org.eclipse.jdt.core.tests.junit.extension;
				public class TestCase extends junit.framework.TestCase {
					protected boolean abortOnFailure= true;
					public TestCase(String name) { super(name); }
					public static junit.framework.Test buildTestSuite(Class<?> type) {
						return new junit.framework.TestSuite(type);
					}
				}
				""");
		ICompilationUnit fieldUser= family("InheritedFieldTests.java", "InheritedFieldTests", //$NON-NLS-1$ //$NON-NLS-2$
				"public void configure() { this.abortOnFailure= false; }"); //$NON-NLS-1$

		ICompilationUnit[] units= { harness, fieldUser };
		JdtCoreHarnessPlanner.Result result= JdtCoreHarnessPlanner.create(context.getJavaProject(), units,
				parse(units), true, null);
		Family family= result.inventory().families().get(0);

		assertFalse(family.directSliceApplicable(), family::typeName);
		assertEquals("UNSUPPORTED_JDT_CORE_ASSERTION_OR_EXECUTION", family.reasonCode()); //$NON-NLS-1$
		assertEquals(0, result.directMigrations().size());
	}

	private ICompilationUnit family(String fileName, String className, String hook) throws CoreException {
		return unit("direct", fileName, //$NON-NLS-1$
				"""
				package direct;
				public class %s extends org.eclipse.jdt.core.tests.junit.extension.TestCase {
					public %s(String name) { super(name); }
					%s
					public void testOne() {}
				}
				""".formatted(className, className, hook));
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
}
