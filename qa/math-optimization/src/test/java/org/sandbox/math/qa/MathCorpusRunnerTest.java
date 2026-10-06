/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MathCorpusRunnerTest {
    @TempDir Path temporary;

    @Test void reportsActualCandidateWithoutChangingInput() throws Exception {
        Path source = write("src/Calculation.java", calculation("", "Calculation", ""));
        byte[] before = Files.readAllBytes(source);
        var report = MathCorpusRunner.analyzeTree(temporary.resolve("src"), temporary.resolve("report"));
        assertEquals(1, report.javaFiles());
        assertEquals(0, report.filesWithErrors());
        assertTrue(report.bigIntegerCalls() >= 4);
        assertTrue(report.extractedRegions() >= 1);
        assertTrue(report.supportedRegions() >= 1);
        assertTrue(report.candidateRegions() >= 1);
        assertEquals(0, report.appliedChanges());
        assertArrayEquals(before, Files.readAllBytes(source));
        assertTrue(Files.readString(temporary.resolve("report/summary.properties")).contains("mode=ANALYSIS_ONLY"));
    }

    @Test void compilerErrorsCannotBecomeQualifiedCandidates() throws Exception {
        write("src/Calculation.java", calculation("", "Calculation", "MissingType bad = null;"));
        var report = MathCorpusRunner.analyzeTree(temporary.resolve("src"), temporary.resolve("report"));
        assertEquals(1, report.javaFiles());
        assertEquals(1, report.filesWithErrors());
        assertEquals(0, report.candidateRegions());
        assertEquals(0, report.appliedChanges());
        assertTrue(Files.readString(temporary.resolve("report/files.tsv")).contains("COMPILATION_ERRORS"));
    }

    @Test void packagedTwoFileSourceEnvironmentSurvivesWholeCompilationUnitValidation() throws Exception {
        write("src/sample/Inputs.java", "package sample; public class Inputs { public static long identity(long x) { return x; } }");
        Path source = write("src/sample/Initialization.java", calculation("package sample;\n", "Initialization",
                "baseSeed = Inputs.identity(baseSeed);"));
        byte[] before = Files.readAllBytes(source);
        var report = MathCorpusRunner.analyzeTree(temporary.resolve("src"), temporary.resolve("report"));
        assertEquals(2, report.javaFiles());
        assertEquals(0, report.filesWithErrors());
        assertTrue(report.candidateRegions() >= 1, report.toString());
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test void fingerprintsWholeCoreClosureAndDetectsDrift() throws Exception {
        var receipt = MathCorpusRunner.implementationReceipt();
        assertTrue(receipt.stringPropertyNames().stream().anyMatch(name -> name.contains("JavaComputationExtractor$")
                && name.endsWith(".class")), receipt.toString());
        assertTrue(receipt.stringPropertyNames().stream().anyMatch(name -> name.contains("MathematicalAnalysis.class")));
        assertEquals("frozen-jar", receipt.getProperty("sdk.codeSourceKind"));
        assertTrue(receipt.getProperty("sdk.sha256").matches("[0-9a-f]{64}"));
        MathCorpusRunner.requireUnchangedImplementation(receipt);
        receipt.setProperty("sdk.sha256", "0".repeat(64));
        assertThrows(IllegalStateException.class, () -> MathCorpusRunner.requireUnchangedImplementation(receipt));
    }

    private Path write(String relative, String source) throws Exception {
        Path path = temporary.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, source);
        return path;
    }

    private static String calculation(String prefix, String name, String extra) {
        return prefix + "import java.math.BigInteger;\npublic class " + name + " {\n"
                + " public static long[] compute(long seed, long baseSeed) {\n" + extra + "\n"
                + " BigInteger base = BigInteger.valueOf(baseSeed);\n"
                + " BigInteger exponent = BigInteger.valueOf(seed & Integer.MAX_VALUE);\n"
                + " BigInteger modulus = new BigInteger(\"65537\");\n"
                + " BigInteger odd = exponent.multiply(BigInteger.valueOf(2)).add(BigInteger.ONE);\n"
                + " BigInteger left = base.modPow(odd, modulus);\n"
                + " BigInteger right = base.modPow(exponent.add(BigInteger.ONE), modulus);\n"
                + " return new long[] {left.longValue(), right.longValue()};\n }\n}\n";
    }
}
