/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXParseException;

class AggregateInstallationEvidenceTest {
    @TempDir Path temporary;
    private static final String AGGREGATE = "sandbox_feature.feature.group";
    private static final String COMPONENT = "sandbox_tools_feature.feature.group";

    @Test
    void readsActualProfileShapeAndKeepsOnlyMarkedRoots() throws Exception {
        var profile = read(units(), properties(AGGREGATE, "true") + properties(COMPONENT, "false"));
        assertEquals(Set.of(AGGREGATE), profile.roots());
        AggregateInstallationEvidence.requireFeatures(profile, expected(), Set.of(AGGREGATE));
    }

    @Test
    void readsCompressedP2Profiles() throws Exception {
        Path file = temporary.resolve("1.profile.gz");
        try (var output = new GZIPOutputStream(Files.newOutputStream(file))) {
            output.write(xml(units(), properties(AGGREGATE, "true")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        AggregateInstallationEvidence.requireFeatures(AggregateInstallationEvidence.read(file), expected(), Set.of(AGGREGATE));
    }

    @Test
    void rejectsMissingOrStaleComponentVersions() throws Exception {
        for (String broken : new String[] {units().replace(unit(COMPONENT, "1.3.5"), ""),
                units().replace(unit(COMPONENT, "1.3.5"), unit(COMPONENT, "1.3.4"))}) {
            var profile = read(broken, properties(AGGREGATE, "true"));
            assertThrows(IOException.class, () -> AggregateInstallationEvidence.requireFeatures(profile, expected(), Set.of(AGGREGATE)));
        }
    }

    @Test
    void rejectsDuplicateFeatureVersionsAndUnexpectedFeature() throws Exception {
        for (String extra : new String[] {unit(COMPONENT, "1.3.4"), unit("sandbox_hidden_feature.feature.group", "1.3.5")}) {
            var profile = read(units() + extra, properties(AGGREGATE, "true"));
            assertThrows(IOException.class, () -> AggregateInstallationEvidence.requireFeatures(profile, expected(), Set.of(AGGREGATE)));
        }
    }

    @Test
    void rejectsMissingRootAndIndividuallyRootedComponents() throws Exception {
        for (String roots : new String[] {"", properties(AGGREGATE, "true") + properties(COMPONENT, "true")}) {
            var profile = read(units(), roots);
            assertThrows(IOException.class, () -> AggregateInstallationEvidence.requireFeatures(profile, expected(), Set.of(AGGREGATE)));
        }
    }

    @Test
    void preservesLegacyRootsOnlyWhenExplicitlyExpected() throws Exception {
        var profile = read(units(), properties(AGGREGATE, "true") + properties(COMPONENT, "true"));
        AggregateInstallationEvidence.requireFeatures(profile, expected(), Set.of(AGGREGATE, COMPONENT));
        assertThrows(IOException.class, () -> AggregateInstallationEvidence.requireFeatures(profile, expected(), Set.of(AGGREGATE)));
    }

    @Test
    void rejectsRootMarkerForAnUninstalledVersion() {
        assertThrows(IOException.class, () -> read(units(), properties(AGGREGATE, "true").replace("1.3.5", "1.3.4")));
    }

    @Test
    void allowsParallelNonSandboxDependencyVersions() throws Exception {
        var profile = read(units() + unit("junit-jupiter-api", "5.14.4") + unit("junit-jupiter-api", "6.1.3"), properties(AGGREGATE, "true"));
        AggregateInstallationEvidence.requireFeatures(profile, expected(), Set.of(AGGREGATE));
    }

    @Test
    void rejectsTestBundlesInTheEndUserInstallation() throws Exception {
        var profile = read(units() + unit("sandbox_tools_test", "1.3.5"), properties(AGGREGATE, "true"));
        assertThrows(IOException.class, () -> AggregateInstallationEvidence.requireFeatures(profile, expected(), Set.of(AGGREGATE)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "<!DOCTYPE profile>",
            "<!DOCTYPE profile [<!ENTITY value 'expanded'>]>",
            "<!DOCTYPE profile [<!ENTITY % definitions \"<!ELEMENT profile ANY>\">%definitions;]>" })
    void rejectsDoctypeBeforeProcessingRepositoryEntities(String declaration) throws Exception {
        byte[] input = (declaration + "<profile/>").getBytes(StandardCharsets.UTF_8);
        try (var stream = new ByteArrayInputStream(input)) {
            assertThrows(SAXParseException.class, () -> AggregateInstallationEvidence.xml(stream));
        }
    }

    @Test
    void readsFileNamesFromAbsoluteAndRelativeEvidencePaths() {
        assertEquals("metadata.jar", AggregateInstallationEvidence.fileName(temporary.resolve("metadata.jar")));
        assertEquals("metadata.jar", AggregateInstallationEvidence.fileName(Path.of("metadata.jar")));
    }

    @Test
    void rejectsRootWithoutAFileName() {
        Path root = temporary.toAbsolutePath().getRoot();
        var failure = assertThrows(IllegalArgumentException.class, () -> AggregateInstallationEvidence.fileName(root));
        assertEquals("Expected a file name: " + root, failure.getMessage());
    }

    @Test
    void createsOnlyParentDirectoriesAndAllowsExistingParents() throws Exception {
        Path file = temporary.resolve("nested/evidence/feature.xml");
        AggregateInstallationEvidence.createParentDirectories(file);
        AggregateInstallationEvidence.createParentDirectories(file);
        assertTrue(Files.isDirectory(temporary.resolve("nested/evidence")));
        assertFalse(Files.exists(file));
    }

    @Test
    void rejectsEvidencePathsWithoutParentDirectories() {
        for (Path file : new Path[] { Path.of("feature.xml"), temporary.toAbsolutePath().getRoot() }) {
            var failure = assertThrows(IOException.class, () -> AggregateInstallationEvidence.createParentDirectories(file));
            assertEquals("Expected a parent directory: " + file, failure.getMessage());
        }
    }

    @Test
    void parentCreationErrorsRemainFailuresWithoutChangingExistingFiles() throws Exception {
        Path existing = temporary.resolve("not-a-directory");
        Files.writeString(existing, "preserve");
        assertThrows(IOException.class, () -> AggregateInstallationEvidence.createParentDirectories(existing.resolve("feature.xml")));
        assertEquals("preserve", Files.readString(existing));
    }

    private AggregateInstallationEvidence.Profile read(String units, String roots) throws Exception {
        Path file = temporary.resolve("1.profile");
        Files.writeString(file, xml(units, roots));
        return AggregateInstallationEvidence.read(file);
    }

    private static Map<String, String> expected() {
        return Map.of(AGGREGATE, "1.3.5", COMPONENT, "1.3.5");
    }

    private static String units() { return unit(AGGREGATE, "1.3.5") + unit(COMPONENT, "1.3.5"); }
    private static String unit(String id, String version) { return "<unit id='" + id + "' version='" + version + "'/>"; }
    private static String xml(String units, String roots) {
        return "<profile><units>" + units + "</units><iusProperties>" + roots + "</iusProperties></profile>";
    }
    private static String properties(String id, String root) {
        return "<iuProperties id='" + id + "' version='1.3.5'><properties><property name='org.eclipse.equinox.p2.type.root' value='" + root + "'/></properties></iuProperties>";
    }
}
