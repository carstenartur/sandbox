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
    private static final long MAX_CONTEXT_BYTES = 512_000_000L;
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
            // A shared immutable source snapshot makes definitions in other units available.
            // Exact selectedEntries still controls analysis; dependencies are not extra scan successes.
            Path sourceRoot = Files.createDirectory(output.resolve("source-context"));
            var contextEntries = CorpusFiles.select(jar, List.of());
            if (contextEntries.size() > 100_000) throw new IOException("Source context file limit");
            Map<String,String> contextHashes = new TreeMap<>();
            long contextBytes = 0;
            for (String path : contextEntries) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Source extraction cancelled");
                byte[] bytes;
                try (var input = jar.getInputStream(jar.getJarEntry(path))) { bytes = input.readNBytes(MAX_SOURCE_BYTES + 1); }
                if (bytes.length > MAX_SOURCE_BYTES) throw new IOException("Source entry exceeds explicit size bound: " + path);
                contextBytes += bytes.length;
                if (contextBytes > MAX_CONTEXT_BYTES) throw new IOException("Source context byte limit");
                String text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                writeSource(sourceRoot, path, text);
                contextHashes.put(sourceRoot.resolve(CorpusFiles.relative(path)).toString(), hash(bytes));
            }
            Map<String, Object> receipt = new TreeMap<>();
            receipt.put("schema", "sandbox.project-math-scan/v1");
            receipt.put("startedAt", Instant.now().toString());
            receipt.put("sourcesSha256", args[4]); receipt.put("binarySha256", binaryHash); receipt.put("profileSha256", profileHash);
            receipt.put("selectedEntries", selected); receipt.put("options", options.toMap()); receipt.put("targetJava", options.targetJava());
            receipt.put("javaRuntime", System.getProperty("java.runtime.version")); receipt.put("runtimeInputs", runtime);
            receipt.put("sourceContextFiles", contextHashes.size());
            receipt.put("sourceContext", "shared archive sources; consulted definitions are rechecked before Apply");
            receipt.put("reviewOnlyWarning", "review-only/ omits automatic contract documentation for manual upstream review. "
                    + "It is not the ordinary Apply output; the numerical contract, source premises and timing limitations remain in this report.");
            receipt.put("scope", "Source analysis and exact Apply/Undo only; no whole-library behavior, timing or constant-time qualification.");
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("inputs.json").toFile(), receipt);
            int parsed = 0, errors = 0, regions = 0, replacements = 0;
            var benchmark = new Properties();
            benchmark.setProperty("schema", "sandbox.generated-corpus-fixture/v1");
            benchmark.setProperty("binarySha256", binaryHash);
            benchmark.setProperty("sourcesSha256", args[4]);
            benchmark.setProperty("profileSha256", profileHash);
            benchmark.setProperty("targetJava", Integer.toString(options.targetJava()));
            var rows = new ArrayList<Map<String, Object>>();
            for (String entry : selected) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Scan cancelled");
                byte[] bytes;
                try (var input = jar.getInputStream(jar.getJarEntry(entry))) { bytes = input.readNBytes(MAX_SOURCE_BYTES + 1); }
                if (bytes.length > MAX_SOURCE_BYTES) throw new IOException("Source entry exceeds explicit size bound: " + entry);
                String source = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                var ast = parse(source, entry, binary, sourceRoot, options.targetJava());
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
                var environment = new MathematicalAnalysis.SourceEnvironment(entry, List.of(binary.toString()), List.of(sourceRoot.toString()), List.of("UTF-8"));
                var analysis = MathematicalAnalysis.analyze(ast, source, options, new NullProgressMonitor(), -1, 0, environment);
                if (analysis.diagnostics().stream().anyMatch(d -> d.code().equals("CANCELLED"))) throw new InterruptedException("Analysis cancelled");
                for (var dependency : analysis.sourceDependencies().receipts()) {
                    if (!dependency.sha256().equals(contextHashes.get(dependency.identity())))
                        throw new IOException("Definition differs from the supplied archive: " + dependency.identity());
                }
                row.put("sourceDependencies", analysis.sourceDependencies().receipts());
                row.put("status", "ANALYZED"); row.put("acceptedRegions", analysis.replacements().size()); row.put("diagnostics", analysis.diagnostics());
                replacements += analysis.replacements().size();
                if (analysis.changed()) {
                    var document = new Document(source);
                    var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
                    String generated = document.get(); undo.apply(document);
                    if (!source.equals(document.get())) throw new AssertionError("Exact Undo failed: " + entry);
                    if (Arrays.stream(parse(generated, entry, binary, sourceRoot, options.targetJava()).getProblems()).anyMatch(p -> p.isError()))
                        throw new AssertionError("Generated Java failed to parse: " + entry);
                    row.put("afterSha256", hash(generated.getBytes(StandardCharsets.UTF_8)));
                    row.put("reviewCandidates", MathReviewReport.ranked(analysis.evidence(), options));
                    writeSource(output.resolve("original"), entry, source); writeSource(output.resolve("generated"), entry, generated);
                    benchmark.setProperty("original." + entry, hash(bytes));
                    benchmark.setProperty("generated." + entry, hash(generated.getBytes(StandardCharsets.UTF_8)));
                    String reviewOnly = calculationReviewSource(source, analysis);
                    if (Arrays.stream(parse(reviewOnly, entry, binary, sourceRoot, options.targetJava()).getProblems()).anyMatch(p -> p.isError()))
                        throw new AssertionError("Review Java failed to parse: " + entry);
                    writeSource(output.resolve("review-only"), entry, reviewOnly);
                    row.put("reviewOnlySourceSha256", hash(reviewOnly.getBytes(StandardCharsets.UTF_8)));
                    row.put("omittedReviewDocumentation", analysis.documentation());
                    Path evidence = output.resolve("evidence").resolve(CorpusFiles.relative(entry) + ".json");
                    Files.createDirectories(evidence.getParent());
                    mapper.writerWithDefaultPrettyPrinter().writeValue(evidence.toFile(), analysis.evidence());
                }
                rows.add(row);
                if (parsed % 50 == 0) System.out.println("parsed=" + parsed + " acceptedRegions=" + replacements);
            }
            verifySourceSnapshot(sourceRoot, contextHashes);
            CorpusFiles.requireHash(archive, args[4]);
            if (!binaryHash.equals(CorpusFiles.sha256(binary)) || !profileHash.equals(CorpusFiles.sha256(profile)) || !runtime.equals(runtimeInputs()))
                throw new IOException("Inputs or runtime changed during scan");
            receipt.put("finishedAt", Instant.now().toString()); receipt.put("parsedFiles", parsed); receipt.put("parseErrorFiles", errors);
            receipt.put("extractedRegions", regions); receipt.put("acceptedRegions", replacements); receipt.put("files", rows);
            String status = errors == 0 ? "COMPLETED" : "COMPLETED_WITH_PARSE_ERRORS";
            receipt.put("status", status);
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("review.json").toFile(), receipt);
            benchmark.setProperty("reviewSha256", CorpusFiles.sha256(output.resolve("review.json")));
            try (var writer = Files.newBufferedWriter(output.resolve("benchmark.properties"))) {
                benchmark.store(writer, "Exact ordinary Apply sources; performance and timing behavior remain unmeasured");
            }
            Files.writeString(output.resolve("COMPLETED"), status + "\nInputs unchanged. Counts are not measured program speedups.\n");
            System.out.println(status + " parsed=" + parsed + " parseErrorFiles=" + errors + " acceptedRegions=" + replacements);
        }
    }

    /** Same verified calculation edits, not a second optimizer. Only separate automatic documentation edits are omitted. */
    private static String calculationReviewSource(String source, MathematicalAnalysis.Analysis analysis) throws Exception {
        var edit = analysis.newEdit(); // Revalidates all source dependencies before either rendering.
        for (var note : analysis.documentation()) {
            TextEdit matched = null;
            for (var child : edit.getChildren()) {
                if (child instanceof org.eclipse.text.edits.ReplaceEdit replacement
                        && replacement.getOffset() == note.offset() && replacement.getLength() == note.length()
                        && replacement.getText().equals(note.replacement())) {
                    if (matched != null) throw new IllegalArgumentException("AMBIGUOUS_DOCUMENTATION_EDIT");
                    matched = child;
                }
            }
            if (matched == null) throw new IllegalArgumentException("DOCUMENTATION_EDIT_NOT_FOUND");
            edit.removeChild(matched);
        }
        var document = new Document(source);
        var undo = edit.apply(document, TextEdit.CREATE_UNDO);
        String result = document.get();
        undo.apply(document);
        if (!source.equals(document.get())) throw new AssertionError("Review exact Undo failed");
        return result;
    }

    static void verifySourceSnapshot(Path root, Map<String,String> expected) throws IOException {
        var found = new java.util.HashSet<String>();
        try (var entries = Files.walk(root)) {
            for (var path : entries.toList()) {
                if (Files.isSymbolicLink(path)) throw new IOException("Source snapshot became a symbolic link: " + path);
                if (Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                String name = path.toAbsolutePath().normalize().toString();
                if (!expected.containsKey(name) || !expected.get(name).equals(CorpusFiles.sha256(path)))
                    throw new IOException("Source snapshot changed: " + name);
                found.add(name);
            }
        }
        if (!found.equals(expected.keySet())) throw new IOException("Source snapshot entries changed");
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

    private static CompilationUnit parse(String source, String entry, Path binary, Path sourceRoot, int java) {
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setUnitName(entry); parser.setSource(source.toCharArray());
        parser.setEnvironment(new String[] {binary.toString()}, new String[] {sourceRoot.toString()}, new String[] {"UTF-8"}, true); parser.setResolveBindings(true);
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
