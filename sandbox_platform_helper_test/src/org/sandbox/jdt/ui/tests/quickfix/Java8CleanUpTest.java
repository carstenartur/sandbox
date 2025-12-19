package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava8;

public class Java8CleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava8();

	enum PlatformStatusPatterns {

		STATUSWARNING3("""
			package test1;

			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;

			public class E1 {
				IStatus status = new Status(IStatus.WARNING, "plugin id","important message");
			}""", //$NON-NLS-1$

				"""
					package test1;

					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;

					public class E1 {
						IStatus status = new Status(IStatus.WARNING, "plugin id","important message");
					}"""), //$NON-NLS-1$
		STATUSWARNING4("""
			package test1;

			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;

			public class E1 {
				IStatus status = new Status(IStatus.WARNING, "plugin id", "important message", null);
				void bla(Throwable e) {
					IStatus status = new Status(IStatus.WARNING, "plugin id", IStatus.OK, "important message", e);
				}
			}""", //$NON-NLS-1$

				"""
					package test1;

					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;

					public class E1 {
						IStatus status = new Status(IStatus.WARNING, "plugin id", "important message", null);
						void bla(Throwable e) {
							IStatus status = new Status(IStatus.WARNING, "plugin id", IStatus.OK, "important message", e);
						}
					}"""), //$NON-NLS-1$
		STATUSWARNING5("""
			package test1;

			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;

			public class E1 {
				void bla(Throwable e) {
					IStatus status = new Status(IStatus.WARNING, "plugin id", IStatus.OK, "important message", e);
				}
			}""", //$NON-NLS-1$

				"""
					package test1;

					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;

					public class E1 {
						void bla(Throwable e) {
							IStatus status = new Status(IStatus.WARNING, "plugin id", IStatus.OK, "important message", e);
						}
					}"""), //$NON-NLS-1$
		STATUSERROR("""
			package test1;

			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;

			public class E1 {
				IStatus status = new Status(IStatus.ERROR, "plugin id", "important message", null);
			}""", //$NON-NLS-1$

				"""
					package test1;

					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;

					public class E1 {
						IStatus status = new Status(IStatus.ERROR, "plugin id", "important message", null);
					}"""), //$NON-NLS-1$
		STATUSINFO("""
			package test1;

			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;

			public class E1 {
				IStatus status = new Status(IStatus.INFO, "plugin id", "important message", null);
			}""", //$NON-NLS-1$

				"""
					package test1;

					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;

					public class E1 {
						IStatus status = new Status(IStatus.INFO, "plugin id", "important message", null);
					}"""); //$NON-NLS-1$

		String given;
		String expected;

		PlatformStatusPatterns(String given, String expected) {
			this.given=given;
			this.expected=expected;
		}
	}

	@ParameterizedTest
	@EnumSource(PlatformStatusPatterns.class)
	public void testPlatformStatusParametrized(PlatformStatusPatterns test) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
	}

	@Test
	public void testPlatformStatusdonttouch() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("E2.java", //$NON-NLS-1$
				"""
					package test1;

					import java.io.ByteArrayOutputStream;
					import java.io.InputStreamReader;
					import java.io.IOException;
					import java.nio.charset.Charset;
					import java.io.FileInputStream;
					import java.io.FileNotFoundException;
					import java.io.UnsupportedEncodingException;

					public class E2 {
					    void method() throws UnsupportedEncodingException, IOException {
					    }
					}
					""", //$NON-NLS-1$
				false, null);

		context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
