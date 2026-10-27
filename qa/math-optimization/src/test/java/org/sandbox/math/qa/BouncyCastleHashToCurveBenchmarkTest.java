/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BouncyCastleHashToCurveBenchmarkTest {
    private static final String ENTRY = "org/bouncycastle/crypto/hash2curve/impl/SimplifiedShallueVanDeWoestijneMapToCurve.java";
    @TempDir static Path temporary;
    private static Path scan, binary;

    @BeforeAll static void generateRealFullClassWithTheProductionScanner() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("sandbox.math.bc.jar") != null
                && System.getProperty("sandbox.math.bc.sourceArchive") != null,
                "Full BC benchmark contract: use the standalone Maven qualification or supply both pinned archive properties");
        binary = Path.of(System.getProperty("sandbox.math.bc.jar"));
        Path sources = Path.of(System.getProperty("sandbox.math.bc.sourceArchive"));
        scan = temporary.resolve("scan");
        ProjectMathScan.main(new String[] {binary.toString(), sources.toString(),
                "qa/math-optimization/profiles/bc-mathematical.properties", scan.toString(), CorpusFiles.sha256(sources), "17", ENTRY});
    }

    @Test void fourActualPublicCallPathsUseDistinctCompiledVariantsAndAgree() throws Exception {
        for (String profile : List.of("P256_XMD_SHA_256", "P384_XMD_SHA_384", "P521_XMD_SHA_512",
                "BLS12_381_G1_XMD_SHA_256_SSWU_RO")) {
            try (var inputs = inputs(scan, profile)) {
                assertDoesNotThrow(inputs::setup);
                assertNotSame(inputs.original.getClass().getClassLoader(), inputs.generated.getClass().getClassLoader());
                assertArrayEquals(inputs.original.apply(new byte[0]), inputs.generated.apply(new byte[0]));
                assertArrayEquals(inputs.original.apply(new byte[257]), inputs.generated.apply(new byte[257]));
            }
        }
    }
    @Test void modifiedGeneratedSourceFailsBeforeMeasurement() throws Exception {
        Path copy = fixtureCopy("modified");
        Files.writeString(copy.resolve("generated").resolve(ENTRY), "// modified\n", java.nio.file.StandardOpenOption.APPEND);
        try (var inputs = inputs(copy, "P256_XMD_SHA_256")) { assertThrows(IOException.class, inputs::setup); }
    }
    @Test void genericSqrtRatioRetriesConnectedModularWindowsBeforeSingletons() throws Exception {
        Path sources = Path.of(System.getProperty("sandbox.math.bc.sourceArchive")), output = temporary.resolve("generic-sqrt");
        String entry = "org/bouncycastle/crypto/hash2curve/impl/GenericSqrtRatioCalculator.java";
        ProjectMathScan.main(new String[] {binary.toString(), sources.toString(),
                "qa/math-optimization/profiles/bc-mathematical.properties", output.toString(), CorpusFiles.sha256(sources), "17", entry});
        var report = new com.fasterxml.jackson.databind.ObjectMapper().readTree(output.resolve("review.json").toFile());
        assertTrue(report.toString().contains("MODULAR_WINDOW_RETRY"));
        assertTrue(report.path("acceptedRegions").asInt() > 0);
        assertTrue(Files.isRegularFile(output.resolve("generated").resolve(entry)));
    }
    @Test void binaryMismatchAndUnchangedSourcesCannotMasqueradeAsAQualifiedComparison() throws Exception {
        for (String kind : List.of("binary", "unchanged", "profile", "sources", "stale-report")) {
            Path copy = fixtureCopy(kind);
            var metadata = new Properties();
            try (var in = Files.newBufferedReader(copy.resolve("benchmark.properties"))) { metadata.load(in); }
            if (kind.equals("binary")) metadata.setProperty("binarySha256", "0".repeat(64));
            else if (kind.equals("profile") || kind.equals("sources")) metadata.setProperty(kind + "Sha256", "0".repeat(64));
            else if (kind.equals("stale-report")) {
                Files.writeString(copy.resolve("generated").resolve(ENTRY), "// different source\n", java.nio.file.StandardOpenOption.APPEND);
                metadata.setProperty("generated." + ENTRY, CorpusFiles.sha256(copy.resolve("generated").resolve(ENTRY)));
            } else {
                Files.copy(copy.resolve("original").resolve(ENTRY), copy.resolve("generated").resolve(ENTRY),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                metadata.setProperty("generated." + ENTRY, metadata.getProperty("original." + ENTRY));
            }
            try (var out = Files.newBufferedWriter(copy.resolve("benchmark.properties"))) { metadata.store(out, "negative control"); }
            try (var inputs = inputs(copy, "P256_XMD_SHA_256")) { assertThrows(IOException.class, inputs::setup, kind); }
        }
    }
    @ParameterizedTest
    @ValueSource(strings = {"bc-strict", "bc-mathematical"})
    void additionalStoresDoNotDiluteEstablishedRegionBudgets(String profile) throws Exception {
        Path sources = Path.of(System.getProperty("sandbox.math.bc.sourceArchive")), output = temporary.resolve("retained-regions-" + profile);
        var expected = java.util.Map.of("org/bouncycastle/pqc/crypto/hawk/HawkEngine.java", 18,
                "org/bouncycastle/pqc/crypto/haetae/HAETAEEngine.java", 1, "org/bouncycastle/pqc/math/ntru/Polynomial.java", 2);
        var args = new java.util.ArrayList<>(List.of(binary.toString(), sources.toString(),
                "qa/math-optimization/profiles/" + profile + ".properties", output.toString(), CorpusFiles.sha256(sources), "17"));
        args.addAll(expected.keySet()); ProjectMathScan.main(args.toArray(String[]::new));
        var report = new com.fasterxml.jackson.databind.ObjectMapper().readTree(output.resolve("review.json").toFile());
        for (var file : report.path("files")) assertTrue(file.path("acceptedRegions").asInt() >= expected.get(file.path("path").asText()),
                file.path("path").asText() + ": " + file.path("acceptedRegions"));
    }
    @Test void sourceDriftDuringMeasurementIsRejectedAtTeardown() throws Exception {
        Path copy = fixtureCopy("late-drift");
        var inputs = inputs(copy, "P256_XMD_SHA_256");
        assertDoesNotThrow(inputs::setup);
        Files.writeString(copy.resolve("original").resolve(ENTRY), "// drift\n", java.nio.file.StandardOpenOption.APPEND);
        assertThrows(IOException.class, inputs::close);
    }
    private static BouncyCastleHashToCurveBenchmark.Inputs inputs(Path directory, String profile) {
        var inputs = new BouncyCastleHashToCurveBenchmark.Inputs();
        inputs.scanDirectory = directory.toString(); inputs.binaryArchive = binary.toString();
        inputs.curveProfile = profile; inputs.messageBytes = 32; return inputs;
    }
    private static Path fixtureCopy(String name) throws Exception {
        Path copy = Files.createDirectory(temporary.resolve(name));
        for (String entry : List.of("benchmark.properties", "review.json", "COMPLETED", "original/" + ENTRY, "generated/" + ENTRY)) {
            Path target = copy.resolve(entry); Files.createDirectories(target.getParent()); Files.copy(scan.resolve(entry), target);
        }
        return copy;
    }
}
