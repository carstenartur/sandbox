/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.qa.reporting;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/** Validates append-only execution evidence. Incomplete is never equivalent to successful. */
public final class ExecutionLedgerSummary {
    private enum Event {
        PLAN_START, PLAN_END, TEST_STARTED, CONTAINER_STARTED,
        TEST_FINISHED, CONTAINER_FINISHED, TEST_SKIPPED, CONTAINER_SKIPPED
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected ledger directory and new summary.properties path");
        Path directory = Path.of(args[0]);
        Path output = Path.of(args[1]);
        Path inventoryPath = output.resolveSibling("execution-inventory.tsv");
        if (Files.exists(output) || Files.exists(inventoryPath)) throw new IllegalStateException("Refusing stale/overwritten summary evidence");
        var inventory = new TreeMap<String, Long>();
        long passed = 0, failed = 0, aborted = 0, skipped = 0, failedContainers = 0, skippedContainers = 0, records = 0;
        boolean complete = true;
        java.util.List<Path> files;
        try (var paths = Files.list(directory)) {
            files = paths.filter(p -> p.getFileName().toString().matches("events-[0-9]+-[0-9]+\\.tsv")).sorted().toList();
        }
        if (files.isEmpty()) throw new IllegalStateException("No execution ledgers were produced");
        for (Path file : files) {
            if (Files.size(file) == 0) throw new IllegalStateException("Empty execution ledger: " + file);
            try (var channel = Files.newByteChannel(file)) {
                ByteBuffer last = ByteBuffer.allocate(1);
                channel.position(channel.size() - 1);
                channel.read(last);
                if (last.array()[0] != '\n') throw new IllegalStateException("Truncated execution ledger: " + file);
            }
            long sequence = 0, previousTime = -1;
            boolean ended = false;
            Set<String> active = new HashSet<>(), completed = new HashSet<>();
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                if (!"JDT-EXECUTION-LEDGER\t1".equals(reader.readLine())) throw new IllegalStateException("Unknown ledger schema: " + file);
                for (String line; (line = reader.readLine()) != null;) {
                    String[] fields = line.split("\t", -1);
                    if (ended || fields.length != 9) throw new IllegalStateException("Unexpected event after end / malformed record: " + file);
                    long seq = Long.parseLong(fields[0]), time = Long.parseLong(fields[1]);
                    if (seq != ++sequence || time < previousTime || time < 0) throw new IllegalStateException("Missing/duplicated/non-monotonic event: " + file);
                    previousTime = time;
                    Event event = Event.valueOf(fields[2]);
                    for (int i = 4; i < fields.length; i++) {
                        StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(Base64.getDecoder().decode(fields[i])));
                    }
                    String identity = fields[4]; // Base64 keeps arbitrary IDs unambiguous in the inventory.
                    String state = fields[3];
                    if (seq == 1 && event != Event.PLAN_START) throw new IllegalStateException("Missing PLAN_START");
                    if (event != Event.PLAN_START && event != Event.PLAN_END && identity.isEmpty()) throw new IllegalStateException("Missing event identity");
                    switch (event) {
                        case PLAN_START -> {
                            if (seq != 1 || !state.isEmpty()) throw new IllegalStateException("Duplicate/invalid PLAN_START");
                        }
                        case PLAN_END -> {
                            if (!active.isEmpty() || !state.isEmpty()) throw new IllegalStateException("PLAN_END has unfinished events");
                            ended = true;
                        }
                        case TEST_STARTED, CONTAINER_STARTED -> {
                            if (!state.isEmpty() || completed.contains(identity) || !active.add(identity)) throw new IllegalStateException("Duplicate start");
                        }
                        case TEST_FINISHED, CONTAINER_FINISHED -> {
                            if (!active.remove(identity) || !completed.add(identity)) throw new IllegalStateException("Finish without unique start");
                            if (!Set.of("SUCCESSFUL", "FAILED", "ABORTED").contains(state)) throw new IllegalStateException("Invalid completion state");
                            if (event == Event.TEST_FINISHED) {
                                if (state.equals("SUCCESSFUL")) passed++;
                                else if (state.equals("FAILED")) failed++;
                                else aborted++;
                                inventory.merge(event + "\t" + identity + "\t" + state, 1L, Long::sum);
                            } else if (!state.equals("SUCCESSFUL")) {
                                if (state.equals("FAILED")) failedContainers++;
                                inventory.merge(event + "\t" + identity + "\t" + state, 1L, Long::sum);
                            }
                        }
                        case TEST_SKIPPED, CONTAINER_SKIPPED -> {
                            if (!state.equals("SKIPPED") || active.contains(identity) || !completed.add(identity)) throw new IllegalStateException("Invalid skip");
                            if (event == Event.TEST_SKIPPED) skipped++; else skippedContainers++;
                            inventory.merge(event + "\t" + identity + "\t" + state, 1L, Long::sum);
                        }
                    }
                }
            }
            if (sequence == 0) throw new IllegalStateException("Ledger contains no events");
            records += sequence;
            complete &= ended;
        }
        var summary = new Properties();
        summary.setProperty("complete", Boolean.toString(complete));
        summary.setProperty("successful", Boolean.toString(complete && failed == 0 && failedContainers == 0 && passed > 0));
        summary.setProperty("plans", Integer.toString(files.size()));
        summary.setProperty("events", Long.toString(records));
        summary.setProperty("passedTests", Long.toString(passed));
        summary.setProperty("failedTests", Long.toString(failed));
        summary.setProperty("abortedTests", Long.toString(aborted));
        summary.setProperty("skippedTests", Long.toString(skipped));
        summary.setProperty("failedContainers", Long.toString(failedContainers));
        summary.setProperty("skippedContainers", Long.toString(skippedContainers));
        summary.setProperty("testOutcomes", Long.toString(passed + failed + aborted + skipped));
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.write(inventoryPath, inventory.entrySet().stream().map(e -> e.getKey() + "\t" + e.getValue()).toList(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        try (var writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            summary.store(writer, "Validated native JUnit events; not a replacement for complete XML results");
        }
        System.out.println(summary);
    }
}
