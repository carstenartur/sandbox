/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Produces ordinary, source-replayed Apply output; no handwritten replacement formulas. */
public final class FiniteSeriesQualification {
    static final List<String> FAMILIES = List.of("polynomial", "stream", "affine", "geometric", "product");
    private FiniteSeriesQualification() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply a fresh fixture directory");
        generate(Path.of(args[0]));
    }

    public static void generate(Path root) throws Exception {
        if (Files.exists(root)) throw new IOException("Qualification output already exists: " + root);
        Files.createDirectories(root);
        var implementation = MathCorpusRunner.implementationReceipt();
        MathCorpusRunner.writeProperties(root.resolve("implementation.properties"), implementation);
        MathCorpusRunner.writeProperties(root.resolve("sandbox-source.properties"), MathCorpusRunner.repositoryReceipt());
        var manifest = new Properties();
        manifest.setProperty("schema", "sandbox.finite-series-fixtures/v1");
        manifest.setProperty("targetJava", "17");
        manifest.setProperty("implementationSha256", CorpusFiles.sha256(root.resolve("implementation.properties")));
        manifest.setProperty("sourceReceiptSha256", CorpusFiles.sha256(root.resolve("sandbox-source.properties")));
        var options = new MathCleanUpOptions(true, EnumSet.allOf(NumericKind.class), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of());
        var rows = new ArrayList<java.util.Map<String, Object>>();
        for (String family : FAMILIES) {
            String source = source(family);
            long start = System.nanoTime();
            var analysis = MathematicalAnalysis.analyze(parse(source), source, options, null, -1, 0);
            var proofs = analysis.evidence().stream().filter(e -> e.runtimeIteration() != null).toList();
            if (!analysis.changed() || proofs.size() != 1)
                throw new IOException("Expected one verified runtime reduction for " + family + ": " + analysis.diagnostics());
            var document = new Document(source);
            var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
            String generated = document.get(); undo.apply(document);
            if (!source.equals(document.get()) || source.equals(generated)) throw new IOException("Apply/Undo failed: " + family);
            parse(generated);
            long preparationNanos = System.nanoTime() - start;
            var proof = proofs.getFirst().runtimeIteration();
            manifest.setProperty(family + ".minimumIterations", Long.toString(proof.minimumIterations()));
            for (String variant : List.of("original", "generated")) {
                Path file = root.resolve(family).resolve(variant).resolve("Calculation.java");
                Files.createDirectories(file.getParent());
                Files.writeString(file, variant.equals("original") ? source : generated);
                manifest.setProperty(family + "." + variant, CorpusFiles.sha256(file));
            }
            rows.add(java.util.Map.of("family", family, "preparationNanos", preparationNanos,
                    "minimumIterations", proof.minimumIterations(), "proof", proof.description(),
                    "originalSha256", manifest.getProperty(family + ".original"),
                    "generatedSha256", manifest.getProperty(family + ".generated")));
        }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(root.resolve("qualification.json").toFile(),
                java.util.Map.of("schema", "sandbox.finite-series-qualification/v1", "options", options.toMap(), "cases", rows,
                        "scope", "Production Apply/Undo and source/proof replay. Compiled equivalence is checked again in every benchmark trial; preparation time is one observation, not a benchmark."));
        manifest.setProperty("qualificationSha256", CorpusFiles.sha256(root.resolve("qualification.json")));
        MathCorpusRunner.requireUnchangedImplementation(implementation);
        MathCorpusRunner.writeProperties(root.resolve("benchmark.properties"), manifest);
        Files.writeString(root.resolve("COMPLETED"), "Source-replayed fixtures; compiled comparison is required before timing.\n");
    }

    private static String source(String family) {
        String body = switch (family) {
            case "polynomial" -> "int sum=seed;for(int i=0;i<n;i++)sum+=i*i;return sum;";
            case "stream" -> "return java.util.stream.IntStream.range(0,n).map(i->i*i+seed).sum();";
            case "affine" -> "int sum=seed;for(int i=0;i<n;i++)sum=3*sum+i;return sum;";
            case "geometric" -> "int sum=seed;int power=1;for(int i=0;i<n;i++){sum+=power;power*=3;}return sum+power;";
            case "product" -> "int sum=seed;for(int i=0;i<n;i++)sum*=3;return sum;";
            default -> throw new IllegalArgumentException("Unknown finite-series family: " + family);
        };
        return "public final class Calculation {public static int run(int n,int seed){" + body + "}}\n";
    }

    private static CompilationUnit parse(String source) throws IOException {
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setUnitName("Calculation.java");
        parser.setSource(source.toCharArray()); parser.setResolveBindings(true);
        parser.setEnvironment(new String[0], new String[0], null, true);
        var settings = new HashMap<String, String>(); JavaCore.setComplianceOptions("17", settings);
        parser.setCompilerOptions(settings); var unit = (CompilationUnit) parser.createAST(null);
        for (var problem : unit.getProblems()) if (problem.isError()) throw new IOException(problem.getMessage());
        return unit;
    }
}
