package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava9;

public class Java9CleanUpTest {
		
	@RegisterExtension
	EclipseJava9 context= new EclipseJava9();
	
	enum PlatformStatusPatterns {

		STATUSWARNING3("package test1;\n"
				+ "\n"
				+ "import org.eclipse.core.runtime.IStatus;\n"
				+ "import org.eclipse.core.runtime.Status;\n"
				+ "\n"
				+ "public class E1 {\n"
				+ "	IStatus status = new Status(IStatus.WARNING, \"plugin id\",\"important message\");\n"
				+ "}",

				"package test1;\n"
						+ "\n"
						+ "import org.eclipse.core.runtime.IStatus;\n"
						+ "import org.eclipse.core.runtime.Status;\n"
						+ "\n"
						+ "public class E1 {\n"
						+ "	IStatus status = Status.warning(\"important message\");\n"
						+ "}"),
		STATUSWARNING4("package test1;\n"
				+ "\n"
				+ "import org.eclipse.core.runtime.IStatus;\n"
				+ "import org.eclipse.core.runtime.Status;\n"
				+ "\n"
				+ "public class E1 {\n"
				+ "	IStatus status = new Status(IStatus.WARNING, \"plugin id\", \"important message\", null);\n"
				+ "	void bla(Throwable e) {\n"
				+ "		IStatus status = new Status(IStatus.WARNING, \"plugin id\", IStatus.OK, \"important message\", e);\n"
				+ "	}\n"
				+ "}",

				"package test1;\n"
						+ "\n"
						+ "import org.eclipse.core.runtime.IStatus;\n"
						+ "import org.eclipse.core.runtime.Status;\n"
						+ "\n"
						+ "public class E1 {\n"
						+ "	IStatus status = Status.warning(\"important message\");\n"
						+ "	void bla(Throwable e) {\n"
						+ "		IStatus status = Status.warning(\"important message\", e);\n"
						+ "	}\n"
						+ "}"),
		STATUSWARNING5("package test1;\n"
				+ "\n"
				+ "import org.eclipse.core.runtime.IStatus;\n"
				+ "import org.eclipse.core.runtime.Status;\n"
				+ "\n"
				+ "public class E1 {\n"
				+ "	void bla(Throwable e) {\n"
				+ "		IStatus status = new Status(IStatus.WARNING, \"plugin id\", IStatus.OK, \"important message\", e);\n"
				+ "	}\n"
				+ "}",

				"package test1;\n"
						+ "\n"
						+ "import org.eclipse.core.runtime.IStatus;\n"
						+ "import org.eclipse.core.runtime.Status;\n"
						+ "\n"
						+ "public class E1 {\n"
						+ "	void bla(Throwable e) {\n"
						+ "		IStatus status = Status.warning(\"important message\", e);\n"
						+ "	}\n"
						+ "}"),
		STATUSERROR("package test1;\n"
				+ "\n"
				+ "import org.eclipse.core.runtime.IStatus;\n"
				+ "import org.eclipse.core.runtime.Status;\n"
				+ "\n"
				+ "public class E1 {\n"
				+ "	IStatus status = new Status(IStatus.ERROR, \"plugin id\", \"important message\", null);\n"
				+ "}",

				"package test1;\n"
						+ "\n"
						+ "import org.eclipse.core.runtime.IStatus;\n"
						+ "import org.eclipse.core.runtime.Status;\n"
						+ "\n"
						+ "public class E1 {\n"
						+ "	IStatus status = Status.error(\"important message\");\n"
						+ "}"),
		STATUSINFO("package test1;\n"
				+ "\n"
				+ "import org.eclipse.core.runtime.IStatus;\n"
				+ "import org.eclipse.core.runtime.Status;\n"
				+ "\n"
				+ "public class E1 {\n"
				+ "	IStatus status = new Status(IStatus.INFO, \"plugin id\", \"important message\", null);\n"
				+ "}",

				"package test1;\n"
						+ "\n"
						+ "import org.eclipse.core.runtime.IStatus;\n"
						+ "import org.eclipse.core.runtime.Status;\n"
						+ "\n"
						+ "public class E1 {\n"
						+ "	IStatus status = Status.info(\"important message\");\n"
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
		IPackageFragment pack= context.fSourceFolder.createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null);
		context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);	
	}

	@Test
	public void testPlatformStatus_donttouch() throws CoreException {
		IPackageFragment pack= context.fSourceFolder.createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("E2.java",
				"" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.ByteArrayOutputStream;\n"
				+ "import java.io.InputStreamReader;\n"
				+ "import java.io.IOException;\n"
				+ "import java.nio.charset.Charset;\n"
				+ "import java.io.FileInputStream;\n"
				+ "import java.io.FileNotFoundException;\n" //
				+ "import java.io.UnsupportedEncodingException;\n" //
				+ "\n" //
				+ "public class E2 {\n" //
				+ "    void method() throws UnsupportedEncodingException, IOException {\n" //
				+ "    }\n" //
				+ "}\n",
				false, null);

		context.enable(MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
