/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Prevents new end-user features from bypassing the single installation IU. */
public class SandboxAggregateFeatureTest {
    private static final String AGGREGATE = "sandbox_feature";

    @Test
    public void aggregateIncludesEveryComponentExactlyOnce() throws Exception {
        Path root = root();
        Set<String> components = components(root);
        assertFalse(components.isEmpty());
        Element aggregate = aggregate(root);
        assertEquals(AGGREGATE, aggregate.getAttribute("id"));
        assertEquals(components, ids(aggregate, "includes"));
        for (Element include : children(aggregate, "includes")) {
            assertEquals("0.0.0", include.getAttribute("version"), include.getAttribute("id"));
            assertFalse(Boolean.parseBoolean(include.getAttribute("optional")), include.getAttribute("id"));
            for (String filter : List.of("os", "ws", "arch", "nl")) {
                assertFalse(include.hasAttribute(filter), "The aggregate must not filter out a component");
            }
        }
        assertTrue(children(aggregate, "plugin").isEmpty(), "Keep plug-in ownership in component features");
        assertTrue(children(aggregate, "requires").isEmpty(), "The aggregate only includes components");
    }

    @Test
    public void productAndBothRepositoriesRetainAllComponentsAndPublishAggregate() throws Exception {
        Path root = root();
        Set<String> expected = new LinkedHashSet<>(components(root));
        expected.add(AGGREGATE);
        for (String directory : List.of("sandbox_product", "sandbox_updatesite")) {
            assertEquals(expected, sandboxIds(xml(root.resolve(directory + "/category.xml")), "feature"));
            Element dependencies = children(xml(root.resolve(directory + "/pom.xml")), "dependencies").getFirst();
            Set<String> actual = new LinkedHashSet<>();
            for (Element dependency : children(dependencies, "dependency")) {
                String artifact = text(dependency, "artifactId");
                if (expected.contains(artifact)) {
                    assertTrue(actual.add(artifact), "Duplicate delivery dependency " + artifact);
                }
            }
            assertEquals(expected, actual, directory + " must build the complete feature graph");
        }
        Element product = xml(root.resolve("sandbox_product/sandbox.product"));
        assertEquals(expected, sandboxIds(children(product, "features").getFirst(), "feature"));
        Element pom = xml(root.resolve("pom.xml"));
        Set<String> modules = new LinkedHashSet<>();
        for (Element module : children(children(pom, "modules").getFirst(), "module")) {
            modules.add(module.getTextContent().strip());
        }
        assertTrue(modules.containsAll(expected), "Every normal feature must participate in the root reactor");
    }

    @Test
    public void componentsRemainIndependentOfTheAggregate() throws Exception {
        Path root = root();
        for (String component : components(root)) {
            Element feature = xml(root.resolve(component + "/feature.xml"));
            assertFalse(ids(feature, "includes").contains(AGGREGATE), component);
            for (Element requires : children(feature, "requires")) {
                for (Element requirement : children(requires, "import")) {
                    assertFalse(AGGREGATE.equals(requirement.getAttribute("feature")), component);
                }
            }
        }
    }

    @Test
    public void aggregateUsesTheNormalFeatureVersionAndPdeBuild() throws Exception {
        Path root = root();
        Element pom = xml(root.resolve(AGGREGATE + "/pom.xml"));
        assertEquals("eclipse-feature", text(pom, "packaging"));
        assertEquals(AGGREGATE, text(pom, "artifactId"));
        String version = text(xml(root.resolve("pom.xml")), "version");
        assertEquals(version, text(children(pom, "parent").getFirst(), "version"));
        assertEquals(version.replace("-SNAPSHOT", ".qualifier"), aggregate(root).getAttribute("version"));
        String build = Files.readString(root.resolve(AGGREGATE + "/build.properties"));
        assertTrue(build.contains("feature.xml"));
        assertTrue(build.contains("feature.properties"));
        assertTrue(Files.isRegularFile(root.resolve(AGGREGATE + "/feature.properties")));
    }

    private static Element aggregate(Path root) throws Exception {
        Path file = root.resolve(AGGREGATE + "/feature.xml");
        assertTrue(Files.isRegularFile(file), "Missing installable aggregate feature " + file);
        return xml(file);
    }

    private static Set<String> components(Path root) throws Exception {
        Set<String> result = new LinkedHashSet<>();
        try (var directories = Files.list(root)) {
            for (Path directory : directories.sorted().toList()) {
                String name = directory.getFileName().toString();
                Path feature = directory.resolve("feature.xml");
                if (!name.equals(AGGREGATE) && name.startsWith("sandbox_")
                        && name.endsWith("_feature") && Files.isRegularFile(feature)) {
                    assertEquals(name, xml(feature).getAttribute("id"));
                    result.add(name);
                }
            }
        }
        return result;
    }

    private static Set<String> sandboxIds(Element parent, String tag) {
        Set<String> result = ids(parent, tag);
        result.removeIf(id -> !id.startsWith("sandbox_"));
        return result;
    }

    private static Set<String> ids(Element parent, String tag) {
        Set<String> result = new LinkedHashSet<>();
        for (Element child : children(parent, tag)) {
            String id = child.getAttribute("id");
            assertFalse(id.isBlank(), "Missing id in " + tag);
            assertTrue(result.add(id), "Duplicate " + tag + ": " + id);
        }
        return result;
    }

    private static List<Element> children(Element parent, String name) {
        var result = new java.util.ArrayList<Element>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getTagName())) {
                result.add(element);
            }
        }
        return result;
    }

    private static String text(Element parent, String name) {
        return children(parent, name).getFirst().getTextContent().strip();
    }

    private static Element xml(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
    }

    private static Path root() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (path != null && !Files.isRegularFile(path.resolve("sandbox_updatesite/category.xml"))) {
            path = path.getParent();
        }
        if (path == null) {
            throw new IllegalStateException("Cannot find Sandbox root");
        }
        return path;
    }
}
