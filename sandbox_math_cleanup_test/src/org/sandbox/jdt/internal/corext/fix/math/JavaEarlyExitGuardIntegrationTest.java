/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

class JavaEarlyExitGuardIntegrationTest {
    private static final String TARGET = "return x.modPow(BigInteger.TWO, p);";
    private static final String INVALID = "x == null || x.getClass() != BigInteger.class || x.bitLength() >= 4096"
            + " || p == null || p.getClass() != BigInteger.class || p.bitLength() >= 4096 || p.signum() <= 0";
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void actualGeneratedMethodsRetainFallbackExceptionsAndCallCounts(boolean throwing) throws Exception {
        String source = source(INVALID, throwing);
        var analysis = analyze(source, true);
        assertEquals(1, analysis.replacements().size(), analysis.diagnostics().toString());
        String replacement = analysis.replacements().getFirst().replacement();
        assertTrue(replacement.contains(".multiply("), replacement);
        assertFalse(replacement.contains("getClass()"), replacement);
        assertFalse(replacement.contains("if ("), replacement);
        assertTrue(analysis.evidence().getFirst().explanation().guardedReceivers().isEmpty());
        var document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        JavaEarlyExitGuardFactsTest.parse(generated);
        undo.apply(document); assertEquals(source, document.get());
        String prefix = source.substring(source.indexOf("visits++;"), source.lastIndexOf(TARGET));
        int generatedStart = generated.indexOf("visits++;");
        assertTrue(generatedStart >= 0);
        assertEquals(prefix, generated.substring(generatedStart, generatedStart + prefix.length()),
                "Guard, early return/throw and original side effect must stay byte-exact; method Javadoc is separate");
        try (var original = compile(source, "before"); var optimized = compile(generated, "after")) {
            Class<?> before = original.loadClass("Sample"), after = optimized.loadClass("Sample");
            BigInteger[] inputs = {null, BigInteger.ZERO, BigInteger.ONE, BigInteger.valueOf(-3),
                    BigInteger.ONE.shiftLeft(4094), BigInteger.ONE.shiftLeft(4095), BigInteger.ONE.shiftLeft(5000)};
            BigInteger[] moduli = {null, BigInteger.ZERO, BigInteger.ONE, BigInteger.valueOf(-1), BigInteger.valueOf(17)};
            int comparisons = 0;
            for (var value : inputs) for (var modulus : moduli) {
                assertEquals(outcome(before, value, modulus), outcome(after, value, modulus)); comparisons++;
            }
            BigInteger subclass = new BigInteger("3") {
                private static final long serialVersionUID = 1L;
                @Override public BigInteger modPow(BigInteger exponent, BigInteger modulus) { return BigInteger.valueOf(12345); }
            };
            assertEquals(outcome(before, subclass, BigInteger.valueOf(17)), outcome(after, subclass, BigInteger.valueOf(17)));
            System.out.println("EARLY_GUARD_DIFFERENTIAL throwing=" + throwing + " comparisons=" + (comparisons + 1));
        }
    }
    @Test void aMissingSignFactRetainsTheCompleteModulusGuard() {
        String source = source(INVALID.replace(" || p.signum() <= 0", ""), false);
        var analysis = analyze(source, true);
        assertEquals(1, analysis.replacements().size(), analysis.diagnostics().toString());
        String replacement = analysis.replacements().getFirst().replacement();
        assertTrue(replacement.contains("p.signum() > 0"), replacement);
        assertTrue(replacement.contains("p.getClass()"), replacement);
        assertFalse(replacement.contains("x.getClass()"), replacement);
    }
    @Test void strictModeDoesNotAcquireIdentityChangingPermission() {
        assertFalse(analyze(source(INVALID, false), false).changed());
    }
    private static String source(String condition, boolean throwing) {
        return "import java.math.BigInteger; public class Sample { public static int visits;"
                + " public static BigInteger f(BigInteger x, BigInteger p) { visits++; if (" + condition + ") "
                + (throwing ? "throw new IllegalArgumentException(\"guard\");" : "return x.modPow(BigInteger.valueOf(2), p);")
                + "\n" + TARGET + " }}";
    }
    private static MathematicalAnalysis.Analysis analyze(String source, boolean mathematical) {
        var options = new MathCleanUpOptions(true, EnumSet.of(NumericKind.BIG_INTEGER, NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of(), mathematical);
        return MathematicalAnalysis.analyze(JavaEarlyExitGuardFactsTest.parse(source), source, options,
                new NullProgressMonitor(), source.lastIndexOf(TARGET), TARGET.length());
    }
    private URLClassLoader compile(String source, String name) throws Exception {
        Path directory = Files.createDirectory(temporary.resolve(name)), file = directory.resolve("Sample.java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler(); assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, "--release", "17", "-d", directory.toString(), file.toString()));
        return new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
    private static Object outcome(Class<?> type, BigInteger value, BigInteger modulus) throws Exception {
        type.getField("visits").setInt(null, 0);
        Object result;
        try { result = type.getMethod("f", BigInteger.class, BigInteger.class).invoke(null, value, modulus); }
        catch (InvocationTargetException failure) {
            var cause = failure.getCause(); result = cause.getClass().getName() + ":" + cause.getMessage();
        }
        assertEquals(1, type.getField("visits").getInt(null));
        return result;
    }
}
