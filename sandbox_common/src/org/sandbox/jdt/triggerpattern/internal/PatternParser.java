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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Parses pattern strings into AST nodes.
 * 
 * <p>This parser handles both expression and statement patterns by embedding them
 * in a minimal wrapper structure (fake class and method) to create valid Java code
 * that can be parsed by the Eclipse JDT parser.</p>
 * 
 * @since 1.2.2
 */
public class PatternParser {
	
	/**
	 * Parses a pattern into an AST node.
	 * 
	 * @param pattern the pattern to parse
	 * @return the parsed AST node (Expression or Statement), or {@code null} if parsing fails
	 */
	public ASTNode parse(Pattern pattern) {
		if (pattern == null) {
			return null;
		}
		
		String patternValue = pattern.getValue();
		PatternKind kind = pattern.getKind();
		
		if (kind == PatternKind.EXPRESSION) {
			return parseExpression(patternValue);
		} else if (kind == PatternKind.STATEMENT) {
			return parseStatement(patternValue);
		}
		
		return null;
	}
	
	/**
	 * Parses an expression pattern.
	 * 
	 * @param expressionSnippet the expression snippet (e.g., {@code "$x + 1"})
	 * @return the parsed Expression node, or {@code null} if parsing fails
	 */
	private Expression parseExpression(String expressionSnippet) {
		// Wrap the expression in a minimal method context
		String source = "class _Pattern { void _method() { _result = " + expressionSnippet + "; } }";
		
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setCompilerOptions(JavaCore.getOptions());
		
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		
		// Navigate to the expression: CompilationUnit -> TypeDeclaration -> MethodDeclaration -> Block -> ExpressionStatement -> Assignment -> right-hand side
		if (cu.types().isEmpty()) {
			return null;
		}
		
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		if (typeDecl.getMethods().length == 0) {
			return null;
		}
		
		MethodDeclaration method = typeDecl.getMethods()[0];
		if (method.getBody() == null || method.getBody().statements().isEmpty()) {
			return null;
		}
		
		Statement stmt = (Statement) method.getBody().statements().get(0);
		if (stmt instanceof org.eclipse.jdt.core.dom.ExpressionStatement) {
			org.eclipse.jdt.core.dom.ExpressionStatement exprStmt = (org.eclipse.jdt.core.dom.ExpressionStatement) stmt;
			Expression expr = exprStmt.getExpression();
			
			// If it's an assignment, extract the right-hand side
			if (expr instanceof org.eclipse.jdt.core.dom.Assignment) {
				org.eclipse.jdt.core.dom.Assignment assignment = (org.eclipse.jdt.core.dom.Assignment) expr;
				return assignment.getRightHandSide();
			}
			
			return expr;
		}
		
		return null;
	}
	
	/**
	 * Parses a statement pattern.
	 * 
	 * @param statementSnippet the statement snippet (e.g., {@code "if ($cond) $then;"})
	 * @return the parsed Statement node, or {@code null} if parsing fails
	 */
	private Statement parseStatement(String statementSnippet) {
		// Wrap the statement in a minimal method context
		String source = "class _Pattern { void _method() { " + statementSnippet + " } }";
		
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setCompilerOptions(JavaCore.getOptions());
		
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		
		// Navigate to the statement: CompilationUnit -> TypeDeclaration -> MethodDeclaration -> Block -> Statement
		if (cu.types().isEmpty()) {
			return null;
		}
		
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		if (typeDecl.getMethods().length == 0) {
			return null;
		}
		
		MethodDeclaration method = typeDecl.getMethods()[0];
		if (method.getBody() == null || method.getBody().statements().isEmpty()) {
			return null;
		}
		
		return (Statement) method.getBody().statements().get(0);
	}
}
