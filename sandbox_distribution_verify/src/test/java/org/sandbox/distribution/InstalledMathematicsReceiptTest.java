/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

/** Rejects stale, incomplete and differently packaged workbench qualification. */
class InstalledMathematicsReceiptTest {
    private static final String PROJECT = "MathematicsHeadlessQualification";
    @TempDir Path temporary;

    @Test
    void acceptsOnlyCompleteSuiteAndRetainsExactArtifactIdentities() throws Exception {
        JsonObject receipt = receipt();
        var accepted = InstalledMathematicsVerifier.readReceipt(write(receipt));
        assertEquals("a".repeat(64), accepted.sdkSha256());
        assertEquals("b".repeat(64), accepted.adapterBundleSha256());
        assertEquals("true", accepted.options().get("cleanup.mathematics"));
        assertThrows(UnsupportedOperationException.class,
                () -> accepted.options().put("cleanup.mathematics", "false"));
    }

    @Test
    void rejectsMissingOrUnknownReceiptSchema() throws Exception {
        JsonObject receipt = receipt();
        receipt.remove("schemaVersion");
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.readReceipt(write(receipt)));
        receipt.addProperty("schemaVersion", 2);
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.readReceipt(write(receipt)));
    }

    @Test
    void rejectsPartialFailedAndUnfinishedWorkbenchRuns() throws Exception {
        JsonObject receipt = receipt();
        for (int count : List.of(0, 1, 8, 10)) {
            receipt.addProperty("successfulTests", count);
            assertThrows(IOException.class, () -> InstalledMathematicsVerifier.readReceipt(write(receipt)));
        }
        receipt.addProperty("successfulTests", 9);
        for (String status : List.of("RUNNING", "FAIL", "ABORTED")) {
            receipt.addProperty("status", status);
            assertThrows(IOException.class, () -> InstalledMathematicsVerifier.readReceipt(write(receipt)));
        }
    }

    @Test
    void rejectsUnpackagedOrMissingAdapterIdentityEvenWithValidSdkHash() throws Exception {
        JsonObject receipt = receipt();
        receipt.addProperty("adapterBundleHashFormat", "sha256-directory");
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.readReceipt(write(receipt)));
        receipt.addProperty("adapterBundleHashFormat", "sha256-jar-bytes");
        receipt.remove("adapterBundleSha256");
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.readReceipt(write(receipt)));
        receipt.addProperty("adapterBundleSha256", "missing");
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.readReceipt(write(receipt)));
    }

    @Test
    void rejectsChangedSourceAndConfigurationAfterTheWorkbenchRun() throws Exception {
        JsonObject receipt = receipt();
        Path source = Path.of(receipt.get("source").getAsString());
        String original = Files.readString(source);
        Files.writeString(source, original.replace("a + b", "a - b"));
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.readReceipt(write(receipt)));
        Files.writeString(source, original);
        Files.writeString(Path.of(receipt.get("configuration").getAsString()), "cleanup.mathematics=false\n");
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.readReceipt(write(receipt)));
    }

    @Test
    void rejectsSourceOutsideTheDisposableProjectDespiteMatchingHash() throws Exception {
        JsonObject receipt = receipt();
        Path unrelated = Files.writeString(temporary.resolve("Unrelated.java"), "class Unrelated {}\n");
        receipt.addProperty("source", unrelated.toString());
        receipt.addProperty("sourceSha256", digest(Files.readString(unrelated)));
        assertThrows(IOException.class, () -> InstalledMathematicsVerifier.readReceipt(write(receipt)));
    }

    @Test
    void rejectsAChangedTargetProjectJavaVersion() throws Exception {
        JsonObject receipt = receipt();
        receipt.addProperty("targetJava", 25);
        IOException rejected = assertThrows(IOException.class,
                () -> InstalledMathematicsVerifier.readReceipt(write(receipt)));
        assertTrue(rejected.getMessage().contains("Java 17"));
    }

    private JsonObject receipt() throws Exception {
        Path workspace = Files.createDirectories(temporary.resolve("workspace"));
        Files.createDirectories(workspace.resolve(".metadata"));
        Path sources = Files.createDirectories(workspace.resolve(PROJECT + "/src/example"));
        String source = "package example; public class Calculation { public int compute(int a,int b) { return a + b; } }\n";
        Path file = Files.writeString(sources.resolve("Calculation.java"), source);
        String config = "cleanup.mathematics=true\ncleanup.mathematics.numericKinds=INT\n"
                + "cleanup.mathematics.safetyProfile=PRESERVE_JAVA\ncleanup.mathematics.goal=READABILITY\n"
                + "cleanup.mathematics.workBudget=100000\ncleanup.mathematics.maxStates=2000\n"
                + "cleanup.mathematics.checkedOptIn=false\ncleanup.mathematics.exclusions=\n"
                + "cleanup.mathematics.underflowChecks=false\n";
        Path configuration = Files.writeString(temporary.resolve("mathematics.properties"), config);
        JsonObject receipt = new JsonObject();
        receipt.addProperty("schemaVersion", 1);
        receipt.addProperty("status", "PASS");
        receipt.addProperty("successfulTests", 9);
        receipt.addProperty("project", PROJECT);
        receipt.addProperty("workspace", workspace.toString());
        receipt.addProperty("source", file.toString());
        receipt.addProperty("sourceSha256", digest(source));
        receipt.addProperty("configuration", configuration.toString());
        receipt.addProperty("configurationSha256", digest(config));
        receipt.addProperty("targetJava", 17);
        receipt.addProperty("sdkSha256", "a".repeat(64));
        receipt.addProperty("adapterBundleSha256", "b".repeat(64));
        receipt.addProperty("adapterBundleHashFormat", "sha256-jar-bytes");
        return receipt;
    }

    private Path write(JsonObject receipt) throws IOException {
        return Files.writeString(temporary.resolve("receipt.json"), receipt.toString());
    }

    private static String digest(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
