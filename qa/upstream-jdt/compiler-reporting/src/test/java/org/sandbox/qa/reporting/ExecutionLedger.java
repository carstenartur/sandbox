/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.qa.reporting;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * Opt-in diagnostic ledger independent of Surefire XML flushing.
 * Every event is appended and flushed once. A PLAN_END marks normal completion,
 * not success. An interrupted run never acquires a synthetic completion marker.
 * This is process-crash evidence, not an fsync/power-loss durability guarantee.
 */
public final class ExecutionLedger implements TestExecutionListener {
    private static final AtomicLong RUNS = new AtomicLong();
    private BufferedWriter writer;
    private long sequence;
    private long started;

    @Override public synchronized void testPlanExecutionStarted(TestPlan plan) {
        String configured = System.getProperty("jdt.qa.progress.dir");
        if (configured == null || configured.isBlank()) return;
        if (writer != null) throw new IllegalStateException("Previous execution ledger is still open");
        try {
            Path directory = Path.of(configured).toAbsolutePath();
            Files.createDirectories(directory);
            Path file = directory.resolve("events-" + ProcessHandle.current().pid() + "-" + RUNS.incrementAndGet() + ".tsv");
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            writer.write("JDT-EXECUTION-LEDGER\t1\n");
            sequence = 0;
            started = System.nanoTime();
            record("PLAN_START", "", null, "");
        } catch (IOException e) { throw new IllegalStateException("Cannot create execution ledger", e); }
    }
    @Override public synchronized void executionStarted(TestIdentifier id) {
        record(id.isTest() ? "TEST_STARTED" : "CONTAINER_STARTED", "", id, "");
    }
    @Override public synchronized void executionSkipped(TestIdentifier id, String reason) {
        record(id.isTest() ? "TEST_SKIPPED" : "CONTAINER_SKIPPED", "SKIPPED", id, reason);
    }
    @Override public synchronized void executionFinished(TestIdentifier id, TestExecutionResult result) {
        if (writer == null) return;
        String problem = result.getThrowable().map(t -> t.getClass().getName() + ": " + t.getMessage()).orElse("");
        record(id.isTest() ? "TEST_FINISHED" : "CONTAINER_FINISHED", result.getStatus().name(), id, problem);
    }
    @Override public synchronized void testPlanExecutionFinished(TestPlan plan) {
        if (writer == null) return;
        try {
            record("PLAN_END", "", null, "");
        } finally {
            try { writer.close(); } catch (IOException e) { throw new IllegalStateException("Cannot close execution ledger", e); }
            finally { writer = null; }
        }
    }
    private void record(String event, String state, TestIdentifier id, String problem) {
        if (writer == null) return;
        try {
            writer.write(++sequence + "\t" + (System.nanoTime() - started) + "\t" + event + "\t" + state + "\t"
                    + encode(id == null ? "" : id.getUniqueId()) + "\t"
                    + encode(id == null ? "" : id.getParentId().orElse("")) + "\t"
                    + encode(id == null ? "" : id.getSource().map(Object::toString).orElse("")) + "\t"
                    + encode(id == null ? "" : id.getDisplayName()) + "\t" + encode(problem) + "\n");
            writer.flush();
        } catch (IOException e) { throw new IllegalStateException("Cannot append execution ledger", e); }
    }
    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
