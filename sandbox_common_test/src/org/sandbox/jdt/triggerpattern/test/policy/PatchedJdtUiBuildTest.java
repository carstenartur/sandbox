/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises source staging; only the expensive Maven process is substituted. */
@EnabledOnOs(OS.LINUX)
class PatchedJdtUiBuildTest {
    private static final String SCRIPT = ".github/scripts/build_patched_jdt_ui.sh";
    private static final String PARENT = "eclipse-platform-parent/pom.xml";
    private static final String TARGET = "eclipse.platform.releng.prereqs.sdk/eclipse-sdk-prereqs.target";
    @TempDir Path temporary;
    private Path root;
    private Path source;
    private Path releng;
    private Path config;
    private Path work;
    private Path arguments;

    @BeforeEach
    void createPinnedLocalRepositories() throws Exception {
        root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve(SCRIPT))) root = root.getParent();
        assertTrue(root != null, "Cannot locate the actual build script");
        source = temporary.resolve("source");
        releng = temporary.resolve("releng");
        String parent;
        String patch;
        try (Git git = Git.init().setDirectory(source.toFile()).call()) {
            write(source, "pom.xml", "<project><modelVersion>4.0.0</modelVersion><artifactId>eclipse.jdt.ui</artifactId></project>");
            write(source, ".mvn/extensions.xml", "<extensions/>");
            parent = commit(git);
            String production = "org.eclipse.jdt.ui/core extension/org/eclipse/jdt/internal/corext/fix/CleanUpRefactoring.java";
            String test = "org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/quickfix/MultiFileCleanUpScopeExpansionTest.java";
            write(source, production, "EXPAND_CLEAN_UP_SCOPE_METHOD");
            write(source, test, "MultiFileCleanUpScopeExpansionTest");
            write(source, ".github/fork-specific-files.txt", production + "\n" + test);
            patch = commit(git);
        }
        String relengCommit;
        try (Git git = Git.init().setDirectory(releng.toFile()).call()) {
            write(releng, PARENT, "<project><artifactId>eclipse-platform-parent</artifactId></project>");
            write(releng, TARGET, "<target name='eclipse-sdk-prereqs'/>");
            write(releng, "eclipse.platform.releng.prereqs.sdk/pom.xml", "<project><artifactId>eclipse-sdk-prereqs</artifactId></project>");
            git.submoduleAdd().setPath("eclipse.jdt.ui").setURI(source.toUri().toString()).call().close();
            relengCommit = commit(git);
        }
        config = temporary.resolve("build.env");
        Files.writeString(config, """
                PATCHED_JDT_UI_REPOSITORY='%s'
                PATCHED_JDT_UI_COMMIT=%s
                PATCHED_JDT_UI_EXPECTED_PARENT=%s
                PATCHED_JDT_UI_BUNDLE=org.eclipse.jdt.ui
                PATCHED_JDT_UI_EXPECTED_BASE_VERSION=3.39.0
                PATCHED_JDT_UI_RELENG_REPOSITORY='%s'
                PATCHED_JDT_UI_RELENG_COMMIT=%s
                PATCHED_JDT_UI_PARENT_SHA256=%s
                PATCHED_JDT_UI_PREREQS_SHA256=%s
                PATCHED_JDT_UI_SDK_REPOSITORY=https://download.eclipse.org/eclipse/updates/4.41/R-4.41-202608281142/
                """.formatted(source.toUri(), patch, parent, releng.toUri(), relengCommit,
                        digest(releng.resolve(PARENT)), digest(releng.resolve(TARGET))));
        work = temporary.resolve("build with spaces");
        arguments = temporary.resolve("maven.args");
        Path maven = temporary.resolve("bin/mvn");
        write(temporary, "bin/mvn", "#!/bin/sh\nprintf '%s\\n' \"$@\" > \"$TEST_MAVEN_ARGUMENTS\"\nexit 73\n");
        Files.setPosixFilePermissions(maven, PosixFilePermissions.fromString("rwx------"));
    }

    @Test
    void suppliesBothSnapshotDescriptorsFromThePinnedCheckoutBeforeMaven() throws Exception {
        assertEquals(73, run(), Files.readString(temporary.resolve("build.log")));
        List<String> args = Files.readAllLines(arguments);
        Path reactor = Path.of(args.get(args.indexOf("-f") + 1));
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var xml = factory.newDocumentBuilder().parse(reactor.toFile());
        var xpath = XPathFactory.newInstance().newXPath();
        assertEquals("3", xpath.evaluate("count(/project/modules/module)", xml));
        for (String module : List.of("eclipse-platform-parent", "eclipse.platform.releng.prereqs.sdk", "eclipse.jdt.ui")) {
            assertEquals("1", xpath.evaluate("count(/project/modules/module[text()='" + module + "'])", xml));
            assertTrue(Files.isRegularFile(reactor.getParent().resolve(module).resolve("pom.xml")));
        }
        assertArrayEquals(Files.readAllBytes(releng.resolve(PARENT)), Files.readAllBytes(work.resolve(PARENT)));
        assertArrayEquals(Files.readAllBytes(releng.resolve(TARGET)), Files.readAllBytes(work.resolve(TARGET)));
        assertEquals("<extensions/>", Files.readString(work.resolve(".mvn/extensions.xml")));
        assertTrue(args.contains(":org.eclipse.jdt.ui"));
        assertTrue(args.contains("-am"));
        assertTrue(args.contains("-Declipse-sdk-repo=https://download.eclipse.org/eclipse/updates/4.41/R-4.41-202608281142/"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "PATCHED_JDT_UI_PARENT_SHA256", "PATCHED_JDT_UI_PREREQS_SHA256" })
    void rejectsChangedBuildDescriptorsBeforeStartingMaven(String pin) throws Exception {
        Files.writeString(config, Files.readString(config).replaceAll(pin + "=[0-9a-f]+", pin + "=" + "0".repeat(64)));
        assertNotEquals(73, run());
        assertNotEquals(73, runRelease());
        assertFalse(Files.exists(arguments), "Unverified build descriptors must not reach Maven");
    }

    @Test
    void releaseBuildPreservesSourceLocationAndCleansOnlyItsOwnDescriptors() throws Exception {
        Path parent = temporary.resolve(PARENT);
        Files.createDirectories(parent.getParent());
        Files.copy(releng.resolve(PARENT), parent);
        assertEquals(73, runRelease(), Files.readString(temporary.resolve("release.log")));
        List<String> args = Files.readAllLines(arguments);
        Path reactor = Path.of(args.get(args.indexOf("-f") + 1));
        assertTrue(Files.isSameFile(source, reactor.getParent().resolve(args.stream()
                .filter(arg -> arg.startsWith("-Djdt-ui-source=")).findFirst().orElseThrow().split("=", 2)[1])));
        assertTrue(args.contains("-Declipse-sdk-repo=https://download.eclipse.org/eclipse/updates/4.41/R-4.41-202608281142/"));
        assertArrayEquals(Files.readAllBytes(releng.resolve(PARENT)), Files.readAllBytes(parent));
        assertFalse(Files.exists(temporary.resolve("eclipse.platform.releng.prereqs.sdk")));
        try (Git git = Git.open(source.toFile())) { assertTrue(git.status().call().isClean()); }
        Files.delete(arguments);
        Files.writeString(parent, "Existing unrelated parent");
        assertNotEquals(73, runRelease());
        assertFalse(Files.exists(arguments));
        assertEquals("Existing unrelated parent", Files.readString(parent));
        Files.delete(parent);
        Files.delete(parent.getParent());
        assertEquals(73, runRelease());
        assertFalse(Files.exists(parent.getParent()));
    }

    @Test
    void releaseBuildRejectsSourceOutsideThePinnedRelease() throws Exception {
        write(source, "unrelated.txt", "different source");
        try (Git git = Git.open(source.toFile())) { commit(git); }
        assertNotEquals(73, runRelease());
        assertFalse(Files.exists(arguments));
    }

    private int runRelease() throws Exception {
        var builder = new ProcessBuilder("bash", root.resolve("qa/upstream-jdt/run-jdt-ui-release-build.sh").toString(),
                source.toString(), temporary.resolve("release build").toString(), temporary.resolve("bin/mvn").toString());
        builder.environment().put("JDT_UI_BUILD_CONFIG", config.toString());
        builder.environment().put("TEST_MAVEN_ARGUMENTS", arguments.toString());
        Process process = builder.redirectErrorStream(true).redirectOutput(temporary.resolve("release.log").toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Release staging timed out");
            return process.exitValue();
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }

    private int run() throws Exception {
        var builder = new ProcessBuilder("bash", root.resolve(SCRIPT).toString(), temporary.resolve("output").toString());
        builder.environment().put("PATCHED_JDT_UI_CONFIG", config.toString());
        builder.environment().put("PATCHED_JDT_UI_WORK_DIR", work.toString());
        builder.environment().put("TEST_MAVEN_ARGUMENTS", arguments.toString());
        builder.environment().put("PATH", temporary.resolve("bin") + ":" + Path.of(System.getProperty("java.home"), "bin")
                + ":" + builder.environment().get("PATH"));
        Process process = builder.redirectErrorStream(true).redirectOutput(temporary.resolve("build.log").toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Build staging timed out");
            return process.exitValue();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static void write(Path root, String name, String contents) throws Exception {
        Path path = root.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }

    private static String commit(Git git) throws Exception {
        git.add().addFilepattern(".").call();
        return git.commit().setMessage("Pinned fixture").setAuthor("Fixture", "fixture@example.invalid")
                .setCommitter("Fixture", "fixture@example.invalid").call().name();
    }

    private static String digest(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
