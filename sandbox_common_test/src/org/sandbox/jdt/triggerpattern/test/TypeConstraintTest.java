/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.internal.PatternParser;
import org.sandbox.jdt.triggerpattern.internal.PlaceholderAstMatcher;

/**
 * Tests for type constraint support in {@link PlaceholderAstMatcher}.
 * 
 * <p>Type constraints use the syntax {@code $name:TypeName} to match only nodes of a specific type.</p>
 */
public class TypeConstraintTest {
	
	private final PatternParser parser = new PatternParser();
	
	@Test
	public void testStringLiteralConstraint() {
		// Pattern: $x + $y:StringLiteral
		Pattern pattern = new Pattern("$x + $y:StringLiteral", PatternKind.EXPRESSION);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: a + "hello"  (should match - second operand is a StringLiteral)
		String code1 = "class Test { void m() { Object z = a + \"hello\"; } }";
		Expression candidate1 = parseExpression(code1);
		assertNotNull(candidate1);
		
		PlaceholderAstMatcher matcher1 = new PlaceholderAstMatcher();
		boolean matches1 = patternNode.subtreeMatch(matcher1, candidate1);
		assertTrue(matches1, "Should match when constraint is satisfied");
		
		// Code: a + 42  (should NOT match - second operand is not a StringLiteral)
		String code2 = "class Test { void m() { Object z = a + 42; } }";
		Expression candidate2 = parseExpression(code2);
		assertNotNull(candidate2);
		
		PlaceholderAstMatcher matcher2 = new PlaceholderAstMatcher();
		boolean matches2 = patternNode.subtreeMatch(matcher2, candidate2);
		assertFalse(matches2, "Should NOT match when constraint is violated");
	}
	
	@Test
	public void testNumberLiteralConstraint() {
		// Pattern: $x + $y:NumberLiteral
		Pattern pattern = new Pattern("$x + $y:NumberLiteral", PatternKind.EXPRESSION);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: a + 42  (should match)
		String code1 = "class Test { void m() { int z = a + 42; } }";
		Expression candidate1 = parseExpression(code1);
		assertNotNull(candidate1);
		
		PlaceholderAstMatcher matcher1 = new PlaceholderAstMatcher();
		boolean matches1 = patternNode.subtreeMatch(matcher1, candidate1);
		assertTrue(matches1, "Should match NumberLiteral");
		
		// Code: a + "text"  (should NOT match)
		String code2 = "class Test { void m() { Object z = a + \"text\"; } }";
		Expression candidate2 = parseExpression(code2);
		assertNotNull(candidate2);
		
		PlaceholderAstMatcher matcher2 = new PlaceholderAstMatcher();
		boolean matches2 = patternNode.subtreeMatch(matcher2, candidate2);
		assertFalse(matches2, "Should NOT match non-NumberLiteral");
	}
	
	@Test
	public void testExpressionConstraint() {
		// Pattern: method($arg:Expression)
		Pattern pattern = new Pattern("method($arg:Expression)", PatternKind.METHOD_CALL);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: method(a + b)  (should match - any expression)
		String code1 = "class Test { void m() { method(a + b); } }";
		MethodInvocation candidate1 = parseMethodInvocation(code1);
		assertNotNull(candidate1);
		
		PlaceholderAstMatcher matcher1 = new PlaceholderAstMatcher();
		boolean matches1 = patternNode.subtreeMatch(matcher1, candidate1);
		assertTrue(matches1, "Should match any Expression");
		
		// Code: method(42)  (should match - literals are also expressions)
		String code2 = "class Test { void m() { method(42); } }";
		MethodInvocation candidate2 = parseMethodInvocation(code2);
		assertNotNull(candidate2);
		
		PlaceholderAstMatcher matcher2 = new PlaceholderAstMatcher();
		boolean matches2 = patternNode.subtreeMatch(matcher2, candidate2);
		assertTrue(matches2, "Should match literal Expression");
	}
	
	@Test
	public void testMethodInvocationConstraint() {
		// Pattern: $x + $y:MethodInvocation
		Pattern pattern = new Pattern("$x + $y:MethodInvocation", PatternKind.EXPRESSION);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: a + method()  (should match)
		String code1 = "class Test { void m() { int z = a + method(); } }";
		Expression candidate1 = parseExpression(code1);
		assertNotNull(candidate1);
		
		PlaceholderAstMatcher matcher1 = new PlaceholderAstMatcher();
		boolean matches1 = patternNode.subtreeMatch(matcher1, candidate1);
		assertTrue(matches1, "Should match MethodInvocation");
		
		// Code: a + b  (should NOT match - b is a SimpleName, not MethodInvocation)
		String code2 = "class Test { void m() { int z = a + b; } }";
		Expression candidate2 = parseExpression(code2);
		assertNotNull(candidate2);
		
		PlaceholderAstMatcher matcher2 = new PlaceholderAstMatcher();
		boolean matches2 = patternNode.subtreeMatch(matcher2, candidate2);
		assertFalse(matches2, "Should NOT match non-MethodInvocation");
	}
	
	@Test
	public void testSimpleNameConstraint() {
		// Pattern: $x + $y:SimpleName
		Pattern pattern = new Pattern("$x + $y:SimpleName", PatternKind.EXPRESSION);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: a + b  (should match)
		String code1 = "class Test { void m() { int z = a + b; } }";
		Expression candidate1 = parseExpression(code1);
		assertNotNull(candidate1);
		
		PlaceholderAstMatcher matcher1 = new PlaceholderAstMatcher();
		boolean matches1 = patternNode.subtreeMatch(matcher1, candidate1);
		assertTrue(matches1, "Should match SimpleName");
		
		// Code: a + 42  (should NOT match - 42 is not a SimpleName)
		String code2 = "class Test { void m() { int z = a + 42; } }";
		Expression candidate2 = parseExpression(code2);
		assertNotNull(candidate2);
		
		PlaceholderAstMatcher matcher2 = new PlaceholderAstMatcher();
		boolean matches2 = patternNode.subtreeMatch(matcher2, candidate2);
		assertFalse(matches2, "Should NOT match non-SimpleName");
	}
	
	private Expression parseExpression(String code) {
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(code.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = type.getMethods()[0];
		Statement stmt = (Statement) method.getBody().statements().get(0);
		
		// Extract the expression from the variable declaration
		if (stmt instanceof org.eclipse.jdt.core.dom.VariableDeclarationStatement) {
			org.eclipse.jdt.core.dom.VariableDeclarationStatement varDecl = 
				(org.eclipse.jdt.core.dom.VariableDeclarationStatement) stmt;
			org.eclipse.jdt.core.dom.VariableDeclarationFragment fragment = 
				(org.eclipse.jdt.core.dom.VariableDeclarationFragment) varDecl.fragments().get(0);
			return fragment.getInitializer();
		}
		
		return null;
	}
	
	private MethodInvocation parseMethodInvocation(String code) {
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(code.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = type.getMethods()[0];
		Statement stmt = (Statement) method.getBody().statements().get(0);
		
		// Extract the method invocation from the expression statement
		if (stmt instanceof org.eclipse.jdt.core.dom.ExpressionStatement) {
			org.eclipse.jdt.core.dom.ExpressionStatement exprStmt = 
				(org.eclipse.jdt.core.dom.ExpressionStatement) stmt;
			if (exprStmt.getExpression() instanceof MethodInvocation) {
				return (MethodInvocation) exprStmt.getExpression();
			}
		}
		
		return null;
	}
}
