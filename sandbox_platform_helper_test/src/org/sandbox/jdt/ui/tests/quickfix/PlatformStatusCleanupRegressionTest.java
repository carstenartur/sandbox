package org.sandbox.jdt.ui.tests.quickfix;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;

import org.junit.jupiter.api.Test;
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

	@Test
	void usesFactoriesOnlyForProvenIdentityAndCompatibleReturnTypes() throws CoreException {
		IFile manifest= createBundleManifest("test.bundle"); //$NON-NLS-1$
		try {
			String given= """
					package test1;
					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;
					public class E1 {
						private static final String PLUGIN_ID = "test.bundle";
						private static String getPluginId() { return PLUGIN_ID; }
						void method(Throwable failure) {
							IStatus fromConstant = new Status(IStatus.ERROR, PLUGIN_ID, IStatus.OK, "constant", null);
							IStatus fromClass = new Status(IStatus.WARNING, E1.class, IStatus.OK, "class", failure);
							Status concreteWarning = new Status(IStatus.WARNING, PLUGIN_ID, IStatus.OK, "warning", failure);
							Status concreteWarningNull = new Status(IStatus.WARNING, PLUGIN_ID, IStatus.OK, "warning null", null);
							Status concreteError = new Status(IStatus.ERROR, PLUGIN_ID, IStatus.OK, "concrete", null);
							IStatus delegated = new Status(IStatus.ERROR, "other.bundle", IStatus.OK, "delegated", null);
							IStatus fromGetter = new Status(IStatus.ERROR, getPluginId(), IStatus.OK, "getter", null);
							IStatus infoWithFailure = new Status(IStatus.INFO, PLUGIN_ID, IStatus.OK, "info", failure);
						}
					}"""; //$NON-NLS-1$
			String expected= """
					package test1;
					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;
					public class E1 {
						private static final String PLUGIN_ID = "test.bundle";
						private static String getPluginId() { return PLUGIN_ID; }
						void method(Throwable failure) {
							IStatus fromConstant = Status.error("constant");
							IStatus fromClass = Status.warning("class", failure);
							Status concreteWarning = new Status(IStatus.WARNING, PLUGIN_ID, "warning", failure);
							Status concreteWarningNull = new Status(IStatus.WARNING, PLUGIN_ID, "warning null", null);
							Status concreteError = new Status(IStatus.ERROR, PLUGIN_ID, "concrete", null);
							IStatus delegated = new Status(IStatus.ERROR, "other.bundle", "delegated", null);
							IStatus fromGetter = new Status(IStatus.ERROR, getPluginId(), "getter", null);
							IStatus infoWithFailure = new Status(IStatus.INFO, PLUGIN_ID, "info", failure);
						}
					}"""; //$NON-NLS-1$

			IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
			ICompilationUnit unit= pack.createCompilationUnit("E1.java", given, false, null); //$NON-NLS-1$
			context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] { expected }, null);
		} finally {
			manifest.getParent().delete(true, null);
		}
	}

	@Test
	void usesFragmentHostAsRuntimeBundleIdentity() throws CoreException {
		IFile manifest= createBundleManifest("test.fragment", "test.host"); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			String given= """
					package test1;
					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;
					public class E1 {
						IStatus host = new Status(IStatus.ERROR, "test.host", IStatus.OK, "host", null);
						IStatus fragment = new Status(IStatus.ERROR, "test.fragment", IStatus.OK, "fragment", null);
					}"""; //$NON-NLS-1$
			String expected= """
					package test1;
					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;
					public class E1 {
						IStatus host = Status.error("host");
						IStatus fragment = new Status(IStatus.ERROR, "test.fragment", "fragment", null);
					}"""; //$NON-NLS-1$

			IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
			ICompilationUnit unit= pack.createCompilationUnit("E1.java", given, false, null); //$NON-NLS-1$
			context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] { expected }, null);
		} finally {
			manifest.getParent().delete(true, null);
		}
	}

	private IFile createBundleManifest(String symbolicName) throws CoreException {
		return createBundleManifest(symbolicName, null);
	}

	private IFile createBundleManifest(String symbolicName, String fragmentHost) throws CoreException {
		IFolder metaInf= context.getJavaProject().getProject().getFolder("META-INF"); //$NON-NLS-1$
		if (!metaInf.exists()) {
			metaInf.create(true, true, null);
		}
		IFile manifest= metaInf.getFile("MANIFEST.MF"); //$NON-NLS-1$
		StringBuilder content= new StringBuilder("Manifest-Version: 1.0\nBundle-SymbolicName: ") //$NON-NLS-1$
				.append(symbolicName).append(";singleton:=true\n"); //$NON-NLS-1$
		if (fragmentHost != null) {
			content.append("Fragment-Host: ").append(fragmentHost).append(";bundle-version=\"[1.0,2.0)\"\n"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		content.append('\n');
		manifest.create(new ByteArrayInputStream(content.toString().getBytes(StandardCharsets.UTF_8)), true, null);
		return manifest;
	}
}
