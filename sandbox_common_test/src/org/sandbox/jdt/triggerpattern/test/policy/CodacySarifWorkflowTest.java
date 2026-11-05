/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Executes the workflow's real SARIF handling on the same tools used in CI. */
class CodacySarifWorkflowTest {
    @TempDir
    Path temporary;

    @Test
    void emptyAnalyzerOutputWarnsWhileTheOtherReportIsPreserved() throws Exception {
        Files.writeString(temporary.resolve("results-spotbugs.sarif"),
				"{\"version\":\"2.1.0\",\"runs\":[{\"tool\":{\"driver\":{\"name\":\"SpotBugs\"}},\"results\":[]}]}",
				StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("results-checkstyle.sarif"), "{\"version\":\"2.1.0\",\"runs\":[]}",
				StandardCharsets.UTF_8);

        Result result = runSplitStep();
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("::warning::Codacy checkstyle produced no usable SARIF runs"), result.output());
        assertTrue(Files.readString(temporary.resolve("outputs"), StandardCharsets.UTF_8).contains("spotbugs_run0=true"));
        assertTrue(Files.isRegularFile(temporary.resolve("sarif_split_spotbugs/run-0.sarif")));
        assertFalse(JsonParser.parseString(Files.readString(temporary.resolve("sarif_split_spotbugs/run-0.sarif"), StandardCharsets.UTF_8))
                .getAsJsonObject().has("$schema"));
        assertFalse(Files.exists(temporary.resolve("sarif_split_checkstyle/run-0.sarif")));
    }

    @Test
    void malformedReportingOutputIsVisibleWithoutBreakingTheMavenGate() throws Exception {
        Files.writeString(temporary.resolve("results-spotbugs.sarif"), "not-json", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("results-checkstyle.sarif"),
				"{\"version\":\"2.1.0\",\"runs\":[{\"tool\":{\"driver\":{\"name\":\"Checkstyle\"}},\"results\":[]}]}",
				StandardCharsets.UTF_8);

        Result result = runSplitStep();
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("::warning::Codacy spotbugs produced no usable SARIF runs"), result.output());
        assertTrue(Files.readString(temporary.resolve("outputs"), StandardCharsets.UTF_8).contains("checkstyle_run0=true"));
        assertFalse(Files.exists(temporary.resolve("sarif_split_spotbugs/run-0.sarif")));
    }

    @Test
    void missingReportsAreVisibleWithoutInventingAnUpload() throws Exception {
        Result result = runSplitStep();
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("::warning::Codacy spotbugs produced no usable SARIF runs"), result.output());
        assertTrue(result.output().contains("::warning::Codacy checkstyle produced no usable SARIF runs"), result.output());
        assertFalse(Files.exists(temporary.resolve("sarif_split_spotbugs/run-0.sarif")));
        assertFalse(Files.exists(temporary.resolve("sarif_split_checkstyle/run-0.sarif")));
    }

    @Test
    void invalidRunResultsAreNotUploaded() throws Exception {
        Files.writeString(temporary.resolve("results-checkstyle.sarif"),
				"{\"version\":\"2.1.0\",\"runs\":[{\"tool\":{\"driver\":{\"name\":\"Checkstyle\"}},\"results\":\"BROKEN\"}]}",
				StandardCharsets.UTF_8);
        Result result = runSplitStep();
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("::warning::Codacy checkstyle produced no usable SARIF runs"), result.output());
        assertFalse(Files.exists(temporary.resolve("sarif_split_checkstyle/run-0.sarif")));
    }

    @Test
    void resultWithoutRequiredMessageIsNotUploaded() throws Exception {
        Files.writeString(temporary.resolve("results-checkstyle.sarif"),
				"{\"version\":\"2.1.0\",\"runs\":[{\"tool\":{\"driver\":{\"name\":\"Checkstyle\"}},\"results\":[{}]}]}",
				StandardCharsets.UTF_8);
        Result result = runSplitStep();
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("::warning::Codacy checkstyle produced no usable SARIF runs"), result.output());
        assertFalse(Files.exists(temporary.resolve("sarif_split_checkstyle/run-0.sarif")));
    }

    @Test
    void concatenatedJsonDocumentsAreRejectedAsOneInvalidReport() throws Exception {
        String report = "{\"version\":\"2.1.0\",\"runs\":[{\"tool\":{\"driver\":{\"name\":\"SpotBugs\"}},\"results\":[]}]}";
        Files.writeString(temporary.resolve("results-spotbugs.sarif"), (report + "\n" + report), StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("results-checkstyle.sarif"), report, StandardCharsets.UTF_8);
        Result result = runSplitStep();
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("::warning::Codacy spotbugs produced no usable SARIF runs"), result.output());
        assertTrue(Files.isRegularFile(temporary.resolve("sarif_split_checkstyle/run-0.sarif")));
        assertFalse(Files.exists(temporary.resolve("sarif_split_spotbugs/run-0.sarif")));
    }

    @Test
    void validSchemaReferenceIsPreserved() throws Exception {
        Files.writeString(temporary.resolve("results-checkstyle.sarif"),
				"{\"$schema\":\"https://example.invalid/sarif.json\",\"version\":\"2.1.0\",\"runs\":[{\"tool\":{\"driver\":{\"name\":\"Checkstyle\"}},\"results\":[{\"message\":{\"text\":\"Empty statement\"}}]}]}",
				StandardCharsets.UTF_8);
        Result result = runSplitStep();
        assertEquals(0, result.exitCode(), result.output());
        var split = JsonParser.parseString(Files.readString(temporary.resolve("sarif_split_checkstyle/run-0.sarif"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("https://example.invalid/sarif.json", split.get("$schema").getAsString());
        assertEquals("Empty statement", split.getAsJsonArray("runs").asList().getFirst().getAsJsonObject()
                .getAsJsonArray("results").asList().getFirst().getAsJsonObject().getAsJsonObject("message").get("text").getAsString());
    }

    @Test
    void reportingUploadFailuresWarnWithoutBlockingTheOtherAnalyzer() throws Exception {
        String workflow = Files.readString(repositoryRoot().resolve(".github/workflows/codacy.yml"), StandardCharsets.UTF_8);
        assertFalse(step(workflow, "Build and run Maven analysis").contains("continue-on-error: true"));
        String warning = step(workflow, "Report SARIF upload failures");
        assertTrue(warning.contains("always()"));
        assertTrue(warning.contains("::warning::"));
        for (String tool : new String[] { "SpotBugs", "Checkstyle" }) {
            for (int index = 0; index < 3; index++) {
                String id = "upload_" + tool.toLowerCase(Locale.ROOT) + "_" + index;
                String upload = step(workflow, "Upload " + tool + " SARIF run " + index);
                assertTrue(upload.contains("id: " + id), upload);
                assertTrue(upload.contains("continue-on-error: true"), upload);
                assertTrue(warning.contains("steps." + id + ".outcome == 'failure'"), warning);
            }
        }
    }

    private Result runSplitStep() throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux"));
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/bash")) && Files.isExecutable(Path.of("/usr/bin/jq")));
        Path workflow = repositoryRoot().resolve(".github/workflows/codacy.yml");
        String content = Files.readString(workflow, StandardCharsets.UTF_8);
        String marker = "      - name: Split SARIF into single-run files\n";
        int start = content.indexOf(marker);
        assertTrue(start >= 0, "Missing SARIF split step");
        int command = content.indexOf("        run: |\n", start);
        int end = content.indexOf("\n      - name:", command);
        assertTrue(command > start && end > command, "Cannot locate SARIF split commands");
        String script = content.substring(command + "        run: |\n".length(), end)
                .replaceAll("(?m)^          ", "");
        ProcessBuilder process = new ProcessBuilder("/usr/bin/bash", "-c", script);
        process.redirectErrorStream(true);
        process.environment().put("RUNNER_TEMP", temporary.toString());
        process.environment().put("GITHUB_OUTPUT", temporary.resolve("outputs").toString());
        Process child = process.start();
        String output = new String(child.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return new Result(child.waitFor(), output);
    }

    private static String step(String workflow, String name) {
        String marker = "      - name: " + name + "\n";
        int start = workflow.indexOf(marker);
        assertTrue(start >= 0, "Missing workflow step: " + name);
        int end = workflow.indexOf("\n      - name:", start + marker.length());
        return end < 0 ? workflow.substring(start) : workflow.substring(start, end);
    }

    private static Path repositoryRoot() throws IOException {
        for (Path path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve(".github/workflows/codacy.yml"))) return path;
        }
        throw new IOException("Cannot find the repository workflow");
    }

    private record Result(int exitCode, String output) { }
}
