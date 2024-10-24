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
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		String code = """
			import java.util.Collection;

			public class E {
				public void hui(Collection<String> arr) {
					Collection coll = null;
					for (String var : arr) {
						 coll.add(var);
					}
				}
			}"""; //$NON-NLS-1$
		char[] source = code.toCharArray();
		parser.setSource(source);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_11, options);
		parser.setCompilerOptions(options);
		CompilationUnit result = (CompilationUnit) parser.createAST(null);
		System.out.println(result.toString());
	}
}
