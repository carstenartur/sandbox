/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

final class MathTestSupport {

	private MathTestSupport() { }

	static CompilationUnit parse(String source) {
		return parse(source, new String[0]);
	}
	static CompilationUnit parse(String source, String[] classpath) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setUnitName("Calculation.java"); //$NON-NLS-1$
		parser.setEnvironment(classpath, new String[0], null, true);
		parser.setResolveBindings(true);
		Map<String, String> options= new HashMap<>();
		JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
		parser.setCompilerOptions(options);
		CompilationUnit result= (CompilationUnit) parser.createAST(null);
		for (var problem : result.getProblems()) assertFalse(problem.isError(), problem.toString());
		return result;
	}
}
