package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.Java9CleanUpTest.PlatformStatusPatterns;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava9;

public class PlatformStatusCleanupRegressionTest {

	@RegisterExtension
	EclipseJava9 context= new EclipseJava9();

	@ParameterizedTest
	@EnumSource(PlatformStatusPatterns.class)
	public void simplifiesStatusConstructorAndKeepsImports(PlatformStatusPatterns test) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("E1.java", test.given, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] { test.expected }, null);
	}
}
