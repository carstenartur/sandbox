/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Executes the workflow's disk reclamation without changing candidate products or evidence. */
@EnabledOnOs(OS.LINUX)
class DistributionConsumerDiskSpaceTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void reclaimsOnlyCompletedAggregateScratchBeforeNativeConsumption(boolean scratchExists) throws Exception {
        String workflow = Files.readString(root().resolve(".github/workflows/distribution-smoke.yml"));
        String heading = "      - name: Reclaim completed aggregate installation scratch space\n";
        int cleanup = workflow.indexOf(heading);
        int build = workflow.indexOf("      - name: Maven distribution build and verification\n");
        int consumer = workflow.indexOf("      - name: Qualify native Eclipse consumers against the candidate image\n");
        assertTrue(build >= 0 && cleanup > build && consumer > cleanup,
                "Reclaim completed scratch after the successful build and before native consumer image creation");
        String step = workflow.substring(cleanup, workflow.indexOf("\n      - ", cleanup + 1));
        assertTrue(step.contains("        if: runner.os == 'Linux'\n"), "Cleanup retains the default success condition");
        int run = step.indexOf("        run: |\n");
        assertTrue(run >= 0, "Missing cleanup shell");
        String script = step.substring(run + "        run: |\n".length()).lines()
                .map(line -> line.isBlank() ? "" : line.substring(10))
                .collect(java.util.stream.Collectors.joining("\n", "", "\n"));

        List<Path> retained = List.of(
                file("target/distribution-verification/fresh-install/plugins/adapter.jar"),
                file("target/distribution-verification/aggregate/verification.properties"),
                file("target/distribution-verification/aggregate-installation-evidence/receipt.json"),
                file("target/aggregate-installations-retained/near-match"),
                file("sandbox_product/target/products/candidate.jar"),
                file("sandbox_cleanup_cli_dist/target/candidate-dist.tar.gz"),
                file("sandbox_math_cleanup_test/target/headless-probe/receipt.json"));
        Path scratch = temporary.resolve("target/aggregate-installation-123/fresh/plugins/old.jar");
        if (scratchExists) file(temporary.relativize(scratch).toString());
        Path link = temporary.resolve("target/aggregate-installation-link");
        Files.createSymbolicLink(link, temporary.resolve("sandbox_product/target/products"));
        Path log = temporary.resolve("cleanup.log");
        Process process = new ProcessBuilder("bash", "--noprofile", "--norc", "-c", script)
                .directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Cleanup did not finish");
            assertEquals(0, process.exitValue(), Files.readString(log));
            assertFalse(Files.exists(scratch.getParent().getParent().getParent()), "Completed installation remains on disk");
            for (Path path : retained) assertEquals("retained\n", Files.readString(path), path.toString());
            assertTrue(Files.isSymbolicLink(link), "Do not traverse or delete installation-like symbolic links");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private Path file(String relative) throws Exception {
        Path path = temporary.resolve(relative);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, "retained\n");
    }

    private static Path root() {
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent())
            if (Files.isRegularFile(root.resolve(".github/workflows/distribution-smoke.yml"))) return root;
        throw new AssertionError("Cannot locate distribution workflow");
    }
}
