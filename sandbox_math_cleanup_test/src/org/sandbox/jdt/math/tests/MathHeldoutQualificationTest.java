/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.SplittableRandom;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Frozen before first optimizer execution; synthetic controls, not an unseen upstream corpus. */
@SuppressWarnings("nls")
class MathHeldoutQualificationTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"stencil-int-original", "stencil-int-renamed", "stencil-int-reduced",
            "stencil-double-negative", "bernstein-bigint-original", "bernstein-bigint-renamed",
            "bernstein-bigint-reduced", "adder-long-original", "adder-long-reduced"})
    void frozenComputationMatchesIndependentOracle(String id) throws Exception {
        String source = resource(id + "/Calculation.java.txt");
        String sourceHash = hash(source);
        assertTrue(resource("selection-receipt.txt").contains(sourceHash + "  " + id + "/Calculation.java"),
                "The pre-execution selection must remain immutable");
        NumericKind kind = id.contains("bigint") ? NumericKind.BIG_INTEGER : id.contains("long") ? NumericKind.LONG
                : id.contains("double") ? NumericKind.DOUBLE : NumericKind.INT;
        var options = new MathCleanUpOptions(true, Set.of(kind), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of());
        var analysis = MathematicalAnalysis.analyze(MathTestSupport.parse(source, 17), source, options,
                new NullProgressMonitor(), -1, 0);
        assertEquals(analysis.replacements().size(), analysis.evidence().size());
        for (var evidence : analysis.evidence()) {
            assertNotNull(evidence.proof());
            assertTrue(evidence.cost().estimatedRuntimeImprovement());
        }
        Document document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        undo.apply(document);
        assertEquals(source, document.get());
        Method before = compile(source, kind), after = compile(generated, kind);
        List<Object[]> cases = cases(kind);
        for (Object[] inputs : cases) {
            Object expected = oracle(kind, inputs);
            same(kind, expected, before.invoke(null, inputs));
            same(kind, expected, after.invoke(null, inputs));
        }
        if (kind == NumericKind.DOUBLE) {
            Object[] witness = {Math.scalb(1.0, 27), 1.0};
            double[] expected = (double[]) oracle(kind, witness);
            assertEquals(0L, Double.doubleToLongBits(expected[1]));
            double[] wrongPolynomial = {4.0 * (double) witness[0] * (double) witness[1], 2.0};
            assertThrows(AssertionError.class, () -> same(kind, expected, wrongPolynomial));
        }
        var second = MathematicalAnalysis.analyze(MathTestSupport.parse(generated, 17), generated, options,
                new NullProgressMonitor(), -1, 0);
        assertFalse(second.changed(), "Idempotence: " + second.diagnostics());
        System.out.println("HELDOUT " + id + " changed=" + analysis.changed() + " oracleCases=" + cases.size());
        String directory = System.getProperty("math.qa.heldoutReportDirectory",
                System.getProperty("sandbox.math.corpus.evidence"));
        if (directory != null) {
            Path output = Files.createDirectories(Path.of(directory).resolve("heldout").resolve(id));
            Files.writeString(output.resolve("original.java"), source, StandardCharsets.UTF_8);
            Files.writeString(output.resolve("generated.java"), generated, StandardCharsets.UTF_8);
            Properties report = new Properties();
            report.setProperty("provenance", "Fresh synthetic heldout selected before execution; not upstream");
            report.setProperty("sourceSha256", sourceHash);
            report.setProperty("generatedSha256", hash(generated));
            report.setProperty("changed", Boolean.toString(analysis.changed()));
            report.setProperty("oracleCases", Integer.toString(cases.size()));
            report.setProperty("diagnostics", analysis.diagnostics().toString());
            report.setProperty("evidence", analysis.evidence().toString());
            report.setProperty("exactUndo", "true");
            report.setProperty("secondPassChanged", "false");
            try (var writer = Files.newBufferedWriter(output.resolve("result.properties"), StandardCharsets.UTF_8)) {
                report.store(writer, "All selected outcomes retained, including unchanged cases");
            }
        }
    }

    private static String resource(String name) throws Exception {
        try (var stream = MathHeldoutQualificationTest.class.getResourceAsStream("heldout/" + name)) {
            assertNotNull(stream, name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Method compile(String source, NumericKind kind) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "heldout-");
        Path file = directory.resolve("Calculation.java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-proc:none",
                "-classpath", directory.toString(), "-d", directory.toString(), file.toString()));
        Class<?>[] signature = kind == NumericKind.BIG_INTEGER ? new Class<?>[] {long.class, long.class, long.class, long.class}
                : kind == NumericKind.INT ? new Class<?>[] {int.class, int.class}
                : kind == NumericKind.LONG ? new Class<?>[] {long.class, long.class} : new Class<?>[] {double.class, double.class};
        try (var loader = new URLClassLoader(new java.net.URL[] {directory.toUri().toURL()}, null)) {
            return loader.loadClass("Calculation").getMethod("compute", signature);
        }
    }

    private static List<Object[]> cases(NumericKind kind) {
        List<Object[]> result = new ArrayList<>();
        SplittableRandom random = new SplittableRandom(1664);
        if (kind == NumericKind.BIG_INTEGER) {
            long[] edges = {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE};
            for (long a : edges) for (long b : edges) for (long c : edges) for (long t : edges) result.add(new Object[] {a, b, c, t});
            for (int i = 0; i < 256; i++) result.add(new Object[] {random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()});
        } else if (kind == NumericKind.INT) {
            int[] edges = {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 1 << 30, 0x55555555, 0xaaaaaaaa};
            for (int a : edges) for (int b : edges) result.add(new Object[] {a, b});
            for (int i = 0; i < 256; i++) result.add(new Object[] {random.nextInt(), random.nextInt()});
        } else if (kind == NumericKind.LONG) {
            long[] edges = {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, 1L << 62, 0x5555555555555555L, 0xaaaaaaaaaaaaaaaaL};
            for (long a : edges) for (long b : edges) result.add(new Object[] {a, b});
            for (int i = 0; i < 256; i++) result.add(new Object[] {random.nextLong(), random.nextLong()});
        } else {
            double[] edges = {0.0, -0.0, 1.0, -1.0, Math.scalb(1.0, 27), -Math.scalb(1.0, 27),
                    Double.MIN_VALUE, Double.MIN_NORMAL, Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN};
            for (double a : edges) for (double b : edges) result.add(new Object[] {a, b});
        }
        return result;
    }

    private static Object oracle(NumericKind kind, Object[] inputs) {
        if (kind == NumericKind.INT) {
            BigInteger center = BigInteger.valueOf((int) inputs[0]), step = BigInteger.valueOf((int) inputs[1]);
            return new int[] {center.multiply(step).shiftLeft(2).intValue(), step.pow(2).shiftLeft(1).intValue()};
        } else if (kind == NumericKind.BIG_INTEGER) {
            BigInteger a = BigInteger.valueOf((long) inputs[0]), b = BigInteger.valueOf((long) inputs[1]),
                    c = BigInteger.valueOf((long) inputs[2]), t = BigInteger.valueOf((long) inputs[3]);
            BigInteger quadratic = a.subtract(b.shiftLeft(1)).add(c), linear = b.shiftLeft(1).subtract(a.shiftLeft(1));
            return new String[] {a.add(linear.multiply(t)).add(quadratic.multiply(t.pow(2))).toString(),
                    linear.add(quadratic.multiply(t).shiftLeft(1)).toString()};
        } else if (kind == NumericKind.LONG) {
            long x = (long) inputs[0], y = (long) inputs[1], parity = 0;
            for (int i = 0; i < 64; i++) if (((x >>> i) & 1) != ((y >>> i) & 1)) parity |= 1L << i;
            return new long[] {BigInteger.valueOf(x).add(BigInteger.valueOf(y)).longValue(), parity};
        } else {
            double center = (double) inputs[0], step = (double) inputs[1], positive = center + step, negative = center - step;
            double right = positive * positive, left = negative * negative;
            return new double[] {right - left, (right + left) - ((2 * center) * center)};
        }
    }

    private static void same(NumericKind kind, Object expected, Object actual) {
        switch (kind) {
            case INT -> assertArrayEquals((int[]) expected, (int[]) actual);
            case LONG -> assertArrayEquals((long[]) expected, (long[]) actual);
            case BIG_INTEGER -> assertArrayEquals((String[]) expected, (String[]) actual);
            case DOUBLE -> {
                double[] a = (double[]) expected, b = (double[]) actual;
                assertEquals(a.length, b.length);
                for (int i = 0; i < a.length; i++) assertEquals(Double.doubleToLongBits(a[i]), Double.doubleToLongBits(b[i]));
            }
            default -> fail("Unexpected numeric kind");
        }
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
