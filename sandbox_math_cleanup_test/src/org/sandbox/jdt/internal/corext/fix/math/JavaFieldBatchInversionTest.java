/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.ObjectMapper;

class JavaFieldBatchInversionTest {
    private static final String VERIFIED = "VERIFIED_FIELD_BATCH_INVERSIONS";
    private static final BigInteger PRIME = BigInteger.ONE.shiftLeft(521).subtract(BigInteger.ONE);
    private static final BigInteger COMPOSITE = BigInteger.ONE.shiftLeft(380);
    private static final String SAMPLE = """
            import java.math.BigInteger;
            public class Sample {
                public interface Output { BigInteger[] point(BigInteger x, BigInteger y); }
                private final BigInteger p;
                public Sample(BigInteger p) { this.p = p; }
                public BigInteger[] coordinates(BigInteger xNum, BigInteger xDen, BigInteger yPrime,
                        BigInteger yNum, BigInteger yDen, Output output) {
                    BigInteger x = xNum.multiply(xDen.modInverse(p)).mod(p);
                    BigInteger y = yPrime.multiply(yNum).mod(this.p)
                        .multiply(yDen.modInverse(p)).mod(p);
                    return output.point(x, y);
                }
            }
            """;

    @TempDir Path temporary;

    @Test void verifiesAnIndependentPairAndPreservesTheNumericalResults() throws Exception {
        var options = options(true, true, "BIG_INTEGER");
        var analysis = analyze(SAMPLE, options, -1);
        assertBatchCount(1, analysis);
        var region = analysis.evidence().stream().filter(e -> e.fieldBatchInversion() != null)
                .findFirst().orElseThrow();
        assertNotNull(region.sourcePlan());
        assertEquals(MathematicalAnalysis.digest(SAMPLE), region.sourcePlan().sourceSha256());
        assertEquals(SAMPLE.substring(region.offset(), region.offset() + region.length()), region.original());
        assertTrue(analysis.matches(SAMPLE, compiler()));
        assertFalse(analysis.matches(SAMPLE.replace("yDen.modInverse(p)", "yNum.modInverse(p)"), compiler()));
        assertEquals(3, occurrences(region.replacement(), ".modInverse("));
        assertTrue(MathReviewReport.ranked(analysis.evidence(), options).stream()
                .flatMap(e -> e.assessment().cautions().stream()).anyMatch(reason -> reason.contains("reference identity")));
        String rewritten = apply(SAMPLE, analysis);
        assertTrue(rewritten.contains("Modular inversion batching preserves numerical BigInteger values"));
        assertBatchCount(0, analyze(rewritten, options, -1));
        try (var before = compile("before", SAMPLE); var after = compile("after", rewritten)) {
            for (var p : List.of(PRIME, COMPOSITE, BigInteger.valueOf(17), PRIME.shiftLeft(4096))) {
                for (long a : List.of(-19L, -1L, 1L, 3L, 73L)) {
                    var operands = new BigInteger[] {value(7), value(a), value(-31), value(11), value(3)};
                    assertArrayEquals(run(before, p, operands), run(after, p, operands));
                }
            }
        }
    }

    @Test void serializesTheCheckedSourceAndSchedule() {
        var analysis = analyze(SAMPLE, options(true, true, "BIG_INTEGER"), -1);
        assertBatchCount(1, analysis);
        var region = analysis.evidence().stream().filter(e -> e.fieldBatchInversion() != null).findFirst().orElseThrow();
        var json = new ObjectMapper().valueToTree(region).path("fieldBatchInversion");
        assertEquals(2, json.path("source").path("operands").asInt());
        assertEquals(4, json.path("plan").path("steps").size());
        assertEquals(2, json.path("plan").path("outputs").size());
        assertEquals(2, json.path("originalInversions").asInt());
        assertEquals(1, json.path("plannedInversions").asInt());
        assertEquals(3, json.path("plannedMultiplications").asInt());
    }

    @Test void speculativeFailureStillThrowsTheOriginalArithmeticException() throws Exception {
        var analysis = analyze(SAMPLE, options(true, true, "BIG_INTEGER"), -1);
        assertBatchCount(1, analysis);
        try (var before = compile("before", SAMPLE); var after = compile("after", apply(SAMPLE, analysis))) {
            for (var p : Arrays.asList(COMPOSITE, PRIME.negate(), BigInteger.ZERO, null)) {
                for (var denominators : List.of(List.of(value(2), value(3)), List.of(value(3), value(2)),
                        List.of(BigInteger.ZERO, BigInteger.ZERO))) {
                    var operands = new BigInteger[] {value(7), denominators.get(0), value(31), value(11), denominators.get(1)};
                    Throwable expected = assertThrows(InvocationTargetException.class, () -> run(before, p, operands)).getCause();
                    Throwable actual = assertThrows(InvocationTargetException.class, () -> run(after, p, operands)).getCause();
                    assertEquals(expected.getClass(), actual.getClass());
                    if (!(expected instanceof NullPointerException)) assertEquals(expected.getMessage(), actual.getMessage());
                }
            }
        }
    }

    @Test void subclassesKeepOriginalEvaluationOrderAndDispatch() throws Exception {
        var analysis = analyze(SAMPLE, options(true, true, "BIG_INTEGER"), -1);
        assertBatchCount(1, analysis);
        try (var before = compile("before", SAMPLE); var after = compile("after", apply(SAMPLE, analysis))) {
            var events = new ArrayList<String>();
            var operands = new BigInteger[] {new ObservedInteger(7, "xNum", events), new ObservedInteger(5, "xDen", events),
                    new ObservedInteger(31, "yPrime", events), value(11), new ObservedInteger(9, "yDen", events)};
            var expected = run(before, PRIME, operands);
            assertEquals(List.of("xDen.inverse", "xNum.multiply", "yPrime.multiply", "yDen.inverse"), events);
            events.clear();
            assertArrayEquals(expected, run(after, PRIME, operands));
            assertEquals(List.of("xDen.inverse", "xNum.multiply", "yPrime.multiply", "yDen.inverse"), events);

            // A subclass in the second initializer alone must also disable speculation.
            events.clear();
            operands[0] = value(7);
            operands[1] = value(2);
            var failure = assertThrows(InvocationTargetException.class, () -> run(after, COMPOSITE, operands));
            assertTrue(failure.getCause() instanceof ArithmeticException);
            assertTrue(events.isEmpty(), events::toString);
        }
    }

    @Test void rejectsDependentUnstableAndUnconsentedPairs() {
        var options = options(true, true, "BIG_INTEGER");
        assertBatchCount(0, analyze(SAMPLE, options(true, false, "BIG_INTEGER"), -1));
        assertBatchCount(0, analyze(SAMPLE, options(false, true, "BIG_INTEGER"), -1));
        assertBatchCount(0, analyze(SAMPLE, options(true, true, "INT"), -1));
        assertBatchCount(0, analyze(SAMPLE, options, SAMPLE.indexOf("output.point")));
        assertBatchCount(1, analyze(SAMPLE, options, SAMPLE.indexOf("yDen.modInverse")));
        for (var source : List.of(
                SAMPLE.replace("yDen.modInverse(p)", "x.modInverse(p)"),
                SAMPLE.replace("yPrime.multiply(yNum)", "yPrime.multiply(x)"),
                SAMPLE.replace("yDen.modInverse(p)", "xDen.modInverse(p)"),
                SAMPLE.replace("yDen.modInverse(p)", "yDen.modInverse(xNum)"),
                SAMPLE.replace("xDen.modInverse(p)", "xDen.abs().modInverse(p)"),
                SAMPLE.replace("return output.point(x, y);", "xDen = yDen; return output.point(x, y);"),
                SAMPLE.replace("BigInteger y =", "/* Do not move this comment. */ BigInteger y ="))) {
            assertBatchCount(0, analyze(source, options, -1));
        }
    }

    @Test void replaysBothProofsAfterCombiningEdits() throws Exception {
        String source = SAMPLE.substring(0, SAMPLE.lastIndexOf('}')) + """
                public static BigInteger[] inverses(BigInteger a, BigInteger b, BigInteger modulus) {
                    final var first = a.modInverse(modulus);
                    final var second = b.modInverse(modulus);
                    return new BigInteger[] {first, second};
                }
            }
            """;
        var analysis = analyze(source, options(true, true, "BIG_INTEGER"), -1);
        assertBatchCount(2, analysis);
        try (var rewritten = compile("combined", apply(source, analysis))) {
            var actual = (BigInteger[]) rewritten.loadClass("Sample")
                    .getMethod("inverses", BigInteger.class, BigInteger.class, BigInteger.class)
                    .invoke(null, value(5), value(-7), PRIME);
            assertArrayEquals(new BigInteger[] {value(5).modInverse(PRIME), value(-7).modInverse(PRIME)}, actual);
        }
    }

    @Test void rejectsAnInstancePublishedBeforeItsFinalModulusIsAssigned() {
        String source = SAMPLE.replace("private final BigInteger p;",
                "private static volatile Sample published; private final BigInteger p;")
                .replace("this.p = p;", "published = this; this.p = p;");
        assertBatchCount(0, analyze(source, options(true, true, "BIG_INTEGER"), -1));
    }

    @Test void rejectsFinalInstanceFieldsThatCanBeRestoredByDeserialization() {
        String source = SAMPLE.replace("public class Sample {",
                "public class Sample implements java.io.Serializable {");
        assertBatchCount(0, analyze(source, options(true, true, "BIG_INTEGER"), -1));
    }

    private static MathematicalAnalysis.Analysis analyze(String source, MathCleanUpOptions options, int selection) {
        var compiler = compiler();
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setUnitName("Sample.java");
        parser.setSource(source.toCharArray());
        parser.setResolveBindings(true);
        parser.setCompilerOptions(compiler);
        var ast = (CompilationUnit) parser.createAST(null);
        assertTrue(Arrays.stream(ast.getProblems()).noneMatch(p -> p.isError()),
                () -> Arrays.toString(ast.getProblems()));
        return MathematicalAnalysis.analyze(ast, source, options, null, selection, 0,
                new MathematicalAnalysis.SourceEnvironment("Sample.java", List.of(), List.of(), List.of(), compiler));
    }

    private static Map<String, String> compiler() {
        var compiler = new HashMap<String, String>();
        JavaCore.setComplianceOptions("17", compiler);
        return compiler;
    }

    private static MathCleanUpOptions options(boolean batch, boolean mathematical, String kinds) {
        return MathCleanUpOptions.parse(Map.of(MathCleanUpOptions.CLEANUP, "true",
                MathCleanUpOptions.FIELD_INVERSIONS, Boolean.toString(batch),
                MathCleanUpOptions.MATHEMATICAL_OPT_IN, Boolean.toString(mathematical),
                MathCleanUpOptions.KINDS, kinds), 17);
    }

    private static void assertBatchCount(long count, MathematicalAnalysis.Analysis analysis) {
        assertEquals(count, analysis.diagnostics().stream().filter(d -> VERIFIED.equals(d.code())).count(),
                () -> analysis.diagnostics().toString());
        if (count != 0) assertTrue(analysis.changed(), () -> analysis.diagnostics().toString());
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int index = source.indexOf(needle); index >= 0; index = source.indexOf(needle, index + needle.length())) count++;
        return count;
    }

    private static String apply(String source, MathematicalAnalysis.Analysis analysis) throws Exception {
        var document = new Document(source);
        analysis.newEdit().apply(document);
        return document.get();
    }

    private URLClassLoader compile(String directory, String source) throws Exception {
        Path work = Files.createDirectory(temporary.resolve(directory));
        Path sample = Files.writeString(work.resolve("Sample.java"), source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-d",
                work.toString(), sample.toString()));
        return new URLClassLoader(new URL[] {work.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    private static BigInteger[] run(ClassLoader loader, BigInteger modulus, BigInteger[] operands) throws Exception {
        var type = loader.loadClass("Sample");
        var output = loader.loadClass("Sample$Output");
        Object sink = java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[] {output}, (proxy, method, args) ->
                new BigInteger[] {(BigInteger) args[0], (BigInteger) args[1]});
        return (BigInteger[]) type.getMethod("coordinates", BigInteger.class, BigInteger.class, BigInteger.class,
                BigInteger.class, BigInteger.class, output).invoke(type.getConstructor(BigInteger.class).newInstance(modulus),
                        operands[0], operands[1], operands[2], operands[3], operands[4], sink);
    }

    private static BigInteger value(long value) { return BigInteger.valueOf(value); }

    private static final class ObservedInteger extends BigInteger {
        private static final long serialVersionUID = 1L;
        private final String name;
        private final List<String> events;

        ObservedInteger(long value, String name, List<String> events) {
            super(Long.toString(value));
            this.name = name;
            this.events = events;
        }

        @Override public BigInteger modInverse(BigInteger modulus) {
            events.add(name + ".inverse");
            return super.modInverse(modulus);
        }

        @Override public BigInteger multiply(BigInteger other) {
            events.add(name + ".multiply");
            return super.multiply(other);
        }
    }
}
