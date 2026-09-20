/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateInstallationVerifierTest {
    private static final String AGGREGATE = "sandbox_feature.feature.group";
    private static final String COMPONENT = "sandbox_tools_feature.feature.group";

    @TempDir Path temporary;

    @Test
    void rejectsWrongPublishedAggregateVersionAndStaleCandidateVersion() throws Exception {
        IOException wrongOld = assertThrows(IOException.class, () -> AggregateInstallationVerifier.requireCandidateUpgradeSource(
                URI.create("https://example.invalid/releases/1.3.5/"),
                Map.of(AGGREGATE, "1.3.4", COMPONENT, "1.3.5"),
                Map.of(AGGREGATE, "1.3.6.qualifier", COMPONENT, "1.3.6.qualifier")));
        assertEquals("Expected published aggregate 1.3.5, found 1.3.4", wrongOld.getMessage());

        IOException stale = assertThrows(IOException.class, () -> AggregateInstallationVerifier.requireCandidateUpgradeSource(
                URI.create("https://example.invalid/releases/1.3.5/"),
                Map.of(AGGREGATE, "1.3.5", COMPONENT, "1.3.5"),
                Map.of(AGGREGATE, "1.3.5.qualifier", COMPONENT, "1.3.6.qualifier")));
        assertEquals("Candidate version is not newer for sandbox_feature.feature.group: old=1.3.5, new=1.3.5.qualifier",
                stale.getMessage());
    }

    @Test
    void rejectsCandidateMissingPublishedComponent() {
        IOException missing = assertThrows(IOException.class, () -> AggregateInstallationVerifier.requireCandidateUpgradeSource(
                URI.create("https://example.invalid/releases/1.3.5/"),
                Map.of(AGGREGATE, "1.3.5", COMPONENT, "1.3.5"),
                Map.of(AGGREGATE, "1.3.6.qualifier")));
        assertEquals("Candidate repository misses published component sandbox_tools_feature.feature.group",
                missing.getMessage());
    }

    @Test
    void rejectsStaleEvidenceFile() throws Exception {
        Path file = Files.writeString(temporary.resolve("runtime.json"), "{}");
        Instant started = Instant.now();
        Files.setLastModifiedTime(file, FileTime.from(started.minusSeconds(5)));
        IOException failure = assertThrows(IOException.class,
                () -> AggregateInstallationVerifier.requireFreshFile(file, started, "Runtime probe result"));
        assertEquals("Runtime probe result is stale: " + file, failure.getMessage());
    }

    @Test
    void rejectsClaimedChangeWhenSourcesDidNotChange() throws Exception {
        Path source = Files.writeString(temporary.resolve("Sample.java"), "class Sample {}\n");
        Map<Path, String> before = Map.of(source, Files.readString(source));
        IOException failure = assertThrows(IOException.class,
                () -> AggregateInstallationVerifier.requireChangedSources("apply", before, before, Set.of(source)));
        assertEquals("apply changed sources differ; expected [" + source + "], actual []", failure.getMessage());
    }

    @Test
    void rejectsCheckModeInputMutation() throws Exception {
        Path source = Files.writeString(temporary.resolve("Sample.java"), "class Sample {}\n");
        Map<Path, String> before = Map.of(source, Files.readString(source));
        Map<Path, String> after = Map.of(source, "class Sample { int value; }\n");
        IOException failure = assertThrows(IOException.class,
                () -> AggregateInstallationVerifier.requireUnchangedSources("check", before, after));
        assertEquals("check changed input during check mode", failure.getMessage());
    }

    @Test
    void rejectsInvalidCompiledOutput() throws Exception {
        Path source = Files.writeString(temporary.resolve("Broken.java"), "class Broken { void run( }");
        IOException failure = assertThrows(IOException.class,
                () -> AggregateInstallationVerifier.requireCompilation(List.of(source), temporary.resolve("classes")));
        assertTrue(failure.getMessage().startsWith("Invalid Java output: "), failure.getMessage());
    }
}
