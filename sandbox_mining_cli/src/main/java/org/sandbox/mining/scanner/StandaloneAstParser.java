/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.mining.scanner;

import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * Standalone AST parser that works without an Eclipse workspace.
 * Uses Eclipse JDT's ASTParser in source-only mode.
 */
public class StandaloneAstParser {

	private final String javaVersion;

	public StandaloneAstParser() {
		this("21");
	}

	public StandaloneAstParser(String javaVersion) {
		this.javaVersion = javaVersion;
	}

	/**
	 * Parses a Java source string into a CompilationUnit.
	 *
	 * @param source the Java source code
	 * @return the parsed CompilationUnit
	 */
	public CompilationUnit parse(String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(false);
		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_SOURCE, javaVersion);
		options.put(JavaCore.COMPILER_COMPLIANCE, javaVersion);
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, javaVersion);
		parser.setCompilerOptions(options);
		return (CompilationUnit) parser.createAST(null);
	}
}
