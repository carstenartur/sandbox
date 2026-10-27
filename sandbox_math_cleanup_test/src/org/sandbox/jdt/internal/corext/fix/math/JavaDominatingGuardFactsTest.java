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

class JavaDominatingGuardFactsTest {
    private static final String EXACT = "x != null && x.getClass() == BigInteger.class";
    private static final String BOUND = "x.bitLength() < 4096";
    private static final String POSITIVE = "x.signum() > 0";
    private static final String TARGET = "return x.add(x);";

    @Test void dominatingGuardProvesAllRequiredReceiverProperties() {
        assertEquals(Set.of("x"), facts(method(EXACT + " && " + BOUND + " && " + POSITIVE), true));
    }
    @Test void nestedGuardsCanContributeIndependentFacts() {
        assertEquals(Set.of("x"), facts(source("if (" + EXACT + ") { if (" + BOUND
                + " && " + POSITIVE + ") { " + TARGET + " } } return x;"), true));
    }
    @Test void positiveBaseIsNotRequiredWhenOnlyItsMagnitudeAndTypeMatter() {
        assertEquals(Set.of("x"), facts(method(EXACT + " && " + BOUND), false));
    }
    @Test void positiveModulusIsNotInferredFromMagnitude() {
        assertEquals(Set.of(), facts(method(EXACT + " && " + BOUND), true));
    }
    @Test void disjunctionDoesNotEstablishEitherOperand() {
        assertEquals(Set.of(), facts(method("(" + EXACT + " && " + BOUND + " && " + POSITIVE + ") || true"), true));
    }
    @Test void falseBranchDoesNotInheritTrueBranchFacts() {
        assertEquals(Set.of(), facts(source("if (" + EXACT + " && " + BOUND + " && " + POSITIVE
                + ") { return x; } else { " + TARGET + " }"), true));
    }
    @Test void instanceofDoesNotProveExactDispatch() {
        assertEquals(Set.of(), facts(method("x instanceof BigInteger && " + BOUND + " && " + POSITIVE), true));
    }
    @Test void weakerMagnitudeLimitIsRejected() {
        assertEquals(Set.of(), facts(method(EXACT + " && x.bitLength() < 4097 && " + POSITIVE), true));
    }
    @Test void inclusiveEquivalentLimitIsSupported() {
        assertEquals(Set.of("x"), facts(method(EXACT + " && x.bitLength() <= 4095 && x.signum() == 1"), true));
    }
    @Test void reassignedReferenceRetainsChecks() {
        assertEquals(Set.of(), facts(source("if (" + EXACT + " && " + BOUND + " && " + POSITIVE
                + ") { x = null; " + TARGET + " } return x;"), true));
    }
    @Test void mutableFieldsAreNotSourceFacts() {
        String text = "import java.math.BigInteger; class Sample { BigInteger x; BigInteger f() { if ("
                + EXACT + " && " + BOUND + " && " + POSITIVE + ") { " + TARGET + " } return x; }}";
        assertEquals(Set.of(), facts(text, true));
    }
    @Test void factsFromPreviousNonDominatingBranchAreNotReused() {
        assertEquals(Set.of(), facts(source("if (" + EXACT + " && " + BOUND + " && " + POSITIVE
                + ") { System.out.print(1); } " + TARGET), true));
    }
    @Test void factsAreBoundToTheActualReceiverNotAnotherLocal() {
        assertEquals(Set.of(), facts(source("BigInteger y = BigInteger.ONE; if (y.getClass() == BigInteger.class"
                + " && y.bitLength() < 4096 && y.signum() > 0) { " + TARGET + " } return x;"), true));
    }
    @Test void sourceCommentsCannotEstablishFacts() {
        assertEquals(Set.of(), facts(source("// " + EXACT + " && " + BOUND + " && " + POSITIVE + "\n" + TARGET), true));
    }
    @Test void noFactsCrossLambdaExecutionBoundaries() {
        assertEquals(Set.of(), facts(source("if (" + EXACT + " && " + BOUND + " && " + POSITIVE
                + ") { java.util.function.Supplier<BigInteger> supplier = () -> { " + TARGET
                + " }; return supplier.get(); } return x;"), true));
    }
    @Test void guardMustDominateTheCompleteSelectedRegion() {
        String text = method(EXACT + " && " + BOUND + " && " + POSITIVE);
        var unit = parse(text);
        int start = text.indexOf(TARGET);
        assertEquals(Set.of(), JavaDominatingGuardFacts.provenReceivers(unit, start,
                text.lastIndexOf("return x;") + "return x;".length() - start, Map.of("x", true)));
    }
    @Test void fractionalBoundaryDoesNotBecomeAnIntegerProof() {
        assertEquals(Set.of(), facts(method(EXACT + " && x.bitLength() < 4096.5 && " + POSITIVE), true));
    }
    @Test void unrelatedUserMethodsDoNotEstablishBigIntegerFacts() {
        assertEquals(Set.of(), facts(method(EXACT + " && x.bitCount() < 4096 && " + POSITIVE), true));
    }
    @Test void immutableAliasesShareFactsInBothDirections() {
        for (boolean guardAlias : new boolean[] {false, true}) {
            String condition = EXACT + " && " + BOUND + " && " + POSITIVE;
            if (guardAlias) condition = condition.replace("x.", "alias.").replace("x !=", "alias !=");
            String target = guardAlias ? TARGET : "return alias.add(alias);";
            String text = source("BigInteger first = x; final BigInteger alias = (first); if (" + condition
                    + ") { " + target + " } return x;");
            assertEquals(Set.of(guardAlias ? "x" : "alias"), JavaDominatingGuardFacts.provenReceivers(
                    parse(text), text.indexOf(target), target.length(), Map.of(guardAlias ? "x" : "alias", true)));
        }
    }
    @Test void aliasFactsDoNotSurviveReassignmentOrAnUnknownFactory() {
        for (String initialization : new String[] {"x; x = BigInteger.TEN", "x.add(BigInteger.ZERO)"}) {
            String text = source("BigInteger alias = " + initialization + "; if (" + EXACT + " && " + BOUND
                    + " && " + POSITIVE + ") { return alias.add(alias); } return x;");
            assertEquals(Set.of(), JavaDominatingGuardFacts.provenReceivers(parse(text),
                    text.indexOf("return alias.add(alias);"), "return alias.add(alias);".length(), Map.of("alias", true)));
        }
    }
    @Test void aliasesDoNotInventMissingModulusFacts() {
        String text = source("BigInteger alias = x; if (" + EXACT + " && " + BOUND
                + ") { return alias.add(alias); } return x;");
        assertEquals(Set.of(), JavaDominatingGuardFacts.provenReceivers(parse(text),
                text.indexOf("return alias.add(alias);"), "return alias.add(alias);".length(), Map.of("alias", true)));
    }
    @Test void directlyCheckedImmutableAliasDoesNotDependOnItsMutableOrigin() {
        String text = source("final BigInteger alias = x; x = BigInteger.TEN; if ("
                + (EXACT + " && " + BOUND + " && " + POSITIVE).replace("x.", "alias.").replace("x !=", "alias !=")
                + ") { return alias.add(alias); } return x;");
        assertEquals(Set.of("alias"), JavaDominatingGuardFacts.provenReceivers(parse(text),
                text.indexOf("return alias.add(alias);"), "return alias.add(alias);".length(), Map.of("alias", true)));
    }
    @Test void checksInsideTheLambdaStillProveItsCapturedReceiver() {
        String text = source("java.util.function.Supplier<BigInteger> s = () -> { if (" + EXACT + " && " + BOUND
                + " && " + POSITIVE + ") { " + TARGET + " } return x; }; return s.get();");
        assertEquals(Set.of("x"), facts(text, true));
    }
    private static String method(String condition) {
        return source("if (" + condition + ") { " + TARGET + " } return x;");
    }
    private static String source(String body) {
        return "import java.math.BigInteger; class Sample { static BigInteger f(BigInteger x) { " + body + " }}";
    }
    private static Set<String> facts(String text, boolean positive) {
        return JavaDominatingGuardFacts.provenReceivers(parse(text), text.indexOf(TARGET), TARGET.length(), Map.of("x", positive));
    }
    private static CompilationUnit parse(String text) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(text.toCharArray()); parser.setUnitName("Sample.java");
        parser.setEnvironment(new String[0], new String[0], null, true); parser.setResolveBindings(true);
        var options = new HashMap<String, String>(); JavaCore.setComplianceOptions("17", options); parser.setCompilerOptions(options);
        var unit = (CompilationUnit) parser.createAST(null);
        for (var problem : unit.getProblems()) assertFalse(problem.isError(), problem.toString());
        return unit;
    }
}
