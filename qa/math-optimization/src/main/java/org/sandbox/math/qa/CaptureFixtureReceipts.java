/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

import org.sandbox.benchmarks.MathematicsOptimizationBenchmark.Inputs;
import org.sandbox.benchmarks.MathematicsOptimizationBenchmark;

/** Uses the actual benchmark setup/compiler, separately from JMH measurements. */
public final class CaptureFixtureReceipts {
    private CaptureFixtureReceipts() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("<fixture-root> <new-receipt.properties> [prior-receipt.properties]");
        Path root = Path.of(args[0]).toAbsolutePath();
        Path output = Path.of(args[1]);
        Properties receipt = new Properties();
        receipt.setProperty("schema", "math-compiled-fixtures/v2");
        receipt.setProperty("javaRuntime", System.getProperty("java.runtime.version"));
        receipt.setProperty("compilerRecipe", "MathematicsOptimizationBenchmark.Inputs.setup/compile; javac --release 17");
        receipt.setProperty("compilerClasspath", System.getProperty("java.class.path"));
        receipt.setProperty("fixtureRoot", root.toString());
        receipt.setProperty("jmhMeasurementsRunByThisHelper", "false");
        receipt.setProperty("validatedInputsPerFixture", "64");
        var compiledDirectory = Inputs.class.getDeclaredField("compiledDirectory");
        compiledDirectory.setAccessible(true);
        for (String size : System.getProperty("math.qa.fixtureCases", "small,wide").split(",", -1)) {
          for (String scenario : System.getProperty("math.qa.inputScenarios", "default").split(",", -1)) {
            if (size.isBlank() || scenario.isBlank()) throw new IllegalArgumentException("Empty fixture/scenario selector");
            try (Inputs inputs = new Inputs()) {
                inputs.fixtureDirectory = root.resolve(size).toString();
                inputs.inputScenario = scenario;
                inputs.setup();
                String casePrefix = size + "." + scenario;
                receipt.setProperty(casePrefix + ".inputSha256", inputHash(inputs));
                Path metadata = root.resolve(size).resolve("fixture.properties");
                if (Files.exists(metadata)) receipt.setProperty(casePrefix + ".metadataSha256", sha256(metadata));
                var benchmark = new MathematicsOptimizationBenchmark();
                int exceptions = 0;
                for (int i = 0; i < 64; i++) if (benchmark.generated(inputs) instanceof ArithmeticException) exceptions++;
                receipt.setProperty(casePrefix + ".generatedExceptionCount", Integer.toString(exceptions));
                receipt.setProperty(casePrefix + ".generatedReturnCount", Integer.toString(64 - exceptions));
                Path classes = (Path) compiledDirectory.get(inputs);
                for (String variant : List.of("original", "generated")) {
                    String prefix = casePrefix + "." + variant;
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

    private static String inputHash(Inputs inputs) throws Exception {
        var seedsField = Inputs.class.getDeclaredField("seeds");
        var basesField = Inputs.class.getDeclaredField("bases");
        seedsField.setAccessible(true);
        basesField.setAccessible(true);
        long[] seeds = (long[]) seedsField.get(inputs), bases = (long[]) basesField.get(inputs);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < seeds.length; i++) digest.update(ByteBuffer.allocate(16).putLong(seeds[i]).putLong(bases[i]).array());
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
