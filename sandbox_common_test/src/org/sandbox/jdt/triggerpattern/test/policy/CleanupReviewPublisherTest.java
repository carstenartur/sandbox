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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Makes the existing publisher behavioral regressions part of the Maven/JUnit gate. */
class CleanupReviewPublisherTest {
    private static final String SUITE = ".github/actions/cleanup-review/test/cleanup-review.test.cjs";
    @TempDir Path temporary;

    @Test
    void completePublisherRegressionSuitePassesWithoutSkippedCases() throws Exception {
        Path root = root();
        Path output = temporary.resolve("publisher-tests.log");
        Process process = new ProcessBuilder("node", "--test", "--test-reporter=tap", root.resolve(SUITE).toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            boolean completed = process.waitFor(30, TimeUnit.SECONDS);
            String report = Files.readString(output, StandardCharsets.UTF_8);
            assertTrue(completed, "Publisher regressions timed out:\n" + report);
            assertEquals(0, process.exitValue(), report);
            int tests = summaryCount(report, "tests");
            assertTrue(tests >= 24, "The complete established publisher regression suite must execute:\n" + report);
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
            if (Files.isRegularFile(root.resolve(SUITE))) return root;
        }
        throw new IllegalStateException("Cannot find the cleanup review publisher regression suite");
    }
}
