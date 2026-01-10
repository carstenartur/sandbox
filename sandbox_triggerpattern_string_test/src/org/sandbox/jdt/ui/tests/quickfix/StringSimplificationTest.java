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
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Tests for string simplification TriggerPattern hints.
 * 
 * <p>These tests verify that the patterns defined in 
 * {@code StringSimplificationHintProvider} correctly match and bind
 * placeholder variables in Java code.</p>
 */
public class StringSimplificationTest {

	private final TriggerPatternEngine engine = new TriggerPatternEngine();

	/**
	 * Tests pattern matching for {@code "" + $x}.
	 */
	@Test
	public void testEmptyStringPrefixPattern() {
		String code = """
			class Test {
				void method() {
					String result = "" + value;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("\"\" + $x", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find exactly one match for \"\" + $x");

		Match match = matches.get(0);
		Map<String, ASTNode> bindings = match.getBindings();
		assertEquals(1, bindings.size(), "Should have one binding for $x");
		assertTrue(bindings.containsKey("$x"), "Should have binding for $x");
	}

	/**
	 * Tests pattern matching for {@code $x + ""}.
	 */
	@Test
	public void testEmptyStringSuffixPattern() {
		String code = """
			class Test {
				void method() {
					String result = value + "";
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x + \"\"", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find exactly one match for $x + \"\"");

		Match match = matches.get(0);
		Map<String, ASTNode> bindings = match.getBindings();
		assertEquals(1, bindings.size(), "Should have one binding for $x");
		assertTrue(bindings.containsKey("$x"), "Should have binding for $x");
	}

	/**
	 * Tests that the pattern matches multiple occurrences.
	 */
	@Test
	public void testMultipleMatches() {
		String code = """
			class Test {
				void method() {
					String a = "" + x;
					String b = "" + y;
					String c = "" + z;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("\"\" + $x", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(3, matches.size(), "Should find three matches");
	}

	/**
	 * Tests that the pattern works with different expression types.
	 */
	@Test
	public void testVariousExpressionTypes() {
		String code = """
			class Test {
				void method() {
					String a = "" + variable;
					String b = "" + getValue();
					String c = "" + obj.field;
					String d = "" + (x + y);
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("\"\" + $x", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(4, matches.size(), "Should find four matches with different expression types");
	}

	/**
	 * Tests that pattern does not match when string is not empty.
	 */
	@Test
	public void testNoMatchForNonEmptyString() {
		String code = """
			class Test {
				void method() {
					String result = "prefix" + value;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("\"\" + $x", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(0, matches.size(), "Should not match non-empty string concatenation");
	}

	/**
	 * Tests pattern with method invocation as operand.
	 */
	@Test
	public void testWithMethodInvocation() {
		String code = """
			class Test {
				void method() {
					String result = "" + toString();
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("\"\" + $x", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should match with method invocation");
	}

	/**
	 * Tests both prefix and suffix patterns in the same code.
	 */
	@Test
	public void testBothPrefixAndSuffix() {
		String code = """
			class Test {
				void method() {
					String a = "" + x;
					String b = y + "";
				}
			}
			""";

		CompilationUnit cu = parse(code);

		// Test prefix pattern
		Pattern prefixPattern = new Pattern("\"\" + $x", PatternKind.EXPRESSION);
		List<Match> prefixMatches = engine.findMatches(cu, prefixPattern);
		assertEquals(1, prefixMatches.size(), "Should find one prefix match");

		// Test suffix pattern
		Pattern suffixPattern = new Pattern("$x + \"\"", PatternKind.EXPRESSION);
		List<Match> suffixMatches = engine.findMatches(cu, suffixPattern);
		assertEquals(1, suffixMatches.size(), "Should find one suffix match");
	}

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
