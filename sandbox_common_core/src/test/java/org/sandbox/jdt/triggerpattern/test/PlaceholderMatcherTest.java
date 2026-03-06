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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.internal.PatternParser;
import org.sandbox.jdt.triggerpattern.internal.PlaceholderAstMatcher;

/**
 * Tests for {@link PlaceholderAstMatcher}.
 */
public class PlaceholderMatcherTest {
	
	private final PatternParser parser = new PatternParser();
	
	@Test
	public void testPlaceholderBinds() {
		// Pattern: $x + 1
		Pattern pattern = Pattern.of("$x + 1", PatternKind.EXPRESSION);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: y + 1
		String code = "class Test { void m() { int z = y + 1; } }";
		Expression candidate = parseExpression(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertTrue(matches, "Pattern should match the candidate");
		
		Map<String, Object> bindings = matcher.getBindings();
		assertEquals(1, bindings.size(), "Should have one binding");
		assertTrue(bindings.containsKey("$x"), "Should have binding for $x");
		
		Object boundValue = bindings.get("$x");
		assertTrue(boundValue instanceof ASTNode, "Bound value should be an ASTNode");
		ASTNode boundNode = (ASTNode) boundValue;
		assertTrue(boundNode instanceof SimpleName, "Bound node should be a SimpleName");
		assertEquals("y", ((SimpleName) boundNode).getIdentifier(), "Bound identifier should be 'y'");
	}
	
	@Test
	public void testMultipleSamePlaceholdersMustMatch() {
		// Pattern: $x + $x  (both placeholders must bind to the same thing)
		Pattern pattern = Pattern.of("$x + $x", PatternKind.EXPRESSION);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: y + y  (should match)
		String code1 = "class Test { void m() { int z = y + y; } }";
		Expression candidate1 = parseExpression(code1);
		assertNotNull(candidate1);
		
		PlaceholderAstMatcher matcher1 = new PlaceholderAstMatcher();
		boolean matches1 = patternNode.subtreeMatch(matcher1, candidate1);
		assertTrue(matches1, "Pattern $x + $x should match y + y");
		
		// Code: y + z  (should NOT match - different variables)
		String code2 = "class Test { void m() { int w = y + z; } }";
		Expression candidate2 = parseExpression(code2);
		assertNotNull(candidate2);
		
		PlaceholderAstMatcher matcher2 = new PlaceholderAstMatcher();
		boolean matches2 = patternNode.subtreeMatch(matcher2, candidate2);
		assertFalse(matches2, "Pattern $x + $x should NOT match y + z");
	}
	
	@Test
	public void testDifferentPlaceholdersBindIndependently() {
		// Pattern: $x + $y
		Pattern pattern = Pattern.of("$x + $y", PatternKind.EXPRESSION);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: a + b
		String code = "class Test { void m() { int z = a + b; } }";
		Expression candidate = parseExpression(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertTrue(matches, "Pattern should match the candidate");
		
		Map<String, Object> bindings = matcher.getBindings();
		assertEquals(2, bindings.size(), "Should have two bindings");
		assertTrue(bindings.containsKey("$x"), "Should have binding for $x");
		assertTrue(bindings.containsKey("$y"), "Should have binding for $y");
	}
	
	@Test
	public void testNonPlaceholderMustMatchExactly() {
		// Pattern: $x + 1  (the literal 1 must match exactly)
		Pattern pattern = Pattern.of("$x + 1", PatternKind.EXPRESSION);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: y + 2  (should NOT match - different literal)
		String code = "class Test { void m() { int z = y + 2; } }";
		Expression candidate = parseExpression(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertFalse(matches, "Pattern $x + 1 should NOT match y + 2");
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

	// --- Bitwise operator tests (Issue 1) ---

	@Test
	public void testBitwiseOrWithPlaceholders() {
		// Pattern: $x | $y
		Pattern pattern = Pattern.of("$x | $y", PatternKind.EXPRESSION);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode, "Pattern should be parsed as InfixExpression");

		// Code: flags | mask
		String code = "class Test { void m() { int z = flags | mask; } }";
		Expression candidate = parseExpression(code);
		assertNotNull(candidate);

		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);

		assertTrue(matches, "Pattern $x | $y should match flags | mask");
		Map<String, Object> bindings = matcher.getBindings();
		assertEquals(2, bindings.size());
		assertTrue(bindings.containsKey("$x"));
		assertTrue(bindings.containsKey("$y"));
	}

	@Test
	public void testBitwiseOrDoesNotMatchDifferentOperator() {
		// Pattern: $x | $y
		Pattern pattern = Pattern.of("$x | $y", PatternKind.EXPRESSION);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);

		// Code: flags & mask (bitwise AND, not OR)
		String code = "class Test { void m() { int z = flags & mask; } }";
		Expression candidate = parseExpression(code);
		assertNotNull(candidate);

		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		assertFalse(matches, "Pattern $x | $y should NOT match flags & mask");
	}

	@Test
	public void testBitwiseAndWithPlaceholders() {
		// Pattern: $x & $y
		Pattern pattern = Pattern.of("$x & $y", PatternKind.EXPRESSION);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);

		// Code: value & MASK
		String code = "class Test { void m() { int z = value & MASK; } }";
		Expression candidate = parseExpression(code);
		assertNotNull(candidate);

		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		assertTrue(matches, "Pattern $x & $y should match value & MASK");
	}

	@Test
	public void testBitwiseXorWithPlaceholders() {
		// Pattern: $x ^ $y
		Pattern pattern = Pattern.of("$x ^ $y", PatternKind.EXPRESSION);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);

		// Code: a ^ b
		String code = "class Test { void m() { int z = a ^ b; } }";
		Expression candidate = parseExpression(code);
		assertNotNull(candidate);

		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		assertTrue(matches, "Pattern $x ^ $y should match a ^ b");
	}

	@Test
	public void testShiftLeftWithPlaceholders() {
		// Pattern: $x << $y
		Pattern pattern = Pattern.of("$x << $y", PatternKind.EXPRESSION);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);

		// Code: value << 2
		String code = "class Test { void m() { int z = value << 2; } }";
		Expression candidate = parseExpression(code);
		assertNotNull(candidate);

		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		assertTrue(matches, "Pattern $x << $y should match value << 2");
	}

	@Test
	public void testShiftRightWithPlaceholders() {
		// Pattern: $x >> $y
		Pattern pattern = Pattern.of("$x >> $y", PatternKind.EXPRESSION);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);

		// Code: value >> 4
		String code = "class Test { void m() { int z = value >> 4; } }";
		Expression candidate = parseExpression(code);
		assertNotNull(candidate);

		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		assertTrue(matches, "Pattern $x >> $y should match value >> 4");
	}

	@Test
	public void testChainedBitwiseOrWithPlaceholders() {
		// Pattern: $x | $y | $z (extended operands)
		Pattern pattern = Pattern.of("$x | $y | $z", PatternKind.EXPRESSION);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);

		// Code: a | b | c
		String code = "class Test { void m() { int z = a | b | c; } }";
		Expression candidate = parseExpression(code);
		assertNotNull(candidate);

		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		assertTrue(matches, "Pattern $x | $y | $z should match a | b | c");
		assertEquals(3, matcher.getBindings().size());
	}

	@Test
	public void testBitwiseOrWithConcreteQualifiedNames() {
		// Concrete pattern: SHOW | LOG (no placeholders, exact match)
		Pattern pattern = Pattern.of("SHOW | LOG", PatternKind.EXPRESSION);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);

		// Code: SHOW | LOG
		String code = "class Test { static int SHOW=1; static int LOG=2; void m() { int z = SHOW | LOG; } }";
		Expression candidate = parseExpression(code);
		assertNotNull(candidate);

		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		assertTrue(matches, "Concrete bitwise OR pattern should match");
	}
}
