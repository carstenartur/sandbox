/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.api.Git;

import org.junit.jupiter.api.Test;

import com.google.gson.GsonBuilder;

/** Explicitly selected by the optional patched-host workflow, never by default. */
class PinnedLtkRuntimeIT {
    private static final String SOURCE = "bundles/org.eclipse.ltk.core.refactoring";
    private static final String TESTS = "tests/org.eclipse.ltk.core.refactoring.tests";
    private static final String IMPLEMENTATION = SOURCE + "/src/org/eclipse/ltk/core/refactoring/CompositeChange.java";
    private static final String TEST_PACKAGE = TESTS + "/src/org/eclipse/ltk/core/refactoring/tests/";

    @Test
    void buildsAndProvisionsTheReleaseAlignedLtkPatch() throws Exception {
        Path root = Path.of(required("sandbox.ltk.root")).toAbsolutePath().normalize();
        Path output = Path.of(required("sandbox.ltk.output")).toAbsolutePath().normalize();
        Path resolvedJars = Path.of(required("sandbox.ltk.resolvedJars"));
        LtkRuntimePatch.require(!Files.exists(output), "Refusing to overwrite prior LTK evidence: " + output);
        Files.createDirectories(output);
        Properties pins = new Properties();
        try (var reader = Files.newBufferedReader(root.resolve(".github/patched-ltk.properties"), StandardCharsets.UTF_8)) {
            pins.load(reader);
        }
        URI remote = URI.create(pin(pins, "sourceRepository"));
        String ref = pin(pins, "sourceRef");
        String commit = pin(pins, "sourceCommit");
        String parent = pin(pins, "sourceParent");
        Path stock = LtkRuntimePatch.findStockBundle(resolvedJars);
        Path temporary = Files.createTempDirectory("sandbox-ltk-source-");
        try {
            try (PinnedGitRepository checkout = PinnedGitRepository.cloneAt(temporary.resolve("checkout"), remote, ref, commit)) {
                Path source = checkout.directory();
                verifySource(checkout, parent, pins);
                Exception buildFailure = null;
                try {
                    runMaven(source, output.resolve("build.log"), Duration.ofMinutes(35),
                            "-Pbuild-individual-bundles", "-pl",
                            ":org.eclipse.ltk.core.refactoring,:org.eclipse.ltk.core.refactoring.tests",
                            "-Dtycho.surefire.useUIHarness=false", "-Dtycho.surefire.useUIThread=false", "clean", "verify");
                } catch (Exception failure) {
                    buildFailure = failure;
                    throw failure;
                } finally {
                    try {
                        preserveReports(source.resolve(TESTS + "/target/surefire-reports"), output.resolve("surefire-reports"));
                    } catch (IOException reportFailure) {
                        if (buildFailure == null) {
                            throw reportFailure;
                        }
                        buildFailure.addSuppressed(reportFailure);
                    }
                }
                var tests = LtkRuntimePatch.verifyTests(output.resolve("surefire-reports"));
                Path bundle;
                try (var candidates = Files.list(source.resolve(SOURCE + "/target"))) {
                    var jars = candidates.filter(p -> p.getFileName().toString().startsWith(LtkRuntimePatch.BUNDLE + "-")
                            && p.toString().endsWith(".jar") && !p.toString().endsWith("-sources.jar")
                            && !p.toString().endsWith("-javadoc.jar")).toList();
                    LtkRuntimePatch.require(jars.size() == 1, "Expected exactly one built LTK bundle: " + jars);
                    bundle = jars.get(0);
                }
                String version = LtkRuntimePatch.validateBundle(bundle, stock);
                String digest = LtkRuntimePatch.sha256(bundle);
                String classDigest = LtkRuntimePatch.sha256(new ByteArrayInputStream(LtkRuntimePatch.readEntry(bundle, LtkRuntimePatch.CLASS)));
                Path publicationSource = output.resolve("publication-source");
                LtkRuntimePatch.prepareFeature(publicationSource, bundle, version);
                Path repository = Files.createDirectories(output.resolve("repository"));
                String tycho = LtkRuntimePatch.value(LtkRuntimePatch.xml(Files.readAllBytes(root.resolve("pom.xml"))),
                        "/project/properties/tycho-version");
                LtkRuntimePatch.require(tycho.matches("[0-9]+\\.[0-9]+\\.[0-9]+"), "Invalid Tycho version: " + tycho);
                writePublisherPom(output.resolve("pom.xml"), tycho);
                runMaven(output, output.resolve("publisher.log"), Duration.ofMinutes(10),
                        "org.eclipse.tycho.extras:tycho-p2-extras-plugin:" + tycho + ":publish-features-and-bundles",
                        "-Dsource.location=" + publicationSource, "-Drepository.location=" + repository);
                LtkRuntimePatch.verifyRepository(repository, version, digest);
                LtkRuntimePatch.addToTarget(root.resolve("sandbox_target/eclipse.target"), repository, version);

                var provenance = new LinkedHashMap<String, Object>();
                provenance.put("schemaVersion", 1);
                provenance.put("sourceRepository", remote.toString());
                provenance.put("sourceRef", ref);
                provenance.put("sourceCommit", checkout.headCommit());
                provenance.put("sourceParent", parent);
                provenance.put("upstreamFix", pin(pins, "upstreamFix"));
                provenance.put("bundleSymbolicName", LtkRuntimePatch.BUNDLE);
                provenance.put("bundleVersion", version);
                provenance.put("bundleSha256", digest);
                provenance.put("implementationClassSha256", classDigest);
                provenance.put("stockBundleVersion", LtkRuntimePatch.STOCK_VERSION);
                provenance.put("stockBundleSha256", LtkRuntimePatch.sha256(stock));
                provenance.put("patchTargetFeatureId", LtkRuntimePatch.TARGET_FEATURE);
                provenance.put("patchTargetFeatureVersion", LtkRuntimePatch.TARGET_VERSION);
                provenance.put("tests", tests);
                Files.writeString(output.resolve("provenance.json"), new GsonBuilder().setPrettyPrinting().create().toJson(provenance) + "\n");
                // Tiny deterministic properties file is also readable from the OSGi test without a JSON dependency.
                Files.writeString(output.resolve("runtime.properties"), "sourceCommit=" + commit + "\n"
                        + "bundleVersion=" + version + "\nimplementationClassSha256=" + classDigest + "\n");
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void verifySource(PinnedGitRepository checkout, String parent, Properties pins) throws Exception {
        try (Git git = Git.open(checkout.directory().toFile())) {
            var repository = git.getRepository();
            LtkRuntimePatch.require(parent.equals(repository.resolve("HEAD^1").name()), "Unexpected patch parent");
            LtkRuntimePatch.require(repository.resolve("HEAD^2") == null, "The release patch must have one parent");
            for (var entry : java.util.Map.of(IMPLEMENTATION, "implementationBlob",
                    TEST_PACKAGE + "CompositeChangeTest.java", "testBlob", TEST_PACKAGE + "AllTests.java", "suiteBlob").entrySet()) {
                LtkRuntimePatch.require(pin(pins, entry.getValue()).equals(repository.resolve("HEAD:" + entry.getKey()).name()),
                        "Pinned source bytes do not match reviewed fix: " + entry.getKey());
            }
            var changes = git.diff().setOldTree(tree(repository, parent)).setNewTree(tree(repository, "HEAD")).call();
            LtkRuntimePatch.require(changes.size() == 3 && changes.stream().allMatch(change ->
                    List.of(IMPLEMENTATION, TEST_PACKAGE + "CompositeChangeTest.java", TEST_PACKAGE + "AllTests.java")
                            .contains(change.getNewPath())), "Release patch contains unrelated changes");
        }
    }

    private static org.eclipse.jgit.treewalk.CanonicalTreeParser tree(org.eclipse.jgit.lib.Repository repository, String ref)
            throws IOException {
        var tree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
        try (var reader = repository.newObjectReader()) {
            tree.reset(reader, repository.resolve(ref + "^{tree}"));
        }
        return tree;
    }

    private static void runMaven(Path directory, Path log, Duration timeout, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of(required("sandbox.ltk.maven"), "-B", "-ntp"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        try {
            if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
                throw new IOException("Maven timed out; see " + log);
            }
            LtkRuntimePatch.require(process.exitValue() == 0, "Maven failed with exit " + process.exitValue() + "; see " + log);
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(30, TimeUnit.SECONDS);
            }
        }
    }

    private static void preserveReports(Path source, Path destination) throws IOException {
        if (Files.isDirectory(source)) {
            Files.createDirectories(destination);
            try (var files = Files.list(source)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    Files.copy(file, destination.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static String required(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required Maven property: " + key);
        }
        return value;
    }

    private static String pin(Properties pins, String key) {
        String value = pins.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing LTK source pin: " + key);
        }
        return value;
    }

    private static void writePublisherPom(Path path, String tycho) throws IOException {
        Files.writeString(path, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.sandbox.build</groupId><artifactId>ltk-patch-publisher</artifactId>
                  <version>1.0.0</version><packaging>pom</packaging>
                  <build><plugins><plugin>
                    <groupId>org.eclipse.tycho.extras</groupId><artifactId>tycho-p2-extras-plugin</artifactId>
                    <version>%s</version>
                    <configuration>
                      <sourceLocation>${source.location}</sourceLocation>
                      <metadataRepositoryLocation>${repository.location}</metadataRepositoryLocation>
                      <artifactRepositoryLocation>${repository.location}</artifactRepositoryLocation>
                      <compress>true</compress><publishArtifacts>true</publishArtifacts><append>false</append>
                    </configuration>
                  </plugin></plugins></build>
                </project>
                """.formatted(tycho));
    }
}
