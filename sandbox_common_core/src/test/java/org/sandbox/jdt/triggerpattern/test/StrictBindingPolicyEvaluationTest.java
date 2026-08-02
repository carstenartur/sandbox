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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuardRegistration;
import org.sandbox.jdt.triggerpattern.internal.HintProgramParser;

/** Verifies optional compatibility and required fail-closed guard semantics. */
class StrictBindingPolicyEvaluationTest {

	@BeforeEach
	void registerGuards() {
		HashMap<String, GuardFunction> guards= new HashMap<>();
		BuiltInGuardRegistration.registerAll(guards);
		GuardFunctionResolverHolder.setResolver(guards::get);
	}

	@Test
	void optionalPolicyKeepsHistoricalUnresolvedBindingFallback() throws Exception {
		HintFile hints= parse("""
				<!binding-policy: optional>
				Assert.assertEquals($message, $expected, $actual)
				    :: $message instanceof java.lang.String
				=> Assertions.assertEquals($expected, $actual, $message)
				;;
				""");

		List<TransformationResult> results= new BatchTransformationProcessor(hints)
				.process(unresolvedSource());

		assertFalse(results.isEmpty());
		assertTrue(results.get(0).hasReplacement());
		assertFalse(results.get(0).isSemanticUnknown());
	}

	@Test
	void requiredPolicyTurnsUnresolvedSourceGuardIntoDiagnostic() throws Exception {
		HintFile hints= parse("""
				<!binding-policy: required>
				@id: strict.assertEquals.message
				Assert.assertEquals($message, $expected, $actual)
				    :: $message instanceof java.lang.String
				=> Assertions.assertEquals($expected, $actual, $message)
				;;
				""");

		List<TransformationResult> results= new BatchTransformationProcessor(hints)
				.process(unresolvedSource());

		assertEquals(1, results.size());
		TransformationResult result= results.get(0);
		assertFalse(result.hasRewrite());
		assertTrue(result.isSemanticUnknown());
		assertTrue(result.description().contains("strict.assertEquals.message")); //$NON-NLS-1$
		assertTrue(result.description().contains("instanceof")); //$NON-NLS-1$
	}

	@Test
	void unknownOrProvenTrueIsAProvenMatch() throws Exception {
		HintFile hints= parse("""
				<!binding-policy: required>
				@id: strict.unknown-or-true
				Assert.assertEquals($message, $expected, $actual)
				    :: $message instanceof java.lang.String || otherwise
				=> Assertions.assertEquals($expected, $actual, $message)
				;;
				""");

		List<TransformationResult> results= new BatchTransformationProcessor(hints)
				.process(unresolvedSource());

		assertEquals(1, results.size());
		assertTrue(results.get(0).hasRewrite());
		assertFalse(results.get(0).isSemanticUnknown());
	}

	@Test
	void repeatedUnknownGuardRemainsUnknownAfterDiagnosticDeduplication() throws Exception {
		HintFile hints= parse("""
				<!binding-policy: required>
				@id: strict.repeated-unknown
				Assert.assertEquals($message, $expected, $actual)
				    :: $message instanceof java.lang.String || $message instanceof java.lang.String
				=> Assertions.assertEquals($expected, $actual, $message)
				;;
				""");

		List<TransformationResult> results= new BatchTransformationProcessor(hints)
				.process(unresolvedSource());

		assertEquals(1, results.size());
		assertTrue(results.get(0).isSemanticUnknown());
		assertFalse(results.get(0).hasRewrite());
		assertEquals(1, results.get(0).unknownSemanticRequirements().size());
	}

	@Test
	void requiredUnknownAlternativeDoesNotFallThroughToOtherwise() throws Exception {
		HintFile hints= parse("""
				<!binding-policy: required>
				@id: strict.alternative
				Assert.assertEquals($a, $b, $c)
				=> Assertions.assertEquals($b, $c, $a) :: $a instanceof java.lang.String
				=> Assertions.assertEquals($a, $b, $c) :: otherwise
				;;
				""");

		List<TransformationResult> results= new BatchTransformationProcessor(hints)
				.process(unresolvedSource());

		assertEquals(1, results.size());
		assertTrue(results.get(0).isSemanticUnknown());
		assertFalse(results.get(0).hasRewrite());
	}

	private static HintFile parse(String source) throws Exception {
		return new HintProgramParser().parseHintFile(source);
	}

	private static CompilationUnit unresolvedSource() {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource("""
				class Sample {
				    void test() {
				        Assert.assertEquals("message", 1, 2);
				    }
				}
				""".toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}
}
