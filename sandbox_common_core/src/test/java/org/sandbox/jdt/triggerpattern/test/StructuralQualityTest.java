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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.GuardContext;
import org.sandbox.jdt.triggerpattern.api.GuardExpression;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Match.Binding;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.Severity;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.GuardExpressionParser;

/**
 * Structural quality tests for the sandbox_common_core refactoring:
 * HintFile severity type-safety, Match.Binding sealed interface,
 * TransformationRule.findMatchingAlternative Optional return, and
 * GuardExpressionParser thread-safety.
 */
public class StructuralQualityTest {

	@BeforeAll
	static void registerGuardFunctions() {
		Map<String, GuardFunction> guards = new HashMap<>();
		guards.put("sourceVersionGE", (ctx, args) -> {
			if (args.length < 1) return false;
			double required = Double.parseDouble(args[0].toString());
			String sv = ctx.getSourceVersion();
			double source = (sv != null && !sv.isEmpty()) ? Double.parseDouble(sv) : 0;
			return source >= required;
		});
		GuardFunctionResolverHolder.setResolver(guards::get);
	}

	// --- Shared helpers ---

	private static ASTNode createDummyNode() {
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource("class Dummy { }".toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		astParser.setCompilerOptions(JavaCore.getOptions());
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		return cu.types().isEmpty() ? cu : (ASTNode) cu.types().get(0);
	}

	private static ASTNode createSimpleNameNode() {
		AST ast = AST.newAST(AST.getJLSLatest());
		return ast.newSimpleName("testVar");
	}

	private static GuardContext createContextWithVersion(String version) {
		ASTNode dummyNode = createDummyNode();
		Match match = new Match(dummyNode, new HashMap<>(), 0, 0);
		Map<String, String> options = new HashMap<>();
		options.put(JavaCore.COMPILER_SOURCE, version);
		return GuardContext.fromMatch(match, null, options);
	}

	// =========================================================================
	// 1. HintFile Severity type-safety
	// =========================================================================

	@Nested
	@DisplayName("HintFile Severity type-safety")
	class HintFileSeverityTests {

		@Test
		@DisplayName("setSeverity(\"warning\") sets Severity.WARNING")
		void testSetSeverityWarningLowercase() {
			HintFile hintFile = new HintFile();
			hintFile.setSeverity("warning");
			assertEquals(Severity.WARNING, hintFile.getSeverity());
		}

		@Test
		@DisplayName("setSeverity(\"ERROR\") (uppercase) sets Severity.ERROR")
		void testSetSeverityErrorUppercase() {
			HintFile hintFile = new HintFile();
			hintFile.setSeverity("ERROR");
			assertEquals(Severity.ERROR, hintFile.getSeverity());
		}

		@Test
		@DisplayName("setSeverity(\"hint\") sets Severity.HINT")
		void testSetSeverityHintLowercase() {
			HintFile hintFile = new HintFile();
			hintFile.setSeverity("hint");
			assertEquals(Severity.HINT, hintFile.getSeverity());
		}

		@Test
		@DisplayName("setSeverity(null) falls back to Severity.INFO")
		void testSetSeverityNullFallback() {
			HintFile hintFile = new HintFile();
			hintFile.setSeverity(Severity.ERROR);
			hintFile.setSeverity((String) null);
			assertEquals(Severity.INFO, hintFile.getSeverity());
		}

		@Test
		@DisplayName("setSeverity(\"invalid_value\") falls back to Severity.INFO")
		void testSetSeverityInvalidFallback() {
			HintFile hintFile = new HintFile();
			hintFile.setSeverity(Severity.ERROR);
			hintFile.setSeverity("invalid_value");
			assertEquals(Severity.INFO, hintFile.getSeverity());
		}

		@Test
		@DisplayName("setSeverity(Severity.ERROR) works directly")
		void testSetSeverityEnumDirectly() {
			HintFile hintFile = new HintFile();
			hintFile.setSeverity(Severity.ERROR);
			assertEquals(Severity.ERROR, hintFile.getSeverity());
		}

		@Test
		@DisplayName("getSeverityAsString() returns lowercase")
		void testGetSeverityAsStringReturnsLowercase() {
			HintFile hintFile = new HintFile();

			hintFile.setSeverity(Severity.ERROR);
			assertEquals("error", hintFile.getSeverityAsString());

			hintFile.setSeverity(Severity.WARNING);
			assertEquals("warning", hintFile.getSeverityAsString());

			hintFile.setSeverity(Severity.INFO);
			assertEquals("info", hintFile.getSeverityAsString());

			hintFile.setSeverity(Severity.HINT);
			assertEquals("hint", hintFile.getSeverityAsString());
		}
	}

	// =========================================================================
	// 2. Match.Binding sealed interface
	// =========================================================================

	@Nested
	@DisplayName("Match.Binding sealed interface")
	class MatchBindingTests {

		@Test
		@DisplayName("getTypedBindings() with a single binding returns Binding.SingleNode")
		void testTypedBindingsSingleNode() {
			ASTNode singleNode = createSimpleNameNode();
			Map<String, Object> bindings = new HashMap<>();
			bindings.put("$x", singleNode);

			ASTNode matchedNode = createDummyNode();
			Match match = new Match(matchedNode, bindings, 0, 10);

			Map<String, Binding> typed = match.getTypedBindings();
			assertEquals(1, typed.size());
			assertTrue(typed.containsKey("$x"));
			assertInstanceOf(Binding.SingleNode.class, typed.get("$x"));

			Binding.SingleNode singleBinding = (Binding.SingleNode) typed.get("$x");
			assertEquals(singleNode, singleBinding.node());
		}

		@Test
		@DisplayName("getTypedBindings() with a list binding returns Binding.NodeList")
		void testTypedBindingsNodeList() {
			ASTNode node1 = createSimpleNameNode();
			ASTNode node2 = createSimpleNameNode();
			List<ASTNode> nodeList = List.of(node1, node2);

			Map<String, Object> bindings = new HashMap<>();
			bindings.put("$args$", nodeList);

			ASTNode matchedNode = createDummyNode();
			Match match = new Match(matchedNode, bindings, 0, 10);

			Map<String, Binding> typed = match.getTypedBindings();
			assertEquals(1, typed.size());
			assertTrue(typed.containsKey("$args$"));
			assertInstanceOf(Binding.NodeList.class, typed.get("$args$"));

			Binding.NodeList listBinding = (Binding.NodeList) typed.get("$args$");
			assertEquals(2, listBinding.nodes().size());
		}

		@Test
		@DisplayName("hasBinding() returns true for existing binding")
		void testHasBindingTrue() {
			ASTNode singleNode = createSimpleNameNode();
			Map<String, Object> bindings = new HashMap<>();
			bindings.put("$x", singleNode);

			ASTNode matchedNode = createDummyNode();
			Match match = new Match(matchedNode, bindings, 0, 10);

			assertTrue(match.hasBinding("$x"));
		}

		@Test
		@DisplayName("hasBinding() returns false for missing binding")
		void testHasBindingFalse() {
			ASTNode matchedNode = createDummyNode();
			Match match = new Match(matchedNode, new HashMap<>(), 0, 10);

			assertFalse(match.hasBinding("$nonexistent"));
		}
	}

	// =========================================================================
	// 3. TransformationRule.findMatchingAlternative returns Optional
	// =========================================================================

	@Nested
	@DisplayName("TransformationRule.findMatchingAlternative returns Optional")
	class FindMatchingAlternativeTests {

		@Test
		@DisplayName("returns Optional.empty() when no alternatives match")
		void testNoAlternativesMatch() {
			GuardExpressionParser guardParser = new GuardExpressionParser();
			GuardExpression guard = guardParser.parse("sourceVersionGE(21)");

			Pattern sourcePattern = new Pattern("$x", PatternKind.EXPRESSION);
			RewriteAlternative alt = new RewriteAlternative("replacement", guard);

			TransformationRule rule = new TransformationRule(
					null, sourcePattern, null, List.of(alt));

			GuardContext ctx = createContextWithVersion("11");
			Optional<RewriteAlternative> matched = rule.findMatchingAlternative(ctx);

			assertTrue(matched.isEmpty(), "Should return empty when no alternatives match");
		}

		@Test
		@DisplayName("returns present Optional for \"otherwise\" alternative")
		void testOtherwiseAlternativeMatches() {
			Pattern sourcePattern = new Pattern("$x", PatternKind.EXPRESSION);
			RewriteAlternative alt = RewriteAlternative.otherwise("fallback($x)");

			TransformationRule rule = new TransformationRule(
					null, sourcePattern, null, List.of(alt));

			GuardContext ctx = createContextWithVersion("11");
			Optional<RewriteAlternative> matched = rule.findMatchingAlternative(ctx);

			assertTrue(matched.isPresent(), "Should return present for otherwise alternative");
			assertTrue(matched.get().isOtherwise());
			assertEquals("fallback($x)", matched.get().replacementPattern());
		}
	}

	// =========================================================================
	// 4. GuardExpressionParser thread-safety
	// =========================================================================

	@Nested
	@DisplayName("GuardExpressionParser thread-safety")
	class GuardExpressionParserThreadSafetyTests {

		@Test
		@DisplayName("concurrent parsing produces correct results without errors")
		void testConcurrentParsing() throws InterruptedException {
			int threadCount = 8;
			CountDownLatch startLatch = new CountDownLatch(1);
			CountDownLatch doneLatch = new CountDownLatch(threadCount);
			ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
			ConcurrentLinkedQueue<GuardExpression> results = new ConcurrentLinkedQueue<>();

			String[] expressions = {
					"sourceVersionGE(11)",
					"sourceVersionGE(17) && sourceVersionGE(11)",
					"!sourceVersionGE(21)",
					"sourceVersionGE(8) || sourceVersionGE(17)",
					"(sourceVersionGE(11) && sourceVersionGE(17)) || sourceVersionGE(21)",
					"$x instanceof String",
					"sourceVersionGE(11) && $y instanceof Integer",
					"!(sourceVersionGE(8) || sourceVersionGE(17))"
			};

			ExecutorService executor = Executors.newFixedThreadPool(threadCount);
			try {
				for (int i = 0; i < threadCount; i++) {
					final String expr = expressions[i % expressions.length];
					executor.submit(() -> {
						try {
							startLatch.await();
							GuardExpressionParser parser = new GuardExpressionParser();
							GuardExpression result = parser.parse(expr);
							results.add(result);
						} catch (Throwable t) {
							errors.add(t);
						} finally {
							doneLatch.countDown();
						}
					});
				}

				startLatch.countDown();
				assertTrue(doneLatch.await(10, TimeUnit.SECONDS),
						"All threads should complete within timeout");
				assertTrue(errors.isEmpty(),
						"No errors should occur during concurrent parsing: " + errors);
				assertEquals(threadCount, results.size(),
						"Each thread should produce a result");

				for (GuardExpression expr : results) {
					assertNotNull(expr, "Each parsed expression should be non-null");
				}
			} finally {
				executor.shutdownNow();
			}
		}
	}
}
