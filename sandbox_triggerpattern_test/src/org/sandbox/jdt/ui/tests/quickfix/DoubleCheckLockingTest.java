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
 * <p>These tests verify that the pattern defined in
 * {@code DoubleCheckLockingHintProvider} correctly matches the
 * double-checked locking idiom in Java code.</p>
 * 
 * @see org.sandbox.jdt.triggerpattern.concurrency.DoubleCheckLockingHintProvider
 */
public class DoubleCheckLockingTest {

	private final TriggerPatternEngine engine = new TriggerPatternEngine();

	private static final String DOUBLE_CHECK_PATTERN = "if ($field == null) { synchronized ($lock) { if ($field == null) { $stmt; } } }"; //$NON-NLS-1$

	/**
	 * Tests that the classic double-checked locking pattern is detected.
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
		Pattern pattern = new Pattern(DOUBLE_CHECK_PATTERN, PatternKind.STATEMENT);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find exactly one double-checked locking match");

		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertTrue(bindings.containsKey("$field"), "Should have binding for $field");
		assertTrue(bindings.containsKey("$lock"), "Should have binding for $lock");
		assertTrue(bindings.containsKey("$stmt"), "Should have binding for $stmt");
	}

	/**
	 * Tests that the pattern matches when using {@code this} as the lock.
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
		Pattern pattern = new Pattern(DOUBLE_CHECK_PATTERN, PatternKind.STATEMENT);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find double-checked locking with 'this' lock");
	}

	/**
	 * Tests that simple synchronization (without double check) is NOT matched.
	 */
	@Test
	public void testSimpleSynchronizationNotMatched() {
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
		Pattern pattern = new Pattern(DOUBLE_CHECK_PATTERN, PatternKind.STATEMENT);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(0, matches.size(), "Should not match simple synchronization without outer if");
	}

	/**
	 * Tests that an if without synchronized block inside is NOT matched.
	 */
	@Test
	public void testSimpleNullCheckNotMatched() {
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
		Pattern pattern = new Pattern(DOUBLE_CHECK_PATTERN, PatternKind.STATEMENT);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(0, matches.size(), "Should not match simple null check without synchronized");
	}

	/**
	 * Tests that different variables in outer and inner if are NOT matched.
	 */
	@Test
	public void testDifferentVariablesNotMatched() {
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
		Pattern pattern = new Pattern(DOUBLE_CHECK_PATTERN, PatternKind.STATEMENT);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(0, matches.size(), "Should not match when outer and inner if check different variables");
	}

	/**
	 * Tests that the pattern matches with a dedicated lock object.
	 */
	@Test
	public void testDoubleCheckWithLockObject() {
		String code = """
			class CachedValue {
				private final Object lock = new Object();
				private volatile String cache;
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
		Pattern pattern = new Pattern(DOUBLE_CHECK_PATTERN, PatternKind.STATEMENT);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find double-checked locking with dedicated lock object");

		Match match = matches.get(0);
		assertTrue(match.getBindings().containsKey("$lock"), "Should have binding for $lock");
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
