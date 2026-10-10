/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLClassLoader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

class MathematicalContractTest {
    @TempDir Path temporary;
    @Test void runtimeGoalDoesNotAcceptStorageOnlyReordering() {
        String source = "class Calculation { long f(int t) { return t * 19; }}";
        assertFalse(analyze(source, false).changed());
    }

    @Test void mathematicalModeRetainsJavaSourceSharingImprovements() {
        // Bouncy Castle Hnf.hnf4xnModCore repeats this calculation for two live locals.
        String source = "class Calculation { void use(int a,int b) {} void f(int n) { int j=n-1; int k=n-1; use(j,k); }}";
        assertTrue(analyze(source, false).changed());
        assertTrue(analyze(source, true).changed());
    }

    @Test void mathematicalModeExpandsTheRangeAndDocumentsTheChangedContract() throws Exception {
        String source = "class Calculation { int f(int x) { return 2 * x / 2; }}";
        assertFalse(analyze(source, false).changed());
        var result = analyze(source, true);
        assertTrue(result.changed(), result.diagnostics().toString());
        Document document = new Document(source);
        var undo = result.newEdit().apply(document, org.eclipse.text.edits.TextEdit.CREATE_UNDO);
        String generated = document.get();
        assertTrue(generated.contains("return x;"), generated);
        assertTrue(generated.contains("-1073741824"), generated);
        assertTrue(generated.contains("1073741823"), generated);
        assertTrue(generated.contains("-2147483648"), generated);
        assertTrue(generated.contains("2147483647"), generated);
        assertTrue(generated.contains("/**"), generated);
        assertFalse(analyze(generated, true).changed());
        MathTestSupport.parse(generated);
        undo.apply(document);
        assertEquals(source, document.get());
    }

    @Test void unknownDivisorDoesNotAcquireAnInventedDomain() {
        String source = "class Calculation { int f(int x) { return x / x; }}";
        assertFalse(analyze(source, true).changed());
    }

    @Test void bigIntegerValueModeAllowsReturnedValuesWithCheckedReceivers() throws Exception {
        String source = "import java.math.BigInteger; class Calculation { BigInteger f(BigInteger x) { return x.add(BigInteger.ONE).subtract(BigInteger.ONE); }}";
        assertFalse(analyze(source, false).changed());
        var result = analyze(source, true);
        assertTrue(result.changed(), result.diagnostics().toString());
        Document document = new Document(source);
        result.newEdit().apply(document);
        assertTrue(document.get().contains("getClass()"), document.get());
        assertTrue(document.get().contains("reference identity"), document.get());
        MathTestSupport.parse(document.get());
    }

    @Test void unsupportedNestedModulusIsDiagnosedRatherThanInventingAProof() {
        String source = "import java.math.BigInteger; class Calculation { BigInteger f(BigInteger x, BigInteger m) { return x.mod(m).mod(m); }}";
        var result = analyze(source, true);
        assertFalse(result.changed());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("NOIMPROVEMENT")));
    }

    @Test void documentedIntBoundariesAndTheExtensionExecuteAsPromised() throws Exception {
        String original = "public class Calculation { public static int compute(int x) { return 2*x/2; }}";
        Method before = compile(original, int.class), after = compile(rewrite(original), int.class);
        for (int x : new int[] {-1073741824, -1, 0, 1, 1073741823}) {
            assertEquals(x, before.invoke(null, x));
            assertEquals(x, after.invoke(null, x));
        }
        for (int x : new int[] {Integer.MIN_VALUE, -1073741825, 1073741824, Integer.MAX_VALUE}) {
            assertNotEquals(x, before.invoke(null, x));
            assertEquals(x, after.invoke(null, x));
        }
    }

    @Test void longNegativeFactorAndExistingJavadocKeepExactBounds() throws Exception {
        String original = "public class Calculation { /** User documentation. @param x input */ public static long compute(long x) { return -2L*x/-2L; }}";
        String generated = rewrite(original);
        assertTrue(generated.contains("User documentation."), generated);
        assertEquals(1, generated.split("/\\*\\*", -1).length - 1, generated);
        assertTrue(generated.contains("[-4611686018427387903, 4611686018427387904]"), generated);
        Method after = compile(generated, long.class);
        assertEquals(Long.MIN_VALUE, after.invoke(null, Long.MIN_VALUE));
        assertEquals(Long.MAX_VALUE, after.invoke(null, Long.MAX_VALUE));
    }

    @Test void bigIntegerFallbackPreservesNullNegativeHugeAndSubclassBehavior() throws Exception {
        String original = "public class Calculation { public static java.math.BigInteger compute(java.math.BigInteger x) { return x.add(java.math.BigInteger.ONE).subtract(java.math.BigInteger.ONE); }}";
        String generated = rewrite(original);
        assertFalse(analyze(generated, true).changed(), generated);
        Method before = compile(original, BigInteger.class), after = compile(generated, BigInteger.class);
        for (BigInteger x : new BigInteger[] {BigInteger.ZERO, BigInteger.ONE, BigInteger.valueOf(-19), BigInteger.ONE.shiftLeft(5000),
                new BigInteger("3") { private static final long serialVersionUID = 1L;
                    @Override public BigInteger add(BigInteger other) { return BigInteger.valueOf(100); }
                }}) {
            assertEquals(before.invoke(null, x), after.invoke(null, x));
        }
        assertInstanceOf(NullPointerException.class,
                assertThrows(InvocationTargetException.class, () -> after.invoke(null, new Object[] {null})).getCause());
    }

    @Test void optInRoundTripsAndCannotSilentlyOverrideCheckedMode() {
        var values = new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        assertFalse(MathCleanUpOptions.defaults(17).mathematicalOptIn());
        values.put(MathCleanUpOptions.MATHEMATICAL_OPT_IN, "true");
        var options = MathCleanUpOptions.parse(values, 17);
        assertTrue(options.mathematicalOptIn());
        assertEquals(options, MathCleanUpOptions.parse(options.toMap(), 17));
        values.put(MathCleanUpOptions.SAFETY, "CHECKED_THROW");
        values.put(MathCleanUpOptions.CHECKED_OPT_IN, "true");
        assertThrows(IllegalArgumentException.class, () -> MathCleanUpOptions.parse(values, 17));
    }

    @Test void receiverChecksMustNotEraseAnAllocationGoalBenefit() {
        // BIP340Signer.sign's arithmetic shape: ordering alone cannot justify four guards.
        String source = "import java.math.BigInteger; class Calculation { BigInteger f(BigInteger k, BigInteger e, BigInteger d, BigInteger n) { return k.add(e.multiply(d)).mod(n); }}";
        var result = analyze(source, true, "LOWER_ALLOCATION");
        assertFalse(result.changed(), result.diagnostics().toString());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("GUARD_COST_EXCEEDS_BENEFIT")), result.diagnostics().toString());
    }

    private static String rewrite(String source) throws Exception {
        var result = analyze(source, true);
        assertTrue(result.changed(), result.diagnostics().toString());
        Document document = new Document(source);
        result.newEdit().apply(document);
        return document.get();
    }

    private Method compile(String source, Class<?> parameter) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "compiled-");
        Path file = directory.resolve("Calculation.java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-d", directory.toString(), file.toString()));
        try (var loader = new URLClassLoader(new java.net.URL[] {directory.toUri().toURL()}, null)) {
            return loader.loadClass("Calculation").getMethod("compute", parameter);
        }
    }

    private static MathematicalAnalysis.Analysis analyze(String source, boolean mathematical) {
        return analyze(source, mathematical, "LOWER_ESTIMATED_RUNTIME");
    }

    private static MathematicalAnalysis.Analysis analyze(String source, boolean mathematical, String goal) {
        var values = new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        values.put(MathCleanUpOptions.CLEANUP, "true");
        values.put(MathCleanUpOptions.KINDS, "INT,LONG,BIG_INTEGER");
        values.put(MathCleanUpOptions.WORK_BUDGET, "1000000");
        values.put(MathCleanUpOptions.MAX_STATES, "20000");
        values.put(MathCleanUpOptions.GOAL, goal);
        if (mathematical) values.put("cleanup.mathematics.mathematicalOptIn", "true");
        return MathematicalAnalysis.analyze(MathTestSupport.parse(source), source,
                MathCleanUpOptions.parse(values, 17), new NullProgressMonitor(), -1, 0);
    }
}
