/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.qa.pde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

class PdeSuiteReplayTest {
    @TempDir
    Path builds;

    @Test
    void originalSuiteRunsInDeployedFeature() throws Exception {
        verify("before", "junit4");
    }

    @Test
    void migratedSuiteRunsInDeployedFeature() throws Exception {
        verify("after", "junit5");
    }

    private void verify(String phase, String provider) throws Exception {
        String capture = System.getProperty("pde.capture");
        assertTrue(capture != null && !capture.isBlank(), "Set -Dpde.capture to the exported lifecycle test directory");
        Path source = Path.of(capture).toAbsolutePath().resolve(phase);
        Path project = Files.createDirectory(builds.resolve(phase));
        for (String module : List.of("bundle", "feature")) {
            Path input = source.resolve(module);
            try (var paths = Files.walk(input)) {
                for (Path file : paths.toList()) {
                    Path relative = input.relativize(file);
                    if (relative.startsWith("target") || relative.equals(Path.of("pom.xml"))) {
                        continue;
                    }
                    Files.copy(file, project.resolve(module).resolve(relative));
                }
            }
            installPom(module, project.resolve(module));
        }
        installPom("reactor", project);
        Files.createDirectory(project.resolve(".mvn"));

        String repository = System.getProperty("pde.repository");
        if (repository != null) {
            Path target = project.resolve("feature/test.target");
            String original = Files.readString(target);
            String location = "location=\"https://download.eclipse.org/releases/2026-09/\"";
            int index = original.indexOf(location);
            assertNotEquals(-1, index, "Expected the exported target repository");
            assertEquals(index, original.lastIndexOf(location), "Expected a single exported target repository");
            String escaped = java.net.URI.create(repository).toASCIIString().replace("&", "&amp;");
            Files.writeString(target, original.replace(location, "location=\"" + escaped + "\""));
        }

        Properties properties = new Properties();
        properties.setProperty("suite.provider", provider);
        var request = new DefaultInvocationRequest();
        request.setBaseDirectory(project.toFile());
        request.setBatchMode(true);
        request.setGoals(List.of("verify"));
        request.setProperties(properties);
        var result = new DefaultInvoker().setMavenHome(new File(System.getProperty("maven.home"))).execute(request);
        assertNull(result.getExecutionException());
        assertEquals(0, result.getExitCode(), "Tycho verify failed for " + phase);
        assertTrue(Files.isRegularFile(project.resolve("feature/target/probe.test.feature-1.0.0.jar")));
        assertSuite(project.resolve("bundle/target/surefire-reports"));
    }

    private static void installPom(String name, Path project) throws Exception {
        try (var input = Objects.requireNonNull(PdeSuiteReplayTest.class.getResourceAsStream("/tycho/" + name + ".xml"))) {
            Files.copy(input, project.resolve("pom.xml"));
        }
    }

    private static void assertSuite(Path reports) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        List<String> identities = new ArrayList<>();
        try (var files = Files.newDirectoryStream(reports, "TEST-*.xml")) {
            for (Path report : files) {
                var cases = factory.newDocumentBuilder().parse(report.toFile()).getElementsByTagName("testcase");
                for (int i = 0; i < cases.getLength(); i++) {
                    Element test = (Element) cases.item(i);
                    for (String failure : List.of("failure", "error", "skipped")) {
                        assertEquals(0, test.getElementsByTagName(failure).getLength(), report.toString());
                    }
                    identities.add(test.getAttribute("classname") + "#" + test.getAttribute("name"));
                }
            }
        }
        assertEquals(List.of("probe.ExampleTest#adds"), identities);
    }
}
