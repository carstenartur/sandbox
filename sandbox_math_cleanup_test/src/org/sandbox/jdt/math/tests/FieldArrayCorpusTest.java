/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathReviewReport;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** Complete pinned sources: admitted inputs are not counted as optimization hits. */
class FieldArrayCorpusTest {
    @TempDir Path temporary;

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void recordCompleteBouncyCastleClassesWithTheSameFiniteRuntimeProfile() throws Exception {
        Path binary = configured("sandbox.math.bc.mavenBinary");
        Path archive = configured("sandbox.math.bc.mavenSources");
        var settings = new HashMap<>(MathCleanUpOptions.defaults(8).toMap());
        settings.put(MathCleanUpOptions.CLEANUP, "true");
        settings.put(MathCleanUpOptions.KINDS, "BYTE,SHORT,CHAR,INT,LONG");
        settings.put(MathCleanUpOptions.WORK_BUDGET, "1000000");
        settings.put(MathCleanUpOptions.MAX_STATES, "20000");
        var options = MathCleanUpOptions.parse(settings, 8);
        assertFalse(options.mathematicalOptIn());
        String binaryHash = hash(Files.readAllBytes(binary));
        String archiveHash = hash(Files.readAllBytes(archive));
        try (var jar = new JarFile(archive.toFile())) {
            for (String entry : List.of("org/bouncycastle/crypto/engines/HC128Engine.java",
                    "org/bouncycastle/crypto/engines/CAST5Engine.java",
                    "org/bouncycastle/crypto/digests/KeccakDigest.java")) {
                var item = Objects.requireNonNull(jar.getJarEntry(entry), entry);
                byte[] bytes;
                try (var input = jar.getInputStream(item)) { bytes = input.readAllBytes(); }
                String source = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                CompilationUnit ast = parse(source, entry, binary);
                var extraction = new JavaComputationExtractor().extractRuntime(ast, source, options);
                long fieldReads = extraction.regions().stream().flatMap(r -> r.arrayReads().stream()).filter(read -> {
                    var node = NodeFinder.perform(ast, read.start(), read.length());
                    if (!(node instanceof ArrayAccess array)) return false;
                    var receiver = array.getArray();
                    IVariableBinding binding = receiver instanceof Name name && name.resolveBinding() instanceof IVariableBinding value
                            ? value : receiver instanceof FieldAccess field ? field.resolveFieldBinding() : null;
                    return binding != null && binding.isField();
                }).count();
                var environment = new MathematicalAnalysis.SourceEnvironment(entry, List.of(binary.toString()), List.of(), List.of());
                var analysis = MathematicalAnalysis.analyze(ast, source, options, new NullProgressMonitor(), -1, 0, environment);
                assertFalse(analysis.diagnostics().stream().anyMatch(d -> List.of("CANCELLED", "STALE_AST_SOURCE",
                        "COMBINED_EDITS_INVALID").contains(d.code())), analysis.diagnostics().toString());
                assertEquals(analysis.replacements().size(), analysis.evidence().size());
                for (var change : analysis.replacements()) {
                    var evidence = analysis.evidence().stream().filter(e -> e.offset() == change.offset()
                            && e.length() == change.length() && e.replacement().equals(change.replacement())).findFirst().orElseThrow();
                    assertNotNull(evidence.proof());
                    assertEquals(source.substring(change.offset(), change.offset() + change.length()), evidence.original());
                    assertTrue(evidence.cost().estimatedRuntimeImprovement());
                }
                var document = new Document(source);
                var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
                String generated = document.get();
                undo.apply(document);
                assertEquals(source, document.get(), "Complete source Undo: " + entry);
                parse(generated, entry, binary);
                compile(source, entry, binary);
                if (!source.equals(generated)) compile(generated, entry, binary);
                var report = new TreeMap<String, Object>();
                report.put("coordinates", "org.bouncycastle:bcprov-jdk18on:1.85.2");
                report.put("sourceEntry", entry);
                report.put("sourceEntrySha256", hash(bytes));
                report.put("sourceArchiveSha256", archiveHash);
                report.put("binaryArchiveSha256", binaryHash);
                report.put("generatedSha256", hash(generated.getBytes(StandardCharsets.UTF_8)));
                report.put("javaRuntime", System.getProperty("java.runtime.version"));
                report.put("targetJava", 8);
                report.put("options", options.toMap());
                report.put("extractedRegions", extraction.regions().size());
                report.put("fieldArrayReadOccurrences", fieldReads);
                report.put("acceptedRegions", analysis.replacements().size());
                report.put("extractionDiagnostics", extraction.diagnostics());
                report.put("diagnostics", analysis.diagnostics());
                report.put("reviewCandidates", MathReviewReport.ranked(analysis.evidence(), options));
                report.put("evidence", analysis.evidence());
                report.put("status", "PROOF_COMPILATION_AND_UNDO_CHECKED");
                report.put("limitations", "Not a cipher-wide execution, constant-time, allocation or speed qualification. Zero accepted regions is not an improvement.");
                String name = entry.substring(entry.lastIndexOf('/') + 1, entry.length() - 5);
                Path directory = Files.createDirectories(Path.of(Objects.requireNonNull(
                        System.getProperty("sandbox.math.corpus.evidence")), "field-arrays", name));
                Files.writeString(directory.resolve("original.java"), source, StandardCharsets.UTF_8);
                Files.writeString(directory.resolve("generated.java"), generated, StandardCharsets.UTF_8);
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(directory.resolve("review.json").toFile(), report);
                System.out.printf("BC_FIELD_ARRAY entry=%s sourceSha256=%s extracted=%d fieldReads=%d accepted=%d targetJava=8 work=1000000 states=20000%n",
                        entry, report.get("sourceEntrySha256"), extraction.regions().size(), fieldReads, analysis.replacements().size());
                if (name.equals("HC128Engine")) assertTrue(fieldReads >= 4,
                        "Both real table helpers must supply their field-array reads: " + extraction.diagnostics());
            }
        }
    }

    private static CompilationUnit parse(String source, String entry, Path binary) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(source.toCharArray()); parser.setUnitName(entry); parser.setResolveBindings(true);
        parser.setEnvironment(new String[] {binary.toString()}, new String[0], null, true);
        var settings = new HashMap<String, String>(); JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, settings);
        parser.setCompilerOptions(settings);
        var ast = (CompilationUnit) parser.createAST(null);
        assertFalse(Arrays.stream(ast.getProblems()).anyMatch(p -> p.isError()), Arrays.toString(ast.getProblems()));
        return ast;
    }
    private void compile(String source, String entry, Path binary) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "bc-complete-");
        Path file = directory.resolve(entry);
        Files.createDirectories(Objects.requireNonNull(file.getParent(), "Source parent"));
        Files.writeString(file, source, StandardCharsets.UTF_8);
        var compiler = Objects.requireNonNull(ToolProvider.getSystemJavaCompiler(), "A JDK is required");
        assertEquals(0, compiler.run(null, null, null, "--release", "8", "-proc:none", "-cp", binary.toString(),
                "-d", directory.toString(), file.toString()), entry);
    }
    private static Path configured(String name) {
        Path path = Path.of(Objects.requireNonNull(System.getProperty(name), name)).toAbsolutePath();
        assertTrue(Files.isRegularFile(path), path.toString());
        return path;
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
