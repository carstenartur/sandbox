/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
