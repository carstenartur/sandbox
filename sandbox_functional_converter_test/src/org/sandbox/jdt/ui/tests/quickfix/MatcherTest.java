package org.sandbox.jdt.ui.tests.quickfix;

import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;

public class MatcherTest {

	@Test
	public void matcherTest() {
		 ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
		 String code = "import java.util.Collection;\n"
		 		+ "\n"
		 		+ "public class E {\n"
		 		+ "	public void hui(Collection<String> arr) {\n"
		 		+ "		Collection coll = null;\n"
		 		+ "		for (String var : arr) {\n"
		 		+ "			 coll.add(var);\n"
		 		+ "		}\n"
		 		+ "	}\n"
		 		+ "}";
		char[] source = code.toCharArray();
		parser.setSource(source);
		Map options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_11, options);
		parser.setCompilerOptions(options);
		CompilationUnit result = (CompilationUnit) parser.createAST(null);
		System.out.println(result.toString());
	}
}
