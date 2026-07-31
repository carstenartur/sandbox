/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;

/** Specialized parser used by {@code PatternIndex} for explicit type headers. */
public final class TypeDeclarationPatternParser {

	public ASTNode parse(String source) {
		if (source == null || source.isBlank()) {
			return null;
		}
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		parser.setStatementsRecovery(true);
		Map<String, String> options= JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		CompilationUnit unit= (CompilationUnit) parser.createAST(null);
		for (org.eclipse.jdt.core.compiler.IProblem problem : unit.getProblems()) {
			if (problem.isError()) {
				return null;
			}
		}
		if (unit.types().size() != 1 || !(unit.types().get(0) instanceof AbstractTypeDeclaration type)) {
			return null;
		}
		if (!type.bodyDeclarations().isEmpty()) {
			return null;
		}
		if (type instanceof EnumDeclaration enumeration && !enumeration.enumConstants().isEmpty()) {
			return null;
		}
		return type;
	}
}
