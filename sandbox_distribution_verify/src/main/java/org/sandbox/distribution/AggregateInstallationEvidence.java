/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.distribution;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Reads the actual p2 profile, not the presence of cached artifact files. */
final class AggregateInstallationEvidence {
    record Profile(Map<String, Set<String>> units, Set<String> roots) {
        Profile {
            Map<String, Set<String>> copy = new LinkedHashMap<>();
            units.forEach((id, versions) -> copy.put(id, Set.copyOf(versions)));
            units = Map.copyOf(copy);
            roots = Set.copyOf(roots);
        }
    }

    private AggregateInstallationEvidence() { }

    static Profile read(Path file) throws Exception {
        try (InputStream raw = Files.newInputStream(file);
                InputStream stream = file.toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw) {
            Element profile = xml(stream);
            require("profile".equals(profile.getTagName()), "Not a p2 profile: " + file);
            Map<String, Set<String>> units = new LinkedHashMap<>();
            for (Element group : children(profile, "units")) {
                for (Element unit : children(group, "unit")) {
                    String id = unit.getAttribute("id");
                    String version = unit.getAttribute("version");
                    require(!id.isBlank() && !version.isBlank(), "Incomplete installed IU");
                    require(units.computeIfAbsent(id, key -> new LinkedHashSet<>()).add(version), "Duplicate installed IU: " + id + '/' + version);
                }
            }
            Set<String> roots = new LinkedHashSet<>();
            for (Element group : children(profile, "iusProperties")) {
                for (Element unit : children(group, "iuProperties")) {
                    for (Element properties : children(unit, "properties")) {
                        for (Element property : children(properties, "property")) {
                            if ("org.eclipse.equinox.p2.type.root".equals(property.getAttribute("name"))
                                    && "true".equals(property.getAttribute("value"))) {
                                String id = unit.getAttribute("id");
                                require(units.getOrDefault(id, Set.of()).contains(unit.getAttribute("version")), "Root is not installed: " + id);
                                require(roots.add(id), "Duplicate root: " + id);
                            }
                        }
                    }
                }
            }
            return new Profile(units, roots);
        }
    }

    static void requireFeatures(Profile profile, Map<String, String> expected, Set<String> expectedRoots) throws IOException {
        Map<String, String> actual = new LinkedHashMap<>();
        for (var entry : profile.units().entrySet()) {
            String id = entry.getKey();
            if (id.startsWith("sandbox") && (id.endsWith("_test") || id.contains("-server"))) {
                throw new IOException("Non-end-user bundle installed: " + id);
            }
            if (isSandboxFeature(id)) {
                require(entry.getValue().size() == 1, "Multiple installed feature versions: " + entry);
                actual.put(id, entry.getValue().iterator().next());
            }
        }
        require(actual.equals(expected), "Installed features differ; expected " + expected + ", actual " + actual);
        Set<String> roots = new LinkedHashSet<>(profile.roots());
        roots.removeIf(id -> !id.startsWith("sandbox"));
        require(roots.equals(expectedRoots), "Sandbox roots differ; expected " + expectedRoots + ", actual " + roots);
    }

    static boolean isSandboxFeature(String id) {
        return id.startsWith("sandbox_") && id.endsWith(".feature.group");
    }

    static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getTagName())) result.add(element);
        }
        return result;
    }

    static Element xml(InputStream stream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(stream).getDocumentElement();
    }

    static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
