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
