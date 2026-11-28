/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;

/** Optional timing run on sources already verified by the pinned upstream fixture. */
final class ReedSolomonEncodingTiming {
    private static final Path EVIDENCE = Path.of("qa/math-optimization/results/backblaze-coding-loops/baseline");
    private static final String DEFAULT = "InputOutputByteTableCodingLoop";
    private static final String RAW_HEADER = "case,variant,fork,ops1,ns1,bytes1,ops2,ns2,bytes2,ops3,ns3,bytes3";

    private record Geometry(String name, int data, int parity, int size, int offset) {}
    private static final List<Geometry> CASES = List.of(
            new Geometry("calibration", 17, 3, 200000, 0),
            new Geometry("small_holdout", 17, 3, 4096, 7),
            new Geometry("large_holdout", 10, 4, 1048576, 13));

    private record Job(Geometry geometry, String variant, int fork, boolean applied) {}
    private record Result(String geometry, String variant, double rate, double ns, double bytes) {}
    private record Summary(String geometry, String variant, double rate, double min, double max,
            double ns, double bytes) {}

    private ReedSolomonEncodingTiming() {}

    static void measure(Path upstreamSources, Path classes, Path results) throws Exception {
        measure(upstreamSources, null, Set.of(), classes, results);
    }

    static void measure(Path upstreamSources, Path overlay, Set<String> changed, Path classes, Path results)
            throws Exception {
        Files.createDirectory(results);
        Files.createDirectory(classes);
        var originalChild = compile(upstreamSources, null, classes.resolve("original"));
        var appliedChild = changed.isEmpty() ? List.<String>of() : compile(upstreamSources, overlay,
                classes.resolve("applied"));
        Path log = results.resolve("last-fork.log");
        var variants = Set.copyOf(invoke(originalChild, List.of("--variants"), log));
        if (variants.size() != 12 || !variants.contains(DEFAULT)) {
            throw new IOException("Unexpected upstream coding loop inventory: " + variants);
        }
        if (!changed.isEmpty() && (!variants.containsAll(changed)
                || !variants.equals(Set.copyOf(invoke(appliedChild, List.of("--variants"), log))))) {
            throw new IOException("Unexpected Apply variant inventory");
        }
        // Replay the preregistered shuffled order. Read only workload, variant and fork ID.
        var jobs = new ArrayList<Job>();
        for (String recorded : Files.readAllLines(EVIDENCE.resolve("forks.csv")).stream().skip(1).toList()) {
            String[] id = recorded.split(",", 4);
            Geometry geometry = CASES.stream().filter(item -> item.name().equals(id[0])).findFirst().orElseThrow();
            int fork = Integer.parseInt(id[2]);
            if (!variants.contains(id[1]) || fork < 1 || fork > 3) {
                throw new IOException("Unexpected fork " + Arrays.toString(id));
            }
            jobs.add(new Job(geometry, id[1], fork, false));
        }
        if (jobs.size() != CASES.size() * variants.size() * 3 || new HashSet<>(jobs).size() != jobs.size()) {
            throw new IOException("Missing or duplicate forks");
        }

        if (!changed.isEmpty()) {
            var candidates = jobs.stream().filter(job -> changed.contains(job.variant()))
                    .map(job -> new Job(job.geometry(), job.variant(), job.fork(), true)).toList();
            jobs.addAll(candidates);
            Collections.shuffle(jobs, new Random(4711));
        }

        var measured = new ArrayList<Result>();
        try (var raw = Files.newBufferedWriter(results.resolve("forks.csv"))) {
            raw.write(RAW_HEADER);
            raw.newLine();
            for (Job job : jobs) {
                Geometry g = job.geometry();
                var child = job.applied() ? appliedChild : originalChild;
                String label = job.applied() ? "Apply-" + job.variant() : job.variant();
                List<String> windows = invoke(child, List.of(job.variant(), String.valueOf(g.data()),
                        String.valueOf(g.parity()), String.valueOf(g.size()), String.valueOf(g.offset())), log);
                if (windows.size() != 3) throw new IOException("Missing windows for " + job);
                var line = new StringBuilder(g.name()).append(',').append(label).append(',').append(job.fork());
                long calls = 0, ns = 0, bytes = 0;
                boolean allocationAvailable = true;
                for (int i = 0; i < windows.size(); i++) {
                    long[] values = Arrays.stream(windows.get(i).split(",")).mapToLong(Long::parseLong).toArray();
                    if (values.length != 4 || values[0] != i + 1 || values[1] < 1 || values[2] < 250_000_000L
                            || values[3] < -1) throw new IOException("Invalid window: " + windows.get(i));
                    for (int field = 1; field < 4; field++) line.append(',').append(values[field]);
                    calls += values[1];
                    ns += values[2];
                    bytes += values[3];
                    allocationAvailable &= values[3] >= 0;
                }
                raw.write(line.toString());
                raw.newLine();
                raw.flush();
                measured.add(new Result(g.name(), label, (double) g.data() * g.size() * calls * 1000 / ns,
                        (double) ns / calls, allocationAvailable ? (double) bytes / calls : Double.NaN));
            }
        }
        var summaries = writeSummary(measured, results.resolve("summary.csv"));
        if (!changed.isEmpty()) writeComparisons(summaries, changed, results.resolve("comparisons.csv"));
        Files.delete(log);
    }

    private static List<String> compile(Path sources, Path overlay, Path classes) throws IOException {
        Files.createDirectory(classes);
        var compile = new ArrayList<>(List.of("--release", "21", "-d", classes.toString()));
        try (var walk = Files.walk(sources)) {
            for (var source : walk.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                var replacement = overlay == null ? source : overlay.resolve(sources.relativize(source));
                compile.add((Files.isRegularFile(replacement) ? replacement : source).toString());
            }
        }
        compile.add(EVIDENCE.resolve("ReedSolomonEncodingBaseline.java").toString());
        if (ToolProvider.getSystemJavaCompiler().run(null, null, null, compile.toArray(String[]::new)) != 0) {
            throw new IOException("Benchmark compilation failed");
        }
        return List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms512m", "-Xmx512m", "-XX:ActiveProcessorCount=2", "-cp",
                classes.toString(), "ReedSolomonEncodingBaseline");
    }

    private static List<String> invoke(List<String> prefix, List<String> args, Path log) throws Exception {
        var command = new ArrayList<>(prefix);
        command.addAll(args);
        var process = new ProcessBuilder(command).redirectOutput(log.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        try {
            if (!process.waitFor(120, TimeUnit.SECONDS)) throw new IOException("Timed out: " + args);
            if (process.exitValue() != 0) throw new IOException("Fork failed: " + args);
            return Files.readAllLines(log);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static List<Summary> writeSummary(List<Result> measured, Path path) throws IOException {
        List<Summary> summaries = new ArrayList<>();
        var order = Comparator.comparingDouble(Summary::rate).reversed().thenComparing(Summary::variant);
        for (Geometry geometry : CASES) {
            Map<String, List<Result>> byVariant = new LinkedHashMap<>();
            for (Result fork : measured) {
                if (geometry.name().equals(fork.geometry())) {
                    byVariant.computeIfAbsent(fork.variant(), ignored -> new ArrayList<>()).add(fork);
                }
            }
            List<Summary> group = new ArrayList<>();
            for (var entry : byVariant.entrySet()) {
                var forks = entry.getValue();
                var rates = forks.stream().mapToDouble(Result::rate).sorted().toArray();
                double ns = forks.stream().mapToDouble(Result::ns).sorted().toArray()[1];
                double bytes = forks.stream().anyMatch(row -> Double.isNaN(row.bytes())) ? Double.NaN
                        : forks.stream().mapToDouble(Result::bytes).sorted().toArray()[1];
                group.add(new Summary(geometry.name(), entry.getKey(), rates[1], rates[0], rates[2], ns, bytes));
            }
            group.sort(order);
            summaries.addAll(group);
        }
        String winner = summaries.stream().filter(row -> !row.variant().startsWith("Apply-"))
                .findFirst().orElseThrow().variant();
        try (var csv = Files.newBufferedWriter(path)) {
            csv.write("case,variant,default,calibration_selected,median_MB_s,min_MB_s,max_MB_s,median_ns_per_encode,"
                    + "median_allocated_bytes_per_encode\n");
            for (Summary row : summaries) {
                csv.write(String.format(Locale.ROOT, "%s,%s,%b,%b,%.3f,%.3f,%.3f,%.3f,%s%n",
                        row.geometry(), row.variant(), DEFAULT.equals(row.variant()), winner.equals(row.variant()),
                        row.rate(), row.min(), row.max(), row.ns(), Double.isNaN(row.bytes()) ? ""
                                : String.format(Locale.ROOT, "%.3f", row.bytes())));
            }
        }
        return summaries;
    }

    private static void writeComparisons(List<Summary> summaries, Set<String> changed, Path path) throws IOException {
        try (var csv = Files.newBufferedWriter(path)) {
            csv.write("case,candidate,comparator,original_variant,candidate_median,candidate_min,candidate_max,"
                    + "original_median,original_min,original_max,median_ratio,range_comparison\n");
            for (Geometry geometry : CASES) {
                var originals = new LinkedHashMap<String, Summary>();
                for (var row : summaries) {
                    if (row.geometry().equals(geometry.name()) && !row.variant().startsWith("Apply-")) {
                        originals.put(row.variant(), row);
                    }
                }
                var best = originals.firstEntry().getValue();
                for (String variant : changed.stream().sorted().toList()) {
                    var candidate = summaries.stream().filter(row -> row.geometry().equals(geometry.name())
                            && row.variant().equals("Apply-" + variant)).findFirst().orElseThrow();
                    var comparisons = new LinkedHashMap<String, Summary>();
                    comparisons.put("same_implementation", originals.get(variant));
                    comparisons.put("upstream_default", originals.get(DEFAULT));
                    comparisons.put("best_original", best);
                    for (var entry : comparisons.entrySet()) {
                        var control = entry.getValue();
                        String outcome = "inconclusive";
                        if (candidate.min() > control.max()) outcome = "faster";
                        if (candidate.max() < control.min()) outcome = "slower";
                        csv.write(String.format(Locale.ROOT, "%s,%s,%s,%s,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.6f,%s%n",
                                geometry.name(), candidate.variant(), entry.getKey(), control.variant(),
                                candidate.rate(), candidate.min(), candidate.max(), control.rate(), control.min(),
                                control.max(), candidate.rate() / control.rate(), outcome));
                    }
                }
            }
        }
    }
}
