/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.oomph;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Configuration contract; native provisioning remains an independent required test. */
class OomphTargetBootstrapTest {
    private static final String BOOTSTRAP = "sandbox.target.project";
    private static final String CLONE = "${git.clone.sandbox.location}";
    private final Path module = Path.of("").toAbsolutePath();

    @Test
    void bootstrapImportsOnlyTheNonJavaTargetProject() throws Exception {
        Element task = task(tasks(), BOOTSTRAP);
        assertEquals("projects:ProjectsImportTask", type(task));
        assertEquals(Set.of("git.clone.sandbox"), predecessors(task));
        var locators = task.getElementsByTagName("sourceLocator");
        assertEquals(1, locators.getLength());
        var locator = (Element) locators.item(0);
        assertEquals(CLONE + "/sandbox_target", locator.getAttribute("rootFolder"));
        assertNotEquals("true", locator.getAttribute("locateNestedProjects"));
        var project = xml(module.resolve("../sandbox_target/.project"));
        var natures = project.getElementsByTagName("nature");
        Set<String> names = new HashSet<>();
        for (int index = 0; index < natures.getLength(); index++) {
            names.add(natures.item(index).getTextContent());
        }
        assertFalse(names.contains("org.eclipse.jdt.core.javanature"),
                "The bootstrap must not import a Java project against the running host");
        assertTrue(names.contains("org.eclipse.m2e.core.maven2Nature"));
    }

    @Test
    void mavenConsumersWaitForTheActiveTarget() throws Exception {
        Map<String, Element> tasks = tasks();
        Element target = task(tasks, "sandbox.target");
        assertEquals("pde:TargetPlatformTask", type(target));
        assertEquals("target platform for sandbox", target.getAttribute("name"));
        assertNotEquals("false", target.getAttribute("activate"));
        assertEquals(Set.of(BOOTSTRAP), predecessors(target));
        assertTrue(predecessors(task(tasks, "sandbox.maven")).contains("sandbox.target"));
    }

    @Test
    void publicWorkspaceEnablesAutomaticResourceRefreshForGeneratedMavenManifests() throws Exception {
        // The optional Sandbox product already opts in. The actual public
        // Github Projects catalog must do so too, otherwise m2e manually
        // touches target/classes/META-INF/MANIFEST.MF while bnd replaces it.
        Document publicProject = xml(module.resolve("sandboxproject.setup"));
        int matches = 0;
        var tasks = publicProject.getElementsByTagName("setupTask");
        for (int i = 0; i < tasks.getLength(); i++) {
            Element task = (Element) tasks.item(i);
            if ("/instance/org.eclipse.core.resources/refresh.enabled".equals(task.getAttribute("key"))) {
                assertEquals("setup:PreferenceTask", type(task));
                assertEquals("true", task.getAttribute("value"),
                        "m2e must not manually touch transient generated bnd manifests");
                matches++;
            }
        }
        assertEquals(1, matches, "The public project must define refresh consistently exactly once");
    }

    @Test
    void activeTargetProvidesTheLanguageServerForImportedSources() throws Exception {
        Document target = xml(module.resolve("../sandbox_target/eclipse.target"));
        var repositories = target.getElementsByTagName("repository");
        Element languageServer = null;
        for (int index = 0; index < repositories.getLength(); index++) {
            Element repository = (Element) repositories.item(index);
            if ("https://download.eclipse.org/jdtls/milestones/1.62.0/repository/"
                    .equals(repository.getAttribute("location"))) {
                assertNull(languageServer, "Do not duplicate the pinned language-server target location");
                languageServer = (Element) repository.getParentNode();
            }
        }
        assertNotNull(languageServer,
                "A module-local Maven repository cannot supply the active Oomph/PDE target");
        Set<String> units = new HashSet<>();
        var nodes = languageServer.getElementsByTagName("unit");
        for (int index = 0; index < nodes.getLength(); index++) {
            units.add(((Element) nodes.item(index)).getAttribute("id"));
        }
        assertTrue(units.containsAll(Set.of("org.eclipse.jdt.ls.core", "org.eclipse.m2e.core",
                "org.eclipse.m2e.maven.runtime", "org.eclipse.m2e.jdt", "org.eclipse.buildship.core")),
                "The adapter and its standard-server test runtime must resolve in the contributor workspace");
    }

    @Test
    void completeProjectImportStillFollowsMaven() throws Exception {
        Map<String, Element> tasks = tasks();
        assertEquals(Set.of("sandbox.maven"), predecessors(task(tasks, "sandbox.projects")));
        Element maven = task(tasks, "sandbox.maven");
        assertEquals("maven:MavenImportTask", type(maven));
        var locators = maven.getElementsByTagName("sourceLocator");
        assertEquals(2, locators.getLength());
        assertEquals(CLONE, ((Element) locators.item(0)).getAttribute("rootFolder"));
        assertEquals(CLONE + "/sandbox_oomph", ((Element) locators.item(1)).getAttribute("rootFolder"));
        assertFalse(maven.hasAttribute("excludedTriggers"));
    }

    @Test
    void buildWaitsForBothTheTargetAndCompleteProjectImport() throws Exception {
        var nodes = xml(module.resolve("sandboxproject.setup")).getElementsByTagName("setupTask");
        int count = 0;
        for (int index = 0; index < nodes.getLength(); index++) {
            Element task = (Element) nodes.item(index);
            if ("projects:ProjectsBuildTask".equals(type(task))) {
                count++;
                assertTrue(predecessors(task).containsAll(Set.of("sandbox.projects", "sandbox.target")));
                assertEquals("true", task.getAttribute("onlyNewProjects"));
            }
        }
        assertEquals(1, count);
    }

    @Test
    void explicitDependenciesHaveNoMissingTasksOrCycles() throws Exception {
        Map<String, Element> tasks = tasks();
        for (String id : tasks.keySet()) {
            visit(tasks, id, new HashSet<>(), new HashSet<>());
        }
    }

    @Test
    void officialCatalogProvidesExtensionSchemasForColdInstaller() throws Exception {
        // The public Github Projects catalog loads this model before m2e, PDE and
        // the corresponding Oomph setup extensions have been provisioned.
        // Without schemaLocation, those xsi:type values can be unresolved even
        // though the full-SDK workspace acceptance test passes.
        Document document = xml(module.resolve("sandboxproject.setup"));
        Element project = document.getDocumentElement();
        String schemaLocation = project.getAttributeNS(
                XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "schemaLocation");
        assertFalse(schemaLocation.isBlank(), "The public setup must be loadable by a fresh installer");

        String[] tokens = schemaLocation.trim().split("\\s+");
        assertEquals(0, tokens.length % 2, "schemaLocation must contain namespace/URI pairs");
        Map<String, String> schemas = new LinkedHashMap<>();
        for (int i = 0; i < tokens.length; i += 2) {
            assertNull(schemas.put(tokens[i], tokens[i + 1]), "Duplicate schema for " + tokens[i]);
        }

        Map<String, String> extensionModels = Map.of(
                "git", "Git.ecore",
                "maven", "Maven.ecore",
                "jdt", "JDT.ecore",
                "pde", "PDE.ecore",
                "predicates", "Predicates.ecore",
                "projects", "Projects.ecore",
                "setup.workingsets", "SetupWorkingSets.ecore");
        String modelRoot = "https://raw.githubusercontent.com/eclipse-oomph/oomph/master/setups/models/";
        for (var entry : extensionModels.entrySet()) {
            String namespace = project.lookupNamespaceURI(entry.getKey());
            assertNotNull(namespace, "Missing namespace declaration: " + entry.getKey());
            assertEquals(modelRoot + entry.getValue(), schemas.get(namespace),
                    "Missing canonical Ecore model for " + entry.getKey());
        }

        var elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String type = element.getAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type");
            if (type.isEmpty()) {
                continue;
            }
            int colon = type.indexOf(':');
            assertTrue(colon > 0, "Unqualified Oomph model type: " + type);
            String prefix = type.substring(0, colon);
            if (!Set.of("setup", "setup.p2").contains(prefix)) {
                assertTrue(schemas.containsKey(element.lookupNamespaceURI(prefix)),
                        "Cold installer cannot resolve extension type " + type);
            }
        }
    }

    @Test
    void officialCatalogIdentityAndCloneRemainUnchanged() throws Exception {
        Document setup = xml(module.resolve("sandboxproject.setup"));
        assertEquals("sandbox", setup.getDocumentElement().getAttribute("name"));
        assertEquals("main", ((Element) setup.getElementsByTagName("stream").item(0)).getAttribute("name"));
        Element clone = task(tasks(), "git.clone.sandbox");
        assertEquals("https://github.com/carstenartur/sandbox.git", clone.getAttribute("remoteURI"));
        assertEquals("main", clone.getAttribute("checkoutBranch"));
    }

    private Map<String, Element> tasks() throws Exception {
        Map<String, Element> result = new LinkedHashMap<>();
        var nodes = xml(module.resolve("sandboxproject.setup")).getElementsByTagName("setupTask");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element task = (Element) nodes.item(index);
            String id = task.getAttribute("id");
            if (!id.isEmpty()) assertNull(result.put(id, task), "Duplicate task: " + id);
        }
        return result;
    }

    private static Element task(Map<String, Element> tasks, String id) {
        Element task = tasks.get(id);
        assertNotNull(task, "Missing task: " + id);
        return task;
    }

    private static String type(Element task) {
        return task.getAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type");
    }

    private static Set<String> predecessors(Element task) {
        String value = task.getAttribute("predecessors").trim();
        return value.isEmpty() ? Set.of() : Set.copyOf(Arrays.asList(value.split("\\s+")));
    }

    private static void visit(Map<String, Element> tasks, String id, Set<String> active, Set<String> visited) {
        if (visited.contains(id)) return;
        assertTrue(active.add(id), "Cyclic setup dependency at " + id);
        for (String predecessor : predecessors(task(tasks, id))) visit(tasks, predecessor, active, visited);
        active.remove(id);
        visited.add(id);
    }

    private static Document xml(Path path) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(path.toFile());
    }
}
