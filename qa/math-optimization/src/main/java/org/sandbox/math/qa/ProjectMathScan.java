/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.jar.JarFile;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathReviewReport;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.regelsuche.sdk.optimization.ComputationOptimizer;

/** Manual, explicit-profile archive scan. Never modifies upstream files or measures their execution time. */
public final class ProjectMathScan {
    private static final int MAX_SOURCE_BYTES = 8_000_000;
    private ProjectMathScan() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 6) throw new IllegalArgumentException(
                "Usage: <binary.jar> <sources.jar> <profile.properties> <fresh-output> <sources-sha256> <target-java> [exact-entry.java ...]");
        Path binary = Path.of(args[0]).toAbsolutePath(), archive = Path.of(args[1]).toAbsolutePath();
        Path profile = Path.of(args[2]).toAbsolutePath(), output = Path.of(args[3]).toAbsolutePath().normalize();
        var options = profile(profile, Integer.parseInt(args[5]));
        CorpusFiles.requireHash(archive, args[4]);
        String binaryHash = CorpusFiles.sha256(binary), profileHash = CorpusFiles.sha256(profile);
        var runtime = runtimeInputs();
        var mapper = new ObjectMapper();
        try (var jar = new JarFile(archive.toFile())) {
            var selected = CorpusFiles.select(jar, Arrays.asList(args).subList(6, args.length));
            Files.createDirectory(output); // Fail rather than overwrite any existing result.
            Map<String, Object> receipt = new TreeMap<>();
            receipt.put("schema", "sandbox.project-math-scan/v1");
            receipt.put("startedAt", Instant.now().toString());
            receipt.put("sourcesSha256", args[4]); receipt.put("binarySha256", binaryHash); receipt.put("profileSha256", profileHash);
            receipt.put("selectedEntries", selected); receipt.put("options", options.toMap()); receipt.put("targetJava", options.targetJava());
            receipt.put("javaRuntime", System.getProperty("java.runtime.version")); receipt.put("runtimeInputs", runtime);
            receipt.put("scope", "Source analysis and exact Apply/Undo only; no whole-library behavior, timing or constant-time qualification.");
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("inputs.json").toFile(), receipt);
            int parsed = 0, errors = 0, regions = 0, replacements = 0;
            var rows = new ArrayList<Map<String, Object>>();
            for (String entry : selected) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Scan cancelled");
                byte[] bytes;
                try (var input = jar.getInputStream(jar.getJarEntry(entry))) { bytes = input.readNBytes(MAX_SOURCE_BYTES + 1); }
                if (bytes.length > MAX_SOURCE_BYTES) throw new IOException("Source entry exceeds explicit size bound: " + entry);
                String source = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                var ast = parse(source, entry, binary, options.targetJava());
                var problems = Arrays.stream(ast.getProblems()).filter(p -> p.isError())
                        .map(p -> p.getSourceLineNumber() + ": " + p.getMessage()).toList();
                Map<String, Object> row = new TreeMap<>();
                row.put("path", entry); row.put("sourceSha256", hash(bytes)); row.put("parseErrors", problems);
                if (!problems.isEmpty()) {
                    errors++; row.put("status", "PARSE_ERROR"); rows.add(row); continue;
                }
                parsed++;
                int extracted = new JavaComputationExtractor().extractRuntime(ast, source, options).regions().size();
                regions += extracted; row.put("extractedRegions", extracted);
                var environment = new MathematicalAnalysis.SourceEnvironment(entry, List.of(binary.toString()), List.of(), List.of());
                var analysis = MathematicalAnalysis.analyze(ast, source, options, new NullProgressMonitor(), -1, 0, environment);
                if (analysis.diagnostics().stream().anyMatch(d -> d.code().equals("CANCELLED"))) throw new InterruptedException("Analysis cancelled");
                row.put("status", "ANALYZED"); row.put("acceptedRegions", analysis.replacements().size()); row.put("diagnostics", analysis.diagnostics());
                replacements += analysis.replacements().size();
                if (analysis.changed()) {
                    var document = new Document(source);
                    var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
                    String generated = document.get(); undo.apply(document);
                    if (!source.equals(document.get())) throw new AssertionError("Exact Undo failed: " + entry);
                    if (Arrays.stream(parse(generated, entry, binary, options.targetJava()).getProblems()).anyMatch(p -> p.isError()))
                        throw new AssertionError("Generated Java failed to parse: " + entry);
                    row.put("afterSha256", hash(generated.getBytes(StandardCharsets.UTF_8)));
                    row.put("reviewCandidates", MathReviewReport.ranked(analysis.evidence(), options));
                    writeSource(output.resolve("original"), entry, source); writeSource(output.resolve("generated"), entry, generated);
                    Path evidence = output.resolve("evidence").resolve(CorpusFiles.relative(entry) + ".json");
                    Files.createDirectories(evidence.getParent());
                    mapper.writerWithDefaultPrettyPrinter().writeValue(evidence.toFile(), analysis.evidence());
                }
                rows.add(row);
                if (parsed % 50 == 0) System.out.println("parsed=" + parsed + " acceptedRegions=" + replacements);
            }
            CorpusFiles.requireHash(archive, args[4]);
            if (!binaryHash.equals(CorpusFiles.sha256(binary)) || !profileHash.equals(CorpusFiles.sha256(profile)) || !runtime.equals(runtimeInputs()))
                throw new IOException("Inputs or runtime changed during scan");
            receipt.put("finishedAt", Instant.now().toString()); receipt.put("parsedFiles", parsed); receipt.put("parseErrorFiles", errors);
            receipt.put("extractedRegions", regions); receipt.put("acceptedRegions", replacements); receipt.put("files", rows);
            String status = errors == 0 ? "COMPLETED" : "COMPLETED_WITH_PARSE_ERRORS";
            receipt.put("status", status);
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("review.json").toFile(), receipt);
            Files.writeString(output.resolve("COMPLETED"), status + "\nInputs unchanged. Counts are not measured program speedups.\n");
            System.out.println(status + " parsed=" + parsed + " parseErrorFiles=" + errors + " acceptedRegions=" + replacements);
        }
    }

    static MathCleanUpOptions profile(Path path, int java) throws IOException {
        var properties = new Properties(); try (var input = Files.newInputStream(path)) { properties.load(input); }
        var values = new HashMap<String, String>(); properties.forEach((key, value) -> values.put(key.toString(), value.toString()));
        if (!values.keySet().equals(MathCleanUpOptions.defaults(java).toMap().keySet()))
            throw new IOException("Profile must explicitly specify every mathematics option, without extra keys");
        var options = MathCleanUpOptions.parse(values, java);
        if (!options.enabled()) throw new IOException("Mathematics must be explicitly enabled");
        return options;
    }

    private static CompilationUnit parse(String source, String entry, Path binary, int java) {
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setUnitName(entry); parser.setSource(source.toCharArray());
        parser.setEnvironment(new String[] {binary.toString()}, new String[0], null, true); parser.setResolveBindings(true);
        var settings = new HashMap<String, String>(); JavaCore.setComplianceOptions(java == 8 ? "1.8" : Integer.toString(java), settings);
        parser.setCompilerOptions(settings); return (CompilationUnit) parser.createAST(null);
    }
    private static void writeSource(Path root, String entry, String source) throws IOException {
        Path file = root.resolve(CorpusFiles.relative(entry)); Files.createDirectories(file.getParent()); Files.writeString(file, source);
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static Map<String, String> runtimeInputs() throws Exception {
        Map<String, String> hashes = new TreeMap<>();
        // Standalone execution: retain every jar and compiled class, not just the two anchor classes.
        for (String entry : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            Path path = Path.of(entry).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) hashes.put(path.toString(), CorpusFiles.sha256(path));
            else if (Files.isDirectory(path)) try (var files = Files.walk(path)) {
                for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".class")).sorted().toList())
                    hashes.put(file.toString(), CorpusFiles.sha256(file));
            }
        }
        for (Class<?> anchor : List.of(ComputationOptimizer.class, MathematicalAnalysis.class, MathReviewReport.class, ProjectMathScan.class, JavaCore.class)) {
            Path origin = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            hashes.put("loaded:" + anchor.getName(), origin.toString());
            if (anchor == ComputationOptimizer.class && !Files.isRegularFile(origin)) throw new IOException("Use the qualified, frozen SDK jar");
            if (Files.isRegularFile(origin)) hashes.put(origin.toString(), CorpusFiles.sha256(origin));
        }
        return hashes;
    }
}
