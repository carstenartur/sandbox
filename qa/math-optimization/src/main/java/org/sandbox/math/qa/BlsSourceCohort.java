/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;

/** Complete pinned BLS source cohort; production scanning remains the only transformation authority. */
public final class BlsSourceCohort {
    static final String BINARY_SHA = "986b0fb92ec10e0c66b43e036ce0077e6150cfaecd1db9fb92b56672e157afe5";
    static final String SOURCES_SHA = "b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f";
    static final List<String> TYPES = List.of("BLS12_381G2HashToCurve", "BLS12_381Pairing", "Fp12Element", "Fp2Element", "Fp6Element");
    static final List<String> ENTRIES = TYPES.stream().map(type -> "org/bouncycastle/crypto/bls/" + type + ".java").toList();
    private static final ObjectMapper JSON = new ObjectMapper();
    private BlsSourceCohort() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Supply pinned binary, sources, explicit BLS profile and fresh output");
        Path binary = Path.of(args[0]).toAbsolutePath(), sources = Path.of(args[1]).toAbsolutePath();
        Path profile = Path.of(args[2]).toAbsolutePath(), output = Path.of(args[3]).toAbsolutePath().normalize();
        CorpusFiles.requireHash(binary, BINARY_SHA); CorpusFiles.requireHash(sources, SOURCES_SHA);
        requireProfile(profile);
        var scan = new ArrayList<>(List.of(binary.toString(), sources.toString(), profile.toString(), output.toString(), SOURCES_SHA, "17"));
        scan.addAll(ENTRIES); ProjectMathScan.main(scan.toArray(String[]::new));
        var report = JSON.readTree(output.resolve("review.json").toFile()); requireReport(report);
        var manifest = read(output.resolve("benchmark.properties"));
        var artifacts = new TreeMap<String, String>(); var dependencies = new TreeMap<String, String>();
        var classes = new LinkedHashMap<String, Object>();
        Files.copy(profile, output.resolve("profile.properties"));
        for (String path : List.of("review.json", "inputs.json", "profile.properties")) capture(output, path, artifacts);
        try (var archive = new JarFile(sources.toFile())) {
            for (String entry : ENTRIES) {
                JsonNode row = row(report, entry); int accepted = row.path("acceptedRegions").asInt(-1);
                if (accepted < 0) throw new IOException("Missing source acceptance: " + entry);
                byte[] original;
                try (var input = archive.getInputStream(archive.getJarEntry(entry))) { original = input.readAllBytes(); }
                if (accepted == 0) for (String variant : List.of("original", "generated")) {
                    Path target = output.resolve(variant).resolve(entry); Files.createDirectories(target.getParent());
                    Files.write(target, original, java.nio.file.StandardOpenOption.CREATE_NEW);
                    manifest.setProperty(variant + "." + entry, CorpusFiles.sha256(target));
                }
                if (!Arrays.equals(original, Files.readAllBytes(output.resolve("original").resolve(entry))))
                    throw new IOException("Original cohort source differs from the pinned archive: " + entry);
                dependencies.put(entry, CorpusFiles.sha256(output.resolve("source-context").resolve(entry)));
                for (String variant : List.of("original", "generated")) capture(output, variant + "/" + entry, artifacts);
                if (accepted > 0) capture(output, "evidence/" + entry + ".json", artifacts);
                for (var dependency : row.path("sourceDependencies")) {
                    Path path = Path.of(dependency.path("identity").asText()).toAbsolutePath().normalize();
                    Path root = output.resolve("source-context");
                    if (!path.startsWith(root)) throw new IOException("Source dependency outside the extracted context");
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    String hash = dependency.path("sha256").asText();
                    CorpusFiles.requireHash(path, hash);
                    if (dependencies.putIfAbsent(relative, hash) != null && !hash.equals(dependencies.get(relative)))
                        throw new IOException("Inconsistent source dependency: " + relative);
                }
                classes.put(entry, Map.of("acceptedRegions", accepted, "originalSha256", manifest.getProperty("original." + entry),
                        "generatedSha256", manifest.getProperty("generated." + entry)));
            }
        }
        var cohort = new LinkedHashMap<String, Object>();
        cohort.put("schema", "sandbox.bls-source-cohort/v1"); cohort.put("classes", classes);
        cohort.put("files", artifacts); cohort.put("sourceDependencies", dependencies);
        cohort.put("scope", "Complete original and ordinary Apply sources; original inputs and unchanged Fp12 remain explicit. Numerical samples and timing do not prove constant-time behavior.");
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("cohort.json").toFile(), cohort);
        manifest.setProperty("cohortSha256", CorpusFiles.sha256(output.resolve("cohort.json")));
        manifest.setProperty("inputsSha256", CorpusFiles.sha256(output.resolve("inputs.json")));
        try (var writer = Files.newBufferedWriter(output.resolve("benchmark.properties"))) {
            manifest.store(writer, "Complete BLS source cohort; original scanner proof and Apply receipts retained");
        }
        requireCurrent(output, binary, sources, CorpusFiles.sha256(output.resolve("benchmark.properties")));
        System.out.printf("BLS_COHORT classes=%d acceptedRegions=%d parseErrorFiles=0%n", ENTRIES.size(), report.path("acceptedRegions").asInt());
    }

    /** The caller supplies the manifest identity recorded before measurement, not a newly trusted replacement. */
    static JsonNode requireCurrent(Path directory, Path binary, Path sources, String manifestSha) throws Exception {
        directory = directory.toAbsolutePath().normalize();
        CorpusFiles.requireHash(directory.resolve("benchmark.properties"), manifestSha);
        var manifest = read(directory.resolve("benchmark.properties"));
        if (!"sandbox.generated-corpus-fixture/v1".equals(manifest.getProperty("schema"))
                || !Files.readString(directory.resolve("COMPLETED")).startsWith("COMPLETED\n")
                || !BINARY_SHA.equals(manifest.getProperty("binarySha256")) || !SOURCES_SHA.equals(manifest.getProperty("sourcesSha256"))
                || !"17".equals(manifest.getProperty("targetJava"))) throw new IOException("Unqualified BLS fixture identity");
        CorpusFiles.requireHash(binary, BINARY_SHA); CorpusFiles.requireHash(sources, SOURCES_SHA);
        for (String file : List.of("review", "inputs", "cohort"))
            CorpusFiles.requireHash(directory.resolve(file + ".json"), manifest.getProperty(file + "Sha256", ""));
        CorpusFiles.requireHash(directory.resolve("profile.properties"), manifest.getProperty("profileSha256", ""));
        var report = JSON.readTree(directory.resolve("review.json").toFile()); requireReport(report);
        var inputs = JSON.readTree(directory.resolve("inputs.json").toFile());
        for (String key : List.of("binarySha256", "sourcesSha256", "profileSha256", "targetJava"))
            if (!manifest.getProperty(key, "").equals(report.path(key).asText())) throw new IOException("Scanner/manifest mismatch: " + key);
        for (String key : List.of("schema", "binarySha256", "sourcesSha256", "profileSha256", "targetJava",
                "options", "selectedEntries", "javaRuntime", "runtimeInputs"))
            if (!inputs.path(key).equals(report.path(key))) throw new IOException("Scanner input receipt mismatch: " + key);
        var options = requireProfile(directory.resolve("profile.properties"));
        if (!JSON.valueToTree(options.toMap()).equals(report.path("options"))
                || !System.getProperty("java.runtime.version").equals(report.path("javaRuntime").asText())
                || !JSON.valueToTree(ProjectMathScan.runtimeInputs()).equals(report.path("runtimeInputs")))
            throw new IOException("Profile, JVM or adapter/SDK implementation changed");
        var cohort = JSON.readTree(directory.resolve("cohort.json").toFile());
        if (!"sandbox.bls-source-cohort/v1".equals(cohort.path("schema").asText())
                || cohort.path("classes").size() != ENTRIES.size() || !cohort.path("files").isObject()
                || !cohort.path("sourceDependencies").isObject()) throw new IOException("Incomplete BLS cohort receipt");
        for (var file : cohort.path("files").properties()) CorpusFiles.requireHash(inside(directory, file.getKey()), file.getValue().asText());
        for (var file : cohort.path("sourceDependencies").properties())
            CorpusFiles.requireHash(inside(directory, "source-context/" + file.getKey()), file.getValue().asText());
        try (var archive = new JarFile(sources.toFile())) {
            for (String entry : ENTRIES) {
                var row = row(report, entry); var declared = cohort.path("classes").path(entry);
                int accepted = row.path("acceptedRegions").asInt(-1);
                String original = row.path("sourceSha256").asText();
                String generated = accepted == 0 ? original : row.path("afterSha256").asText();
                if (accepted < 0 || accepted != declared.path("acceptedRegions").asInt(-1)
                        || !original.equals(declared.path("originalSha256").asText()) || !generated.equals(declared.path("generatedSha256").asText())
                        || !original.equals(manifest.getProperty("original." + entry)) || !generated.equals(manifest.getProperty("generated." + entry)))
                    throw new IOException("Cohort source differs from its scanner result: " + entry);
                CorpusFiles.requireHash(directory.resolve("original").resolve(entry), original);
                CorpusFiles.requireHash(directory.resolve("generated").resolve(entry), generated);
                try (var input = archive.getInputStream(archive.getJarEntry(entry))) {
                    if (!Arrays.equals(input.readAllBytes(), Files.readAllBytes(directory.resolve("original").resolve(entry))))
                        throw new IOException("Original source is not the pinned archive entry: " + entry);
                }
                if (accepted > 0 && !cohort.path("files").has("evidence/" + entry + ".json"))
                    throw new IOException("Missing original proof receipt: " + entry);
                for (var dependency : row.path("sourceDependencies")) {
                    String identity = dependency.path("identity").asText().replace('\\', '/');
                    int boundary = identity.lastIndexOf("/source-context/");
                    String relative = boundary < 0 ? "" : identity.substring(boundary + "/source-context/".length());
                    if (!dependency.path("sha256").asText().equals(cohort.path("sourceDependencies").path(relative).asText()))
                        throw new IOException("Source dependency receipt differs: " + identity);
                }
            }
        }
        return cohort;
    }

    private static MathCleanUpOptions requireProfile(Path profile) throws IOException {
        var options = ProjectMathScan.profile(profile, 17);
        if (!options.mathematicalOptIn() || options.valueRuntime() != MathCleanUpOptions.ValueRuntime.AUDITED_OPENJDK_25_0_5_8)
            throw new IOException("BLS comparison requires explicit mathematical and audited-runtime consent");
        return options;
    }
    private static void requireReport(JsonNode report) throws IOException {
        var selected = new TreeSet<String>(); report.path("selectedEntries").forEach(entry -> selected.add(entry.asText()));
        if (!"sandbox.project-math-scan/v1".equals(report.path("schema").asText())
                || !"COMPLETED".equals(report.path("status").asText()) || report.path("parseErrorFiles").asInt(-1) != 0
                || report.path("parsedFiles").asInt(-1) != ENTRIES.size() || report.path("files").size() != ENTRIES.size()
                || !report.path("files").isArray() || !report.path("selectedEntries").isArray()
                || report.path("selectedEntries").size() != ENTRIES.size() || !selected.equals(new TreeSet<>(ENTRIES))
                || report.path("acceptedRegions").asInt() <= 0) throw new IOException("Expected a complete changed five-class BLS scan");
    }
    private static JsonNode row(JsonNode report, String entry) throws IOException {
        JsonNode found = null;
        for (var row : report.path("files")) if (entry.equals(row.path("path").asText())) {
            if (found != null) throw new IOException("Duplicate scanner row: " + entry); found = row;
        }
        if (found == null || !"ANALYZED".equals(found.path("status").asText())
                || !found.path("parseErrors").isArray() || !found.path("parseErrors").isEmpty())
            throw new IOException("Missing or failed scanner row: " + entry);
        return found;
    }
    private static Path inside(Path root, String relative) throws IOException {
        Path file = root.resolve(relative).normalize();
        if (relative.isBlank() || relative.indexOf('\\') >= 0 || Path.of(relative).isAbsolute()
                || !file.startsWith(root) || !file.toRealPath().startsWith(root.toRealPath()))
            throw new IOException("Artifact outside its source cohort: " + relative);
        return file;
    }
    private static void capture(Path root, String relative, Map<String, String> files) throws IOException {
        files.put(relative, CorpusFiles.sha256(root.resolve(relative)));
    }
    static Properties read(Path file) throws IOException {
        var result = new Properties(); try (var reader = Files.newBufferedReader(file)) { result.load(reader); } return result;
    }
}
