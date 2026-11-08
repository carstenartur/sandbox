/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
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
import de.regelsuche.ast.iteration.FieldInversionReduction.Kind;

class JavaFieldInversionTest {
    @TempDir Path temporary;

    private static final String FIELD = """
            class Octets {
                enum Reduction { AES, RSA }
                private final int reduction;
                Octets(Reduction polynomial) {
                    switch (polynomial) {
                    case AES: reduction = 0x11b; break;
                    case RSA: reduction = 0x11d; break;
                    default: throw new IllegalArgumentException("polynomial");
                    }
                }
                protected byte times(int a, int b) {
                    int product = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        product ^= a & -(b & 1);
                        b >>>= 1;
                        int carry = (a >>> 7) & 1;
                        a = ((a << 1) ^ (reduction & -carry)) & 255;
                    }
                    return (byte) product;
                }
                protected byte power(int x, byte exponent) {
                    int product = 1;
                    for (int bit = 0; bit < 8; bit++) {
                        if ((exponent & (1 << bit)) != 0) {
                            product = times(product & 255, x & 255);
                        }
                        x = times(x & 255, x & 255);
                    }
                    return (byte) product;
                }
                protected byte over(int a, int b) { return times(a, power(b, (byte) 254) & 255); }
            }
            """;

    private static final String SAMPLE = """
            public class Sample {
                static class Slot { final int coordinate; Slot(int value) { coordinate = value; } }
                private final Octets arithmetic;
                private final Slot[] shares;
                private Sample(int mode, int[] coordinates) {
                    arithmetic = switch (mode) {
                        case 0 -> new Octets(Octets.Reduction.AES);
                        case 1 -> new Octets(Octets.Reduction.RSA);
                        case 2 -> new Octets(Octets.Reduction.AES) {
                            protected byte over(int a, int b) { return (byte) (a - b); }
                        };
                        case 3 -> new Octets(Octets.Reduction.RSA) {
                            protected byte power(int x, byte exponent) { throw new IllegalStateException("virtual"); }
                        };
                        default -> null;
                    };
                    shares = new Slot[coordinates.length];
                    for (int i = 0; i < coordinates.length; i++)
                        if (coordinates[i] != Integer.MIN_VALUE) shares[i] = new Slot(coordinates[i]);
                }
                public static byte[] scale(byte[] bytes, int denominator, int mode) {
                    return new Sample(mode, new int[0]).divide(bytes, denominator);
                }
                public static byte[] recover(int[] coordinates, int mode) {
                    return new Sample(mode, coordinates).coefficients();
                }
                private byte[] divide(byte[] bytes, int denominator) {
                    for (int j = 0; j < bytes.length; ++j) {
                        bytes[j] = arithmetic.over(bytes[j] & 255, denominator);
                    }
                    return bytes;
                }
                private byte[] coefficients() {
                    int n = shares.length;
                    if (n == 0) return new byte[0];
                    byte[] result = new byte[n];
                    byte coefficient;
                    byte[] scratch = new byte[n - 1];
                    for (int i = 0; i < n; i++) {
                        int cursor = 0;
                        for (int j = 0; j < n; j++) {
                            if (j != i) {
                                scratch[cursor++] = arithmetic.over(shares[j].coordinate, shares[i].coordinate ^ shares[j].coordinate);
                            }
                        }

                        coefficient = 1;
                        for (int p = 0; p != scratch.length; p++) {
                            coefficient = arithmetic.times(coefficient & 255, scratch[p] & 255);
                        }
                        result[i] = coefficient;
                    }
                    return result;
                }
            }
            """;

    @Test void compiledFastPathsPreserveByteResultsAndFallbackDispatch() throws Exception {
        var analysis = analyze(SAMPLE, FIELD, true);
        var evidence = analysis.evidence().stream().map(MathematicalAnalysis.VerifiedRegion::fieldInversion)
                .filter(java.util.Objects::nonNull).toList();
        assertEquals(List.of(Kind.PER_ELEMENT, Kind.PRODUCT), evidence.stream().map(e -> e.plan().kind()).toList(),
                () -> analysis.diagnostics().toString());
        for (var item : evidence) {
            assertEquals(2, item.minimumCost().originalInversions().intValueExact());
            assertEquals(1, item.minimumCost().plannedInversions().intValueExact());
        }
        var document = new Document(SAMPLE);
        analysis.newEdit().apply(document);
        assertTrue(document.get().lines().allMatch(line -> line.equals(line.stripTrailing())));
        try (var original = compile("before", SAMPLE); var generated = compile("after", document.get())) {
            Class<?> old = original.loadClass("Sample"), optimized = generated.loadClass("Sample");
            byte[] all = new byte[256];
            for (int i = 0; i < all.length; i++) all[i] = (byte) i;
            for (int mode = 0; mode < 5; mode++) {
                for (int d : new int[] {0, 1, 19, 128, 255, -237, 256}) {
                    for (byte[] bytes : new byte[][] {null, {}, {(byte) 0xff}, all}) {
                        Object[] a = {bytes == null ? null : bytes.clone(), d, mode};
                        Object[] b = {bytes == null ? null : bytes.clone(), d, mode};
                        compare(old, optimized, "scale", new Class<?>[] {byte[].class, int.class, int.class}, a, b);
                        assertArrayEquals((byte[]) a[0], (byte[]) b[0]);
                    }
                }
                for (int[] coordinates : new int[][] {{}, {1}, {1, 2}, {1, 2, 3}, {1, 1, 2},
                        {1, 257, 2}, {255, -2, 160, 42}, {1, Integer.MIN_VALUE, 3}}) {
                    Object[] args = {coordinates, mode};
                    compare(old, optimized, "recover", new Class<?>[] {int[].class, int.class}, args, args);
                }
            }
        }
        assertFalse(analyze(document.get(), FIELD, true).changed(), "A second cleanup must not wrap its fallback again");
        Files.writeString(temporary.resolve("Octets.java"), FIELD.replace("a = ((a << 1)", "a = ((a << 2)"));
        assertThrows(IllegalArgumentException.class, analysis::newEdit);
    }

    @Test void disabledOrUnprovedContractsDoNotEmitFastPaths() throws Exception {
        assertFalse(analyze(SAMPLE, FIELD, false).changed());
        assertNoEvidence(analyze(SAMPLE, FIELD.replace("return times(a, power", "System.nanoTime(); return times(a, power"), true));
        assertNoEvidence(analyze(SAMPLE, FIELD.replace("0x11d", "0x100"), true));
        assertNoEvidence(analyze(SAMPLE, FIELD.replace("protected byte power", "protected synchronized byte power"), true));
        assertNoEvidence(analyze(SAMPLE.replace("final Octets arithmetic", "Octets arithmetic"), FIELD, true));
        var escaping = analyze(SAMPLE.replace("return result;", "result[0] ^= scratch[0]; return result;"), FIELD, true);
        assertFalse(escaping.evidence().stream().anyMatch(e -> e.fieldInversion() != null
                && e.fieldInversion().plan().kind() == Kind.PRODUCT));
    }

    @Test void capturedScratchStillFeedsItsOwningMethod() throws Exception {
        String source = """
                class Sample {
                    private final Octets arithmetic = new Octets(Octets.Reduction.AES);
                    private final Slot[] shares = {new Slot(1), new Slot(2), new Slot(3)};
                    static class Slot { final int coordinate; Slot(int value) { coordinate = value; } }
                    byte run(int n) {
                        byte[] scratch = new byte[n - 1];
                        class Worker {
                            byte calculate() {
                                byte coefficient = 1;
                                for (int i = 0; i < n; i++) {
                                    int cursor = 0;
                                    for (int j = 0; j < n; j++) {
                                        if (j != i) {
                                            scratch[cursor++] = arithmetic.over(shares[j].coordinate, shares[i].coordinate ^ shares[j].coordinate);
                                        }
                                    }
                                    coefficient = 1;
                                    for (int p = 0; p != scratch.length; p++) {
                                        coefficient = arithmetic.times(coefficient & 255, scratch[p] & 255);
                                    }
                                }
                                return coefficient;
                            }
                        }
                        return (byte) (new Worker().calculate() ^ scratch[0]);
                    }
                }
                """;
        assertNoEvidence(analyze(source, FIELD, true));
        // Returning the closure exposes multiple invocations to the same captured scratch array.
        String lambda = source.replace("byte run(int n)", "java.util.function.IntSupplier run(int n)")
                .replace("class Worker {\n            byte calculate() {", "return () -> {\n            {")
                .replace("        }\n        return (byte) (new Worker().calculate() ^ scratch[0]);", "        };");
        assertNoEvidence(analyze(lambda, FIELD, true));
    }

    @Test void selectedLoopDoesNotRewriteOtherMatches() throws Exception {
        var analysis = analyze(SAMPLE, FIELD, true, SAMPLE.indexOf("scratch[cursor++]"));
        assertEquals(List.of(Kind.PRODUCT), analysis.evidence().stream().map(MathematicalAnalysis.VerifiedRegion::fieldInversion)
                .filter(java.util.Objects::nonNull).map(e -> e.plan().kind()).toList(), () -> analysis.diagnostics().toString());
    }

    @Test void mathematicalModeStillAllowsTheJavaPreservingByteFieldProof() throws Exception {
        Files.writeString(temporary.resolve("Octets.java"), FIELD);
        var compiler = new HashMap<String, String>();
        JavaCore.setComplianceOptions("17", compiler);
        var options = MathCleanUpOptions.parse(Map.of(MathCleanUpOptions.CLEANUP, "true",
                MathCleanUpOptions.FIELD_INVERSIONS, "true", MathCleanUpOptions.MATHEMATICAL_OPT_IN, "true"), 17);
        var analysis = MathematicalAnalysis.analyze(parse(SAMPLE, compiler), SAMPLE, options, null, -1, 0,
                new MathematicalAnalysis.SourceEnvironment("Sample.java", List.of(), List.of(temporary.toString()),
                        List.of(), compiler));
        assertEquals(List.of(Kind.PER_ELEMENT, Kind.PRODUCT), analysis.evidence().stream()
                .map(MathematicalAnalysis.VerifiedRegion::fieldInversion).filter(java.util.Objects::nonNull)
                .map(e -> e.plan().kind()).toList(), () -> analysis.diagnostics().toString());
    }

    @Test void freshExternalHelperModifiersOverrideTheEarlierCallerBinding() throws Exception {
        Path path = Files.writeString(temporary.resolve("Octets.java"), FIELD);
        var compiler = new HashMap<String, String>();
        JavaCore.setComplianceOptions("17", compiler);
        var caller = parse(SAMPLE, compiler);
        Files.writeString(path, FIELD.replace("protected byte over", "protected synchronized byte over"));
        assertNoEvidence(analyzeParsed(caller, SAMPLE, compiler, true, -1));
    }

    private static void assertNoEvidence(MathematicalAnalysis.Analysis analysis) {
        assertFalse(analysis.evidence().stream().anyMatch(e -> e.fieldInversion() != null));
    }

    private MathematicalAnalysis.Analysis analyze(String sample, String field, boolean enabled) throws Exception {
        return analyze(sample, field, enabled, -1);
    }

    private MathematicalAnalysis.Analysis analyze(String sample, String field, boolean enabled, int selection) throws Exception {
        Files.writeString(temporary.resolve("Octets.java"), field);
        var compiler = new HashMap<String, String>();
        JavaCore.setComplianceOptions("17", compiler);
        return analyzeParsed(parse(sample, compiler), sample, compiler, enabled, selection);
    }

    private CompilationUnit parse(String sample, Map<String, String> compiler) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setEnvironment(new String[0], new String[] {temporary.toString()}, null, true);
        parser.setUnitName("Sample.java");
        parser.setSource(sample.toCharArray());
        parser.setResolveBindings(true);
        parser.setCompilerOptions(compiler);
        var ast = (CompilationUnit) parser.createAST(null);
        assertTrue(java.util.Arrays.stream(ast.getProblems()).noneMatch(p -> p.isError()),
                () -> java.util.Arrays.toString(ast.getProblems()));
        return ast;
    }

    private MathematicalAnalysis.Analysis analyzeParsed(CompilationUnit ast, String sample,
            Map<String, String> compiler, boolean enabled, int selection) {
        var options = MathCleanUpOptions.parse(Map.of(MathCleanUpOptions.CLEANUP, "true",
                MathCleanUpOptions.FIELD_INVERSIONS, Boolean.toString(enabled)), 17);
        return MathematicalAnalysis.analyze(ast, sample, options, null, selection, 0,
                new MathematicalAnalysis.SourceEnvironment("Sample.java", List.of(), List.of(temporary.toString()),
                        List.of(), compiler));
    }

    private URLClassLoader compile(String directory, String source) throws Exception {
        Path work = Files.createDirectory(temporary.resolve(directory));
        Path sample = Files.writeString(work.resolve("Sample.java"), source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", work.toString(),
                sample.toString(), temporary.resolve("Octets.java").toString()));
        return new URLClassLoader(new URL[] {work.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    private static void compare(Class<?> before, Class<?> after, String name, Class<?>[] parameters,
            Object[] original, Object[] rewritten) throws Exception {
        Object expected;
        try {
            expected = before.getMethod(name, parameters).invoke(null, original);
        } catch (InvocationTargetException failed) {
            var actual = assertThrows(InvocationTargetException.class,
                    () -> after.getMethod(name, parameters).invoke(null, rewritten)).getCause();
            assertEquals(failed.getCause().getClass(), actual.getClass());
            if (!(actual instanceof NullPointerException)) assertEquals(failed.getCause().getMessage(), actual.getMessage());
            return;
        }
        assertArrayEquals((byte[]) expected, (byte[]) after.getMethod(name, parameters).invoke(null, rewritten));
    }
}
