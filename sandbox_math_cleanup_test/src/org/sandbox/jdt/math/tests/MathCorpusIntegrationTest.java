/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import javax.tools.ToolProvider;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Reconstructed regression inputs; fresh emitter results, never historical measurements. */
class MathCorpusIntegrationTest {
    private static final String SMALL = "65537";
    private static final String WIDE = "115792089210356248762697446949407573530086143415290314195533631308867097853951";
    private static final long[] EDGES = {0, 1, -1, 65536, 65537, 65538, Long.MIN_VALUE, Long.MAX_VALUE};
    @TempDir Path temporary;

    @Test void localReferenceIsProvedEmittedCompiledAndStable() throws Exception {
        Rewrite rewrite = qualify(source(SMALL), true);
        assertArrayEquals(new long[] {65527, 65527}, invoke(compile(rewrite.generated()), 0, -10));
        assertArrayEquals(new long[] {64537, 100}, invoke(compile(rewrite.generated()), 1, -10));
        export("small", rewrite);
    }

    @Test void wideModulusUsesTheSameActualJavaPipeline() throws Exception {
        export("wide", qualify(source(WIDE), true));
    }

    @Test void renamedVariablesDoNotSupplyAssumptions() throws Exception {
        qualify(source(SMALL).replace("baseSeed", "rawInput").replace("base", "factor")
                .replace("exponent", "degree").replace("modulus", "ringSize").replace("odd", "affine")
                .replace("left", "first").replace("right", "second"), true);
    }

    @Test void independentAffineCombinationIsAlsoProvedAndExecuted() throws Exception {
        String source = source(SMALL)
                .replace("exponent.multiply(BigInteger.TWO).add(BigInteger.ONE)",
                        "exponent.multiply(BigInteger.valueOf(3)).add(BigInteger.TWO)")
                .replace("exponent.add(BigInteger.ONE)", "exponent.multiply(BigInteger.TWO).add(BigInteger.ONE)");
        qualify(source, true);
    }

    @Test void unknownSignedExponentIsNotGivenACommentBasedAssumption() {
        assertRejected(source(SMALL).replace("seed & Integer.MAX_VALUE", "seed")
                .replace("BigInteger exponent", "/* nonnegative, trusted public parameter */ BigInteger exponent"));
    }

    @Test void zeroModulusIsRejectedWithoutChangingExceptionBehavior() {
        assertRejected(source("0"));
    }

    @Test void nonlinearExponentDoesNotPassAsAnAffineRelation() {
        assertRejected(source(SMALL).replace("exponent.multiply(BigInteger.TWO).add(BigInteger.ONE)",
                "exponent.multiply(exponent)"));
    }

    private Rewrite qualify(String source, boolean requireStable) throws Exception {
        long started = System.nanoTime();
        var analysis = MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options(), new NullProgressMonitor(), -1, 0);
        long elapsed = System.nanoTime() - started;
        assertTrue(analysis.changed(), analysis.diagnostics().toString());
        assertFalse(analysis.evidence().isEmpty(), "Candidate must carry independently checked evidence");
        assertTrue(analysis.evidence().stream().allMatch(e -> e.cost().estimatedRuntimeImprovement()));
        Document document = new Document(source);
        analysis.newEdit().apply(document);
        String generated = document.get();
        assertFalse(generated.contains("de.regelsuche"), generated);
        MathTestSupport.parse(generated);
        Method before = compile(source);
        Method after = compile(generated);
        for (long seed : EDGES) for (long base : EDGES)
            assertArrayEquals(invoke(before, seed, base), invoke(after, seed, base), "seed=" + seed + ", base=" + base);
        long[] random = new Random(93802455L).longs(256).toArray();
        for (int i = 0; i < random.length; i += 2)
            assertArrayEquals(invoke(before, random[i], random[i + 1]), invoke(after, random[i], random[i + 1]));
        if (requireStable) {
            var repeated = MathematicalAnalysis.analyze(MathTestSupport.parse(generated), generated, options(), new NullProgressMonitor(), -1, 0);
            assertFalse(repeated.changed(), repeated.diagnostics().toString());
        }
        return new Rewrite(source, generated, elapsed, analysis);
    }

    private static void assertRejected(String source) {
        var analysis = MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options(), new NullProgressMonitor(), -1, 0);
        assertFalse(analysis.changed(), analysis.diagnostics().toString());
        assertTrue(analysis.evidence().isEmpty());
        assertFalse(analysis.diagnostics().isEmpty(), "Unsupported input must be diagnosed");
    }

    private Method compile(String source) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "compiled-");
        Path file = directory.resolve("Calculation.java");
        Files.writeString(file, source);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A full JDK is required");
        assertEquals(0, compiler.run(null, null, null, "--release", "17", "-d", directory.toString(), file.toString()), source);
        try (var loader = new URLClassLoader(new java.net.URL[] {directory.toUri().toURL()}, null)) {
            return loader.loadClass("Calculation").getMethod("compute", long.class, long.class);
        }
    }

    private static long[] invoke(Method method, long seed, long base) throws Exception {
        return (long[]) method.invoke(null, seed, base);
    }

    private static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true, Set.of(NumericKind.BIG_INTEGER), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000L, 20_000, false, 17, List.of());
    }

    private static String source(String modulus) {
        // Source fixtures retain canonical LF on every host, including Windows.
        return """
                import java.math.BigInteger;
                public class Calculation {
                  public static long[] compute(long seed, long baseSeed) {
                    BigInteger base = BigInteger.valueOf(baseSeed);
                    BigInteger exponent = BigInteger.valueOf(seed & Integer.MAX_VALUE);
                    BigInteger modulus = new BigInteger("%s");
                    BigInteger odd = exponent.multiply(BigInteger.TWO).add(BigInteger.ONE);
                    BigInteger left = base.modPow(odd, modulus);
                    BigInteger right = base.modPow(exponent.add(BigInteger.ONE), modulus);
                    return new long[] {left.longValue(), right.longValue()};
                  }
                }
                """.replace("%s", modulus);
    }

    private static void export(String name, Rewrite rewrite) throws Exception {
        String configured = System.getProperty("math.qa.fixtureDirectory");
        if (configured == null || configured.isBlank()) return;
        Path root = Path.of(configured).resolve(name);
        writeNewOrIdentical(root.resolve("original/Calculation.java"), rewrite.original());
        writeNewOrIdentical(root.resolve("generated/Calculation.java"), rewrite.generated());
        Path analysis = root.resolve("analysis.txt");
        if (!Files.exists(analysis)) Files.writeString(analysis,
                "Fresh reconstructed-source execution; not recovered pre-incident data.\n"
                + "javaRuntime=" + System.getProperty("java.runtime.version") + "\n"
                + "parseSearchVerifyEmitElapsedNanos=" + rewrite.elapsedNanos() + "\n"
                + "runtimeMeasurementsRun=false\nfiniteExecutionChecksAreNotTheSymbolicProof=true\n"
                + rewrite.analysis().replacements().stream().map(MathematicalAnalysis.Replacement::description)
                        .reduce("", (a, b) -> a + b + "\n"), StandardOpenOption.CREATE_NEW);
    }

    private static void writeNewOrIdentical(Path path, String content) throws Exception {
        Files.createDirectories(Objects.requireNonNull(path.getParent(), "Fixture file must have a parent directory"));
        if (Files.exists(path)) assertEquals(content, Files.readString(path), "Refusing to overwrite earlier fixture: " + path);
        else Files.writeString(path, content, StandardOpenOption.CREATE_NEW);
    }

    private record Rewrite(String original, String generated, long elapsedNanos, MathematicalAnalysis.Analysis analysis) {}
}
