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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Tests for the double-checked locking TriggerPattern hint.
 * 
 * <p>These tests verify that the expression-level pattern {@code $field == null}
 * used in {@code DoubleCheckLockingHintProvider} correctly matches null-check
 * expressions in Java code. The full double-checked locking structural validation
 * is performed in the hint provider method itself via AST tree walking.</p>
 * 
 * @see org.sandbox.jdt.triggerpattern.concurrency.DoubleCheckLockingHintProvider
 */
public class DoubleCheckLockingTest {

	private final TriggerPatternEngine engine = new TriggerPatternEngine();

	private static final String NULL_CHECK_PATTERN = "$field == null"; //$NON-NLS-1$

	/**
	 * Tests that the null-check expression pattern matches in double-checked locking code.
	 * The classic pattern contains two {@code instance == null} checks.
	 */
	@Test
	public void testClassicDoubleCheckedLocking() {
		String code = """
			class Singleton {
				private static Singleton instance;
				public static Singleton getInstance() {
					if (instance == null) {
						synchronized (Singleton.class) {
							if (instance == null) {
								instance = new Singleton();
							}
						}
					}
					return instance;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern(NULL_CHECK_PATTERN, PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		// Two null checks: outer if and inner if
		assertEquals(2, matches.size(), "Should find two null-check expressions in double-checked locking");

		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertTrue(bindings.containsKey("$field"), "Should have binding for $field");
	}

	/**
	 * Tests that the pattern matches null checks when using {@code this} as the lock.
	 */
	@Test
	public void testDoubleCheckWithThisLock() {
		String code = """
			class LazyInit {
				private Object resource;
				public Object getResource() {
					if (resource == null) {
						synchronized (this) {
							if (resource == null) {
								resource = new Object();
							}
						}
					}
					return resource;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern(NULL_CHECK_PATTERN, PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(2, matches.size(), "Should find two null-check expressions with 'this' lock");
	}

	/**
	 * Tests that simple synchronization (without double check) has only one null check.
	 */
	@Test
	public void testSimpleSynchronizationSingleNullCheck() {
		String code = """
			class SafeInit {
				private Object resource;
				public Object getResource() {
					synchronized (this) {
						if (resource == null) {
							resource = new Object();
						}
					}
					return resource;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern(NULL_CHECK_PATTERN, PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find only one null-check in simple synchronization");
	}

	/**
	 * Tests that an if without synchronized block inside has only one null check.
	 */
	@Test
	public void testSimpleNullCheckSingleMatch() {
		String code = """
			class SimpleCheck {
				private Object resource;
				public Object getResource() {
					if (resource == null) {
						resource = new Object();
					}
					return resource;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern(NULL_CHECK_PATTERN, PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find one null-check in simple if");
	}

	/**
	 * Tests that different variables produce separate bindings for each null check.
	 */
	@Test
	public void testDifferentVariablesProduceSeparateBindings() {
		String code = """
			class DifferentVars {
				private Object resource;
				private Object other;
				public Object getResource() {
					if (resource == null) {
						synchronized (this) {
							if (other == null) {
								other = new Object();
							}
						}
					}
					return resource;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern(NULL_CHECK_PATTERN, PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(2, matches.size(), "Should find two null-checks with different variables");
	}

	/**
	 * Tests that the pattern matches with a dedicated lock object.
	 */
	@Test
	public void testDoubleCheckWithLockObject() {
		String code = """
			class CachedValue {
				private final Object lock = new Object();
				private String cache;
				public String getCache() {
					if (cache == null) {
						synchronized (lock) {
							if (cache == null) {
								cache = computeValue();
							}
						}
					}
					return cache;
				}
				private String computeValue() { return "value"; }
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern(NULL_CHECK_PATTERN, PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(2, matches.size(), "Should find two null-check expressions with dedicated lock object");
	}

	/**
	 * Tests that code without any null checks produces no matches.
	 */
	@Test
	public void testNoNullChecksNoMatches() {
		String code = """
			class NoNullChecks {
				private int value;
				public int getValue() {
					if (value > 0) {
						return value;
					}
					return 0;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern(NULL_CHECK_PATTERN, PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(0, matches.size(), "Should find no null-check expressions");
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
