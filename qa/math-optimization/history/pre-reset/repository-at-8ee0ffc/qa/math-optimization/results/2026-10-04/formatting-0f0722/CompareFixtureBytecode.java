/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import org.sandbox.benchmarks.MathematicsOptimizationBenchmark.Inputs;

/** Receipt helper: uses the actual benchmark setup/compiler, without running JMH. */
class CompareFixtureBytecode {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("historical-root regenerated-root output-directory");
        Path historical = Path.of(args[0]);
        Path regenerated = Path.of(args[1]);
        Path output = Files.createDirectories(Path.of(args[2]));
        var compiledDirectory = Inputs.class.getDeclaredField("compiledDirectory");
        compiledDirectory.setAccessible(true);
        StringBuilder sources = new StringBuilder("fixture\tvariant\thistorical_sha256\tregenerated_sha256\tbyte_identical\n");
        StringBuilder classes = new StringBuilder("fixture\tvariant\tclass_file\thistorical_sha256\tregenerated_sha256\tbyte_identical\n");
        int classPairs = 0;
        boolean allEqual = true;
        for (String size : List.of("small", "wide")) {
            try (Inputs before = new Inputs(); Inputs after = new Inputs()) {
                before.fixtureDirectory = historical.resolve(size).toString();
                after.fixtureDirectory = regenerated.resolve(size).toString();
                // Invokes the unchanged benchmark's exact signature transformation,
                // javac --release 17 recipe, and result check over its 64 inputs.
                before.setup();
                after.setup();
                Path oldClasses = (Path) compiledDirectory.get(before);
                Path newClasses = (Path) compiledDirectory.get(after);
                for (String variant : List.of("original", "generated")) {
                    byte[] oldSource = Files.readAllBytes(historical.resolve(size).resolve(variant).resolve("Calculation.java"));
                    byte[] newSource = Files.readAllBytes(regenerated.resolve(size).resolve(variant).resolve("Calculation.java"));
                    sources.append(size).append('\t').append(variant).append('\t')
                            .append(sha256(oldSource)).append('\t').append(sha256(newSource)).append('\t')
                            .append(Arrays.equals(oldSource, newSource)).append('\n');
                    Path oldRoot = oldClasses.resolve(variant);
                    Path newRoot = newClasses.resolve(variant);
                    List<String> oldEntries = classFiles(oldRoot);
                    if (!oldEntries.equals(classFiles(newRoot))) throw new IllegalStateException("Class entry sets differ: " + size + '/' + variant);
                    if (oldEntries.isEmpty()) throw new IllegalStateException("No compiled classes: " + size + '/' + variant);
                    for (String name : oldEntries) {
                        byte[] oldBytes = Files.readAllBytes(oldRoot.resolve(name));
                        byte[] newBytes = Files.readAllBytes(newRoot.resolve(name));
                        boolean equal = Arrays.equals(oldBytes, newBytes);
                        allEqual &= equal;
                        classPairs++;
                        classes.append(size).append('\t').append(variant).append('\t').append(name).append('\t')
                                .append(sha256(oldBytes)).append('\t').append(sha256(newBytes)).append('\t').append(equal).append('\n');
                    }
                }
            }
        }
        Files.writeString(output.resolve("source-comparison.tsv"), sources);
        Files.writeString(output.resolve("class-comparison.tsv"), classes);
        Files.writeString(output.resolve("compiler.properties"), "javaRuntime=" + System.getProperty("java.runtime.version")
                + "\ncompilerRecipe=Unmodified MathematicsOptimizationBenchmark.Inputs.setup/compile; --release 17\n"
                + "compilerClasspath=" + System.getProperty("java.class.path") + "\n"
                + "fixturePairs=4\nclassPairs=" + classPairs + "\nallClassBytesIdentical=" + allEqual + "\n"
                + "jmhMeasurementsRun=false\n");
        if (!allEqual) throw new IllegalStateException("Historical/regenerated class bytes differ; inspect class-comparison.tsv");
        System.out.println("Four fixture pairs and " + classPairs + " class pairs are byte-identical after the exact benchmark compilation recipe.");
    }

    private static List<String> classFiles(Path root) throws Exception {
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".class"))
                    .map(root::relativize).map(Path::toString).sorted().toList();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
