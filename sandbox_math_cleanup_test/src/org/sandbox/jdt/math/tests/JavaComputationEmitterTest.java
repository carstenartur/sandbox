/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationEmitter;
import de.regelsuche.ast.Expr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.sdk.optimization.*;
import de.regelsuche.sdk.optimization.OptimizationResult.*;
import de.regelsuche.search.program.JointComputationPlan;
import de.regelsuche.search.program.ComputationBackend.Type;
import de.regelsuche.search.program.JointComputationPlan.Output;

class JavaComputationEmitterTest {
    @TempDir Path temporary;
    private final JavaComputationEmitter emitter = new JavaComputationEmitter();
    private static final Expr X = new VariableExpr("x");
    private static final Expr Y = new VariableExpr("y");

    @Test
    void sharedDagIsEmittedOnceWithCollisionFreeJava8Names() throws Exception {
        Expr product = op(NumericKind.INT, NumericOperation.MULTIPLY, X, JavaExpressions.literal(2));
        var plan = new JointComputationPlan(Map.of("x", NumericKind.INT.type()), Map.of(),
                List.of(new Output("first", NumericKind.INT.type(), product), new Output("second", NumericKind.INT.type(), product)));
        var emitted = emitter.emit(ComputationOptimizer.prepare(plan), names(plan), Set.of("_math0", "_math1"), 8);
        assertEquals(1, emitted.statements().split(" \\* ", -1).length - 1);
        assertFalse(emitted.statements().contains(" _math0 ="));
        assertFalse(emitted.statements().contains(" _math1 ="));
        assertEquals(emitted.outputValues().get("first"), emitted.outputValues().get("second"));
        Method method = compile("int x", emitted.statements() + returns(emitted.outputValues().values()), 8, int.class);
        assertArrayEquals(new Object[] { -2, -2 }, invoke(method, Integer.MAX_VALUE));
    }

    @Test
    void allPrimitiveLiteralKindsCompileWithoutChangingTheirBits() throws Exception {
        Map<NumericKind, List<Object>> cases = Map.of(
                NumericKind.BYTE, List.of(Byte.MIN_VALUE, Byte.MAX_VALUE),
                NumericKind.SHORT, List.of(Short.MIN_VALUE, Short.MAX_VALUE),
                NumericKind.CHAR, List.of('\0', '\'', '\\', '\n', '\r', '\uffff', '\ud800'),
                NumericKind.INT, List.of(Integer.MIN_VALUE, Integer.MAX_VALUE),
                NumericKind.LONG, List.of(Long.MIN_VALUE, Long.MAX_VALUE),
                NumericKind.FLOAT, List.of(-0.0f, Float.MIN_VALUE, Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.intBitsToFloat(0x7fc12345)),
                NumericKind.DOUBLE, List.of(-0.0d, Double.MIN_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.longBitsToDouble(0x7ff8123456789abcl)));
        for (var entry : cases.entrySet()) for (Object expected : entry.getValue()) {
            NumericKind kind = entry.getKey();
            var plan = plan(kind, Map.of(), literal(expected));
            var emitted = plain(plan, kind.floatingPoint() ? 17 : 8);
            Object actual = invoke(compile("", emitted.statements() + returns(emitted.outputValues().values()), kind.floatingPoint() ? 17 : 8))[0];
            if (kind == NumericKind.FLOAT) assertEquals(Float.floatToRawIntBits((Float)expected), Float.floatToRawIntBits((Float)actual));
            else if (kind == NumericKind.DOUBLE) assertEquals(Double.doubleToRawLongBits((Double)expected), Double.doubleToRawLongBits((Double)actual));
            else assertEquals(expected, actual);
        }
    }

    @Test
    void castsBeforeAndAfterIntegerMultiplicationRemainDistinct() throws Exception {
        Expr after = JavaExpressions.cast(NumericKind.INT, NumericKind.LONG,
                op(NumericKind.INT, NumericOperation.MULTIPLY, X, JavaExpressions.literal(2)));
        Expr before = op(NumericKind.LONG, NumericOperation.MULTIPLY,
                JavaExpressions.cast(NumericKind.INT, NumericKind.LONG, X), JavaExpressions.literal(2L));
        var plan = new JointComputationPlan(Map.of("x", NumericKind.INT.type()), Map.of(),
                List.of(new Output("after", NumericKind.LONG.type(), after), new Output("before", NumericKind.LONG.type(), before)));
        var emitted = plain(plan, 8);
        assertArrayEquals(new Object[] { -2L, 4294967294L },
                invoke(compile("int x", emitted.statements() + returns(emitted.outputValues().values()), 8, int.class), Integer.MAX_VALUE));
    }

    @Test
    void bigIntegerModularLoweringNeedsNoSdkAtRuntime() throws Exception {
        Expr modulus = JavaExpressions.literal(BigInteger.valueOf(17));
        var plan = plan(NumericKind.BIG_INTEGER, Map.of("x", NumericKind.BIG_INTEGER.type(), "y", NumericKind.BIG_INTEGER.type()),
                op(NumericKind.BIG_INTEGER, NumericOperation.MOD_MULTIPLY, X, Y, modulus));
        var emitted = plain(plan, 8);
        assertTrue(emitted.statements().contains(".multiply("));
        assertTrue(emitted.statements().contains(".mod("));
        assertFalse(emitted.statements().contains("regelsuche"));
        BigInteger x = new BigInteger("123456789123456789"), y = BigInteger.valueOf(-11);
        assertEquals(x.multiply(y).mod(BigInteger.valueOf(17)),
                invoke(compile("java.math.BigInteger x, java.math.BigInteger y", emitted.statements() + returns(emitted.outputValues().values()),
                        8, BigInteger.class, BigInteger.class), x, y)[0]);
    }

    @Test
    void preserveKeepsMathExactExceptions() throws Exception {
        var plan = plan(NumericKind.INT, Map.of("x", NumericKind.INT.type()),
                op(NumericKind.INT, NumericOperation.ADD_EXACT, X, JavaExpressions.literal(1)));
        var emitted = plain(plan, 8);
        Method method = compile("int x", emitted.statements() + returns(emitted.outputValues().values()), 8, int.class);
        assertEquals(8, invoke(method, 7)[0]);
        arithmetic(method, null, Integer.MAX_VALUE);
    }

    @Test
    void checkedEliminatedMultiplicationStillDetectsOriginalOverflow() throws Exception {
        Expr expression = op(NumericKind.INT, NumericOperation.DIVIDE,
                op(NumericKind.INT, NumericOperation.MULTIPLY, X, JavaExpressions.literal(2)), JavaExpressions.literal(2));
        var original = plan(NumericKind.INT, Map.of("x", NumericKind.INT.type()), expression);
        var fixture = fixture(original, original.withOutputs(List.of(X)), SafetyProfile.CHECKED_THROW);
        var emitted = emitter.emitChecked(fixture.request(), fixture.candidate(), names(original), Set.of(), 8);
        Method method = compile("int x", emitted.statements() + returns(emitted.outputValues().values()), 8, int.class);
        assertEquals(17, invoke(method, 17)[0]);
        arithmetic(method, "MATH_OVERFLOW", Integer.MAX_VALUE);
        arithmetic(method, "MATH_OVERFLOW", Integer.MIN_VALUE);
    }

    @Test
    void checkedNarrowingUsesTheDeclaredByteRange() throws Exception {
        var original = plan(NumericKind.BYTE, Map.of("x", NumericKind.INT.type()), JavaExpressions.cast(NumericKind.INT, NumericKind.BYTE, X));
        var fixture = fixture(original, original, SafetyProfile.CHECKED_THROW);
        var emitted = emitter.emitChecked(fixture.request(), fixture.candidate(), names(original), Set.of(), 8);
        Method method = compile("int x", emitted.statements() + returns(emitted.outputValues().values()), 8, int.class);
        assertEquals((byte)-128, invoke(method, -128)[0]);
        assertEquals((byte)127, invoke(method, 127)[0]);
        arithmetic(method, "MATH_NARROWING", -129);
        arithmetic(method, "MATH_NARROWING", 128);
    }

    @Test
    void divisionAndRemainderDistinguishTheMinimumOverMinusOne() throws Exception {
        for (NumericOperation operation : List.of(NumericOperation.DIVIDE, NumericOperation.REMAINDER)) {
            var original = plan(NumericKind.INT, Map.of("x", NumericKind.INT.type(), "y", NumericKind.INT.type()), op(NumericKind.INT, operation, X, Y));
            var fixture = fixture(original, original, SafetyProfile.CHECKED_THROW);
            var emitted = emitter.emitChecked(fixture.request(), fixture.candidate(), names(original), Set.of(), 8);
            Method method = compile("int x, int y", emitted.statements() + returns(emitted.outputValues().values()), 8, int.class, int.class);
            arithmetic(method, "MATH_DIVIDE_BY_ZERO", 1, 0);
            if (operation == NumericOperation.DIVIDE) arithmetic(method, "MATH_OVERFLOW", Integer.MIN_VALUE, -1);
            else assertEquals(0, invoke(method, Integer.MIN_VALUE, -1)[0]);
        }
    }

    @Test
    void longChecksUseExactWideArithmetic() throws Exception {
        var original = plan(NumericKind.LONG, Map.of("x", NumericKind.LONG.type(), "y", NumericKind.LONG.type()),
                op(NumericKind.LONG, NumericOperation.MULTIPLY, X, Y));
        var fixture = fixture(original, original, SafetyProfile.CHECKED_THROW);
        var emitted = emitter.emitChecked(fixture.request(), fixture.candidate(), names(original), Set.of(), 8);
        Method method = compile("long x, long y", emitted.statements() + returns(emitted.outputValues().values()), 8, long.class, long.class);
        assertEquals(Long.MIN_VALUE, invoke(method, Long.MIN_VALUE, 1L)[0]);
        arithmetic(method, "MATH_OVERFLOW", Long.MIN_VALUE, -1L);
        arithmetic(method, "MATH_OVERFLOW", Long.MAX_VALUE, 2L);
    }

    @Test
    void originalOccurrenceOrderIncludesDeadAndRepeatedOperations() throws Exception {
        var original = plan(NumericKind.INT, Map.of("x", NumericKind.INT.type()), X);
        Expr divide = op(NumericKind.INT, NumericOperation.DIVIDE, X, JavaExpressions.literal(0));
        Expr overflow = op(NumericKind.INT, NumericOperation.ADD, X, JavaExpressions.literal(1));
        var trace = new SourceEvaluationTrace(List.of(
                new SourceEvaluationTrace.Occurrence("first-dead", divide, NumericKind.INT, NumericKind.INT),
                new SourceEvaluationTrace.Occurrence("second-dead", overflow, NumericKind.INT, NumericKind.INT),
                new SourceEvaluationTrace.Occurrence("third-duplicate", overflow, NumericKind.INT, NumericKind.INT)));
        var fixture = fixture(original, original, SafetyProfile.CHECKED_THROW, trace);
        var emitted = emitter.emitChecked(fixture.request(), fixture.candidate(), names(original), Set.of(), 8);
        assertEquals(2, emitted.statements().split("java.lang.Math.addExact", -1).length - 1);
        Method method = compile("int x", emitted.statements() + returns(emitted.outputValues().values()), 8, int.class);
        arithmetic(method, "MATH_DIVIDE_BY_ZERO", Integer.MAX_VALUE);
    }

    @Test
    void guardFallsBackWithoutCatchingOrSwallowingOriginalExceptions() throws Exception {
        Expr expression = op(NumericKind.INT, NumericOperation.DIVIDE,
                op(NumericKind.INT, NumericOperation.MULTIPLY, X, JavaExpressions.literal(2)), Y);
        var original = plan(NumericKind.INT, Map.of("x", NumericKind.INT.type(), "y", NumericKind.INT.type()), expression);
        var fixture = fixture(original, original, SafetyProfile.GUARDED_FALLBACK);
        var emitted = emitter.emitGuard(fixture.request(), fixture.candidate(), names(original), Set.of(), 8);
        assertFalse(emitted.statements().contains("catch"));
        String body = emitted.statements() + "if (" + emitted.guardJava() + ") {"
                + returns(emitted.replacement().outputValues().values()) + "} else {return new Object[]{(x*2)/y};}";
        Method method = compile("int x,int y", body, 8, int.class, int.class);
        assertEquals(17, invoke(method, 17, 2)[0]);
        assertEquals(-1, invoke(method, Integer.MAX_VALUE, 2)[0]);
        arithmetic(method, null, 1, 0);
    }

    @Test
    void checkedShiftUsesJavaMaskedDistancesAndRejectsLostSignificantBits() throws Exception {
        var original = plan(NumericKind.LONG, Map.of("x", NumericKind.LONG.type(), "y", NumericKind.INT.type()),
                op(NumericKind.LONG, NumericOperation.SHIFT_LEFT, X, Y));
        var fixture = fixture(original, original, SafetyProfile.CHECKED_THROW);
        var emitted = emitter.emitChecked(fixture.request(), fixture.candidate(), names(original), Set.of(), 8);
        Method method = compile("long x,int y", emitted.statements() + returns(emitted.outputValues().values()), 8, long.class, int.class);
        assertEquals(Long.MIN_VALUE, invoke(method, -1L, 63)[0]);
        assertEquals(3L, invoke(method, 3L, 64)[0]);
        arithmetic(method, "MATH_OVERFLOW", 1L, -1);
        arithmetic(method, "MATH_OVERFLOW", Long.MAX_VALUE, 1);
    }

    @Test
    void floatingPointRequiresJava17AndMatchesStrictRounding() throws Exception {
        Expr expression = op(NumericKind.DOUBLE, NumericOperation.SUBTRACT,
                op(NumericKind.DOUBLE, NumericOperation.ADD, X, JavaExpressions.literal(1.0)), X);
        var plan = plan(NumericKind.DOUBLE, Map.of("x", NumericKind.DOUBLE.type()), expression);
        assertThrows(UnsupportedOperationException.class, () -> plain(plan, 8));
        var emitted = plain(plan, 17);
        Method method = compile("double x", emitted.statements() + returns(emitted.outputValues().values()), 17, double.class);
        assertEquals(0.0, invoke(method, 1e16)[0]);
        assertEquals(1.0, invoke(method, 1.0)[0]);
    }

    @Test
    void invalidInputNamesAndJavaPackageShadowingAreRejected() {
        var plan = plan(NumericKind.INT, Map.of("x", NumericKind.INT.type()), X);
        for (String name : List.of("x; throw new Error()", "this", "", "x.y"))
            assertThrows(IllegalArgumentException.class, () -> emitter.emit(ComputationOptimizer.prepare(plan), Map.of("x", name), Set.of(), 8));
        assertThrows(UnsupportedOperationException.class, () -> emitter.emit(ComputationOptimizer.prepare(plan), names(plan), Set.of("java"), 8));
    }

    @Test
    void incompleteOriginalTraceCannotSilentlySkipAnOperation() {
        Expr expression = op(NumericKind.INT, NumericOperation.ADD,
                op(NumericKind.INT, NumericOperation.MULTIPLY, X, JavaExpressions.literal(2)), JavaExpressions.literal(1));
        var original = plan(NumericKind.INT, Map.of("x", NumericKind.INT.type()), expression);
        var trace = new SourceEvaluationTrace(List.of(new SourceEvaluationTrace.Occurrence("only-parent", expression, NumericKind.INT, NumericKind.INT)));
        var fixture = fixture(original, original, SafetyProfile.CHECKED_THROW, trace);
        var rejected = assertThrows(UnsupportedOperationException.class,
                () -> emitter.emitChecked(fixture.request(), fixture.candidate(), names(original), Set.of(), 8));
        assertEquals("MATH_MISSING_TRACE_OPERATION", rejected.getMessage());
    }

    private JavaComputationEmitter.Emission plain(JointComputationPlan plan, int target) {
        return emitter.emit(ComputationOptimizer.prepare(plan), names(plan), Set.of(), target);
    }

    private static JointComputationPlan plan(NumericKind kind, Map<String, Type> inputs, Expr expression) {
        return new JointComputationPlan(inputs, Map.of(), List.of(new Output("result", kind.type(), expression)));
    }

    private static Expr op(NumericKind kind, NumericOperation operation, Expr... operands) {
        return JavaExpressions.operation(kind, operation, operands);
    }

    private static Expr literal(Object value) {
        return switch (value) {
            case Byte number -> JavaExpressions.literal(number.byteValue());
            case Short number -> JavaExpressions.literal(number.shortValue());
            case Character number -> JavaExpressions.literal(number.charValue());
            case Integer number -> JavaExpressions.literal(number.intValue());
            case Long number -> JavaExpressions.literal(number.longValue());
            case Float number -> JavaExpressions.literal(number.floatValue());
            case Double number -> JavaExpressions.literal(number.doubleValue());
            default -> throw new AssertionError(value);
        };
    }

    private static Map<String, String> names(JointComputationPlan plan) {
        Map<String, String> names = new LinkedHashMap<>();
        plan.inputs().keySet().forEach(name -> names.put(name, name));
        return names;
    }

    private static Fixture fixture(JointComputationPlan original, JointComputationPlan replacement, SafetyProfile profile) {
        return fixture(original, replacement, profile, SourceEvaluationTrace.fromPlan(original));
    }

    /** Explicit test contract for emission mechanics; these markers are never accepted as optimizer proof. */
    private static Fixture fixture(JointComputationPlan original, JointComputationPlan replacement, SafetyProfile profile, SourceEvaluationTrace trace) {
        var policy = profile == SafetyProfile.CHECKED_THROW ? CheckedPolicy.EXPLICIT_DEFAULT : CheckedPolicy.NONE;
        var request = new OptimizationRequest(original, trace, EnumSet.allOf(NumericKind.class), "java25-numeric/v1", Set.of(),
                profile, OptimizationGoal.READABILITY, new OptimizationBudget(100_000, 2000, 64, 5000), policy);
        boolean floating = original.inputs().values().stream().map(NumericKind::fromType).anyMatch(NumericKind::floatingPoint)
                || trace.occurrences().stream().anyMatch(occurrence -> occurrence.evaluatedKind().floatingPoint());
        var obligations = new RuntimeObligations(profile == SafetyProfile.GUARDED_FALLBACK
                ? RuntimeObligations.GuardKind.ORIGINAL_AND_REPLACEMENT_RANGE : RuntimeObligations.GuardKind.NONE,
                trace, SourceEvaluationTrace.fromPlan(replacement), !floating, floating, floating, 1, trace.occurrences().size());
        var evidence = new VerificationEvidence("test-only", "java25-numeric/v1", "emitter-fixture", "emitter-fixture",
                "test-only-source", "test-only-candidate", "test-only-trace", "test-only-assumptions",
                profile, policy, obligations, List.of("EMISSION_MECHANICS_ONLY"), 0);
        var prepared = ComputationOptimizer.prepare(replacement);
        var cost = new CostAssessment(1, 1, 1, trace.occurrences().size(), ComputationOptimizer.prepare(original).cost(), prepared.cost(), false);
        var candidate = new Candidate(replacement, prepared, evidence, obligations, cost, SearchCompletion.IMPROVEMENT_FOUND, 0);
        return new Fixture(request, candidate);
    }

    private record Fixture(OptimizationRequest request, Candidate candidate) {}

    private Method compile(String parameters, String body, int target, Class<?>... parameterTypes) throws Exception {
        Path output = Files.createTempDirectory(temporary, "compiled-");
        Path file = output.resolve("Calculation.java");
        String source = "public class Calculation { public static Object[] compute(" + parameters + ") {" + body + "}}";
        Files.writeString(file, source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", Integer.toString(target),
                "-Xlint:-options", "-d", output.toString(), file.toString()), source);
        try (var loader = new URLClassLoader(new URL[] { output.toUri().toURL() }, null)) {
            return loader.loadClass("Calculation").getMethod("compute", parameterTypes);
        }
    }

    private static String returns(java.util.Collection<String> values) {
        return "return new Object[]{" + String.join(",", values) + "};";
    }

    private static Object[] invoke(Method method, Object... arguments) throws Exception {
        return (Object[]) method.invoke(null, arguments);
    }

    private static void arithmetic(Method method, String diagnostic, Object... arguments) {
        var invocation = assertThrows(InvocationTargetException.class, () -> invoke(method, arguments));
        var exception = assertInstanceOf(ArithmeticException.class, invocation.getCause());
        if (diagnostic != null) assertEquals(diagnostic, exception.getMessage());
    }
}
