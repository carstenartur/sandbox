package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.sandbox.jdt.internal.corext.fix.MYCleanUpConstants;
import org.junit.jupiter.params.provider.EnumSource;

public class CleanUpTest {
		
	@RegisterExtension
	EclipseJava11 context= new EclipseJava11();
	
	enum ExplicitEncodingPatterns {
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
		INPUTSTREAMREADER("" //
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
		FILEREADER("" //
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
				+ "        try {\n"
				+ "            Reader is=new InputStreamReader(new FileInputStream(filename), Charset.defaultCharset());\n"
				+ "            } catch (FileNotFoundException e) {\n"
				+ "            e.printStackTrace();\n"
				+ "            }\n" //
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
	public void testExplicitEncodingParametrized(ExplicitEncodingPatterns test) throws Exception {
		IPackageFragment pack= context.fSourceFolder.createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);	
	}

	@Test
	public void testExplicitEncoding_donttouch() throws Exception {
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
