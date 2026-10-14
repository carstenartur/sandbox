/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavaEarlyExitGuardFactsTest {
    private static final String VALID = "x != null && x.getClass() == BigInteger.class && x.bitLength() < 4096 && x.signum() > 0";
    private static final String INVALID = "x == null || x.getClass() != BigInteger.class || x.bitLength() >= 4096 || x.signum() <= 0";
    private static final String TARGET = "return x.add(x);";

    @ParameterizedTest
    @ValueSource(strings = {"return BigInteger.ZERO;", "throw new IllegalArgumentException();",
            "{ return BigInteger.ZERO; }", "{ { throw new IllegalArgumentException(); } }"})
    void completeGuardBeforeUnconditionalExitAppliesToContinuation(String exit) {
        assertEquals(Set.of("x"), facts("if (" + INVALID + ") " + exit + TARGET));
    }
    @Test void negatedCompleteConjunctionIsSupported() {
        assertEquals(Set.of("x"), facts("if (!(" + VALID + ")) return BigInteger.ZERO;" + TARGET));
    }
    @Test void separateExitsAndNestedThenBranchCombineTheirFacts() {
        assertEquals(Set.of("x"), facts("if (x == null) return BigInteger.ZERO;"
                + "if (x.getClass() != BigInteger.class) throw new IllegalArgumentException();"
                + "if (x.bitLength() < 4096) { if (x.signum() < 1) return BigInteger.ZERO; " + TARGET + " } return x;"));
    }
    @Test void earlierOuterGuardDominatesANestedCalculation() {
        assertEquals(Set.of("x"), facts("if (" + INVALID + ") return BigInteger.ZERO; if (flag) { " + TARGET + " } return x;"));
    }
    @Test void unrelatedInterveningCallDoesNotMutateAnExactImmutableReceiver() {
        assertEquals(Set.of("x"), facts("if (" + INVALID + ") return BigInteger.ZERO; touch();" + TARGET));
    }
    @ParameterizedTest
    @ValueSource(strings = {
            "if (BAD) touch(); TARGET",
            "if (BAD && flag) return BigInteger.ZERO; TARGET",
            "if (BAD) { if (flag) return BigInteger.ZERO; } TARGET",
            "if (flag) { if (BAD) return BigInteger.ZERO; } TARGET",
            "try { if (BAD) throw new IllegalArgumentException(); } catch (IllegalArgumentException e) { } TARGET",
            "if (BAD) { try { return BigInteger.ZERO; } finally { if (flag) touch(); } } TARGET",
            "if (BAD) return BigInteger.ZERO; else touch(); TARGET",
            "TARGET"
    })
    void pathsWithoutTheSupportedUnconditionalExitRetainChecks(String body) {
        assertEquals(Set.of(), facts(body.replace("BAD", INVALID).replace("TARGET", TARGET)));
    }
    @Test void boundsMustNotBeWeakenedByNegatingComparison() {
        assertEquals(Set.of(), facts("if (" + INVALID.replace(">= 4096", "> 4096") + ") return BigInteger.ZERO;" + TARGET));
    }
    @Test void fractionalBoundDoesNotBecomeAnIntegralProof() {
        assertEquals(Set.of(), facts("if (" + INVALID.replace("4096", "4096.5") + ") return BigInteger.ZERO;" + TARGET));
    }
    @Test void reassignmentsInvalidateEarlierFacts() {
        assertEquals(Set.of(), facts("if (" + INVALID + ") return BigInteger.ZERO; x = null;" + TARGET));
    }
    @Test void factsDoNotCrossLambdaExecutionFrames() {
        assertEquals(Set.of(), facts("if (" + INVALID + ") return BigInteger.ZERO;"
                + "java.util.function.Supplier<BigInteger> s = () -> { " + TARGET + " }; return s.get();"));
    }
    @Test void sourceCommentsAreNotGuardClauses() {
        assertEquals(Set.of(), facts("/* if (" + INVALID + ") return BigInteger.ZERO; */" + TARGET));
    }
    @Test void explicitlyFinalParametersCanUseTheSameGuardFacts() {
        String source = source("if (" + INVALID + ") return BigInteger.ZERO;" + TARGET)
                .replace("BigInteger x, boolean flag", "final BigInteger x, boolean flag");
        assertEquals(Set.of("x"), JavaDominatingGuardFacts.provenReceivers(parse(source), source.indexOf(TARGET),
                TARGET.length(), Map.of("x", true)));
    }
    @Test void existingThenBranchBehaviorIsPreserved() {
        assertEquals(Set.of("x"), facts("if (" + VALID + ") {" + TARGET + "} return x;"));
    }
    @Test void aFalseDisjunctionCanAlsoDescribeAnEnclosingTrueBranch() {
        assertEquals(Set.of("x"), facts("if (!(" + INVALID + ")) {" + TARGET + "} return x;"));
    }
    @Test void earlyExitAfterSelectedRegionDoesNotApply() {
        String source = source("BigInteger y = x.add(x); if (" + INVALID + ") return BigInteger.ZERO; return y;");
        assertEquals(Set.of(), JavaDominatingGuardFacts.provenReceivers(parse(source), source.indexOf("BigInteger y"),
                "BigInteger y = x.add(x);".length(), Map.of("x", true)));
    }
    @Test void scanningManyPriorStatementsIsBounded() {
        assertEquals(Set.of(), facts("touch();".repeat(4100) + "if (" + INVALID + ") return BigInteger.ZERO;" + TARGET));
    }
    private static Set<String> facts(String body) {
        String source = source(body);
        return JavaDominatingGuardFacts.provenReceivers(parse(source), source.indexOf(TARGET), TARGET.length(), Map.of("x", true));
    }
    private static String source(String body) {
        return "import java.math.BigInteger; class Sample { static void touch() {}"
                + " static BigInteger f(BigInteger x, boolean flag) { " + body + " }}";
    }
    static CompilationUnit parse(String source) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(source.toCharArray()); parser.setUnitName("Sample.java");
        parser.setEnvironment(new String[0], new String[0], null, true); parser.setResolveBindings(true);
        var options = new HashMap<String, String>(); JavaCore.setComplianceOptions("17", options); parser.setCompilerOptions(options);
        var unit = (CompilationUnit) parser.createAST(null);
        for (var problem : unit.getProblems()) assertFalse(problem.isError(), problem.toString());
        return unit;
    }
}
