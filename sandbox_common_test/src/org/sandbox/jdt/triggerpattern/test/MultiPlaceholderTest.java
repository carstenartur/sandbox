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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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
 * Tests for multi-placeholder support in {@link PlaceholderAstMatcher}.
 * 
 * <p>Multi-placeholders use the syntax {@code $name$} and match zero or more AST nodes.</p>
 */
public class MultiPlaceholderTest {
	
	private final PatternParser parser = new PatternParser();
	
	@Test
	public void testMultiPlaceholderMatchesZeroArguments() {
		// Pattern: method($args$)
		Pattern pattern = new Pattern("method($args$)", PatternKind.METHOD_CALL);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: method()  (no arguments)
		String code = "class Test { void m() { method(); } }";
		MethodInvocation candidate = parseMethodInvocation(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertTrue(matches, "Multi-placeholder should match zero arguments");
		
		// Check binding is an empty list
		Object binding = matcher.getBindings().get("$args$");
		assertNotNull(binding, "Should have binding for $args$");
		assertTrue(binding instanceof List<?>, "Binding should be a List");
		@SuppressWarnings("unchecked")
		List<ASTNode> listBinding = (List<ASTNode>) binding;
		assertEquals(0, listBinding.size(), "List should be empty");
	}
	
	@Test
	public void testMultiPlaceholderMatchesOneArgument() {
		// Pattern: method($args$)
		Pattern pattern = new Pattern("method($args$)", PatternKind.METHOD_CALL);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: method(42)  (one argument)
		String code = "class Test { void m() { method(42); } }";
		MethodInvocation candidate = parseMethodInvocation(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertTrue(matches, "Multi-placeholder should match one argument");
		
		// Check binding is a list with one element
		Object binding = matcher.getBindings().get("$args$");
		assertNotNull(binding, "Should have binding for $args$");
		assertTrue(binding instanceof List<?>, "Binding should be a List");
		@SuppressWarnings("unchecked")
		List<ASTNode> listBinding = (List<ASTNode>) binding;
		assertEquals(1, listBinding.size(), "List should have one element");
	}
	
	@Test
	public void testMultiPlaceholderMatchesMultipleArguments() {
		// Pattern: method($args$)
		Pattern pattern = new Pattern("method($args$)", PatternKind.METHOD_CALL);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: method(1, 2, 3)  (three arguments)
		String code = "class Test { void m() { method(1, 2, 3); } }";
		MethodInvocation candidate = parseMethodInvocation(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertTrue(matches, "Multi-placeholder should match multiple arguments");
		
		// Check binding is a list with three elements
		Object binding = matcher.getBindings().get("$args$");
		assertNotNull(binding, "Should have binding for $args$");
		assertTrue(binding instanceof List<?>, "Binding should be a List");
		@SuppressWarnings("unchecked")
		List<ASTNode> listBinding = (List<ASTNode>) binding;
		assertEquals(3, listBinding.size(), "List should have three elements");
		
		// Verify all are Expression nodes
		for (ASTNode node : listBinding) {
			assertTrue(node instanceof Expression, "Each element should be an Expression");
		}
	}
	
	@Test
	public void testMultiPlaceholderConsistencyCheck() {
		// Pattern: method($args$)
		Pattern pattern = new Pattern("method($args$)", PatternKind.METHOD_CALL);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// First match: method(1, 2, 3)
		String code1 = "class Test { void m() { method(1, 2, 3); } }";
		MethodInvocation candidate1 = parseMethodInvocation(code1);
		assertNotNull(candidate1);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches1 = patternNode.subtreeMatch(matcher, candidate1);
		assertTrue(matches1, "Should match first call");
		
		// Second match with different number of arguments: method(4, 5)
		String code2 = "class Test { void m() { method(4, 5); } }";
		MethodInvocation candidate2 = parseMethodInvocation(code2);
		assertNotNull(candidate2);
		
		// Should NOT match because $args$ is already bound to 3 arguments
		boolean matches2 = patternNode.subtreeMatch(matcher, candidate2);
		assertFalse(matches2, "Should NOT match with different number of arguments");
	}
	
	@Test
	public void testMultiPlaceholderMatchesVariousArgumentTypes() {
		// Pattern: Assert.assertEquals($args$)
		Pattern pattern = new Pattern("Assert.assertEquals($args$)", PatternKind.METHOD_CALL);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: Assert.assertEquals("msg", expected, actual)
		String code = "class Test { void m() { Assert.assertEquals(\"msg\", expected, actual); } }";
		MethodInvocation candidate = parseMethodInvocation(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertTrue(matches, "Multi-placeholder should match various argument types");
		
		// Check binding has three elements
		Object binding = matcher.getBindings().get("$args$");
		assertNotNull(binding, "Should have binding for $args$");
		@SuppressWarnings("unchecked")
		List<ASTNode> listBinding = (List<ASTNode>) binding;
		assertEquals(3, listBinding.size(), "List should have three elements");
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
