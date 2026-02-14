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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.GuardContext;
import org.sandbox.jdt.triggerpattern.api.GuardExpression;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.internal.GuardExpressionParser;
import org.sandbox.jdt.triggerpattern.internal.GuardRegistry;

/**
 * Tests for guard expressions: parsing and evaluation.
 * 
 * @see GuardExpressionParser
 * @see GuardExpression
 * @see GuardRegistry
 */
public class GuardExpressionTest {
	
	private final GuardExpressionParser parser = new GuardExpressionParser();
	
	// --- Parsing tests ---
	
	@Test
	public void testParseSimpleFunctionCall() {
		GuardExpression expr = parser.parse("sourceVersionGE(11)");
		
		assertNotNull(expr);
		assertInstanceOf(GuardExpression.FunctionCall.class, expr);
		
		GuardExpression.FunctionCall fc = (GuardExpression.FunctionCall) expr;
		assertEquals("sourceVersionGE", fc.name());
		assertEquals(List.of("11"), fc.args());
	}
	
	@Test
	public void testParseInstanceOf() {
		GuardExpression expr = parser.parse("$x instanceof String");
		
		assertNotNull(expr);
		assertInstanceOf(GuardExpression.FunctionCall.class, expr);
		
		GuardExpression.FunctionCall fc = (GuardExpression.FunctionCall) expr;
		assertEquals("instanceof", fc.name());
		assertEquals(List.of("$x", "String"), fc.args());
	}
	
	@Test
	public void testParseAnd() {
		GuardExpression expr = parser.parse("$x instanceof String && sourceVersionGE(11)");
		
		assertNotNull(expr);
		assertInstanceOf(GuardExpression.And.class, expr);
		
		GuardExpression.And and = (GuardExpression.And) expr;
		assertInstanceOf(GuardExpression.FunctionCall.class, and.left());
		assertInstanceOf(GuardExpression.FunctionCall.class, and.right());
		
		GuardExpression.FunctionCall left = (GuardExpression.FunctionCall) and.left();
		assertEquals("instanceof", left.name());
		
		GuardExpression.FunctionCall right = (GuardExpression.FunctionCall) and.right();
		assertEquals("sourceVersionGE", right.name());
	}
	
	@Test
	public void testParseOr() {
		GuardExpression expr = parser.parse("sourceVersionGE(11) || sourceVersionGE(17)");
		
		assertNotNull(expr);
		assertInstanceOf(GuardExpression.Or.class, expr);
		
		GuardExpression.Or or = (GuardExpression.Or) expr;
		assertInstanceOf(GuardExpression.FunctionCall.class, or.left());
		assertInstanceOf(GuardExpression.FunctionCall.class, or.right());
	}
	
	@Test
	public void testParseNot() {
		GuardExpression expr = parser.parse("!isStatic($x)");
		
		assertNotNull(expr);
		assertInstanceOf(GuardExpression.Not.class, expr);
		
		GuardExpression.Not not = (GuardExpression.Not) expr;
		assertInstanceOf(GuardExpression.FunctionCall.class, not.operand());
		
		GuardExpression.FunctionCall fc = (GuardExpression.FunctionCall) not.operand();
		assertEquals("isStatic", fc.name());
		assertEquals(List.of("$x"), fc.args());
	}
	
	@Test
	public void testParseParentheses() {
		GuardExpression expr = parser.parse("(sourceVersionGE(11) || sourceVersionGE(17)) && isFinal($x)");
		
		assertNotNull(expr);
		assertInstanceOf(GuardExpression.And.class, expr);
		
		GuardExpression.And and = (GuardExpression.And) expr;
		assertInstanceOf(GuardExpression.Or.class, and.left());
		assertInstanceOf(GuardExpression.FunctionCall.class, and.right());
	}
	
	@Test
	public void testParseMultipleArgs() {
		GuardExpression expr = parser.parse("sourceVersionBetween(11, 17)");
		
		assertNotNull(expr);
		assertInstanceOf(GuardExpression.FunctionCall.class, expr);
		
		GuardExpression.FunctionCall fc = (GuardExpression.FunctionCall) expr;
		assertEquals("sourceVersionBetween", fc.name());
		assertEquals(List.of("11", "17"), fc.args());
	}
	
	@Test
	public void testParseEmptyThrows() {
		assertThrows(IllegalArgumentException.class, () -> parser.parse(""));
		assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
	}
	
	// --- Evaluation tests ---
	
	@Test
	public void testEvaluateSourceVersionGE() {
		GuardContext ctx = createContextWithVersion("17"); //$NON-NLS-1$
		GuardExpression expr = parser.parse("sourceVersionGE(11)");
		
		assertTrue(expr.evaluate(ctx), "Version 17 should be >= 11");
	}
	
	@Test
	public void testEvaluateSourceVersionGEFalse() {
		GuardContext ctx = createContextWithVersion("1.8"); //$NON-NLS-1$
		GuardExpression expr = parser.parse("sourceVersionGE(11)");
		
		assertFalse(expr.evaluate(ctx), "Version 1.8 should not be >= 11");
	}
	
	@Test
	public void testEvaluateSourceVersionLE() {
		GuardContext ctx = createContextWithVersion("11"); //$NON-NLS-1$
		GuardExpression expr = parser.parse("sourceVersionLE(17)");
		
		assertTrue(expr.evaluate(ctx), "Version 11 should be <= 17");
	}
	
	@Test
	public void testEvaluateSourceVersionBetween() {
		GuardContext ctx = createContextWithVersion("17"); //$NON-NLS-1$
		GuardExpression expr = parser.parse("sourceVersionBetween(11, 21)");
		
		assertTrue(expr.evaluate(ctx), "Version 17 should be between 11 and 21");
	}
	
	@Test
	public void testEvaluateMatchesAny() {
		Map<String, Object> bindings = new HashMap<>();
		ASTNode dummyNode = createDummyNode();
		bindings.put("$x", dummyNode);
		
		GuardContext ctx = createContextWithBindings(bindings);
		GuardExpression expr = parser.parse("matchesAny($x)");
		
		assertTrue(expr.evaluate(ctx), "Bound placeholder should match");
	}
	
	@Test
	public void testEvaluateMatchesNone() {
		Map<String, Object> bindings = new HashMap<>();
		
		GuardContext ctx = createContextWithBindings(bindings);
		GuardExpression expr = parser.parse("matchesNone($x)");
		
		assertTrue(expr.evaluate(ctx), "Unbound placeholder should match matchesNone");
	}
	
	@Test
	public void testEvaluateCombinedExpression() {
		GuardContext ctx = createContextWithVersion("17"); //$NON-NLS-1$
		GuardExpression expr = parser.parse("sourceVersionGE(11) && sourceVersionLE(21)");
		
		assertTrue(expr.evaluate(ctx), "Version 17 should be >= 11 and <= 21");
	}
	
	@Test
	public void testEvaluateNotExpression() {
		GuardContext ctx = createContextWithVersion("17"); //$NON-NLS-1$
		GuardExpression expr = parser.parse("!sourceVersionLE(11)");
		
		assertTrue(expr.evaluate(ctx), "Version 17 should NOT be <= 11");
	}
	
	@Test
	public void testEvaluateOrExpression() {
		GuardContext ctx = createContextWithVersion("11"); //$NON-NLS-1$
		GuardExpression expr = parser.parse("sourceVersionGE(17) || sourceVersionGE(11)");
		
		assertTrue(expr.evaluate(ctx), "Version 11 should be >= 11 via OR");
	}
	
	@Test
	public void testGuardRegistryGet() {
		GuardRegistry registry = GuardRegistry.getInstance();
		
		assertNotNull(registry.get("instanceof")); //$NON-NLS-1$
		assertNotNull(registry.get("matchesAny")); //$NON-NLS-1$
		assertNotNull(registry.get("matchesNone")); //$NON-NLS-1$
		assertNotNull(registry.get("hasNoSideEffect")); //$NON-NLS-1$
		assertNotNull(registry.get("sourceVersionGE")); //$NON-NLS-1$
		assertNotNull(registry.get("sourceVersionLE")); //$NON-NLS-1$
		assertNotNull(registry.get("sourceVersionBetween")); //$NON-NLS-1$
		assertNotNull(registry.get("isStatic")); //$NON-NLS-1$
		assertNotNull(registry.get("isFinal")); //$NON-NLS-1$
		assertNotNull(registry.get("hasAnnotation")); //$NON-NLS-1$
		assertNotNull(registry.get("isDeprecated")); //$NON-NLS-1$
		assertNotNull(registry.get("referencedIn")); //$NON-NLS-1$
		assertNotNull(registry.get("elementKindMatches")); //$NON-NLS-1$
	}
	
	@Test
	public void testCustomGuardRegistration() {
		GuardRegistry registry = GuardRegistry.getInstance();
		
		// Register a custom guard
		registry.register("alwaysTrue", (ctx, args) -> true); //$NON-NLS-1$
		assertNotNull(registry.get("alwaysTrue")); //$NON-NLS-1$
		
		// Evaluate the custom guard
		GuardContext ctx = createContextWithVersion("17"); //$NON-NLS-1$
		GuardExpression expr = new GuardExpression.FunctionCall("alwaysTrue", List.of()); //$NON-NLS-1$
		assertTrue(expr.evaluate(ctx), "Custom guard should return true");
		
		// Register and evaluate a custom guard that returns false
		registry.register("alwaysFalse", (ctx2, args) -> false); //$NON-NLS-1$
		GuardExpression exprFalse = new GuardExpression.FunctionCall("alwaysFalse", List.of()); //$NON-NLS-1$
		assertFalse(exprFalse.evaluate(ctx), "Custom guard should return false");
	}
	
	@Test
	public void testParseInstanceOfArray() {
		GuardExpression expr = parser.parse("$x instanceof String[]");
		
		assertNotNull(expr);
		assertInstanceOf(GuardExpression.FunctionCall.class, expr);
		
		GuardExpression.FunctionCall fc = (GuardExpression.FunctionCall) expr;
		assertEquals("instanceof", fc.name());
		assertEquals(List.of("$x", "String[]"), fc.args());
	}
	
	@Test
	public void testParseMatchesAnyWithLiterals() {
		GuardExpression expr = parser.parse("matchesAny($x, \"foo\", \"bar\")");
		
		assertNotNull(expr);
		assertInstanceOf(GuardExpression.FunctionCall.class, expr);
		
		GuardExpression.FunctionCall fc = (GuardExpression.FunctionCall) expr;
		assertEquals("matchesAny", fc.name());
		assertEquals(3, fc.args().size());
		assertEquals("$x", fc.args().get(0));
		assertEquals("\"foo\"", fc.args().get(1));
		assertEquals("\"bar\"", fc.args().get(2));
	}
	
	@Test
	public void testParseMatchesNoneWithLiterals() {
		GuardExpression expr = parser.parse("matchesNone($x, \"foo\", \"bar\")");
		
		assertNotNull(expr);
		assertInstanceOf(GuardExpression.FunctionCall.class, expr);
		
		GuardExpression.FunctionCall fc = (GuardExpression.FunctionCall) expr;
		assertEquals("matchesNone", fc.name());
		assertEquals(3, fc.args().size());
	}
	
	@Test
	public void testParseReferencedIn() {
		GuardExpression expr = parser.parse("referencedIn($x, $y)");
		
		assertNotNull(expr);
		assertInstanceOf(GuardExpression.FunctionCall.class, expr);
		
		GuardExpression.FunctionCall fc = (GuardExpression.FunctionCall) expr;
		assertEquals("referencedIn", fc.name());
		assertEquals(List.of("$x", "$y"), fc.args());
	}
	
	@Test
	public void testParseElementKindMatches() {
		GuardExpression expr = parser.parse("elementKindMatches($x, FIELD)");
		
		assertNotNull(expr);
		assertInstanceOf(GuardExpression.FunctionCall.class, expr);
		
		GuardExpression.FunctionCall fc = (GuardExpression.FunctionCall) expr;
		assertEquals("elementKindMatches", fc.name());
		assertEquals(List.of("$x", "FIELD"), fc.args());
	}
	
	@Test
	public void testEvaluateReferencedIn() {
		// Create a source with variable x used in expression y
		String code = "class Test { void m() { int x = 1; int y = x + 2; } }"; //$NON-NLS-1$
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(code.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_SOURCE, "17"); //$NON-NLS-1$
		astParser.setCompilerOptions(options);
		
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		org.eclipse.jdt.core.dom.TypeDeclaration typeDecl = (org.eclipse.jdt.core.dom.TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		org.eclipse.jdt.core.dom.Block body = method.getBody();
		
		// Get statement "int x = 1;" and "int y = x + 2;"
		ASTNode stmt1 = (ASTNode) body.statements().get(0); // int x = 1
		ASTNode stmt2 = (ASTNode) body.statements().get(1); // int y = x + 2
		
		// Bind $x to the SimpleName "x" from the first statement
		org.eclipse.jdt.core.dom.VariableDeclarationStatement vds1 = (org.eclipse.jdt.core.dom.VariableDeclarationStatement) stmt1;
		org.eclipse.jdt.core.dom.VariableDeclarationFragment vdf1 = (org.eclipse.jdt.core.dom.VariableDeclarationFragment) vds1.fragments().get(0);
		SimpleName xName = vdf1.getName();
		
		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", xName);
		bindings.put("$y", stmt2);
		
		GuardContext ctx = createContextWithBindings(bindings);
		GuardExpression expr = parser.parse("referencedIn($x, $y)");
		
		assertTrue(expr.evaluate(ctx), "Variable x should be referenced in y = x + 2");
	}
	
	@Test
	public void testEvaluateReferencedInFalse() {
		// Create a source with variable x NOT used in expression y
		String code = "class Test { void m() { int x = 1; int y = 42; } }"; //$NON-NLS-1$
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(code.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_SOURCE, "17"); //$NON-NLS-1$
		astParser.setCompilerOptions(options);
		
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		org.eclipse.jdt.core.dom.TypeDeclaration typeDecl = (org.eclipse.jdt.core.dom.TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		org.eclipse.jdt.core.dom.Block body = method.getBody();
		
		ASTNode stmt1 = (ASTNode) body.statements().get(0);
		ASTNode stmt2 = (ASTNode) body.statements().get(1);
		
		org.eclipse.jdt.core.dom.VariableDeclarationStatement vds1 = (org.eclipse.jdt.core.dom.VariableDeclarationStatement) stmt1;
		org.eclipse.jdt.core.dom.VariableDeclarationFragment vdf1 = (org.eclipse.jdt.core.dom.VariableDeclarationFragment) vds1.fragments().get(0);
		SimpleName xName = vdf1.getName();
		
		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", xName);
		bindings.put("$y", stmt2);
		
		GuardContext ctx = createContextWithBindings(bindings);
		GuardExpression expr = parser.parse("referencedIn($x, $y)");
		
		assertFalse(expr.evaluate(ctx), "Variable x should NOT be referenced in y = 42");
	}
	
	@Test
	public void testEvaluateElementKindMatchesMethod() {
		String code = "class Test { void myMethod() { } }"; //$NON-NLS-1$
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(code.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		astParser.setCompilerOptions(JavaCore.getOptions());
		
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		org.eclipse.jdt.core.dom.TypeDeclaration typeDecl = (org.eclipse.jdt.core.dom.TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		
		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", method);
		
		GuardContext ctx = createContextWithBindings(bindings);
		
		GuardExpression exprMethod = parser.parse("elementKindMatches($x, METHOD)");
		assertTrue(exprMethod.evaluate(ctx), "MethodDeclaration should match METHOD kind");
		
		GuardExpression exprField = parser.parse("elementKindMatches($x, FIELD)");
		assertFalse(exprField.evaluate(ctx), "MethodDeclaration should not match FIELD kind");
	}
	
	@Test
	public void testEvaluateMatchesAnyWithLiterals() {
		// Parse code with a method invocation to get a SimpleName as binding
		String code = "class Test { void m() { String x = \"hello\"; } }"; //$NON-NLS-1$
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(code.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		astParser.setCompilerOptions(JavaCore.getOptions());
		
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		org.eclipse.jdt.core.dom.TypeDeclaration typeDecl = (org.eclipse.jdt.core.dom.TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		org.eclipse.jdt.core.dom.VariableDeclarationStatement vds = (org.eclipse.jdt.core.dom.VariableDeclarationStatement) method.getBody().statements().get(0);
		org.eclipse.jdt.core.dom.VariableDeclarationFragment vdf = (org.eclipse.jdt.core.dom.VariableDeclarationFragment) vds.fragments().get(0);
		ASTNode initializer = vdf.getInitializer(); // StringLiteral "hello"
		
		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", initializer);
		
		GuardContext ctx = createContextWithBindings(bindings);
		
		// "hello" should not match "foo" or "bar"
		GuardExpression expr = parser.parse("matchesAny($x, \"foo\", \"bar\")");
		assertFalse(expr.evaluate(ctx), "\"hello\" should not match \"foo\" or \"bar\"");
	}
	
	@Test
	public void testEvaluateMatchesNoneWithLiterals() {
		String code = "class Test { void m() { String x = \"hello\"; } }"; //$NON-NLS-1$
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(code.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		astParser.setCompilerOptions(JavaCore.getOptions());
		
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		org.eclipse.jdt.core.dom.TypeDeclaration typeDecl = (org.eclipse.jdt.core.dom.TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		org.eclipse.jdt.core.dom.VariableDeclarationStatement vds = (org.eclipse.jdt.core.dom.VariableDeclarationStatement) method.getBody().statements().get(0);
		org.eclipse.jdt.core.dom.VariableDeclarationFragment vdf = (org.eclipse.jdt.core.dom.VariableDeclarationFragment) vds.fragments().get(0);
		ASTNode initializer = vdf.getInitializer();
		
		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", initializer);
		
		GuardContext ctx = createContextWithBindings(bindings);
		
		// "hello" should not match "foo" or "bar", so matchesNone returns true
		GuardExpression expr = parser.parse("matchesNone($x, \"foo\", \"bar\")");
		assertTrue(expr.evaluate(ctx), "\"hello\" should match none of \"foo\", \"bar\"");
	}
	
	// --- Helper methods ---
	
	private GuardContext createContextWithVersion(String version) {
		Map<String, Object> bindings = Collections.emptyMap();
		ASTNode dummyNode = createDummyNode();
		Match match = new Match(dummyNode, bindings, 0, 0);
		
		Map<String, String> options = new HashMap<>();
		options.put(JavaCore.COMPILER_SOURCE, version);
		
		return GuardContext.fromMatch(match, null, options);
	}
	
	private GuardContext createContextWithBindings(Map<String, Object> bindings) {
		ASTNode dummyNode = createDummyNode();
		Match match = new Match(dummyNode, bindings, 0, 0);
		
		Map<String, String> options = new HashMap<>();
		options.put(JavaCore.COMPILER_SOURCE, "17"); //$NON-NLS-1$
		
		return GuardContext.fromMatch(match, null, options);
	}
	
	private ASTNode createDummyNode() {
		String code = "class Dummy { }"; //$NON-NLS-1$
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(code.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		astParser.setCompilerOptions(JavaCore.getOptions());
		
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		// Return the type declaration as a non-null node
		return cu.types().isEmpty() ? cu : (ASTNode) cu.types().get(0);
	}
}
