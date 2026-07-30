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
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Proves that Slice A preserves the JUnit 3 reflective accessibility contract. */
public class JdtCoreHarnessAccessibilityTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
	}

	@Test
	public void rejectsClassesAndConstructorsThatTheJunit3HarnessCannotReflectivelyConstruct() throws Exception {
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
					public TestCase(String name) { super(name); }
				}
				""");
		ICompilationUnit packagePrivateFamily= unit("direct", "PackagePrivateTests.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package direct;
				class PackagePrivateTests extends org.eclipse.jdt.core.tests.junit.extension.TestCase {
					public PackagePrivateTests(String name) { super(name); }
					public void testInvisibleClass() {}
				}
				""");
		ICompilationUnit hiddenConstructorFamily= unit("direct", "HiddenConstructorTests.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package direct;
				public class HiddenConstructorTests extends org.eclipse.jdt.core.tests.junit.extension.TestCase {
					HiddenConstructorTests(String name) { super(name); }
					public void testInvisibleConstructor() {}
				}
				""");

		ICompilationUnit[] units= { performance, harness, packagePrivateFamily, hiddenConstructorFamily };
		JdtCoreHarnessPlanner.Result result= JdtCoreHarnessPlanner.create(context.getJavaProject(), units,
				parse(units), true, null);

		Family packagePrivate= family(result, "direct.PackagePrivateTests"); //$NON-NLS-1$
		assertFalse(packagePrivate.directSliceApplicable());
		assertEquals("JDT_CORE_PUBLIC_FAMILY_REQUIRED", packagePrivate.reasonCode()); //$NON-NLS-1$

		Family hiddenConstructor= family(result, "direct.HiddenConstructorTests"); //$NON-NLS-1$
		assertFalse(hiddenConstructor.directSliceApplicable());
		assertEquals("JDT_CORE_NAMED_CONSTRUCTOR_REQUIRED", hiddenConstructor.reasonCode()); //$NON-NLS-1$
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
