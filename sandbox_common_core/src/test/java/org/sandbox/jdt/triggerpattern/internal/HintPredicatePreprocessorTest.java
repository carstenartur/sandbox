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
package org.sandbox.jdt.triggerpattern.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

class HintPredicatePreprocessorTest {

	@Test
	void expandsNamedPredicateIntoOrdinaryGuardAst() throws Exception {
		String source= """
				<!id: composed>
				<!predicate exactTest($method):
				    isPublic($method) && !isStatic($method) && paramCount($method, 0)>
				foo($method) :: exactTest($method)
				=> bar($method)
				;;
				""";

		HintProgramParser.ParsedProgram program= new HintProgramParser().parse(source);

		assertEquals(1, program.predicates().size());
		assertEquals("exactTest($method)", program.predicates().get(0).signature()); //$NON-NLS-1$
		assertEquals(1, program.hintFile().getRules().size());
		assertFalse(program.expandedSource().contains("<!predicate")); //$NON-NLS-1$
		assertFalse(program.expandedSource().contains("exactTest($method)")); //$NON-NLS-1$
		assertTrue(program.expandedSource().contains("isPublic($method)")); //$NON-NLS-1$
		assertTrue(program.expandedSource().contains("paramCount($method, 0)")); //$NON-NLS-1$
	}

	@Test
	void composesPredicatesAndSubstitutesArgumentsStructurally() throws Exception {
		String source= """
				<!id: nested>
				<!predicate visible($member): isPublic($member) && !isStatic($member)>
				<!predicate exactTest($candidate): visible($candidate) && paramCount($candidate, 0)>
				foo($node) :: exactTest($node)
				=> bar($node)
				;;
				""";

		String expanded= new HintProgramParser().parse(source).expandedSource();

		assertFalse(expanded.contains("visible(")); //$NON-NLS-1$
		assertFalse(expanded.contains("exactTest(")); //$NON-NLS-1$
		assertTrue(expanded.contains("isPublic($node)")); //$NON-NLS-1$
		assertTrue(expanded.contains("isStatic($node)")); //$NON-NLS-1$
	}

	@Test
	void preservesWhitespaceInsideStringLiteralArguments() throws Exception {
		String source= """
				<!id: literal-whitespace>
				<!predicate containsExactText($node): contains($node, "two  spaces")>
				foo($node) :: containsExactText($node)
				=> bar($node)
				;;
				""";

		HintProgramParser.ParsedProgram program= new HintProgramParser().parse(source);

		assertEquals("contains($node, \"two  spaces\")", //$NON-NLS-1$
				program.predicates().get(0).expression());
		assertTrue(program.expandedSource().contains("\"two  spaces\"")); //$NON-NLS-1$
	}

	@Test
	void doesNotRewriteSameNamedCallInSourcePattern() throws Exception {
		String source= """
				<!id: source-call>
				<!predicate selected($node): matchesAny($node)>
				selected($node) :: selected($node)
				=> replacement($node)
				;;
				""";

		String expanded= new HintProgramParser().parse(source).expandedSource();

		assertTrue(expanded.contains("selected($node) :: matchesAny($node)")); //$NON-NLS-1$
	}

	@Test
	void ignoresPredicateTextInsideComments() throws Exception {
		String source= """
				<!id: comments>
				// <!predicate ignored($x): isPublic($x)>
				foo($x) :: matchesAny($x)
				=> bar($x)
				;;
				""";

		HintProgramParser.ParsedProgram program= new HintProgramParser().parse(source);

		assertTrue(program.predicates().isEmpty());
		assertEquals(1, program.hintFile().getRules().size());
	}

	@Test
	void doesNotTreatLongerMetadataNameAsPredicateDeclaration() throws Exception {
		String source= """
				<!id: boundary>
				<!predicateFactory: ignored-for-forward-compatibility>
				foo($x) :: matchesAny($x)
				=> bar($x)
				;;
				""";

		HintProgramParser.ParsedProgram program= new HintProgramParser().parse(source);

		assertTrue(program.predicates().isEmpty());
		assertEquals(1, program.hintFile().getRules().size());
	}

	@Test
	void rejectsDuplicatePredicateNamesWithSourceLine() {
		HintParseException failure= assertThrows(HintParseException.class, () -> new HintProgramParser().parse("""
				<!id: duplicate>
				<!predicate reusable($x): matchesAny($x)>
				<!predicate reusable($x): matchesNone($x)>
				foo($x) :: reusable($x)
				=> bar($x)
				;;
				"""));

		assertEquals(3, failure.getLineNumber());
		assertTrue(failure.getMessage().contains("Duplicate predicate reusable")); //$NON-NLS-1$
	}

	@Test
	void rejectsWrongPredicateArity() {
		HintParseException failure= assertThrows(HintParseException.class, () -> new HintProgramParser().parse("""
				<!id: arity>
				<!predicate pair($left, $right): referencedIn($left, $right)>
				foo($x) :: pair($x)
				=> bar($x)
				;;
				"""));

		assertTrue(failure.getMessage().contains("expects 2 arguments but received 1")); //$NON-NLS-1$
	}

	@Test
	void rejectsDirectAndIndirectPredicateCycles() {
		HintParseException direct= assertThrows(HintParseException.class,
				() -> new HintProgramParser().parse("""
						<!id: direct-cycle>
						<!predicate loop($x): loop($x)>
						foo($x) :: loop($x)
						=> bar($x)
						;;
						"""));
		assertTrue(direct.getMessage().contains("Recursive predicate cycle")); //$NON-NLS-1$

		HintParseException indirect= assertThrows(HintParseException.class,
				() -> new HintProgramParser().parse("""
						<!id: indirect-cycle>
						<!predicate first($x): second($x)>
						<!predicate second($x): first($x)>
						foo($x) :: first($x)
						=> bar($x)
						;;
						"""));
		assertTrue(indirect.getMessage().contains("first -> second -> first")); //$NON-NLS-1$
	}
}
