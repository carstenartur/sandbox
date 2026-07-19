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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.mining.core.candidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder;

/** Tests deterministic behavior verification for mined candidates. */
class CandidateVerifierTest {

	private final CandidateVerifier verifier = new CandidateVerifier();

	@Test
	void verifiesFullSourceReplacementAndNegativeExample() {
		CandidateVerification result = verifier.verify(validCandidate());

		assertTrue(result.successful(), result.message());
		assertEquals(CandidateVerification.Stage.SUCCESS, result.stage());
		assertEquals(1, result.replacements());
	}

	@Test
	void verifiesTypeGuardWithResolvedBinding() {
		MiningCandidate candidate = validCandidate();
		candidate.setDslRule("$value.trim() :: instanceof($value, \"java.lang.String\")\n" //$NON-NLS-1$
				+ "=> $value.strip()\n;;"); //$NON-NLS-1$
		candidate.setBeforeExample(
				"class Test { String m(String value) { return value.trim(); } }"); //$NON-NLS-1$
		candidate.setAfterExample(
				"class Test { String m(String value) { return value.strip(); } }"); //$NON-NLS-1$
		candidate.setNegativeExample(
				"class Test { String m(String value) { return value.strip(); } }"); //$NON-NLS-1$

		CandidateVerification result = verifier.verify(candidate);

		assertTrue(result.successful(), result.message());
		assertEquals(CandidateVerification.Stage.SUCCESS, result.stage());
	}

	@Test
	void rejectsTypeGuardForWrongResolvedReceiverType() {
		MiningCandidate candidate = validCandidate();
		candidate.setDslRule("$value.trim() :: instanceof($value, \"java.lang.String\")\n" //$NON-NLS-1$
				+ "=> $value.strip()\n;;"); //$NON-NLS-1$
		candidate.setBeforeExample("""
				class Box { String trim() { return ""; } }
				class Test { String m(Box value) { return value.trim(); } }
				"""); //$NON-NLS-1$
		candidate.setAfterExample(candidate.getBeforeExample());
		candidate.setNegativeExample("""
				class Box { String strip() { return ""; } }
				class Test { String m(Box value) { return value.strip(); } }
				"""); //$NON-NLS-1$

		CandidateVerification result = verifier.verify(candidate);

		assertFalse(result.successful());
		assertEquals(CandidateVerification.Stage.BEFORE_MATCH, result.stage());
	}

	@Test
	void rejectsFragmentToFullSourceMismatch() {
		MiningCandidate candidate = validCandidate();
		candidate.setAfterExample("class Test { void m() { int r = 2; } }"); //$NON-NLS-1$

		CandidateVerification result = verifier.verify(candidate);

		assertFalse(result.successful());
		assertEquals(CandidateVerification.Stage.AFTER_REWRITE, result.stage());
	}

	@Test
	void structuralComparisonPreservesWhitespaceInsideLiterals() {
		MiningCandidate candidate = validCandidate();
		candidate.setBeforeExample(
				"class Test { void m() { String s = \"a  b\"; int r = 1 + 0; } }"); //$NON-NLS-1$
		candidate.setAfterExample(
				"class Test { void m() { String s = \"a b\"; int r = 1; } }"); //$NON-NLS-1$

		CandidateVerification result = verifier.verify(candidate);

		assertFalse(result.successful());
		assertEquals(CandidateVerification.Stage.AFTER_REWRITE, result.stage());
	}

	@Test
	void restoresPreviousGuardResolver() {
		Function<String, GuardFunction> original = GuardFunctionResolverHolder.getResolver();
		Function<String, GuardFunction> previous = name -> null;
		GuardFunctionResolverHolder.setResolver(previous);
		try {
			verifier.verify(validCandidate());
			assertSame(previous, GuardFunctionResolverHolder.getResolver());
		} finally {
			GuardFunctionResolverHolder.setResolver(original);
		}
	}

	@Test
	void rejectsNegativeExampleThatMatches() {
		MiningCandidate candidate = validCandidate();
		candidate.setNegativeExample(candidate.getBeforeExample());

		CandidateVerification result = verifier.verify(candidate);

		assertFalse(result.successful());
		assertEquals(CandidateVerification.Stage.NEGATIVE_MATCH, result.stage());
	}

	@Test
	void rejectsAmbiguousPositiveExample() {
		MiningCandidate candidate = validCandidate();
		candidate.setBeforeExample(
				"class Test { void m() { int a = 1 + 0; int b = 2 + 0; } }"); //$NON-NLS-1$
		candidate.setAfterExample(
				"class Test { void m() { int a = 1; int b = 2; } }"); //$NON-NLS-1$

		CandidateVerification result = verifier.verify(candidate);

		assertFalse(result.successful());
		assertEquals(CandidateVerification.Stage.BEFORE_MATCH, result.stage());
		assertTrue(result.message().contains("ambiguous")); //$NON-NLS-1$
	}

	@Test
	void rejectsIncompleteCandidateSchema() {
		MiningCandidate candidate = validCandidate();
		candidate.setNegativeExample(" "); //$NON-NLS-1$

		CandidateVerification result = verifier.verify(candidate);

		assertFalse(result.successful());
		assertEquals(CandidateVerification.Stage.SCHEMA, result.stage());
		assertTrue(result.message().contains("negativeExample")); //$NON-NLS-1$
	}

	@Test
	void rejectsInvalidDslBeforeBehaviorEvaluation() {
		MiningCandidate candidate = validCandidate();
		candidate.setDslRule("<!id:>\n=>\n"); //$NON-NLS-1$

		CandidateVerification result = verifier.verify(candidate);

		assertFalse(result.successful());
		assertEquals(CandidateVerification.Stage.DSL_VALIDATION, result.stage());
	}

	@Test
	void rejectsMultipleRulesInOneCandidate() {
		MiningCandidate candidate = validCandidate();
		candidate.setDslRule("$x + 0\n=> $x\n;;\n\n$x * 1\n=> $x\n;;"); //$NON-NLS-1$

		CandidateVerification result = verifier.verify(candidate);

		assertFalse(result.successful());
		assertEquals(CandidateVerification.Stage.DSL_PARSE, result.stage());
		assertTrue(result.message().contains("exactly one")); //$NON-NLS-1$
	}

	@Test
	void rejectsUnknownGuardBeforeMatching() {
		MiningCandidate candidate = validCandidate();
		candidate.setDslRule("$x + 0 :: definitelyUnknownGuard()\n=> $x\n;;"); //$NON-NLS-1$

		CandidateVerification result = verifier.verify(candidate);

		assertFalse(result.successful());
		assertEquals(CandidateVerification.Stage.GUARD_RESOLUTION, result.stage());
		assertTrue(result.message().contains("definitelyUnknownGuard")); //$NON-NLS-1$
	}

	@Test
	void rejectsJavaSyntaxErrorInPositiveExample() {
		MiningCandidate candidate = validCandidate();
		candidate.setBeforeExample("class Test { void m( { }"); //$NON-NLS-1$

		CandidateVerification result = verifier.verify(candidate);

		assertFalse(result.successful());
		assertEquals(CandidateVerification.Stage.BEFORE_PARSE, result.stage());
	}

	@Test
	void rejectsUnresolvedTypeInPositiveExample() {
		MiningCandidate candidate = validCandidate();
		candidate.setBeforeExample("class Test { MissingType m() { return null; } }"); //$NON-NLS-1$

		CandidateVerification result = verifier.verify(candidate);

		assertFalse(result.successful());
		assertEquals(CandidateVerification.Stage.BEFORE_PARSE, result.stage());
		assertTrue(result.message().contains("MissingType")); //$NON-NLS-1$
	}

	@Test
	void rejectsJavaSyntaxErrorInExpectedExample() {
		MiningCandidate candidate = validCandidate();
		candidate.setAfterExample("class Test { void m( { }"); //$NON-NLS-1$

		CandidateVerification result = verifier.verify(candidate);

		assertFalse(result.successful());
		assertEquals(CandidateVerification.Stage.AFTER_PARSE, result.stage());
	}

	private static MiningCandidate validCandidate() {
		MiningCandidate candidate = new MiningCandidate(
				"$x + 0\n=> $x\n;;", //$NON-NLS-1$
				"class Test { void m() { int r = 1 + 0; } }", //$NON-NLS-1$
				"class Test { void m() { int r = 1; } }", //$NON-NLS-1$
				"class Test { void m() { int r = 1 + 2; } }", //$NON-NLS-1$
				"performance.sandbox-hint", //$NON-NLS-1$
				"0123456789abcdef", //$NON-NLS-1$
				"https://github.com/example/repo", //$NON-NLS-1$
				"arithmetic", //$NON-NLS-1$
				"Remove addition of zero", //$NON-NLS-1$
				"2026-07-19T00:00:00Z"); //$NON-NLS-1$
		candidate.setSourceVersion("21"); //$NON-NLS-1$
		return candidate;
	}
}
