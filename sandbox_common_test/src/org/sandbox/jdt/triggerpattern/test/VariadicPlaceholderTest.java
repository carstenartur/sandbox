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
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;
import org.sandbox.jdt.triggerpattern.internal.PatternParser;
import org.sandbox.jdt.triggerpattern.internal.PlaceholderAstMatcher;

/**
 * Tests for enhanced variadic placeholder support (Phase 1 of .hint file parser).
 * 
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Mixed argument patterns: {@code method($a, $args$)} and {@code method($args$, $last)}</li>
 *   <li>Variadic placeholders in statement sequences: {@code { $before$; return $x; }}</li>
 *   <li>Block pattern matching via {@link PatternKind#BLOCK}</li>
 * </ul>
 */
public class VariadicPlaceholderTest {
	
	private final PatternParser parser = new PatternParser();
	private final TriggerPatternEngine engine = new TriggerPatternEngine();
	
	// ========================
	// Mixed Argument Patterns
	// ========================
	
	@Test
	public void testMixedPatternFirstArgSeparateRestInList() {
		// Pattern: method($a, $args$) - first arg separate, rest in list
		Pattern pattern = new Pattern("method($a, $args$)", PatternKind.METHOD_CALL);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: method(1, 2, 3)
		String code = "class Test { void m() { method(1, 2, 3); } }";
		MethodInvocation candidate = parseMethodInvocation(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertTrue(matches, "Mixed pattern should match with first arg separate");
		
		// Check $a is bound to first argument (1)
		Object aBinding = matcher.getBindings().get("$a");
		assertNotNull(aBinding, "Should have binding for $a");
		assertTrue(aBinding instanceof ASTNode, "$a should be a single ASTNode");
		
		// Check $args$ is bound to remaining arguments [2, 3]
		Object argsBinding = matcher.getBindings().get("$args$");
		assertNotNull(argsBinding, "Should have binding for $args$");
		assertTrue(argsBinding instanceof List<?>, "$args$ should be a List");
		@SuppressWarnings("unchecked")
		List<ASTNode> argsList = (List<ASTNode>) argsBinding;
		assertEquals(2, argsList.size(), "Rest list should have 2 elements");
	}
	
	@Test
	public void testMixedPatternFirstArgSeparateEmptyRest() {
		// Pattern: method($a, $args$) - first arg separate, no rest
		Pattern pattern = new Pattern("method($a, $args$)", PatternKind.METHOD_CALL);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: method(42) - only one argument
		String code = "class Test { void m() { method(42); } }";
		MethodInvocation candidate = parseMethodInvocation(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertTrue(matches, "Mixed pattern should match with first arg and empty rest");
		
		// Check $a is bound
		assertNotNull(matcher.getBindings().get("$a"));
		
		// Check $args$ is an empty list
		Object argsBinding = matcher.getBindings().get("$args$");
		assertNotNull(argsBinding);
		@SuppressWarnings("unchecked")
		List<ASTNode> argsList = (List<ASTNode>) argsBinding;
		assertEquals(0, argsList.size(), "Rest list should be empty");
	}
	
	@Test
	public void testMixedPatternNotEnoughArgs() {
		// Pattern: method($a, $args$) requires at least 1 argument
		Pattern pattern = new Pattern("method($a, $args$)", PatternKind.METHOD_CALL);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: method() - no arguments
		String code = "class Test { void m() { method(); } }";
		MethodInvocation candidate = parseMethodInvocation(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertFalse(matches, "Should not match when there are not enough arguments for fixed placeholders");
	}
	
	@Test
	public void testMixedPatternLastArgSeparate() {
		// Pattern: method($args$, $last) - last arg separate
		Pattern pattern = new Pattern("method($args$, $last)", PatternKind.METHOD_CALL);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: method(1, 2, 3)
		String code = "class Test { void m() { method(1, 2, 3); } }";
		MethodInvocation candidate = parseMethodInvocation(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertTrue(matches, "Mixed pattern should match with last arg separate");
		
		// Check $last is bound to last argument (3)
		Object lastBinding = matcher.getBindings().get("$last");
		assertNotNull(lastBinding, "Should have binding for $last");
		assertTrue(lastBinding instanceof ASTNode, "$last should be a single ASTNode");
		
		// Check $args$ is bound to preceding arguments [1, 2]
		Object argsBinding = matcher.getBindings().get("$args$");
		assertNotNull(argsBinding, "Should have binding for $args$");
		@SuppressWarnings("unchecked")
		List<ASTNode> argsList = (List<ASTNode>) argsBinding;
		assertEquals(2, argsList.size(), "Variadic list should have 2 elements");
	}
	
	@Test
	public void testMixedPatternFirstAndLastSeparate() {
		// Pattern: method($first, $args$, $last) - first and last separate
		Pattern pattern = new Pattern("method($first, $args$, $last)", PatternKind.METHOD_CALL);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: method(1, 2, 3, 4)
		String code = "class Test { void m() { method(1, 2, 3, 4); } }";
		MethodInvocation candidate = parseMethodInvocation(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertTrue(matches, "Mixed pattern should match with first and last args separate");
		
		// Check $first and $last bindings
		assertNotNull(matcher.getBindings().get("$first"));
		assertNotNull(matcher.getBindings().get("$last"));
		
		// Check $args$ is bound to middle arguments [2, 3]
		Object argsBinding = matcher.getBindings().get("$args$");
		assertNotNull(argsBinding);
		@SuppressWarnings("unchecked")
		List<ASTNode> argsList = (List<ASTNode>) argsBinding;
		assertEquals(2, argsList.size(), "Middle list should have 2 elements");
	}
	
	// ============================
	// Block / Statement Patterns
	// ============================
	
	@Test
	public void testBlockPatternParserCreatesBlock() {
		// Parse a BLOCK pattern
		Pattern pattern = new Pattern("{ $stmts$; }", PatternKind.BLOCK);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode, "Block pattern should be parsed");
		assertTrue(patternNode instanceof Block, "Should parse to a Block node");
		Block block = (Block) patternNode;
		assertEquals(1, block.statements().size(), "Should have 1 statement (the $stmts$ placeholder)");
	}
	
	@Test
	public void testBlockPatternMatchesAllStatements() {
		// Pattern: { $stmts$; } - matches any block
		Pattern pattern = new Pattern("{ $stmts$; }", PatternKind.BLOCK);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: { a(); b(); c(); }
		String code = "class Test { void m() { a(); b(); c(); } }";
		Block candidate = parseBlock(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertTrue(matches, "Block with variadic should match all statements");
		
		Object stmtsBinding = matcher.getBindings().get("$stmts$");
		assertNotNull(stmtsBinding, "Should have binding for $stmts$");
		@SuppressWarnings("unchecked")
		List<ASTNode> stmtsList = (List<ASTNode>) stmtsBinding;
		assertEquals(3, stmtsList.size(), "Should capture all 3 statements");
	}
	
	@Test
	public void testBlockPatternStatementsBeforeReturn() {
		// Pattern: { $before$; return $x; } - statements before a return
		Pattern pattern = new Pattern("{ $before$; return $x; }", PatternKind.BLOCK);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: { a(); b(); return result; }
		String code = "class Test { int m() { a(); b(); return result; } }";
		Block candidate = parseBlock(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertTrue(matches, "Block pattern should match statements before return");
		
		// Check $before$ is bound to [a(); b();]
		Object beforeBinding = matcher.getBindings().get("$before$");
		assertNotNull(beforeBinding);
		@SuppressWarnings("unchecked")
		List<ASTNode> beforeList = (List<ASTNode>) beforeBinding;
		assertEquals(2, beforeList.size(), "Should capture 2 statements before return");
		
		// Check $x is bound to 'result'
		Object xBinding = matcher.getBindings().get("$x");
		assertNotNull(xBinding, "Should have binding for $x");
		assertTrue(xBinding instanceof SimpleName, "$x should be a SimpleName");
		assertEquals("result", ((SimpleName) xBinding).getIdentifier());
	}
	
	@Test
	public void testBlockPatternEmptyBeforeReturn() {
		// Pattern: { $before$; return $x; } - no statements before return
		Pattern pattern = new Pattern("{ $before$; return $x; }", PatternKind.BLOCK);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: { return result; }
		String code = "class Test { int m() { return result; } }";
		Block candidate = parseBlock(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertTrue(matches, "Block pattern should match with no statements before return");
		
		// Check $before$ is an empty list
		Object beforeBinding = matcher.getBindings().get("$before$");
		assertNotNull(beforeBinding);
		@SuppressWarnings("unchecked")
		List<ASTNode> beforeList = (List<ASTNode>) beforeBinding;
		assertEquals(0, beforeList.size(), "Should capture zero statements before return");
		
		// Check $x is bound
		assertNotNull(matcher.getBindings().get("$x"));
	}
	
	@Test
	public void testBlockPatternNoReturn() {
		// Pattern: { $before$; return $x; } - block without return should not match
		Pattern pattern = new Pattern("{ $before$; return $x; }", PatternKind.BLOCK);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: { a(); b(); } - no return statement
		String code = "class Test { void m() { a(); b(); } }";
		Block candidate = parseBlock(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertFalse(matches, "Block pattern requiring return should not match block without return");
	}
	
	@Test
	public void testBlockPatternMatchesEmptyBlock() {
		// Pattern: { $stmts$; } - matches empty block
		Pattern pattern = new Pattern("{ $stmts$; }", PatternKind.BLOCK);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: { } - empty block
		String code = "class Test { void m() { } }";
		Block candidate = parseBlock(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertTrue(matches, "Variadic block pattern should match empty block");
		
		Object stmtsBinding = matcher.getBindings().get("$stmts$");
		assertNotNull(stmtsBinding);
		@SuppressWarnings("unchecked")
		List<ASTNode> stmtsList = (List<ASTNode>) stmtsBinding;
		assertEquals(0, stmtsList.size(), "Should capture zero statements");
	}
	
	// ========================================
	// Engine Integration Tests
	// ========================================
	
	@Test
	public void testEngineFindsBlockPatternMatches() {
		String code = """
			class Test {
				int method1() {
					a();
					return 1;
				}
				int method2() {
					b();
					c();
					return 2;
				}
				void method3() {
					d();
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("{ $before$; return $x; }", PatternKind.BLOCK);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		// method1 and method2 have blocks ending with return
		assertEquals(2, matches.size(), "Should find two block matches");
	}
	
	@Test
	public void testEngineMixedArgPattern() {
		String code = """
			class Test {
				void m() {
					method(1, 2, 3);
					method(4, 5);
					other(6, 7, 8);
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("method($a, $args$)", PatternKind.METHOD_CALL);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		// Both method(1,2,3) and method(4,5) should match
		assertEquals(2, matches.size(), "Should find two method matches with mixed arg pattern");
	}
	
	// ========================
	// Existing test backward compat
	// ========================
	
	@Test
	public void testExistingMultiPlaceholderStillWorks() {
		// Verify backward compatibility: method($args$) still works
		Pattern pattern = new Pattern("method($args$)", PatternKind.METHOD_CALL);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		// Code: method(1, 2, 3)
		String code = "class Test { void m() { method(1, 2, 3); } }";
		MethodInvocation candidate = parseMethodInvocation(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertTrue(matches, "Original multi-placeholder pattern should still work");
		
		Object binding = matcher.getBindings().get("$args$");
		assertNotNull(binding);
		@SuppressWarnings("unchecked")
		List<ASTNode> listBinding = (List<ASTNode>) binding;
		assertEquals(3, listBinding.size());
	}
	
	@Test
	public void testExistingMultiPlaceholderZeroArgs() {
		// Verify backward compatibility: method($args$) with zero args
		Pattern pattern = new Pattern("method($args$)", PatternKind.METHOD_CALL);
		ASTNode patternNode = parser.parse(pattern);
		assertNotNull(patternNode);
		
		String code = "class Test { void m() { method(); } }";
		MethodInvocation candidate = parseMethodInvocation(code);
		assertNotNull(candidate);
		
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		boolean matches = patternNode.subtreeMatch(matcher, candidate);
		
		assertTrue(matches, "Original multi-placeholder pattern should still work with zero args");
		
		@SuppressWarnings("unchecked")
		List<ASTNode> listBinding = (List<ASTNode>) matcher.getBindings().get("$args$");
		assertEquals(0, listBinding.size());
	}
	
	// ========================
	// Helper methods
	// ========================
	
	private MethodInvocation parseMethodInvocation(String code) {
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(code.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = type.getMethods()[0];
		Statement stmt = (Statement) method.getBody().statements().get(0);
		
		if (stmt instanceof org.eclipse.jdt.core.dom.ExpressionStatement) {
			org.eclipse.jdt.core.dom.ExpressionStatement exprStmt =
				(org.eclipse.jdt.core.dom.ExpressionStatement) stmt;
			if (exprStmt.getExpression() instanceof MethodInvocation) {
				return (MethodInvocation) exprStmt.getExpression();
			}
		}
		
		return null;
	}
	
	private Block parseBlock(String code) {
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(code.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = type.getMethods()[0];
		return method.getBody();
	}
	
	private CompilationUnit parse(String code) {
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(code.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		return (CompilationUnit) astParser.createAST(null);
	}
}
