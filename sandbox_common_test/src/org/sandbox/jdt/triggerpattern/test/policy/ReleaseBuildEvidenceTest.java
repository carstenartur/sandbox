/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Runs the Ubuntu release workflow's real shell against a controlled Maven process. */
@EnabledOnOs(OS.LINUX)
class ReleaseBuildEvidenceTest {
    private static final String WORKFLOW = ".github/workflows/deploy-release.yml";
    private static final String BEFORE_CLEAN = "Maven stdout before clean\n";
    private static final String MAVEN_OUTPUT = BEFORE_CLEAN + "Maven stderr before clean\n"
            + "Maven stdout after clean\nMaven stderr after clean\n";

    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(ints = { 0, 37 })
    void retainsCompleteLogAndOriginalExitStatusWhenCleanRemovesTarget(int mavenExit) throws Exception {
        Path checkout = Files.createDirectory(temporary.resolve("checkout"));
        Path bin = Files.createDirectory(temporary.resolve("bin"));
        Path runnerTemp = Files.createDirectory(temporary.resolve("runner-temp"));
        Path sentinel = Files.createDirectories(checkout.resolve("target")).resolve("clean-sentinel");
        Files.writeString(sentinel, "Maven clean must remove this file", StandardCharsets.UTF_8);
        writeExecutable(bin.resolve("xvfb-run"), "#!/bin/bash\nshift\nexec \"$@\"\n");
        writeExecutable(bin.resolve("mvn"), FAKE_MAVEN);
        writeExecutable(checkout.resolve("mvnw"), FAKE_MAVEN);

        Path console = temporary.resolve("console.log");
        ProcessBuilder builder = new ProcessBuilder("bash", "--noprofile", "--norc", "-c", buildScript())
                .directory(checkout.toFile()).redirectErrorStream(true).redirectOutput(console.toFile());
        builder.environment().put("PATH", bin + ":" + builder.environment().get("PATH"));
        builder.environment().put("RUNNER_TEMP", runnerTemp.toString());
        builder.environment().put("SKIP_TESTS", "false");
        builder.environment().put("FAKE_MAVEN_EXIT", Integer.toString(mavenExit));
        Process process = builder.start();
        try {
            // Seeing tee's console output proves it opened the log before clean removes target.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (process.isAlive() && !Files.readString(console, StandardCharsets.UTF_8).contains(BEFORE_CLEAN)
                    && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(Files.readString(console, StandardCharsets.UTF_8).contains(BEFORE_CLEAN), Files.readString(console, StandardCharsets.UTF_8));
            process.getOutputStream().write("continue\n".getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Release build shell did not finish");

            String output = Files.readString(console, StandardCharsets.UTF_8);
            assertEquals(mavenExit, process.exitValue(), output);
            assertEquals(MAVEN_OUTPUT, output);
            assertFalse(Files.exists(sentinel), "The fixture must execute Maven clean's deletion");
            Path evidence = checkout.resolve("target/distribution-verification/maven-release.log");
            assertTrue(Files.isRegularFile(evidence), "Maven log must survive clean for exit " + mavenExit);
            assertEquals(MAVEN_OUTPUT, Files.readString(evidence, StandardCharsets.UTF_8));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static void writeExecutable(Path file, String source) throws Exception {
        Files.writeString(file, source, StandardCharsets.UTF_8);
        assertTrue(file.toFile().setExecutable(true), "Cannot execute fixture " + file);
    }

    private static String buildScript() throws Exception {
        List<String> lines = Files.readAllLines(root().resolve(WORKFLOW), StandardCharsets.UTF_8);
        String step = "      - name: Maven distribution build and verification";
        assertEquals(1, lines.stream().filter(step::equals).count(), "Expected exactly one distribution build");
        int start = lines.indexOf(step) + 1;
        while (start < lines.size() && !lines.get(start).startsWith("      - ")
                && !lines.get(start).equals("        run: |")) start++;
        assertTrue(start < lines.size() && lines.get(start).equals("        run: |"), "Missing build shell");
        StringBuilder script = new StringBuilder();
        for (int index = start + 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!line.isBlank() && !line.startsWith("          ")) break;
            script.append(line.isBlank() ? "" : line.substring(10)).append('\n');
        }
        assertFalse(script.toString().isBlank(), "Empty build shell");
        return script.toString();
    }

    private static Path root() {
        for (Path path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve(WORKFLOW))) return path;
        }
        throw new AssertionError("Cannot locate release workflow");
    }

    // The handshake lets the real tee open its destination before Maven clean deletes it.
    // Both Maven entry points use this process adapter so wrapper changes keep the same coverage.
    private static final String FAKE_MAVEN = """
            #!/bin/bash
            printf 'Maven stdout before clean\\n'
            printf 'Maven stderr before clean\\n' >&2
            read -r continue_build
            find target -depth -delete
            printf 'Maven stdout after clean\\n'
            printf 'Maven stderr after clean\\n' >&2
            exit "$FAKE_MAVEN_EXIT"
            """;
}
