/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.GuardContext;
import org.sandbox.jdt.triggerpattern.api.HintBindingPolicy;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/** Source API identity is a prerequisite independent of rewrite guards. */
class SourcePatternBindingAuthorizationTest {

	private static final Pattern SOURCE = Pattern.of("java.util.Collections.emptyList()", PatternKind.METHOD_CALL);

	@Test
	void everyGuardContextFactoryRetainsSourceIdentityRequirements() {
		CompilationUnit cu = parse(false);
		Match match = new TriggerPatternEngine().findMatches(cu, SOURCE).get(0);
		assertFalse(match.getUnknownSemanticRequirements().isEmpty());
		for (GuardContext context : List.of(GuardContext.fromMatch(match, cu),
				GuardContext.fromMatch(match, cu, Map.of()),
				GuardContext.fromMatch(match, cu, Map.of(), SemanticRewritePlan.empty()))) {
			assertEquals(match.getUnknownSemanticRequirements(), context.getUnknownSemanticRequirements());
			assertTrue(context.hasUnknownSemanticRequirements());
		}
	}

	@Test
	void strictBatchReturnsTheSourceIdentityDiagnosticBeforeChoosingOtherwise() {
		CompilationUnit cu = parse(false);
		List<BatchTransformationProcessor.TransformationResult> results = processor().process(cu);

		assertEquals(1, results.size());
		var result = results.get(0);
		assertFalse(result.hasRewrite());
		assertFalse(result.hasImportDirective());
		assertTrue(result.isSemanticUnknown());
		assertEquals(result.match().getUnknownSemanticRequirements(), result.unknownSemanticRequirements());
		assertTrue(result.description().contains("source-pattern"));
		assertTrue(result.description().contains("java.util.Collections"));
	}

	@Test
	void resolvedSourceIdentityStillAuthorizesTheStrictReplacement() {
		var results = processor().process(parse(true));

		assertEquals(1, results.size());
		assertTrue(results.get(0).hasRewrite());
		assertFalse(results.get(0).isSemanticUnknown());
	}

	private static BatchTransformationProcessor processor() {
		HintFile file = new HintFile();
		file.setBindingPolicy(HintBindingPolicy.REQUIRED);
		file.addRule(TransformationRule.of(SOURCE, List.of(RewriteAlternative.otherwise("java.util.List.of()"))));
		return new BatchTransformationProcessor(file);
	}

	private static CompilationUnit parse(boolean bindings) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource("import java.util.Collections; class Example { Object run() { return Collections.emptyList(); } }".toCharArray());
		if (bindings) {
			parser.setResolveBindings(true);
			parser.setEnvironment(new String[0], new String[0], null, true);
			parser.setUnitName("Example.java");
		}
		return (CompilationUnit) parser.createAST(null);
	}
}
