/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000));
        AggregateInstallationVerifier.FileSnapshot snapshot = AggregateInstallationVerifier.snapshot(file);
        IOException failure = assertThrows(IOException.class,
                () -> AggregateInstallationVerifier.requireFreshFile(file, snapshot, "Runtime probe result"));
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

    @Test
    void createsFixturePackageDirectoriesBeforeWritingSources() throws Exception {
        Path root = temporary.resolve("repo");
        Files.createDirectories(root.resolve("target"));
        AggregateInstallationVerifier verifier = new AggregateInstallationVerifier(root);
        Path charset = verifier.writeCharsetProject("stage", "keep");
        Path functional = verifier.writeFunctionalProject("stage");
        assertTrue(Files.isRegularFile(charset.resolve("src/main/java/probe/charset/Alpha.java")));
        assertTrue(Files.isRegularFile(charset.resolve("src/test/java/probe/charset/Skip.java")));
        assertTrue(Files.isRegularFile(functional.resolve("src/main/java/probe/functional/LoopSample.java")));
    }

    @Test
    void reportsStringOverloadWhenUtf8IsConvertedToString() throws Exception {
        Path source = temporary.resolve("probe/charset/Fallback.java");
        AggregateInstallationEvidence.createParentDirectories(source);
        Files.writeString(source, """
                package probe.charset;
                class Fallback {
                    byte[] bytes(String text) throws Exception {
                        return text.getBytes(java.nio.charset.StandardCharsets.UTF_8.toString());
                    }
                    String decode(byte[] bytes) throws Exception {
                        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8.toString());
                    }
                }
                """);
        AggregateInstallationVerifier.SourceAnalysis analysis = AggregateInstallationVerifier.analyzeCharsetSources(List.of(source));
        IOException failure = assertThrows(IOException.class, () -> AggregateInstallationVerifier.requireResolvedInvocations(source,
                analysis, Set.of(
                        "bytes -> java.lang.String.getBytes(java.nio.charset.Charset)",
                        "decode -> java.lang.String.<init>(byte[],java.nio.charset.Charset)")));
        assertTrue(failure.getMessage().startsWith("Resolved encoding invocations differ for " + source), failure.getMessage());
    }

    @Test
    void acceptsCharsetOverloadsByResolvedIdentity() throws Exception {
        Path source = temporary.resolve("probe/charset/Accepted.java");
        AggregateInstallationEvidence.createParentDirectories(source);
        Files.writeString(source, """
                package probe.charset;
                class Accepted {
                    byte[] bytes(String text) throws Exception {
                        return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    }
                    String decode(byte[] bytes) throws Exception {
                        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
                """);
        AggregateInstallationVerifier.SourceAnalysis analysis = AggregateInstallationVerifier.analyzeCharsetSources(List.of(source));
        AggregateInstallationVerifier.requireResolvedInvocations(source, analysis, Set.of(
                "bytes -> java.lang.String.getBytes(java.nio.charset.Charset)",
                "decode -> java.lang.String.<init>(byte[],java.nio.charset.Charset)"));
    }

    @Test
    void rejectsCleanupReportWhenChangedFileOnlyAppearsOutsideChangedFilesArray() throws Exception {
        Path source = temporary.resolve("Sample.java");
        String report = """
                {
                  "mode": "apply",
                  "filesProcessed": 1,
                  "filesChanged": 1,
                  "errorCount": 0,
                  "changedFiles": [],
                  "errors": [],
                  "debug": "%s"
                }
                """.formatted(source.toString().replace("\\", "\\\\"));
        IOException failure = assertThrows(IOException.class,
                () -> AggregateInstallationVerifier.requireCleanupReport("apply", report, "apply", 1, Set.of(source), 0));
        assertTrue(failure.getMessage().startsWith("apply recorded wrong changedFiles array"), failure.getMessage());
    }

    @Test
    void rejectsRuntimeProbeWhenResolvedBundlesDoNotMatchExpectedInventory() throws Exception {
        String report = """
                {
                  "status": "PASS",
                  "bundles": 1,
                  "cleanups": 2,
                  "helpTocs": 1,
                  "resolvedBundles": ["sandbox_other/1.3.6.qualifier"]
                }
                """;
        IOException failure = assertThrows(IOException.class,
                () -> AggregateInstallationVerifier.requireRuntimeProbe("runtime", report, Set.of("sandbox_feature")));
        assertTrue(failure.getMessage().startsWith("runtime recorded wrong resolvedBundles ids"), failure.getMessage());
    }

    @Test
    void acceptsImportedQualifiedAndStaticImportJdkUtf8FieldsByResolvedIdentity() throws Exception {
        Path imported = writeJava("Imported.java", """
                import java.nio.charset.Charset;
                import java.nio.charset.StandardCharsets;
                class Imported {
                    static final Charset UTF_8 = StandardCharsets.UTF_8;
                }
                """);
        AggregateInstallationVerifier.requireResolvedUtf8Field(imported,
                AggregateInstallationVerifier.analyzeCharsetSources(List.of(imported)));

        Path qualified = writeJava("Qualified.java", """
                import java.nio.charset.Charset;
                class Qualified {
                    static final Charset UTF_8 = java.nio.charset.StandardCharsets.UTF_8;
                }
                """);
        AggregateInstallationVerifier.requireResolvedUtf8Field(qualified,
                AggregateInstallationVerifier.analyzeCharsetSources(List.of(qualified)));

        Path staticImport = writeJava("StaticImport.java", """
                import java.nio.charset.Charset;
                import static java.nio.charset.StandardCharsets.UTF_8;
                class StaticImport {
                    static final Charset VALUE = UTF_8;
                }
                """);
        AggregateInstallationVerifier.SourceAnalysis staticImportAnalysis = AggregateInstallationVerifier.analyzeCharsetSources(List.of(staticImport));
        AggregateInstallationVerifier.requireResolvesToJdkUtf8("static import",
                AggregateInstallationVerifier.requireSingleField(staticImport, staticImportAnalysis));
    }

    @Test
    void rejectsInheritedShadowUtf8FieldByResolvedIdentity() throws Exception {
        Path evil = writeJava("Evil.java", """
                import java.nio.charset.Charset;
                class Evil {
                    static final Charset UTF_8 = Charset.forName("ISO-8859-1");
                }
                """);
        Path subject = writeJava("Subject.java", """
                import java.nio.charset.Charset;
                class Subject {
                    static final Charset UTF_8 = java.nio.charset.StandardCharsets.UTF_8;
                    static class java { static class nio { static class charset {
                        static class StandardCharsets extends Evil { }
                    } } }
                }
                """);
        AggregateInstallationVerifier.SourceAnalysis analysis = AggregateInstallationVerifier.analyzeCharsetSources(List.of(subject, evil));
        IOException failure = assertThrows(IOException.class,
                () -> AggregateInstallationVerifier.requireResolvedUtf8Field(subject, analysis));
        assertTrue(failure.getMessage().contains("wrong declaring type"), failure.getMessage());
    }

    @Test
    void rejectsFieldEvidenceWhenAnalysisHasDiagnostics() throws Exception {
        Path source = writeJava("Broken.java", """
                import java.nio.charset.Charset;
                class Broken {
                    static final Charset UTF_8 = Missing.UTF_8;
                }
                """);
        AggregateInstallationVerifier.SourceAnalysis analysis = AggregateInstallationVerifier.analyzeCharsetSources(List.of(source));
        IOException failure = assertThrows(IOException.class,
                () -> AggregateInstallationVerifier.requireResolvedUtf8Field(source, analysis));
        assertTrue(failure.getMessage().contains("analysis produced diagnostics"), failure.getMessage());
    }

    private Path writeJava(String fileName, String source) throws Exception {
        Path file = temporary.resolve(fileName);
        AggregateInstallationEvidence.createParentDirectories(file);
        Files.writeString(file, source);
        return file;
    }
}
