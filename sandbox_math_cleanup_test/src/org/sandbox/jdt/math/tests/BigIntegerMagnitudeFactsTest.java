/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.sdk.optimization.SemanticAssumption;
import java.util.HashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationRegion;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;

class BigIntegerMagnitudeFactsTest {
    @Test void fixedByteArrayBoundsRemoveOnlyTheProvedReceiverGuard() {
        for (String constructor : new String[] {"new BigInteger(1, magnitude)", "new BigInteger(-1, magnitude)", "new BigInteger(magnitude)"}) {
            var region = region("byte[] magnitude = new byte[48]; magnitude[0] = 42;", constructor);
            assertEquals(Set.of("modulus"), region.receiverGuards());
            assertBound(region, "384");
        }
    }

    @Test void directAllocationsSupportTheBoundaryWithoutClaimingPositivity() {
        assertBound(region("", "new BigInteger(-1, new byte[] {(byte) 255})"), "8");
        var largest = region("", "new BigInteger(new byte[512])");
        assertEquals(Set.of("modulus"), largest.receiverGuards());
        assertBound(largest, "4096");
    }

    @Test void arrayInitializerLengthBoundsTheCapturedValueAfterContentMutation() {
        var region = region("byte[] magnitude = {1, 2, 3}; byte[] alias = magnitude; alias[0] = -1;",
                "new BigInteger(1, magnitude)");
        assertEquals(Set.of("modulus"), region.receiverGuards());
        assertBound(region, "24");
    }

    @Test void unprovedArrayLengthsRetainTheReceiverGuard() {
        for (String preparation : new String[] {
                "byte[] magnitude = new byte[48]; magnitude = new byte[49];",
                "byte[] magnitude = incoming;",
                "byte[] magnitude = new byte[513];"}) {
            var region = region(preparation, "new BigInteger(1, magnitude)");
            assertEquals(Set.of("x", "modulus"), region.receiverGuards());
            assertBound(region, "4096");
        }
    }

    @Test void unrelatedConstructorOverloadsRetainTheReceiverGuard() {
        for (String constructor : new String[] {"new BigInteger(384, new java.util.Random(1))",
                "new BigInteger(1, new byte[48], 0, 48)", "new BigInteger(new byte[48]) {}",
                "new BigInteger(new byte[javax.crypto.Cipher.ENCRYPT_MODE])", "new BigInteger(new byte[-1])"}) {
            var region = region("", constructor);
            assertEquals(Set.of("x", "modulus"), region.receiverGuards());
            assertBound(region, "4096");
        }
    }

    private static void assertBound(JavaComputationRegion region, String expected) {
        String input = region.inputNames().entrySet().stream().filter(e -> e.getValue().equals("x"))
                .findFirst().orElseThrow().getKey();
        assertTrue(region.assumptions().stream().noneMatch(a -> a.subject().equals(input)
                && (a.kind() == SemanticAssumption.Kind.POSITIVE || a.kind() == SemanticAssumption.Kind.NON_NEGATIVE)),
                "A magnitude bound does not establish the sign of a byte-array value");
        assertEquals(expected, region.assumptions().stream()
                .filter(a -> a.kind() == SemanticAssumption.Kind.BIG_INTEGER_BIT_LENGTH_BOUND && a.subject().equals(input))
                .findFirst().orElseThrow().parameter());
    }

    private static JavaComputationRegion region(String preparation, String constructor) {
        String source = "import java.math.BigInteger; public class Calculation {"
                + "public BigInteger compute(byte[] incoming, BigInteger modulus) {"
                + preparation + "BigInteger x = " + constructor + "; return x.multiply(x).mod(modulus); }}";
        var values = new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        values.put(MathCleanUpOptions.CLEANUP, "true");
        values.put(MathCleanUpOptions.KINDS, "BIG_INTEGER");
        values.put(MathCleanUpOptions.MATHEMATICAL_OPT_IN, "true");
        var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source,
                MathCleanUpOptions.parse(values, 17));
        assertEquals(1, extraction.regions().size(), extraction.diagnostics().toString());
        return extraction.regions().getFirst();
    }
}
