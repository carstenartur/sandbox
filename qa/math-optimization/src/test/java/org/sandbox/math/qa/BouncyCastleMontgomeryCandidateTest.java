/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import org.sandbox.jdt.math.tests.StrictCompilerOptions;
import org.sandbox.jdt.triggerpattern.test.policy.PinnedGitRepository;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

@EnabledIfSystemProperty(named = "sandbox.math.bc.montgomery.upstream", matches = "true")
class BouncyCastleMontgomeryCandidateTest {
    private static final String COMMIT = "bb81bfa75c9fa81c7fde467a6314374d22715472";
    @TempDir Path temporary;

    @Test @Timeout(240) void reportsBothPinnedKernelsWithoutAuthorizingEdits() throws Exception {
        String mirror = System.getProperty("sandbox.math.bc.montgomery.repository");
        try (var checkout = mirror == null
                ? PinnedGitRepository.cloneAtCommit(temporary.resolve("bc"), URI.create("https://github.com/bcgit/bc-java.git"),
                        "refs/heads/main", COMMIT)
                : PinnedGitRepository.cloneAt(temporary.resolve("bc"), URI.create(mirror), "refs/heads/main", COMMIT)) {
            Path sources = checkout.directory().resolve("core/src/main/java");
            for (String file : List.of("org/bouncycastle/crypto/signers/mldsa/Poly.java",
                    "org/bouncycastle/crypto/kems/mlkem/PolyVec.java")) {
                String source = Files.readString(sources.resolve(file));
                var compiler = StrictCompilerOptions.allWarnings(17);
                var parser = ASTParser.newParser(AST.getJLSLatest());
                parser.setSource(source.toCharArray());
                parser.setUnitName(file);
                parser.setEnvironment(new String[0], new String[] {sources.toString()}, null, true);
                parser.setResolveBindings(true);
                parser.setCompilerOptions(compiler);
                var ast = (CompilationUnit)parser.createAST(null);
                assertFalse(Arrays.stream(ast.getProblems()).anyMatch(IProblem::isError), () -> Arrays.toString(ast.getProblems()));
                var options = new MathCleanUpOptions(true, Set.of(NumericKind.SHORT, NumericKind.INT, NumericKind.LONG),
                        SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 2_000_000,
                        4096, false, 17, List.of(), true, MathCleanUpOptions.ExplanationMode.NONE, 60);
                var environment = new MathematicalAnalysis.SourceEnvironment(file, List.of(), List.of(sources.toString()),
                        List.of(), compiler);
                var result = MathematicalAnalysis.analyze(ast, source, options, null, source.indexOf("pointwiseAccountMontgomery("),
                        source.substring(source.indexOf("pointwiseAccountMontgomery(")).indexOf('{') + 2, environment);
                assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("MONTGOMERY_CALLER_PROOF_REQUIRED")),
                        () -> result.diagnostics().toString());
                assertTrue(result.replacements().isEmpty(), result.replacements().toString());
            }
        }
    }
}
