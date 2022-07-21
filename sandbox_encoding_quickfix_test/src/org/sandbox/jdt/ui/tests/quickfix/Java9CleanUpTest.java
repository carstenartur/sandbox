package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.common.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava9;

public class Java9CleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava9();

	enum ExplicitEncodingPatterns {

		BYTEARRAYOUTSTREAM("" //
				+ "package test1;\n"
				+ "\n" //
				+ "import java.io.ByteArrayOutputStream;\n"
				+ "import java.io.InputStreamReader;\n"
				+ "import java.io.FileInputStream;\n"
				+ "import java.io.FileReader;\n"
				+ "import java.io.Reader;\n"
				+ "import java.io.FileNotFoundException;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    void method(String filename) {\n" //
				+ "        ByteArrayOutputStream ba=new ByteArrayOutputStream();\n"
				+ "        String result=ba.toString();\n"
				+ "       }\n" //
				+ "    }\n" //
				+ "}\n",

				"" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.ByteArrayOutputStream;\n"
				+ "import java.io.InputStreamReader;\n"
				+ "import java.io.FileInputStream;\n"
				+ "import java.io.FileReader;\n"
				+ "import java.io.Reader;\n"
				+ "import java.nio.charset.Charset;\n"
				+ "import java.io.FileNotFoundException;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    void method(String filename) {\n" //
				+ "        ByteArrayOutputStream ba=new ByteArrayOutputStream();\n"
				+ "        String result=ba.toString(Charset.defaultCharset().displayName());\n"
				+ "       }\n" //
				+ "    }\n" //
				+ "}\n"),
		FILEREADER("" //
				+ "package test1;\n"
				+ "\n" //
				+ "import java.io.InputStreamReader;\n"
				+ "import java.io.FileInputStream;\n"
				+ "import java.io.FileReader;\n"
				+ "import java.io.Reader;\n"
				+ "import java.io.FileNotFoundException;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    void method(String filename) {\n" //
				+ "        try {\n"
				+ "            Reader is=new FileReader(filename);\n"
				+ "            } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
				+ "       }\n" //
				+ "    }\n" //
				+ "}\n",

				"" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.InputStreamReader;\n"
				+ "import java.io.FileInputStream;\n"
				+ "import java.io.FileReader;\n"
				+ "import java.io.Reader;\n"
				+ "import java.nio.charset.Charset;\n"
				+ "import java.io.FileNotFoundException;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    void method(String filename) {\n" //
				+ "        try {\n"
				+ "            Reader is=new InputStreamReader(new FileInputStream(filename), Charset.defaultCharset());\n"
				+ "            } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
				+ "       }\n" //
				+ "    }\n" //
				+ "}\n"),
		FILEWRITER("" //
				+ "package test1;\n"
				+ "\n" //
				+ "import java.io.FileWriter;\n"
				+ "import java.io.Writer;\n"
				+ "import java.io.FileNotFoundException;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    void method(String filename) {\n" //
				+ "        try {\n"
				+ "            Writer fw=new FileWriter(filename);\n"
				+ "            } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
				+ "       }\n" //
				+ "    }\n" //
				+ "}\n",

				"" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.FileWriter;\n"
				+ "import java.io.OutputStreamWriter;\n"
				+ "import java.io.Writer;\n"
				+ "import java.nio.charset.Charset;\n"
				+ "import java.io.FileNotFoundException;\n" //
				+ "import java.io.FileOutputStream;\n"
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    void method(String filename) {\n" //
				+ "        try {\n"
				+ "            Writer fw=new OutputStreamWriter(new FileOutputStream(filename), Charset.defaultCharset());\n"
				+ "            } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
				+ "       }\n" //
				+ "    }\n" //
				+ "}\n"),
		INPUTSTREAMREADER("" //
				+ "package test1;\n"
				+ "\n" //
				+ "import java.io.InputStreamReader;\n"
				+ "import java.io.FileInputStream;\n"
				+ "import java.io.FileReader;\n"
				+ "import java.io.Reader;\n"
				+ "import java.io.FileNotFoundException;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    void method(String filename) {\n" //
				+ "        try {\n"
				+ "            InputStreamReader is=new InputStreamReader(new FileInputStream(\"\")); //$NON-NLS-1$\n"
				+ "            } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
				+ "       }\n" //
				+ "    }\n" //
				+ "}\n",

				"" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.InputStreamReader;\n"
				+ "import java.io.FileInputStream;\n"
				+ "import java.io.FileReader;\n"
				+ "import java.io.Reader;\n"
				+ "import java.nio.charset.Charset;\n"
				+ "import java.io.FileNotFoundException;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    void method(String filename) {\n" //
				+ "        try {\n"
				+ "            InputStreamReader is=new InputStreamReader(new FileInputStream(\"\"), Charset.defaultCharset()); //$NON-NLS-1$\n"
				+ "            } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
				+ "       }\n" //
				+ "    }\n" //
				+ "}\n"),
		OUTPUTSTREAMWRITER("" //
				+ "package test1;\n"
				+ "\n" //
				+ "import java.io.ByteArrayOutputStream;\n"
				+ "import java.io.InputStreamReader;\n"
				+ "import java.io.FileInputStream;\n"
				+ "import java.io.FileReader;\n"
				+ "import java.io.Reader;\n"
				+ "import java.io.FileNotFoundException;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    void method(String filename) {\n" //
				+ "        try {\n"
				+ "            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream(\"\")); //$NON-NLS-1$\n"
				+ "            } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
				+ "       }\n" //
				+ "    }\n" //
				+ "}\n",

				"" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.ByteArrayOutputStream;\n"
				+ "import java.io.InputStreamReader;\n"
				+ "import java.io.FileInputStream;\n"
				+ "import java.io.FileReader;\n"
				+ "import java.io.Reader;\n"
				+ "import java.nio.charset.Charset;\n"
				+ "import java.io.FileNotFoundException;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    void method(String filename) {\n" //
				+ "        try {\n"
				+ "            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream(\"\"), Charset.defaultCharset()); //$NON-NLS-1$\n"
				+ "            } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
				+ "       }\n" //
				+ "    }\n" //
				+ "}\n"),
		PRINTWRITER("" //
				+ "package test1;\n"
				+ "\n" //
				+ "import java.io.PrintWriter;\n"
				+ "import java.io.Writer;\n"
				+ "import java.io.FileNotFoundException;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    void method(String filename) {\n" //
				+ "        try {\n"
				+ "            Writer w=new PrintWriter(filename);\n"
				+ "            } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
				+ "       }\n" //
				+ "    }\n" //
				+ "}\n",

				"" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.PrintWriter;\n"
				+ "import java.io.Writer;\n"
				+ "import java.nio.charset.Charset;\n"
				+ "import java.io.BufferedWriter;\n" //
				+ "import java.io.FileNotFoundException;\n" //
				+ "import java.io.FileOutputStream;\n"
				+ "import java.io.OutputStreamWriter;\n"
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    void method(String filename) {\n" //
				+ "        try {\n"
				+ "            Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), Charset.defaultCharset()));\n"
				+ "            } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
				+ "       }\n" //
				+ "    }\n" //
				+ "}\n"),
		STRINGGETBYTES("" //
				+ "package test1;\n"
				+ "\n" //
				+ "import java.io.ByteArrayOutputStream;\n"
				+ "import java.io.InputStreamReader;\n"
				+ "import java.io.FileInputStream;\n"
				+ "import java.io.FileReader;\n"
				+ "import java.io.Reader;\n"
				+ "import java.io.FileNotFoundException;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    void method(String filename) {\n" //
				+ "        String s=\"asdf\"; //$NON-NLS-1$\n"
				+ "        byte[] bytes= s.getBytes();\n"
				+ "        System.out.println(bytes.length);\n"
				+ "       }\n" //
				+ "    }\n" //
				+ "}\n",

				"" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.ByteArrayOutputStream;\n"
				+ "import java.io.InputStreamReader;\n"
				+ "import java.io.FileInputStream;\n"
				+ "import java.io.FileReader;\n"
				+ "import java.io.Reader;\n"
				+ "import java.nio.charset.Charset;\n"
				+ "import java.io.FileNotFoundException;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    void method(String filename) {\n" //
				+ "        String s=\"asdf\"; //$NON-NLS-1$\n" //
				+ "        byte[] bytes= s.getBytes(Charset.defaultCharset());\n" //
				+ "        System.out.println(bytes.length);\n" //
				+ "       }\n" //
				+ "    }\n" //
				+ "}\n"),
		THREE("" //
				+ "package test1;\n"
				+ "\n" //
				+ "import java.io.ByteArrayOutputStream;\n"
				+ "import java.io.InputStreamReader;\n"
				+ "import java.io.FileInputStream;\n"
				+ "import java.io.FileReader;\n"
				+ "import java.io.Reader;\n"
				+ "import java.io.FileNotFoundException;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    void method(String filename) {\n" //
				+ "        String s=\"asdf\"; //$NON-NLS-1$\n"
				+ "        byte[] bytes= s.getBytes();\n"
				+ "        System.out.println(bytes.length);\n"
				+ "        ByteArrayOutputStream ba=new ByteArrayOutputStream();\n"
				+ "        String result=ba.toString();\n"
				+ "        try {\n"
				+ "            InputStreamReader is=new InputStreamReader(new FileInputStream(\"\")); //$NON-NLS-1$\n"
				+ "            } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
				+ "        try {\n"
				+ "            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream(\"\")); //$NON-NLS-1$\n"
				+ "            } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
				+ "        try {\n"
				+ "            Reader is=new FileReader(filename);\n"
				+ "            } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
				+ "       }\n" //
				+ "    }\n" //
				+ "}\n",

				"" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.ByteArrayOutputStream;\n"
				+ "import java.io.InputStreamReader;\n"
				+ "import java.io.FileInputStream;\n"
				+ "import java.io.FileReader;\n"
				+ "import java.io.Reader;\n"
				+ "import java.nio.charset.Charset;\n"
				+ "import java.io.FileNotFoundException;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    void method(String filename) {\n" //
				+ "        String s=\"asdf\"; //$NON-NLS-1$\n" //
				+ "        byte[] bytes= s.getBytes(Charset.defaultCharset());\n" //
				+ "        System.out.println(bytes.length);\n" //
				+ "        ByteArrayOutputStream ba=new ByteArrayOutputStream();\n"
				+ "        String result=ba.toString(Charset.defaultCharset().displayName());\n"
				+ "        try {\n"
				+ "            InputStreamReader is=new InputStreamReader(new FileInputStream(\"\"), Charset.defaultCharset()); //$NON-NLS-1$\n"
				+ "            } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
				+ "        try {\n"
				+ "            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream(\"\"), Charset.defaultCharset()); //$NON-NLS-1$\n"
				+ "            } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
				+ "        try {\n"
				+ "            Reader is=new InputStreamReader(new FileInputStream(filename), Charset.defaultCharset());\n"
				+ "            } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
				+ "       }\n" //
				+ "    }\n" //
				+ "}\n");

		ExplicitEncodingPatterns(String given, String expected) {
			this.given=given;
			this.expected=expected;
		}

		String given, expected;
	}

	@ParameterizedTest
	@EnumSource(ExplicitEncodingPatterns.class)
	public void testExplicitEncodingParametrized(ExplicitEncodingPatterns test) throws CoreException {
		IPackageFragment pack= context.fSourceFolder.createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
	}

	@Test
	public void testExplicitEncoding_donttouch() throws CoreException{
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
				+ "        String s=\"asdf\"; //$NON-NLS-1$\n" //
				+ "        byte[] bytes= s.getBytes(Charset.defaultCharset());\n" //
				+ "        System.out.println(bytes.length);\n" //
				+ "        ByteArrayOutputStream ba=new ByteArrayOutputStream();\n"
				+ "        String result=ba.toString(Charset.defaultCharset().displayName());\n"
				+ "        try (\n"
				+ "            InputStreamReader is=new InputStreamReader(new FileInputStream(\"\"), Charset.defaultCharset()); //$NON-NLS-1$\n"
				+ "           ){ } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
				+ "    }\n" //
				+ "}\n",
				false, null);

		context.enable(MYCleanUpConstants.EXPLICITENCODING_CLEANUP);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
