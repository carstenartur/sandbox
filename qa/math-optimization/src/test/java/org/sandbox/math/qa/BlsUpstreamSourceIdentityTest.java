/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.triggerpattern.test.policy.PinnedGitRepository;

/** Explicit network gate: the archive cohort is byte-identical to one verified upstream release ref. */
@EnabledIfSystemProperty(named = "sandbox.math.bc.upstream", matches = "true")
class BlsUpstreamSourceIdentityTest {
    private static final String REMOTE = "https://github.com/bcgit/bc-java.git";
    private static final String REF = "refs/tags/r1rv85v2";
    private static final String COMMIT = "854bd5c4286e1530132418906bf6175da7d9d83c";
    @TempDir Path temporary;

    @Test @Timeout(180) void allFiveCompleteSourcesMatchThePinnedUpstreamRelease() throws Exception {
        Path sources = Path.of(System.getProperty("sandbox.math.bc.sourceArchive"));
        CorpusFiles.requireHash(sources, "b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f");
        var hashes = new TreeMap<String, String>();
        try (var checkout = PinnedGitRepository.cloneAt(temporary.resolve("upstream"), URI.create(REMOTE), REF, COMMIT);
                var archive = new JarFile(sources.toFile())) {
            assertEquals(COMMIT, checkout.headCommit());
            for (String type : List.of("BLS12_381G2HashToCurve", "BLS12_381Pairing", "Fp12Element", "Fp2Element", "Fp6Element")) {
                String entry = "org/bouncycastle/crypto/bls/" + type + ".java";
                Path upstream = checkout.directory().resolve("core/src/main/java/" + entry);
                try (var input = archive.getInputStream(archive.getJarEntry(entry))) {
                    assertArrayEquals(input.readAllBytes(), Files.readAllBytes(upstream), entry);
                }
                hashes.put(entry, CorpusFiles.sha256(upstream));
            }
        }
        String export = System.getProperty("sandbox.math.corpus.evidence");
        if (export != null) {
            Path output = Path.of(export); Files.createDirectories(output);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("bls-source-identity.json").toFile(),
                    Map.of("schema", "sandbox.bls-upstream-source-identity/v1", "remote", REMOTE, "ref", REF,
                            "commit", COMMIT, "sourcesSha256", CorpusFiles.sha256(sources), "files", hashes));
        }
        System.out.printf("BLS_UPSTREAM remote=%s ref=%s commit=%s classes=%d%n", REMOTE, REF, COMMIT, hashes.size());
    }
}
