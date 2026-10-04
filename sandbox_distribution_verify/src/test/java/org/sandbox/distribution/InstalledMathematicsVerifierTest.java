/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.distribution;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class InstalledMathematicsVerifierTest {
    @TempDir Path temporary;
    private static final String BEFORE = "package example; public class Calculation { public int compute(int a,int b) { return (a*b+1)-1; }}";
    private static final String AFTER = "package example; public class Calculation { public int compute(int a,int b) { return a*b; }}";
    private static final Map<String, String> OPTIONS = Map.of("cleanup.mathematics", "true",
            "cleanup.mathematics.exclusions", "", "cleanup.mathematics.numericKinds", "INT",
            "cleanup.mathematics.safetyProfile", "PRESERVE_JAVA", "cleanup.mathematics.goal", "READABILITY",
            "cleanup.mathematics.workBudget", "100000", "cleanup.mathematics.maxStates", "2000",
            "cleanup.mathematics.checkedOptIn", "false", "cleanup.mathematics.underflowChecks", "false");

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void selectsTheExactMathematicsBundleAlongsideItsHelpBundle(boolean packed) throws Exception {
        Path home = temporary.resolve("installation");
        Path plugins = Files.createDirectories(home.resolve("plugins"));
        bundle(plugins, "sandbox_math_cleanup", "sdk-fixture", packed);
        bundle(plugins, "sandbox_math_cleanup_help", null, packed);
        assertEquals(hash("sdk-fixture"), InstalledMathematicsVerifier.installedSdkHash(home));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void installedBundleIdentityBindsAdapterBytesIndependentlyOfTheSdk(boolean packed) throws Exception {
        Path home = temporary.resolve("installation");
        Path plugins = Files.createDirectories(home.resolve("plugins"));
        bundle(plugins, "sandbox_math_cleanup", "sdk-fixture", packed, "adapter-original");
        var before = InstalledMathematicsVerifier.installedMathBundleIdentity(home);
        String sdk = InstalledMathematicsVerifier.installedSdkHash(home);
        bundle(plugins, "sandbox_math_cleanup", "sdk-fixture", packed, "adapter-changed");
        var after = InstalledMathematicsVerifier.installedMathBundleIdentity(home);
        assertEquals(sdk, InstalledMathematicsVerifier.installedSdkHash(home));
        assertNotEquals(before.get("sha256"), after.get("sha256"));
        assertEquals("sandbox_math_cleanup", after.get("symbolicName"));
        assertEquals("1.3.6", after.get("version"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "eclipse", "eclipse.exe", "../MacOS/eclipse", "Eclipse.app/Contents/MacOS/eclipse" })
    void supportsAllNativeLauncherLayouts(String layout) throws Exception {
        Path home = Files.createDirectories(temporary.resolve("installation"));
        Path executable = home.resolve(layout).normalize();
        Files.createDirectories(executable.getParent());
        Files.writeString(executable, "");
        assertEquals(executable, InstalledMathematicsVerifier.launcher(home).normalize());
    }

    @Test
    void nativeApplicationEnvironmentRemovesDisplayConnectionsOnly() {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("DISPLAY", ":121");
        environment.put("WAYLAND_DISPLAY", "wayland-0");
        environment.put("PATH", "/usr/bin");
        assertEquals(List.of("DISPLAY", "WAYLAND_DISPLAY"),
                InstalledMathematicsVerifier.removeDisplayEnvironment(environment));
        assertEquals(Map.of("PATH", "/usr/bin"), environment);
    }

    @Test
    void requiresTheUnchangedDisposableReceipt() throws Exception {
        Path path = receipt();
        var fixture = InstalledMathematicsVerifier.readReceipt(path);
        assertEquals("MathematicsHeadlessQualification", fixture.project());
        Files.writeString(fixture.source(), BEFORE + " ");
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.readReceipt(path));
    }

    @Test
    void rejectsADifferentProjectOrChangedConfiguration() throws Exception {
        Path path = receipt();
        JsonObject invalid = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        invalid.addProperty("project", "UserProject");
        Files.writeString(path, invalid.toString());
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.readReceipt(path));
        Path freshPath = receipt();
        var fixture = InstalledMathematicsVerifier.readReceipt(freshPath);
        Files.writeString(fixture.configuration(), "cleanup.mathematics=false\n");
        Path changedPath = freshPath;
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.readReceipt(changedPath));
    }

    @Test
    void analysisOnlyRequiresNoSourceMutation() throws Exception {
        var fixture = InstalledMathematicsVerifier.readReceipt(receipt());
        JsonObject report = report(fixture, "analysis", BEFORE, AFTER, true, false);
        assertEquals(AFTER, InstalledMathematicsVerifier.requireReport(report, fixture, BEFORE, BEFORE, "analysis", null));
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.requireReport(report, fixture, BEFORE, AFTER, "analysis", null));
        report.getAsJsonArray("files").get(0).getAsJsonObject().addProperty("applied", true);
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.requireReport(report, fixture, BEFORE, BEFORE, "analysis", null));
    }

    @Test
    void rejectsMissingProofOrDifferentExecutable() throws Exception {
        var fixture = InstalledMathematicsVerifier.readReceipt(receipt());
        JsonObject missing = report(fixture, "analysis", BEFORE, AFTER, true, false);
        proof(missing).remove("candidateHash");
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.requireReport(missing, fixture, BEFORE, BEFORE, "analysis", null));
        JsonObject failed = report(fixture, "analysis", BEFORE, AFTER, true, false);
        failed.addProperty("adapterBundleSha256", "f".repeat(64));
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.requireReport(failed, fixture, BEFORE, BEFORE, "analysis", null));
    }

    @Test
    void requiresExactlyTheRequestedOptionsIncludingEmptyValues() throws Exception {
        var fixture = InstalledMathematicsVerifier.readReceipt(receipt());
        JsonObject report = report(fixture, "analysis", BEFORE, AFTER, true, false);
        report.getAsJsonObject("requestedOptions").addProperty("cleanup.mathematics", "false");
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.requireReport(report, fixture, BEFORE, BEFORE, "analysis", null));
        report.remove("requestedOptions");
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.requireReport(report, fixture, BEFORE, BEFORE, "analysis", null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "original", "replacement", "offset", "cost" })
    void bindsEachEvidenceRegionToAnActualSourceEdit(String field) throws Exception {
        var fixture = InstalledMathematicsVerifier.readReceipt(receipt());
        JsonObject report = report(fixture, "analysis", BEFORE, AFTER, true, false);
        JsonObject region = report.getAsJsonArray("files").get(0).getAsJsonObject().getAsJsonArray("evidence").get(0).getAsJsonObject();
        if (field.equals("offset")) region.addProperty(field, 1);
        else if (field.equals("cost")) region.remove(field);
        else region.addProperty(field, "different source");
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.requireReport(report, fixture, BEFORE, BEFORE, "analysis", null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "schemaRevision", "semanticsRevision", "assumptionsHash", "proofMethods" })
    void requiresTheQualifiedProofContract(String field) throws Exception {
        var fixture = InstalledMathematicsVerifier.readReceipt(receipt());
        JsonObject report = report(fixture, "analysis", BEFORE, AFTER, true, false);
        if (field.equals("proofMethods")) proof(report).add(field, new Gson().toJsonTree(List.of()));
        else proof(report).addProperty(field, "unknown");
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.requireReport(report, fixture, BEFORE, BEFORE, "analysis", null));
    }

    @Test
    void recordedEditsMustReconstructThePreview() throws Exception {
        var fixture = InstalledMathematicsVerifier.readReceipt(receipt());
        JsonObject report = report(fixture, "analysis", BEFORE, AFTER, true, false);
        report.getAsJsonArray("files").get(0).getAsJsonObject().addProperty("replacement", AFTER + " ");
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.requireReport(report, fixture, BEFORE, BEFORE, "analysis", null));
    }

    @Test
    void applyMustMatchTheExactReviewedPreview() throws Exception {
        var fixture = InstalledMathematicsVerifier.readReceipt(receipt());
        JsonObject report = report(fixture, "apply", BEFORE, AFTER, true, true);
        assertEquals(AFTER, InstalledMathematicsVerifier.requireReport(report, fixture, BEFORE, AFTER, "apply", AFTER));
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.requireReport(report, fixture, BEFORE, BEFORE, "apply", AFTER));
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.requireReport(report, fixture, BEFORE, AFTER, "apply", AFTER + " "));
    }

    @Test
    void idempotenceRequiresAnUnchangedSecondAnalysis() throws Exception {
        var fixture = InstalledMathematicsVerifier.readReceipt(receipt());
        JsonObject report = report(fixture, "analysis", AFTER, AFTER, false, false);
        assertEquals(AFTER, InstalledMathematicsVerifier.requireReport(report, fixture, AFTER, AFTER, "idempotence", null));
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.requireReport(report, fixture, AFTER, AFTER + " ", "idempotence", null));
        report.getAsJsonArray("files").get(0).getAsJsonObject().addProperty("replacement", AFTER + " ");
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.requireReport(report, fixture, AFTER, AFTER, "idempotence", null));
    }

    @Test
    void idempotenceRejectsHiddenProposals() throws Exception {
        var fixture = InstalledMathematicsVerifier.readReceipt(receipt());
        JsonObject report = report(fixture, "analysis", AFTER, AFTER, true, false);
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.requireReport(report, fixture, AFTER, AFTER, "idempotence", null));
    }

    @Test
    void standaloneCompilationChecksActualFixtureSemantics() throws Exception {
        InstalledMathematicsVerifier.compareCompiledSources(BEFORE, AFTER, temporary.resolve("valid"));
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.compareCompiledSources(
                BEFORE, AFTER.replace("a*b", "a+b"), temporary.resolve("invalid")));
    }

    @Test
    void repeatedCompilationCannotReuseStaleReplacementBytecode() throws Exception {
        Path output = temporary.resolve("same-output");
        InstalledMathematicsVerifier.compareCompiledSources(BEFORE, AFTER, output);
        assertThrows(ClassNotFoundException.class, () -> InstalledMathematicsVerifier.compareCompiledSources(
                BEFORE, "package example; // no class generated", output));
    }

    @Test
    void workflowActuallyInvokesTheInstalledGate() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("sandbox_distribution_verify/pom.xml"))) root = root.getParent();
        assertNotNull(root, "Test must run inside the Sandbox checkout");
        String workflow = Files.readString(root.resolve(".github/workflows/distribution-smoke.yml"));
        String pom = Files.readString(root.resolve("sandbox_distribution_verify/pom.xml"));
        assertTrue(workflow.contains("sandbox.math.retainHeadlessProbe=true"));
        assertTrue(workflow.contains("exec:java@verify-installed-mathematics"));
        assertTrue(pom.contains("verify-installed-mathematics"));
        assertTrue(pom.contains("org.sandbox.distribution.InstalledMathematicsVerifier"));
    }

    private Path receipt() throws Exception {
        Path workspace = Files.createDirectories(temporary.resolve("workspace"));
        Files.createDirectories(workspace.resolve(".metadata"));
        Path source = workspace.resolve("MathematicsHeadlessQualification/src/example/Calculation.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, BEFORE);
        Path configuration = temporary.resolve("mathematics.properties");
        String config = OPTIONS.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + '=' + entry.getValue() + '\n').reduce("", String::concat);
        Files.writeString(configuration, config);
        Path receipt = temporary.resolve("receipt.json");
        JsonObject data = new Gson().toJsonTree(Map.of(
                "project", "MathematicsHeadlessQualification", "workspace", workspace.toString(),
                "targetJava", 17, "source", source.toString(), "sourceSha256", hash(BEFORE),
                "configuration", configuration.toString(), "configurationSha256", hash(config),
                "sdkSha256", "a".repeat(64))).getAsJsonObject();
        data.addProperty("schemaVersion", 1);
        data.addProperty("status", "PASS");
        data.addProperty("successfulTests", 9);
        data.addProperty("adapterBundleSha256", "b".repeat(64));
        data.addProperty("adapterBundleHashFormat", "sha256-jar-bytes");
        Files.writeString(receipt, data.toString());
        return receipt;
    }

    private static JsonObject report(InstalledMathematicsVerifier.Receipt fixture, String mode,
            String before, String after, boolean changed, boolean applied) throws Exception {
        Map<String, Object> proof = Map.of(
                "schemaRevision", "regelsuche.optimization-evidence/v1",
                "semanticsRevision", "java25-numeric/v1",
                "checkerRevision", "checker/v1", "generatorRevision", "generator/v1",
                "safetyProfile", "PRESERVE_JAVA", "sourceHash", "a".repeat(64),
                "candidateHash", "b".repeat(64), "traceHash", "c".repeat(64),
                "assumptionsHash", "d".repeat(64), "proofMethods", List.of("fixture-proof"));
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("path", "/MathematicsHeadlessQualification/src/example/Calculation.java");
        file.put("targetJava", 17);
        file.put("options", OPTIONS);
        file.put("sourceSha256", hash(before));
        file.put("afterSha256", hash(after));
        file.put("original", before); file.put("replacement", after);
        file.put("applied", applied);
        file.put("environmentDigest", "e".repeat(64));
        file.put("compilerOptions", Map.of("org.eclipse.jdt.core.compiler.source", "17",
                "org.eclipse.jdt.core.compiler.compliance", "17", "org.eclipse.jdt.core.compiler.codegen.targetPlatform", "17"));
        file.put("changes", changed ? List.of(Map.of("offset", 0, "length", before.length(), "replacement", after)) : List.of());
        file.put("evidence", changed ? List.of(Map.of("proof", proof, "offset", 0, "length", before.length(),
                "original", before, "replacement", after, "cost", Map.of("sourceScore", 2, "candidateScore", 1))) : List.of());
        return new Gson().toJsonTree(Map.of(
                "schemaVersion", 1, "mode", mode, "project", fixture.project(),
                "sdkSha256", fixture.sdkSha256(), "adapterBundleSha256", fixture.adapterBundleSha256(),
                "files", List.of(file), "requestedOptions", OPTIONS, "configProperties", OPTIONS)).getAsJsonObject();
    }

    private static JsonObject proof(JsonObject report) {
        return report.getAsJsonArray("files").get(0).getAsJsonObject()
                .getAsJsonArray("evidence").get(0).getAsJsonObject().getAsJsonObject("proof");
    }

    private static String hash(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static void bundle(Path plugins, String id, String sdk, boolean packed) throws Exception {
        bundle(plugins, id, sdk, packed, "adapter-original");
    }

    private static void bundle(Path plugins, String id, String sdk, boolean packed, String adapter) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Bundle-SymbolicName", id + ";singleton:=true");
        manifest.getMainAttributes().putValue("Bundle-Version", "1.3.6");
        if (packed) {
            try (var jar = new JarOutputStream(Files.newOutputStream(plugins.resolve(id + "_1.3.6.jar")), manifest)) {
                jar.putNextEntry(new JarEntry("Adapter.class"));
                jar.write(adapter.getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
                if (sdk != null) {
                    jar.putNextEntry(new JarEntry("lib/regelsuche-optimization-sdk.jar"));
                    jar.write(sdk.getBytes(StandardCharsets.UTF_8));
                    jar.closeEntry();
                }
            }
        } else {
            Path directory = Files.createDirectories(plugins.resolve(id + "_1.3.6"));
            Files.createDirectories(directory.resolve("META-INF"));
            try (var output = Files.newOutputStream(directory.resolve("META-INF/MANIFEST.MF"))) { manifest.write(output); }
            Files.writeString(directory.resolve("Adapter.class"), adapter);
            if (sdk != null) {
                Files.createDirectories(directory.resolve("lib"));
                Files.writeString(directory.resolve("lib/regelsuche-optimization-sdk.jar"), sdk);
            }
        }
    }
}
