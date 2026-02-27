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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.SuppressWarningsChecker;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Tests for {@link SuppressWarningsChecker}.
 */
public class SuppressWarningsCheckerTest {

	private final TriggerPatternEngine engine = new TriggerPatternEngine();

	@Test
	public void testNotSuppressedWithoutAnnotation() {
		String code = """
			class Test {
				void method() {
					int x = a + 1;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x + 1", PatternKind.EXPRESSION);
		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size());
		assertFalse(SuppressWarningsChecker.isSuppressed(
				matches.get(0).getMatchedNode(), Set.of("my-hint")),
				"Should not be suppressed without @SuppressWarnings");
	}

	@Test
	public void testSuppressedBySingleMemberAnnotation() {
		String code = """
			class Test {
				@SuppressWarnings("my-hint")
				void method() {
					int x = a + 1;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x + 1", PatternKind.EXPRESSION);
		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size());
		assertTrue(SuppressWarningsChecker.isSuppressed(
				matches.get(0).getMatchedNode(), Set.of("my-hint")),
				"Should be suppressed by @SuppressWarnings(\"my-hint\")");
	}

	@Test
	public void testSuppressedByArrayAnnotation() {
		String code = """
			class Test {
				@SuppressWarnings({"unused", "my-hint"})
				void method() {
					int x = a + 1;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x + 1", PatternKind.EXPRESSION);
		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size());
		assertTrue(SuppressWarningsChecker.isSuppressed(
				matches.get(0).getMatchedNode(), Set.of("my-hint")),
				"Should be suppressed by array @SuppressWarnings");
	}

	@Test
	public void testNotSuppressedByDifferentKey() {
		String code = """
			class Test {
				@SuppressWarnings("unused")
				void method() {
					int x = a + 1;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x + 1", PatternKind.EXPRESSION);
		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size());
		assertFalse(SuppressWarningsChecker.isSuppressed(
				matches.get(0).getMatchedNode(), Set.of("my-hint")),
				"Should not be suppressed by different key");
	}

	@Test
	public void testSuppressedByEnclosingClass() {
		String code = """
			@SuppressWarnings("my-hint")
			class Test {
				void method() {
					int x = a + 1;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x + 1", PatternKind.EXPRESSION);
		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size());
		assertTrue(SuppressWarningsChecker.isSuppressed(
				matches.get(0).getMatchedNode(), Set.of("my-hint")),
				"Should be suppressed by enclosing class @SuppressWarnings");
	}

	@Test
	public void testNullNodeNotSuppressed() {
		assertFalse(SuppressWarningsChecker.isSuppressed(null, Set.of("my-hint")),
				"Null node should not be suppressed");
	}

	@Test
	public void testEmptyKeysNotSuppressed() {
		String code = """
			class Test {
				@SuppressWarnings("my-hint")
				void method() {
					int x = a + 1;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x + 1", PatternKind.EXPRESSION);
		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size());
		assertFalse(SuppressWarningsChecker.isSuppressed(
				matches.get(0).getMatchedNode(), Set.of()),
				"Empty keys should not suppress");
	}

	@Test
	public void testSuppressedByMultipleKeys() {
		String code = """
			class Test {
				@SuppressWarnings("my-hint")
				void method() {
					int x = a + 1;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x + 1", PatternKind.EXPRESSION);
		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size());
		assertTrue(SuppressWarningsChecker.isSuppressed(
				matches.get(0).getMatchedNode(), Set.of("other-hint", "my-hint")),
				"Should be suppressed when any key matches");
	}

	private CompilationUnit parse(String code) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(code.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		return (CompilationUnit) parser.createAST(null);
	}
}
