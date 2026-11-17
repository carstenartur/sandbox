/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.distribution;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonParser;

/** Runs the installed Maven plugin and native CLI on a project with no Eclipse metadata. */
class MavenCleanupConsumerIT {
    @TempDir Path temporary;

    private final Path root = Path.of(System.getProperty("basedir")).toAbsolutePath().getParent();

    @Test
    void checksPreviewsAndAppliesEditsToAnOrdinaryMavenProject() throws Exception {
        Path project = Files.createDirectory(temporary.resolve("plain Maven project"));
        String relative = "src/main/java/example/EncodingExample.java";
        Path source = project.resolve(relative);
        Files.createDirectories(source.getParent());
        Files.copy(root.resolve("examples/cleanup-review/eclipse/src/example/EncodingExample.java"), source);
        Files.copy(root.resolve(".github/actions/cleanup-review/profiles/conservative-encoding.properties"),
                project.resolve("cleanup.properties"));
        Files.writeString(project.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>cleanup-consumer</artifactId><version>1</version>
                  <properties><maven.compiler.release>17</maven.compiler.release></properties>
                  <build><plugins><plugin>
                    <groupId>io.github.carstenartur</groupId><artifactId>sandbox-maven-plugin</artifactId>
                    <version>%s</version>
                    <configuration><configFile>${project.basedir}/cleanup.properties</configFile></configuration>
                  </plugin></plugins></build>
                </project>
                """.formatted(System.getProperty("sandbox.plugin.version")));
        byte[] original = Files.readAllBytes(source);
        Files.setLastModifiedTime(source, FileTime.fromMillis(1_700_000_000_000L));
        FileTime originalTime = Files.getLastModifiedTime(source);
        runGoal(project, "check", 1, 1);
        assertArrayEquals(original, Files.readAllBytes(source));
        assertEquals(originalTime, Files.getLastModifiedTime(source));
        Path preview = runGoal(project, "diff", 0, 1);
        String patch = Files.readString(preview.resolve("cleanup.patch"));
        assertTrue(patch.contains("+++ b/" + relative), patch);
        assertTrue(patch.contains("+        return StandardCharsets.UTF_8;"), patch);
        assertArrayEquals(original, Files.readAllBytes(source));
        assertEquals(originalTime, Files.getLastModifiedTime(source));

        runGoal(project, "apply", 0, 1);
        assertTrue(Files.readString(source).contains("return StandardCharsets.UTF_8;"));
        assertTrue(Files.readString(source).contains("return StandardCharsets.ISO_8859_1;"));
        runGoal(project, "check", 0, 0);
        for (String metadata : List.of(".project", ".classpath", ".settings", ".metadata")) {
            assertFalse(Files.exists(project.resolve(metadata)), metadata);
        }
    }

    private Path runGoal(Path project, String goal, int exit, int changes) throws Exception {
        Path result = Files.createTempDirectory(temporary, goal);
        Path log = result.resolve("maven.log");
        Path report = result.resolve("report.json");
        Process process = new ProcessBuilder(root.resolve("mvnw").toString(), "-B", "-ntp", "sandbox:" + goal,
                "-Dsandbox.toolSource=" + System.getProperty("sandbox.cli.home"),
                "-Dsandbox.failOnChanges=" + !goal.equals("diff"), "-Dsandbox.reportFile=" + report,
                "-Dsandbox.patchFile=" + result.resolve("cleanup.patch"))
                .directory(project.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(5, TimeUnit.MINUTES), () -> "Timed out running sandbox:" + goal);
            String output = Files.readString(log);
            assertEquals(exit, process.exitValue(), output);
            assertTrue(Files.isRegularFile(report), output);
            String json = Files.readString(report);
            var evidence = JsonParser.parseString(json).getAsJsonObject();
            assertEquals(0, evidence.get("errorCount").getAsInt(), json);
            assertEquals(1, evidence.get("filesProcessed").getAsInt(), json);
            assertEquals(changes, evidence.get("filesChanged").getAsInt(), json);
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
        return result;
    }
}
