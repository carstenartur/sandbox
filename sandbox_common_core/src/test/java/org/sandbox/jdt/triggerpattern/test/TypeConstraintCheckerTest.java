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

import java.util.Collections;
import java.util.HashMap;
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
import org.sandbox.jdt.triggerpattern.internal.TypeConstraintChecker;

/**
 * Tests for {@link TypeConstraintChecker} and type constraint integration
 * in {@link TriggerPatternEngine}.
 */
public class TypeConstraintCheckerTest {

	private final TriggerPatternEngine engine = new TriggerPatternEngine();

	@Test
	public void testSatisfiesConstraintsWithEmptyConstraints() {
		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", "dummy");

		assertTrue(TypeConstraintChecker.satisfiesConstraints(bindings, Collections.emptyMap()),
				"Empty constraints should always be satisfied");
	}

	@Test
	public void testSatisfiesConstraintsWithNullConstraints() {
		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", "dummy");

		assertTrue(TypeConstraintChecker.satisfiesConstraints(bindings, null),
				"Null constraints should always be satisfied");
	}

	@Test
	public void testSatisfiesConstraintsWithMissingBinding() {
		Map<String, Object> bindings = new HashMap<>();
		// $x is not in bindings
		Map<String, String> constraints = Map.of("$x", "java.lang.String");

		assertFalse(TypeConstraintChecker.satisfiesConstraints(bindings, constraints),
				"Missing binding should fail constraint check");
	}

	@Test
	public void testPatternWithTypeConstraintsMatchesWithoutBindings() {
		// Without binding resolution, type constraints are satisfied (lenient mode)
		String code = """
			class Test {
				void method() {
					int x = a + 1;
				}
			}
			""";

		CompilationUnit cu = parse(code);

		Map<String, String> constraints = Map.of("$x", "java.lang.String");
		Pattern pattern = new Pattern("$x + 1", PatternKind.EXPRESSION,
				null, null, null, null, constraints);

		List<Match> matches = engine.findMatches(cu, pattern);

		// Without binding resolution, the constraint check returns true (lenient)
		// because Expression.resolveTypeBinding() returns null without a project context
		assertEquals(1, matches.size(),
				"Type constraints should pass leniently when bindings are not available");
	}

	@Test
	public void testPatternWithoutTypeConstraintsStillMatches() {
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

		assertEquals(1, matches.size(), "Pattern without constraints should still match");
	}

	@Test
	public void testPatternTypeConstraintsFieldAccessor() {
		// Verify the Pattern class correctly stores and returns type constraints
		Map<String, String> constraints = Map.of("$x", "java.lang.String", "$y", "int");
		Pattern pattern = new Pattern("$x + $y", PatternKind.EXPRESSION,
				null, null, null, null, constraints);

		assertEquals(constraints, pattern.getTypeConstraints(),
				"Type constraints should be stored and retrievable");
	}

	@Test
	public void testPatternEmptyTypeConstraintsByDefault() {
		Pattern pattern = new Pattern("$x + 1", PatternKind.EXPRESSION);

		assertTrue(pattern.getTypeConstraints().isEmpty(),
				"Default pattern should have empty type constraints");
	}

	private CompilationUnit parse(String code) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(code.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		return (CompilationUnit) parser.createAST(null);
	}
}
