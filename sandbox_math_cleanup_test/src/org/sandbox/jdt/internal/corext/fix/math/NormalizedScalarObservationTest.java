/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import static java.math.BigInteger.*;
import java.math.BigInteger;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import javax.tools.ToolProvider;
import org.eclipse.jdt.core.compiler.batch.BatchCompiler;
import org.junit.jupiter.api.io.TempDir;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.math.tests.StrictCompilerOptions;

class NormalizedScalarObservationTest {
    @TempDir Path temporary;
    static final BigInteger P = new BigInteger(
            "1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaab", 16);
    static final String IMPORTS = "import java.math.BigInteger; import java.lang.System; ";
    static final String ENCODE = """
            static byte[] encode(BigInteger x, BigInteger y) {
                byte[] left = x.toByteArray(), right = y.toByteArray();
                byte[] out = new byte[4 + left.length + right.length];
                out[0] = (byte)(left.length >>> 24);
                out[1] = (byte)(left.length >>> 16);
                out[2] = (byte)(left.length >>> 8);
                out[3] = (byte)left.length;
                System.arraycopy(left, 0, out, 4, left.length);
                System.arraycopy(right, 0, out, 4 + left.length, right.length);
                return out;
            }
            """;
    static final String CALLER = """
            static byte[] observe(BigInteger a, BigInteger b, BigInteger g, boolean target) {
                final BigInteger[] pair = recover(a, b, g);
                BigInteger x = pair[0];
                BigInteger y = pair[1];
                if ((x.signum() != 0 ? x : y).testBit(0) != target) {
                    x = x.negate().mod(Q);
                    y = y.negate().mod(Q);
                }
                return encode(x, y);
            }
            """;
    static final String SOURCE = GuardedQuadraticRootSourceTest.ORIGINAL.replace("BigInteger.valueOf(19)", "new BigInteger(\"" + P + "\")")
            .replace("import java.math.BigInteger;", IMPORTS)
            .replace("static long pack", "static BigInteger[] pack")
            .replace("left.longValueExact()*1000L + right.longValueExact()", "new BigInteger[]{left, right}")
            .replace("static long recover", ENCODE + CALLER + "static BigInteger[] recover");
    static final MathCleanUpOptions OPTIONS = MathCleanUpOptions.parse(Map.of(MathCleanUpOptions.CLEANUP, "true",
            MathCleanUpOptions.KINDS, "INT,BIG_INTEGER", MathCleanUpOptions.WORK_BUDGET, "100000000", MathCleanUpOptions.MAX_STATES, "20000",
            MathCleanUpOptions.TIMEOUT_SECONDS, "30"), 17);
    static CompilationUnit parse(String source, String... paths) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setEnvironment(new String[0], paths, null, true);
        parser.setUnitName("Ocean.java");
        parser.setCompilerOptions(StrictCompilerOptions.allWarnings(17));
        parser.setResolveBindings(true);
        parser.setSource(source.toCharArray());
        var ast = (CompilationUnit)parser.createAST(null);
        assertFalse(Arrays.stream(ast.getProblems()).anyMatch(p -> p.isError()), () -> Arrays.toString(ast.getProblems()));
        return ast;
    }
    static MathematicalAnalysis.Analysis analyze(String source, int selected, String... paths) {
        return MathematicalAnalysis.analyze(parse(source, paths), source, OPTIONS, null, selected, 1,
                new MathematicalAnalysis.SourceEnvironment("Ocean.java", List.of(), List.of(paths), List.of(),
                        StrictCompilerOptions.allWarnings(17)));
    }
    record Applied(String source, MathematicalAnalysis.VerifiedRegion region) {}
    static Applied apply(String source) throws Exception {
        var analysis = analyze(source, source.indexOf("recover(a, b, g)"));
        var document = new Document(source);
        var undo = analysis.newEdit().apply(document);
        String generated = document.get();
        assertNotEquals(source, generated, () -> analysis.diagnostics().toString());
        undo.apply(document);
        assertEquals(source, document.get());
        return new Applied(generated, analysis.evidence().stream().filter(r -> r.fieldRoot() != null).findFirst().orElseThrow());
    }

    @Test void ordinaryApplyFindsTheNormalizedCaller() throws Exception {
        var applied = apply(SOURCE);
        var program = applied.region().fieldRoot().certificate().program();
        assertFalse(program.equal().normalizeRealCharacter());
        assertFalse(program.unequal().normalizeRealCharacter());
        assertFalse(applied.source().contains("getLowestSetBit"));
    }

    private URLClassLoader compile(String source, String profile) throws Exception {
        Path dir = Files.createTempDirectory(temporary, "classes");
        Path java = Files.writeString(dir.resolve("Ocean.java"), source, StandardCharsets.UTF_8);
        var args = new ArrayList<>(List.of("--release", "17", "-proc:none", "-classpath", dir.toString(), "-d", dir.toString()));
        if (profile.equals("ecj")) {
            args.addAll(List.of("-g:none", java.toString()));
            var errors = new java.io.StringWriter();
            var writer = new java.io.PrintWriter(errors);
            assertTrue(BatchCompiler.compile(args.toArray(String[]::new), writer, writer, null), errors::toString);
        } else {
            if (!profile.isEmpty()) args.add(profile);
            args.addAll(List.of("-Xlint:all", "-Werror", java.toString()));
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        }
        return new URLClassLoader(new URL[]{dir.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
    private static Method method(ClassLoader loader, String name, boolean target) throws Exception {
        var types = new ArrayList<Class<?>>(List.of(BigInteger.class, BigInteger.class, BigInteger.class));
        if (target) types.add(boolean.class);
        var method = loader.loadClass("Ocean").getDeclaredMethod(name, types.toArray(Class<?>[]::new));
        method.setAccessible(true);
        return method;
    }
    private record Input(BigInteger a, BigInteger b, BigInteger g) {
        Object[] roles() { return new Object[]{a, b, g}; }
        Object[] roles(boolean target) { return new Object[]{a, b, g, target}; }
    }
    private static BigInteger seed(int seed, String coordinate) throws Exception {
        return new BigInteger(1, MessageDigest.getInstance("SHA-512").digest(
                ("scalar-root-vector/v1/interior-" + seed + "/" + coordinate).getBytes(StandardCharsets.US_ASCII))).mod(P);
    }
    private static byte[] encode(BigInteger[] raw, boolean realFirst, boolean target) {
        var first = raw[realFirst ? 0 : 1];
        boolean negate = (first.signum() != 0 ? first : raw[realFirst ? 1 : 0]).testBit(0) != target;
        byte[] x = (negate ? raw[0].negate().mod(P) : raw[0]).toByteArray();
        byte[] y = (negate ? raw[1].negate().mod(P) : raw[1]).toByteArray();
        return ByteBuffer.allocate(4 + x.length + y.length).putInt(x.length).put(x).put(y).array();
    }
    @Test void fullWidthObservationsAgreeAcrossBothCandidateArmsAndRawOrientations() throws Exception {
        var anchor = new Input(valueOf(3), valueOf(4), P.subtract(valueOf(5)));
        var vectors = new ArrayList<>(List.of(anchor));
        for (int index : List.of(1, 2)) {
            var u = seed(index, "real");
            var v = seed(index, "imaginary");
            var a = u.multiply(u).subtract(v.multiply(v)).mod(P);
            var b = u.multiply(v).shiftLeft(1).mod(P);
            var norm = u.multiply(u).add(v.multiply(v)).mod(P);
            for (var g : List.of(norm, P.subtract(norm))) vectors.add(new Input(a, b, g));
        }
        String raw = "static BigInteger[] raw(BigInteger a, BigInteger b, BigInteger g) { return recover(a,b,g); }";
        for (boolean realFirst : List.of(true, false)) {
            String source = SOURCE.replace(ENCODE, raw + "\n" + ENCODE);
            if (!realFirst) source = source.replace("x.signum() != 0 ? x : y", "y.signum() != 0 ? y : x");
            var applied = apply(source);
            var proof = applied.region().fieldRoot();
            assertTrue(applied.source().contains(raw));
            assertTrue(applied.source().contains(source.substring(source.indexOf("static BigInteger[] recover"))));
            try (var before = compile(source, ""); var after = compile(applied.source(), "")) {
                var candidate = method(after, proof.callSite().helperName(), false);
                var baseline = method(before, "raw", false);
                var unchanged = method(after, "raw", false);
                var oldObserve = method(before, "observe", true);
                var newObserve = method(after, "observe", true);
                var arms = new HashSet<Boolean>();
                var orientations = new HashSet<Boolean>();
                for (var vector : vectors) {
                    var original = (BigInteger[])baseline.invoke(null, vector.roles());
                    var generated = (BigInteger[])candidate.invoke(null, vector.roles());
                    assertNotNull(generated);
                    var forwarded = (BigInteger[])unchanged.invoke(null, vector.roles());
                    assertArrayEquals(original, forwarded);
                    assertNotSame(forwarded, unchanged.invoke(null, vector.roles()));
                    var delta = vector.a().add(vector.g()).multiply(TWO.modInverse(P)).mod(P);
                    var r = delta.modPow(P.add(ONE).shiftRight(2).subtract(ONE), P).multiply(delta).mod(P);
                    boolean equalArm = r.multiply(r).mod(P).equals(delta);
                    arms.add(equalArm);
                    assertEquals(r, generated[equalArm ? 0 : 1].multiply(equalArm ? ONE : ONE.negate()).mod(P));
                    orientations.add(Arrays.equals(original, generated));
                    if (vector == anchor) {
                        assertArrayEquals(new BigInteger[]{P.subtract(TWO), P.subtract(ONE)}, original);
                        assertArrayEquals(new BigInteger[]{TWO, ONE}, generated);
                    } else {
                        for (var pair : List.of(original, generated)) for (var n : pair) assertTrue(n.bitLength() > 64);
                    }
                    for (boolean target : List.of(false, true)) {
                        byte[] expected = encode(original, realFirst, target);
                        assertArrayEquals(expected, (byte[])oldObserve.invoke(null, vector.roles(target)));
                        assertArrayEquals(expected, (byte[])newObserve.invoke(null, vector.roles(target)));
                    }
                }
                assertEquals(Set.of(true, false), arms);
                assertEquals(Set.of(true, false), orientations);
            }
        }
    }

    private static Throwable failure(Method method, Object[] args) {
        return assertThrows(InvocationTargetException.class, () -> method.invoke(null, args)).getCause();
    }
    @Test void scopedLocalPreservesHelpfulMessagesOnEveryQualifiedCompiler() throws Exception {
        for (boolean prefix : List.of(false, true)) for (boolean isFinal : List.of(false, true)) {
            String source = SOURCE.replace("final BigInteger[] pair", (prefix ? "long live = b == null ? 0L : b.longValue(); if (live == 13) return null; " : "")
                    + (isFinal ? "final " : "") + "BigInteger[] pair")
                    .replace("return pack(amber, cobalt);", "return grey == null ? null : pack(amber, grey.signum() == 0 ? null : cobalt);");
            var applied = apply(source);
            for (String profile : List.of("", "-g:none", "-g:vars,lines,source", "ecj")) {
                try (var before = compile(source, profile); var after = compile(applied.source(), profile)) {
                    var original = method(before, "observe", true);
                    var edited = method(after, "observe", true);
                    var lanes = List.of(new Input(ONE, ZERO, null), new Input(null, ZERO, ONE), new Input(ONE, ZERO, ZERO));
                    var names = List.of("pair", "x", "y");
                    for (int i = 0; i < lanes.size(); i++) {
                        var args = lanes.get(i).roles(false);
                        var expected = failure(original, args);
                        var actual = failure(edited, args);
                        assertEquals(NullPointerException.class, expected.getClass());
                        assertEquals(expected.getClass(), actual.getClass());
                        assertEquals(expected.getMessage(), actual.getMessage());
                        // Coverage probes can shift unnamed local slots in both compiled methods.
                        String local = profile.startsWith("-g:vars") ? names.get(i) : "<local[0-9]+>";
                        assertTrue(expected.getMessage().matches(".*\"" + local + "\" is null"), profile + expected.getMessage());
                    }
                }
            }
        }
    }

    @Test void unsupportedInputsTakeTheOriginalWithTheSameCallbacksAndSinkExceptions() throws Exception {
        String source = "import java.lang.IllegalArgumentException; " + SOURCE.replace("byte[] left =", "if (x.intValue() == 2) throw new IllegalArgumentException(\"coordinate\"); byte[] left =")
                .replace("static final BigInteger Grade", """
                    static final String early = early();
                    static String early() {
                        String real = java.util.Arrays.toString(observe(BigInteger.ONE, BigInteger.ZERO, BigInteger.ONE, true));
                        try { observe(BigInteger.valueOf(3), BigInteger.valueOf(4), Q.subtract(BigInteger.valueOf(5)), true); return real; }
                        catch (RuntimeException error) { return real + "|" + error.getClass() + "|" + error.getMessage(); }
                    }
                    static final BigInteger Grade""");
        var applied = apply(source);
        try (var before = compile(source, ""); var after = compile(applied.source(), "")) {
            var f1 = before.loadClass("Ocean").getDeclaredField("early");
            var f2 = after.loadClass("Ocean").getDeclaredField("early");
            f1.setAccessible(true); f2.setAccessible(true);
            assertTrue(((String)f1.get(null)).contains("NullPointerException"));
            assertEquals(f1.get(null), f2.get(null));
            var orig = method(before, "observe", true);
            var edit = method(after, "observe", true);
            var attempt = method(after, applied.region().fieldRoot().callSite().helperName(), false);
            StringBuilder log = new StringBuilder();
            BigInteger trace = new BigInteger("4") {
                private static final long serialVersionUID = 1L;
                @Override public int signum() {
                    var caller = StackWalker.getInstance().walk(s -> s.skip(1).findFirst().orElseThrow().getMethodName());
                    log.append(caller).append(' ');
                    return super.signum();
                }
            };
            for (var vector : List.of(new Input(ONE, null, ONE), new Input(null, ONE, ONE), new Input(ONE, ONE, null),
                    new Input(ONE, ONE, ONE), new Input(ONE.negate(), TWO, ONE), new Input(P, TWO, ONE),
                    new Input(valueOf(3).subtract(P), valueOf(4), P.subtract(valueOf(5))),
                    new Input(valueOf(3).add(P), valueOf(4), P.subtract(valueOf(5))),
                    new Input(ZERO, ZERO, ZERO), new Input(ONE, ZERO, ONE), new Input(valueOf(3), trace, P.subtract(valueOf(5))))) {
                log.setLength(0);
                assertNull(attempt.invoke(null, vector.roles()));
                assertTrue(log.isEmpty());
                String expected = outcome(orig, vector);
                String callbacks = log.toString();
                if (vector.b() == trace) assertTrue(callbacks.startsWith("recover "), callbacks);
                log.setLength(0);
                assertEquals(expected, outcome(edit, vector));
                assertEquals(callbacks, log.toString());
            }
            var anchor = new Input(valueOf(3), valueOf(4), P.subtract(valueOf(5)));
            assertNotNull(attempt.invoke(null, anchor.roles()));
            assertEquals("class java.lang.IllegalArgumentException|coordinate", outcome(orig, anchor));
            assertEquals(outcome(orig, anchor), outcome(edit, anchor));
        }
    }
    private static String outcome(Method method, Input input) throws Exception {
        try { return Arrays.toString((byte[])method.invoke(null, input.roles(false))); }
        catch (InvocationTargetException failure) { return failure.getCause().getClass() + "|" + failure.getCause().getMessage(); }
    }
}
