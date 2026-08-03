package org.sandbox.distribution;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Adds exact published feature identities and digests to the distribution
 * verification report consumed by the post-publication release gate.
 */
public final class PublishedFeatureEvidenceWriter {
    private final Path root;
    private final Path repository;
    private final Path report;

    private PublishedFeatureEvidenceWriter(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.repository = this.root.resolve("sandbox_updatesite/target/repository");
        this.report = this.root.resolve("target/distribution-verification/verification.json");
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        new PublishedFeatureEvidenceWriter(root).write();
    }

    private void write() throws Exception {
        require(Files.isRegularFile(report),
                "Distribution verification report does not exist: " + report);
        String existing = Files.readString(report, StandardCharsets.UTF_8);
        require(existing.contains("\"result\": \"PASS\""),
                "Distribution verification report does not record PASS");
        require(!existing.contains("\"repository\""),
                "Distribution verification report already contains repository evidence");

        List<FeatureEvidence> features = collectFeatureEvidence();
        require(!features.isEmpty(), "No published Sandbox feature evidence was generated");

        String stripped = existing.stripTrailing();
        require(stripped.endsWith("}"), "Distribution verification report is not a JSON object");
        String enriched = stripped.substring(0, stripped.length() - 1).stripTrailing()
                + ",\n" + renderRepository(features) + "\n}\n";
        Files.writeString(report, enriched, StandardCharsets.UTF_8);
        System.out.printf("Added exact evidence for %d published features to %s.%n",
                features.size(), report);
    }

    private List<FeatureEvidence> collectFeatureEvidence() throws Exception {
        require(Files.isDirectory(repository), "Built p2 repository does not exist: " + repository);

        Set<String> publishedFeatures = new TreeSet<>();
        for (Element feature : elements(parseXml(root.resolve("sandbox_updatesite/category.xml")), "feature")) {
            String id = feature.getAttribute("id");
            if (id.startsWith("sandbox_")) {
                require(publishedFeatures.add(id), "Duplicate published feature declaration: " + id);
            }
        }

        Map<String, Set<String>> iuVersions = new TreeMap<>();
        for (Element unit : elements(repositoryXml("content"), "unit")) {
            String id = unit.getAttribute("id");
            String version = unit.getAttribute("version");
            if (!id.isBlank() && !version.isBlank()) {
                iuVersions.computeIfAbsent(id, unused -> new LinkedHashSet<>()).add(version);
            }
        }

        Set<String> featureArtifacts = new LinkedHashSet<>();
        for (Element artifact : elements(repositoryXml("artifacts"), "artifact")) {
            if (!"org.eclipse.update.feature".equals(artifact.getAttribute("classifier"))) {
                continue;
            }
            String id = artifact.getAttribute("id");
            String version = artifact.getAttribute("version");
            if (!id.isBlank() && !version.isBlank()) {
                require(featureArtifacts.add(id + ":" + version),
                        "Duplicate feature artifact metadata: " + id + "/" + version);
            }
        }

        List<FeatureEvidence> result = new ArrayList<>();
        for (String id : publishedFeatures) {
            String iu = id + ".feature.group";
            Set<String> versions = iuVersions.getOrDefault(iu, Set.of());
            require(versions.size() == 1,
                    "Expected exactly one published IU version for " + iu + ", found " + versions);
            String version = versions.iterator().next();
            require(featureArtifacts.contains(id + ":" + version),
                    "Feature artifact metadata missing for exact IU version: " + id + "/" + version);

            Path artifact = repository.resolve("features").resolve(id + "_" + version + ".jar");
            require(Files.isRegularFile(artifact), "Published feature artifact is missing: " + artifact);
            result.add(new FeatureEvidence(
                    id,
                    iu,
                    version,
                    Files.size(artifact),
                    digest(artifact, "SHA-256")));
        }
        return result.stream()
                .sorted(Comparator.comparing(FeatureEvidence::id)
                        .thenComparing(FeatureEvidence::version))
                .toList();
    }

    private String renderRepository(List<FeatureEvidence> features) {
        StringBuilder json = new StringBuilder();
        json.append("  \"repository\": {\n");
        json.append("    \"publishedFeatures\": [\n");
        for (int index = 0; index < features.size(); index++) {
            FeatureEvidence feature = features.get(index);
            json.append("      {\n");
            json.append("        \"id\": \"").append(jsonEscape(feature.id())).append("\",\n");
            json.append("        \"iu\": \"").append(jsonEscape(feature.iu())).append("\",\n");
            json.append("        \"version\": \"").append(jsonEscape(feature.version())).append("\",\n");
            json.append("        \"artifactSize\": ").append(feature.artifactSize()).append(",\n");
            json.append("        \"artifactSha256\": \"")
                    .append(feature.artifactSha256()).append("\"\n");
            json.append("      }");
            if (index + 1 < features.size()) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("    ]\n");
        json.append("  }");
        return json.toString();
    }

    private Document repositoryXml(String stem) throws Exception {
        Path jar = repository.resolve(stem + ".jar");
        if (Files.isRegularFile(jar)) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                ZipEntry entry = zip.getEntry(stem + ".xml");
                require(entry != null, jar + " does not contain " + stem + ".xml");
                try (InputStream stream = zip.getInputStream(entry)) {
                    return parseXml(stream);
                }
            }
        }
        Path xml = repository.resolve(stem + ".xml");
        require(Files.isRegularFile(xml),
                "Repository is missing both " + stem + ".jar and " + stem + ".xml");
        return parseXml(xml);
    }

    private Document parseXml(Path path) throws Exception {
        try (InputStream stream = Files.newInputStream(path)) {
            return parseXml(stream);
        }
    }

    private Document parseXml(InputStream stream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(stream);
    }

    private List<Element> elements(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        List<Element> result = new ArrayList<>(nodes.getLength());
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) instanceof Element element) {
                result.add(element);
            }
        }
        return result;
    }

    private String digest(Path path, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream stream = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024 * 1024];
            for (int read; (read = stream.read(buffer)) >= 0;) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }

    private record FeatureEvidence(
            String id,
            String iu,
            String version,
            long artifactSize,
            String artifactSha256) {
    }
}
