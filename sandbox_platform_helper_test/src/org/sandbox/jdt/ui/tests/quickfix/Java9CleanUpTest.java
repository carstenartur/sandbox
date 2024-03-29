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

		STATUSWARNING5("package test1;\n"
				+ "import org.eclipse.core.runtime.IStatus;\n"
				+ "import org.eclipse.core.runtime.Status;\n"
				+ "public class E1 {\n"
				+ "	void bla(Throwable e) {\n"
				+ "		IStatus status = new Status(IStatus.WARNING, \"plugin id\", IStatus.OK, \"important message\", e);\n"
				+ "	}\n"
				+ "}",

				"package test1;\n"
						+ "import org.eclipse.core.runtime.IStatus;\n"
						+ "import org.eclipse.core.runtime.Status;\n"
						+ "public class E1 {\n"
						+ "	void bla(Throwable e) {\n"
						+ "		IStatus status = Status.warning(\"important message\", e);\n"
						+ "	}\n"
						+ "}"),
		STATUSERROR5("package test1;\n"
				+ "import org.eclipse.core.runtime.IStatus;\n"
				+ "import org.eclipse.core.runtime.Status;\n"
				+ "public class E1 {\n"
				+ "	void bla(Throwable e) {\n"
				+ "		IStatus status = new Status(IStatus.ERROR, \"plugin id\", IStatus.OK, \"important message\", e);\n"
				+ "	}\n"
				+ "}",

				"package test1;\n"
						+ "import org.eclipse.core.runtime.IStatus;\n"
						+ "import org.eclipse.core.runtime.Status;\n"
						+ "public class E1 {\n"
						+ "	void bla(Throwable e) {\n"
						+ "		IStatus status = Status.error(\"important message\", e);\n"
						+ "	}\n"
						+ "}"),
		STATUSINFO5("package test1;\n"
				+ "import org.eclipse.core.runtime.IStatus;\n"
				+ "import org.eclipse.core.runtime.Status;\n"
				+ "public class E1 {\n"
				+ "	void bla(Throwable e) {\n"
				+ "		IStatus status = new Status(IStatus.INFO, \"plugin id\", IStatus.OK, \"important message\", e);\n"
				+ "	}\n"
				+ "}",

				"package test1;\n"
						+ "import org.eclipse.core.runtime.IStatus;\n"
						+ "import org.eclipse.core.runtime.Status;\n"
						+ "public class E1 {\n"
						+ "	void bla(Throwable e) {\n"
						+ "		IStatus status = Status.info(\"important message\", e);\n"
						+ "	}\n"
						+ "}");

		PlatformStatusPatterns(String given, String expected) {
			this.given=given;
			this.expected=expected;
		}

		String given, expected;
	}

	@ParameterizedTest
	@EnumSource(PlatformStatusPatterns.class)
	public void testPlatformStatusParametrized(PlatformStatusPatterns test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null);
		context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
	}

	@ParameterizedTest
	@EnumSource(PlatformStatusPatternsDontTouch.class)
	public void testPlatformStatus_donttouch(PlatformStatusPatternsDontTouch test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null);
		context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
	
	enum PlatformStatusPatternsDontTouch {

		SIMPLE("package test1;\n" //
				+ "import java.io.ByteArrayOutputStream;\n"
				+ "import java.io.InputStreamReader;\n"
				+ "import java.io.IOException;\n"
				+ "import java.nio.charset.Charset;\n"
				+ "import java.io.FileInputStream;\n"
				+ "import java.io.FileNotFoundException;\n" //
				+ "import java.io.UnsupportedEncodingException;\n" //
				+ "public class E1 {\n" //
				+ "    void method() throws UnsupportedEncodingException, IOException {\n" //
				+ "    }\n" //
				+ "}\n")
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

//	@ParameterizedTest
//	@EnumSource(PlatformStatusPatternsDontTouch.class)
//	public void testPlatformStatus_donttouch(PlatformStatusPatternsDontTouch test) throws CoreException {
//		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test1", false, null);
//		ICompilationUnit cu= pack.createCompilationUnit("E2.java", test.given, false, null);
//		context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);
//		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
//	}
}
