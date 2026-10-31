/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Execute actual Apply output; the probe is added only to the separately compiled execution fixture. */
class JavaRuntimePrimitiveGuardTest extends JavaContextTestSupport {
    record Case(String body, boolean descending, boolean inclusive) { }
    static Stream<Case> primitiveCases() {
        return Stream.of(
                new Case("for(int i=a;i<b;i++)sum+=i*i;", false, false),
                new Case("for(int i=a;i<=b;i++)sum+=i*i;", false, true),
                new Case("for(int i=a;i>b;i--)sum+=i*i;", true, false),
                new Case("for(int i=a;i>=b;i--)sum+=i*i;", true, true),
                new Case("int i=a;while(i<b){sum+=i*i;i++;}sum+=i;", false, false),
                new Case("int i=a;do{sum+=i*i;i++;}while(i<=b);sum+=i;", false, true),
                new Case("int i=a;do{sum+=i*i;i--;}while(i>=b);sum+=i;", true, true),
                new Case("for(int i=a;i<b;i++)sum=3*sum+i;", false, false),
                new Case("for(int i=a;i<b;i++)sum*=3;", false, false),
                new Case("int power=1;for(int i=a;i<b;i++){sum+=power;power*=3;}sum+=power;", false, false),
                new Case("sum=java.util.stream.IntStream.range(a,b).map(i->i*i).sum();", false, false),
                new Case("sum=java.util.stream.IntStream.rangeClosed(a,b).map(i->i*i).sum();", false, true));
    }
    private static String source(String body) {
        return "public class Sample {public static int run(int a,int b){int sum=17;" + body + "return sum;}}";
    }
    private static long threshold(String source) {
        var proofs = analyze(source).evidence().stream().filter(e -> e.runtimeIteration() != null).toList();
        assertEquals(1, proofs.size()); return proofs.getFirst().runtimeIteration().minimumIterations();
    }
    private static String instrument(String generated) {
        assertTrue(generated.contains("java.math.BigInteger.valueOf("));
        return generated.replace("java.math.BigInteger.valueOf(", "Probe.valueOf(")
                + "\nclass Probe {static java.math.BigInteger valueOf(long n){throw new AssertionError(\"BigInteger prelude reached\");}}";
    }
    private static Method run(ClassLoader loader) throws Exception {
        return loader.loadClass("Sample").getMethod("run", int.class, int.class);
    }
    private static void reached(Method probe, int a, int b) {
        var failure = assertThrows(InvocationTargetException.class, () -> probe.invoke(null, a, b));
        assertInstanceOf(AssertionError.class, failure.getCause());
        assertEquals("BigInteger prelude reached", failure.getCause().getMessage());
    }

    @ParameterizedTest @MethodSource("primitiveCases")
    void smallRangesSkipBigIntegerAndLargeRangesRetainItsGuards(Case fixture) throws Exception {
        String original = source(fixture.body()), generated = changed(original);
        int boundary = Math.toIntExact(threshold(original)) - (fixture.inclusive() ? 1 : 0);
        try (var before = compile(original); var after = compile(generated); var observed = compile(instrument(generated))) {
            Method reference = run(before), actual = run(after), probe = run(observed);
            for (int distance : new int[] {-17, 0, 1, 16, boundary - 1}) {
                int bound = fixture.descending() ? -distance : distance;
                Object expected = reference.invoke(null, 0, bound);
                assertEquals(expected, actual.invoke(null, 0, bound));
                assertEquals(expected, probe.invoke(null, 0, bound), "small distance=" + distance);
            }
            for (int distance : new int[] {boundary, boundary + 1, 50000}) {
                int bound = fixture.descending() ? -distance : distance;
                assertEquals(reference.invoke(null, 0, bound), actual.invoke(null, 0, bound));
                reached(probe, 0, bound);
            }
            // Every original terminates, including do loops. Negative distances can exceed signed-int range.
            int[][] extremes = fixture.descending()
                    ? new int[][] {{Integer.MAX_VALUE, Integer.MAX_VALUE - 16}, {Integer.MIN_VALUE + 17, Integer.MIN_VALUE + 1},
                            {Integer.MIN_VALUE + 1, 17}, {-2, Integer.MAX_VALUE}}
                    : new int[][] {{Integer.MIN_VALUE, Integer.MIN_VALUE + 16}, {Integer.MAX_VALUE - 17, Integer.MAX_VALUE - 1},
                            {Integer.MAX_VALUE - 1, -17}, {1, Integer.MIN_VALUE}};
            for (int[] input : extremes) {
                Object expected = reference.invoke(null, input[0], input[1]);
                assertEquals(expected, actual.invoke(null, input[0], input[1]));
                assertEquals(expected, probe.invoke(null, input[0], input[1]));
            }
        }
    }

    @Test void headerExpressionsOverflowInIntBeforeWideningTheDistance() throws Exception {
        String original = source("for(int i=a-16;i<a+b;i++)sum+=i*i;");
        String generated = changed(original); int boundary = Math.toIntExact(threshold(original));
        try (var before = compile(original); var after = compile(generated); var observed = compile(instrument(generated))) {
            Method reference = run(before), actual = run(after), probe = run(observed);
            for (int[] input : new int[][] {{Integer.MAX_VALUE, boundary}, {Integer.MIN_VALUE, 0}, {0, 16}, {17, -20}}) {
                Object expected = reference.invoke(null, input[0], input[1]);
                assertEquals(expected, actual.invoke(null, input[0], input[1]));
                assertEquals(expected, probe.invoke(null, input[0], input[1]));
            }
            assertEquals(reference.invoke(null, 0, boundary), actual.invoke(null, 0, boundary));
            reached(probe, 0, boundary);
        }
    }

    @Test void nonUnitStridesLongCountersAndLongBoundsKeepTheExistingPath() throws Exception {
        for (String original : List.of(source("for(int i=a;i<b;i+=2)sum+=i*i;"),
                source("for(int i=a;i>b;i-=2)sum+=i*i;"), source("for(long i=a;i<b;i++)sum+=1;"),
                source("for(int i=a;i<(long)b;i++)sum+=i*i;"),
                "public class Sample {public static long run(int a,int b){return java.util.stream.LongStream.range(a,b).map(i->i*i).sum();}}")) {
            String generated = changed(original); threshold(original);
            try (var before = compile(original); var after = compile(generated); var observed = compile(instrument(generated))) {
                assertEquals(run(before).invoke(null, 0, 16), run(after).invoke(null, 0, 16));
                reached(run(observed), 0, 16);
            }
        }
    }
}
