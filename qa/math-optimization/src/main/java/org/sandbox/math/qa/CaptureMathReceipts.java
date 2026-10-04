/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Captures a fresh scoped qualification receipt without running a corpus or JMH. */
public final class CaptureMathReceipts {
    private CaptureMathReceipts() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("<new-output-directory> [prior-implementation.properties]");
        Path output = Path.of(args[0]);
        if (Files.exists(output)) throw new IllegalArgumentException("Refusing to overwrite receipt directory: " + output);
        if (args.length == 2) {
            Properties before = new Properties();
            try (var reader = Files.newBufferedReader(Path.of(args[1]))) { before.load(reader); }
            MathCorpusRunner.requireUnchangedImplementation(before);
        }
        Files.createDirectories(output);
        MathCorpusRunner.writeProperties(output.resolve("implementation.properties"), MathCorpusRunner.implementationReceipt());
        MathCorpusRunner.writeProperties(output.resolve("sandbox-source.properties"), MathCorpusRunner.repositoryReceipt());
    }
}
