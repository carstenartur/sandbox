/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.distribution.SupportArtifacts.AGGREGATE;
import static org.sandbox.distribution.SupportArtifacts.BSN;
import static org.sandbox.distribution.SupportArtifacts.BINDING;
import static org.sandbox.distribution.SupportArtifacts.COMPONENT;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

class SupportArtifactsTest {
    @TempDir Path repo;
    private static final String V = "1.3.11.test";
    private static final String KEY = "<artifact classifier='osgi.bundle' id='" + BSN + "' version='" + V + "'/>";
    private static final String UNIT = "<unit id='" + BSN + "' version='" + V + "'><artifacts>" + KEY + "</artifacts></unit>";
    private static final String MEMBER = "<plugin id='" + BSN + "' version='" + V + "'/>";
    private static final String REQUIRED = required(BSN);

    @Test
    void checksActualPayloadMetadataArchivesAndInstalledDigest() throws Exception {
        createRepository();
        var site = SupportArtifacts.read(repo, false);
        assertEquals(AggregateInstallationVerifier.digest(site.support().path()), site.support().sha256());
        Path copy = repo.resolve("copy.jar");
        Files.copy(site.support().path(), copy);
        site.support().requireSameBytes(copy);
        Files.write(copy, new byte[] { 0 }, java.nio.file.StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> site.support().requireSameBytes(copy));
        feature(COMPONENT, MEMBER.replace(V, "1.0.0"));
        assertThrows(IOException.class, () -> SupportArtifacts.read(repo, false));
    }

    @Test
    void rejectsDuplicateOptionalUnboundedAndMissingSupport() throws Exception {
        createRepository();
        Path xml = repo.resolve("content.xml");
        String original = Files.readString(xml);
        for (String mutation : java.util.List.of(original.replace(UNIT, UNIT + UNIT), original.replace(UNIT, ""),
                original.replace(REQUIRED, REQUIRED.replace("/>", " optional='true'/>")),
                original.replace(REQUIRED, REQUIRED.replace("[" + V + "," + V + "]", "0.0.0")),
                original.replace(REQUIRED, REQUIRED + REQUIRED))) {
            Files.writeString(xml, mutation);
            assertThrows(IOException.class, () -> SupportArtifacts.read(repo, false));
        }
    }

    @ParameterizedTest
    @CsvSource({"os,linux", "ws,gtk", "arch,x86_64", "nl,en_US", "filter,(osgi.os=linux)", "p2Filter,(osgi.os=linux)"})
    void rejectsConditionalMembersThroughoutDeliveryGraph(String selector, String value) throws Exception {
        createRepository();
        Path content = repo.resolve("content.xml");
        String original = Files.readString(content);
        for (String id : java.util.List.of(BSN, BINDING, COMPONENT)) {
            boolean include = id.equals(COMPONENT);
            String feature = include ? AGGREGATE : COMPONENT;
            String members = include ? "<includes id='" + COMPONENT + "' version='" + V + "'/>" : MEMBER + MEMBER.replace(BSN, BINDING);
            if ("p2Filter".equals(selector)) {
                String requirement = required(include ? COMPONENT + ".feature.group" : id);
                Files.writeString(content, original.replace(requirement,
                        requirement.replace("/>", "><filter>(osgi.os=linux)</filter></required>")));
            } else {
                feature(feature, members.replace("id='" + id + "'", "id='" + id + "' " + selector + "='" + value + "'"));
            }
            assertThrows(IOException.class, () -> SupportArtifacts.read(repo, false), id + '/' + selector);
            feature(feature, members);
            Files.writeString(content, original);
        }
    }

    @Test
    void initialAbsenceRequiresAgreementOfIuKeyFilesAndContainingFeature() throws Exception {
        createRepository();
        Path content = repo.resolve("content.xml"), keys = repo.resolve("artifacts.xml");
        Files.writeString(content, Files.readString(content).replace(UNIT, "").replace(REQUIRED, "").replace(UNIT.replace(BSN, BINDING), "").replace(required(BINDING), ""));
        assertThrows(IOException.class, () -> SupportArtifacts.read(repo, true));
        Files.writeString(keys, Files.readString(keys).replace(KEY, "").replace(KEY.replace(BSN, BINDING), ""));
        assertThrows(IOException.class, () -> SupportArtifacts.read(repo, true));
        Files.delete(repo.resolve("plugins/" + BSN + '_' + V + ".jar"));
        Files.delete(repo.resolve("plugins/" + BINDING + '_' + V + ".jar"));
        assertThrows(IOException.class, () -> SupportArtifacts.read(repo, true));
        feature(COMPONENT, "");
        assertTrue(SupportArtifacts.read(repo, true).support() == null);
    }

    @Test
    void bindsTheAdvertisedDownloadChecksumToTheIndependentBuildManifest() throws Exception {
        createRepository();
        var tested = SupportArtifacts.read(repo, false);
        Path xml = repo.resolve("artifacts.xml");
        String descriptor = KEY.replace("/>", "><properties><property name='download.checksum.sha-256' value='"
                + tested.support().sha256() + "'/></properties></artifact>");
        String verified = Files.readString(xml).replace(KEY, descriptor);
        Files.writeString(xml, verified);
        Path original = Files.writeString(repo.resolve("original.json"), metadataManifest("artifacts.xml", "content.xml"));
        Path staged = Files.copy(original, repo.resolve("build-manifest.json"));
        SnapshotSupportPreflight.verifiedManifest(repo, original);
        Files.writeString(xml, verified.replace(tested.support().sha256(), "0".repeat(64)));
        assertEquals(tested, SupportArtifacts.read(repo, false));
        assertThrows(IOException.class, () -> SnapshotSupportPreflight.verifiedManifest(repo, original));
        assertThrows(IOException.class, () -> SnapshotSupportPreflight.verifiedManifest(repo, staged));
        Files.writeString(staged, metadataManifest("artifacts.xml", "content.xml"));
        assertThrows(IOException.class, () -> SnapshotSupportPreflight.verifiedManifest(repo, original));
    }

    @ParameterizedTest
    @ValueSource(strings = {"content.jar", "artifacts.jar", "content.xml.xz", "artifacts.xml.xz", "content.xz", "p2.index", "compositeContent.jar"})
    void rejectsAnUnlistedP2MetadataAlternative(String alternative) throws Exception {
        createRepository();
        Path manifest = Files.writeString(repo.resolve("build-manifest.json"), metadataManifest("artifacts.xml", "content.xml"));
        Files.writeString(repo.resolve(alternative), "unverified metadata");
        assertThrows(IOException.class, () -> SnapshotSupportPreflight.verifiedManifest(repo, manifest));
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate", "missing", "size", "traversal", "nested", "absolute"})
    void rejectsInvalidManifestInventory(String defect) throws Exception {
        createRepository();
        String manifest = metadataManifest("artifacts.xml", "content.xml");
        String invalid = switch (defect) {
            case "duplicate" -> metadataManifest("artifacts.xml", "artifacts.xml", "content.xml");
            case "missing" -> metadataManifest("artifacts.xml");
            case "size" -> manifest.replaceFirst("\"size_bytes\":\\d+", "\"size_bytes\":0");
            case "traversal" -> manifest.replace("/content.xml", "/../repository/content.xml");
            case "nested" -> manifest.replace("/content.xml", "/child/content.xml");
            case "absolute" -> manifest.replace("sandbox_updatesite/", "/sandbox_updatesite/");
            default -> throw new AssertionError(defect);
        };
        Path path = Files.writeString(repo.resolve("build-manifest.json"), invalid);
        assertThrows(IOException.class, () -> SnapshotSupportPreflight.verifiedManifest(repo, path));
    }

    private String metadataManifest(String... names) throws Exception {
        var entries = new java.util.ArrayList<String>();
        for (String name : names) {
            Path file = repo.resolve(name);
            entries.add("""
                    {"path":"sandbox_updatesite/target/repository/%s","size_bytes":%d,"sha256":"%s"}
                    """.formatted(name, Files.size(file), AggregateInstallationVerifier.digest(file)).strip());
        }
        return """
                {"ref":"refs/heads/main","artifacts":{"update_site":{"metadata":[%s]}}}
                """.formatted(String.join(",", entries));
    }

    private void createRepository() throws Exception {
        Path plugins = Files.createDirectory(repo.resolve("plugins"));
        Files.createDirectory(repo.resolve("features"));
        var manifest = new Manifest();
        var headers = manifest.getMainAttributes();
        headers.putValue("Manifest-Version", "1.0");
        headers.putValue("Bundle-Version", V);
        headers.putValue("Bundle-SymbolicName", BSN);
        try (var jar = new JarOutputStream(Files.newOutputStream(plugins.resolve(BSN + '_' + V + ".jar")), manifest)) {
            jar.finish();
        }
        headers.putValue("Bundle-SymbolicName", BINDING);
        headers.putValue("Fragment-Host", BSN);
        try (var jar = new JarOutputStream(Files.newOutputStream(plugins.resolve(BINDING + '_' + V + ".jar")), manifest)) {
            jar.finish();
        }
        feature(COMPONENT, MEMBER + MEMBER.replace(BSN, BINDING));
        feature(AGGREGATE, "<includes id='" + COMPONENT + "' version='" + V + "'/>");
        Files.writeString(repo.resolve("content.xml"), "<repository><units>" + UNIT + UNIT.replace(BSN, BINDING) + group(COMPONENT, REQUIRED + required(BINDING))
                + group(AGGREGATE, required(COMPONENT + ".feature.group")) + "</units></repository>");
        Files.writeString(repo.resolve("artifacts.xml"), "<repository><artifacts>" + KEY + KEY.replace(BSN, BINDING)
                + featureKey(COMPONENT) + featureKey(AGGREGATE) + "</artifacts></repository>");
    }

    private static String required(String member) {
        return "<required namespace='org.eclipse.equinox.p2.iu' name='" + member + "' range='[" + V + "," + V + "]'/>";
    }

    private static String group(String id, String requirement) {
        return "<unit id='" + id + ".feature.group' version='" + V + "'><requires>" + requirement + "</requires></unit>";
    }

    private static String featureKey(String id) {
        return "<artifact classifier='org.eclipse.update.feature' id='" + id + "' version='" + V + "'/>";
    }

    private void feature(String id, String members) throws IOException {
        try (var jar = new JarOutputStream(Files.newOutputStream(repo.resolve("features/" + id + '_' + V + ".jar")))) {
            jar.putNextEntry(new JarEntry("feature.xml"));
            jar.write(("<feature id='" + id + "' version='" + V + "'>" + members + "</feature>").getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }
}
