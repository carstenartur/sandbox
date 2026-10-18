/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.GuardExpression;
import org.sandbox.jdt.triggerpattern.api.GuardExpression.TruthValue;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.RuleMatchDiagnostics;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuardRegistration;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

/** Explains actual structural candidates without changing batch authorization. */
class RuleMatchDiagnosticsTest {

	private Function<String, GuardFunction> previous;

	@BeforeEach
	void installGuards() {
		previous= GuardExpression.getGuardFunctionResolver();
		Map<String, GuardFunction> guards= new HashMap<>();
		BuiltInGuardRegistration.registerAll(guards);
		GuardExpression.setGuardFunctionResolver(guards::get);
	}

	@AfterEach
	void restoreGuards() {
		GuardExpression.setGuardFunctionResolver(previous);
	}

	@Test
	void showsAcceptedAndRejectedOriginalCandidatesWithResolvedTypes() throws Exception {
		String source= "class Example { void m(String text, Integer number) { text.toString(); number.toString(); } }";
		var hint= new HintFileParser().parse("""
				<!binding-policy: required>
				$x.toString() :: $x instanceof java.lang.String
				=> $x
				;;
				""");
		var result= explain(hint, source, true, -1, 0, 10);
		assertEquals(2, result.size());
		assertEquals(TruthValue.MATCH, result.get(0).sourceGuard().truthValue());
		assertEquals("text", result.get(0).plannedReplacement());
		assertEquals("text.toString()", result.get(0).originalSource());
		assertTrue(result.get(0).bindings().stream().anyMatch(binding -> "java.lang.String".equals(binding.resolvedType())));
		assertEquals(TruthValue.NO_MATCH, result.get(1).sourceGuard().truthValue());
		assertNull(result.get(1).plannedReplacement());
		assertTrue(result.get(1).describe().contains("NO_MATCH"));
	}

	@Test
	void missingBindingRemainsUnknownAndCannotProduceRequiredReplacement() throws Exception {
		String source= "class Example { void m(Missing text) { text.toString(); } }";
		var hint= new HintFileParser().parse("""
				<!binding-policy: required>
				$x.toString() :: $x instanceof java.lang.String
				=> $x
				;;
				""");
		var candidate= explain(hint, source, false, -1, 0, 10).getFirst();
		assertEquals(TruthValue.UNKNOWN, candidate.sourceGuard().truthValue());
		assertNull(candidate.plannedReplacement());
		assertFalse(candidate.unknownSemanticRequirements().isEmpty());
		assertTrue(candidate.describe().contains("UNKNOWN"));
	}

	@Test
	void unresolvedConcreteApiIdentityIsExplainedEvenWithoutASourceGuard() throws Exception {
		String source= "import java.util.ArrayList; class Example { Object m() { return new ArrayList<>(); } }";
		var hint= new HintFileParser().parse("""
				<!binding-policy: required>
				new java.util.ArrayList<>()
				=> new java.util.LinkedList<>()
				;;
				""");
		var candidate= explain(hint, source, false, -1, 0, 10).getFirst();
		assertEquals(TruthValue.UNKNOWN, candidate.sourceGuard().truthValue());
		assertNull(candidate.plannedReplacement());
		assertFalse(candidate.batchAccepted());
		assertFalse(candidate.alternatives().getFirst().evaluated());
		assertTrue(candidate.describe().contains("java.util.ArrayList"));
	}

	@Test
	void selectionAndLimitBoundDiagnosticsWithoutListingUnrelatedNodes() throws Exception {
		String source= "class Example { int m(int a, int b) { return (a + 0) + (b + 0); } }";
		var hint= new HintFileParser().parse("$x + 0\n=> $x\n;;");
		assertEquals(1, explain(hint, source, false, -1, 0, 1).size());
		var candidates= explain(hint, source, false, source.indexOf("b + 0"), 5, 10);
		assertEquals(1, candidates.size());
		assertEquals("b + 0", candidates.getFirst().originalSource());
		assertTrue(explain(hint, source, false, -1, 0, 0).isEmpty());
	}

	@Test
	void reportsAlternativeConditionsAndUsesActualProcessorReplacement() throws Exception {
		String source= "class Example { int m(int x) { return x + 0; } }";
		var hint= new HintFileParser().parse("""
				$x + 0
				=> wrong($x) :: sourceVersionGE(99)
				=> $x :: otherwise
				;;
				""");
		var candidate= explain(hint, source, false, -1, 0, 10).getFirst();
		assertEquals(TruthValue.NO_MATCH, candidate.alternatives().get(0).truthValue());
		assertEquals(TruthValue.MATCH, candidate.alternatives().get(1).truthValue());
		assertEquals("x", candidate.plannedReplacement());
	}

	@Test
	void preservesPlaceholderSourceCommentsAndResolvedMethodSignature() throws Exception {
		String source= "class Example { String m(String text) { return (text /* keep */.trim()).toString(); } }";
		var hint= new HintFileParser().parse("$x.toString()\n=> $x\n;;");
		var candidate= explain(hint, source, true, -1, 0, 10).getFirst();
		assertTrue(candidate.bindings().getFirst().source().contains("/* keep */"));
		assertTrue(candidate.matchedMethod().contains("java.lang.Object#toString")
				|| candidate.matchedMethod().contains("java.lang.String#toString"));
	}

	@Test
	void hugeEnclosingClassIsRepresentedByIdentityAndNeverCopiedIntoEachBinding() throws Exception {
		String source= "class Example { /* PRIVATE_CLASS_SENTINEL " + "padding ".repeat(30_000)
				+ " */ int m(int x) { return x + 0; } }";
		var hint= new HintFileParser().parse("$x + 0\n=> $x\n;;");
		var candidate= explain(hint, source, false, -1, 0, 10).getFirst();
		var enclosing= candidate.bindings().stream().filter(binding -> "$this".equals(binding.placeholder())).findFirst().orElseThrow();
		assertTrue(enclosing.source().length() <= 240);
		assertEquals("enclosing type Example", enclosing.source());
		assertTrue(enclosing.originalNode().getLength() > 200_000);
		assertEquals(enclosing.originalNode().getLength(), enclosing.length());
		assertFalse(candidate.describe().contains("PRIVATE_CLASS_SENTINEL"));
		assertTrue(candidate.describe().length() <= 2048);
		assertEquals("x + 0", candidate.originalSource());
		assertEquals("x", candidate.plannedReplacement());
	}

	@Test
	void longExpressionsAreClippedBeforeDiagnosticCopiesWhileActualReplacementRemainsAvailable() throws Exception {
		String source= "class Example { String m() { return (\"" + "a".repeat(20_000) + "\").toString(); } }";
		var hint= new HintFileParser().parse("$x.toString()\n=> $x\n;;");
		var candidate= explain(hint, source, false, -1, 0, 10).getFirst();
		assertTrue(candidate.originalSource().length() <= 1200);
		assertTrue(candidate.bindings().stream().allMatch(binding -> binding.source().length() <= 240));
		assertTrue(candidate.plannedReplacement().length() > 20_000);
		assertTrue(candidate.describe().length() <= 2048);
		assertTrue(candidate.describe().contains("[truncated]"));
	}

	@Test
	void variadicBindingsAndGuardDetailsHaveExplicitDiagnosticBounds() throws Exception {
		String arguments= java.util.stream.IntStream.range(0, 200).mapToObj(index -> "argument" + index)
				.collect(java.util.stream.Collectors.joining(", "));
		String source= "class Example { void m() { old(" + arguments + "); } }";
		var hint= new HintFileParser().parse("old($args$)\n=> modern($args$)\n;;");
		var candidate= explain(hint, source, false, -1, 0, 10).getFirst();
		assertTrue(candidate.bindings().size() <= 64);
		assertTrue(candidate.describe().length() <= 2048);

		Function<String, GuardFunction> base= GuardExpression.getGuardFunctionResolver();
		GuardExpression.setGuardFunctionResolver(name -> "reportUnknown".equals(name) ? (context, args) -> {
			context.markUnknown("guard".repeat(2000), "detail".repeat(2000));
			return false;
		} : base.apply(name));
		var guarded= new HintFileParser().parse("$x + 0 :: reportUnknown(\"" + "arg".repeat(3000)
				+ "\")\n=> $x\n;;");
		var unknown= explain(guarded, "class Example { int m(int x) { return x + 0; } }", false, -1, 0, 10).getFirst();
		assertTrue(unknown.sourceGuard().condition().length() <= 240);
		assertTrue(unknown.unknownSemanticRequirements().stream().allMatch(requirement ->
				requirement.guardName().length() <= 240 && requirement.detail().length() <= 240));
		assertTrue(unknown.describe().length() <= 2048);
	}

	@Test
	void optionalRewritesUseTheSameStrictAlternativeSelectionAsBatchExecution() throws Exception {
		var hint= new HintFileParser().parse("""
				<!binding-policy: optional>
				$x.toString()
				=> $x :: !($x instanceof java.lang.String)
				=> fallback($x) :: otherwise
				;;
				""");
		var candidate= explain(hint, "class Example { Object m(Missing text) { return text.toString(); } }", false, -1, 0, 10).getFirst();
		assertEquals(TruthValue.UNKNOWN, candidate.alternatives().getFirst().truthValue());
		assertFalse(candidate.alternatives().get(1).evaluated());
		assertNull(candidate.plannedReplacement());
		assertFalse(candidate.batchAccepted());
	}

	@Test
	void optionalHintOnlyGuardsObserveTheSameContextAsBatchExecution() throws Exception {
		Function<String, GuardFunction> base= GuardExpression.getGuardFunctionResolver();
		GuardExpression.setGuardFunctionResolver(name -> "noPriorUnknowns".equals(name)
				? (context, args) -> !context.hasUnknownSemanticRequirements() : base.apply(name));
		var hint= new HintFileParser().parse("""
				<!binding-policy: optional>
				new java.util.ArrayList<>() :: noPriorUnknowns()
				;;
				""");
		String source= "import java.util.ArrayList; class Example { Object m() { return new ArrayList<>(); } }";
		var candidate= explain(hint, source, false, -1, 0, 10).getFirst();
		assertTrue(candidate.batchAccepted());
		assertEquals(TruthValue.UNKNOWN, candidate.sourceGuard().truthValue());
		assertFalse(candidate.unknownSemanticRequirements().isEmpty());
	}

	private static List<RuleMatchDiagnostics.Candidate> explain(org.sandbox.jdt.triggerpattern.api.HintFile hint,
			String source, boolean bindings, int offset, int length, int limit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setUnitName("Example.java");
		parser.setResolveBindings(bindings);
		parser.setEnvironment(new String[0], new String[0], null, true);
		Map<String, String> options= new HashMap<>();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		return new RuleMatchDiagnostics(hint).explain((CompilationUnit) parser.createAST(null), source,
				offset, length, limit, options, SemanticRewritePlan.empty());
	}
}
