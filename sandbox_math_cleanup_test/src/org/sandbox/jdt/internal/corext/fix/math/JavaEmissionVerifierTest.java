/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import de.regelsuche.sdk.optimization.*;
import de.regelsuche.sdk.optimization.OptimizationResult.Candidate;

class JavaEmissionVerifierTest {
    @Test
    void validEmissionPassesIndependentParsingAndBothProofs() {
        Fixture fixture = fixture(NumericKind.INT, SafetyProfile.PRESERVE_JAVA, "(x+1)-1");
        assertDoesNotThrow(() -> verify(fixture, fixture.emission()));
    }

    @Test
    void changedOutputLiteralIsRejected() {
        Fixture fixture = fixture(NumericKind.INT, SafetyProfile.PRESERVE_JAVA, "(x+1)-1");
        var tampered = new JavaComputationEmitter.Emission(fixture.emission().statements(), Map.of("output0", "0"));
        assertThrows(IllegalArgumentException.class, () -> verify(fixture, tampered));
    }

    @Test
    void changedInputOperatorIsRejected() {
        Fixture fixture = fixture(NumericKind.INT, SafetyProfile.PRESERVE_JAVA, "(x+1)-1");
        String modified = fixture.emission().statements().replace(" = x;", " = -x;");
        assertNotEquals(fixture.emission().statements(), modified, "The mutation must alter emitted Java");
        assertThrows(IllegalArgumentException.class, () -> verify(fixture,
                new JavaComputationEmitter.Emission(modified, fixture.emission().outputValues())));
    }

    @Test
    void correctOutputCannotHideAnAdditionalSideEffect() {
        Fixture fixture = fixture(NumericKind.INT, SafetyProfile.PRESERVE_JAVA, "(x+1)-1");
        var tampered = new JavaComputationEmitter.Emission(
                "System.setProperty(\"math.review\", \"side-effect\");\n" + fixture.emission().statements(),
                fixture.emission().outputValues());
        assertThrows(IllegalArgumentException.class, () -> verify(fixture, tampered));
    }

    @Test
    void correctOutputCannotHideAnUnusedThrowingDeclaration() {
        Fixture fixture = fixture(NumericKind.INT, SafetyProfile.PRESERVE_JAVA, "(x+1)-1");
        var tampered = new JavaComputationEmitter.Emission(
                "int _mathReviewDead = 1 / 0;\n" + fixture.emission().statements(),
                fixture.emission().outputValues());
        assertThrows(IllegalArgumentException.class, () -> verify(fixture, tampered));
    }

    @Test
    void correctOutputCannotHideAThrowingOperationInsideAUsedInitializer() {
        Fixture fixture = fixture(NumericKind.INT, SafetyProfile.PRESERVE_JAVA, "(x+1)-1");
        String modified = fixture.emission().statements().replace(" = x;", " = x + (1 / 0) * 0;");
        assertNotEquals(fixture.emission().statements(), modified);
        assertThrows(IllegalArgumentException.class, () -> verify(fixture,
                new JavaComputationEmitter.Emission(modified, fixture.emission().outputValues())));
    }

    @Test
    void correctOutputCannotHideAnAdditionalExactArithmeticException() {
        Fixture fixture = fixture(NumericKind.INT, SafetyProfile.PRESERVE_JAVA, "(x+1)-1");
        var tampered = new JavaComputationEmitter.Emission(
                "int _mathReviewDead = java.lang.Math.addExact(x, 1);\n" + fixture.emission().statements(),
                fixture.emission().outputValues());
        var rejected = assertThrows(IllegalArgumentException.class, () -> verify(fixture, tampered));
        assertTrue(rejected.getMessage().startsWith("EMITTED_TRACE_NOT_PROVED"), rejected.getMessage());
    }

    @Test
    void entireBigIntegerTraceMustPreserveExceptionsAndMagnitudeBounds() {
        Fixture fixture = fixture("class Calculation { int compute() { java.math.BigInteger r="
                + "java.math.BigInteger.valueOf(3).add(java.math.BigInteger.ONE).subtract(java.math.BigInteger.ONE);"
                + " return r.intValue(); }}", NumericKind.BIG_INTEGER, SafetyProfile.PRESERVE_JAVA);
        assertDoesNotThrow(() -> verify(fixture, fixture.emission()));
        for (String extra : List.of("java.math.BigInteger.ONE.divide(java.math.BigInteger.ZERO)",
                "java.math.BigInteger.ONE.shiftLeft(1000000)")) {
            var tampered = new JavaComputationEmitter.Emission(
                    "java.math.BigInteger _mathReviewDead = " + extra + ";\n" + fixture.emission().statements(),
                    fixture.emission().outputValues());
            assertThrows(IllegalArgumentException.class, () -> verify(fixture, tampered), extra);
        }
    }

    @ParameterizedTest
    @EnumSource(value = SafetyProfile.class, names = { "CHECKED_THROW", "GUARDED_FALLBACK" })
    void conditionalFloatingProofCannotExcuseAnIncorrectReplacement(SafetyProfile profile) {
        Fixture fixture = fixture(NumericKind.DOUBLE, profile, "(x+1.0)-x");
        assertDoesNotThrow(() -> verify(fixture, fixture.emission()));
        var tampered = new JavaComputationEmitter.Emission(fixture.emission().statements(), Map.of("output0", "2.0"));
        var rejected = assertThrows(IllegalArgumentException.class, () -> verify(fixture, tampered));
        assertTrue(rejected.getMessage().startsWith("EMITTED_CANDIDATE_MISMATCH"), rejected.getMessage());
    }

    private static void verify(Fixture fixture, JavaComputationEmitter.Emission emission) {
        JavaEmissionVerifier.verify(fixture.request(), fixture.candidate(), emission, fixture.region(), fixture.options(), () -> false);
    }

    private static Fixture fixture(NumericKind kind, SafetyProfile profile, String expression) {
        String javaType = kind == NumericKind.INT ? "int" : "double";
        String source = "class Calculation { " + javaType + " compute(" + javaType + " x) { "
                + javaType + " r=" + expression + "; return r; }}";
        return fixture(source, kind, profile);
    }

    private static Fixture fixture(String source, NumericKind kind, SafetyProfile profile) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(source.toCharArray());
        parser.setUnitName("Calculation.java");
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setResolveBindings(true);
        var compilerOptions = new java.util.HashMap<String, String>();
        JavaCore.setComplianceOptions("17", compilerOptions);
        parser.setCompilerOptions(compilerOptions);
        CompilationUnit ast = (CompilationUnit) parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.getMessage());
        var options = new MathCleanUpOptions(true, Set.of(kind), profile, OptimizationGoal.READABILITY,
                100_000, 2000, profile == SafetyProfile.CHECKED_THROW, 17, List.of());
        var extraction = new JavaComputationExtractor().extract(ast, source, options);
        assertEquals(1, extraction.regions().size(), extraction.diagnostics().toString());
        var region = extraction.regions().getFirst();
        var request = new OptimizationRequest(region.plan(), region.trace(), options.kinds(), "java25-numeric/v1",
                region.assumptions(), profile, options.goal(), new OptimizationBudget(100_000, 2000, 64, 5000),
                profile == SafetyProfile.CHECKED_THROW ? CheckedPolicy.EXPLICIT_DEFAULT : CheckedPolicy.NONE);
        var candidate = assertInstanceOf(Candidate.class, new ComputationOptimizer().optimize(request, () -> false));
        var emission = new JavaComputationEmitter().emit(candidate.prepared(), region.inputNames(), region.reservedNames(), 17);
        return new Fixture(request, candidate, emission, region, options);
    }

    private record Fixture(OptimizationRequest request, Candidate candidate, JavaComputationEmitter.Emission emission,
            JavaComputationRegion region, MathCleanUpOptions options) {}
}
