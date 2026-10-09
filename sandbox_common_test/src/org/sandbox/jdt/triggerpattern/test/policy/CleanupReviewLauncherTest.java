/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises the shipped shell launcher without starting a second Eclipse build. */
@EnabledOnOs({OS.LINUX, OS.MAC})
class CleanupReviewLauncherTest {
    private static final String LAUNCHER = "sandbox_cleanup_cli_dist/src/main/scripts/sandbox-cleanup";
    @TempDir Path temporary;
    private Path install;
    private Path javaHome;

    @BeforeEach
    void prepareReadOnlyInstallation() throws Exception {
        temporary = temporary.toRealPath();
        install = Files.createDirectories(temporary.resolve("read only installation"));
        Files.createDirectories(install.resolve("bin"));
        Files.createDirectories(install.resolve("plugins"));
        Files.createDirectories(install.resolve("configuration"));
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve(LAUNCHER))) root = root.getParent();
        assertTrue(root != null, "Cannot find the shipped cleanup launcher");
        Files.copy(root.resolve(LAUNCHER), install.resolve("bin/sandbox-cleanup"));
        Files.writeString(install.resolve("plugins/org.eclipse.equinox.launcher_test.jar"), "fixture",
				StandardCharsets.UTF_8);
        Files.writeString(install.resolve("configuration/config.ini"), "fixture.shared.configuration=true\\n",
				StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(install.resolve("configuration"), PosixFilePermissions.fromString("r-xr-xr-x"));
        javaHome = Files.createDirectories(temporary.resolve("test jdk/bin")).getParent();
        Path java = javaHome.resolve("bin/java");
        // Substitute only the JVM process boundary. All paths, quoting, permissions
        // and workspace/configuration setup are handled by the actual launcher.
        Files.writeString(java, """
                #!/bin/sh
                set -eu
                if [ "$1" = -XshowSettings:properties ]; then
                    echo "    java.specification.version = ${TEST_JAVA_VERSION:-25}" >&2
                    exit 0
                fi
                printf '%s\\n' "$@" > "$TEST_JAVA_ARGUMENTS"
                while [ "$#" -gt 0 ]; do
                    if [ "$1" = -configuration ]; then
                        [ -w "$2" ] || { echo "Configuration is not writable: $2" >&2; exit 15; }
                        : > "$2/started.marker"
                        break
                    fi
                    shift
                done
                exit "${TEST_JAVA_EXIT:-0}"
                """, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(java, PosixFilePermissions.fromString("rwx------"));
    }

    @AfterEach
    void restorePermissionsForTemporaryDirectoryCleanup() throws Exception {
        if (install != null && Files.exists(install.resolve("configuration"))) {
            Files.setPosixFilePermissions(install.resolve("configuration"), PosixFilePermissions.fromString("rwx------"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SANDBOX_WORKSPACE", "SANDBOX_CLEANUP_WORKSPACE", "TMPDIR"})
    void runtimeStateIsWritableAndIsolatedFromTheInstallation(String workspaceVariable) throws Exception {
        Path first = runAndReadConfiguration(workspaceVariable, "first workspace");
        Path second = runAndReadConfiguration(workspaceVariable, "second workspace");
        assertFalse(first.equals(second), "Independent workspaces must not share Equinox state");
        assertEquals("fixture.shared.configuration=true\n", Files.readString(install.resolve("configuration/config.ini"), StandardCharsets.UTF_8));
        assertFalse(Files.exists(install.resolve("configuration/started.marker")), "The installation must stay untouched");
    }

    @Test
    void forwardsTheApplicationExitCode() throws Exception {
        ProcessBuilder builder = process("SANDBOX_WORKSPACE", temporary.resolve("exit workspace"), temporary.resolve("exit.args"));
        builder.environment().put("TEST_JAVA_EXIT", "2");
        assertEquals(2, execute(builder, temporary.resolve("exit.log")));
    }

    @Test
    void rejectsUnsupportedJavaBeforeStartingEquinox() throws Exception {
        Path capture = temporary.resolve("unsupported.args");
        ProcessBuilder builder = process("SANDBOX_WORKSPACE", temporary.resolve("unsupported workspace"), capture);
        builder.environment().put("TEST_JAVA_VERSION", "21");
        assertEquals(1, execute(builder, temporary.resolve("unsupported.log")));
        assertFalse(Files.exists(capture), "Unsupported Java must not start Equinox");
        assertTrue(Files.readString(temporary.resolve("unsupported.log"), StandardCharsets.UTF_8).contains("requires the supported Java 25 runtime"));
    }

    private Path runAndReadConfiguration(String variable, String name) throws Exception {
        Path workspace = Files.createDirectories(temporary.resolve(name));
        Path capture = temporary.resolve(name + ".args");
        Path log = temporary.resolve(name + ".log");
        assertEquals(0, execute(process(variable, workspace, capture), log), () -> readLog(log));
        List<String> args = Files.readAllLines(capture, StandardCharsets.UTF_8);
        Path data = Path.of(argumentAfter(args, "-data"));
        Path configuration = Path.of(argumentAfter(args, "-configuration"));
        if (variable.equals("TMPDIR")) assertEquals(workspace, data.getParent());
        else assertEquals(workspace, data);
        assertEquals(data.resolve(".sandbox-configuration"), configuration);
        assertTrue(Files.isRegularFile(configuration.resolve("started.marker")));
        assertTrue(args.indexOf("-Dosgi.configuration.cascaded=true") < args.indexOf("-jar"));
        assertTrue(args.contains("-Dosgi.configuration.cascaded=true"));
        assertTrue(args.contains("-Dosgi.sharedConfiguration.area=" + install.resolve("configuration")));
        assertTrue(args.contains("-Dosgi.sharedConfiguration.area.readOnly=true"));
        assertEquals(install.toString(), argumentAfter(args, "-install"));
        assertEquals("source with spaces/Example.java", argumentAfter(args, "--source"));
        return configuration;
    }

    private ProcessBuilder process(String variable, Path workspace, Path capture) {
        ProcessBuilder builder = new ProcessBuilder("sh", install.resolve("bin/sandbox-cleanup").toString(),
                "--mode", "apply", "--source", "source with spaces/Example.java");
        for (String key : List.of("SANDBOX_WORKSPACE", "SANDBOX_CLEANUP_WORKSPACE", "TMPDIR")) builder.environment().remove(key);
        builder.environment().put(variable, workspace.toString());
        builder.environment().put("JAVA_HOME", javaHome.toString());
        builder.environment().put("TEST_JAVA_ARGUMENTS", capture.toString());
        return builder;
    }

    private static int execute(ProcessBuilder builder, Path log) throws Exception {
        Process process = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Launcher did not terminate");
            return process.exitValue();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static String argumentAfter(List<String> arguments, String flag) {
        int index = arguments.indexOf(flag);
        assertTrue(index >= 0 && index + 1 < arguments.size(), "Missing argument: " + flag + " in " + arguments);
        return arguments.get(index + 1);
    }

    private static String readLog(Path log) {
        try { return Files.readString(log, StandardCharsets.UTF_8); }
        catch (java.io.IOException exception) { return exception.toString(); }
    }
}
