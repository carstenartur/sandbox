/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathReviewReport;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Whole, version-pinned upstream files; zero accepted edits is a valid, reported outcome. */
class SwtImageDataRuntimeCorpusTest {
    private static final String BC_SOURCE_SHA256 = "b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f";

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void analyzesTheCompleteSwtImageDataWithTheStrictIntegralProfile() throws Exception {
        Path binary = configured("sandbox.math.swt.binary");
        Path sources = configured("sandbox.math.swt.sources");
        Path evidence = configuredDirectory("sandbox.math.corpus.evidence");
        int java = sourceLevel(binary);
        var values = new HashMap<>(MathCleanUpOptions.defaults(java).toMap());
        values.put(MathCleanUpOptions.CLEANUP, "true");
        values.put(MathCleanUpOptions.KINDS, "BYTE,SHORT,CHAR,INT,LONG");
        values.put(MathCleanUpOptions.WORK_BUDGET, "1000000");
        values.put(MathCleanUpOptions.MAX_STATES, "20000");
        values.put(MathCleanUpOptions.EXPLANATIONS, "NONTRIVIAL");
        var options = MathCleanUpOptions.parse(values, java);
        String entry = "org/eclipse/swt/graphics/ImageData.java";
        byte[] bytes = entry(sources, entry);
        String source = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        CompilationUnit ast = parse(source, entry, binary, java);
        var extraction = new JavaComputationExtractor().extractRuntime(ast, source, options);
        assertFalse(extraction.regions().isEmpty(), "The whole SWT file must reach the actual numeric adapter");
        var environment = new MathematicalAnalysis.SourceEnvironment(entry, List.of(binary.toString()), List.of(), List.of());
        var analysis = MathematicalAnalysis.analyze(ast, source, options, new NullProgressMonitor(), -1, 0, environment);
        assertFalse(analysis.diagnostics().stream().anyMatch(d -> d.code().equals("CANCELLED")
                || d.code().equals("STALE_AST_SOURCE") || d.code().equals("COMBINED_EDITS_INVALID")), analysis.diagnostics().toString());
        assertEquals(analysis.replacements().size(), analysis.evidence().size());
        for (var replacement : analysis.replacements()) {
            var proof = analysis.evidence().stream().filter(e -> e.offset() == replacement.offset()
                    && e.length() == replacement.length() && e.replacement().equals(replacement.replacement()))
                    .findFirst().orElseThrow();
            assertNotNull(proof.proof());
            assertEquals(source.substring(proof.offset(), proof.offset() + proof.length()), proof.original());
            assertTrue(proof.cost().estimatedRuntimeImprovement());
        }
        Document document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        parse(generated, entry, binary, java);
        undo.apply(document);
        assertEquals(source, document.get(), "Whole-file edits must support byte-exact Undo");
        var counts = new TreeMap<String, Integer>();
        analysis.diagnostics().forEach(d -> counts.merge(d.code(), 1, Integer::sum));
        var report = new TreeMap<String, Object>();
        report.put("sourceEntry", entry);
        report.put("sourceEntrySha256", hash(bytes));
        report.put("sourceArchiveSha256", hash(Files.readAllBytes(sources)));
        report.put("binaryArchiveSha256", hash(Files.readAllBytes(binary)));
        report.put("javaRuntime", System.getProperty("java.runtime.version"));
        report.put("targetJava", java);
        report.put("options", options.toMap());
        report.put("extractedRegions", extraction.regions().size());
        report.put("acceptedRegions", analysis.replacements().size());
        report.put("diagnosticCounts", counts);
        report.put("diagnostics", analysis.diagnostics());
        report.put("reviewCandidates", MathReviewReport.ranked(analysis.evidence(), options));
        report.put("evidence", analysis.evidence());
        report.put("generatedSha256", hash(generated.getBytes(StandardCharsets.UTF_8)));
        report.put("scope", "Complete ImageData source analysis and Apply/Undo; no SWT-wide scan, image execution or speedup claim");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("swt-imagedata-review.json").toFile(), report);
        Files.writeString(evidence.resolve("ImageData.before.java"), source);
        Files.writeString(evidence.resolve("ImageData.after.java"), generated);
        copy(binary, evidence.resolve("swt-binary.jar"));
        copy(sources, evidence.resolve("swt-sources.jar"));
        System.out.printf("SWT_MATH_CORPUS targetJava=%d extracted=%d accepted=%d source=%s generated=%s%n",
                java, extraction.regions().size(), analysis.replacements().size(), hash(bytes), hash(generated.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void retainsTheExactHistoricalBouncyCastleArchiveForIndependentCorpusRuns() throws Exception {
        Path binary = configured("sandbox.math.bc.mavenBinary");
        Path sources = configured("sandbox.math.bc.mavenSources");
        assertEquals(BC_SOURCE_SHA256, hash(Files.readAllBytes(sources)), "Never silently change the frozen comparison corpus");
        assertEquals("afccf30d462347704f3e090542b75e81bb782e567abffee780af4dfd3e7de1a7",
                hash(entry(sources, "org/bouncycastle/pqc/crypto/hawk/HawkEngine.java")));
        Path evidence = configuredDirectory("sandbox.math.corpus.evidence");
        copy(binary, evidence.resolve("bc-binary.jar"));
        copy(sources, evidence.resolve("bc-sources.jar"));
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("bc-inputs.json").toFile(), Map.of(
                "sourcesSha256", BC_SOURCE_SHA256, "binarySha256", hash(Files.readAllBytes(binary)),
                "scope", "Pinned source acquisition only; complete corpus optimization is a separate manual run"));
    }

    private static int sourceLevel(Path binary) throws Exception {
        try (var jar = new JarFile(binary.toFile())) {
            String bree = jar.getManifest().getMainAttributes().getValue("Bundle-RequiredExecutionEnvironment");
            assertNotNull(bree);
            assertTrue(bree.matches("JavaSE-(17|21|25)"), "Unexpected SWT Java baseline: " + bree);
            return Integer.parseInt(bree.substring("JavaSE-".length()));
        }
    }
    private static byte[] entry(Path path, String name) throws Exception {
        try (var jar = new JarFile(path.toFile())) {
            var entry = jar.getJarEntry(name);
            assertNotNull(entry, name);
            try (var input = jar.getInputStream(entry)) { return input.readAllBytes(); }
        }
    }
    private static CompilationUnit parse(String source, String name, Path binary, int java) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setUnitName(name); parser.setSource(source.toCharArray());
        parser.setEnvironment(new String[] {binary.toString()}, new String[0], null, true); parser.setResolveBindings(true);
        var settings = new HashMap<String, String>(); JavaCore.setComplianceOptions(Integer.toString(java), settings);
        parser.setCompilerOptions(settings);
        var ast = (CompilationUnit) parser.createAST(null);
        assertFalse(Arrays.stream(ast.getProblems()).anyMatch(p -> p.isError()), Arrays.toString(ast.getProblems()));
        return ast;
    }
    private static Path configured(String name) {
        String value = System.getProperty(name); assertNotNull(value, "Run through the existing Maven test module: " + name);
        Path path = Path.of(value).toAbsolutePath(); assertTrue(Files.isRegularFile(path), path.toString()); return path;
    }
    private static Path configuredDirectory(String name) throws Exception {
        String value = System.getProperty(name); assertNotNull(value, name); return Files.createDirectories(Path.of(value));
    }
    private static String hash(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
    private static void copy(Path from, Path to) throws Exception {
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        assertEquals(hash(Files.readAllBytes(from)), hash(Files.readAllBytes(to)));
    }
}
