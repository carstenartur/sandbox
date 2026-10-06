/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Document;

/** Maven recipes must not activate PDE's unrelated plain-Bnd-workspace fallback. */
class MavenBndConfigurationTest {
    @ParameterizedTest(name = "{0}: explicit Maven recipe without a Bnd workspace")
    @CsvSource({
        "sandbox-ast-api, org.sandbox.ast.api",
        "sandbox-ast-api-jdt, org.sandbox.ast.api.jdt",
        "sandbox_common_core, sandbox_common_core",
        "sandbox-functional-converter-core, org.sandbox.functional.core",
        "sandbox-jgit-storage-hibernate, sandbox-jgit-storage-hibernate"
    })
    void mavenBundleInstructionsRemainExplicitAndResolvable(String module, String symbolicName) throws Exception {
        Path project = repositoryRoot().resolve(module);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document pom = factory.newDocumentBuilder().parse(project.resolve("pom.xml").toFile());
        var xpath = XPathFactory.newInstance().newXPath();
        String plugin = "/project/build/plugins/plugin[groupId='biz.aQute.bnd' and artifactId='bnd-maven-plugin']";
        assertEquals("1", xpath.evaluate("count(" + plugin + ")", pom), module);
        String recipe = xpath.evaluate(plugin + "/configuration/bndfile", pom).strip();
        assertEquals("maven-bundle.bnd", recipe, "Maven must select the renamed recipe explicitly");
        assertFalse(Files.exists(project.resolve("bnd.bnd")),
                "PDE interprets bnd.bnd without a generated model as a plain Bnd workspace project");
        assertEquals("1", xpath.evaluate("count(" + plugin
                + "/executions/execution/goals/goal[text()='bnd-process'])", pom),
                "The Maven manifest generation must not be removed");
        assertTrue(Files.isRegularFile(project.resolve(recipe)), "Configured recipe must exist");
        Properties instructions = new Properties();
        try (var reader = Files.newBufferedReader(project.resolve(recipe))) {
            instructions.load(reader);
        }
        assertEquals(symbolicName, instructions.getProperty("Bundle-SymbolicName"));
        assertNotNull(instructions.getProperty("Bundle-Version"), "Retain bundle version instructions");
        assertFalse(instructions.getProperty("Export-Package", "").isBlank(), "Retain the public packages");
        assertEquals("${project.build.outputDirectory}/META-INF/MANIFEST.MF", xpath.evaluate(
                "/project/build/plugins/plugin[artifactId='maven-jar-plugin']/configuration/archive/manifestFile", pom));
    }

    private static Path repositoryRoot() {
        for (Path path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("sandbox_common_core/pom.xml"))
                    && Files.isRegularFile(path.resolve("sandbox-ast-api/pom.xml"))) return path;
        }
        throw new IllegalStateException("Cannot find the Sandbox Maven projects");
    }
}
