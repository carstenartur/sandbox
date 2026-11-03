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

/** The pre-measurement fixture digest is an actual parameter in every raw JMH result. */
public final class BlsBenchmarkRunner {
    private BlsBenchmarkRunner() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Supply fixture directory, pinned binary/sources and fresh JMH output");
        Path directory = Path.of(args[0]).toAbsolutePath().normalize(), binary = Path.of(args[1]).toAbsolutePath();
        Path sources = Path.of(args[2]).toAbsolutePath(), output = Path.of(args[3]).toAbsolutePath();
        if (Files.exists(output)) throw new IOException("Refusing to overwrite an earlier BLS measurement");
        String digest = CorpusFiles.sha256(directory.resolve("benchmark.properties"));
        BlsSourceCohort.requireCurrent(directory, binary, sources, digest);
        new Runner(new OptionsBuilder().include("^" + Pattern.quote(BlsBenchmark.class.getName()) + "\\..*$")
                .param("fixtureDirectory", directory.toString()).param("fixtureManifestSha256", digest)
                .param("binaryArchive", binary.toString()).param("sourceArchive", sources.toString())
                .shouldFailOnError(true).addProfiler(GCProfiler.class)
                .resultFormat(ResultFormatType.JSON).result(output.toString()).build()).run();
        BlsSourceCohort.requireCurrent(directory, binary, sources, digest);
    }
}
