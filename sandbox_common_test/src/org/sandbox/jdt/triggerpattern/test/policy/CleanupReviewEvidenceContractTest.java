/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Executes the Linux composite-action shell adapter, not a second CI test runner. */
class CleanupReviewEvidenceContractTest {
    private static final String ACTION = ".github/actions/cleanup-review/action.yml";
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(ints = {0, 17, 23})
    @EnabledOnOs(OS.LINUX)
    void cleanupExitStatusAndDiagnosticsSurviveEveryExit(int exitCode) throws Exception {
        String cleanup = step(action(), "Run configured Sandbox cleanup");
        String marker = "      run: |\n";
        assertTrue(cleanup.contains(marker));
        String command = cleanup.substring(cleanup.indexOf(marker) + marker.length()).stripTrailing();
        command = command.lines().map(line -> {
            assertTrue(line.isBlank() || line.startsWith("        "), line);
            return line.isBlank() ? "" : line.substring(8);
        }).collect(java.util.stream.Collectors.joining("\n"));
        for (String input : new String[]{"base-sha", "head-sha", "config-file", "image", "scope", "source-mode"}) {
            command = command.replace("${{ inputs." + input + " }}", "fixture");
        }
        assertFalse(command.contains("${{"), "Every action expression must be resolved before shell execution");

        Path tools = Files.createDirectory(temporary.resolve("action"));
        Path output = temporary.resolve("evidence");
        Path githubOutput = Files.createFile(temporary.resolve("github-output"));
        Path runner = tools.resolve("run-cleanup-review.sh");
        Files.writeString(runner, """
                #!/usr/bin/env bash
                set -euo pipefail
                echo "fixture stdout"
                echo "fixture stderr" >&2
                if [[ "$FIXTURE_EXIT" == 23 ]]; then exit 23; fi
                # The real cleanup runner clears its evidence directory on initialization.
                rm -rf -- "$OUTPUT_DIR"
                mkdir -p "$OUTPUT_DIR/manifests"
                printf 'partial report\\n' > "$OUTPUT_DIR/manifests/project-0.files"
                if [[ "$FIXTURE_EXIT" == 0 ]]; then
                  printf 'has_changes=false\\noutput_dir=%s\\n' "$OUTPUT_DIR" >> "$GITHUB_OUTPUT"
                fi
                exit "$FIXTURE_EXIT"
                """, StandardCharsets.UTF_8);
        assertTrue(runner.toFile().setExecutable(true));
        Path shell = Files.writeString(temporary.resolve("step.sh"), command, StandardCharsets.UTF_8);
        ProcessBuilder builder = new ProcessBuilder("bash", "--noprofile", "--norc", "-e", "-o", "pipefail", shell.toString());
        builder.environment().putAll(Map.of("GITHUB_ACTION_PATH", tools.toString(), "OUTPUT_DIR", output.toString(),
                "GITHUB_OUTPUT", githubOutput.toString(), "FIXTURE_EXIT", Integer.toString(exitCode)));
        Path console = temporary.resolve("console.log");
        Process process = builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Action adapter did not terminate");
            assertEquals(exitCode, process.exitValue(), Files.readString(console));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
        Path log = output.resolve("cleanup.log");
        assertTrue(Files.isRegularFile(log), "The failing action must retain its console log");
        String diagnostic = Files.readString(log, StandardCharsets.UTF_8);
        assertTrue(diagnostic.contains("fixture stdout"));
        assertTrue(diagnostic.contains("fixture stderr"));
        if (exitCode != 23) {
            assertEquals("partial report\n", Files.readString(output.resolve("manifests/project-0.files")));
        }
        if (exitCode != 0) {
            assertFalse(Files.readString(githubOutput).contains("has_changes=true"),
                    "A failure must not manufacture publishable changes");
        }
    }

    @Test
    void uploadRetainsFailuresWithoutPublishingAnUnfinishedCleanup() throws Exception {
        String yaml = action();
        String upload = step(yaml, "Upload complete cleanup evidence");
        assertTrue(upload.contains("always()"));
        assertTrue(upload.contains("inputs.upload-artifact == 'true'"));
        assertTrue(upload.contains("(steps.cleanup.outputs.has_changes == 'true' || steps.cleanup.outcome == 'failure')"),
                "Failed cleanup must upload evidence even when it produced no has_changes output");
        assertTrue(upload.contains("path: ${{ runner.temp }}/sandbox-cleanup-review-${{ github.run_id }}-${{ github.run_attempt }}"),
                "The failure upload must not depend on outputs produced only at successful script completion");
        String cleanup = step(yaml, "Run configured Sandbox cleanup");
        assertFalse(cleanup.contains("continue-on-error"));
        String prepare = condition(step(yaml, "Prepare complete grouped review"));
        assertTrue(prepare.contains("steps.cleanup.outcome == 'success'"),
                "A failed cleanup must not prepare its partial diff for publication");
        assertTrue(prepare.contains("steps.cleanup.outputs.has_changes == 'true'"),
                "Only a completed cleanup with changes should prepare a review");
        String publish = condition(step(yaml, "Publish complete cleanup review"));
        assertTrue(publish.contains("steps.prepare.outcome == 'success'"),
                "Publication must require successfully prepared complete evidence");
        assertTrue(publish.contains("!cancelled()"), "A cancelled run must not publish a review");
        // always() is safe behind these gates: an upload failure must not hide a ready review.
    }

    private static String action() throws Exception {
        for (Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath(); root != null; root = root.getParent()) {
            Path candidate = root.resolve(ACTION);
            if (Files.isRegularFile(candidate)) return Files.readString(candidate, StandardCharsets.UTF_8);
        }
        throw new IllegalStateException("Cannot locate the cleanup review action");
    }

    private static String step(String yaml, String name) {
        var match = Pattern.compile("(?m)^    - name: " + Pattern.quote(name) + "\\r?$").matcher(yaml);
        assertTrue(match.find(), "Missing action step " + name);
        int start = match.start(), contentStart = match.end();
        assertFalse(match.find(), "Duplicate action step " + name);
        var next = Pattern.compile("(?m)^    - name: ").matcher(yaml);
        return yaml.substring(start, next.find(contentStart) ? next.start() : yaml.length());
    }

    private static String condition(String step) {
        var match = Pattern.compile("(?m)^      if: (.+)\\r?$").matcher(step);
        assertTrue(match.find(), "Missing action step condition: " + step);
        String condition = match.group(1);
        assertFalse(match.find(), "Duplicate action step condition: " + step);
        return condition;
    }
}
