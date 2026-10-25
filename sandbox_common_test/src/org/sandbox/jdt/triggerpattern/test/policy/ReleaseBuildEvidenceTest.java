/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Runs the release build adapter against a clean lifecycle that removes target. */
class ReleaseBuildEvidenceTest {
    @EnabledOnOs(value = OS.LINUX, disabledReason = "Release publication runs on ubuntu-latest with Bash")
    @ParameterizedTest
    @ValueSource(ints = { 0, 23 })
    void cleanBuildRetainsItsLogAndExitStatus(int buildExit, @TempDir Path directory) throws Exception {
        Path bin = Files.createDirectory(directory.resolve("bin"));
        executable(bin.resolve("xvfb-run"), "#!/bin/bash\nshift\nexec \"$@\"\n");
        String build = "#!/bin/bash\necho 'release build started'\nrm -rf target\n"
                + "echo 'release build finished'\nexit " + buildExit + "\n";
        executable(directory.resolve("mvnw"), build);
        executable(bin.resolve("mvn"), build);
        Path temporary = Files.createDirectory(directory.resolve("runner-temp"));
        Path script = directory.resolve("build.sh");
        Files.writeString(script, buildScript());
        Path output = directory.resolve("process.log");
        ProcessBuilder builder = new ProcessBuilder("bash", script.toString()).directory(directory.toFile())
                .redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().put("PATH", bin + ":" + builder.environment().get("PATH"));
        builder.environment().put("RUNNER_TEMP", temporary.toString());
        builder.environment().put("SKIP_TESTS", "false");
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS));
            assertEquals(buildExit, process.exitValue(), Files.readString(output));
            Path log = directory.resolve("target/distribution-verification/maven-release.log");
            assertTrue(Files.isRegularFile(log), "Maven clean must not delete the release build log");
            assertEquals("release build started\nrelease build finished\n", Files.readString(log));
        } finally {
            process.destroyForcibly();
        }
    }

    private static void executable(Path path, String contents) throws IOException {
        Files.writeString(path, contents);
        assertTrue(path.toFile().setExecutable(true));
    }

    private static String buildScript() throws IOException {
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            Path workflow = root.resolve(".github/workflows/deploy-release.yml");
            if (!Files.isRegularFile(workflow)) continue;
            String text = Files.readString(workflow);
            String name = "      - name: Maven distribution build and verification\n";
            int start = text.indexOf(name);
            assertTrue(start >= 0);
            int script = text.indexOf("        run: |\n", start) + "        run: |\n".length();
            int end = text.indexOf("\n      - name:", script);
            assertTrue(end > script);
            return text.substring(script, end).stripIndent();
        }
        throw new IOException("Release workflow not found");
    }
}
