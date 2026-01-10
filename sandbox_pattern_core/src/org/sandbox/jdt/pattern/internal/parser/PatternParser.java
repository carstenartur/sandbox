/*******************************************************************************
 * Copyright (c) 2026 Sandbox contributors.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Sandbox contributors - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.pattern.internal.parser;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.sandbox.jdt.pattern.api.Pattern;
import org.sandbox.jdt.pattern.api.PatternKind;

/**
 * Parses pattern strings into AST nodes.
 * <p>
 * This parser handles code snippets by embedding them in a minimal class/method
 * structure to allow the Java parser to process them correctly.
 * </p>
 */
public class PatternParser {
	
	private static final String WRAPPER_CLASS_PREFIX = "class __Wrapper { void __method() { ";
	private static final String WRAPPER_CLASS_SUFFIX = " } }";
	private static final String EXPRESSION_STATEMENT_PREFIX = "Object __x = ";
	private static final String EXPRESSION_STATEMENT_SUFFIX = ";";

	/**
	 * Parses a pattern into an AST node.
	 * 
	 * @param pattern the pattern to parse
	 * @return the parsed AST node, or null if parsing fails
	 */
	public ASTNode parse(Pattern pattern) {
		if (pattern.getKind() == PatternKind.EXPRESSION) {
			return parseExpression(pattern.getValue());
		} else {
			return parseStatement(pattern.getValue());
		}
	}

	/**
	 * Parses an expression pattern.
	 * <p>
	 * The expression is wrapped in a statement and embedded in a class/method
	 * to allow parsing.
	 * </p>
	 * 
	 * @param expressionString the expression string
	 * @return the parsed Expression node, or null if parsing fails
	 */
	public Expression parseExpression(String expressionString) {
		// Wrap expression in assignment statement: Object __x = <expression>;
		String wrappedCode= WRAPPER_CLASS_PREFIX 
				+ EXPRESSION_STATEMENT_PREFIX 
				+ expressionString 
				+ EXPRESSION_STATEMENT_SUFFIX
				+ WRAPPER_CLASS_SUFFIX;

		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(wrappedCode.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);

		CompilationUnit cu= (CompilationUnit) parser.createAST(null);
		
		if (cu == null || cu.types().isEmpty()) {
			return null;
		}

		// Navigate to the expression: CU -> TypeDecl -> Method -> Block -> Statement -> Expression
		TypeDeclaration typeDecl= (TypeDeclaration) cu.types().get(0);
		if (typeDecl.getMethods().length == 0) {
			return null;
		}

		MethodDeclaration method= typeDecl.getMethods()[0];
		Block body= method.getBody();
		if (body == null || body.statements().isEmpty()) {
			return null;
		}

		Statement stmt= (Statement) body.statements().get(0);
		if (stmt instanceof org.eclipse.jdt.core.dom.VariableDeclarationStatement) {
			org.eclipse.jdt.core.dom.VariableDeclarationStatement varStmt= 
					(org.eclipse.jdt.core.dom.VariableDeclarationStatement) stmt;
			if (varStmt.fragments().isEmpty()) {
				return null;
			}
			org.eclipse.jdt.core.dom.VariableDeclarationFragment fragment= 
					(org.eclipse.jdt.core.dom.VariableDeclarationFragment) varStmt.fragments().get(0);
			return fragment.getInitializer();
		}

		return null;
	}

	/**
	 * Parses a statement pattern.
	 * <p>
	 * The statement is embedded in a class/method to allow parsing.
	 * </p>
	 * 
	 * @param statementString the statement string
	 * @return the parsed Statement node, or null if parsing fails
	 */
	public Statement parseStatement(String statementString) {
		String wrappedCode= WRAPPER_CLASS_PREFIX + statementString + WRAPPER_CLASS_SUFFIX;

		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(wrappedCode.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);

		CompilationUnit cu= (CompilationUnit) parser.createAST(null);
		
		if (cu == null || cu.types().isEmpty()) {
			return null;
		}

		// Navigate to the statement: CU -> TypeDecl -> Method -> Block -> Statement
		TypeDeclaration typeDecl= (TypeDeclaration) cu.types().get(0);
		if (typeDecl.getMethods().length == 0) {
			return null;
		}

		MethodDeclaration method= typeDecl.getMethods()[0];
		Block body= method.getBody();
		if (body == null || body.statements().isEmpty()) {
			return null;
		}

		return (Statement) body.statements().get(0);
	}
}
