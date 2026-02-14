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
