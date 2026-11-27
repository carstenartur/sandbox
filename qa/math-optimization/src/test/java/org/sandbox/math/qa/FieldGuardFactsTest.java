/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.FieldCallEvidence;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import org.sandbox.jdt.math.tests.StrictCompilerOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FieldGuardFactsTest {
    @TempDir Path temporary;
    private static final String WRAPPER = """
            static final class Coordinates {
                private final byte[] left, right;
                Coordinates(byte[] l, byte[] r) { left = l; right = r; }
                BigInteger first() { return positive(left); }
                BigInteger second() { return positive(right); }
                static BigInteger positive(byte[] bytes) { return new BigInteger(1, bytes); }
            }
            static boolean publish(Coordinates point, int[][] destination) {
                BigInteger modulus = Q;
                BigInteger left = point.first(), right = point.second();
                BigInteger magnitude = left.multiply(left).add(right.multiply(right)).mod(modulus);
                BigInteger root = magnitude.modPow(Grade, modulus);
                boolean confirmed = root.multiply(root).mod(modulus).equals(magnitude);
                if (!confirmed) {
                    left = point.second();
                    right = point.first();
                    root = left.multiply(root).mod(modulus);
                }
                destination[0] = recover(left, right, root);
                return confirmed;
            }
            """;

    private static final String ROOT = """
            static int[] recover(BigInteger red, BigInteger blue, BigInteger grey) {
                BigInteger field = Q;
                if (blue.signum() == 0) return pack(red, blue);
                BigInteger delta = red.add(grey).multiply(Half).mod(field);
                BigInteger x = delta.modPow(Grade, field);
                if (!x.multiply(x).mod(field).equals(delta)) {
                    delta = red.subtract(grey).multiply(Half).mod(field);
                    x = delta.modPow(Grade, field);
                }
                BigInteger y = blue.multiply(x.shiftLeft(1).modInverse(field)).mod(field);
                return pack(x, y);
            }
            """;
    private static final String SOURCE = """
            import java.math.BigInteger;
            class Ocean {
                static final BigInteger Q = BigInteger.valueOf(19);
                static final BigInteger Half = BigInteger.TWO.modInverse(Q);
                static final BigInteger Grade = Q.add(BigInteger.ONE).shiftRight(2);
                static int[] pack(BigInteger left, BigInteger right) { return new int[]{left.intValueExact(), right.intValueExact()}; }
            """ + WRAPPER + ROOT + "}";

    @Test void specializesOnlyTheCallWhereTheNormWasObserved() throws Exception {
        var analysis = analyze(SOURCE);
        var receipt = receipt(analysis);
        assertTrue(receipt.inputs().get(0).exactClass());
        assertTrue(receipt.inputs().get(1).nonnegative());
        assertFalse(receipt.inputs().get(1).belowModulus());
        assertFalse(receipt.inputs().get(2).exactClass());
        assertFalse(receipt.normWitnessKey().isEmpty());
        var document = new Document(SOURCE);
        var undo = analysis.newEdit().apply(document);
        String output = document.get();
        assertTrue(output.contains(ROOT));
        assertTrue(output.contains("getLowestSetBit()"));
        try (var baseline = compile(SOURCE, "before"); var changed = compile(output, "after")) {
            var original = baseline.loadClass("Ocean");
            var generated = changed.loadClass("Ocean");
            var attempt = generated.getDeclaredMethod(receipt.helperName(), BigInteger.class, BigInteger.class, BigInteger.class, boolean.class);
            attempt.setAccessible(true);
            assertNull(attempt.invoke(null, BigInteger.ONE, BigInteger.TWO, BigInteger.ONE, false));
            assertNull(attempt.invoke(null, BigInteger.ONE, BigInteger.ZERO, BigInteger.ONE, true));
            assertNull(attempt.invoke(null, BigInteger.valueOf(19), BigInteger.TWO, BigInteger.ONE, true));
            assertNotNull(attempt.invoke(null, BigInteger.ZERO, BigInteger.TWO, BigInteger.TWO, false));
            for (int a = 0; a < 22; ++a) for (int b = 0; b < 22; ++b) {
                assertEquals(run(original, a, b, 1), run(generated, a, b, 1), a + "," + b);
            }
            for (int n : new int[]{-1,0}) assertEquals(run(original, 2, 1, n), run(generated, 2, 1, n));
        }
        assertFalse(analyze(output, -1).evidence().stream().anyMatch(r -> r.fieldRoot() != null), "do not specialize the original fallback again");
        undo.apply(document);
        assertEquals(SOURCE, document.get());
    }

    @Test void wholeFileApplyReachesItsFixedPointWhenTwoCallersQualify() throws Exception {
        String first = WRAPPER.substring(WRAPPER.indexOf("static boolean publish"));
        String second = first.replace("publish(", "publishAgain(");
        var document = new Document(SOURCE.replace(ROOT, second + ROOT));
        var analysis = analyze(document.get(), -1);
        assertTrue(receipt(analysis).callerKey().contains("publish("));
        analysis.newEdit().apply(document);
        String output = document.get();
        assertTrue(output.contains(second));
        for (int selected : new int[]{-1, output.indexOf("publishAgain("), output.indexOf("BigInteger delta =")}) {
            var repeated = analyze(output, selected);
            assertTrue(repeated.evidence().stream().noneMatch(r -> r.fieldRoot() != null), () -> repeated.diagnostics().toString());
            repeated.newEdit().apply(document);
            assertEquals(output, document.get());
        }
    }

    @Test void handwrittenNullableAttemptsLeaveTheSelectedScalarActionAvailable() throws Exception {
        for (String parameters : List.of("", "BigInteger a, BigInteger b, BigInteger g")) {
            String args = parameters.isEmpty() ? "" : "a, b, g";
            String wrapper = """
                    private static int[] cached(%s) { return null; }
                    static int[] cachedOrCompute(BigInteger a, BigInteger b, BigInteger g) {
                        int[] chosen = cached(%s);
                        int[] result = chosen != null ? chosen : recover(a, b, g);
                        return result;
                    }
                    """.formatted(parameters, args);
            String source = SOURCE.replace(ROOT, wrapper + ROOT);
            assertSelectedScalar(source);
        }
    }

    @Test void aStaleCandidateBindingOrGuardDoesNotReserveTheScalarRoot() throws Exception {
        var analysis = analyze(SOURCE);
        String helper = receipt(analysis).helperName();
        var doc = new Document(SOURCE);
        analysis.newEdit().apply(doc);
        String generated = doc.get();
        for (String changed : List.of(
                generated.replace(helper + "(left, right, root, confirmed)", helper + "(right, left, root, confirmed)"),
                generated.replace("blue.signum() > 0", "blue.signum() < 0"),
                generated.replace("return confirmed;", "return !confirmed;"))) {
            assertNotEquals(generated, changed);
            assertSelectedScalar(changed);
        }
    }

    @Test void laterDereferencePreservesTheHelpfulNpeOnFastAndFallbackInputs() throws Exception {
        String source = SOURCE.replace("destination[0] = ", "int[] result = ")
                .replace("return confirmed;", "String doomed = result.length > 0 ? null : \"\"; return doomed.length() > 0;");
        var document = new Document(source);
        var whole = analyze(source, -1);
        whole.newEdit().apply(document);
        try (var baseline = compile(source, "original"); var applied = compile(document.get(), "applied")) {
            var before = baseline.loadClass("Ocean");
            var after = applied.loadClass("Ocean");
            for (int imaginary : new int[]{2, 0}) {
                String expected = run(before, 0, imaginary, 1);
                assertTrue(expected.startsWith("java.lang.NullPointerException|"), expected);
                assertEquals(expected, run(after, 0, imaginary, 1));
            }
        }
        assertTrue(whole.evidence().stream().anyMatch(r -> r.fieldRoot() != null && r.fieldRoot().callSite() == null));
        var selected = analyze(source, source.indexOf("int[] result ="));
        var original = new Document(source);
        selected.newEdit().apply(original);
        assertEquals(source, original.get());
    }

    @Test void admitsOnlyDirectReturnsWithNoBoxingOrUnboxing() {
        String caller = SOURCE.replace("int[][] destination)", "int[][] destination, Boolean boxed)")
                .replace("destination[0] = ", "int[] result = ");
        record Ending(String type, String suffix, boolean eligible) {}
        for (var form : List.of(new Ending("void", "", true), new Ending("void", "return;", true),
                new Ending("int[]", "return result;", true), new Ending("BigInteger", "return left;", true),
                new Ending("Boolean", "return boxed;", true), new Ending("boolean", "return confirmed;", true),
                new Ending("boolean", "return true;", true), new Ending("int[]", "return null;", true),
                new Ending("boolean", "return boxed;", false), new Ending("Boolean", "return confirmed;", false),
                new Ending("BigInteger", "return Q;", false), new Ending("BigInteger", "return point.first();", false),
                new Ending("boolean", "return (boolean)confirmed;", false))) {
            String source = caller.replace("static boolean publish", "static " + form.type() + " publish")
                    .replace("return confirmed;", form.suffix());
            var analysis = analyze(source, source.indexOf("int[] result ="));
            assertEquals(form.eligible(), analysis.evidence().stream().anyMatch(r -> r.fieldRoot() != null && r.fieldRoot().callSite() != null),
                    form.toString());
        }
    }

    @Test void inferredReturnLocalCannotCreateAnEditThatOutlivesItsForeignInitializerType() throws Exception {
        String explicit = SOURCE.replace("BigInteger modulus = Q;", "boolean allowed = Foreign.flag(); BigInteger modulus = Q;")
                .replace("return confirmed;", "return allowed;");
        String inferred = explicit.replace("boolean allowed", "var allowed");
        Path foreign = temporary.resolve("Foreign.java");
        Files.writeString(foreign, "class Foreign { static boolean flag() { return true; } }");
        var cached = analyze(inferred, inferred.indexOf("destination[0]"), temporary.toString());
        receipt(analyze(explicit, explicit.indexOf("destination[0]"), temporary.toString()));
        Files.writeString(foreign, "class Foreign { static Boolean flag() { return null; } }");
        var document = new Document(inferred);
        cached.newEdit().apply(document);
        assertEquals(inferred, document.get());
        assertFalse(cached.changed());
        assertFalse(analyze(inferred, inferred.indexOf("destination[0]"), temporary.toString()).changed());
        receipt(analyze(explicit, explicit.indexOf("destination[0]"), temporary.toString()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cachedFieldInitializersCannotFoldInheritedJdkMembers(boolean factory) throws Exception {
        String source = factory ? SOURCE.replace("Q = BigInteger.valueOf(19)", "Q = Child.valueOf(19)")
                : SOURCE.replace("Half = BigInteger.TWO", "Half = Child.TWO");
        String header = "import java.math.BigInteger; class Child extends BigInteger { "
                + "private static final long serialVersionUID = 1L; Child() { super(\"0\"); } ";
        Path child = Files.writeString(temporary.resolve("Child.java"), header + "}");
        int selected = source.indexOf("destination[0]");
        var cached = analyze(source, selected, temporary.toString());
        String hidden = factory ? "public static BigInteger valueOf(long ignored) { return BigInteger.valueOf(13); }"
                : "public static final BigInteger TWO = BigInteger.valueOf(3);";
        Files.writeString(child, header + hidden + "}");
        String expected = factory ? "true[[8, 9]]" : "true[[5, 2]]";
        try (var baseline = compile(source, "original")) {
            assertEquals(expected, run(baseline.loadClass("Ocean"), 0, 1, 1));
        }
        for (var result : List.of(cached, analyze(source, selected, temporary.toString()))) {
            var doc = new Document(source);
            result.newEdit().apply(doc);
            try (var loader = compile(doc.get(), result == cached ? "cached" : "fresh")) {
                assertEquals(expected, run(loader.loadClass("Ocean"), 0, 1, 1));
            }
        }
    }

    @Test void anInheritedModulusAliasCannotQualifyACachedExactCall() throws Exception {
        String imports = "import java.math.BigInteger; ";
        Files.writeString(temporary.resolve("Parent.java"), imports + "class Parent { static final BigInteger P = BigInteger.valueOf(19); }");
        Path child = Files.writeString(temporary.resolve("Child.java"), "class Child extends Parent {}");
        String source = SOURCE.replace("Q = BigInteger.valueOf(19)", "Q = Child.P");
        int selected = source.indexOf("destination[0]");
        var cached = analyze(source, selected, temporary.toString());
        Files.writeString(child, imports + "class Child extends Parent { static final BigInteger P = BigInteger.ZERO; }");
        for (var result : List.of(cached, analyze(source, selected, temporary.toString()))) {
            var doc = new Document(source);
            result.newEdit().apply(doc);
            assertEquals(source, doc.get());
        }
    }

    @Test void anInheritedModulusCannotCertifyTheCallersNormWitness() throws Exception {
        String source = SOURCE.replace("BigInteger modulus = Q;", "BigInteger modulus = Child.Q;");
        Files.writeString(temporary.resolve("Ocean.java"), source);
        Path child = Files.writeString(temporary.resolve("Child.java"), "class Child extends Ocean {}");
        int selected = source.indexOf("destination[0]");
        var cached = analyze(source, selected, temporary.toString());
        Files.writeString(child, "import java.math.BigInteger; class Child extends Ocean { static final BigInteger Q = BigInteger.valueOf(17); }");
        for (var result : List.of(cached, analyze(source, selected, temporary.toString()))) {
            var doc = new Document(source);
            result.newEdit().apply(doc);
            assertTrue(result.evidence().stream().noneMatch(r -> r.fieldRoot() != null && r.fieldRoot().callSite() != null
                    && !r.fieldRoot().callSite().normWitnessKey().isEmpty()), doc::get);
        }
    }

    private static void assertSelectedScalar(String source) throws Exception {
        var analysis = analyze(source, source.indexOf("BigInteger delta ="));
        assertTrue(analysis.evidence().stream().anyMatch(r -> r.fieldRoot() != null && r.fieldRoot().callSite() == null));
        var doc = new Document(source);
        var undo = analysis.newEdit().apply(doc);
        assertFalse(doc.get().contains(ROOT));
        undo.apply(doc);
        assertEquals(source, doc.get());
    }

    @Test void changesToTheProvedPredicateKeepTheRuntimeNormCheck() {
        for (var source : List.of(SOURCE.replace("root.multiply(root).mod(modulus)", "root.multiply(left).mod(modulus)"),
                SOURCE.replace("mod(modulus).equals(magnitude)", "mod(Grade).equals(magnitude)"),
                SOURCE.replace("left = point.second();", "confirmed = true; left = point.second();"))) {
            assertTrue(receipt(analyze(source)).normWitnessKey().isEmpty());
        }
    }

    @Test void skipsUnstableArgumentsAndAddresses() {
        for (var source : List.of(SOURCE.replace("destination[0] = ", "int i = 0; destination[++i] = "),
                SOURCE.replace("recover(left, right, root)", "recover(left.add(BigInteger.ZERO), right, root)"),
                SOURCE.replace("static final class Coordinates", "static class Coordinates"),
                SOURCE.replace("static int[] recover(", "static synchronized int[] recover("),
                SOURCE.replace("BigInteger field = Q;", "BigInteger field = Other.q;")
                        + " class Other { static final java.math.BigInteger q = java.math.BigInteger.valueOf(19); }")) {
            var analysis = analyze(source);
            assertTrue(analysis.evidence().stream().noneMatch(r -> r.fieldRoot() != null), () -> analysis.diagnostics().toString());
        }
    }

    @Test void distinguishesRepresentationFromThePostconditionOfAnExactReceiver() {
        var signed = receipt(analyze(SOURCE.replace("new BigInteger(1, bytes)", "new BigInteger(bytes)")));
        assertTrue(signed.inputs().getFirst().exactClass());
        assertFalse(signed.inputs().getFirst().nonnegative());
        String reduced = SOURCE.replace("= point.second()", "= point.second().mod(modulus)")
                .replace("right = point.first()", "right = point.first().mod(modulus)");
        var facts = receipt(analyze(reduced)).inputs().get(1);
        assertFalse(facts.exactClass());
        assertTrue(facts.nonnegative());
        assertTrue(facts.belowModulus());
    }

    @Test void keepsUnprovedDirectCallersAndTheirCallbacks() throws Exception {
        String forwarded = SOURCE.replace("static final class Coordinates", "static class Coordinates")
                .replace("BigInteger first()", "final BigInteger first()").replace("left = point.second();", "left = point.first();")
                .replace("right = point.first();", "right = point.second();")
                .replace("static boolean publish", """
                        static final StringBuilder events = new StringBuilder();
                        static final class Trace extends BigInteger {
                            private static final long serialVersionUID = 1L;
                            Trace() { super("3"); }
                            @Override public int signum() {
                                String caller = StackWalker.getInstance().walk(stack -> stack.skip(1).findFirst().orElseThrow().getMethodName());
                                events.append(caller);
                                return super.signum();
                            }
                        }
                        static String callbacks() {
                            Coordinates point = new Coordinates(new byte[]{1}, null) {
                                @Override BigInteger second() { events.append("read"); return new Trace(); }
                            };
                            events.setLength(0);
                            try { publish(point, new int[1][]); }
                            catch (ArithmeticException ignored) { events.append("arithmetic"); }
                            return events.toString();
                        }
                        static void raw(BigInteger a, BigInteger b, BigInteger g, int[][] out) { out[0] = recover(a,b,g); }
                        static boolean publish""");
        var result = analyze(forwarded, -1);
        var proof = receipt(result);
        assertTrue(proof.inputs().getFirst().exactClass());
        assertFalse(proof.inputs().get(1).exactClass());
        assertTrue(proof.normWitnessKey().isEmpty());
        var document = new Document(forwarded);
        result.newEdit().apply(document);
        try (var baseline = compile(forwarded, "original"); var candidate = compile(document.get(), "changed")) {
            var before = baseline.loadClass("Ocean").getDeclaredMethod("callbacks");
            var after = candidate.loadClass("Ocean").getDeclaredMethod("callbacks");
            before.setAccessible(true); after.setAccessible(true);
            String log = (String)before.invoke(null);
            assertTrue(log.contains("recover"), log);
            assertEquals(log, after.invoke(null));
        }
    }

    private static FieldCallEvidence receipt(MathematicalAnalysis.Analysis analysis) {
        return analysis.evidence().stream().filter(r -> r.fieldRoot() != null && r.fieldRoot().callSite() != null)
                .findFirst().orElseThrow(() -> new AssertionError(analysis.diagnostics())).fieldRoot().callSite();
    }

    private URLClassLoader compile(String source, String name) throws Exception {
        Path classes = Files.createDirectory(temporary.resolve(name));
        Path file = Files.writeString(classes.resolve("Ocean.java"), source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-Xlint:all", "-Werror",
                "-sourcepath", temporary.toString(), "-d", classes.toString(), file.toString()));
        return new URLClassLoader(new URL[]{classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    private static String run(Class<?> owner, int a, int b, int length) throws Exception {
        var coords = Class.forName("Ocean$Coordinates", true, owner.getClassLoader());
        var ctor = coords.getDeclaredConstructor(byte[].class, byte[].class);
        ctor.setAccessible(true);
        var publish = owner.getDeclaredMethod("publish", coords, int[][].class);
        publish.setAccessible(true);
        int[][] output = length < 0 ? null : new int[length][];
        try {
            var input = ctor.newInstance(new byte[]{(byte)a}, new byte[]{(byte)b});
            return publish.invoke(null, input, output) + Arrays.deepToString(output);
        } catch (java.lang.reflect.InvocationTargetException e) {
            return e.getCause().getClass().getName() + "|" + e.getCause().getMessage();
        }
    }

    private static MathematicalAnalysis.Analysis analyze(String source) {
        return analyze(source, source.indexOf("destination["));
    }

    private static MathematicalAnalysis.Analysis analyze(String source, int selection, String... sourcepath) {
        var compiler = StrictCompilerOptions.allWarnings(17);
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setEnvironment(new String[0], sourcepath, null, true);
        parser.setUnitName("Ocean.java");
        parser.setCompilerOptions(compiler);
        parser.setResolveBindings(true);
        parser.setSource(source.toCharArray());
        var ast = (CompilationUnit)parser.createAST(null);
        assertFalse(Arrays.stream(ast.getProblems()).anyMatch(p -> p.isError()), () -> Arrays.toString(ast.getProblems()));
        var options = MathCleanUpOptions.parse(Map.of(MathCleanUpOptions.CLEANUP, "true", MathCleanUpOptions.WORK_BUDGET, "100000000",
                MathCleanUpOptions.MAX_STATES, "20000", MathCleanUpOptions.TIMEOUT_SECONDS, "30"), 17);
        return MathematicalAnalysis.analyze(ast, source, options, null, selection, 1,
                new MathematicalAnalysis.SourceEnvironment("Ocean.java", List.of(), List.of(sourcepath), List.of(), compiler));
    }
}
