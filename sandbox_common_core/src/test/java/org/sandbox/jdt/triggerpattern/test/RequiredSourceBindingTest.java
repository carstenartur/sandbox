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
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.GuardExpression;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuards;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

/** A concrete API identity must be resolved before a required-binding rewrite. */
class RequiredSourceBindingTest {

	@Test
	void sourceVersionExclusionPrecedesUnresolvedApiDiagnostic() throws Exception {
		var previousResolver = GuardExpression.getGuardFunctionResolver();
		try {
			var guards = new HashMap<String, GuardFunction>();
			BuiltInGuards.registerAll(guards);
			GuardExpression.setGuardFunctionResolver(guards::get);
			var hints = new HintFileParser().parse("""
					new java.util.ArrayList<>() :: sourceVersionGE(9)
					=> new java.util.LinkedList<>()
					;;
					""");
			ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
			parser.setSource("import java.util.ArrayList; class Example { Object make() { return new ArrayList<>(); } }".toCharArray());
			CompilationUnit cu = (CompilationUnit) parser.createAST(null);
			var processor = new BatchTransformationProcessor(hints);

			assertTrue(processor.process(cu, Map.of(JavaCore.COMPILER_SOURCE, "1.8")).isEmpty());
			var eligible = processor.process(cu, Map.of(JavaCore.COMPILER_SOURCE, "21"));
			assertEquals(1, eligible.size());
			assertTrue(eligible.getFirst().isSemanticUnknown());
			assertFalse(eligible.getFirst().hasRewrite());
		} finally {
			GuardExpression.setGuardFunctionResolver(previousResolver);
		}
	}

	@Test
	void requiredConstructorIdentityProducesDiagnosticWhenBindingsAreUnavailable() throws Exception {
		TransformationResult result = process("required", false);
		assertFalse(result.hasRewrite());
		assertTrue(result.isSemanticUnknown());
		assertTrue(result.description().contains("java.util.ArrayList"));
	}

	@Test
	void executableRuleRequiresConstructorIdentityEvenWithOptionalPolicy() throws Exception {
		TransformationResult result = process("optional", false);

		assertFalse(result.hasRewrite());
		assertTrue(result.isSemanticUnknown());
	}

	@Test
	void requiredConstructorIdentityAllowsResolvedCompatibleApi() throws Exception {
		TransformationResult result = process("required", true);

		assertTrue(result.hasRewrite());
		assertFalse(result.isSemanticUnknown());
	}

	private TransformationResult process(String policy, boolean resolveBindings) throws Exception {
		var hints = new HintFileParser().parse("""
				<!binding-policy: %s>
				new java.util.ArrayList<>()
				=> new java.util.LinkedList<>()
				;;
				""".formatted(policy));
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource("import java.util.ArrayList; class Example { Object make() { return new ArrayList<>(); } }".toCharArray());
		if (resolveBindings) {
			parser.setUnitName("Example.java");
			parser.setEnvironment(new String[0], new String[0], null, true);
			parser.setResolveBindings(true);
		}
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		var results = new BatchTransformationProcessor(hints).process(cu);
		assertEquals(1, results.size());
		return results.getFirst();
	}
}
