/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.distribution;

import static org.sandbox.distribution.AggregateInstallationEvidence.children;
import static org.sandbox.distribution.AggregateInstallationEvidence.require;
import static org.sandbox.distribution.AggregateInstallationEvidence.xml;

import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
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

import javax.tools.ToolProvider;
import javax.xml.XMLConstants;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Element;

/** Additional end-to-end gate in the existing distribution Maven reactor. */
public final class AggregateInstallationVerifier {
    private static final String AGGREGATE = "sandbox_feature.feature.group";
    private static final String DIRECTOR = "org.eclipse.equinox.p2.director";
    private static final String PROBE = "org.sandbox.distribution.aggregate.probe";
    private static final String PREVIOUS_REPOSITORY = "https://carstenartur.github.io/sandbox/releases/1.3.4/";
    private final Path root;
    private final Path evidence;
    private final Path work;
    private final Path builder;
    private final URI repository;
    private final List<String> baseRepositories = new ArrayList<>();
    private final Map<String, String> records = new TreeMap<>();
    private Path probe;
    private Path inventory;

    private AggregateInstallationVerifier(Path root) throws IOException {
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
        for (Element repositories : children(read(root.resolve("pom.xml")), "repositories")) {
            for (Element item : children(repositories, "repository")) {
                if (text(item, "layout").equals("p2")) baseRepositories.add(text(item, "url"));
            }
        }
        require(!baseRepositories.isEmpty(), "Missing target repositories");
        inventory = inventory();
        probe = compileProbe();
        Path fresh = installation("fresh");
        provisionStock(fresh, "fresh-stock");
        Map<String, String> stock = hostDigests(fresh);
        provision(fresh, "fresh-aggregate", List.of(repository.toString()), List.of(AGGREGATE + '/' + current.get(AGGREGATE)), List.of());
        verifyStage(fresh, "fresh", current, stock);

        String next = nextFixtureVersion(current.get(AGGREGATE));
        URI nextRepository = publishNextAggregate(current.get(AGGREGATE), next);
        provision(fresh, "aggregate-update", List.of(nextRepository.toString(), repository.toString()),
                List.of(AGGREGATE + '/' + next), List.of(AGGREGATE + '/' + current.get(AGGREGATE)));
        Map<String, String> updated = new TreeMap<>(current);
        updated.put(AGGREGATE, next);
        verifyStage(fresh, "updated", updated, stock);

        URI previousRepository = URI.create(System.getProperty("sandbox.aggregate.previousRepository", PREVIOUS_REPOSITORY));
        Map<String, String> previous = featureVersions(previousRepository);
        require(!previous.isEmpty() && !previous.containsKey(AGGREGATE), "The legacy fixture must have individual features, not an aggregate");
        require(current.keySet().containsAll(previous.keySet()), "The aggregate loses previously published components");
        Path legacy = installation("legacy");
        provisionStock(legacy, "legacy-stock");
        Map<String, String> legacyStock = hostDigests(legacy);
        List<String> previousRoots = previous.entrySet().stream().map(entry -> entry.getKey() + '/' + entry.getValue()).toList();
        provision(legacy, "legacy-components", List.of(previousRepository.toString()), previousRoots, List.of());
        AggregateInstallationEvidence.requireFeatures(profile(legacy, "legacy-components"), previous, previous.keySet());
        // One planner/engine transaction: replace roots, never uninstall in a separate run.
        provision(legacy, "legacy-to-aggregate", List.of(repository.toString()),
                List.of(AGGREGATE + '/' + current.get(AGGREGATE)), previousRoots);
        verifyStage(legacy, "migrated", current, legacyStock);

        records.put("previousRepository", previousRepository.toString());
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
        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        arguments.addAll(List.of("-p2.os", os.contains("win") ? "win32" : os.contains("mac") ? "macosx" : "linux",
                "-p2.ws", os.contains("win") ? "win32" : os.contains("mac") ? "cocoa" : "gtk",
                "-p2.arch", System.getProperty("os.arch").equals("aarch64") ? "aarch64" : "x86_64"));
        if (!uninstall.isEmpty()) arguments.addAll(List.of("-uninstallIU", String.join(",", uninstall)));
        if (!install.isEmpty()) arguments.addAll(List.of("-installIU", String.join(",", install)));
        run(builder, stage, DIRECTOR, arguments, Duration.ofMinutes(15));
    }

    private void verifyStage(Path installation, String stage, Map<String, String> versions, Map<String, String> stock) throws Exception {
        Path home = home(installation);
        AggregateInstallationEvidence.requireFeatures(profile(installation, stage), versions, Set.of(AGGREGATE));
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
        Path configuration = home.resolve("configuration/org.eclipse.equinox.simpleconfigurator/bundles.info");
        byte[] original = Files.readAllBytes(configuration);
        Path result = evidence.resolve(stage + "-runtime.json");
        Files.deleteIfExists(result);
        try {
            Files.writeString(configuration, new String(original, StandardCharsets.UTF_8) + '\n'
                    + PROBE + ",1.0.0," + probe.toUri() + ",4,false\n");
            run(home, stage + "-runtime", PROBE + ".verify", List.of(inventory.toString(), result.toString()), Duration.ofMinutes(3));
            require(Files.isRegularFile(result), "Runtime probe produced no result");
        } finally {
            Files.write(configuration, original);
        }
        require(java.util.Arrays.equals(original, Files.readAllBytes(configuration)), "Probe changed installation configuration");
        cleanupSmoke(home, stage);
    }

    private void cleanupSmoke(Path home, String stage) throws Exception {
        Path project = work.resolve(stage + "-input/SmokeProject");
        Path source = project.resolve("src/smoke/Smoke.java");
        Files.createDirectories(source.getParent());
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
        Path report = evidence.resolve(stage + "-cleanup.json");
        run(home, stage + "-cleanup", "org.sandbox.jdt.core.JavaCleanup", List.of("--import-project", project.toString(),
                "--mode", "apply", "--report", report.toString(), "--config", config.toString(), source.toString()), Duration.ofMinutes(3));
        String result = Files.readString(report);
        for (String field : List.of("filesProcessed", "filesChanged", "errorCount")) {
            int expected = field.equals("errorCount") ? 0 : 1;
            require(java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*" + expected + "\\s*[,}]").matcher(result).find(), "Bad cleanup result: " + result);
        }
        require(!Files.readString(source).equals(before), "Cleanup did not change source");
        Path classes = work.resolve(stage + "-classes");
        Files.createDirectories(classes);
        require(ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "21", "-d", classes.toString(), source.toString()) == 0, "Cleaned source does not compile");
        Files.copy(source, evidence.resolve(stage + "-Smoke.java"), StandardCopyOption.REPLACE_EXISTING);
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
                    Files.createDirectories(output.getParent());
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
                    .max(Comparator.comparingLong(path -> Long.parseLong(path.getFileName().toString().split("\\.")[0])))
                    .orElseThrow(() -> new IOException("No p2 profile in " + registry));
        }
    }

    private Map<String, String> hostDigests(Path installation) throws Exception {
        Map<String, String> result = new TreeMap<>();
        Path home = home(installation);
        for (String id : List.of("org.eclipse.jdt.ui", "org.eclipse.ltk.core.refactoring", "org.eclipse.ltk.ui.refactoring")) {
            try (var files = Files.list(home.resolve("plugins"))) {
                List<Path> matches = files.filter(path -> path.getFileName().toString().startsWith(id + '_')).toList();
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
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("mac")) command.add("-XstartOnFirstThread");
        Path launcher;
        try (var files = Files.list(home.resolve("plugins"))) {
            List<Path> candidates = files.filter(path -> path.getFileName().toString().startsWith("org.eclipse.equinox.launcher_") && path.toString().endsWith(".jar")).toList();
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
            require(process.exitValue() == 0, "Failed: " + stage + "; see " + log);
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

    private static String text(Element parent, String name) {
        return children(parent, name).stream().findFirst().map(element -> element.getTextContent().strip()).orElse("");
    }
}
