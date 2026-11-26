/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.oomph.probe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/** Expected project names for the public project's Eclipse and Maven source locators. */
final class PublicProjectInventory {
    private static final List<Path> EXCLUDED = List.of(Path.of(".git"), Path.of(".github"),
            Path.of("target"), Path.of("sandbox_oomph/target"));

    private PublicProjectInventory() {
    }

    @FunctionalInterface
    interface ProjectDescriptionReader {
        String read(Path path) throws Exception;
    }

    static Set<String> expectedProjects(Path clone, Collection<Path> committedDescriptions,
            ProjectDescriptionReader readDescription) throws Exception {
        Set<String> names = new TreeSet<>();
        for (Path path : committedDescriptions) {
            if (!excluded(path)) {
                names.add(readDescription.read(path));
            }
        }

        var factory = DocumentBuilderFactory.newDefaultNSInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder xml = factory.newDocumentBuilder();

        Path root = clone.toRealPath();
        // The first Maven source locator traverses the root POM and its modules.
        addModules(root, root, xml, new HashSet<>(), names);
        // The second locator imports the standalone verifier without traversing its modules.
        Path verifier = root.resolve("sandbox_oomph");
        names.add(projectName(artifactId(readPom(xml, verifier), verifier), verifier));
        return names;
    }

    private static void addModules(Path root, Path directory, DocumentBuilder xml, Set<Path> visited,
            Set<String> names) throws IOException, SAXException {
        if (!directory.startsWith(root) || !Files.isRegularFile(directory.resolve("pom.xml"))) {
            return;
        }
        Path canonicalDirectory = directory.toRealPath();
        if (!canonicalDirectory.startsWith(root) || !visited.add(canonicalDirectory)) {
            return;
        }
        Element model = readPom(xml, canonicalDirectory);
        String artifactId = artifactId(model, canonicalDirectory);
        // Oomph applies these predicates to each candidate, then still visits its modules.
        if (!artifactId.equals("central") && !excluded(root.relativize(canonicalDirectory))) {
            names.add(projectName(artifactId, canonicalDirectory));
        }

        List<Element> declarations = children(model, "modules");
        // m2e's LocalProjectScanner discovers modules in every raw profile, regardless of activation.
        for (Element profiles : children(model, "profiles")) {
            for (Element profile : children(profiles, "profile")) {
                declarations.addAll(children(profile, "modules"));
            }
        }
        for (Element modules : declarations) {
            for (Element module : children(modules, "module")) {
                String path = module.getTextContent().strip();
                if (path.endsWith("/pom.xml")) {
                    path = path.substring(0, path.length() - "/pom.xml".length());
                }
                addModules(root, canonicalDirectory.resolve(path).normalize(), xml, visited, names);
            }
        }
    }

    private static Element readPom(DocumentBuilder xml, Path directory) throws IOException, SAXException {
        return xml.parse(directory.resolve("pom.xml").toFile()).getDocumentElement();
    }

    private static String artifactId(Element model, Path directory) throws IOException {
        return children(model, "artifactId").stream().findFirst().map(Element::getTextContent).map(String::strip)
                .filter(id -> !id.isEmpty()).orElseThrow(() -> new IOException("No artifactId in " + directory.resolve("pom.xml")));
    }

    private static String projectName(String artifactId, Path directory) {
        // The default m2e naming template strips unresolved placeholders and falls back to the directory name.
        String name = artifactId.replaceAll("\\$\\{[^}]+}", "");
        return name.isEmpty() ? directory.getFileName().toString() : name;
    }

    private static boolean excluded(Path relative) {
        return EXCLUDED.stream().anyMatch(relative::startsWith);
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> children = new ArrayList<>();
        for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getLocalName())) {
                children.add(element);
            }
        }
        return children;
    }
}
