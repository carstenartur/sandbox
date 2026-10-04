/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.triggerpattern.test.policy.PinnedGitRepository;

/** Opt-in real trees. Never substitutes a synthetic class for the upstream file. */
class BouncyCastleMathReferenceTest {
    @TempDir Path temporary;

    @Test void exactPinnedTreesRemainUnchangedWithHonestUnsupportedDiagnostics() throws Exception {
        String configured = System.getProperty("math.qa.bcMirror");
        assumeTrue(configured != null && !configured.isBlank(), "Set math.qa.bcMirror to a verified JGit mirror");
        Path mirror = Path.of(configured);
        int index = 0;
        for (var pin : List.of(MathCorpusRunner.ORIGINAL, MathCorpusRunner.CANDIDATE)) {
            try (var checkout = PinnedGitRepository.cloneAt(temporary.resolve("checkout-" + index), mirror.toUri(), pin.ref(), pin.commit())) {
                assertEquals(pin.commit(), checkout.headCommit());
                MathCorpusRunner.verifyPinnedBlobs(checkout.directory(), pin);
                Path source = checkout.directory().resolve(MathCorpusRunner.REFERENCE_PATH);
                byte[] before = Files.readAllBytes(source);
                Path output = temporary.resolve("report-" + index++);
                var report = MathCorpusRunner.analyzeReference(checkout.directory(), output);
                assertEquals(1, report.javaFiles());
                assertEquals(0, report.filesWithErrors());
                assertTrue(report.bigIntegerCalls() > 0);
                assertEquals(0, report.candidateRegions(), "No field/control-flow assumptions are inferred");
                assertEquals(0, report.appliedChanges());
                assertArrayEquals(before, Files.readAllBytes(source));
                assertTrue(Files.readString(output.resolve("files.tsv")).contains("INITIALIZATION_REQUIRES_PARAMETER_REVIEW"));
                assertFalse(Files.readString(output.resolve("diagnostics.tsv")).isBlank());
            }
        }
        assertThrows(IllegalArgumentException.class, () -> PinnedGitRepository.cloneAt(temporary.resolve("wrong-pin"),
                mirror.toUri(), MathCorpusRunner.ORIGINAL.ref(), MathCorpusRunner.CANDIDATE.commit()));
    }
}
