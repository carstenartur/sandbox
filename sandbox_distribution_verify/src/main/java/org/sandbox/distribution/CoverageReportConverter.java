/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.distribution;

import static org.sandbox.distribution.AggregateInstallationEvidence.children;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Element;

/** Validates measured JaCoCo source lines, retains Cobertura evidence, and enforces the coverage minimum. */
public final class CoverageReportConverter {
    private static final BigDecimal MINIMUM_LINE_COVERAGE = new BigDecimal("0.63");

    private CoverageReportConverter() { }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        LineCoverage coverage = convert(root, root.resolve("sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml"),
                root.resolve("target/github-coverage/cobertura.xml"));
        if (BigDecimal.valueOf(coverage.covered()).compareTo(
                BigDecimal.valueOf(coverage.total()).multiply(MINIMUM_LINE_COVERAGE)) < 0)
            throw new IOException("JaCoCo LINE coverage " + coverage.covered() + "/" + coverage.total()
                    + " is below the required 63%");
        System.out.println("Verified JaCoCo LINE coverage minimum: 63%");
    }

    record LineCoverage(long covered, long total) { }

    static LineCoverage convert(Path root, Path input, Path output) throws Exception {
        root = root.toAbsolutePath().normalize();
        Files.deleteIfExists(output);
        Path checkout = root;
        List<Path> trackedSources = Arrays.stream(Files.readString(root.resolve("target/github-coverage/sources.list"))
                .split("\u0000")).filter(s -> !s.isEmpty()).map(checkout::resolve)
                .map(Path::normalize).toList();
        if (trackedSources.stream().anyMatch(p -> !p.startsWith(checkout)))
            throw new IOException("Source outside checkout");
        Element report = read(input);
        if (!"report".equals(report.getTagName())) throw new IOException("Not a JaCoCo report");
        Map<String, Path> modules = new HashMap<>();
        try (var directories = Files.list(root)) {
            for (Path module : directories.filter(Files::isDirectory).toList()) {
                Path pom = module.resolve("pom.xml");
                if (!Files.isRegularFile(pom)) continue;
                List<Element> ids = children(read(pom), "artifactId");
                if (ids.size() != 1 || modules.put(ids.getFirst().getTextContent(), module) != null)
                    throw new IOException("Ambiguous module identity: " + pom);
            }
        }
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element coverage = document.createElement("coverage");
        document.appendChild(coverage);
        Element counter = children(report, "counter").stream()
                .filter(c -> "LINE".equals(c.getAttribute("type"))).findFirst()
                .orElseThrow(() -> new IOException("Missing JaCoCo LINE counter"));
        long expectedCovered = number(counter, "covered");
        long expectedTotal = expectedCovered + number(counter, "missed");
        coverage.setAttribute("lines-covered", Long.toString(expectedCovered));
        coverage.setAttribute("lines-valid", Long.toString(expectedTotal));
        copyRates(report, coverage);
        append(append(coverage, "sources"), "source").setTextContent(".");
        Element packages = append(coverage, "packages");
        long total = 0;
        long covered = 0;
        var emittedFiles = new HashSet<Path>();
        for (Element group : children(report, "group")) {
            Path module = modules.get(group.getAttribute("name"));
            if (module == null) throw new IOException("Unknown JaCoCo module: " + group.getAttribute("name"));
            List<Path> sources = trackedSources.stream().filter(p -> p.startsWith(module))
                    .filter(Files::isRegularFile).toList();
            for (Element pkg : children(group, "package")) {
                Element targetPackage = append(packages, "package");
                targetPackage.setAttribute("name", group.getAttribute("name") + "/" + pkg.getAttribute("name"));
                copyRates(pkg, targetPackage);
                Element classes = append(targetPackage, "classes");
                for (Element file : children(pkg, "sourcefile")) {
                    Path suffix = Path.of(pkg.getAttribute("name")).resolve(file.getAttribute("name"));
                    List<Path> matches = sources.stream().filter(p -> p.endsWith(suffix)).toList();
                    if (matches.size() != 1 || !emittedFiles.add(matches.getFirst()))
                        throw new IOException("Missing, ambiguous or duplicate coverage source: " + module + "/" + suffix);
                    String filename = root.relativize(matches.getFirst()).toString().replace('\\', '/');
                    Element targetClass = append(classes, "class");
                    targetClass.setAttribute("name", filename);
                    targetClass.setAttribute("filename", filename);
                    copyRates(file, targetClass);
                    Element lines = append(targetClass, "lines");
                    var numbers = new HashSet<Long>();
                    for (Element line : children(file, "line")) {
                        long nr = number(line, "nr");
                        if (nr == 0 || !numbers.add(nr)) throw new IOException("Invalid or duplicate source line: " + filename);
                        boolean hit = number(line, "ci") > 0;
                        Element targetLine = append(lines, "line");
                        targetLine.setAttribute("number", Long.toString(nr));
                        targetLine.setAttribute("hits", hit ? "1" : "0");
                        total++;
                        if (hit) covered++;
                    }
                }
            }
        }
        if (total == 0 || total != expectedTotal || covered != expectedCovered)
            throw new IOException("Transferred source lines differ from the JaCoCo LINE counter");
        Path parent = output.toAbsolutePath().getParent();
        if (parent == null) throw new IOException("Output has no parent directory");
        Files.createDirectories(parent);
        var factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        factory.newTransformer().transform(new DOMSource(document), new StreamResult(output.toFile()));
        System.out.println("Transferred JaCoCo coverage: " + covered + "/" + total + " source lines");
        return new LineCoverage(covered, total);
    }

    private static Element append(Element parent, String name) {
        Element child = parent.getOwnerDocument().createElement(name);
        parent.appendChild(child);
        return child;
    }

    private static void copyRates(Element source, Element target) throws IOException {
        for (Element counter : children(source, "counter")) {
            String type = counter.getAttribute("type");
            if (!"LINE".equals(type) && !"BRANCH".equals(type)) continue;
            long covered = number(counter, "covered");
            long total = covered + number(counter, "missed");
            target.setAttribute("LINE".equals(type) ? "line-rate" : "branch-rate",
                    total == 0 ? "0" : Double.toString((double) covered / total));
        }
    }

    private static long number(Element element, String attribute) throws IOException {
        try {
            long value = Long.parseLong(element.getAttribute(attribute));
            if (value >= 0) return value;
        } catch (NumberFormatException ignored) { }
        throw new IOException("Invalid JaCoCo count: " + attribute);
    }

    private static Element read(Path path) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        try (var stream = Files.newInputStream(path)) {
            return factory.newDocumentBuilder().parse(stream).getDocumentElement();
        }
    }
}
