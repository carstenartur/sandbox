/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import org.sandbox.jdt.triggerpattern.test.policy.PinnedGitRepository;

@EnabledIfSystemProperty(named = "sandbox.math.bc.rational.upstream", matches = "true")
class JavaQuadraticDenominatorTest {
    private static final String COMMIT = "6b4f58259d2b3ee8301fda1d6c8798f816abc5ea";
    private static final String PACKAGE = "org/bouncycastle/crypto/bls/";
    private static final String TARGET = PACKAGE + "BLS12_381G2HashToCurve.java";
    private static final String FIELD = PACKAGE + "Fp2Element.java";
    private static final String VERIFIED = "VERIFIED_QUADRATIC_DENOMINATOR";
    @TempDir Path temporary;

    @Test @Timeout(240) void appliesTheVerifiedScheduleToPinnedUpstreamSource() throws Exception {
        String mirror = System.getProperty("sandbox.math.bc.rational.repository");
        try (var checkout = mirror == null
                ? PinnedGitRepository.cloneAtCommit(temporary.resolve("bc"), URI.create("https://github.com/bcgit/bc-java.git"),
                        "refs/heads/main", COMMIT)
                : PinnedGitRepository.cloneAt(temporary.resolve("bc"), URI.create(mirror), "refs/heads/main", COMMIT)) {
            Path sources = checkout.directory().resolve("core/src/main/java");
            String original = Files.readString(sources.resolve(TARGET));
            var analysis = analyze(original, sources, true);
            assertTrue(analysis.diagnostics().stream().anyMatch(diagnostic -> VERIFIED.equals(diagnostic.code())),
                    () -> analysis.diagnostics().toString());
            var document = new Document(original);
            var undo = analysis.newEdit().apply(document);
            String generated = document.get();
            assertTrue(generated.contains("Fp2Element.of(6L, -6L)"));
            assertEquals(1, tail(generated).split("\\.inverse\\(\\)", -1).length - 1);
            undo.apply(document);
            assertEquals(original, document.get());
            assertEquals(run(sources, original, "original"), run(sources, generated, "generated"));

            assertFalse(verified(analyze(original, sources, false)));
            assertFalse(verified(analyze(original.replace("valueOf(0xc)", "valueOf(0xd)"), sources, true)));
            Files.writeString(sources.resolve(FIELD), Files.readString(sources.resolve(FIELD)).replace("int K = P.bitLength()", "int K = P.bitLength() + 1"));
            assertThrows(IllegalArgumentException.class, analysis::newEdit);
            assertFalse(verified(analyze(original, sources, true)));
        }
    }

    private static boolean verified(MathematicalAnalysis.Analysis analysis) {
        return analysis.diagnostics().stream().anyMatch(diagnostic -> VERIFIED.equals(diagnostic.code()));
    }

    private static MathematicalAnalysis.Analysis analyze(String source, Path sources, boolean optIn) {
        var compiler = new HashMap<String, String>();
        JavaCore.setComplianceOptions("17", compiler);
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setEnvironment(new String[0], new String[] {sources.toString()}, null, true);
        parser.setUnitName(TARGET);
        parser.setCompilerOptions(compiler);
        parser.setResolveBindings(true);
        parser.setSource(source.toCharArray());
        var ast = (CompilationUnit) parser.createAST(null);
        assertTrue(Arrays.stream(ast.getProblems()).noneMatch(problem -> problem.isError()),
                () -> Arrays.toString(ast.getProblems()));
        var options = MathCleanUpOptions.parse(Map.of(MathCleanUpOptions.CLEANUP, "true",
                MathCleanUpOptions.TIMEOUT_SECONDS, "20", MathCleanUpOptions.MATHEMATICAL_OPT_IN,
                Boolean.toString(optIn)), 17);
        return MathematicalAnalysis.analyze(ast, source, options, null, -1, 0,
                new MathematicalAnalysis.SourceEnvironment(TARGET, List.of(), List.of(sources.toString()), List.of(), compiler));
    }

    private static String tail(String source) {
        int start = source.indexOf("// iso_3 evaluation");
        return source.substring(start, source.indexOf("return BLS12_381G2Point.ofUnchecked", start));
    }

    private Object run(Path sources, String source, String name) throws Exception {
        Path directory = Files.createDirectory(temporary.resolve(name));
        Path output = Files.createDirectory(directory.resolve("classes"));
        int prefix = source.indexOf("// Simplified SWU on E'");
        int iso = source.indexOf("// iso_3 evaluation", prefix);
        int signature = source.indexOf("BLS12_381G2Point mapToCurveAndIso3(");
        int methodEnd = source.indexOf("\n    }", iso);
        String explicitPoleProbe = source.substring(signature, methodEnd + "\n    }".length())
                .replace("mapToCurveAndIso3(Fp2Element u)", "evaluateIso3(Fp2Element xPrime, Fp2Element yPrime)")
                .replace(source.substring(prefix, iso), "");
        Path target = Files.writeString(directory.resolve("BLS12_381G2HashToCurve.java"),
                source.substring(0, methodEnd + "\n    }".length()) + "\n    " + explicitPoleProbe
                + source.substring(methodEnd + "\n    }".length()));
        Path probe = Files.writeString(directory.resolve("QuadraticDenominatorProbe.java"), PROBE);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-sourcepath",
                sources.toString(), "-d", output.toString(), target.toString(), probe.toString()));
        try (var loader = new URLClassLoader(new URL[] {output.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            return loader.loadClass("org.bouncycastle.crypto.bls.QuadraticDenominatorProbe").getMethod("run").invoke(null);
        }
    }

    private static final String PROBE = """
        package org.bouncycastle.crypto.bls;
        import java.math.BigInteger;
        import java.nio.charset.StandardCharsets;
        import java.util.ArrayList;
        import java.util.SplittableRandom;
        public class QuadraticDenominatorProbe {
            public static Object run() {
                var curve = new BLS12_381G2HashToCurve("BLS12381G2_XMD:SHA-256_SSWU_RO_TEST".getBytes(StandardCharsets.US_ASCII));
                var result = new ArrayList<String>();
                var random = new SplittableRandom(0xae766417L);
                for (int i = 0; i < 16; i++) {
                    byte[] input = new byte[3 * i];
                    random.nextBytes(input);
                    append(result, curve.hashToCurve(input));
                    append(result, curve.mapToCurveAndIso3(Fp2Element.of(random.nextLong(), random.nextLong())));
                    append(result, curve.evaluateIso3(Fp2Element.of(random.nextLong(), random.nextLong()), Fp2Element.ONE));
                }
                Fp2Element unchecked = Fp2Element.ofReduced(BigInteger.valueOf(-1), BigInteger.valueOf(-1));
                append(result, curve.evaluateIso3(unchecked, Fp2Element.ONE));
                append(result, curve.evaluateIso3(Fp2Element.ONE, unchecked));
                var observed = new CountingInteger();
                append(result, curve.evaluateIso3(Fp2Element.ofReduced(observed, BigInteger.ZERO), Fp2Element.ONE));
                if (observed.multiplications == 0) {
                    throw new AssertionError("The unchecked schedule must retain the BigInteger calls");
                }
                result.add(Integer.toString(observed.multiplications));
                try {
                    curve.evaluateIso3(Fp2Element.of(-6, 6), Fp2Element.ONE);
                    throw new AssertionError("The pole must throw");
                } catch (ArithmeticException expected) {
                    result.add(expected.getClass().getName() + ":" + expected.getMessage());
                }
                return result;
            }
            private static void append(ArrayList<String> out, BLS12_381G2Point point) {
                out.add(point.isInfinity() ? "infinity" : point.x() + ":" + point.y());
            }
            private static final class CountingInteger extends BigInteger {
                int multiplications;
                CountingInteger() {
                    super("4");
                }
                @Override public BigInteger multiply(BigInteger value) {
                    multiplications++;
                    return super.multiply(value);
                }
            }
        }
        """;
}
