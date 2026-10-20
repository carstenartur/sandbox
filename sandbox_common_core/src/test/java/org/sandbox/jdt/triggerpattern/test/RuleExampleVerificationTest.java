/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0: https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder;
import org.sandbox.jdt.triggerpattern.api.RuleExampleRefiner;
import org.sandbox.jdt.triggerpattern.api.RuleExampleSuite;
import org.sandbox.jdt.triggerpattern.api.RuleExampleSuite.Example;
import org.sandbox.jdt.triggerpattern.api.RuleExampleVerifier;
import org.sandbox.jdt.triggerpattern.api.RuleExampleVerifier.Outcome;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuardRegistration;
import org.sandbox.jdt.triggerpattern.internal.RuleAuthoringDocument;
import org.sandbox.jdt.triggerpattern.internal.RuleAuthoringDocument.ChangeExample;

/** Replays persisted evidence through the real shared matcher and JDT bindings. */
class RuleExampleVerificationTest {

	private static final String RULE = """
			<!binding-policy: required>
			$x.strip().isEmpty() :: sourceVersionGE(11) && instanceof($x, java.lang.String)
			=> $x.isBlank()
			;;
			""";
	private final Map<String, String> options = new HashMap<>(JavaCore.getOptions());
	private RuleExampleVerifier verifier;

	@BeforeEach
	void setUp() {
		JavaCore.setComplianceOptions("11", options);
		Map<String, GuardFunction> guards = new HashMap<>();
		BuiltInGuardRegistration.registerAll(guards);
		GuardFunctionResolverHolder.setResolver(guards::get);
		verifier = new RuleExampleVerifier(options, (name, code) -> parse(name, code, true));
	}

	@Test
	void readableSidecarRoundTripsAllSourceBytesAndRejectsInconsistentExpectations() {
		RuleExampleSuite suite = suite();
		String json = suite.toJson();
		assertEquals(suite, RuleExampleSuite.fromJson(json));
		assertTrue(json.contains("schemaVersion"));
		assertTrue(json.contains("expectChange"));
		assertThrows(IllegalArgumentException.class,
				() -> new Example("wrong", "Test.java", "before", "after", false));
		assertThrows(IllegalArgumentException.class,
				() -> new RuleExampleSuite(2, suite.examples()));
		assertThrows(IllegalArgumentException.class,
				() -> new RuleExampleSuite(1, List.of(suite.examples().getFirst(), suite.examples().getFirst())));
		assertThrows(IllegalArgumentException.class,
				() -> RuleExampleSuite.fromJson(json.replace("expectChange", "expectChagne")));
	}

	@Test
	void sidecarRejectsPrimitiveCoercionRatherThanChangingPersistedEvidence() {
		assertThrows(IllegalArgumentException.class,
				() -> RuleExampleSuite.fromJson("{\"schemaVersion\":1.5,\"examples\":[]}"));
		assertThrows(IllegalArgumentException.class,
				() -> RuleExampleSuite.fromJson("{\"schemaVersion\":\"1\",\"examples\":[]}"));
		String json = suite().toJson();
		assertThrows(IllegalArgumentException.class,
				() -> RuleExampleSuite.fromJson(json.replace("\"name\": \"Unicode whitespace\"", "\"name\": 7")));
		assertThrows(IllegalArgumentException.class,
				() -> RuleExampleSuite.fromJson("""
						{"schemaVersion":1,"examples":[{"name":"bad","unitName":"Test.java",
						"before":true,"after":true,"expectChange":false}]}
						"""));
	}

	@Test
	void positiveAndCounterexamplePassExactOutputCompilationAndIdempotence() {
		var report = verifier.verify(RULE, suite());
		assertTrue(report.passed(), report.toString());
		assertEquals(List.of(Outcome.PASS, Outcome.PASS), report.results().stream().map(r -> r.outcome()).toList());
		assertEquals(64, report.ruleSha256().length());
		assertEquals(64, report.suiteSha256().length());
	}

	@Test
	void changedRuleOrExpectedOutputCannotReuseEarlierSuccess() {
		var original = verifier.verify(RULE, suite());
		var changedRule = verifier.verify(RULE.replace("$x.isBlank()", "true"), suite());
		assertFalse(changedRule.passed());
		assertNotEquals(original.ruleSha256(), changedRule.ruleSha256());
		Example positive = suite().examples().getFirst();
		var changedSuite = new RuleExampleSuite(1, List.of(new Example(positive.name(), positive.unitName(),
				positive.before(), positive.after().replace("text.isBlank()", "false"), true), suite().examples().get(1)));
		var changedEvidence = verifier.verify(RULE, changedSuite);
		assertFalse(changedEvidence.passed());
		assertNotEquals(original.suiteSha256(), changedEvidence.suiteSha256());
	}

	@Test
	void negativeThatWouldChangeFailsRatherThanCountingAMatchAsSuccess() {
		Example positive = suite().examples().getFirst();
		var suite = new RuleExampleSuite(1, List.of(positive,
				new Example("must stay", "Test.java", positive.before(), positive.before(), false)));
		var report = verifier.verify(RULE, suite);
		assertFalse(report.passed());
		assertEquals(Outcome.FAIL, report.results().get(1).outcome());
	}

	@Test
	void incompleteEvidenceDoesNotQualifyARule() {
		assertFalse(verifier.verify(RULE, new RuleExampleSuite(1, List.of())).passed());
		assertFalse(verifier.verify(RULE, new RuleExampleSuite(1, List.of(suite().examples().getFirst()))).passed());
		assertFalse(verifier.verify(RULE, new RuleExampleSuite(1, List.of(suite().examples().get(1)))).passed());
	}

	@Test
	void unresolvedSourcesAndDisabledBindingsAreUnknownIncludingNegativeCases() {
		String broken = "class Test { boolean m(Missing text) { return text.strip().isEmpty(); } }";
		var suite = new RuleExampleSuite(1, List.of(suite().examples().getFirst(),
				new Example("missing dependency", "Test.java", broken, broken, false)));
		var report = verifier.verify(RULE, suite);
		assertEquals(Outcome.UNKNOWN, report.results().get(1).outcome());
		assertFalse(report.passed());
		var noBindings = new RuleExampleVerifier(options, (name, code) -> parse(name, code, false));
		assertTrue(noBindings.verify(RULE, suite()).results().stream().allMatch(r -> r.outcome() == Outcome.UNKNOWN));
	}

	@Test
	void invalidReplacementAndNonIdempotentRuleFail() {
		String before = "class Test { boolean m() { return true; } }";
		String after = before.replace("true", "false");
		String negative = "class Test { boolean m(boolean value) { return value; } }";
		var suite = new RuleExampleSuite(1, List.of(new Example("repeat", "Test.java", before, after, true),
				new Example("literal", "Test.java", negative, negative, false)));
		var report = verifier.verify("true => false ;;\nfalse => true ;;", suite);
		assertFalse(report.passed());
		assertEquals(Outcome.FAIL, report.results().getFirst().outcome());
		assertTrue(report.results().getFirst().message().contains("second application"), report.toString());
		assertFalse(verifier.verify(RULE.replace("$x.isBlank()", "new Missing()"), suite()).passed());
	}

	@Test
	void unsupportedImportAndEmbeddedRulesNeverReceiveAPass() {
		String withImports = RULE.replace("$x.isBlank()", "java.util.Objects.isNull($x)");
		var report = verifier.verify(withImports, suite());
		assertFalse(report.passed());
		assertTrue(report.results().stream().allMatch(result -> result.outcome() == Outcome.UNKNOWN));
		assertFalse(verifier.verify("<? int helper() { return 1; } ?>\n" + RULE, suite()).passed());
	}

	@Test
	void includedRulesCannotBeSilentlyOmittedFromCompleteDocumentVerification() {
		var report = verifier.verify("<!include: external-rules>\n" + RULE, suite());
		assertFalse(report.passed());
		assertTrue(report.results().stream().allMatch(result -> result.outcome() == Outcome.UNKNOWN));
		assertTrue(report.diagnostic().contains("includes"), report.diagnostic());
	}

	@Test
	void minimumJavaVersionAndHostFiltersCannotBeIgnoredByReplay() {
		assertFalse(verifier.verify("<!minJavaVersion: 17>\n" + RULE, suite()).passed());
		assertTrue(verifier.verify("<!minJavaVersion: 11>\n" + RULE, suite()).passed());
		var suppressed = verifier.verify("<!suppressWarnings: blank>\n" + RULE, suite());
		assertFalse(suppressed.passed());
		assertTrue(suppressed.results().stream().allMatch(result -> result.outcome() == Outcome.UNKNOWN));
	}

	@Test
	void refinerGeneralizesOnlyCandidatesThatPassBothKindsOfExample() {
		ChangeExample seed = new ChangeExample("text.strip().isEmpty()", "text.isBlank()");
		Example original = suite().examples().getFirst();
		var evidence = new RuleExampleSuite(1, List.of(original,
				new Example("renamed", "Test.java", original.before().replace("text", "input"),
						original.after().replace("text", "input"), true), suite().examples().get(1)));
		var proposal = RuleExampleRefiner.refine(seed, evidence, verifier,
				change -> RuleAuthoringDocument.fromChange(change.before(), change.after(),
						"sourceVersionGE(11) && instanceof($text, java.lang.String)").content()).orElseThrow();
		assertEquals(Set.of("text"), proposal.generalizedVariables());
		assertEquals("$text.strip().isEmpty()", proposal.example().before());
		assertTrue(proposal.report().passed());
		var contradictory = new RuleExampleSuite(1, List.of(original,
				new Example("contradiction", "Test.java", original.before(), original.before(), false)));
		assertTrue(RuleExampleRefiner.refine(seed, contradictory, verifier,
				change -> RuleAuthoringDocument.fromChange(change.before(), change.after(), "").content()).isEmpty());
	}

	private RuleExampleSuite suite() {
		String before = "class Test { boolean m(String text) { return text.strip().isEmpty(); } }";
		String negative = "class Test { boolean m(String text) { return text.trim().isEmpty(); } }";
		return new RuleExampleSuite(1, List.of(
				new Example("Unicode whitespace", "Test.java", before, before.replace("strip().isEmpty()", "isBlank()"), true),
				new Example("ASCII trim", "Test.java", negative, negative, false)));
	}

	private CompilationUnit parse(String name, String source, boolean bindings) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		parser.setUnitName(name);
		parser.setCompilerOptions(options);
		parser.setResolveBindings(bindings);
		parser.setBindingsRecovery(true);
		parser.setEnvironment(new String[0], null, null, true);
		return (CompilationUnit) parser.createAST(null);
	}
}
