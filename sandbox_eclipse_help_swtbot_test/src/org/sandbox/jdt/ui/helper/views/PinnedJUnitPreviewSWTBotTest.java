/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

/** Replay the file-scoped JUnit plan from the same pin and actual paired upstream test execution. */
public class PinnedJUnitPreviewSWTBotTest {
    private static final String PROJECT = "org.eclipse.jdt.ui.tests";
    private static final String SOURCE = "ui/org/eclipse/jdt/ui/tests/core/StringsTest.java";
    private static final String CLASS = "org.eclipse.jdt.ui.tests.core.StringsTest";
    private static final String TEST = "removeTrailingCharacters";
    private static final String PROFILE = "junit4-to-jupiter.properties";

    @Test
    public void replayPinnedStrictMigrationAndItsUpstreamTest() throws Exception {
        Path evidence = Path.of(System.getenv("SANDBOX_STATUS_EVIDENCE"));
        Path corpus = Path.of(System.getenv("SANDBOX_JUNIT_CORPUS_EVIDENCE"));
        JsonObject provenance = json(corpus.resolve("provenance.json"));
        assertEquals("pinned-real-jdt-ui-junit4-before-after", provenance.get("evidenceType").getAsString());
        assertEquals("strict", provenance.get("mode").getAsString());
        assertEquals("PASS", provenance.get("result").getAsString());
        var revision = provenance.getAsJsonObject("jdtUi");
        var sourcePin = PinnedFilePreviewDriver.properties(SandboxCheckout.locate("sandbox.repository.root")
                .resolve("qa/upstream-jdt/pins.env"));
        assertEquals(sourcePin.getProperty("PIN_JDT_UI_COMMIT"), revision.get("commit").getAsString());
        assertEquals(revision.get("commit"), revision.get("originalHead"));
        assertEquals(sourcePin.getProperty("PIN_JDT_UI_REF"), revision.get("ref").getAsString());
        for (String key : List.of("baselineMavenExitCode", "migratedMavenExitCode", "testInventoryComparisonExitCode")) {
            assertEquals(0, provenance.get(key).getAsInt(), key);
        }
        JsonObject artifacts = provenance.getAsJsonObject("artifacts");
        assertEquals(PROFILE, artifacts.get("cleanupProfile").getAsString());
        Path config = SandboxCheckout.locate("sandbox.repository.root").resolve("qa/upstream-jdt/" + PROFILE);
        verifyDigest(artifacts, "cleanupProfileSha256", config);
        Path report = corpus.resolve("cleanup-check-report.json");
        verifyDigest(artifacts, "checkReportSha256", report);
        JsonObject headless = json(report);
        assertEquals(0, headless.get("errorCount").getAsInt());
        assertTrue(headless.getAsJsonArray("changedFiles").asList().stream().anyMatch(file ->
                file.getAsString().endsWith("/" + SOURCE) || file.getAsString().equals(SOURCE)));
        Path migration = corpus.resolve("migration.patch");
        verifyDigest(artifacts, "migrationPatchSha256", migration);
        String relative = PROJECT + "/" + SOURCE;
        String header = "--- a/" + relative + "\n+++ b/" + relative + "\n";
        String all = Files.readString(migration);
        int begin = all.indexOf(header);
        assertTrue(begin >= 0, relative);
        int end = all.indexOf("diff --git ", begin);
        String patch = all.substring(begin, end >= 0 ? end : all.length()).replace(header,
                "--- a/" + SOURCE + "\n+++ b/" + SOURCE + "\n");
        assertEquals(patch, Files.readString(evidence.resolve("junit/cleanup-check.patch")),
                "The screenshot selection must reproduce the patch exercised in the full before/after build");
        Path inventory = corpus.resolve("test-inventory-comparison.json");
        verifyDigest(artifacts, "testInventorySha256", inventory);
        JsonObject comparison = json(inventory);
        assertEquals("PASS", comparison.get("result").getAsString());
        assertEquals(comparison.get("baseline"), comparison.get("migrated"));
        for (String phase : List.of("baseline", "migrated")) {
            assertEquals(List.of(TEST), passedSelectedTests(corpus.resolve(phase), CLASS), phase);
        }

        var scenario = new CleanupScreenshotScenario("pinned-junit-strings", PreviewContract.FILE_COMBINED_DIFF,
                PROJECT, Set.of(SOURCE), Set.of(SOURCE));
        PinnedFilePreviewDriver.compare(scenario, SOURCE, PROFILE, "org.junit.jupiter.api.Test",
                "import static org.junit.Assert.assertEquals;", "import static org.junit.jupiter.api.Assertions.assertEquals;",
                original -> original.replace("import static org.junit.Assert.assertEquals;",
                        "import static org.junit.jupiter.api.Assertions.assertEquals;")
                        .replace("import org.junit.Test;", "import org.junit.jupiter.api.Test;"),
                "junit", "junit-strings-pinned", "sandbox_junit_cleanup_help");
        // The corpus report contains agent-local paths and timestamps. Retain that run-specific link beside the PNG.
        Files.copy(corpus.resolve("provenance.json"), evidence.resolve("junit/upstream-before-after.provenance.json"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    static List<String> passedSelectedTests(Path phase, String className) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var builder = factory.newDocumentBuilder();
        var passed = new ArrayList<String>();
        try (var paths = Files.walk(phase)) {
            var reports = phase.getFileSystem().getPathMatcher("glob:TEST-*.xml");
            for (Path file : paths.filter(Files::isRegularFile)
                    .filter(path -> reports.matches(phase.relativize(path).getFileName())).toList()) {
                var cases = builder.parse(file.toFile()).getElementsByTagName("testcase");
                for (int i = 0; i < cases.getLength(); i++) {
                    Element test = (Element) cases.item(i);
                    if (className.equals(test.getAttribute("classname"))) {
                        for (String status : List.of("failure", "error", "skipped")) {
                            assertEquals(0, test.getElementsByTagName(status).getLength(), file.toString());
                        }
                        passed.add(test.getAttribute("name"));
                    }
                }
            }
        }
        passed.sort(String::compareTo);
        return passed;
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static void verifyDigest(JsonObject artifacts, String key, Path file) throws Exception {
        assertEquals(artifacts.get(key).getAsString(),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))), key);
    }
}
