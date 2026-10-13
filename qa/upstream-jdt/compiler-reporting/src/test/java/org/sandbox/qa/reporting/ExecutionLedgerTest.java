/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.qa.reporting;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/** Uses actual launcher callbacks and an abruptly terminated child JVM, not mocked events. */
@Execution(ExecutionMode.SAME_THREAD)
class ExecutionLedgerTest {
    @TempDir Path directory;

    public static class Outcomes {
        @Test void passes() { }
        @Test void fails() { fail("expected failure with\nmultiple lines\tand ünicode"); }
        @Test void errors() { throw new IllegalArgumentException("expected error"); }
        @Test void aborts() { Assumptions.assumeTrue(false, "expected assumption"); }
        @Test @Disabled("expected skipped method") void disabled() { fail("must not run"); }
    }
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    public static class AbruptFixture {
        @Test @Order(1) void completedBeforeTermination() { }
        @Test @Order(2) void stopProcess() {
            if (!Boolean.getBoolean("ledger.child")) throw new IllegalStateException("Only run in isolated child");
            Runtime.getRuntime().halt(23);
        }
    }
    public static class Child {
        public static void main(String[] args) {
            LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectClass(AbruptFixture.class)).build());
        }
    }

    @Test void recordsEachOutcomeAndACompletionMarker() throws Exception {
        withLedger(() -> LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(Outcomes.class)).build()));
        List<String[]> entries = records();
        assertEquals(1, count(entries, "PLAN_START", ""));
        assertEquals(1, count(entries, "PLAN_END", ""));
        assertEquals(1, count(entries, "TEST_FINISHED", "SUCCESSFUL"));
        assertEquals(2, count(entries, "TEST_FINISHED", "FAILED"));
        assertEquals(1, count(entries, "TEST_FINISHED", "ABORTED"));
        assertEquals(1, count(entries, "TEST_SKIPPED", "SKIPPED"));
        assertTrue(entries.stream().map(row -> decode(row[8])).anyMatch(s -> s.contains("multiple lines\tand ünicode")));
    }

    @Test void abruptProcessExitRetainsCompletedTestButCannotClaimCompleteRun() throws Exception {
        Path output = directory.resolve("child.log");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dledger.child=true", "-Djdt.qa.progress.dir=" + directory,
                "-cp", classpath, Child.class.getName()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(child.waitFor(30, TimeUnit.SECONDS), "Child did not finish: " + output);
            assertEquals(23, child.exitValue(), () -> read(output));
        } finally { if (child.isAlive()) child.destroyForcibly().waitFor(); }
        List<String[]> entries = records();
        assertEquals(1, count(entries, "TEST_FINISHED", "SUCCESSFUL"));
        assertEquals(0, count(entries, "PLAN_END", ""), "An interrupted run must remain visibly incomplete");
        assertTrue(entries.stream().filter(r -> r[2].equals("TEST_FINISHED"))
                .allMatch(r -> decode(r[4]).contains("completedBeforeTermination")));
    }

    @Test void incompleteSummaryNeverReportsSuccess() throws Exception {
        withLedger(() -> LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(Outcomes.class)).build()));
        Path ledger;
        try (var paths = Files.list(directory)) { ledger = paths.filter(p -> p.getFileName().toString().startsWith("events-")).findFirst().orElseThrow(); }
        var lines = Files.readAllLines(ledger);
        assertTrue(lines.getLast().contains("\tPLAN_END\t"));
        Files.write(ledger, lines.subList(0, lines.size() - 1));
        Path output = directory.resolve("summary.properties");
        ExecutionLedgerSummary.main(new String[]{directory.toString(), output.toString()});
        var properties = new java.util.Properties();
        try (var reader = Files.newBufferedReader(output)) { properties.load(reader); }
        assertEquals("false", properties.getProperty("complete"));
        assertEquals("false", properties.getProperty("successful"));
        assertEquals("5", properties.getProperty("testOutcomes"));
    }

    @Test void disabledByDefaultCreatesNoEvidence() throws Exception {
        assertNull(System.getProperty("jdt.qa.progress.dir"), "Test requires unconfigured launcher");
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass(Outcomes.class)).build());
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test void summaryDistinguishesCompletionFromSuccess() throws Exception {
        withLedger(() -> LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(Outcomes.class)).build()));
        Path output = directory.resolve("summary.properties");
        ExecutionLedgerSummary.main(new String[]{directory.toString(), output.toString()});
        assertTrue(Files.isRegularFile(output), "Missing validated ledger summary");
        var summary = new java.util.Properties();
        try (var reader = Files.newBufferedReader(output)) { summary.load(reader); }
        assertEquals("true", summary.getProperty("complete"));
        assertEquals("2", summary.getProperty("failedTests"));
        assertEquals("false", summary.getProperty("successful"));
        assertEquals("5", summary.getProperty("testOutcomes"));
        assertEquals(5, Files.readAllLines(directory.resolve("execution-inventory.tsv")).size());
    }

    @Test void summaryRejectsTruncatedOrDuplicatedEvents() throws Exception {
        withLedger(() -> LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(Outcomes.class)).build()));
        Path ledger;
        try (var files = Files.list(directory)) { ledger = files.filter(p -> p.toString().endsWith(".tsv")).findFirst().orElseThrow(); }
        String original = Files.readString(ledger);
        Files.writeString(ledger, original.substring(0, original.length() - 1));
        Path output = directory.resolve("summary.properties");
        assertThrows(IllegalStateException.class, () -> ExecutionLedgerSummary.main(new String[]{directory.toString(), output.toString()}));
        assertFalse(Files.exists(output), "Invalid evidence must not produce a summary");
        List<String> lines = original.lines().toList();
        Files.writeString(ledger, String.join("\n", lines.subList(0, 2)) + "\n" + lines.get(1) + "\n" + String.join("\n", lines.subList(2, lines.size())) + "\n");
        assertThrows(IllegalStateException.class, () -> ExecutionLedgerSummary.main(new String[]{directory.toString(), output.toString()}));
        assertFalse(Files.exists(output));
    }
    private void withLedger(Runnable action) {
        String previous = System.getProperty("jdt.qa.progress.dir");
        System.setProperty("jdt.qa.progress.dir", directory.toString());
        try { action.run(); } finally {
            if (previous == null) System.clearProperty("jdt.qa.progress.dir");
            else System.setProperty("jdt.qa.progress.dir", previous);
        }
    }
    private List<String[]> records() throws Exception {
        List<Path> ledgers;
        try (var files = Files.list(directory)) { ledgers = files.filter(p -> p.toString().endsWith(".tsv")).toList(); }
        assertEquals(1, ledgers.size(), "Missing independently flushed execution ledger");
        List<String> lines = Files.readAllLines(ledgers.getFirst());
        assertEquals("JDT-EXECUTION-LEDGER\t1", lines.getFirst());
        List<String[]> entries = lines.subList(1, lines.size()).stream().map(line -> line.split("\t", -1)).toList();
        for (int i = 0; i < entries.size(); i++) {
            assertEquals(9, entries.get(i).length, "Malformed or incomplete record");
            assertEquals(i + 1L, Long.parseLong(entries.get(i)[0]), "Lost/duplicated event");
        }
        return entries;
    }
    private static long count(List<String[]> rows, String event, String state) {
        return rows.stream().filter(row -> row[2].equals(event) && row[3].equals(state)).count();
    }
    private static String decode(String encoded) { return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8); }
    private static String read(Path path) { try { return Files.readString(path); } catch (Exception e) { return e.toString(); } }
}
