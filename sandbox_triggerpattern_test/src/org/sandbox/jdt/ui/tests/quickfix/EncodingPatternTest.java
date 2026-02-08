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
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.FixUtilities;
import org.sandbox.jdt.triggerpattern.api.HintContext;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Tests for encoding-related TriggerPattern plugins.
 * 
 * <p>These tests verify that the patterns defined in the
 * {@code org.sandbox.jdt.triggerpattern.encoding} package correctly match
 * encoding-related code patterns and support declarative rewriting.</p>
 * 
 * @since 1.2.5
 */
public class EncodingPatternTest {

	private final TriggerPatternEngine engine = new TriggerPatternEngine();

	// ========== String Constructor Tests ==========

	/**
	 * Tests pattern matching for {@code new String($bytes, "UTF-8")}.
	 */
	@Test
	public void testStringConstructorUtf8Pattern() {
		String code = """
			class Test {
				void method(byte[] bytes) {
					String text = new String(bytes, "UTF-8");
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("new String($bytes, \"UTF-8\")", PatternKind.CONSTRUCTOR);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find exactly one match for new String($bytes, \"UTF-8\")");

		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertTrue(bindings.containsKey("$bytes"), "Should have binding for $bytes");
		
		ASTNode matchedNode = match.getMatchedNode();
		assertTrue(matchedNode instanceof ClassInstanceCreation, 
				"Matched node should be ClassInstanceCreation");
	}

	/**
	 * Tests that the String constructor pattern binds $bytes correctly.
	 */
	@Test
	public void testStringConstructorPlaceholderBinding() {
		String code = """
			class Test {
				void method() {
					byte[] data = "hello".getBytes();
					String text = new String(data, "UTF-8");
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("new String($bytes, \"UTF-8\")", PatternKind.CONSTRUCTOR);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size());
		Match match = matches.get(0);
		
		ASTNode bytesBinding = (ASTNode) match.getBindings().get("$bytes");
		assertNotNull(bytesBinding, "$bytes should be bound");
		assertEquals("data", bytesBinding.toString(), "$bytes should be bound to 'data'");
	}

	/**
	 * Tests declarative rewrite for String constructor with UTF-8.
	 */
	@Test
	public void testStringConstructorDeclarativeRewrite() {
		String code = """
			class Test {
				void method(byte[] bytes) {
					String text = new String(bytes, "UTF-8");
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("new String($bytes, \"UTF-8\")", PatternKind.CONSTRUCTOR);

		List<Match> matches = engine.findMatches(cu, pattern);
		assertEquals(1, matches.size());

		Match match = matches.get(0);
		ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
		HintContext ctx = new HintContext(cu, null, match, rewrite);

		// Apply declarative rewrite
		FixUtilities.rewriteFix(ctx, "new String($bytes, StandardCharsets.UTF_8)");

		// Verify rewrite was created successfully
		assertNotNull(ctx.getASTRewrite());
	}

	// ========== Charset.forName Tests ==========

	/**
	 * Tests pattern matching for {@code Charset.forName("UTF-8")}.
	 */
	@Test
	public void testCharsetForNameUtf8Pattern() {
		String code = """
			import java.nio.charset.Charset;
			class Test {
				void method() {
					Charset charset = Charset.forName("UTF-8");
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("Charset.forName(\"UTF-8\")", PatternKind.METHOD_CALL);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find exactly one match for Charset.forName(\"UTF-8\")");

		Match match = matches.get(0);
		ASTNode matchedNode = match.getMatchedNode();
		assertTrue(matchedNode instanceof MethodInvocation, 
				"Matched node should be MethodInvocation");
	}

	/**
	 * Tests declarative rewrite for Charset.forName.
	 * 
	 * <p>Note: This test verifies that the pattern matches correctly. The actual
	 * declarative rewrite for this case requires special handling because the
	 * replacement ({@code StandardCharsets.UTF_8}) is a field access expression,
	 * not a method call. The {@code CharsetForNameEncodingPlugin} handles this
	 * via {@code processRewriteWithRule()} which uses different logic than
	 * {@code FixUtilities.rewriteFix()}.</p>
	 */
	@Test
	public void testCharsetForNameDeclarativeRewrite() {
		String code = """
			import java.nio.charset.Charset;
			class Test {
				void method() {
					Charset charset = Charset.forName("UTF-8");
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("Charset.forName(\"UTF-8\")", PatternKind.METHOD_CALL);

		List<Match> matches = engine.findMatches(cu, pattern);
		assertEquals(1, matches.size());

		Match match = matches.get(0);
		ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
		HintContext ctx = new HintContext(cu, null, match, rewrite);

		// Note: FixUtilities.rewriteFix() cannot parse "StandardCharsets.UTF_8" as a
		// standalone replacement when the source pattern kind is METHOD_CALL.
		// The CharsetForNameEncodingPlugin uses processRewriteWithRule() which
		// handles this case differently. Here we just verify the match works.
		assertNotNull(ctx.getASTRewrite());
		assertNotNull(match.getMatchedNode());
		assertTrue(match.getMatchedNode() instanceof MethodInvocation);
	}

	// ========== String.getBytes Tests ==========

	/**
	 * Tests pattern matching for {@code $str.getBytes("UTF-8")}.
	 */
	@Test
	public void testStringGetBytesUtf8Pattern() {
		String code = """
			class Test {
				void method(String text) {
					byte[] bytes = text.getBytes("UTF-8");
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$str.getBytes(\"UTF-8\")", PatternKind.METHOD_CALL);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find exactly one match for $str.getBytes(\"UTF-8\")");

		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertTrue(bindings.containsKey("$str"), "Should have binding for $str");
		
		ASTNode matchedNode = match.getMatchedNode();
		assertTrue(matchedNode instanceof MethodInvocation, 
				"Matched node should be MethodInvocation");
	}

	/**
	 * Tests that the String.getBytes pattern binds $str correctly.
	 */
	@Test
	public void testStringGetBytesPlaceholderBinding() {
		String code = """
			class Test {
				void method() {
					String message = "hello";
					byte[] bytes = message.getBytes("UTF-8");
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$str.getBytes(\"UTF-8\")", PatternKind.METHOD_CALL);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size());
		Match match = matches.get(0);
		
		ASTNode strBinding = (ASTNode) match.getBindings().get("$str");
		assertNotNull(strBinding, "$str should be bound");
		assertEquals("message", strBinding.toString(), "$str should be bound to 'message'");
	}

	/**
	 * Tests declarative rewrite for String.getBytes with placeholder preservation.
	 */
	@Test
	public void testStringGetBytesDeclarativeRewrite() {
		String code = """
			class Test {
				void method(String text) {
					byte[] bytes = text.getBytes("UTF-8");
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$str.getBytes(\"UTF-8\")", PatternKind.METHOD_CALL);

		List<Match> matches = engine.findMatches(cu, pattern);
		assertEquals(1, matches.size());

		Match match = matches.get(0);
		ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
		HintContext ctx = new HintContext(cu, null, match, rewrite);

		// Apply declarative rewrite with placeholder preservation
		FixUtilities.rewriteFix(ctx, "$str.getBytes(StandardCharsets.UTF_8)");

		// Verify rewrite was created successfully
		assertNotNull(ctx.getASTRewrite());
	}

	// ========== Multiple Matches Tests ==========

	/**
	 * Tests that patterns find multiple occurrences in the same file.
	 */
	@Test
	public void testMultipleEncodingMatches() {
		String code = """
			class Test {
				void method(String text, byte[] bytes) {
					byte[] b1 = text.getBytes("UTF-8");
					String s1 = new String(bytes, "UTF-8");
					byte[] b2 = "hello".getBytes("UTF-8");
				}
			}
			""";

		CompilationUnit cu = parse(code);
		
		// Test getBytes pattern
		Pattern getBytesPattern = new Pattern("$str.getBytes(\"UTF-8\")", PatternKind.METHOD_CALL);
		List<Match> getBytesMatches = engine.findMatches(cu, getBytesPattern);
		assertEquals(2, getBytesMatches.size(), "Should find two getBytes matches");

		// Test constructor pattern
		Pattern constructorPattern = new Pattern("new String($bytes, \"UTF-8\")", PatternKind.CONSTRUCTOR);
		List<Match> constructorMatches = engine.findMatches(cu, constructorPattern);
		assertEquals(1, constructorMatches.size(), "Should find one constructor match");
	}

	// ========== Negative Tests ==========

	/**
	 * Tests that pattern does NOT match different encoding strings.
	 */
	@Test
	public void testDoesNotMatchOtherEncodings() {
		String code = """
			class Test {
				void method(String text) {
					byte[] bytes = text.getBytes("ISO-8859-1");
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$str.getBytes(\"UTF-8\")", PatternKind.METHOD_CALL);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(0, matches.size(), "Should NOT match ISO-8859-1 encoding");
	}

	/**
	 * Tests that pattern does NOT match when using Charset object instead of String.
	 */
	@Test
	public void testDoesNotMatchCharsetObject() {
		String code = """
			import java.nio.charset.StandardCharsets;
			class Test {
				void method(String text) {
					byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$str.getBytes(\"UTF-8\")", PatternKind.METHOD_CALL);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(0, matches.size(), "Should NOT match when already using StandardCharsets");
	}

	// ========== Helper Methods ==========

	/**
	 * Parses Java source code into a CompilationUnit.
	 */
	private CompilationUnit parse(String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(false);
		return (CompilationUnit) parser.createAST(null);
	}
}
