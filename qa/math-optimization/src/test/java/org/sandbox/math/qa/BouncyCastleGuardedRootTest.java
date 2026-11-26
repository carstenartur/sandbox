/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
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
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import org.sandbox.jdt.math.tests.StrictCompilerOptions;
import org.sandbox.jdt.triggerpattern.test.policy.PinnedGitRepository;

/** Qualification of the ordinary Java analysis on an unmodified, commit-pinned scalar program. */
@EnabledIfSystemProperty(named = "sandbox.math.bc.root.upstream", matches = "true")
class BouncyCastleGuardedRootTest {
    private static final String REMOTE = "https://github.com/bcgit/bc-java.git";
    private static final String REF = "refs/heads/main", COMMIT = "6560e91d0a164b8ab58266f141ee6daeec21a340";
    private static final String TARGET = "org/bouncycastle/crypto/bls/BLS12_381G2HashToCurve.java";
    @TempDir Path temporary;

    @Test @Timeout(240) void originalJavaProducesAnExactGuardedPlanAndPreservesPublicVectors() throws Exception {
        var mirror = System.getProperty("sandbox.math.bc.root.repository");
        var remote = URI.create(mirror == null ? REMOTE : mirror);
        if (mirror != null) try (var repo = Git.open(Path.of(remote).toFile())) {
            assertEquals(REMOTE, repo.getRepository().getConfig().getString("remote", "origin", "url"));
        }
        try (var checkout = mirror == null
                ? PinnedGitRepository.cloneAtCommit(temporary.resolve("checkout"), remote, REF, COMMIT)
                : PinnedGitRepository.cloneAt(temporary.resolve("checkout"), remote, REF, COMMIT)) {
            assertEquals(COMMIT, checkout.headCommit());
            Path sources = checkout.directory().resolve("core/src/main/java");
            String original = Files.readString(sources.resolve(TARGET));
            var analysis = analyze(original, sources);
            var region = analysis.evidence().stream().filter(r -> r.fieldRoot() != null).findFirst()
                    .orElseThrow(() -> new AssertionError(analysis.diagnostics().toString()));
            var program = region.fieldRoot().certificate().program();
            assertEquals(1L, program.prefix().stream().filter(s -> s.op().name().equals("POWER")).count());
            assertTrue(List.of(program.prefix(), program.equal().steps(), program.unequal().steps()).stream()
                    .flatMap(List::stream).noneMatch(s -> s.op().name().equals("INVERSE")));
            var document = new Document(original);
            var undo = analysis.newEdit().apply(document);
            String generated = document.get();
            undo.apply(document);
            assertEquals(original, document.get());
            assertFalse(analyze(generated, sources).changed());
            Path output = Path.of(System.getProperty("sandbox.math.bc.root.output", temporary.resolve("products").toString()));
            assertEquals(run(output.resolve("original"), original, sources), run(output.resolve("generated"), generated, sources));
            var receipts = analysis.sourceDependencyReceipts().stream().map(r -> r.identity()).toList();
            for (var file : List.of("Fp2Element.java", "Fp.java", "BLS12_381G1.java", "BigIntegers.java", "Pack.java", "Nat.java", "Mod.java", "Arrays.java"))
                assertTrue(receipts.stream().anyMatch(path -> path.endsWith(file)), () -> receipts.toString());
            Path helper = sources.resolve("org/bouncycastle/util/Pack.java");
            Files.writeString(helper, Files.readString(helper) + "\n");
            assertThrows(IllegalArgumentException.class, analysis::newEdit);
            if (System.getProperty("sandbox.math.bc.root.output") != null) {
                var receipt = new java.util.Properties();
                receipt.setProperty("upstreamCommit", COMMIT);
                for (String variant : List.of("original", "generated")) {
                    var directory = output.resolve(variant);
                    receipt.setProperty(variant + "SourceSha256", CorpusFiles.sha256(directory.resolve("BLS12_381G2HashToCurve.java")));
                    receipt.setProperty(variant + "ClassesSha256", GuardedRootBenchmark.classTreeSha256(directory.resolve("classes")));
                }
                receipt.setProperty("candidates", Integer.toString(region.fieldRoot().candidates()));
                receipt.setProperty("searchWork", Long.toString(region.fieldRoot().searchWork()));
                receipt.setProperty("prefixSteps", Integer.toString(program.prefix().size()));
                receipt.setProperty("equalSteps", Integer.toString(program.equal().steps().size()));
                receipt.setProperty("unequalSteps", Integer.toString(program.unequal().steps().size()));
                try (var writer = Files.newBufferedWriter(output.resolve("root-qualification.properties"))) {
                    receipt.store(writer, "Exact-pair verification on upstream source");
                }
            }
            System.out.printf("FIELD_ROOT source=%s candidates=%d work=%d prefix=%d equal=%d unequal=%d%n",
                    COMMIT, region.fieldRoot().candidates(), region.fieldRoot().searchWork(),
                    program.prefix().size(), program.equal().steps().size(), program.unequal().steps().size());
        }
    }

    private static MathematicalAnalysis.Analysis analyze(String source, Path sources) {
        var compiler = StrictCompilerOptions.allWarnings(17);
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setEnvironment(new String[0], new String[]{sources.toString()}, null, true);
        parser.setUnitName(TARGET);
        parser.setCompilerOptions(compiler);
        parser.setResolveBindings(true);
        parser.setSource(source.toCharArray());
        var ast = (CompilationUnit) parser.createAST(null);
        assertFalse(Arrays.stream(ast.getProblems()).anyMatch(p -> p.isError()), () -> Arrays.toString(ast.getProblems()));
        var options = MathCleanUpOptions.parse(Map.of(MathCleanUpOptions.CLEANUP, "true",
                MathCleanUpOptions.WORK_BUDGET, "100000000", MathCleanUpOptions.MAX_STATES, "20000",
                MathCleanUpOptions.TIMEOUT_SECONDS, "60"), 17);
        return MathematicalAnalysis.analyze(ast, source, options, null, -1, 0,
                new MathematicalAnalysis.SourceEnvironment(TARGET, List.of(), List.of(sources.toString()), List.of(), compiler));
    }

    private static Object run(Path dir, String source, Path sources) throws Exception {
        var classes = Files.createDirectories(dir.resolve("classes"));
        var target = Files.writeString(dir.resolve("BLS12_381G2HashToCurve.java"), source);
        var probe = Files.writeString(dir.resolve("RootProbe.java"), PROBE);
        var javac = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, javac.run(null, null, null, "--release", "17", "-sourcepath", sources.toString(),
                "-d", classes.toString(), target.toString(), probe.toString()));
        assertEquals(0, javac.run(null, null, null, "--release", "17", "-Xlint:all", "-Werror",
                "-cp", classes.toString(), "-d", classes.toString(), target.toString(), probe.toString()));
        try (var loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            return loader.loadClass("org.bouncycastle.crypto.bls.RootProbe").getMethod("run").invoke(null);
        }
    }

    private static final String PROBE = """
            package org.bouncycastle.crypto.bls;
            import java.math.BigInteger;
            import java.nio.charset.StandardCharsets;
            import java.util.ArrayList;
            import java.util.Random;
            public class RootProbe {
                public static Object run() {
                    var out = new ArrayList<String>();
                    var random = new Random(541331);
                    for (int i = 0; i < 128; i++) {
                        BigInteger a = new BigInteger(381, random).mod(Fp2Element.P);
                        BigInteger b = new BigInteger(381, random).mod(Fp2Element.P);
                        var input = i < 3 ? Fp2Element.of(i, 0) : Fp2Element.of(a, b);
                        var root = new Fp2Element[1];
                        boolean square = BLS12_381G2HashToCurve.sqrtOrSqrtZ(input, root);
                        out.add(square + ":" + root[0].c0() + ":" + root[0].c1());
                    }
                    var h = new BLS12_381G2HashToCurve("QUUX-V01-CS02-with-BLS12381G2_XMD:SHA-256_SSWU_RO_".getBytes(StandardCharsets.US_ASCII));
                    for (String message : new String[]{"", "abc", "abcdef0123456789"}) {
                        var point = h.hashToCurve(message.getBytes(StandardCharsets.US_ASCII));
                        out.add(point.x().toString() + ":" + point.y().toString());
                        if (message.isEmpty() && !point.x().c0().equals(new BigInteger(
                                "0141ebfbdca40eb85b87142e130ab689c673cf60f1a3e98d69335266f30d9b8d4ac44c1038e9dcdd5393faf5c41fb78a", 16)))
                            throw new AssertionError("RFC 9380 J.10.1");
                    }
                    return out;
                }
            }
            """;
}
