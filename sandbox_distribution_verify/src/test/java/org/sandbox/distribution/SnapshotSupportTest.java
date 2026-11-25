/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.distribution;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.osgi.framework.Version;

class SnapshotSupportTest {
    @TempDir Path directory;

    @ParameterizedTest
    @CsvSource({
        // Existing artifact version/bytes = 2/a. Compare both containing features independently.
        "true,2a,2a,2a,true", "true,2a,3b,3b,true", "true,2a,3b,2a,true", "true,2a,2a,3b,true",
        "true,2a,2b,2a,false", "true,2a,2a,2b,false", "true,2b,2a,2a,false", "true,3a,3a,3a,false",
        "true,2a,1a,2a,false", "true,2a,2a,1a,false",
        "false,3b,3b,3b,true", "false,2a,3b,3b,false", "false,1a,3b,3b,false",
        "false,3b,2a,3b,false", "false,3b,3b,2a,false"
    })
    void ordersActualSupportAndBothEnclosingArchives(boolean retry, String support, String component, String aggregate, boolean allowed) {
        var previous = site(artifact("2a"), artifact("2a"), artifact("2a"));
        var candidate = site(artifact(support), artifact(component), artifact(aggregate));
        if (allowed) assertDoesNotThrow(() -> SnapshotSupportPreflight.compare(retry, previous, candidate));
        else assertThrows(IOException.class, () -> SnapshotSupportPreflight.compare(retry, previous, candidate));
    }

    @Test
    void aBindingRetainedAcrossSourceChangesMustStayImmutable() throws Exception {
        var previous = site(artifact("2a"), artifact("2a"), artifact("2a"));
        SnapshotSupportPreflight.compare(false, previous, new SupportArtifacts(artifact("3a"), artifact("3a"), artifact("3a"), artifact("2a")));
        assertThrows(IOException.class, () -> SnapshotSupportPreflight.compare(false, previous,
                new SupportArtifacts(artifact("3a"), artifact("3a"), artifact("3a"), artifact("2b"))));
    }

    @Test
    void introductionRequiresNewSourceAndBothVersionsToAdvance() throws Exception {
        var previous = site(null, artifact("2a"), artifact("2a"));
        var candidate = site(artifact("3b"), artifact("3b"), artifact("3b"));
        SnapshotSupportPreflight.compare(false, previous, candidate);
        assertThrows(IOException.class, () -> SnapshotSupportPreflight.compare(true, previous, candidate));
        assertThrows(IOException.class, () -> SnapshotSupportPreflight.compare(false, previous,
                site(candidate.support(), candidate.component(), previous.aggregate())));
    }

    @ParameterizedTest
    @CsvSource({"fragment", "component", "aggregate"})
    void rejectsStagedSubstitutionsAgainstThePassingBuild(String replaced) throws Exception {
        var tested = SupportArtifacts.Artifact.read("2.0.0", Files.writeString(directory.resolve("tested"), "tested bytes"));
        var changed = SupportArtifacts.Artifact.read("2.0.0", Files.writeString(directory.resolve("substitute"), "different bytes"));
        Path aggregate = Files.createDirectory(directory.resolve("aggregate"));
        var properties = new java.util.Properties();
        properties.setProperty("status", "PASS");
        properties.setProperty("support.version", "2.0.0");
        for (String key : java.util.List.of("support.maven", "support.p2", "fresh.support", "product.support", "support-after.support")) {
            properties.setProperty(key + ".sha256", tested.sha256());
        }
        try (var out = Files.newOutputStream(aggregate.resolve("verification.properties"))) { properties.store(out, null); }
        String installed = """
                {"fragmentVersion":"2.0.0","fragmentPath":"/ide/plugins/sandbox_junit_cleanup_jdt5_2.0.0.jar","fragmentSha256":"%s"}
                """.formatted(tested.sha256());
        for (String stage : java.util.List.of("product", "fresh", "support-after", "updated", "upgraded", "migrated")) {
            Files.writeString(aggregate.resolve(stage + "-support.json"), installed);
        }
        Files.writeString(directory.resolve("verification.json"), """
                {"result":"PASS","repository":{"publishedFeatures":[
                   {"id":"sandbox_junit_cleanup_feature","version":"2.0.0","artifactSha256":"%s"},
                   {"id":"sandbox_feature","version":"2.0.0","artifactSha256":"%s"}]}}
                """.formatted(tested.sha256(), tested.sha256()));
        SnapshotSupportPreflight.verifyBuild(directory, new SupportArtifacts(tested, tested, tested, tested));
        var staged = new SupportArtifacts(tested, "component".equals(replaced) ? changed : tested,
                "aggregate".equals(replaced) ? changed : tested, "fragment".equals(replaced) ? changed : tested);
        assertThrows(IOException.class, () -> SnapshotSupportPreflight.verifyBuild(directory, staged));
    }

    @Test
    void requiresExistingReceiptWhenSupportIsPublished() throws Exception {
        var receipt = new SnapshotSupportPreflight.Receipt("refs/heads/main", "a".repeat(40), 1, "5",
                SupportArtifacts.BSN, "1.0.0", "abc", "1.0.0", "abc", "1.0.0", "def", "1.0.0", "abc");
        assertThrows(IOException.class, () -> SnapshotSupportPreflight.verifyReceipt(directory, receipt));
        Files.writeString(directory.resolve(SnapshotSupportPreflight.RECEIPT), "{}");
        assertThrows(IOException.class, () -> SnapshotSupportPreflight.verifyReceipt(directory, receipt));
    }

    @Test
    void immutableQualifierSortsAboveLegacyAndBelowRelease() throws IOException {
        String sha = "a".repeat(40);
        String first = SnapshotSource.qualifier(42, sha);
        assertEquals("v000000000042-g" + sha, first);
        var next = new Version(1, 3, 11, first);
        assertTrue(new Version("1.3.11.202611230145").compareTo(new Version(HistoricalSupport.VERSION)) < 0);
        assertTrue(new Version(HistoricalSupport.VERSION).compareTo(next) < 0);
        assertTrue(next.compareTo(new Version(1, 3, 11, SnapshotSource.qualifier(43, sha))) < 0);
        assertTrue(next.compareTo(new Version("1.3.11.zRelease")) < 0);
        assertThrows(IOException.class, () -> SnapshotSource.qualifier(1_000_000_000_000L, sha));
        assertThrows(IOException.class, () -> SnapshotSource.qualifier(3, "main"));
    }

    @Test
    void publicationReceiptBindsRealQualifierToManifestSourceAndRun() throws Exception {
        String sha = "f".repeat(40);
        var source = new SnapshotSource.Revision(sha, 32);
        var manifest = new com.google.gson.JsonObject();
        manifest.addProperty("commit_sha", sha);
        manifest.addProperty("ref", "refs/heads/main");
        var workflow = new com.google.gson.JsonObject();
        workflow.addProperty("source_java_ci_run_id", "5678");
        manifest.add("provenance_workflow", workflow);
        var support = new SupportArtifacts.Artifact(new Version(1, 3, 11, SnapshotSource.qualifier(32, sha)), directory, "abc");
        var receipt = SnapshotSupportPreflight.receipt(manifest, source, site(support, artifact("2a"), artifact("2a")));
        assertEquals("5678", receipt.sourceCiRunId());
        Files.writeString(directory.resolve(SnapshotSupportPreflight.RECEIPT), new com.google.gson.Gson().toJson(receipt));
        SnapshotSupportPreflight.verifyReceipt(directory, receipt);
        var dev = new SupportArtifacts.Artifact(new Version("1.3.11.dev20261123010101000"), directory, "abc");
        assertThrows(IOException.class, () -> SnapshotSupportPreflight.receipt(manifest, source, site(dev, artifact("2a"), artifact("2a"))));
        manifest.addProperty("commit_sha", "a".repeat(40));
        assertThrows(IOException.class, () -> SnapshotSupportPreflight.receipt(manifest, source, site(support, artifact("2a"), artifact("2a"))));
    }

    @Test
    void rejectsUnexpectedCheckoutSideBranchAndShallowHistory() throws Exception {
        SnapshotSource.git(directory, "init", "-b", "main");
        SnapshotSource.git(directory, "-c", "user.name=test", "-c", "user.email=test@example.invalid", "commit", "--allow-empty", "-m", "first");
        String sha = SnapshotSource.git(directory, "rev-parse", "HEAD");
        SnapshotSource.git(directory, "update-ref", "refs/remotes/origin/main", sha);
        assertEquals(new SnapshotSource.Revision(sha, 1), SnapshotSource.selected(directory, sha));
        assertThrows(IOException.class, () -> SnapshotSource.selected(directory, "0".repeat(40)));
        Files.writeString(directory.resolve("unexpected"), "modified source");
        SnapshotSource.git(directory, "add", "unexpected");
        assertThrows(IOException.class, () -> SnapshotSource.selected(directory, sha));
        SnapshotSource.git(directory, "reset", "--", "unexpected");
        SnapshotSource.git(directory, "-c", "user.name=test", "-c", "user.email=test@example.invalid", "commit", "--allow-empty", "-m", "off main");
        String side = SnapshotSource.git(directory, "rev-parse", "HEAD");
        assertThrows(IOException.class, () -> SnapshotSource.selected(directory, side));
        SnapshotSource.git(directory, "update-ref", "refs/remotes/origin/main", side);
        Files.writeString(directory.resolve(".git/shallow"), sha + "\n");
        assertThrows(IOException.class, () -> SnapshotSource.selected(directory, side));
    }

    @Test
    void rejectsDuplicateStaleRootedAndOutOfPoolInstallations() throws Exception {
        var support = artifact("2a");
        Path loose = Files.writeString(directory.resolve("reactor.jar"), "loose");
        for (var profile : java.util.List.of(profile(Set.of("2.0.0", "1.0.0"), false),
                profile(Set.of("1.0.0"), false), profile(Set.of("2.0.0"), true))) {
            var e = assertThrows(IOException.class, () -> SupportArtifacts.installed(SupportArtifacts.BSN, support, directory, loose, profile));
            assertTrue(e.getMessage().contains("versions or root"));
        }
        var e = assertThrows(IOException.class, () -> SupportArtifacts.installed(SupportArtifacts.BSN, support, directory, loose, profile(Set.of("2.0.0"), false)));
        assertTrue(e.getMessage().contains("outside selected installation"));
    }

    private AggregateInstallationEvidence.Profile profile(Set<String> versions, boolean root) {
        return new AggregateInstallationEvidence.Profile(Map.of(SupportArtifacts.BSN, versions), root ? Set.of(SupportArtifacts.BSN) : Set.of(), "test", directory.toString());
    }

    private static SupportArtifacts site(SupportArtifacts.Artifact support, SupportArtifacts.Artifact component, SupportArtifacts.Artifact aggregate) {
        return new SupportArtifacts(support, component, aggregate, component);
    }

    private SupportArtifacts.Artifact artifact(String value) {
        return new SupportArtifacts.Artifact(new Version(value.substring(0, 1)), directory, value.substring(1));
    }
}
