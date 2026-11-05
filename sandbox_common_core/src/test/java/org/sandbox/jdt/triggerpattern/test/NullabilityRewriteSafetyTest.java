/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.DryRunReporter;
import org.sandbox.jdt.triggerpattern.api.GuardContext;
import org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/** Exercise nullability guards through the real hint transformation processor. */
class NullabilityRewriteSafetyTest extends HintRuleTestSupport {

	private HintFile nonNull;

	@BeforeEach
	void setUp() throws Exception {
		registerBuiltInGuards();
		nonNull= parseHint("$x.toString() :: isNonNull($x)\n=> \"rewritten\"\n;;\n"); //$NON-NLS-1$
	}

	@Test
	void referenceTypeAndEnumDoNotProveAValueNonNull() {
		assertNotRewritten("class Test { String f(String s) { return s.toString(); } }"); //$NON-NLS-1$
		assertNotRewritten("class Test { String f(Thread.State s) { return s.toString(); } }"); //$NON-NLS-1$
	}

	@Test
	void reassignmentInvalidatesInitializerAndGuard() {
		assertNotRewritten("class Test { String f() { Object s = new Object(); s = null; return s.toString(); } }"); //$NON-NLS-1$
		assertNotRewritten("class Test { String f(Object s) { if (s != null) { s = null; return s.toString(); } return \"\"; } }"); //$NON-NLS-1$
		assertNotRewritten("class Test { Object s; String f() { if (s != null) { s = null; return s.toString(); } return \"\"; } }"); //$NON-NLS-1$
	}

	@Test
	void annotationAndMethodNamesAreNotProofs() {
		assertNull(String.valueOf(new Object() {
			@Override
			public String toString() {
				return null;
			}
		}));
		assertNotRewritten("@interface NonNull {} class Test { String f(@NonNull Object s) { return s.toString(); } }"); //$NON-NLS-1$
		assertNotRewritten("class Test { String f(Object s) { return String.valueOf(s).toString(); } }"); //$NON-NLS-1$
		assertNotRewritten("class Test { String f(org.eclipse.jdt.core.dom.MethodDeclaration m) { return m.getBody().toString(); } }"); //$NON-NLS-1$
	}

	@Test
	void directAllocationIsStillAccepted() {
		assertTrue(process(nonNull, "class Test { String f() { return new Object().toString(); } }").stream() //$NON-NLS-1$
				.anyMatch(TransformationResult::hasReplacement));
	}

	@Test
	void stableLocalInitializedWithAllocationIsAccepted() {
		assertTrue(process(nonNull, "class Test { String f() { final Object s = new Object(); return s.toString(); } }").stream() //$NON-NLS-1$
				.anyMatch(TransformationResult::hasReplacement));
		assertTrue(process(nonNull, "class Test { String f(boolean b) { final Object s = new Object(); if (b) { return s.toString(); } return \"\"; } }").stream() //$NON-NLS-1$
				.anyMatch(TransformationResult::hasReplacement));
	}

	@Test
	void stableLocalInsideNonNullBranchIsAccepted() {
		assertTrue(process(nonNull, "class Test { String f(Object s) { if (s != null) return s.toString(); return \"\"; } }").stream() //$NON-NLS-1$
				.anyMatch(TransformationResult::hasReplacement));
	}

	@Test
	void negatedRiskThresholdIsNotANonNullProof() throws Exception {
		HintFile threshold= parseHint("$x.toString() :: !isNullable($x, 10)\n=> \"rewritten\"\n;;\n"); //$NON-NLS-1$
		assertTrue(process(threshold, "class Test { String f(Object s) { return s.toString(); } }").stream() //$NON-NLS-1$
				.noneMatch(TransformationResult::hasReplacement));
		assertTrue(process(threshold, "class Test { String f() { return new Object().toString(); } }").stream() //$NON-NLS-1$
				.anyMatch(TransformationResult::hasReplacement));
		HintFile repeated= parseHint("$x.toString() :: !isNullable($x, 10) || !isNullable($x, 10)\n=> \"rewritten\"\n;;\n"); //$NON-NLS-1$
		assertTrue(process(repeated, "class Test { String f(Object s) { return s.toString(); } }").stream() //$NON-NLS-1$
				.noneMatch(TransformationResult::hasReplacement));
	}

	@Test
	void publicAlternativeSelectionAndPreviewDoNotTreatUnknownAsTrue() throws Exception {
		HintFile alternatives= parseHint("$x.toString()\n=> \"rewritten\" :: !isNullable($x, 10)\n=> \"fallback\" :: otherwise\n;;\n"); //$NON-NLS-1$
		CompilationUnit unit= parseBound("class Test { String f(Object s) { return s.toString(); } }"); //$NON-NLS-1$
		var rule= alternatives.getRules().get(0);
		var match= new TriggerPatternEngine().findMatches(unit, rule.sourcePattern()).get(0);
		assertTrue(rule.findMatchingAlternative(GuardContext.fromMatch(match, unit)).isEmpty());
		var reporter= new DryRunReporter();
		assertTrue(reporter.analyze(unit, alternatives.getRules(), Map.of(JavaCore.COMPILER_SOURCE, "21")).stream() //$NON-NLS-1$
				.noneMatch(DryRunReporter.ReportEntry::hasReplacement));
		assertTrue(reporter.analyze(unit, alternatives.getRules()).stream()
				.noneMatch(DryRunReporter.ReportEntry::hasReplacement));
		CompilationUnit proven= parseBound("class Test { String f() { return new Object().toString(); } }"); //$NON-NLS-1$
		assertTrue(reporter.analyze(proven, alternatives.getRules()).stream()
				.anyMatch(DryRunReporter.ReportEntry::hasReplacement));
	}

	@Test
	void previewHonorsSourceGuardAndStillShowsProvenCases() throws Exception {
		HintFile rules= parseHint("$x.toString() :: isNonNull($x)\n=> \"rewritten\"\n;;\n"); //$NON-NLS-1$
		var reporter= new DryRunReporter();
		CompilationUnit unknown= parseBound("class Test { String f(Object s) { return s.toString(); } }"); //$NON-NLS-1$
		assertTrue(reporter.analyze(unknown, rules.getRules(), Map.of(JavaCore.COMPILER_SOURCE, "21")).stream() //$NON-NLS-1$
				.noneMatch(DryRunReporter.ReportEntry::hasReplacement));
		assertTrue(reporter.analyze(unknown, rules.getRules()).stream()
				.noneMatch(DryRunReporter.ReportEntry::hasReplacement));
		HintFile threshold= parseHint("$x.toString() :: !isNullable($x, 10)\n=> \"rewritten\"\n;;\n"); //$NON-NLS-1$
		assertTrue(reporter.analyze(unknown, threshold.getRules()).stream()
				.noneMatch(DryRunReporter.ReportEntry::hasReplacement));
		CompilationUnit proven= parseBound("class Test { String f() { return new Object().toString(); } }"); //$NON-NLS-1$
		assertTrue(reporter.analyze(proven, rules.getRules()).stream()
				.anyMatch(DryRunReporter.ReportEntry::hasReplacement));
	}

	@Test
	void standalonePreviewFailsClosedWhenGuardResolverIsUnavailable() throws Exception {
		HintFile rules= parseHint("$x.toString() :: isNonNull($x)\n=> \"rewritten\"\n;;\n"); //$NON-NLS-1$
		CompilationUnit unit= parseBound("class Test { String f(Object s) { return s.toString(); } }"); //$NON-NLS-1$
		var previous= GuardFunctionResolverHolder.getResolver();
		try {
			GuardFunctionResolverHolder.setResolver(null);
			assertTrue(new DryRunReporter().analyze(unit, rules.getRules()).stream()
					.noneMatch(DryRunReporter.ReportEntry::hasReplacement));
			GuardFunctionResolverHolder.setResolver(name -> null);
			assertTrue(new DryRunReporter().analyze(unit, rules.getRules()).stream()
					.noneMatch(DryRunReporter.ReportEntry::hasReplacement));
		} finally {
			GuardFunctionResolverHolder.setResolver(previous);
		}
	}

	@Test
	void previewDoesNotGuessTheSourceVersion() throws Exception {
		HintFile rules= parseHint("$x.toString() :: sourceVersionLE(8)\n=> \"java8\"\n;;\n"); //$NON-NLS-1$
		CompilationUnit unit= parseBound("class Test { String f(Object s) { return s.toString(); } }"); //$NON-NLS-1$
		var reporter= new DryRunReporter();
		assertTrue(reporter.analyze(unit, rules.getRules()).stream()
				.noneMatch(DryRunReporter.ReportEntry::hasReplacement));
		assertTrue(reporter.analyze(unit, rules.getRules(), Map.of(JavaCore.COMPILER_SOURCE, "21")).stream() //$NON-NLS-1$
				.noneMatch(DryRunReporter.ReportEntry::hasReplacement));
		assertTrue(reporter.analyze(unit, rules.getRules(), Map.of(JavaCore.COMPILER_SOURCE, "1.8")).stream() //$NON-NLS-1$
				.anyMatch(DryRunReporter.ReportEntry::hasReplacement));
	}

	private static CompilationUnit parseBound(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setUnitName("Test.java"); //$NON-NLS-1$
		parser.setResolveBindings(true);
		parser.setEnvironment(new String[0], null, null, true);
		Map<String, String> options= JavaCore.getOptions();
		JavaCore.setComplianceOptions("21", options); //$NON-NLS-1$
		parser.setCompilerOptions(options);
		return (CompilationUnit) parser.createAST(null);
	}

	private void assertNotRewritten(String source) {
		assertTrue(process(nonNull, source).stream().noneMatch(TransformationResult::hasReplacement), source);
	}
}
