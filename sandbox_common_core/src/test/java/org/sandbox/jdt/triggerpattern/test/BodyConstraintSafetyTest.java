/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

class BodyConstraintSafetyTest {

	private final TriggerPatternEngine engine = new TriggerPatternEngine();
	private final Pattern method = Pattern.of("void run()", PatternKind.METHOD_DECLARATION);
	private static final String API = "java.util.Collections.emptyList()";

	@Test
	void unresolvedImportedApiCannotProvePositiveBodyConstraint() {
		CompilationUnit cu = parse("import java.util.Collections; class Example { void run() { Collections.emptyList(); } }", false);
		assertEquals(0, engine.findMatchesWithConstraints(cu, method, API, PatternKind.METHOD_CALL, false).size());
	}

	@Test
	void unresolvedSimilarApiCannotProveNegativeBodyConstraint() {
		CompilationUnit cu = parse("class Example { void run() { Collections.emptyList(); } }", false);
		assertEquals(0, engine.findMatchesWithConstraints(cu, method, API, PatternKind.METHOD_CALL, true).size());
	}

	@Test
	void resolvedPresenceSatisfiesPositiveConstraintAndRejectsNegation() {
		CompilationUnit cu = parse("import java.util.Collections; class Example { void run() { Collections.emptyList(); } }", true);
		assertEquals(1, engine.findMatchesWithConstraints(cu, method, API, PatternKind.METHOD_CALL, false).size());
		assertEquals(0, engine.findMatchesWithConstraints(cu, method, API, PatternKind.METHOD_CALL, true).size());
	}

	@Test
	void structurallyUnrelatedCallProvesAbsenceWithoutRequiringItsBinding() {
		CompilationUnit cu = parse("class Example { void run() { unrelated(); } }", false);
		assertEquals(0, engine.findMatchesWithConstraints(cu, method, API, PatternKind.METHOD_CALL, false).size());
		assertEquals(1, engine.findMatchesWithConstraints(cu, method, API, PatternKind.METHOD_CALL, true).size());
	}

	@Test
	void oneResolvedOccurrenceProvesPresenceDespiteAnotherUnresolvedCandidate() {
		CompilationUnit cu = parse("class Example { void run() { Missing.emptyList(); java.util.Collections.emptyList(); } }", true);
		assertEquals(1, engine.findMatchesWithConstraints(cu, method, API, PatternKind.METHOD_CALL, false).size());
		assertEquals(0, engine.findMatchesWithConstraints(cu, method, API, PatternKind.METHOD_CALL, true).size());
	}

	@Test
	void malformedConstraintCannotAuthorizeEitherDirection() {
		CompilationUnit cu = parse("class Example { void run() {} }", true);
		assertEquals(0, engine.findMatchesWithConstraints(cu, method, "not a call !", PatternKind.METHOD_CALL, false).size());
		assertEquals(0, engine.findMatchesWithConstraints(cu, method, "not a call !", PatternKind.METHOD_CALL, true).size());
	}

	private CompilationUnit parse(String source, boolean bindings) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		if (bindings) {
			parser.setResolveBindings(true);
			parser.setEnvironment(new String[0], new String[0], null, true);
			parser.setUnitName("Example.java");
		}
		return (CompilationUnit) parser.createAST(null);
	}
}
