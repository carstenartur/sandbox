/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Element;

class AggregateProvisioningContractTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @CsvSource({"Linux,amd64,linux,gtk,x86_64", "Linux,x86_64,linux,gtk,x86_64",
            "Linux,aarch64,linux,gtk,aarch64", "Linux,arm64,linux,gtk,aarch64",
            "Mac OS X,x86_64,macosx,cocoa,x86_64", "Mac OS X,aarch64,macosx,cocoa,aarch64",
            "Mac OS X,arm64,macosx,cocoa,aarch64", "Windows 11,amd64,win32,win32,x86_64",
            "Windows 11,arm64,win32,win32,aarch64", "LINUX,AARCH64,linux,gtk,aarch64"})
    void mapsKnownPlatforms(String os, String arch, String p2os, String ws, String p2arch) throws Exception {
        assertEquals(List.of("-p2.os", p2os, "-p2.ws", ws, "-p2.arch", p2arch),
                AggregateInstallationVerifier.platformArguments(os, arch));
    }

    @ParameterizedTest
    @CsvSource({"FreeBSD,amd64", "Linux,ppc64le", "Linux,''", "'',amd64"})
    void rejectsUnknownPlatformsInsteadOfGuessing(String os, String arch) {
        assertThrows(Exception.class, () -> AggregateInstallationVerifier.platformArguments(os, arch));
    }

    @Test
    void provisionsFromTargetLocationsRatherThanMavenRepositories() throws Exception {
        Files.writeString(temporary.resolve("pom.xml"), "<project><repositories><repository><layout>p2</layout><url>https://example.invalid/pom-only/</url></repository></repositories></project>");
        Path target = temporary.resolve("sandbox_target/eclipse.target");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "<target><locations><location><repository location='https://example.invalid/release/'/></location><location><repository location='https://example.invalid/maven-osgi/'/></location><location><repository location='https://example.invalid/swtbot/'/></location></locations></target>");
        assertEquals(List.of("https://example.invalid/release/", "https://example.invalid/maven-osgi/", "https://example.invalid/swtbot/"),
                AggregateInstallationVerifier.provisioningRepositories(temporary));
    }

    @Test
    void missingTargetCannotFallBackToMavenRepositories() throws Exception {
        Files.writeString(temporary.resolve("pom.xml"), "<project><repositories><repository><layout>p2</layout><url>https://example.invalid/pom-only/</url></repository></repositories></project>");
        assertThrows(Exception.class, () -> AggregateInstallationVerifier.provisioningRepositories(temporary));
    }

    @Test
    void committedPdeLaunchIncludesAggregateInBothFeatureSelections() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("sandbox_product/sandbox.product.launch"))) root = root.getParent();
        assertTrue(root != null, "Cannot locate the product launch descriptor");
        Element launch;
        try (var input = Files.newInputStream(root.resolve("sandbox_product/sandbox.product.launch"))) {
            launch = AggregateInstallationEvidence.xml(input);
        }
        Set<String> checked = new HashSet<>();
        for (Element group : AggregateInstallationEvidence.children(launch, "setAttribute")) {
            String key = group.getAttribute("key");
            if (Set.of("selected_features", "root_features").contains(key)) {
                assertTrue(checked.add(key), "Duplicate feature selection " + key);
                String required = "sandbox_feature" + (key.equals("selected_features") ? ":default" : "");
                assertEquals(1, AggregateInstallationEvidence.children(group, "setEntry").stream()
                        .filter(entry -> required.equals(entry.getAttribute("value"))).count(), key);
            }
        }
        assertEquals(Set.of("selected_features", "root_features"), checked);
    }
}
