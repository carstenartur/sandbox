/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GuardedRootBenchmarkTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"root-qualification.properties", "original/BLS12_381G2HashToCurve.java", "generated/classes/Target.class"})
    void rejectsChangesAfterQualification(String modified) throws Exception {
        String digest = writeExport();
        GuardedRootBenchmark.requireCurrent(directory, digest);
        Files.writeString(directory.resolve(modified), "different bytes");
        assertThrows(IOException.class, () -> GuardedRootBenchmark.requireCurrent(directory, digest));
    }

    @Test void preservesAnExistingResult() throws Exception {
        writeExport();
        Path output = Files.writeString(directory.resolve("results.json"), "previous JMH result");
        assertThrows(FileAlreadyExistsException.class,
                () -> GuardedRootBenchmark.main(new String[]{directory.toString(), output.toString()}));
        assertEquals("previous JMH result", Files.readString(output));
    }

    @ParameterizedTest
    @EnabledIfSystemProperty(named = "sandbox.math.bc.root.benchmarkExport", matches = ".+")
    @ValueSource(strings = {"sqrtOrSqrtZ", "hashToCurve32"})
    void runsTheQualifiedPathsInSeparateLoaders(String workload) throws Exception {
        var benchmark = new GuardedRootBenchmark();
        benchmark.exportDirectory = System.getProperty("sandbox.math.bc.root.benchmarkExport");
        benchmark.qualificationSha256 = CorpusFiles.sha256(Path.of(benchmark.exportDirectory, "root-qualification.properties"));
        benchmark.workload = workload;
        benchmark.setup();
        try {
            assertNotSame(benchmark.original().getClass().getClassLoader(), benchmark.generated().getClass().getClassLoader());
        } finally {
            benchmark.close();
        }
    }

    private String writeExport() throws Exception {
        var receipt = new Properties();
        receipt.setProperty("upstreamCommit", "6560e91d0a164b8ab58266f141ee6daeec21a340");
        for (String variant : new String[]{"original", "generated"}) {
            Path output = Files.createDirectory(directory.resolve(variant));
            Path source = Files.writeString(output.resolve("BLS12_381G2HashToCurve.java"), variant + " source");
            Path classes = Files.createDirectory(output.resolve("classes"));
            Path bytecode = Files.writeString(classes.resolve("Target.class"), variant + " class bytes");
            receipt.setProperty(variant + "SourceSha256", CorpusFiles.sha256(source));
            byte[] input = ("Target.class\0" + CorpusFiles.sha256(bytecode) + "\n").getBytes(StandardCharsets.UTF_8);
            receipt.setProperty(variant + "ClassesSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)));
        }
        Path path = directory.resolve("root-qualification.properties");
        try (var writer = Files.newBufferedWriter(path)) {
            receipt.store(writer, null);
        }
        return CorpusFiles.sha256(path);
    }
}
