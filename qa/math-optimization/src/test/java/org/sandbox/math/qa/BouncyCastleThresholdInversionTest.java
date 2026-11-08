/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import de.regelsuche.ast.iteration.FieldInversionReduction.Kind;

/** Runs the production adapter against actual threshold sources at a pinned upstream commit. */
@EnabledIfSystemProperty(named = "sandbox.math.bc.threshold.upstream", matches = "true")
class BouncyCastleThresholdInversionTest {
    private static final URI REMOTE = URI.create("https://github.com/bcgit/bc-java.git");
    private static final String COMMIT = "e40316b6e25816c1e302c98b5fc1ba6e2dfdfc0c";
    private static final String THRESHOLD = "org/bouncycastle/crypto/threshold/";
    private static final String TARGET = THRESHOLD + "ShamirSplitSecret.java";
    private static final List<String> COHORT = List.of("Polynomial.java", "SecretShare.java", "SecretSplitter.java",
            "ShamirSecretSplitter.java", "ShamirSplitSecretShare.java", "SplitSecret.java");
    @TempDir Path temporary;

    @Test @Timeout(240) void exactUpstreamDivideAndReconstructionMatchCompiledOriginals() throws Exception {
        try (var checkout = PinnedGitRepository.cloneAtCommit(temporary.resolve("bc"), REMOTE, "refs/heads/main", COMMIT)) {
            assertEquals(COMMIT, checkout.headCommit());
            Path core = checkout.directory().resolve("core/src/main/java"), sources = temporary.resolve("sources");
            var files = new ArrayList<String>();
            for (String file : COHORT) files.add(THRESHOLD + file);
            for (String file : List.of("Arrays.java", "Encodable.java", "Objects.java")) files.add("org/bouncycastle/util/" + file);
            for (String path : files) {
                Files.createDirectories(sources.resolve(path).getParent());
                Files.copy(core.resolve(path), sources.resolve(path));
            }
            Files.writeString(sources.resolve(THRESHOLD + "ThresholdProbe.java"), PROBE);
            files.add(THRESHOLD + "ThresholdProbe.java");
            String original = Files.readString(core.resolve(TARGET));
            var compiler = new HashMap<String, String>();
            JavaCore.setComplianceOptions("17", compiler);
            var parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setEnvironment(new String[0], new String[] {sources.toString()}, null, true);
            parser.setUnitName(TARGET);
            parser.setCompilerOptions(compiler);
            parser.setResolveBindings(true);
            parser.setSource(original.toCharArray());
            var ast = (CompilationUnit) parser.createAST(null);
            assertTrue(java.util.Arrays.stream(ast.getProblems()).noneMatch(p -> p.isError()),
                    () -> java.util.Arrays.toString(ast.getProblems()));
            var options = MathCleanUpOptions.parse(Map.of(MathCleanUpOptions.CLEANUP, "true",
                    MathCleanUpOptions.FIELD_INVERSIONS, "true"), 17);
            var analysis = MathematicalAnalysis.analyze(ast, original, options, null, -1, 0,
                    new MathematicalAnalysis.SourceEnvironment(TARGET, List.of(), List.of(sources.toString()), List.of(), compiler));
            assertEquals(List.of(Kind.PER_ELEMENT, Kind.PRODUCT), analysis.evidence().stream()
                    .filter(e -> e.fieldInversion() != null).map(e -> e.fieldInversion().plan().kind()).toList(),
                    () -> analysis.diagnostics().toString());
            var output = new Document(original);
            analysis.newEdit().apply(output);
            assertEquals(run("original", original, sources, files), run("generated", output.get(), sources, files));
        }
    }

    private Object run(String directory, String source, Path sources, List<String> files) throws Exception {
        Path output = Files.createDirectory(temporary.resolve(directory));
        Path target = Files.writeString(output.resolve("ShamirSplitSecret.java"), source);
        var arguments = new ArrayList<>(List.of("--release", "17", "-d", output.toString(), target.toString()));
        for (String file : files) arguments.add(sources.resolve(file).toString());
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)));
        try (var loader = new URLClassLoader(new URL[] {output.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            return loader.loadClass("org.bouncycastle.crypto.threshold.ThresholdProbe").getMethod("run").invoke(null);
        }
    }

    private static final String PROBE = """
            package org.bouncycastle.crypto.threshold;
            import java.util.ArrayList;
            import java.util.Arrays;
            import java.util.SplittableRandom;
            public class ThresholdProbe {
                public static Object run() throws Exception {
                    var output = new ArrayList<String>();
                    var random = new SplittableRandom(0x5348414d4952L);
                    for (var field : ShamirSecretSplitter.Algorithm.values()) {
                        for (int count : new int[] {0, 1, 2, 3, 8, 16}) {
                            for (int width : new int[] {0, 1, 32}) {
                                for (int divisor : new int[] {0, 1, 17, 128, 255, 256, -239}) {
                                    var shares = new ShamirSplitSecretShare[count];
                                    for (int i = 0; i < count; i++) {
                                        byte[] row = new byte[width];
                                        random.nextBytes(row);
                                        // Zero and duplicate low-byte coordinates exercise totalized power at zero.
                                        shares[i] = new ShamirSplitSecretShare(row, i % 3 == 0 ? 257 : i);
                                    }
                                    var original = ShamirSplitSecret.getInstance(field, shares);
                                    try { output.add(Arrays.toString(original.getSecret())); }
                                    catch (RuntimeException failure) { output.add(failure.getClass().getName()); }
                                    try { original.divide(divisor); }
                                    catch (RuntimeException failure) { output.add(failure.getClass().getName()); }
                                    for (var share : shares) output.add(Arrays.toString(share.getEncoded()));
                                }
                            }
                        }
                        int[] divisions = {0};
                        var subtype = new Polynomial(field) {
                            protected byte gfDiv(int x, int y) { divisions[0]++; return (byte) (x ^ y ^ 7); }
                        };
                        var shares = new ShamirSplitSecretShare[] {new ShamirSplitSecretShare(new byte[] {1, 2, 3}, 1),
                                new ShamirSplitSecretShare(new byte[] {4, 5, 6}, 2), new ShamirSplitSecretShare(new byte[] {7, 8, 9}, 3)};
                        var custom = new ShamirSplitSecret(subtype, shares);
                        output.add(Arrays.toString(custom.getSecret()));
                        custom.divide(17);
                        output.add(Integer.toString(divisions[0]));
                        for (var share : shares) output.add(Arrays.toString(share.getEncoded()));
                    }
                    return output;
                }
            }
            """;
}
