/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Runs both cleanup capture/publication and complete-commit regressions through Maven/JUnit. */
class CleanupReviewPublisherTest {
    private static final String ACTION_DIRECTORY = ".github/actions/cleanup-review";
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"cleanup-review.test.cjs", "cleanup-proposal.test.cjs"})
    void completePublisherRegressionSuitePassesWithoutSkippedCases(String suite) throws Exception {
        Path root = root();
        Path output = temporary.resolve("publisher-tests.log");
        Path scenario = root.resolve(ACTION_DIRECTORY).resolve("test").resolve(suite);
        assertTrue(Files.isRegularFile(scenario), "Missing cleanup regression suite: " + scenario);
        Process process = new ProcessBuilder("node", "--test", "--test-reporter=tap", scenario.toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            boolean completed = process.waitFor(30, TimeUnit.SECONDS);
            String report = Files.readString(output, StandardCharsets.UTF_8);
            assertTrue(completed, "Publisher regressions timed out:\n" + report);
            assertEquals(0, process.exitValue(), report);
            assertFalse(report.lines().anyMatch(line -> line.equals("# Subtest: " + scenario)),
                    "Node must execute registered scenarios, not only load an empty suite:\n" + report);
            int tests = summaryCount(report, "tests");
            assertTrue(tests > 0, "The cleanup regression suite must execute tests:\n" + report);
            assertEquals(tests, summaryCount(report, "pass"), report);
            for (String outcome : new String[]{"fail", "cancelled", "skipped", "todo"}) {
                assertEquals(0, summaryCount(report, outcome), report);
            }
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static int summaryCount(String report, String name) {
        var match = Pattern.compile("(?m)^# " + Pattern.quote(name) + " (\\d+)\\r?$").matcher(report);
        assertTrue(match.find(), "Missing TAP summary for " + name + ":\n" + report);
        int count = Integer.parseInt(match.group(1));
        assertFalse(match.find(), "Duplicate TAP summary for " + name + ":\n" + report);
        return count;
    }

    private static Path root() {
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            if (Files.isRegularFile(root.resolve(ACTION_DIRECTORY).resolve("action.yml"))) return root;
        }
        throw new IllegalStateException("Cannot find the cleanup review publisher regression suite");
    }
}
