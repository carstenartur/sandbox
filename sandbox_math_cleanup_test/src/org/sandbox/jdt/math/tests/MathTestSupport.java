/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

final class MathTestSupport {

	private MathTestSupport() { }

	static String diagnosticDecline(String source, org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions options) throws Exception {
		var result=org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.analyze(parse(source),source,options,null,-1,0);
		assertFalse(result.changed(),result.diagnostics().toString());
		assertTrue(result.diagnostics().stream().anyMatch(d->d.message().startsWith("GENERATED_DIAGNOSTIC_INTRODUCED")),result.diagnostics().toString());
		var document=new org.eclipse.jface.text.Document(source);result.newEdit().apply(document);
		assertEquals(source,document.get());return document.get();
	}

	static CompilationUnit parse(String source) {
		return parse(source, new String[0]);
	}
	static CompilationUnit parse(String source, int targetJava) {
		return parse(source, new String[0], targetJava);
	}
	static CompilationUnit parse(String source, String[] classpath) {
		return parse(source, classpath, 17);
	}
	private static CompilationUnit parse(String source, String[] classpath, int targetJava) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setUnitName("Calculation.java"); //$NON-NLS-1$
		parser.setEnvironment(classpath, new String[0], null, true);
		parser.setResolveBindings(true);
		Map<String, String> options= new HashMap<>();
		JavaCore.setComplianceOptions(targetJava == 8 ? JavaCore.VERSION_1_8 : Integer.toString(targetJava), options);
		parser.setCompilerOptions(options);
		CompilationUnit result= (CompilationUnit) parser.createAST(null);
		for (var problem : result.getProblems()) assertFalse(problem.isError(), problem.toString());
		return result;
	}
}
