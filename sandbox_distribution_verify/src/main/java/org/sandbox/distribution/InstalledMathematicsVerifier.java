/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.distribution;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

import javax.tools.ToolProvider;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Native installed-product gate consuming the successful SWT suite's disposable fixture.
 * All behavioral assertions and standalone compilation execute in Java. */
public final class InstalledMathematicsVerifier {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PROJECT = "MathematicsHeadlessQualification";
    private final Path root;
    private final Path evidence;
    private final List<Map<String, Object>> stages = new ArrayList<>();

    private InstalledMathematicsVerifier(Path root) {
        this.root = root.toAbsolutePath().normalize();
        evidence = this.root.resolve("target/distribution-verification/mathematics");
    }

    public static void main(String[] arguments) throws Exception {
        require(arguments.length == 1, "Expected the Sandbox repository root");
        new InstalledMathematicsVerifier(Path.of(arguments[0])).verify();
    }

    private void verify() throws Exception {
        Files.createDirectories(evidence);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema", "sandbox.installed-mathematics.v1");
        result.put("started", Instant.now().toString());
        result.put("result", "RUNNING");
        result.put("stages", stages);
        Files.writeString(evidence.resolve("qualification.json"), JSON.toJson(result) + '\n');
        try {
            Path receiptFile = Path.of(System.getProperty("sandbox.math.probeReceipt",
                    root.resolve("sandbox_math_cleanup_test/target/headless-probe/receipt.json").toString()));
            Receipt fixture = readReceipt(receiptFile);
            Path installation = Path.of(System.getProperty("sandbox.math.installedProduct",
                    root.resolve("target/distribution-verification/fresh-install").toString()));
            Path home = installedHome(installation);
            Path launcher = launcher(home);
            String sdk = installedSdkHash(home);
            Map<String, String> bundleIdentity = installedMathBundleIdentity(home);
            Files.writeString(evidence.resolve("installed-math-bundle.json"), JSON.toJson(bundleIdentity) + '\n');
            result.put("installedMathBundle", bundleIdentity);
            result.put("scope", "Installed analysis, apply, standalone compilation and idempotence using the exact adapter and SDK qualified by the successful SWT suite");
            require(sdk.equals(fixture.sdkSha256()), "Installed SDK differs from the successful workbench fixture SDK");
            require("sha256(jar bytes)".equals(bundleIdentity.get("hashFormat")), "Installed adapter must be a packaged JAR");
            require(bundleIdentity.get("sha256").equals(fixture.adapterBundleSha256()),
                    "Installed adapter differs from the successful workbench fixture adapter");
            result.put("fixtureReceiptSha256", hash(Files.readAllBytes(receiptFile)));
            result.put("installedHome", home.toString());
            result.put("launcherSha256", hash(Files.readAllBytes(launcher)));
            result.put("sdkSha256", sdk);
            result.put("workspace", fixture.workspace().toString());
            result.put("project", fixture.project());
            Files.copy(receiptFile, evidence.resolve("workbench-fixture-receipt.json"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(fixture.configuration(), evidence.resolve("mathematics.properties"), StandardCopyOption.REPLACE_EXISTING);
            String before = Files.readString(fixture.source());
            Files.writeString(evidence.resolve("source-before.java"), before);
            var projectFiles = new LinkedHashMap<Path, String>();
            for (String file : List.of(".project", ".classpath", ".settings/org.eclipse.jdt.core.prefs")) {
                Path path = fixture.workspace().resolve(fixture.project()).resolve(file);
                require(Files.isRegularFile(path), "Missing synthetic project metadata: " + path);
                projectFiles.put(path, hash(Files.readAllBytes(path)));
            }
            JsonObject analysis = launch(launcher, home, fixture, "analysis", false);
            String preview = requireReport(analysis, fixture, before, Files.readString(fixture.source()), "analysis", null);
            JsonObject apply = launch(launcher, home, fixture, "apply", true);
            String after = Files.readString(fixture.source());
            requireReport(apply, fixture, before, after, "apply", preview);
            Files.writeString(evidence.resolve("source-after.java"), after);
            compareCompiledSources(before, after, evidence.resolve("standalone-java"));
            JsonObject idempotence = launch(launcher, home, fixture, "idempotence", false);
            requireReport(idempotence, fixture, after, Files.readString(fixture.source()), "idempotence", null);
            requireSameEnvironment(analysis, apply, idempotence);
            require(fixture.configurationSha256().equals(hash(Files.readAllBytes(fixture.configuration()))), "Probe changed its configuration");
            for (var entry : projectFiles.entrySet()) {
                require(entry.getValue().equals(hash(Files.readAllBytes(entry.getKey()))), "Probe changed project metadata: " + entry.getKey());
            }
            result.put("sourceBeforeSha256", hash(before.getBytes(StandardCharsets.UTF_8)));
            result.put("sourceAfterSha256", hash(after.getBytes(StandardCharsets.UTF_8)));
            result.put("targetJava", 17);
            result.put("standaloneCompiledRuntime", "bootstrap classes only; no SDK runtime");
            result.put("result", "PASS");
        } catch (Exception failure) {
            result.put("result", "FAIL");
            result.put("failure", failure.toString());
            throw failure;
        } finally {
            result.put("finished", Instant.now().toString());
            Files.writeString(evidence.resolve("qualification.json"), JSON.toJson(result) + '\n');
        }
    }

    private JsonObject launch(Path launcher, Path home, Receipt fixture, String stage, boolean apply) throws Exception {
        Path report = evidence.resolve(stage + ".json");
        Files.deleteIfExists(report);
        var command = new ArrayList<>(List.of(launcher.toString(), "-nosplash", "-consoleLog", "-data", fixture.workspace().toString(),
                "-application", "sandbox_math_cleanup.analysis", "--project", fixture.project(),
                "--config", fixture.configuration().toString(), "--report", report.toString()));
        if (apply) command.add("--apply");
        Files.writeString(evidence.resolve(stage + ".command.json"), JSON.toJson(command) + '\n');
        Path log = evidence.resolve(stage + ".log");
        ProcessBuilder builder = new ProcessBuilder(command).directory(home.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        List<String> removed = removeDisplayEnvironment(builder.environment());
        Files.writeString(evidence.resolve(stage + ".environment.json"), JSON.toJson(Map.of(
                "displayVariablesUnset", List.of("DISPLAY", "WAYLAND_DISPLAY"), "removedVariables", removed)) + '\n');
        Process process = builder.start();
        try {
            require(process.waitFor(180, TimeUnit.SECONDS), "Installed mathematics application timed out: " + log);
            stages.add(Map.of("stage", stage, "exitCode", process.exitValue()));
            require(process.exitValue() == 0, "Installed mathematics application failed: " + log);
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
        require(Files.isRegularFile(report), "Installed application did not write its report: " + report);
        return object(report);
    }

    static List<String> removeDisplayEnvironment(Map<String, String> environment) {
        List<String> removed = new ArrayList<>();
        for (String name : List.of("DISPLAY", "WAYLAND_DISPLAY"))
            if (environment.remove(name) != null) removed.add(name);
        return List.copyOf(removed);
    }

    record Receipt(String project, Path workspace, Path source, Path configuration, String configurationSha256,
            String sdkSha256, String adapterBundleSha256, Map<String, String> options) {
        Receipt { options = Map.copyOf(options); }
    }

    static Receipt readReceipt(Path receiptFile) throws Exception {
        JsonObject receipt = object(receiptFile);
        require(integer(receipt, "schemaVersion") == 1, "Unknown workbench receipt schema");
        require("PASS".equals(text(receipt, "status")) && integer(receipt, "successfulTests") == 9,
                "The complete nine-test workbench suite must pass before retaining its fixture");
        require("sha256-jar-bytes".equals(text(receipt, "adapterBundleHashFormat")),
                "Workbench receipt must qualify a packaged adapter JAR");
        require(PROJECT.equals(text(receipt, "project")), "Only the disposable successful-SWT project can be modified");
        require(integer(receipt, "targetJava") == 17, "Fixture must retain Java 17");
        Path workspace = Path.of(text(receipt, "workspace")).toRealPath();
        Path source = Path.of(text(receipt, "source")).toRealPath();
        Path configuration = Path.of(text(receipt, "configuration")).toRealPath();
        require(Files.isDirectory(workspace.resolve(".metadata")), "Missing saved Eclipse workspace");
        require(source.startsWith(workspace.resolve(PROJECT).toRealPath()), "Fixture source is outside the disposable project");
        require(text(receipt, "sourceSha256").equals(hash(Files.readAllBytes(source))), "Fixture source differs from successful SWT receipt");
        String configHash = text(receipt, "configurationSha256");
        require(configHash.equals(hash(Files.readAllBytes(configuration))), "Fixture configuration differs from successful SWT receipt");
        String sdkHash = text(receipt, "sdkSha256");
        require(sdkHash.matches("[a-f0-9]{64}"), "Receipt SDK hash is missing or malformed");
        String adapterHash = text(receipt, "adapterBundleSha256");
        require(adapterHash.matches("[a-f0-9]{64}"), "Receipt adapter hash is missing or malformed");
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(configuration)) { properties.load(reader); }
        Map<String, String> options = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) options.put(name, properties.getProperty(name));
        return new Receipt(PROJECT, workspace, source, configuration, configHash, sdkHash, adapterHash, options);
    }

    static String requireReport(JsonObject report, Receipt fixture, String before, String actualSource,
            String stage, String reviewedPreview) throws Exception {
        require(integer(report, "schemaVersion") == 1, "Unknown mathematics report schema");
        require(text(report, "project").equals(fixture.project()), "Report names a different project");
        require(JSON.toJsonTree(fixture.options()).equals(report.get("configProperties")), "Report configuration differs");
        require(JSON.toJsonTree(fixture.options()).equals(report.get("requestedOptions")), "Report did not use the exact requested options");
        require(fixture.sdkSha256().equals(text(report, "sdkSha256")), "Report SDK differs from workbench fixture");
        require(fixture.adapterBundleSha256().equals(text(report, "adapterBundleSha256")), "Report adapter differs from workbench fixture");
        boolean apply = stage.equals("apply");
        boolean idempotence = stage.equals("idempotence");
        require(text(report, "mode").equals(apply ? "apply" : "analysis"), "Report mode differs");
        require(report.has("files") && report.get("files").isJsonArray() && report.getAsJsonArray("files").size() == 1,
                "Expected exactly one disposable source file");
        JsonObject file = report.getAsJsonArray("files").get(0).getAsJsonObject();
        String sourcePath = "/" + fixture.workspace().relativize(fixture.source()).toString().replace('\\', '/');
        require(text(file, "path").equals(sourcePath), "Report source path differs");
        require(integer(file, "targetJava") == 17, "Report changed the target Java version");
        require(JSON.toJsonTree(fixture.options()).equals(file.get("options")), "File options differ from requested options");
        require(text(file, "original").equals(before), "Report source snapshot differs");
        require(text(file, "sourceSha256").equals(hash(before.getBytes(StandardCharsets.UTF_8))), "Report source hash differs");
        String after = text(file, "replacement");
        require(text(file, "afterSha256").equals(hash(after.getBytes(StandardCharsets.UTF_8))), "Report replacement hash differs");
        require(flag(file, "applied") == apply, "Report applied status differs");
        require(text(file, "environmentDigest").matches("[a-f0-9]{64}"), "Missing binding environment hash");
        require(file.has("compilerOptions") && file.get("compilerOptions").isJsonObject(), "Missing compiler options");
        JsonObject compiler = file.getAsJsonObject("compilerOptions");
        for (String option : List.of("org.eclipse.jdt.core.compiler.source", "org.eclipse.jdt.core.compiler.compliance",
                "org.eclipse.jdt.core.compiler.codegen.targetPlatform"))
            require("17".equals(text(compiler, option)), "Fixture compiler settings must retain Java 17");
        JsonArray replacements = array(file, "changes");
        JsonArray evidence = array(file, "evidence");
        if (idempotence) {
            require(before.equals(actualSource) && before.equals(after), "Idempotence analysis changed or proposed changing source");
            require(replacements.isEmpty() && evidence.isEmpty(), "Idempotence report still proposes edits or verified regions");
        } else {
            require(!before.equals(after), "No mathematics improvement proposed");
            requireVerifiedEdits(before, after, replacements, evidence);
            if (apply) require(after.equals(actualSource) && after.equals(reviewedPreview), "Apply differs from the reviewed preview");
            else require(before.equals(actualSource), "Analysis-only changed source");
        }
        return after;
    }

    static void requireSameEnvironment(JsonObject... reports) throws IOException {
        require(reports.length >= 2, "At least two application reports are required");
        JsonObject reference = reports[0].getAsJsonArray("files").get(0).getAsJsonObject();
        for (JsonObject report : reports) {
            JsonObject file = report.getAsJsonArray("files").get(0).getAsJsonObject();
            for (String key : List.of("compilerOptions", "options", "environmentDigest"))
                require(Objects.equals(reference.get(key), file.get(key)), "Application binding environment changed: " + key);
        }
    }

    private record Edit(int offset, int length, String replacement) { }

    private static void requireVerifiedEdits(String before, String after, JsonArray replacements, JsonArray evidence) throws IOException {
        require(!replacements.isEmpty() && replacements.size() == evidence.size(), "Missing edit or independent verification evidence");
        Map<Integer, Edit> edits = new LinkedHashMap<>();
        for (var element : replacements) {
            require(element.isJsonObject(), "Invalid proposed edit");
            JsonObject replacement = element.getAsJsonObject();
            Edit edit = new Edit(integer(replacement, "offset"), integer(replacement, "length"), text(replacement, "replacement"));
            require(edit.offset() >= 0 && edit.length() > 0 && edit.offset() <= before.length() - edit.length(), "Edit is outside the source");
            require(edits.put(edit.offset(), edit) == null, "Duplicate edit location");
        }
        List<Edit> ordered = edits.values().stream().sorted(Comparator.comparingInt(Edit::offset).reversed()).toList();
        StringBuilder preview = new StringBuilder(before);
        int nextStart = before.length();
        for (Edit edit : ordered) {
            require(edit.offset() + edit.length() <= nextStart, "Overlapping proposed edits");
            preview.replace(edit.offset(), edit.offset() + edit.length(), edit.replacement());
            nextStart = edit.offset();
        }
        require(preview.toString().equals(after), "Recorded edits do not produce the report preview");
        for (var element : evidence) {
            require(element.isJsonObject(), "Invalid verified region");
            JsonObject region = element.getAsJsonObject();
            Edit edit = edits.remove(integer(region, "offset"));
            if (edit == null) throw new IOException("Proof region does not match an edit");
            require(integer(region, "length") == edit.length(), "Proof region does not match an edit");
            require(text(region, "original").equals(before.substring(edit.offset(), edit.offset() + edit.length())), "Proof region source differs");
            require(text(region, "replacement").equals(edit.replacement()), "Proof region replacement differs");
            require(region.has("cost") && region.get("cost").isJsonObject(), "Missing cost assessment");
            require(region.has("proof") && region.get("proof").isJsonObject(), "Missing proof receipt");
            JsonObject proof = region.getAsJsonObject("proof");
            require(text(proof, "schemaRevision").equals("regelsuche.optimization-evidence/v1"), "Unknown proof schema");
            require(text(proof, "semanticsRevision").equals("java25-numeric/v1"), "Unknown numerical semantics");
            text(proof, "checkerRevision"); text(proof, "generatorRevision");
            require(text(proof, "safetyProfile").equals("PRESERVE_JAVA"), "Fixture changed its numerical contract");
            for (String name : List.of("sourceHash", "candidateHash", "traceHash", "assumptionsHash"))
                require(text(proof, name).matches("[a-f0-9]{64}"), "Missing proof binding: " + name);
            JsonArray methods = array(proof, "proofMethods");
            require(!methods.isEmpty(), "Missing independent proof methods");
            for (var method : methods)
                require(method.isJsonPrimitive() && method.getAsJsonPrimitive().isString() && !method.getAsString().isBlank(), "Invalid proof method");
        }
    }

    /** Compiles only the known synthetic fixture and compares boundary cases in isolated classloaders. */
    static void compareCompiledSources(String before, String after, Path output) throws Exception {
        Path original = compile(before, output.resolve("original"));
        Path replacement = compile(after, output.resolve("replacement"));
        try (var leftLoader = new URLClassLoader(new URL[] {original.toUri().toURL()}, null);
                var rightLoader = new URLClassLoader(new URL[] {replacement.toUri().toURL()}, null)) {
            Class<?> left = leftLoader.loadClass("example.Calculation");
            Class<?> right = rightLoader.loadClass("example.Calculation");
            Object leftInstance = left.getConstructor().newInstance();
            Object rightInstance = right.getConstructor().newInstance();
            var leftMethod = left.getMethod("compute", int.class, int.class);
            var rightMethod = right.getMethod("compute", int.class, int.class);
            int[] inputs = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -65_537, -1, 0, 1, 65_537, Integer.MAX_VALUE};
            for (int a : inputs) for (int b : inputs)
                require(Objects.equals(leftMethod.invoke(leftInstance, a, b), rightMethod.invoke(rightInstance, a, b)),
                        "Installed cleanup changed the synthetic program result");
        }
    }

    private static Path compile(String source, Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("Calculation.java");
        Files.writeString(file, source);
        Path classes = Files.createTempDirectory(directory, "classes-");
        Path emptyClasspath = Files.createDirectories(directory.resolve("empty-classpath"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        require(compiler != null, "Installed qualification requires a JDK 25 compiler");
        try (var log = Files.newOutputStream(directory.resolve("javac.log"))) {
            require(compiler.run(null, log, log, "--release", "17", "-classpath", emptyClasspath.toString(),
                    "-d", classes.toString(), file.toString()) == 0, "Standalone Java 17 compilation failed: " + file);
        }
        return classes;
    }

    private static Path installedHome(Path installation) throws IOException {
        require(Files.isDirectory(installation), "Fresh p2 installation must pass before this gate: " + installation);
        try (var files = Files.walk(installation, 4)) {
            List<Path> homes = files.filter(Files::isDirectory).filter(path -> path.endsWith("plugins"))
                    .map(Path::getParent).filter(Objects::nonNull)
                    .filter(path -> Files.isRegularFile(path.resolve("configuration/config.ini")))
                    .filter(path -> launcherCandidates(path).stream().anyMatch(Files::isRegularFile)).toList();
            require(homes.size() == 1, "Expected one installed Eclipse home: " + homes);
            return homes.getFirst();
        }
    }

    static Path launcher(Path home) throws IOException {
        for (Path path : launcherCandidates(home))
            if (Files.isRegularFile(path)) return path;
        throw new IOException("Missing installed native launcher: " + home);
    }

    private static List<Path> launcherCandidates(Path home) {
        return List.of(home.resolve("eclipse"), home.resolve("eclipse.exe"), home.resolve("../MacOS/eclipse").normalize(),
                home.resolve("Eclipse.app/Contents/MacOS/eclipse"));
    }

    private record InstalledBundle(Path path, Manifest manifest) { }

    private static InstalledBundle installedMathBundle(Path home) throws IOException {
        try (var plugins = Files.list(home.resolve("plugins"))) {
            List<InstalledBundle> bundles = new ArrayList<>();
            for (Path path : plugins.toList()) {
                Manifest manifest;
                if (Files.isDirectory(path)) {
                    Path metadata = path.resolve("META-INF/MANIFEST.MF");
                    if (!Files.isRegularFile(metadata)) continue;
                    try (var input = Files.newInputStream(metadata)) { manifest = new Manifest(input); }
                } else {
                    Path filename = path.getFileName();
                    if (filename == null || !filename.toString().endsWith(".jar")) continue;
                    try (var jar = new JarFile(path.toFile())) { manifest = jar.getManifest(); }
                }
                String id = manifest == null ? null : manifest.getMainAttributes().getValue("Bundle-SymbolicName");
                if (id != null && id.split(";", 2)[0].trim().equals("sandbox_math_cleanup")) bundles.add(new InstalledBundle(path, manifest));
            }
            require(bundles.size() == 1, "Expected exactly one installed mathematics bundle");
            return bundles.getFirst();
        }
    }

    static String installedSdkHash(Path home) throws Exception {
        Path bundle = installedMathBundle(home).path();
        if (Files.isDirectory(bundle)) return hash(Files.readAllBytes(bundle.resolve("lib/regelsuche-optimization-sdk.jar")));
        try (var zip = new ZipFile(bundle.toFile())) {
            var entry = zip.getEntry("lib/regelsuche-optimization-sdk.jar");
            require(entry != null, "Installed bundle does not contain its SDK");
            try (var stream = zip.getInputStream(entry)) { return hash(stream.readAllBytes()); }
        }
    }

    static Map<String, String> installedMathBundleIdentity(Path home) throws Exception {
        InstalledBundle bundle = installedMathBundle(home);
        String version = bundle.manifest().getMainAttributes().getValue("Bundle-Version");
        require(version != null && !version.isBlank(), "Installed mathematics bundle has no version");
        String sha;
        String format;
        if (Files.isDirectory(bundle.path())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var paths = Files.walk(bundle.path())) {
                List<Path> files = paths.filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(path -> bundle.path().relativize(path).toString().replace('\\', '/'))).toList();
                for (Path file : files) {
                    digest.update(bundle.path().relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                    digest.update(hash(Files.readAllBytes(file)).getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) '\n');
                }
            }
            sha = HexFormat.of().formatHex(digest.digest());
            format = "sha256(sorted UTF-8 relative path + NUL + content sha256 hex + LF)";
        } else {
            sha = hash(Files.readAllBytes(bundle.path()));
            format = "sha256(jar bytes)";
        }
        return Map.of("path", bundle.path().toRealPath().toString(), "symbolicName", "sandbox_math_cleanup",
                "version", version, "sha256", sha, "hashFormat", format);
    }

    private static JsonObject object(Path path) throws IOException {
        try { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
        catch (RuntimeException malformed) { throw new IOException("Invalid JSON evidence: " + path, malformed); }
    }

    private static String text(JsonObject object, String name) throws IOException {
        require(object.has(name) && object.get(name).isJsonPrimitive() && object.get(name).getAsJsonPrimitive().isString(), "Missing text: " + name);
        String value = object.get(name).getAsString();
        require(!value.isBlank(), "Empty text: " + name);
        return value;
    }

    private static int integer(JsonObject object, String name) throws IOException {
        require(object.has(name) && object.get(name).isJsonPrimitive() && object.get(name).getAsJsonPrimitive().isNumber(), "Missing number: " + name);
        try { return object.get(name).getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException malformed) { throw new IOException("Expected integer: " + name, malformed); }
    }

    private static JsonArray array(JsonObject object, String name) throws IOException {
        require(object.has(name) && object.get(name).isJsonArray(), "Missing array: " + name);
        return object.getAsJsonArray(name);
    }

    private static boolean flag(JsonObject object, String name) throws IOException {
        require(object.has(name) && object.get(name).isJsonPrimitive() && object.get(name).getAsJsonPrimitive().isBoolean(), "Missing boolean: " + name);
        return object.get(name).getAsBoolean();
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
