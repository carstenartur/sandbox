/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuardedQuadraticRootSourceTest {
    @TempDir Path temporary;
    static final String ORIGINAL = """
        import java.math.BigInteger;
        class Ocean {
            static final BigInteger Q = BigInteger.valueOf(19);
            static final BigInteger Slice = BigInteger.TWO.modInverse(Q);
            static final BigInteger Grade = Q.add(BigInteger.ONE).shiftRight(2);
            static long pack(BigInteger left, BigInteger right) {
                return left.longValueExact()*1000L + right.longValueExact();
            }
            static long recover(BigInteger amber, BigInteger cobalt, BigInteger grey) {
                BigInteger field = Q;
                if (cobalt.signum() == 0) {
                    return pack(amber, cobalt);
                }
                BigInteger seed = amber.add(grey).multiply(Slice).mod(field);
                BigInteger u = seed.modPow(Grade, field);
                if (!u.multiply(u).mod(field).equals(seed)) {
                    seed = amber.subtract(grey).multiply(Slice).mod(field);
                    u = seed.modPow(Grade, field);
                }
                BigInteger v = cobalt.multiply(u.shiftLeft(1).modInverse(field)).mod(field);
                return pack(u, v);
            }
        }
        """;

    static MathematicalAnalysis.Analysis analyze(String source, long budget) {
        var compiler = JavaCore.getOptions();
        JavaCore.setComplianceOptions("17", compiler);
        var ast = parse(source, compiler);
        var options = options(budget);
        return MathematicalAnalysis.analyze(ast, source, options, null, source.indexOf("seed ="), 1,
                new MathematicalAnalysis.SourceEnvironment("Ocean.java", List.of(), List.of(), List.of(), compiler));
    }

    private static CompilationUnit parse(String source, Map<String,String> compiler) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setUnitName("Ocean.java");
        parser.setCompilerOptions(compiler);
        parser.setResolveBindings(true);
        parser.setSource(source.toCharArray());
        var ast = (CompilationUnit) parser.createAST(null);
        assertFalse(Arrays.stream(ast.getProblems()).anyMatch(p -> p.isError()), () -> Arrays.toString(ast.getProblems()));
        return ast;
    }

    private static MathCleanUpOptions options(long budget) {
        return MathCleanUpOptions.parse(Map.of(MathCleanUpOptions.CLEANUP, "true",
                MathCleanUpOptions.WORK_BUDGET, Long.toString(budget), MathCleanUpOptions.MAX_STATES, "20000",
                MathCleanUpOptions.TIMEOUT_SECONDS, "30"), 17);
    }

    @Test void discoversTheFieldProgramFromRenamedJava() throws Exception {
        var result = analyze(ORIGINAL, 100_000_000);
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("VERIFIED_QUADRATIC_ROOT")),
                () -> result.diagnostics().toString());
        var document = new Document(ORIGINAL);
        result.newEdit().apply(document);
        String generated = document.get();
        assertTrue(generated.contains(ORIGINAL.substring(ORIGINAL.indexOf("BigInteger seed"))));
        assertFalse(analyze(generated, 100_000_000).changed());
    }

    @Test void bindsInputRolesAcrossParameterAndSourceSyntaxChanges() {
        String changed = ORIGINAL.replace("class Ocean", "class Ridge")
                .replace("BigInteger amber, BigInteger cobalt, BigInteger grey", "BigInteger grey, BigInteger cobalt, BigInteger amber")
                .replace("amber.add(grey)", "(amber.add(grey))")
                .replace("!u.multiply(u).mod(field).equals(seed)", "!seed.equals(u.multiply(u).mod(field))")
                .replace("pack(", "combine(");
        var result = analyze(changed, 100_000_000);
        assertTrue(result.evidence().stream().anyMatch(r -> r.fieldRoot() != null), () -> result.diagnostics().toString());
    }

    @Test void independentlyDecodesJavaRegistersAndRejectsChangedGuardsSignsAndFallbacks() throws Exception {
        var analysis = analyze(ORIGINAL, 100_000_000);
        var region = analysis.evidence().stream().filter(r -> r.fieldRoot() != null).findFirst().orElseThrow();
        var document = new Document(ORIGINAL);
        analysis.newEdit().apply(document);
        String generated = document.get();
        var proof = region.fieldRoot();
        String root = proof.prefix();
        var arm = proof.certificate().program().equal();
        String x = root + arm.real(), y = root + arm.imaginary();
        for (String changed : List.of(generated.replace("grey.multiply(grey)", "grey.multiply(amber)"),
                generated.replace(root + "Sign < 0", root + "Sign > 0"),
                generated.replace("getLowestSetBit()", "bitCount()"),
                generated.replace(".testBit(1)", ".testBit(0)"),
                generated.replace("return pack(" + x + ", " + y + ")", "return pack(" + y + ", " + x + ")"),
                generated.replace(".add(grey).mod(", ".subtract(grey).mod("),
                generated.replace("cobalt.multiply(u.shiftLeft(1)", "cobalt.multiply(u.shiftLeft(2)"))) {
            assertNotEquals(generated, changed);
            var ast = parse(changed, analysis.compilerOptions());
            var options = options(100_000_000);
            var work = new JavaValueSourceSummary.Work(options.workBudget(), options, () -> false);
            var method = (MethodDeclaration)ast.findDeclaringNode(proof.methodKey());
            try (var ctx = JavaSourceContext.open(ast, changed, null, null, options)) {
                JavaSourceContext.valueWork(ast, work);
                boolean accepted;
                try {
                    var site = new JavaGuardedFieldSource(ast, changed, method, true, proof.swappedInputs(), work);
                    var actual = new JavaFieldProgramLowering(site, root, proof.indent())
                            .decode((IfStatement)method.getBody().statements().get(2));
                    accepted = site.original.equals(proof.certificate().contract().original())
                            && actual.equals(proof.certificate().program());
                } catch (IllegalArgumentException rejected) { accepted = false; }
                assertFalse(accepted);
                assertTrue(ctx.checkpoint().current());
            }
        }
    }

    @Test void replaysTheActualTryCallFallbackAndRemainingGuards() throws Exception {
        String source = ORIGINAL.replace("static long pack", "static long[] pack")
                .replace("left.longValueExact()*1000L + right.longValueExact()", "new long[]{left.longValueExact(), right.longValueExact()}")
                .replace("static long recover", """
                        static void send(byte[] ar, byte[] br, BigInteger g, long[][] out) {
                            BigInteger a = new BigInteger(1, ar);
                            BigInteger b = new BigInteger(1, br);
                            out[0] = recover(a, b, g);
                        }
                        static long[] recover""");
        var compiler = JavaCore.getOptions();
        JavaCore.setComplianceOptions("17", compiler);
        var options = options(100_000_000);
        var analysis = MathematicalAnalysis.analyze(parse(source, compiler), source, options, null, source.indexOf("out[0] ="), 1,
                new MathematicalAnalysis.SourceEnvironment("Ocean.java", List.of(), List.of(), List.of(), compiler));
        var region = analysis.evidence().stream().filter(r -> r.fieldRoot() != null && r.fieldRoot().callSite() != null).findFirst().orElseThrow();
        var proof = region.fieldRoot();
        var document = new Document(source);
        analysis.newEdit().apply(document);
        String output = document.get(), helper = proof.callSite().helperName();
        for (var changed : List.of(output.replace(helper + "(a, b, g)", helper + "(b, a, g)"),
                output.replace(": recover(a, b, g)", ": recover(b, a, g)"),
                output.replace("cobalt.signum() > 0", "cobalt.signum() < 0"),
                output.replace("grey.getClass()", "amber.getClass()"), output.replace("new BigInteger(1, ar)", "new BigInteger(1, br)"))) {
            assertNotEquals(output, changed);
            var ast = parse(changed, compiler);
            var method = (MethodDeclaration)ast.findDeclaringNode(proof.methodKey());
            var work = new JavaValueSourceSummary.Work(options.workBudget(), options, () -> false);
            try (var context = JavaSourceContext.open(ast, changed, null, null, options)) {
                JavaSourceContext.valueWork(ast, work);
                var root = new JavaGuardedFieldSource(ast, changed, method, false, proof.swappedInputs(), work);
                assertThrows(IllegalArgumentException.class, () -> JavaFieldCallOptimizer.replay(root, region));
                assertTrue(context.checkpoint().current());
            }
        }
    }

    @Test void comparesBothCoordinatesAndExceptionTypesAcrossEverySmallPrimeTriple() throws Exception {
        for (int prime : new int[]{7, 11, 19, 23}) {
            String original = ORIGINAL.replace("valueOf(19)", "valueOf(" + prime + ")")
                    .replace("static final BigInteger Grade", """
                            static final String early = beforeExponent();
                            static String beforeExponent() {
                                try { return Long.toString(recover(BigInteger.ZERO, BigInteger.TWO, BigInteger.TWO)); }
                                catch (RuntimeException e) { return e.getClass().getName(); }
                            }
                            static final BigInteger Grade""");
            var result = analyze(original, 100_000_000);
            assertTrue(result.evidence().stream().anyMatch(r -> r.fieldRoot() != null), () -> result.diagnostics().toString());
            var document = new Document(original);
            result.newEdit().apply(document);
            try (var first = compile(original, "original" + prime); var second = compile(document.get(), "changed" + prime)) {
                var before = recover(first);
                var after = recover(second);
                assertEquals(NullPointerException.class.getName(), early(before));
                assertEquals(early(before), early(after));
                for (int a = -1; a <= prime; a++) for (int b = -1; b <= prime; b++) for (int g = -1; g <= prime; g++) {
                    Object[] input = {BigInteger.valueOf(a), BigInteger.valueOf(b), BigInteger.valueOf(g)};
                    assertEquals(observe(before, input), observe(after, input), () -> Arrays.toString(input));
                }
                for (int i = 0; i < 3; i++) {
                    Object[] input = {BigInteger.ONE, BigInteger.ONE, BigInteger.TWO};
                    input[i] = null;
                    assertEquals(observe(before, input), observe(after, input));
                    var counting = new CountingInteger();
                    input[i] = counting;
                    Object expected = observe(before, input);
                    String effects = counting.log.toString();
                    counting.log.setLength(0);
                    assertEquals(expected, observe(after, input));
                    assertEquals(effects, counting.log.toString());
                }
            }
        }
    }

    @Test void preservesExceptionValuesWhileCallerLocationsRemainDiagnosticMetadata() throws Exception {
        String original = ORIGINAL.replace("return left.longValueExact()",
                        "if (left.signum() > 0) throw new IllegalArgumentException(\"coordinate\"); return left.longValueExact()")
                .replace("static long recover", """
                        static String[] observeFailure() {
                            try {
                                recover(BigInteger.ZERO, BigInteger.TWO, BigInteger.TWO);
                                throw new AssertionError("expected factory failure");
                            } catch (IllegalArgumentException failure) {
                                return new String[]{failure.getClass().getName(), failure.getMessage(),
                                        Integer.toString(failure.getStackTrace()[1].getLineNumber())};
                            }
                        }
                        static long recover""");
        var analysis = analyze(original, 100_000_000);
        assertTrue(analysis.evidence().stream().anyMatch(r -> r.fieldRoot() != null), () -> analysis.diagnostics().toString());
        var document = new Document(original);
        analysis.newEdit().apply(document);
        try (var first = compile(original, "exception-original"); var second = compile(document.get(), "exception-changed")) {
            var before = first.loadClass("Ocean").getDeclaredMethod("observeFailure");
            var after = second.loadClass("Ocean").getDeclaredMethod("observeFailure");
            before.setAccessible(true);
            after.setAccessible(true);
            var expected = (String[])before.invoke(null);
            var actual = (String[])after.invoke(null);
            assertArrayEquals(new String[]{IllegalArgumentException.class.getName(), "coordinate"}, Arrays.copyOf(expected, 2));
            assertArrayEquals(Arrays.copyOf(expected, 2), Arrays.copyOf(actual, 2));
            // Both unchanged catchers can inspect a source location; its identity is outside this proof.
            assertTrue(Integer.parseInt(expected[2]) > 0);
            assertTrue(Integer.parseInt(actual[2]) > 0);
        }
    }

    private URLClassLoader compile(String source, String name) throws Exception {
        Path folder = Files.createDirectories(temporary.resolve(name));
        Path file = Files.writeString(folder.resolve("Ocean.java"), source, StandardCharsets.UTF_8);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17",
                "-Xlint:all", "-Werror", "-d", folder.toString(), file.toString()));
        return new URLClassLoader(new URL[]{folder.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    private static Method recover(ClassLoader loader) throws Exception {
        var method = loader.loadClass("Ocean").getDeclaredMethod("recover", BigInteger.class, BigInteger.class, BigInteger.class);
        method.setAccessible(true);
        return method;
    }
    private static Object early(Method method) throws Exception {
        var field = method.getDeclaringClass().getDeclaredField("early");
        field.setAccessible(true);
        return field.get(null);
    }
    private static Object observe(Method method, Object[] input) throws IllegalAccessException {
        try { return method.invoke(null, input); }
        catch (InvocationTargetException thrown) { return thrown.getCause().getClass(); }
    }
    private static final class CountingInteger extends BigInteger {
        private static final long serialVersionUID = 1;
        final transient StringBuilder log = new StringBuilder();
        CountingInteger() { super("1"); }
        @Override public int signum() { log.append("signum "); return super.signum(); }
        @Override public BigInteger add(BigInteger right) { log.append("add "); return super.add(right); }
        @Override public BigInteger multiply(BigInteger right) { log.append("multiply "); return super.multiply(right); }
    }

    @Test void checksTheReturnedProductsConstructorAndPrivateArrayOwnership() {
        String product = ORIGINAL.replace("static long pack", """
                        static final class Product {
                            final long value;
                            Product(long x) { this.value = x; }
                        }
                        static long number(byte[] packed, int[] scratch) { scratch[0] = packed[0]; return scratch[0]; }
                        static Product pack""")
                .replace("static long recover", "static Product recover")
                .replace("return left.longValueExact()*1000L + right.longValueExact();",
                        "return new Product(number(left.toByteArray(), new int[1]) * 1000L + right.longValueExact());");
        var good = analyze(product, 100_000_000);
        assertTrue(good.evidence().stream().anyMatch(r -> r.fieldRoot() != null), () -> good.diagnostics().toString());
        for (String changed : List.of(
                product.replace("this.value = x;", "this.value = x + new Throwable().getStackTrace()[1].getLineNumber();"),
                product.replace("static long number", "private static final int[] state = {0}; static long number")
                        .replace("number(left.toByteArray(), new int[1])", "number(left.toByteArray(), state)"))) {
            assertTrue(analyze(changed, 100_000_000).evidence().stream().noneMatch(r -> r.fieldRoot() != null));
        }
    }

    @Test void rejectsCallerObservationAndExternallyVisibleEffectsAnywhereInTheFactory() {
        String stack = "new Throwable().getStackTrace()[1].getLineNumber()";
        String store = "static long calls; static long pack";
        for (String source : List.of(
                ORIGINAL.replace("return left.longValueExact()", "long line = " + stack + "; return line * 1000000L + left.longValueExact()"),
                ORIGINAL.replace("static long pack", "static int context() { return " + stack + "; } static long pack")
                        .replace("+ right.longValueExact()", "+ right.longValueExact() + context()"),
                ORIGINAL.replace("static long pack", store).replace("return left.longValueExact()", "calls++; return left.longValueExact()"),
                ORIGINAL.replace("static long pack", "static int[] shared = {0}; static long pack")
                        .replace("return left.longValueExact()", "shared[0]++; return left.longValueExact()"),
                ORIGINAL.replace("static long pack", "static class Product { long value; Product(long x) { value = x + " + stack + "; } } static long pack")
                        .replace("return left.longValueExact()*1000L + right.longValueExact();", "return new Product(left.longValueExact() + right.longValueExact()).value;"),
                ORIGINAL.replace("static long pack", "static class Cold { static int value = " + stack + "; static int read() { return value; } } static long pack")
                        .replace("+ right.longValueExact()", "+ right.longValueExact() + Cold.read()"))) {
            assertTrue(analyze(source, 100_000_000).evidence().stream().noneMatch(r -> r.fieldRoot() != null), source);
        }
    }

    @Test void rejectsMutatedArithmeticAndObservableCoordinates() {
        for (String mutated : List.of(ORIGINAL.replace("shiftLeft(1)", "shiftLeft(2)"),
                ORIGINAL.replace("!u.multiply", "u.multiply"), ORIGINAL.replace("subtract(grey)", "add(grey)"),
                ORIGINAL.replace("shiftRight(2)", "shiftRight(1)"), ORIGINAL.replace("TWO.modInverse(Q)", "ONE.modInverse(Q)"),
                ORIGINAL.replace("return left.longValueExact()", "if (left == right) throw new Error(); return left.longValueExact()"),
                ORIGINAL.replace("seed = amber.subtract", "System.nanoTime(); seed = amber.subtract"),
                ORIGINAL.replace("static final BigInteger Slice", "static BigInteger Slice"),
                ORIGINAL.replace("static long pack", "static BigInteger saved; static long pack")
                        .replace("return left.longValueExact()", "saved = left; return left.longValueExact()"),
                ORIGINAL.replace("return left.longValueExact()", "synchronized (left) { System.nanoTime(); } return left.longValueExact()"),
                ORIGINAL.replace("BigInteger v =", "BigInteger unused = cobalt.add(amber); BigInteger v ="),
                ORIGINAL.replace("cobalt.signum() == 0", "amber.signum() == 0"),
                ORIGINAL.replace("u.multiply(u).mod(field).equals(seed)", "u.multiply(u).equals(seed)"),
                ORIGINAL.replace("return pack(u, v)", "return pack(v, u)"),
                ORIGINAL.replace("return left.longValueExact()", "BigInteger alias = left; if (alias == right) throw new Error(); return left.longValueExact()"))) {
            assertFalse(analyze(mutated, 100_000_000).diagnostics().stream().anyMatch(d -> d.code().equals("VERIFIED_QUADRATIC_ROOT")));
        }
        assertFalse(analyze(ORIGINAL, 25).diagnostics().stream().anyMatch(d -> d.code().equals("VERIFIED_QUADRATIC_ROOT")));
    }
}
