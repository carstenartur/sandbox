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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.ConstraintVariableType;
import org.sandbox.jdt.triggerpattern.api.Hint;
import org.sandbox.jdt.triggerpattern.api.HintContext;
import org.sandbox.jdt.triggerpattern.api.HintKind;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.Severity;
import org.sandbox.jdt.triggerpattern.api.TriggerPattern;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;
import org.sandbox.jdt.triggerpattern.api.TriggerPatterns;
import org.sandbox.jdt.triggerpattern.api.TriggerTreeKind;

/**
 * Tests for NetBeans TriggerPattern API parity.
 * 
 * <p>This test class validates that the sandbox TriggerPattern implementation
 * has feature parity with NetBeans' spi.java.hints TriggerPattern API.</p>
 */
public class NetBeansParityTest {
	
	private final TriggerPatternEngine engine = new TriggerPatternEngine();
	
	// ========== Tests for New Enums ==========
	
	@Test
	public void testSeverityEnum() {
		assertEquals(4, Severity.values().length, "Severity should have 4 values");
		assertNotNull(Severity.ERROR);
		assertNotNull(Severity.WARNING);
		assertNotNull(Severity.INFO);
		assertNotNull(Severity.HINT);
	}
	
	@Test
	public void testHintKindEnum() {
		assertEquals(2, HintKind.values().length, "HintKind should have 2 values");
		assertNotNull(HintKind.INSPECTION);
		assertNotNull(HintKind.ACTION);
	}
	
	// ========== Tests for @TriggerPatterns Container ==========
	
	@Test
	public void testTriggerPatternsAnnotation() throws Exception {
		Method method = TestHintProvider.class.getMethod("multiPatternHint", HintContext.class);
		TriggerPatterns patterns = method.getAnnotation(TriggerPatterns.class);
		
		assertNotNull(patterns, "@TriggerPatterns should be present");
		assertEquals(2, patterns.value().length, "Should have 2 patterns");
		assertEquals("$x + 1", patterns.value()[0].value());
		assertEquals("$x + 1L", patterns.value()[1].value());
	}
	
	// ========== Tests for @ConstraintVariableType ==========
	
	@Test
	public void testConstraintVariableTypeAnnotation() throws Exception {
		Method method = TestHintProvider.class.getMethod("constrainedHint", HintContext.class);
		TriggerPattern pattern = method.getAnnotation(TriggerPattern.class);
		
		assertNotNull(pattern, "@TriggerPattern should be present");
		assertEquals(1, pattern.constraints().length, "Should have 1 constraint");
		
		ConstraintVariableType constraint = pattern.constraints()[0];
		assertEquals("$x", constraint.variable());
		assertEquals("java.lang.String", constraint.type());
	}
	
	// ========== Tests for @TriggerTreeKind ==========
	
	@Test
	public void testTriggerTreeKindAnnotation() throws Exception {
		Method method = TestHintProvider.class.getMethod("treeKindHint", HintContext.class);
		TriggerTreeKind treeKind = method.getAnnotation(TriggerTreeKind.class);
		
		assertNotNull(treeKind, "@TriggerTreeKind should be present");
		assertEquals(1, treeKind.value().length, "Should have 1 node type");
		assertEquals(ASTNode.METHOD_DECLARATION, treeKind.value()[0]);
	}
	
	// ========== Tests for Enhanced @Hint ==========
	
	@Test
	public void testEnhancedHintAnnotation() throws Exception {
		Method method = TestHintProvider.class.getMethod("enhancedHint", HintContext.class);
		Hint hint = method.getAnnotation(Hint.class);
		
		assertNotNull(hint, "@Hint should be present");
		assertEquals("test-hint-id", hint.id());
		assertEquals("test-category", hint.category());
		assertEquals(2, hint.suppressWarnings().length);
		assertEquals("unused", hint.suppressWarnings()[0]);
		assertEquals("deprecation", hint.suppressWarnings()[1]);
		assertEquals(HintKind.ACTION, hint.hintKind());
		assertEquals("11", hint.minSourceVersion());
		assertEquals(Severity.WARNING, hint.severity());
	}
	
	// ========== Tests for $_ and $this Auto-Bindings ==========
	
	@Test
	public void testAutoBindingDollarUnderscore() {
		String code = """
			class TestClass {
				void method() {
					int x = a + 1;
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x + 1", PatternKind.EXPRESSION);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size());
		Match match = matches.get(0);
		
		// Check $_ binding (matched node itself)
		ASTNode dollarUnderscore = match.getBinding("$_");
		assertNotNull(dollarUnderscore, "$_ should be auto-bound to the matched node");
		assertEquals(match.getMatchedNode(), dollarUnderscore, "$_ should equal the matched node");
	}
	
	@Test
	public void testAutoBindingDollarThis() {
		String code = """
			class TestClass {
				void method() {
					int x = a + 1;
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x + 1", PatternKind.EXPRESSION);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size());
		Match match = matches.get(0);
		
		// Check $this binding (enclosing type)
		ASTNode dollarThis = match.getBinding("$this");
		assertNotNull(dollarThis, "$this should be auto-bound to enclosing type");
		assertTrue(dollarThis instanceof TypeDeclaration, "$this should be a TypeDeclaration");
		assertEquals("TestClass", ((TypeDeclaration) dollarThis).getName().getIdentifier());
	}
	
	// ========== Tests for HintContext Enhancements ==========
	
	@Test
	public void testHintContextCancel() {
		String code = "class Test { void method() { int x = 1; } }";
		CompilationUnit cu = parse(code);
		Match match = new Match(cu, Map.of(), 0, code.length());
		ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
		
		// Without explicit cancel
		HintContext ctx1 = new HintContext(cu, null, match, rewrite);
		assertFalse(ctx1.isCanceled(), "Should not be canceled by default");
		
		// With explicit cancel
		AtomicBoolean cancel = new AtomicBoolean(true);
		HintContext ctx2 = new HintContext(cu, null, match, rewrite, cancel);
		assertTrue(ctx2.isCanceled(), "Should be canceled when AtomicBoolean is true");
	}
	
	@Test
	public void testHintContextGetVariables() {
		String code = """
			class Test {
				void method() {
					int x = a + b;
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x + $y", PatternKind.EXPRESSION);
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size());
		Match match = matches.get(0);
		ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
		HintContext ctx = new HintContext(cu, null, match, rewrite);
		
		Map<String, ASTNode> variables = ctx.getVariables();
		
		// Should have $x and $y single placeholders, plus $_ and $this auto-bindings
		assertTrue(variables.containsKey("$x"));
		assertTrue(variables.containsKey("$y"));
		assertTrue(variables.containsKey("$_"));
		assertTrue(variables.containsKey("$this"));
	}
	
	@Test
	public void testHintContextGetVariableNames() {
		String code = """
			class Test {
				void method() {
					int x = a + b;
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x + $y", PatternKind.EXPRESSION);
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size());
		Match match = matches.get(0);
		ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
		HintContext ctx = new HintContext(cu, null, match, rewrite);
		
		Map<String, String> variableNames = ctx.getVariableNames();
		
		assertTrue(variableNames.containsKey("$x"));
		assertTrue(variableNames.containsKey("$y"));
		assertEquals("a", variableNames.get("$x"));
		assertEquals("b", variableNames.get("$y"));
	}
	
	// ========== Helper Methods ==========
	
	private CompilationUnit parse(String code) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(code.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		return (CompilationUnit) parser.createAST(null);
	}
	
	// ========== Test Hint Provider Class ==========
	
	/**
	 * Sample hint provider class for testing annotations.
	 */
	public static class TestHintProvider {
		
		@TriggerPatterns({
			@TriggerPattern(value = "$x + 1", kind = PatternKind.EXPRESSION),
			@TriggerPattern(value = "$x + 1L", kind = PatternKind.EXPRESSION)
		})
		@Hint(displayName = "Multi-pattern hint")
		public static void multiPatternHint(HintContext ctx) {
			// Test method
		}
		
		@TriggerPattern(
			value = "$x.toString()",
			constraints = @ConstraintVariableType(variable = "$x", type = "java.lang.String")
		)
		@Hint(displayName = "Constrained hint")
		public static void constrainedHint(HintContext ctx) {
			// Test method
		}
		
		@TriggerTreeKind(ASTNode.METHOD_DECLARATION)
		@Hint(displayName = "Tree kind hint")
		public static void treeKindHint(HintContext ctx) {
			// Test method
		}
		
		@TriggerPattern(value = "$x + 1", kind = PatternKind.EXPRESSION)
		@Hint(
			displayName = "Enhanced hint",
			id = "test-hint-id",
			category = "test-category",
			suppressWarnings = {"unused", "deprecation"},
			hintKind = HintKind.ACTION,
			minSourceVersion = "11",
			severity = Severity.WARNING
		)
		public static void enhancedHint(HintContext ctx) {
			// Test method
		}
	}
}
