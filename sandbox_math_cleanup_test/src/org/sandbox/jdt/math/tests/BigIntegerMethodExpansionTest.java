/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.sdk.optimization.ComputationOptimizer;
import de.regelsuche.sdk.optimization.JavaExpressions;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.NumericOperation;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationRegion;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** Actual helper bodies supply the original graph; the SDK chooses any replacement. */
class BigIntegerMethodExpansionTest {
    private static final String CALLER = "public static BigInteger compute";
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(strings = { "BigInteger", "var", "parameter" })
    void overwrittenDeclarationEscapesBothGuardBranchesAndFallbackRetainsOriginalBehavior(String type) throws Exception {
        String local = type.equals("parameter") ? "x" : "result";
        String declaration = type.equals("parameter") ? "" : type + " ";
        String original = source("static BigInteger observed; static void consume(BigInteger value) { observed=value; }",
                declaration + local + "=x.modPow(BigInteger.TWO,m);\n"
                + local + "=" + local + ".multiply(" + local + ").mod(m);\nconsume(" + local + ");\nreturn observed;");
        var analysis = analyzeCaller(original, options(true));
        if (!type.equals("parameter")) {
            assertTrue(analysis.evidence().stream().anyMatch(e -> e.original().contains("modPow")
                    && e.original().contains(local + ".multiply(" + local + ")")), analysis.diagnostics().toString());
        }
        String generated = qualify(original, true);
        Method before = compile(original), after = compile(generated);
        BigInteger overridden = new BigInteger("3") {
            private static final long serialVersionUID = 1L;
            @Override public BigInteger modPow(BigInteger exponent, BigInteger modulus) { return BigInteger.valueOf(73); }
        };
        for (BigInteger x : new BigInteger[] {null, overridden, BigInteger.ZERO, BigInteger.valueOf(-19),
                BigInteger.ONE.shiftLeft(5000)}) {
            for (BigInteger m : new BigInteger[] {null, BigInteger.ZERO, BigInteger.valueOf(-101), BigInteger.valueOf(101)}) {
                assertEquals(outcome(before, x, m), outcome(after, x, m));
            }
        }
    }

    @Test void modularHelperUsesCallerGuardsAndKeepsTheOriginalCallAsFallback() throws Exception {
        String helpers = """
            private static BigInteger power(BigInteger value, BigInteger modulus) {
                /* This documentation belongs to the helper. */
                return value.modPow(BigInteger.TWO, modulus);
            }
            """;
        String original = source(helpers, "return power(x, m);");
        String generated = qualify(original, true);
        assertTrue(generated.contains(helpers), generated);
        String caller = generated.substring(generated.indexOf(CALLER));
        assertTrue(caller.contains("x.multiply(x).mod(m)"), caller);
        assertTrue(caller.contains("return power(x, m);"), caller);
        assertTrue(caller.contains("x.getClass()"), caller);
        assertTrue(caller.contains("m.getClass()"), caller);
        assertFalse(caller.contains("value.getClass()"), caller);
        assertFalse(caller.contains("modulus.getClass()"), caller);

        BigInteger overridden = new BigInteger("3") {
            private static final long serialVersionUID = 1L;
            @Override public BigInteger modPow(BigInteger exponent, BigInteger modulus) {
                return BigInteger.valueOf(73);
            }
        };
        Method before = compile(original), after = compile(generated);
        assertEquals(BigInteger.valueOf(73), after.invoke(null, overridden, BigInteger.valueOf(101)));
        for (BigInteger x : new BigInteger[] {null, overridden, BigInteger.ZERO, BigInteger.ONE,
                BigInteger.valueOf(-19), BigInteger.ONE.shiftLeft(5000)}) {
            for (BigInteger m : new BigInteger[] {null, BigInteger.ZERO, BigInteger.valueOf(-101), BigInteger.valueOf(101)}) {
                assertEquals(outcome(before, x, m), outcome(after, x, m));
            }
        }
    }

    @Test void generalAlgebraCanCrossTheHelperBoundary() throws Exception {
        String original = source("""
            private static BigInteger combine(BigInteger first, BigInteger second) {
                return first.add(second).subtract(second);
            }
            """, "return combine(x, m).add(x).subtract(x);");
        String generated = qualify(original, true);
        assertTrue(generated.contains("return x;"), generated);
    }

    @Test void nestedRepeatedCallsAndLocalAliasesRetainTheirOwnValues() throws Exception {
        String original = source("""
            private static BigInteger sum(BigInteger p, BigInteger q) { return p.add(q); }
            private static BigInteger difference(BigInteger p, BigInteger q) {
                BigInteger old = p;
                p = sum(p, q);
                BigInteger result = p.multiply(old);
                result = result.subtract(q);
                return result;
            }
            """, "return difference(x, m).subtract(difference(m, x));");
        checkDecoded(original);
    }

    @Test void overloadsAndPrimitiveArgumentsUseResolvedBindings() throws Exception {
        String original = source("""
            private static BigInteger blend(BigInteger p, BigInteger q) { return p.subtract(q); }
            private static BigInteger blend(BigInteger p, int distance) { return p.shiftLeft(distance); }
            private static BigInteger blend(BigInteger p, long ignored) { return p.negate(); }
            """, "return blend(blend(x, 2), m).add(blend(m, 2L));");
        checkDecoded(original);
    }

    @Test void bigIntegerOnlySelectionSupportsPureBigIntegerHelpers() {
        String original = source("private static BigInteger fn(BigInteger p, BigInteger q) { return p.modPow(BigInteger.TWO, q); }",
                "return fn(x, m);");
        var options = options(true, Set.of(NumericKind.BIG_INTEGER));
        var result = analyzeCaller(original, options);
        assertTrue(result.changed(), result.diagnostics().toString());
    }

    @Test void everyUnusedArgumentPrecedesTheUnusedCalleeOperations() throws Exception {
        String original = source("""
            private static BigInteger keep(BigInteger unused, BigInteger p) {
                BigInteger dead = p.divide(BigInteger.ZERO);
                return p;
            }
            """, "return keep(x.divide(m), x).add(m).subtract(m);");
        var region = extractCaller(original);
        var divisions = region.trace().occurrences().stream()
                .filter(o -> JavaExpressions.operationOf(o.expression()).orElse(null) == NumericOperation.DIVIDE).toList();
        assertEquals(2, divisions.size());
        assertTrue(divisions.getFirst().sourceId().startsWith("operation@" + original.indexOf("x.divide(m)") + ":"));
        assertTrue(divisions.getLast().sourceId().startsWith("operation@" + original.indexOf("p.divide(BigInteger.ZERO)") + ":"));
        qualify(original, false);
    }

    @Test void strictModeDoesNotOptIntoReferenceValuedHelperExpansion() {
        String original = source("private static BigInteger fn(BigInteger p, BigInteger q) { return p.add(q).subtract(q); }",
                "return fn(x, m).add(x).subtract(x);");
        assertFalse(analyzeCaller(original, options(false)).changed());
    }

    @Test void helperStateAndControlFlowStillRejectTheWholeCallerRegion() {
        for (String helpers : List.of(
                "private static BigInteger state; private static BigInteger fn(BigInteger p, BigInteger q) { return state.add(p); }",
                "private static final BigInteger VALUE = BigInteger.TWO; private static BigInteger fn(BigInteger p, BigInteger q) { return p.modPow(VALUE, q); }",
                "private static synchronized BigInteger fn(BigInteger p, BigInteger q) { return p.add(q); }",
                "private static native BigInteger fn(BigInteger p, BigInteger q);",
                "private static BigInteger fn(BigInteger p, BigInteger q) { if (p.signum() == 0) return q; return p.add(q); }",
                "private static BigInteger fn(BigInteger p, BigInteger q) { BigInteger[] values = {p}; return values[0].add(q); }",
                "private static BigInteger fn(BigInteger p, BigInteger q) { return fn(p, q); }",
                "private static BigInteger fn(BigInteger p, BigInteger q) { return other(p, q); } private static BigInteger other(BigInteger p, BigInteger q) { return fn(p, q); }")) {
            assertRejected(source(helpers, "return fn(x, m).add(x).subtract(x);"));
        }
    }

    @Test void effectsAndOtherClassInitializationCannotDisappear() {
        assertRejected(source("""
            private static Calculation receiver() { throw new IllegalStateException(); }
            private static BigInteger fn(BigInteger p, BigInteger q) { return p.add(q); }
            """, "return receiver().fn(x, m).subtract(m);"));
        assertRejected(source("""
            private static BigInteger fn(BigInteger p, BigInteger q) { return p.add(q); }
            """, "return fn(x = m, m).subtract(m);"));
        assertRejected(source("""
            private static class Other {
                static { System.setProperty("math-helper-initialized", "true"); }
                private static BigInteger fn(BigInteger p, BigInteger q) { return p.add(q); }
            }
            """, "return Other.fn(x, m).subtract(m);"));
    }

    @Test void helperConstantCalculationsRetainTheExistingSourcePolicy() {
        assertRejected(source("""
            private static BigInteger fn(BigInteger p, BigInteger q) {
                return p.multiply(BigInteger.valueOf(5 * 7 * 11));
            }
            """, "return fn(x, m).add(x).subtract(x);"));
    }

    @Test void primitiveConstantFieldsCannotEnterThroughHelperFactories() {
        for (String argument : List.of("EXP", "Calculation.EXP", "+EXP")) {
            assertRejected(source("private static final long EXP = 2L; "
                    + "private static BigInteger fn(BigInteger p, BigInteger q) { "
                    + "return p.modPow(BigInteger.valueOf(" + argument + "), q); }",
                    "return fn(x, m);"));
        }
    }

    @Test void nestedConstantCalculationsCannotEnterThroughHelperFactories() {
        for (String argument : List.of("+(1 * 2)", "-(-1 - 1)", "~(-3)", "+(int)(1 * 2)")) {
            assertRejected(source("private static BigInteger fn(BigInteger p, BigInteger q) { "
                    + "return p.modPow(BigInteger.valueOf(" + argument + "), q); }",
                    "return fn(x, m);"));
        }
    }

    @Test void literalFactoriesKeepTheirSupportedSignedForms() throws Exception {
        qualify(source("private static BigInteger fn(BigInteger p, BigInteger q) { "
                + "return p.modPow(BigInteger.valueOf(+2), q); }", "return fn(x, m);"), true);
        checkDecoded(source("private static BigInteger fn(BigInteger p, BigInteger q) { "
                + "return p.multiply(BigInteger.valueOf(-2)).add(q); }", "return fn(x, m);"));
    }

    @Test void deepAcyclicBigIntegerCallsStayBounded() {
        StringBuilder helpers = new StringBuilder();
        for (int i = 0; i < 24; i++) {
            helpers.append("private static BigInteger step").append(i)
                    .append("(BigInteger p, BigInteger q) { return ")
                    .append(i == 23 ? "p.add(q)" : "step" + (i + 1) + "(p, q)").append("; }\n");
        }
        String original = source(helpers.toString(), "return step0(x, m).subtract(m);");
        var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(original), original, options(true));
        assertTrue(extraction.regions().stream().noneMatch(r -> r.start() > original.indexOf(CALLER)));
        assertTrue(extraction.diagnostics().stream().anyMatch(d -> d.offset() > original.indexOf(CALLER)
                && (d.code().contains("DEPTH") || d.code().contains("LIMIT"))), extraction.diagnostics().toString());
    }

    private void checkDecoded(String original) throws Exception {
        var region = extractCaller(original);
        assertEquals(Set.of("x", "m"), Set.copyOf(region.inputNames().values()));
        var prepared = ComputationOptimizer.prepare(region.plan());
        Method method = compile(original);
        for (long x : new long[] {-123, -1, 0, 1, 17, 103}) {
            for (long m : new long[] {-101, -1, 0, 1, 19, 127}) {
                Map<String, BigInteger> values = Map.of("x", BigInteger.valueOf(x), "m", BigInteger.valueOf(m));
                Map<String, Object> inputs = new HashMap<>();
                region.inputNames().forEach((id, name) -> inputs.put(id, values.get(name)));
                assertEquals(method.invoke(null, values.get("x"), values.get("m")),
                        prepared.execute(inputs).get(region.plan().outputs().getFirst().name()), original);
            }
        }
    }

    private static JavaComputationRegion extractCaller(String original) {
        var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(original), original, options(true));
        var regions = extraction.regions().stream().filter(r -> r.start() > original.indexOf(CALLER)).toList();
        assertEquals(1, regions.size(), extraction.diagnostics().toString());
        return regions.getFirst();
    }

    private static void assertRejected(String original) {
        var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(original), original, options(true));
        assertTrue(extraction.regions().stream().noneMatch(r -> r.start() > original.indexOf(CALLER)), original);
        assertTrue(extraction.diagnostics().stream().anyMatch(d -> d.offset() > original.indexOf(CALLER)), original);
        assertFalse(analyzeCaller(original, options(true)).changed(), original);
    }

    private String qualify(String original, boolean requireImprovement) throws Exception {
        var result = analyzeCaller(original, options(true));
        if (requireImprovement) {
            assertTrue(result.changed(), result.diagnostics().toString());
            assertFalse(result.evidence().isEmpty());
        }
        Document document = new Document(original);
        var undo = result.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        MathTestSupport.parse(generated);
        undo.apply(document);
        assertEquals(original, document.get());
        assertFalse(analyzeCaller(generated, options(true)).changed(), generated);
        Method before = compile(original), after = compile(generated);
        for (long x : new long[] {-101, -1, 0, 1, 101}) {
            for (long m : new long[] {-19, -1, 0, 1, 19}) {
                assertEquals(outcome(before, BigInteger.valueOf(x), BigInteger.valueOf(m)),
                        outcome(after, BigInteger.valueOf(x), BigInteger.valueOf(m)), generated);
            }
        }
        return generated;
    }

    private Method compile(String source) throws Exception {
        Path output = Files.createTempDirectory(temporary, "compiled-");
        Path file = output.resolve("Calculation.java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-d", output.toString(), file.toString()), source);
        try (var loader = new URLClassLoader(new java.net.URL[] {output.toUri().toURL()}, null)) {
            return loader.loadClass("Calculation").getMethod("compute", BigInteger.class, BigInteger.class);
        }
    }

    private static Object outcome(Method method, BigInteger x, BigInteger m) throws Exception {
        try {
            return method.invoke(null, x, m);
        } catch (InvocationTargetException failure) {
            return List.of(failure.getCause().getClass(), String.valueOf(failure.getCause().getMessage()));
        }
    }

    private static String source(String helpers, String body) {
        return "import java.math.BigInteger; public class Calculation {\n" + helpers + "\n"
                + CALLER + "(BigInteger x, BigInteger m) {\n" + body + "\n}}";
    }

    private static MathCleanUpOptions options(boolean mathematical) {
        return options(mathematical, EnumSet.allOf(NumericKind.class));
    }

    private static MathCleanUpOptions options(boolean mathematical, Set<NumericKind> kinds) {
        return new MathCleanUpOptions(true, kinds, SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000L, 20_000, false, 17, List.of(), mathematical);
    }

    private static MathematicalAnalysis.Analysis analyzeCaller(String source, MathCleanUpOptions options) {
        int start = source.indexOf(CALLER);
        return MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options,
                new NullProgressMonitor(), start, source.length() - start);
    }
}
