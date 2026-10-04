/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

import org.sandbox.benchmarks.MathematicsOptimizationBenchmark.Inputs;

/** Uses the actual benchmark setup/compiler, separately from JMH measurements. */
public final class CaptureFixtureReceipts {
    private CaptureFixtureReceipts() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("<fixture-root> <new-receipt.properties> [prior-receipt.properties]");
        Path root = Path.of(args[0]).toAbsolutePath();
        Path output = Path.of(args[1]);
        Properties receipt = new Properties();
        receipt.setProperty("schema", "math-compiled-fixtures/v1-reconstructed");
        receipt.setProperty("javaRuntime", System.getProperty("java.runtime.version"));
        receipt.setProperty("compilerRecipe", "Unmodified MathematicsOptimizationBenchmark.Inputs.setup/compile; javac --release 17");
        receipt.setProperty("compilerClasspath", System.getProperty("java.class.path"));
        receipt.setProperty("fixtureRoot", root.toString());
        receipt.setProperty("jmhMeasurementsRunByThisHelper", "false");
        receipt.setProperty("equalityCheckInputsPerFixture", "64");
        var compiledDirectory = Inputs.class.getDeclaredField("compiledDirectory");
        compiledDirectory.setAccessible(true);
        for (String size : List.of("small", "wide")) {
            try (Inputs inputs = new Inputs()) {
                inputs.fixtureDirectory = root.resolve(size).toString();
                inputs.setup();
                Path classes = (Path) compiledDirectory.get(inputs);
                for (String variant : List.of("original", "generated")) {
                    String prefix = size + "." + variant;
                    receipt.setProperty(prefix + ".sourceSha256", sha256(root.resolve(size).resolve(variant).resolve("Calculation.java")));
                    try (var paths = Files.walk(classes.resolve(variant))) {
                        List<Path> entries = paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".class")).sorted().toList();
                        if (entries.isEmpty()) throw new IllegalStateException("No compiled fixture classes: " + prefix);
                        receipt.setProperty(prefix + ".classCount", Integer.toString(entries.size()));
                        for (Path entry : entries) receipt.setProperty(prefix + ".class." + classes.resolve(variant).relativize(entry), sha256(entry));
                    }
                }
            }
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (var writer = Files.newBufferedWriter(output, StandardOpenOption.CREATE_NEW)) {
            receipt.store(writer, "Fresh Java fixture source/class receipt; independent of historical measurements");
        }
        if (args.length == 3) {
            Properties before = new Properties();
            try (var reader = Files.newBufferedReader(Path.of(args[2]))) { before.load(reader); }
            if (!before.equals(receipt)) throw new IllegalStateException("Fixture sources/classes changed during measurement; compare receipts");
        }
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
