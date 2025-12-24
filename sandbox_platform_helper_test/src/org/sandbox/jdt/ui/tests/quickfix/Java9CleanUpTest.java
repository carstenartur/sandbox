package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava9;

public class Java9CleanUpTest {

	@RegisterExtension
	EclipseJava9 context= new EclipseJava9();

	enum PlatformStatusPatterns {

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
							IStatus status = Status.warning("important message", e);
						}
					}"""), //$NON-NLS-1$
		STATUSERROR5("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;
			public class E1 {
				void bla(Throwable e) {
					IStatus status = new Status(IStatus.ERROR, "plugin id", IStatus.OK, "important message", e);
				}
			}""", //$NON-NLS-1$

				"""
					package test1;
					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;
					public class E1 {
						void bla(Throwable e) {
							IStatus status = Status.error("important message", e);
						}
					}"""), //$NON-NLS-1$
		STATUSINFO5("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.Status;
			public class E1 {
				void bla(Throwable e) {
					IStatus status = new Status(IStatus.INFO, "plugin id", IStatus.OK, "important message", e);
				}
			}""", //$NON-NLS-1$

				"""
					package test1;
					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.Status;
					public class E1 {
						void bla(Throwable e) {
							IStatus status = Status.info("important message", e);
						}
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

	@ParameterizedTest
	@EnumSource(PlatformStatusPatternsDontTouch.class)
	public void testPlatformStatusdonttouch(PlatformStatusPatternsDontTouch test) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	enum PlatformStatusPatternsDontTouch {

		SIMPLE("""
			package test1;
			import java.io.ByteArrayOutputStream;
			import java.io.InputStreamReader;
			import java.io.IOException;
			import java.nio.charset.Charset;
			import java.io.FileInputStream;
			import java.io.FileNotFoundException;
			import java.io.UnsupportedEncodingException;
			public class E1 {
			    void method() throws UnsupportedEncodingException, IOException {
			    }
			}
			""") //$NON-NLS-1$
//		,
//		STATUSWARNING3("package test1;\n"
//				+ "import org.eclipse.core.runtime.IStatus;\n"
//				+ "import org.eclipse.core.runtime.Status;\n"
//				+ "public class E1 {\n"
//				+ "	void bla(Throwable e) {\n"
//				+ "	IStatus status = new Status(IStatus.WARNING, \"plugin id\",\"important message\");\n"
//				+ "	}\n"
//				+ "}"),
//		STATUSWARNING4("package test1;\n"
//				+ "import org.eclipse.core.runtime.IStatus;\n"
//				+ "import org.eclipse.core.runtime.Status;\n"
//				+ "public class E1 {\n"
//				+ "	IStatus status = new Status(IStatus.WARNING, \"plugin id\", \"important message\", null);\n"
//				+ "	void bla(Throwable e) {\n"
//				+ "		IStatus status = new Status(IStatus.WARNING, \"plugin id\", IStatus.OK, \"important message\", e);\n"
//				+ "	}\n"
//				+ "}"),
//		STATUSERROR4("package test1;\n"
//				+ "import org.eclipse.core.runtime.IStatus;\n"
//				+ "import org.eclipse.core.runtime.Status;\n"
//				+ "public class E1 {\n"
//				+ "	void bla(Throwable e) {\n"
//				+ "	IStatus status = new Status(IStatus.ERROR, \"plugin id\", \"important message\", null);\n"
//				+ "	}\n"
//				+ "}"),
//		STATUSINFO4("package test1;\n"
//				+ "import org.eclipse.core.runtime.IStatus;\n"
//				+ "import org.eclipse.core.runtime.Status;\n"
//				+ "public class E1 {\n"
//				+ "	void bla(Throwable e) {\n"
//				+ "	IStatus status = new Status(IStatus.INFO, \"plugin id\", \"important message\", null);\n"
//				+ "	}\n"
//				+ "}")
//		,
//		WRONGSECONDPARAM("package test1;\n"
//				+ "import org.eclipse.core.runtime.IStatus;\n"
//				+ "import org.eclipse.core.runtime.Status;\n"
//				+ "public class E2 {\n"
//				+ "	IStatus status = new Status(IStatus.WARNING, \"plugin id\", \"important message\", null);\n"
//				+ "	void bla(Throwable e) {\n"
//				+ "		IStatus status = new Status(IStatus.WARNING, \"plugin id\", IStatus.OK, \"important message\", e);\n"
//				+ "	}\n"
//				+ "}")
		;

		PlatformStatusPatternsDontTouch(String given) {
			this.given=given;
		}

		String given;
	}

	enum MultiStatusPatterns {

		MULTISTATUS_BASIC("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.MultiStatus;
			public class E1 {
				void bla(Throwable e) {
					MultiStatus status = new MultiStatus("my.plugin.id", 1, "important message", e);
				}
			}""", //$NON-NLS-1$

				"""
					package test1;
					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.MultiStatus;
					public class E1 {
						void bla(Throwable e) {
							MultiStatus status = new MultiStatus("my.plugin.id", IStatus.OK, "important message", e);
						}
					}"""), //$NON-NLS-1$
		MULTISTATUS_WITH_CONSTANT("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.MultiStatus;
			public class E1 {
				private static final int ERROR_CODE = 100;
				void bla(Throwable e) {
					MultiStatus status = new MultiStatus("my.plugin.id", ERROR_CODE, "important message", e);
				}
			}""", //$NON-NLS-1$

				"""
					package test1;
					import org.eclipse.core.runtime.IStatus;
					import org.eclipse.core.runtime.MultiStatus;
					public class E1 {
						private static final int ERROR_CODE = 100;
						void bla(Throwable e) {
							MultiStatus status = new MultiStatus("my.plugin.id", IStatus.OK, "important message", e);
						}
					}"""); //$NON-NLS-1$

		String given;
		String expected;

		MultiStatusPatterns(String given, String expected) {
			this.given = given;
			this.expected = expected;
		}
	}

	@ParameterizedTest
	@EnumSource(MultiStatusPatterns.class)
	public void testMultiStatusParametrized(MultiStatusPatterns test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
	}

	enum MultiStatusPatternsDontTouch {

		ALREADY_OK("""
			package test1;
			import org.eclipse.core.runtime.IStatus;
			import org.eclipse.core.runtime.MultiStatus;
			public class E1 {
				void bla(Throwable e) {
					MultiStatus status = new MultiStatus("my.plugin.id", IStatus.OK, "important message", e);
				}
			}"""); //$NON-NLS-1$

		String given;

		MultiStatusPatternsDontTouch(String given) {
			this.given = given;
		}
	}

	@ParameterizedTest
	@EnumSource(MultiStatusPatternsDontTouch.class)
	public void testMultiStatusDontTouch(MultiStatusPatternsDontTouch test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

//	@ParameterizedTest
//	@EnumSource(PlatformStatusPatternsDontTouch.class)
//	public void testPlatformStatus_donttouch(PlatformStatusPatternsDontTouch test) throws CoreException {
//		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null);
//		ICompilationUnit cu= pack.createCompilationUnit("E2.java", test.given, false, null);
//		context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);
//		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
//	}
}
