/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.distribution;

import static org.sandbox.distribution.AggregateInstallationEvidence.children;
import static org.sandbox.distribution.AggregateInstallationEvidence.createParentDirectories;
import static org.sandbox.distribution.AggregateInstallationEvidence.fileName;
import static org.sandbox.distribution.AggregateInstallationEvidence.require;
import static org.sandbox.distribution.AggregateInstallationEvidence.xml;

import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaCompiler;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.xml.XMLConstants;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Element;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

/** Additional end-to-end gate in the existing distribution Maven reactor. */
public final class AggregateInstallationVerifier {
    private static final String AGGREGATE = "sandbox_feature.feature.group";
    private static final String CHARSET_MAIN = "probe.charset.Runner";
    private static final String FUNCTIONAL_MAIN = "probe.functional.Runner";
    private static final String DIRECTOR = "org.eclipse.equinox.p2.director";
    private static final String PROBE = "org.sandbox.distribution.aggregate.probe";
    private static final String LEGACY_COMPONENT_REPOSITORY = "https://carstenartur.github.io/sandbox/releases/1.3.4/";
    private static final String STABLE_AGGREGATE_REPOSITORY = "https://carstenartur.github.io/sandbox/releases/1.3.5/";
    private final Path root;
    private final Path evidence;
    private final Path work;
    private final Path builder;
    private final URI repository;
    private final List<String> baseRepositories = new ArrayList<>();
    private final Map<String, String> records = new TreeMap<>();
    private Path probe;
    private Path inventory;

    private record CleanupRun(int exitCode, String report, Map<Path, String> sources) { }
    record SourceAnalysis(Map<Path, List<String>> invocations, Map<Path, List<String>> fields, List<String> diagnostics) {
        SourceAnalysis {
            invocations = immutableLines(invocations);
            fields = immutableLines(fields);
            diagnostics = List.copyOf(diagnostics);
        }
    }
    record FileSnapshot(Object fileKey, FileTime lastModified) { }

    AggregateInstallationVerifier(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        evidence = this.root.resolve("target/distribution-verification/aggregate");
        Files.createDirectories(evidence);
        // Never delete an unrelated directory or reuse an old installation as evidence.
        work = Files.createTempDirectory(this.root.resolve("target"), "aggregate-installation-");
        builder = home(this.root.resolve("target/distribution-verification/fresh-install"));
        repository = this.root.resolve("sandbox_updatesite/target/repository").toUri();
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 1, "Expected the Sandbox repository root");
        new AggregateInstallationVerifier(Path.of(args[0])).verify();
    }

    private void verify() throws Exception {
        Files.deleteIfExists(evidence.resolve("verification.properties"));
        Map<String, String> current = featureVersions(repository);
        require(current.containsKey(AGGREGATE), "The built repository has no aggregate IU");
        baseRepositories.addAll(provisioningRepositories(root));
        require(!baseRepositories.isEmpty(), "Missing target repositories");
        inventory = inventory();
        probe = compileProbe();
        Path fresh = installation("fresh");
        provisionStock(fresh, "fresh-stock");
        Map<String, String> stock = hostDigests(fresh);
        provision(fresh, "fresh-aggregate", List.of(repository.toString()), List.of(AGGREGATE + '/' + current.get(AGGREGATE)), List.of());
        verifyStage(fresh, "fresh", current, stock, Set.of(AGGREGATE));
        verifyInstalledSandboxFunctionality(home(fresh), "fresh");

        String next = nextFixtureVersion(current.get(AGGREGATE));
        URI nextRepository = publishNextAggregate(current.get(AGGREGATE), next);
        provision(fresh, "aggregate-update", List.of(nextRepository.toString(), repository.toString()),
                List.of(AGGREGATE + '/' + next), List.of(AGGREGATE + '/' + current.get(AGGREGATE)));
        Map<String, String> updated = new TreeMap<>(current);
        updated.put(AGGREGATE, next);
        verifyStage(fresh, "updated", updated, stock, Set.of(AGGREGATE));

        URI releasedRepository = URI.create(System.getProperty("sandbox.aggregate.stableRepository", STABLE_AGGREGATE_REPOSITORY));
        Map<String, String> released = featureVersions(releasedRepository);
        requireCandidateUpgradeSource(releasedRepository, released, current);
        Path upgraded = installation("upgraded");
        provisionStock(upgraded, "upgraded-stock");
        Map<String, String> upgradedStock = hostDigests(upgraded);
        provision(upgraded, "released-aggregate", List.of(releasedRepository.toString()),
                List.of(AGGREGATE + '/' + released.get(AGGREGATE)), List.of());
        verifyInstalledFeatures(upgraded, "released", released, upgradedStock, Set.of(AGGREGATE));
        provision(upgraded, "released-to-candidate", List.of(repository.toString()),
                List.of(AGGREGATE + '/' + current.get(AGGREGATE)), List.of(AGGREGATE + '/' + released.get(AGGREGATE)));
        verifyStage(upgraded, "upgraded", current, upgradedStock, Set.of(AGGREGATE));
        verifyInstalledSandboxFunctionality(home(upgraded), "upgraded");

        URI previousRepository = URI.create(System.getProperty("sandbox.aggregate.previousRepository", LEGACY_COMPONENT_REPOSITORY));
        Map<String, String> previous = featureVersions(previousRepository);
        require(!previous.isEmpty() && !previous.containsKey(AGGREGATE), "The legacy fixture must have individual features, not an aggregate");
        require(current.keySet().containsAll(previous.keySet()), "The aggregate loses previously published components");
        Path legacy = installation("legacy");
        provisionStock(legacy, "legacy-stock");
        Map<String, String> legacyStock = hostDigests(legacy);
        List<String> previousRoots = previous.entrySet().stream().map(entry -> entry.getKey() + '/' + entry.getValue()).toList();
        provision(legacy, "legacy-components", List.of(previousRepository.toString()), previousRoots, List.of());
        AggregateInstallationEvidence.requireFeatures(profile(legacy, "legacy-components"), previous, previous.keySet());
        // One planner/engine transaction: update versions, never uninstall in a separate run.
        provision(legacy, "legacy-to-aggregate", List.of(repository.toString()),
                List.of(AGGREGATE + '/' + current.get(AGGREGATE)), previousRoots);
        Set<String> legacyRoots = new TreeSet<>(previous.keySet());
        legacyRoots.add(AGGREGATE);
        verifyStage(legacy, "migrated", current, legacyStock, legacyRoots);

        records.put("previousRepository", previousRepository.toString());
        records.put("releasedRepository", releasedRepository.toString());
        records.put("releasedAggregateVersion", released.get(AGGREGATE));
        records.put("previousFeatureCount", Integer.toString(previous.size()));
        records.put("aggregateIU", AGGREGATE);
        records.put("aggregateVersion", current.get(AGGREGATE));
        records.put("componentCount", Integer.toString(current.size() - 1));
        records.put("updateFixtureVersion", next);
        records.put("updateFixtureScope", "aggregate-metadata-only; not a future released component build");
        records.put("status", "PASS");
        records.put("schemaVersion", "1");
        records.put("platform", System.getProperty("os.name") + '/' + System.getProperty("os.arch"));
        Files.writeString(evidence.resolve("verification.properties"), records.entrySet().stream()
                .map(entry -> entry.getKey() + '=' + entry.getValue() + '\n').reduce("", String::concat));
        System.out.println("Aggregate installation, legacy migration and aggregate update: PASS");
    }

    static List<String> provisioningRepositories(Path root) throws Exception {
        return DistributionVerifier.targetRepositories(root.resolve("sandbox_target/eclipse.target"));
    }

    static List<String> platformArguments(String osName, String archName) throws Exception {
        var platform = DistributionVerifier.Platform.from(osName, archName);
        return List.of("-p2.os", platform.osgiOs(), "-p2.ws", platform.osgiWs(), "-p2.arch", platform.osgiArch());
    }

    private void provisionStock(Path destination, String stage) throws Exception {
        var bootstrap = AggregateInstallationEvidence.read(latestProfile(builder));
        List<String> roots = new ArrayList<>();
        for (String id : List.of("org.eclipse.sdk.ide", "org.eclipse.equinox.p2.extras.feature.feature.group", "org.eclipse.equinox.executable.feature.group")) {
            Set<String> versions = bootstrap.units().getOrDefault(id, Set.of());
            require(versions.size() == 1, "Ambiguous bootstrap IU " + id);
            roots.add(id + '/' + versions.iterator().next());
        }
        provision(destination, stage, List.of(), roots, List.of());
        AggregateInstallationEvidence.requireFeatures(profile(destination, stage), Map.of(), Set.of());
    }

    private void provision(Path destination, String stage, List<String> additionalRepositories,
            List<String> install, List<String> uninstall) throws Exception {
        List<String> repositories = new ArrayList<>(additionalRepositories);
        repositories.addAll(baseRepositories);
        List<String> arguments = new ArrayList<>(List.of("-repository", String.join(",", repositories),
                "-destination", destination.toString(), "-bundlepool", destination.toString(),
                "-profile", "SandboxAggregate", "-profileProperties", "org.eclipse.update.install.features=true", "-roaming"));
        arguments.addAll(platformArguments(System.getProperty("os.name", ""), System.getProperty("os.arch", "")));
        if (!uninstall.isEmpty()) arguments.addAll(List.of("-uninstallIU", String.join(",", uninstall)));
        if (!install.isEmpty()) arguments.addAll(List.of("-installIU", String.join(",", install)));
        run(builder, stage, DIRECTOR, arguments, Duration.ofMinutes(15));
    }

    private void verifyStage(Path installation, String stage, Map<String, String> versions,
            Map<String, String> stock, Set<String> expectedRoots) throws Exception {
        verifyInstalledFeatures(installation, stage, versions, stock, expectedRoots);
        Path home = home(installation);
        Path result = evidence.resolve(stage + "-runtime.json");
        FileSnapshot previous = snapshot(result);
        Files.deleteIfExists(result);
        Path configuration = home.resolve("configuration/org.eclipse.equinox.simpleconfigurator/bundles.info");
        byte[] original = Files.readAllBytes(configuration);
        try {
            Files.writeString(configuration, new String(original, StandardCharsets.UTF_8) + '\n'
                    + PROBE + ",1.0.0," + probe.toUri() + ",4,false\n");
            run(home, stage + "-runtime", PROBE + ".verify", List.of(inventory.toString(), result.toString()), Duration.ofMinutes(3));
            requireFreshFile(result, previous, "Runtime probe result");
        } finally {
            Files.write(configuration, original);
        }
        require(java.util.Arrays.equals(original, Files.readAllBytes(configuration)), "Probe changed installation configuration");
        formatterSmoke(home, stage);
    }

    private void verifyInstalledFeatures(Path installation, String stage, Map<String, String> versions,
            Map<String, String> stock, Set<String> expectedRoots) throws Exception {
        Path home = home(installation);
        AggregateInstallationEvidence.requireFeatures(profile(installation, stage), versions, expectedRoots);
        records.put(stage + ".sandboxRoots", String.join(",", new TreeSet<>(expectedRoots)));
        Map<String, String> actualHosts = hostDigests(installation);
        require(actualHosts.equals(stock), "Aggregate replaced a stock JDT/LTK host in " + stage);
        actualHosts.forEach((id, hash) -> records.put(stage + ".host." + id + ".sha256", hash));
        for (var entry : versions.entrySet()) {
            String id = entry.getKey().substring(0, entry.getKey().length() - ".feature.group".length());
            Path file = home.resolve("features/" + id + '_' + entry.getValue() + "/feature.xml");
            Element feature = read(file);
            require(id.equals(feature.getAttribute("id")) && entry.getValue().equals(feature.getAttribute("version")), "Installed feature identity mismatch: " + file);
            records.put(stage + '.' + id, entry.getValue());
        }
    }

    private void formatterSmoke(Path home, String stage) throws Exception {
        Path project = work.resolve(stage + "-input/SmokeProject");
        Path source = project.resolve("src/smoke/Smoke.java");
        createParentDirectories(source);
        String before = "package smoke;public class Smoke{void run(){System.out.println(\"smoke\");}}";
        Files.writeString(source, before);
        Files.writeString(project.resolve(".project"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <projectDescription><name>SmokeProject</name><projects/>
                <buildSpec><buildCommand><name>org.eclipse.jdt.core.javabuilder</name><arguments/></buildCommand></buildSpec>
                <natures><nature>org.eclipse.jdt.core.javanature</nature></natures>
                </projectDescription>
                """);
        Files.writeString(project.resolve(".classpath"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <classpath><classpathentry kind="src" path="src"/>
                <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
                <classpathentry kind="output" path="bin"/></classpath>
                """);
        Path config = evidence.resolve("cleanup.properties");
        Files.writeString(config, "cleanup.format_source_code=true\ncleanup.format_source_code_changes_only=false\n");
        Map<Path, String> original = captureSources(List.of(source));
        CleanupRun apply = runCleanup(home, stage + "-cleanup", "apply", project, "both", config, List.of(source), List.of(source), Set.of(0));
        requireCleanupReport(stage + " formatter", apply.report(), "apply", 1, Set.of(source), 0);
        requireChangedSources(stage + " formatter", original, apply.sources(), Set.of(source));
        Path classes = work.resolve(stage + "-classes");
        requireCompilation(List.of(source), classes);
        Files.copy(source, evidence.resolve(stage + "-Smoke.java"), StandardCopyOption.REPLACE_EXISTING);
    }

    private void verifyInstalledSandboxFunctionality(Path home, String stage) throws Exception {
        verifyCharsetCleanup(home, stage, "keep", "cleanup.explicit_encoding_keep_behavior"); //$NON-NLS-1$ //$NON-NLS-2$
        verifyCharsetCleanup(home, stage, "utf8", "cleanup.explicit_encoding_insert_utf8"); //$NON-NLS-1$ //$NON-NLS-2$
        verifyCharsetCleanup(home, stage, "aggregate", "cleanup.explicit_encoding_aggregate_to_utf8"); //$NON-NLS-1$ //$NON-NLS-2$
        verifyFunctionalCleanup(home, stage);
    }

    private void verifyCharsetCleanup(Path home, String stage, String behavior, String option) throws Exception {
        Path project = writeCharsetProject(stage, behavior);
        Path alpha = project.resolve("src/main/java/probe/charset/Alpha.java");
        Path beta = project.resolve("src/main/java/probe/charset/Beta.java");
        Path runner = project.resolve("src/main/java/probe/charset/Runner.java");
        Path skipped = project.resolve("src/test/java/probe/charset/Skip.java");
        List<Path> tracked = List.of(alpha, beta, runner, skipped);
        Map<Path, String> original = captureSources(tracked);
        writeSourceEvidence(stage + "-charset-" + behavior, "before", project, original);
        Path config = evidence.resolve(stage + "-charset-" + behavior + ".properties");
        Files.writeString(config, "cleanup.explicit_encoding=true\n" + option + "=true\n");
        Path originalClasses = work.resolve(stage + "-charset-" + behavior + "-original-classes");
        List<String> originalDiagnostics = compileSources(allJavaSources(project), originalClasses);
        SourceAnalysis originalAnalysis = analyzeCharsetSources(allJavaSources(project));
        writeAnalysisEvidence(stage + "-charset-" + behavior, "before", project, originalAnalysis);
        requireJavaMain(originalClasses, CHARSET_MAIN, "PASS", stage + "-charset-" + behavior + "-before");

        CleanupRun check = runCleanup(home, stage + "-charset-" + behavior + "-check", "check", project, "main",
                config, List.of(project), tracked, Set.of(2));
        requireCleanupReport(stage + " charset " + behavior + " check", check.report(), "check", 3,
                Set.of(alpha, beta), 0);
        requireUnchangedSources(stage + " charset " + behavior + " check", original, check.sources());

        CleanupRun apply = runCleanup(home, stage + "-charset-" + behavior + "-apply", "apply", project, "main",
                config, List.of(project), tracked, Set.of(0));
        requireCleanupReport(stage + " charset " + behavior + " apply", apply.report(), "apply", 3,
                Set.of(alpha, beta), 0);
        requireChangedSources(stage + " charset " + behavior + " apply", original, apply.sources(), Set.of(alpha, beta));
        writeSourceEvidence(stage + "-charset-" + behavior, "after", project, apply.sources());
        Path classes = work.resolve(stage + "-charset-" + behavior + "-classes");
        List<String> changedDiagnostics = compileSources(allJavaSources(project), classes);
        require(originalDiagnostics.equals(changedDiagnostics),
                stage + " charset " + behavior + " diagnostics differ; expected " + originalDiagnostics + ", actual " + changedDiagnostics);
        SourceAnalysis changedAnalysis = analyzeCharsetSources(allJavaSources(project));
        writeAnalysisEvidence(stage + "-charset-" + behavior, "after", project, changedAnalysis);
        requireCharsetSources(behavior, changedAnalysis, alpha, beta, apply.sources().get(runner), apply.sources().get(skipped));
        requireJavaMain(classes, CHARSET_MAIN, "PASS", stage + "-charset-" + behavior);

        CleanupRun idempotent = runCleanup(home, stage + "-charset-" + behavior + "-idempotent", "check", project,
                "main", config, List.of(project), tracked, Set.of(0));
        requireCleanupReport(stage + " charset " + behavior + " idempotent", idempotent.report(), "check", 3,
                Set.of(), 0);
        requireUnchangedSources(stage + " charset " + behavior + " idempotent", apply.sources(), idempotent.sources());
    }

    private void verifyFunctionalCleanup(Path home, String stage) throws Exception {
        Path project = writeFunctionalProject(stage);
        Path sample = project.resolve("src/main/java/probe/functional/LoopSample.java");
        Path runner = project.resolve("src/main/java/probe/functional/Runner.java");
        List<Path> tracked = List.of(sample, runner);
        Map<Path, String> original = captureSources(tracked);
        writeSourceEvidence(stage + "-functional", "before", project, original);
        Path config = evidence.resolve(stage + "-functional.properties");
        Files.writeString(config, "cleanup.functionalloop=true\n");
        Path originalClasses = work.resolve(stage + "-functional-before-classes");
        requireCompilation(allJavaSources(project), originalClasses);
        requireJavaMain(originalClasses, FUNCTIONAL_MAIN, "ab", stage + "-functional-before");

        CleanupRun apply = runCleanup(home, stage + "-functional-apply", "apply", project, "main", config,
                List.of(project), tracked, Set.of(0));
        requireCleanupReport(stage + " functional apply", apply.report(), "apply", 2, Set.of(sample), 0);
        requireChangedSources(stage + " functional apply", original, apply.sources(), Set.of(sample));
        require(apply.sources().get(sample).contains(".forEach("), "Functional cleanup did not produce a deterministic forEach rewrite");
        writeSourceEvidence(stage + "-functional", "after", project, apply.sources());
        Path classes = work.resolve(stage + "-functional-classes");
        requireCompilation(allJavaSources(project), classes);
        requireJavaMain(classes, FUNCTIONAL_MAIN, "ab", stage + "-functional");
    }

    private CleanupRun runCleanup(Path home, String stage, String mode, Path project, String scope, Path config,
            List<Path> inputs, List<Path> trackedSources, Set<Integer> expectedExitCodes) throws Exception {
        Path report = evidence.resolve(stage + ".json");
        Path patch = evidence.resolve(stage + ".patch");
        FileSnapshot previous = snapshot(report);
        Files.deleteIfExists(report);
        Files.deleteIfExists(patch);
        List<String> arguments = new ArrayList<>(List.of("--import-project", project.toString(), "--mode", mode,
                "--scope", scope, "--patch", patch.toString(), "--report", report.toString(), "--config",
                config.toString()));
        inputs.stream().map(Path::toString).forEach(arguments::add);
        int exitCode = run(home, stage, "org.sandbox.jdt.core.JavaCleanup", arguments, Duration.ofMinutes(3),
                expectedExitCodes);
        requireFreshFile(report, previous, "Cleanup report");
        return new CleanupRun(exitCode, Files.readString(report), captureSources(trackedSources));
    }

    static void requireCandidateUpgradeSource(URI releasedRepository, Map<String, String> released,
            Map<String, String> current) throws IOException {
        String oldVersion = released.get(AGGREGATE);
        require(oldVersion != null, "The published 1.3.5 repository has no aggregate IU: " + releasedRepository);
        require("1.3.5".equals(baseVersion(oldVersion)), "Expected published aggregate 1.3.5, found " + oldVersion);
        for (var entry : released.entrySet()) {
            require("1.3.5".equals(baseVersion(entry.getValue())),
                    "Expected published 1.3.5 component version for " + entry.getKey() + ", found " + entry.getValue());
            String candidate = current.get(entry.getKey());
            require(candidate != null, "Candidate repository misses published component " + entry.getKey());
            require(compareBaseVersions(candidate, entry.getValue()) > 0,
                    "Candidate version is not newer for " + entry.getKey() + ": old=" + entry.getValue() + ", new=" + candidate);
        }
    }

    static int compareBaseVersions(String left, String right) throws IOException {
        int[] leftParts = baseVersionParts(left);
        int[] rightParts = baseVersionParts(right);
        for (int index = 0; index < leftParts.length; index++) {
            if (leftParts[index] != rightParts[index]) return Integer.compare(leftParts[index], rightParts[index]);
        }
        return 0;
    }

    static String baseVersion(String version) throws IOException {
        int[] parts = baseVersionParts(version);
        return parts[0] + "." + parts[1] + "." + parts[2];
    }

    private static int[] baseVersionParts(String version) throws IOException {
        String[] parts = version.split("\\.", 4);
        require(parts.length >= 3, "Invalid OSGi version: " + version);
        try {
            return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]) };
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid OSGi version: " + version, exception);
        }
    }

    static void requireFreshFile(Path file, FileSnapshot previous, String label) throws IOException {
        require(Files.isRegularFile(file), label + " produced no result");
        if (previous == null) return;
        BasicFileAttributes current = Files.readAttributes(file, BasicFileAttributes.class);
        boolean sameKey = previous.fileKey() != null && previous.fileKey().equals(current.fileKey());
        boolean notNewer = !current.lastModifiedTime().toInstant().isAfter(previous.lastModified().toInstant());
        require(!sameKey || !notNewer, label + " is stale: " + file);
    }

    static void requireCompilation(List<Path> sources, Path classes) throws IOException {
        compileSources(sources, classes);
    }

    static void requireChangedSources(String label, Map<Path, String> before, Map<Path, String> after,
            Set<Path> expectedChanged) throws IOException {
        Set<Path> actualChanged = new TreeSet<>(Comparator.comparing(Path::toString));
        for (var entry : before.entrySet()) {
            if (!entry.getValue().equals(after.get(entry.getKey()))) actualChanged.add(entry.getKey());
        }
        require(actualChanged.equals(expectedChanged), label + " changed sources differ; expected "
                + expectedChanged + ", actual " + actualChanged);
    }

    static void requireUnchangedSources(String label, Map<Path, String> before, Map<Path, String> after)
            throws IOException {
        require(before.equals(after), label + " changed input during check mode");
    }

    static void requireCleanupReport(String label, String report, String mode, int filesProcessed, Set<Path> changed,
            int errorCount) throws IOException {
        JsonObject parsed = jsonObject(label, report);
        require(mode.equals(jsonString(parsed, "mode")), label + " recorded wrong mode: " + report);
        require(filesProcessed == jsonInt(parsed, "filesProcessed"),
                label + " recorded wrong filesProcessed count: " + report);
        require(changed.size() == jsonInt(parsed, "filesChanged"),
                label + " recorded wrong filesChanged count: " + report);
        require(errorCount == jsonInt(parsed, "errorCount"),
                label + " recorded wrong errorCount: " + report);
        List<String> expectedChanged = changed.stream().map(Path::toString).sorted().toList();
        List<String> actualChanged = new ArrayList<>();
        for (JsonElement element : jsonArray(parsed, "changedFiles")) {
            require(element.isJsonPrimitive() && element.getAsJsonPrimitive().isString(),
                    label + " recorded non-string changedFiles entry: " + report);
            actualChanged.add(element.getAsString());
        }
        actualChanged.sort(String::compareTo);
        require(expectedChanged.equals(actualChanged),
                label + " recorded wrong changedFiles array; expected " + expectedChanged + ", actual " + actualChanged + ": " + report);
        JsonArray errors = jsonArray(parsed, "errors");
        require(errorCount == errors.size(),
                label + " recorded wrong errors array size: " + report);
    }

    private static JsonObject jsonObject(String label, String json) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            require(parsed.isJsonObject(), label + " did not produce a JSON object: " + json);
            return parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IOException(label + " did not produce parseable JSON", exception);
        }
    }

    private static String jsonString(JsonObject object, String field) throws IOException {
        JsonElement value = object.get(field);
        require(value != null, "Missing JSON string field " + field);
        require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(),
                "JSON field is not a string: " + field);
        return value.getAsString();
    }

    private static int jsonInt(JsonObject object, String field) throws IOException {
        JsonElement value = object.get(field);
        require(value != null, "Missing JSON number field " + field);
        require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(),
                "JSON field is not a number: " + field);
        return value.getAsInt();
    }

    private static JsonArray jsonArray(JsonObject object, String field) throws IOException {
        JsonElement value = object.get(field);
        require(value != null, "Missing JSON array field " + field);
        require(value.isJsonArray(), "JSON field is not an array: " + field);
        return value.getAsJsonArray();
    }

    private static Map<Path, String> captureSources(List<Path> files) throws IOException {
        Map<Path, String> result = new LinkedHashMap<>();
        for (Path file : files) result.put(file, Files.readString(file));
        return result;
    }

    private static List<Path> allJavaSources(Path project) throws IOException {
        try (var files = Files.walk(project)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    Path writeCharsetProject(String stage, String behavior) throws IOException {
        Path project = work.resolve(stage + "-charset-" + behavior + "/CharsetProject");
        writeProjectMetadata(project, "CharsetProject");
        writeJavaFile(project.resolve("src/main/java/probe/charset/Alpha.java"), """
                package probe.charset;
                public class Alpha {
                    public static byte[] bytes(String text) throws Exception {
                        return text.getBytes("UTF-8");
                    }
                    public static String decode(byte[] bytes) throws Exception {
                        return new String(bytes, "UTF-8");
                    }
                }
                """);
        writeJavaFile(project.resolve("src/main/java/probe/charset/Beta.java"), """
                package probe.charset;
                import java.nio.charset.Charset;
                public class Beta {
                    public static Charset charset() throws Exception {
                        return Charset.forName("UTF-8");
                    }
                    public static String roundTrip(String text) throws Exception {
                        return new String(text.getBytes("UTF-8"), charset());
                    }
                }
                """);
        writeJavaFile(project.resolve("src/main/java/probe/charset/Runner.java"), """
                package probe.charset;
                public class Runner {
                    public static void main(String[] args) throws Exception {
                        String text = "Grüße";
                        boolean pass = Alpha.decode(Alpha.bytes(text)).equals(text)
                                && Beta.roundTrip(text).equals(text)
                                && Beta.charset().equals(java.nio.charset.StandardCharsets.UTF_8);
                        System.out.print(pass ? "PASS" : "FAIL");
                    }
                }
                """);
        writeJavaFile(project.resolve("src/test/java/probe/charset/Skip.java"), """
                package probe.charset;
                class Skip {
                    static String untouched(byte[] bytes) throws Exception {
                        return new String(bytes, "UTF-8");
                    }
                }
                """);
        return project;
    }

    private static void requireCharsetSources(String behavior, SourceAnalysis analysis, Path alpha, Path beta, String runner, String skipped)
            throws IOException {
        requireResolvedInvocations(alpha, analysis, Set.of(
                "bytes -> java.lang.String.getBytes(java.nio.charset.Charset)",
                "decode -> java.lang.String.<init>(byte[],java.nio.charset.Charset)"));
        requireResolvedInvocations(beta, analysis, Set.of(
                "roundTrip -> java.lang.String.getBytes(java.nio.charset.Charset)",
                "roundTrip -> java.lang.String.<init>(byte[],java.nio.charset.Charset)"));
        require(runner.contains("Beta.charset().equals(java.nio.charset.StandardCharsets.UTF_8)"),
                "Charset runtime probe runner changed unexpectedly");
        require(skipped.contains("new String(bytes, \"UTF-8\")"),
                "Main-scope cleanup touched the explicit negative-scope source");
        if ("aggregate".equals(behavior)) {
            requireResolvedUtf8Field(alpha, analysis);
            requireResolvedUtf8Field(beta, analysis);
        }
    }

    Path writeFunctionalProject(String stage) throws IOException {
        Path project = work.resolve(stage + "-functional/FunctionalProject");
        writeProjectMetadata(project, "FunctionalProject");
        writeJavaFile(project.resolve("src/main/java/probe/functional/LoopSample.java"), """
                package probe.functional;
                import java.util.List;
                class LoopSample {
                    static void print(List<String> items) {
                        for (String item : items) {
                            System.out.print(item.trim());
                        }
                    }
                }
                """);
        writeJavaFile(project.resolve("src/main/java/probe/functional/Runner.java"), """
                package probe.functional;
                import java.util.List;
                public class Runner {
                    public static void main(String[] args) {
                        LoopSample.print(List.of(" a ", "b"));
                    }
                }
                """);
        return project;
    }

    private static void writeProjectMetadata(Path project, String name) throws IOException {
        Files.createDirectories(project.resolve("src/main/java"));
        Files.createDirectories(project.resolve("src/test/java"));
        Files.writeString(project.resolve(".project"), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<projectDescription><name>" + name + "</name><projects/>\n"
                + "<buildSpec><buildCommand><name>org.eclipse.jdt.core.javabuilder</name><arguments/></buildCommand></buildSpec>\n"
                + "<natures><nature>org.eclipse.jdt.core.javanature</nature></natures>\n"
                + "</projectDescription>\n");
        Files.writeString(project.resolve(".classpath"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <classpath><classpathentry kind="src" path="src/main/java"/>
                <classpathentry kind="src" path="src/test/java"/>
                <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
                <classpathentry kind="output" path="bin"/></classpath>
                """);
    }

    private static void writeJavaFile(Path file, String source) throws IOException {
        createParentDirectories(file);
        Files.writeString(file, source);
    }

    private void requireJavaMain(Path classes, String mainClass, String expectedOutput, String stage) throws Exception {
        Path log = evidence.resolve(stage + "-java.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classes.toString(), mainClass).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            require(process.waitFor(60, TimeUnit.SECONDS), "Timed out: " + stage + "; see " + log);
            require(process.exitValue() == 0, "Failed: " + stage + "; see " + log);
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
        require(expectedOutput.equals(Files.readString(log)), "Unexpected Java runtime result for " + stage);
    }

    static FileSnapshot snapshot(Path file) throws IOException {
        if (!Files.exists(file)) return null;
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
        return new FileSnapshot(attributes.fileKey(), attributes.lastModifiedTime());
    }

    static SourceAnalysis analyzeCharsetSources(List<Path> sources) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        require(compiler != null, "A full JDK is required to analyze the transformed source");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Map<Path, List<String>> invocations = new TreeMap<>(Comparator.comparing(Path::toString));
        Map<Path, List<String>> fields = new TreeMap<>(Comparator.comparing(Path::toString));
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sources);
            JavacTask task = (JavacTask) compiler.getTask(null, fileManager, diagnostics, List.of("--release", "21", "-proc:none"),
                    null, units);
            Iterable<? extends CompilationUnitTree> trees = task.parse();
            task.analyze();
            Trees analysis = Trees.instance(task);
            for (CompilationUnitTree unit : trees) {
                Path source = Path.of(unit.getSourceFile().toUri());
                new TreePathScanner<Void, Void>() {
                    private String enclosing = "<initializer>";

                    @Override
                    public Void visitMethod(MethodTree node, Void unused) {
                        String previous = enclosing;
                        enclosing = node.getName().toString();
                        try {
                            return super.visitMethod(node, unused);
                        } finally {
                            enclosing = previous;
                        }
                    }

                    @Override
                    public Void visitNewClass(NewClassTree node, Void unused) {
                        if (analysis.getElement(getCurrentPath()) instanceof ExecutableElement executable
                                && executable.getKind() == ElementKind.CONSTRUCTOR
                                && "java.lang.String".equals(executable.getEnclosingElement().toString())) {
                            invocations.computeIfAbsent(source, key -> new ArrayList<>())
                                    .add(enclosing + " -> java.lang.String.<init>" + descriptor(executable));
                        }
                        return super.visitNewClass(node, unused);
                    }

                    @Override
                    public Void visitVariable(VariableTree node, Void unused) {
                        if (analysis.getElement(getCurrentPath()) instanceof VariableElement variable
                                && "UTF_8".contentEquals(variable.getSimpleName())
                                && variable.getEnclosingElement() != null
                                && variable.getEnclosingElement().getKind().isClass()) {
                            fields.computeIfAbsent(source, key -> new ArrayList<>()).add(variable.getEnclosingElement()
                                    + "." + variable.getSimpleName() + ':' + variable.asType() + '=' + node.getInitializer());
                        }
                        return super.visitVariable(node, unused);
                    }

                    @Override
                    public Void visitMethodInvocation(com.sun.source.tree.MethodInvocationTree node, Void unused) {
                        if (analysis.getElement(getCurrentPath()) instanceof ExecutableElement executable
                                && "getBytes".contentEquals(executable.getSimpleName())
                                && "java.lang.String".equals(executable.getEnclosingElement().toString())) {
                            invocations.computeIfAbsent(source, key -> new ArrayList<>())
                                    .add(enclosing + " -> java.lang.String.getBytes" + descriptor(executable));
                        }
                        return super.visitMethodInvocation(node, unused);
                    }
                }.scan(unit, null);
            }
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Failed to analyze charset sources", exception);
        }
        invocations.values().forEach(lines -> lines.sort(String::compareTo));
        fields.values().forEach(lines -> lines.sort(String::compareTo));
        return new SourceAnalysis(invocations, fields, diagnostics(diagnostics));
    }

    private static List<String> compileSources(List<Path> sources, Path classes) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        require(compiler != null, "A full JDK is required to compile the transformed source");
        Files.createDirectories(classes);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sources);
            List<String> arguments = List.of("--release", "21", "-d", classes.toString());
            Boolean success = compiler.getTask(null, fileManager, diagnostics, arguments, null, units).call();
            List<String> normalized = diagnostics(diagnostics);
            require(Boolean.TRUE.equals(success), "Invalid Java output: " + String.join(System.lineSeparator(), normalized));
            return normalized;
        }
    }

    private static String descriptor(ExecutableElement executable) {
        return executable.getParameters().stream().map(parameter -> parameter.asType().toString())
                .collect(java.util.stream.Collectors.joining(",", "(", ")"));
    }

    private static List<String> diagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .map(diagnostic -> diagnostic.getKind() + ":" + diagnostic.getCode() + ':' + diagnostic.getLineNumber() + ':'
                        + diagnostic.getColumnNumber() + ':' + diagnostic.getMessage(java.util.Locale.ROOT))
                .toList();
    }

    private static void requireResolvedInvocations(Path source, SourceAnalysis analysis, Set<String> expected) throws IOException {
        List<String> actual = analysis.invocations().getOrDefault(source, List.of());
        List<String> sortedExpected = expected.stream().sorted().toList();
        require(sortedExpected.equals(actual),
                "Resolved encoding invocations differ for " + source + "; expected " + sortedExpected + ", actual " + actual);
    }

    private static void requireResolvedUtf8Field(Path source, SourceAnalysis analysis) throws IOException {
        List<String> fields = analysis.fields().getOrDefault(source, List.of());
        require(fields.size() == 1, "Aggregate cleanup created wrong UTF_8 field count for " + source + ": " + fields);
        require(fields.getFirst().endsWith(":java.nio.charset.Charset=java.nio.charset.StandardCharsets.UTF_8"),
                "Aggregate cleanup created the wrong UTF_8 field for " + source + ": " + fields.getFirst());
    }

    private void writeSourceEvidence(String stage, String label, Path project, Map<Path, String> sources) throws Exception {
        Path directory = evidence.resolve(stage + '-' + label + "-sources");
        recreateDirectory(directory);
        List<String> digests = new ArrayList<>();
        for (var entry : sources.entrySet()) {
            Path relative = project.relativize(entry.getKey());
            Path target = directory.resolve(relative.toString());
            createParentDirectories(target);
            Files.writeString(target, entry.getValue());
            digests.add(digest(target) + "  " + relative.toString().replace('\\', '/'));
        }
        Files.write(evidence.resolve(stage + '-' + label + "-sources.sha256"), digests);
    }

    private void writeAnalysisEvidence(String stage, String label, Path project, SourceAnalysis analysis) throws IOException {
        List<String> invocations = new ArrayList<>();
        for (var entry : analysis.invocations().entrySet()) {
            String relative = project.relativize(entry.getKey()).toString().replace('\\', '/');
            entry.getValue().forEach(line -> invocations.add(relative + '\t' + line));
        }
        Files.write(evidence.resolve(stage + '-' + label + "-overloads.txt"), invocations);
        List<String> fields = new ArrayList<>();
        for (var entry : analysis.fields().entrySet()) {
            String relative = project.relativize(entry.getKey()).toString().replace('\\', '/');
            entry.getValue().forEach(line -> fields.add(relative + '\t' + line));
        }
        Files.write(evidence.resolve(stage + '-' + label + "-fields.txt"), fields);
        Files.write(evidence.resolve(stage + '-' + label + "-diagnostics.txt"), analysis.diagnostics());
    }

    private static Map<Path, List<String>> immutableLines(Map<Path, List<String>> values) {
        Map<Path, List<String>> copy = new LinkedHashMap<>();
        values.forEach((path, lines) -> copy.put(path, List.copyOf(lines)));
        return Map.copyOf(copy);
    }

    private static void recreateDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
        Files.createDirectories(directory);
    }

    private URI publishNextAggregate(String current, String next) throws Exception {
        Path source = work.resolve("next-source");
        Path feature = source.resolve("features/sandbox_feature_" + next);
        Files.createDirectories(feature);
        Files.createDirectories(source.resolve("plugins"));
        try (ZipFile archive = new ZipFile(Path.of(repository).resolve("features/sandbox_feature_" + current + ".jar").toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                Path output = feature.resolve(entry.getName()).normalize();
                require(output.startsWith(feature), "Unsafe feature entry");
                if (entry.isDirectory()) Files.createDirectories(output);
                else {
                    createParentDirectories(output);
                    try (InputStream stream = archive.getInputStream(entry)) { Files.copy(stream, output); }
                }
            }
        }
        Element metadata = read(feature.resolve("feature.xml"));
        require(current.equals(metadata.getAttribute("version")), "Wrong update fixture origin");
        metadata.setAttribute("version", next);
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        factory.newTransformer().transform(new DOMSource(metadata.getOwnerDocument()), new StreamResult(feature.resolve("feature.xml").toFile()));
        Path target = work.resolve("next-repository");
        Files.createDirectories(target);
        run(builder, "publish-update-fixture", "org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher",
                List.of("-source", source.toString(), "-metadataRepository", target.toUri().toString(),
                        "-artifactRepository", target.toUri().toString(), "-publishArtifacts", "-compress"), Duration.ofMinutes(3));
        require(featureVersions(target.toUri()).equals(Map.of(AGGREGATE, next)), "Unexpected fixture metadata");
        return target.toUri();
    }

    static String nextFixtureVersion(String version) throws IOException {
        String[] parts = version.split("\\.", 4);
        require(parts.length >= 3, "Invalid OSGi version: " + version);
        return parts[0] + '.' + parts[1] + '.' + Math.addExact(Integer.parseInt(parts[2]), 1) + ".aggregateVerification";
    }

    private Path inventory() throws Exception {
        Set<String> bundles = new TreeSet<>();
        Element aggregate = read(root.resolve("sandbox_feature/feature.xml"));
        for (Element include : children(aggregate, "includes")) {
            Path feature = root.resolve(include.getAttribute("id") + "/feature.xml");
            for (Element plugin : children(read(feature), "plugin")) bundles.add(plugin.getAttribute("id"));
        }
        // Shared Help navigation is contributed by the mandatory common runtime.
        bundles.add("sandbox_common");
        List<String> lines = new ArrayList<>();
        for (String id : bundles) {
            lines.add("bundle\t" + id);
            Path descriptor = root.resolve(id + "/plugin.xml");
            if (!Files.isRegularFile(descriptor)) continue;
            for (Element extension : children(read(descriptor), "extension")) {
                String point = extension.getAttribute("point");
                if (point.equals("org.eclipse.jdt.ui.cleanUps")) {
                    for (Element cleanup : children(extension, "cleanUp")) lines.add("cleanup\t" + id + '\t' + cleanup.getAttribute("id"));
                } else if (point.equals("org.eclipse.help.toc")) {
                    for (Element toc : children(extension, "toc")) lines.add("toc\t" + id + '\t' + toc.getAttribute("file"));
                }
            }
        }
        Path result = evidence.resolve("expected-runtime.tsv");
        Files.write(result, lines);
        return result;
    }

    private Path compileProbe() throws Exception {
        Path source = work.resolve("AggregateProbe.java");
        try (InputStream stream = AggregateInstallationVerifier.class.getResourceAsStream("/probe/AggregateProbe.java")) {
            require(stream != null, "Missing runtime probe source");
            Files.copy(stream, source);
        }
        Path classes = work.resolve("probe-classes");
        Files.createDirectories(classes);
        String classpath;
        try (var files = Files.list(builder.resolve("plugins"))) {
            classpath = files.filter(path -> path.toString().endsWith(".jar")).sorted().map(Path::toString)
                    .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        }
        require(ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "21", "-classpath", classpath,
                "-d", classes.toString(), source.toString()) == 0, "Runtime probe compilation failed");
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.putValue("Manifest-Version", "1.0");
        attributes.putValue("Bundle-ManifestVersion", "2");
        attributes.putValue("Bundle-SymbolicName", PROBE + ";singleton:=true");
        attributes.putValue("Bundle-Version", "1.0.0");
        attributes.putValue("Require-Bundle", "org.eclipse.core.runtime,org.eclipse.equinox.app,org.eclipse.jdt.ui,org.eclipse.help");
        attributes.putValue("Import-Package", "org.osgi.framework,org.osgi.framework.wiring");
        attributes.putValue("Bundle-RequiredExecutionEnvironment", "JavaSE-21");
        Path archive = work.resolve("aggregate-probe.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(archive), manifest)) {
            output.putNextEntry(new JarEntry("plugin.xml"));
            output.write(("<plugin><extension point='org.eclipse.core.runtime.applications' id='verify'><application><run class='org.sandbox.distribution.probe.AggregateProbe'/></application></extension></plugin>").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            try (var files = Files.walk(classes)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                    Files.copy(file, output);
                    output.closeEntry();
                }
            }
        }
        return archive;
    }

    private AggregateInstallationEvidence.Profile profile(Path installation, String stage) throws Exception {
        Path profile = latestProfile(home(installation));
        Files.copy(profile, evidence.resolve(stage + (profile.toString().endsWith(".gz") ? ".profile.gz" : ".profile")), StandardCopyOption.REPLACE_EXISTING);
        records.put(stage + ".profile.sha256", digest(profile));
        return AggregateInstallationEvidence.read(profile);
    }

    private static Path latestProfile(Path home) throws IOException {
        Path registry = home.resolve("p2/org.eclipse.equinox.p2.engine/profileRegistry");
        try (var files = Files.walk(registry, 2)) {
            return files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".profile") || path.toString().endsWith(".profile.gz"))
                    .max(Comparator.comparingLong(path -> Long.parseLong(fileName(path).split("\\.")[0])))
                    .orElseThrow(() -> new IOException("No p2 profile in " + registry));
        }
    }

    private Map<String, String> hostDigests(Path installation) throws Exception {
        Map<String, String> result = new TreeMap<>();
        Path home = home(installation);
        for (String id : List.of("org.eclipse.jdt.ui", "org.eclipse.ltk.core.refactoring", "org.eclipse.ltk.ui.refactoring")) {
            try (var files = Files.list(home.resolve("plugins"))) {
                List<Path> matches = files.filter(path -> fileName(path).startsWith(id + '_')).toList();
                require(matches.size() == 1, "Ambiguous stock host " + id);
                result.put(id, digest(matches.getFirst()));
            }
        }
        return result;
    }

    private static String digest(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private Path installation(String name) {
        return work.resolve(System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("mac") ? name + ".app" : name);
    }

    private static Path home(Path installation) {
        Path mac = installation.resolve("Contents/Eclipse");
        return Files.isDirectory(mac) ? mac : installation;
    }

    private void run(Path home, String stage, String application, List<String> arguments, Duration timeout) throws Exception {
        run(home, stage, application, arguments, timeout, Set.of(0));
    }

    private int run(Path home, String stage, String application, List<String> arguments, Duration timeout,
            Set<Integer> expectedExitCodes) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("mac")) command.add("-XstartOnFirstThread");
        Path launcher;
        try (var files = Files.list(home.resolve("plugins"))) {
            List<Path> candidates = files.filter(path -> fileName(path).startsWith("org.eclipse.equinox.launcher_") && path.toString().endsWith(".jar")).toList();
            require(candidates.size() == 1, "Ambiguous Eclipse launcher");
            launcher = candidates.getFirst();
        }
        command.addAll(List.of("-Declipse.p2.mirrors=false", "-jar", launcher.toString(), "-nosplash", "-consoleLog", "-clean",
                "-install", home.toString(), "-configuration", home.resolve("configuration").toString(),
                "-data", work.resolve("workspaces/" + stage).toString(), "-application", application));
        command.addAll(arguments);
        Path log = evidence.resolve(stage + ".log");
        Files.write(evidence.resolve(stage + ".command.txt"), command);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            require(process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS), "Timed out: " + stage + "; see " + log);
            require(expectedExitCodes.contains(process.exitValue()), "Failed: " + stage + "; see " + log);
            return process.exitValue();
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private static Map<String, String> featureVersions(URI repository) throws Exception {
        var connection = repository.resolve("content.jar").toURL().openConnection();
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        try (InputStream input = connection.getInputStream(); ZipInputStream archive = new ZipInputStream(input)) {
            for (var entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
                if (!entry.getName().equals("content.xml")) continue;
                Map<String, String> result = new TreeMap<>();
                for (Element units : children(xml(archive), "units")) {
                    for (Element unit : children(units, "unit")) {
                        String id = unit.getAttribute("id");
                        if (AggregateInstallationEvidence.isSandboxFeature(id)) {
                            require(result.putIfAbsent(id, unit.getAttribute("version")) == null, "Ambiguous repository IU " + id);
                        }
                    }
                }
                return result;
            }
        }
        throw new IOException("Missing content.xml in " + repository);
    }

    private static Element read(Path file) throws Exception {
        try (InputStream input = Files.newInputStream(file)) { return xml(input); }
    }
}
