/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Keeps the source build and installed replay on the same repository-pinned Maven. */
class VerificationMavenWrapperContractTest {
    private record Invocation(String file, String step, String command) { }

    static Stream<Invocation> invocations() {
        return Stream.of(
                new Invocation("codacy.yml", "Build and run Maven analysis", "run: ./mvnw -B -DskipTests package"),
                new Invocation("codeql.yml", "Build with Maven", "run: ./mvnw -B -DskipTests clean package"),
                new Invocation("distribution-smoke.yml", "Maven distribution build and verification",
                        "command=(./mvnw -Pdistribution,cli-dist,swtbot"),
                new Invocation("distribution-smoke.yml", "Verify installed mathematics without a display",
                        "./mvnw -Pdistribution --batch-mode -pl sandbox_distribution_verify"),
                new Invocation("publish-cleanup-image.yml", "Build Eclipse product",
                        "xvfb-run --auto-servernum ./mvnw -e -V --batch-mode"),
                new Invocation("eclipse-help-screenshots.yml", "Reproduce Eclipse Help screenshots",
                        "          ./mvnw\n"),
                new Invocation("patched-jdt-ui-atomic-help-screenshot.yml", "Reproduce the atomic Cleanup previews",
                        "          ./mvnw\n"),
                new Invocation("patched-jdt-ui-atomic-help-screenshot.yml",
                        "Build, verify and provision the pinned LTK runtime through Maven",
                        "./mvnw -B -ntp -Dsandbox.tycho.linux-only=true"));
    }

    @ParameterizedTest
    @MethodSource("invocations")
    void verificationUsesTheRepositoryWrapper(Invocation invocation) throws IOException {
        assertPinned(workflow(invocation.file()), invocation);
    }

    @ParameterizedTest
    @MethodSource("invocations")
    void eachVerificationRejectsRunnerMavenAndDisabledValidation(Invocation invocation) throws IOException {
        String original = workflow(invocation.file());
        assertPinned(original, invocation);
        String step = step(original, invocation.step());
        assertThrows(AssertionError.class, () -> assertPinned(original.replace(step,
                step.replace("./mvnw", "mvn")), invocation));
        assertThrows(AssertionError.class, () -> assertPinned(original.replace(step, ""), invocation));
        assertThrows(AssertionError.class, () -> assertPinned(original.replace(step,
                step + "\n          -Dmaven.resolver.validation=off\n"), invocation));
    }

    @ParameterizedTest
    @ValueSource(strings = { "codacy.yml", "codeql.yml", "distribution-smoke.yml", "publish-cleanup-image.yml",
            "eclipse-help-screenshots.yml", "patched-jdt-ui-atomic-help-screenshot.yml" })
    void wrapperChangesTriggerVerification(String file) throws IOException {
        String workflow = workflow(file);
        int pathLists = "distribution-smoke.yml".equals(file) ? 2 : 1;
        for (String path : new String[] { "mvnw", "mvnw.cmd", ".mvn/**" }) {
            assertEquals(pathLists, workflow.lines().filter(line -> line.strip().equals("- '" + path + "'")).count(), path);
        }
    }

    private static void assertPinned(String workflow, Invocation invocation) {
        String bootstrap = step(workflow, "Verify pinned Maven Wrapper");
        assertTrue(bootstrap.contains("run: ./mvnw --batch-mode --no-transfer-progress --version"));
        String execution = step(workflow, invocation.step());
        assertTrue(execution.contains(invocation.command()), invocation.step());
        assertFalse(Pattern.compile("(?<![\\w./-])mvn(?:\\.cmd)?\\s").matcher(execution).find(), invocation.step());
        assertFalse(workflow.contains("maven.resolver.validation"), "Do not turn off coordinate validation");
        if (invocation.step().startsWith("Reproduce ")) {
            assertTrue(execution.contains("-f sandbox_help_build/pom.xml"));
            assertTrue(execution.contains("-Phelp-screenshots"));
            assertTrue(execution.contains("clean verify"));
            assertTrue(execution.contains("-Dtycho.localArtifacts=ignore"));
            assertFalse(execution.contains("-DskipTests"), "Screenshot verification must execute its tests");
            assertFalse(execution.contains("-Dmaven.test.skip"), "Screenshot tests must be compiled and executed");
            String testClass = "eclipse-help-screenshots.yml".equals(invocation.file())
                    ? "SandboxHelpScreenshotsMergeGateSWTBotTest" : "SandboxAtomicPreviewPatchedJdtSWTBotTest";
            assertTrue(execution.contains("-Dhelp.screenshot.testClass=org.sandbox.jdt.ui.helper.views." + testClass));
        } else if (invocation.step().equals("Build, verify and provision the pinned LTK runtime through Maven")) {
            assertTrue(execution.contains("-Dsandbox.ltk.maven=\"$GITHUB_WORKSPACE/mvnw\""),
                    "The nested LTK build must use the same pinned Maven");
            assertTrue(execution.contains("-Dtest=LtkRuntimePatchTest,LtkRuntimeMetadataTest,PinnedLtkRuntimeIT"));
        } else if (invocation.step().equals("Maven distribution build and verification")) {
            assertTrue(execution.contains("-Dsandbox.math.retainHeadlessProbe=true clean verify"));
            assertTrue(execution.contains("-Dtycho.localArtifacts=ignore"));
        } else if (invocation.step().equals("Verify installed mathematics without a display")) {
            assertTrue(execution.contains("exec:java@verify-installed-mathematics"));
        } else if (invocation.step().equals("Build Eclipse product")) {
            assertTrue(execution.contains("-Dtycho.localArtifacts=ignore"));
            assertTrue(execution.contains("-Pproduct clean verify -DskipTests"));
            assertFalse(Pattern.compile("(?:^|\\s)-T(?:\\s|\\d)").matcher(execution).find(),
                    "Keep the mixed Maven/bnd/Tycho product build sequential");
        }
    }

    private static String step(String workflow, String name) {
        var start = Pattern.compile("(?m)^([ ]*)- name: " + Pattern.quote(name) + "\\r?$").matcher(workflow);
        assertTrue(start.find(), "Missing workflow step: " + name);
        int position = start.start(), after = start.end(), indentation = start.group(1).length();
        assertFalse(start.find(), "Duplicate workflow step: " + name);
        var next = Pattern.compile("(?m)^[ ]{" + indentation + "}- ").matcher(workflow);
        return workflow.substring(position, next.find(after) ? next.start() : workflow.length());
    }

    private static String workflow(String file) throws IOException {
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            Path path = root.resolve(".github/workflows").resolve(file);
            if (Files.isRegularFile(path)) return Files.readString(path, StandardCharsets.UTF_8);
        }
        throw new IOException("Repository workflow not found: " + file);
    }
}
