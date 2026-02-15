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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.GuardContext;
import org.sandbox.jdt.triggerpattern.api.GuardExpression;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.GuardExpressionParser;

/**
 * Tests for Phase 4: Conditional Multi-Rewrite (TransformationRule and RewriteAlternative).
 */
public class TransformationRuleTest {
	
	private final GuardExpressionParser guardParser = new GuardExpressionParser();
	
	@BeforeAll
	static void registerGuardFunctions() {
		Map<String, GuardFunction> guards = new HashMap<>();
		guards.put("sourceVersionGE", (ctx, args) -> { //$NON-NLS-1$
			if (args.length < 1) return false;
			double required = Double.parseDouble(args[0].toString());
			String sv = ctx.getSourceVersion();
			double source = (sv != null && !sv.isEmpty()) ? Double.parseDouble(sv) : 0;
			return source >= required;
		});
		guards.put("sourceVersionLE", (ctx, args) -> { //$NON-NLS-1$
			if (args.length < 1) return false;
			double required = Double.parseDouble(args[0].toString());
			String sv = ctx.getSourceVersion();
			double source = (sv != null && !sv.isEmpty()) ? Double.parseDouble(sv) : 0;
			return source <= required;
		});
		GuardFunctionResolverHolder.setResolver(guards::get);
	}
	
	@Test
	public void testSimpleRuleWithOneAlternative() {
		Pattern sourcePattern = new Pattern("$x + 1", PatternKind.EXPRESSION);
		RewriteAlternative alt = RewriteAlternative.otherwise("++$x");
		
		TransformationRule rule = new TransformationRule(
				"Simplify increment", sourcePattern, null, List.of(alt));
		
		assertEquals("Simplify increment", rule.getDescription());
		assertEquals(sourcePattern, rule.sourcePattern());
		assertNull(rule.sourceGuard());
		assertEquals(1, rule.alternatives().size());
		assertFalse(rule.isHintOnly());
	}
	
	@Test
	public void testHintOnlyRule() {
		Pattern sourcePattern = new Pattern("Thread.sleep($t)", PatternKind.METHOD_CALL);
		
		TransformationRule rule = new TransformationRule(
				"Avoid Thread.sleep", sourcePattern, null, List.of());
		
		assertTrue(rule.isHintOnly(), "Rule with no alternatives should be hint-only");
	}
	
	@Test
	public void testTwoAlternativesFirstMatches() {
		GuardExpression guard1 = guardParser.parse("sourceVersionGE(11)");
		GuardExpression guard2 = guardParser.parse("sourceVersionGE(7)");
		
		Pattern sourcePattern = new Pattern("new String($bytes, \"UTF-8\")", PatternKind.CONSTRUCTOR);
		RewriteAlternative alt1 = new RewriteAlternative(
				"new String($bytes, StandardCharsets.UTF_8)", guard1);
		RewriteAlternative alt2 = new RewriteAlternative(
				"new String($bytes, Charset.forName(\"UTF-8\"))", guard2);
		
		TransformationRule rule = new TransformationRule(null, sourcePattern, null, List.of(alt1, alt2));
		
		// Java 17: first alternative should match
		GuardContext ctx = createContextWithVersion("17"); //$NON-NLS-1$
		RewriteAlternative matched = rule.findMatchingAlternative(ctx);
		
		assertNotNull(matched);
		assertEquals("new String($bytes, StandardCharsets.UTF_8)", matched.replacementPattern());
	}
	
	@Test
	public void testTwoAlternativesSecondMatches() {
		GuardExpression guard1 = guardParser.parse("sourceVersionGE(11)");
		
		Pattern sourcePattern = new Pattern("new String($bytes, \"UTF-8\")", PatternKind.CONSTRUCTOR);
		RewriteAlternative alt1 = new RewriteAlternative(
				"new String($bytes, StandardCharsets.UTF_8)", guard1);
		RewriteAlternative alt2 = RewriteAlternative.otherwise(
				"new String($bytes, Charset.forName(\"UTF-8\"))");
		
		TransformationRule rule = new TransformationRule(null, sourcePattern, null, List.of(alt1, alt2));
		
		// Java 8: first doesn't match, otherwise catches
		GuardContext ctx = createContextWithVersion("1.8"); //$NON-NLS-1$
		RewriteAlternative matched = rule.findMatchingAlternative(ctx);
		
		assertNotNull(matched);
		assertTrue(matched.isOtherwise());
		assertEquals("new String($bytes, Charset.forName(\"UTF-8\"))", matched.replacementPattern());
	}
	
	@Test
	public void testNoAlternativeMatches() {
		GuardExpression guard1 = guardParser.parse("sourceVersionGE(21)");
		GuardExpression guard2 = guardParser.parse("sourceVersionGE(17)");
		
		Pattern sourcePattern = new Pattern("$x", PatternKind.EXPRESSION);
		RewriteAlternative alt1 = new RewriteAlternative("alt1", guard1);
		RewriteAlternative alt2 = new RewriteAlternative("alt2", guard2);
		
		TransformationRule rule = new TransformationRule(null, sourcePattern, null, List.of(alt1, alt2));
		
		// Java 11: neither guard matches
		GuardContext ctx = createContextWithVersion("11"); //$NON-NLS-1$
		RewriteAlternative matched = rule.findMatchingAlternative(ctx);
		
		assertNull(matched, "No alternative should match for Java 11");
	}
	
	@Test
	public void testOtherwiseAlternative() {
		RewriteAlternative alt = RewriteAlternative.otherwise("fallback");
		
		assertTrue(alt.isOtherwise());
		assertNull(alt.condition());
		assertEquals("fallback", alt.replacementPattern());
	}
	
	@Test
	public void testRuleWithSourceGuard() {
		GuardExpression sourceGuard = guardParser.parse("sourceVersionGE(11)");
		Pattern sourcePattern = new Pattern("$x", PatternKind.EXPRESSION);
		
		TransformationRule rule = new TransformationRule(
				null, sourcePattern, sourceGuard, List.of());
		
		assertNotNull(rule.sourceGuard());
	}
	
	// --- Helper methods ---
	
	private GuardContext createContextWithVersion(String version) {
		ASTNode dummyNode = createDummyNode();
		Match match = new Match(dummyNode, new HashMap<>(), 0, 0);
		Map<String, String> options = new HashMap<>();
		options.put(JavaCore.COMPILER_SOURCE, version);
		return GuardContext.fromMatch(match, null, options);
	}
	
	private ASTNode createDummyNode() {
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource("class Dummy { }".toCharArray()); //$NON-NLS-1$
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		astParser.setCompilerOptions(JavaCore.getOptions());
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		return cu.types().isEmpty() ? cu : (ASTNode) cu.types().get(0);
	}
}
