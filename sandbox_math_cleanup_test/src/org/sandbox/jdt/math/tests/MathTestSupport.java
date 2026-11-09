/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.SourceEnvironment;

final class MathTestSupport {
	private static final String ENVIRONMENT = MathTestSupport.class.getName() + ".environment"; //$NON-NLS-1$

	private MathTestSupport() { }

	static String diagnosticDecline(String source, MathCleanUpOptions options) throws Exception {
		var result=analyze(parse(source),source,options,null,-1,0);
		assertFalse(result.changed(),result.diagnostics().toString());
		assertTrue(result.diagnostics().stream().anyMatch(d->d.message().startsWith("GENERATED_DIAGNOSTIC_INTRODUCED")),result.diagnostics().toString());
		var document=new org.eclipse.jface.text.Document(source);result.newEdit().apply(document);
		assertEquals(source,document.get());return document.get();
	}

	static MathematicalAnalysis.Analysis analyze(CompilationUnit ast, String source, MathCleanUpOptions options,
			IProgressMonitor monitor, int selectionOffset, int selectionLength) {
		if (!(ast.getProperty(ENVIRONMENT) instanceof SourceEnvironment environment))
			throw new IllegalArgumentException("AST must be parsed by MathTestSupport"); //$NON-NLS-1$
		// Retain this AST and its actual parse settings, including intentionally stale AST fixtures.
		return MathematicalAnalysis.analyze(ast, source, options, monitor, selectionOffset, selectionLength, environment);
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
		var environment = new SourceEnvironment("Calculation.java", List.of(classpath), List.of(), List.of(), //$NON-NLS-1$
				StrictCompilerOptions.options(targetJava));
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setUnitName(environment.unitName());
		parser.setEnvironment(environment.classpath().toArray(String[]::new), new String[0], null, true);
		parser.setResolveBindings(true);
		parser.setCompilerOptions(environment.compilerOptions());
		CompilationUnit result= (CompilationUnit) parser.createAST(null);
		for (var problem : result.getProblems()) assertFalse(problem.isError(), problem.toString());
		result.setProperty(ENVIRONMENT, environment);
		return result;
	}
}
