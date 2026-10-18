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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.triggerpattern.api.GuardContext;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.GuardExpression;
import org.sandbox.jdt.triggerpattern.api.GuardExpression.TruthValue;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuardRegistration;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

class SideEffectGuardTest {

	private static final Map<String, GuardFunction> GUARDS= new HashMap<>();

	@BeforeAll
	static void register() {
		BuiltInGuardRegistration.registerAll(GUARDS);
		GuardExpression.setGuardFunctionResolver(GUARDS::get);
	}

	@ParameterizedTest
	@ValueSource(strings= { "i++", "++i", "i--", "--i", "i = 3", "i += 2", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
			"call()", "(call())", "1 + call()", "array[i++]", "flag ? i : call()", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			"new Object()", "new int[3]", "changing", "this.changing", "holder.changing", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			"Test.counter", "\"x\" + holder", "\"\" + i + holder", "\"\" + array" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	void rejectsEffectsAnywhereInTheExpression(String source) {
		assertFalse(evaluate(context(source, true)), source);
	}

	@ParameterizedTest
	@ValueSource(strings= { "i", "(i)", "-i", "!flag", "i + 2", "i / 0", "array[0]", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
			"holder.value", "\"x\" + i", "\"x\" + text", "flag ? i : 0", "i > 0", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			"holder instanceof Object", "String.class", "Test.CONSTANT", "'x'", "null" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
	void acceptsResolvedReadsWithoutPromisingExceptionFreedomOrStability(String source) {
		assertTrue(evaluate(context(source, true)), source);
	}

	@Test
	void missingPlaceholderFailsClosedAndReportsUnknown() {
		GuardContext parsed= context("i", true); //$NON-NLS-1$
		GuardContext context= GuardContext.fromMatch(new Match(parsed.getMatchedNode(), Map.of(), 0, 0),
				parsed.getCompilationUnit());
		assertFalse(evaluate(context));
		assertTrue(context.hasUnknownSemanticRequirements());
	}

	@Test
	void invalidArityAndNonExpressionBindingsFailClosed() {
		GuardContext context= context("i", true); //$NON-NLS-1$
		GuardFunction guard= GUARDS.get("hasNoSideEffect"); //$NON-NLS-1$
		assertFalse(guard.evaluate(context));
		assertFalse(guard.evaluate(context, "$x", "$x")); //$NON-NLS-1$ //$NON-NLS-2$
		GuardContext nonExpression= GuardContext.fromMatch(new Match(context.getCompilationUnit(),
				Map.of("$x", context.getCompilationUnit()), 0, 0), context.getCompilationUnit()); //$NON-NLS-1$
		assertFalse(evaluate(nonExpression));
	}

	@ParameterizedTest
	@ValueSource(strings= { "i", "holder.value", "\"x\" + i" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	void unresolvedReadsRemainUnknownWhenTheGuardIsNegated(String source) {
		GuardContext context= context(source, false);
		GuardExpression expression= new GuardExpression.Not(
				new GuardExpression.FunctionCall("hasNoSideEffect", List.of("$x"))); //$NON-NLS-1$ //$NON-NLS-2$
		assertFalse(evaluate(context));
		assertEquals(TruthValue.UNKNOWN, expression.evaluateDetailed(context).truthValue());
	}

	@Test
	void recoveredVariableBindingFailsClosed() {
		GuardContext context= context("missing", true); //$NON-NLS-1$
		assertFalse(evaluate(context));
		assertTrue(context.hasUnknownSemanticRequirements());
	}

	@ParameterizedTest
	@ValueSource(strings= { "call()", "(call())", "new Object()", "Test.counter", "\"x\" + holder" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
	void unprovenPurityCannotBecomeProvenImpurityThroughNegation(String source) {
		GuardContext context= context(source, true);
		GuardExpression expression= new GuardExpression.Not(
				new GuardExpression.FunctionCall("hasNoSideEffect", List.of("$x"))); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(TruthValue.UNKNOWN, expression.evaluateDetailed(context).truthValue());
	}

	@ParameterizedTest
	@ValueSource(strings= { "i++", "i = 3", "changing" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	void observedWritesAndVolatileReadsAreDecisiveFailures(String source) {
		GuardContext context= context(source, true);
		GuardExpression expression= new GuardExpression.FunctionCall("hasNoSideEffect", List.of("$x")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(TruthValue.NO_MATCH, expression.evaluateDetailed(context).truthValue());
	}

	@ParameterizedTest
	@ValueSource(strings= { "fixed", "(fixed)", "finalText", "1", "true", "null", "\"text\"" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
	void stableValuesAreLimitedToLiteralsAndFinalLocalReads(String source) {
		assertTrue(GUARDS.get("isStableValue").evaluate(context(source, true), "$x"), source); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@ParameterizedTest
	@ValueSource(strings= { "i", "array[0]", "i / 0", "holder.value", "Test.CONSTANT", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			"changing", "call()", "(String) text", "fixed + 1" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	void effectFreeExpressionsDoNotAutomaticallyEstablishStableNonThrowingValues(String source) {
		assertFalse(GUARDS.get("isStableValue").evaluate(context(source, true), "$x"), source); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void unresolvedStableValueCannotBeNegatedIntoAProof() {
		GuardContext context= context("fixed", false); //$NON-NLS-1$
		GuardExpression expression= new GuardExpression.Not(
				new GuardExpression.FunctionCall("isStableValue", List.of("$x"))); //$NON-NLS-1$ //$NON-NLS-2$
		assertFalse(GUARDS.get("isStableValue").evaluate(context, "$x")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(TruthValue.UNKNOWN, expression.evaluateDetailed(context).truthValue());
	}

	@Test
	void optionalMigrationNeverEagerlyEvaluatesAFormerlyUntakenThrowingDefault() throws Exception {
		Optional<Integer> optional= Optional.of(1);
		int[] empty= {};
		assertEquals(1, optional.isPresent() ? optional.get() : empty[0]);
		assertThrows(ArrayIndexOutOfBoundsException.class, () -> optional.orElse(empty[0]));
		try (InputStream resource= getClass().getResourceAsStream(
				"/org/sandbox/jdt/triggerpattern/internal/modernize-java9.sandbox-hint")) { //$NON-NLS-1$
			assertNotNull(resource);
			HintFile file= new HintFileParser().parse(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
			GuardContext context= context("java.util.Optional.of(1).isPresent() ? java.util.Optional.of(1).get() : array[0]", true); //$NON-NLS-1$
			var results= new BatchTransformationProcessor(file).process(context.getCompilationUnit(),
					Map.of(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_25));
			assertTrue(results.stream().anyMatch(result ->
					"modernize-java9.optional-ternary.to-orelse".equals(result.rule().getRuleId()))); //$NON-NLS-1$
			assertTrue(results.stream().noneMatch(result -> result.hasReplacement() || result.hasStructuredActions()));
		}
	}

	@Test
	void objectEqualsMigrationNeverSilentlyChangesOverloadDispatch() throws Exception {
		OverloadedEquality left= new OverloadedEquality();
		OverloadedEquality right= new OverloadedEquality();
		assertTrue((left == null && right == null) || (left != null && left.equals(right)));
		assertFalse(Objects.equals(left, right));
		try (InputStream resource= getClass().getResourceAsStream(
				"/org/sandbox/jdt/triggerpattern/internal/null-safety.sandbox-hint")) { //$NON-NLS-1$
			assertNotNull(resource);
			HintFile file= new HintFileParser().parse(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
			GuardContext context= context("(holder == null && other == null) || (holder != null && holder.equals(other))", true); //$NON-NLS-1$
			var results= new BatchTransformationProcessor(file).process(context.getCompilationUnit());
			assertFalse(results.isEmpty());
			assertTrue(results.stream().noneMatch(result -> result.hasReplacement() || result.hasStructuredActions()));
		}
	}

	private static final class OverloadedEquality {
		boolean equals(OverloadedEquality other) {
			return true;
		}
	}

	private static boolean evaluate(GuardContext context) {
		return GUARDS.get("hasNoSideEffect").evaluate(context, "$x"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static GuardContext context(String expression, boolean bindings) {
		String source= "class Test { volatile int changing; static int counter; static final int CONSTANT=3; " //$NON-NLS-1$
				+ "int value; static int call() { return 1; } " //$NON-NLS-1$
				+ "Object m(int i, boolean flag, int[] array, Test holder, Test other, String text, final String finalText) { " //$NON-NLS-1$
				+ "final int fixed=7; return " //$NON-NLS-1$
				+ expression + "; } }"; //$NON-NLS-1$
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options= JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_25, options);
		parser.setCompilerOptions(options);
		parser.setResolveBindings(bindings);
		parser.setBindingsRecovery(bindings);
		parser.setEnvironment(new String[0], new String[0], new String[0], true);
		parser.setUnitName("Test.java"); //$NON-NLS-1$
		CompilationUnit cu= (CompilationUnit) parser.createAST(null);
		Expression[] result= new Expression[1];
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(ReturnStatement statement) {
				result[0]= statement.getExpression();
				return false;
			}
		});
		assertNotNull(result[0], source);
		return GuardContext.fromMatch(new Match(result[0], Map.of("$x", result[0]), 0, 0), cu); //$NON-NLS-1$
	}
}
