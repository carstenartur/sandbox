/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/** Binds the fixture identity into JMH's own raw-result parameters before either fork starts. */
public final class FiniteSeriesBenchmarkRunner {
    private FiniteSeriesBenchmarkRunner() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Supply fixture directory and fresh JMH JSON output");
        Path fixture = Path.of(args[0]).toAbsolutePath().normalize(), output = Path.of(args[1]).toAbsolutePath();
        if (!Files.isRegularFile(fixture.resolve("COMPLETED")) || Files.exists(output))
            throw new IOException("Require completed fixtures and fresh JMH output");
        String hash = CorpusFiles.sha256(fixture.resolve("benchmark.properties"));
        // Forks, warmup, duration and measurement counts come from the benchmark annotations.
        new Runner(new OptionsBuilder().include("^" + Pattern.quote(FiniteSeriesBenchmark.class.getName()) + "\\..*$")
                .param("fixtureDirectory", fixture.toString()).param("fixtureManifestSha256", hash)
                .shouldFailOnError(true).addProfiler(GCProfiler.class)
                .resultFormat(ResultFormatType.JSON).result(output.toString()).build()).run();
        CorpusFiles.requireHash(fixture.resolve("benchmark.properties"), hash);
    }
}
