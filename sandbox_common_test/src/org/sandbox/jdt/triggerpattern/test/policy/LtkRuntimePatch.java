/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Fail-closed packaging checks shared by the local tests and optional host IT. */
final class LtkRuntimePatch {
    static final String BUNDLE = "org.eclipse.ltk.core.refactoring";
    static final String CLASS = "org/eclipse/ltk/core/refactoring/CompositeChange.class";
    static final String STOCK_VERSION = "3.16.0.v20260702-0744";
    static final String FEATURE = "sandbox_patched_ltk_feature";
    static final String TARGET_FEATURE = "org.eclipse.platform";
    static final String TARGET_VERSION = "4.41.0.v20260828-1142";
    private static final List<String> CONTRACT_HEADERS = List.of("Bundle-SymbolicName",
            "Bundle-RequiredExecutionEnvironment", "Require-Bundle", "Import-Package", "Export-Package",
            "Bundle-ClassPath", "Bundle-Activator", "Fragment-Host", "Bundle-ActivationPolicy");

    private LtkRuntimePatch() {
    }

    static String validateBundle(Path patch, Path stock) throws IOException {
        Attributes original = headers(stock);
        Attributes replacement = headers(patch);
        require(STOCK_VERSION.equals(original.getValue("Bundle-Version")), "Unexpected stock LTK version");
        require(BUNDLE.equals(symbolicName(original)), "Unexpected stock bundle identity");
        String version = replacement.getValue("Bundle-Version");
        require(version != null && version.matches("3\\.16\\.0\\.v[0-9]{8}-[0-9]{4}"),
                "Replacement is not a qualified Eclipse 4.41 LTK bundle: " + version);
        require(version.compareTo(STOCK_VERSION) > 0, "Replacement must be newer than stock");
        for (String header : CONTRACT_HEADERS) {
            require(normalize(original.getValue(header)).equals(normalize(replacement.getValue(header))),
                    "Replacement changes runtime contract header: " + header);
        }
        require(normalize(replacement.getValue("Bundle-SymbolicName")).contains("singleton:=true"),
                "Replacement must retain singleton identity");
        require(readEntry(patch, CLASS).length > 0, "Replacement has no file-count implementation");
        return version;
    }

    static Path findStockBundle(Path resolvedJars) throws IOException {
        Map<String, Path> digests = new TreeMap<>();
        for (String line : Files.readAllLines(resolvedJars, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                Path jar = Path.of(line);
                if (BUNDLE.equals(symbolicName(headers(jar)))) {
                    require(STOCK_VERSION.equals(headers(jar).getValue("Bundle-Version")),
                            "Fresh target contains another stock LTK version: " + jar);
                    digests.put(sha256(jar), jar);
                }
            }
        }
        require(digests.size() == 1, "Expected one unique stock LTK artifact, found " + digests.size());
        return digests.values().iterator().next();
    }

    static String featureVersion(String bundleVersion) throws IOException {
        require(bundleVersion.matches("3\\.16\\.0\\.v[0-9]{8}-[0-9]{4}"), "Invalid bundle version");
        return "1.0.0." + bundleVersion.substring("3.16.0.".length());
    }

    static Path prepareFeature(Path source, Path patch, String version) throws IOException {
        Path plugins = Files.createDirectories(source.resolve("plugins"));
        Files.copy(patch, plugins.resolve(BUNDLE + "_" + version + ".jar"));
        Path feature = Files.createDirectories(source.resolve("features").resolve(FEATURE + "_" + featureVersion(version)));
        Files.writeString(feature.resolve("feature.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <feature id="%s" label="Sandbox LTK file-count patch" version="%s" provider-name="Sandbox">
                  <description>Release-aligned LTK fix for eclipse-platform/eclipse.platform.ui#4382.</description>
                  <license url="https://www.eclipse.org/legal/epl-2.0/">Eclipse Public License 2.0</license>
                  <requires><import feature="%s" version="%s" patch="true"/></requires>
                  <plugin id="%s" version="%s" download-size="0" install-size="0" unpack="false"/>
                </feature>
                """.formatted(FEATURE, featureVersion(version), TARGET_FEATURE, TARGET_VERSION, BUNDLE, version));
        return feature.resolve("feature.xml");
    }

    static void verifyRepository(Path repository, String version, String bundleDigest) throws Exception {
        String featureVersion = featureVersion(version);
        Document content = xml(readEntry(repository.resolve("content.jar"), "content.xml"));
        Set<String> units = new HashSet<>();
        var nodes = content.getElementsByTagName("unit");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element unit = (Element) nodes.item(i);
            require(units.add(unit.getAttribute("id")), "Duplicate installable unit");
            require((BUNDLE.equals(unit.getAttribute("id")) ? version : featureVersion).equals(unit.getAttribute("version")),
                    "Unexpected installable unit version");
        }
        require(units.equals(Set.of(BUNDLE, FEATURE + ".feature.group", FEATURE + ".feature.jar")),
                "Patch repository contains unexpected installable units: " + units);
        String group = "/repository/units/unit[@id='" + FEATURE + ".feature.group']";
        require(featureVersion.equals(value(content, group + "/@version")), "Wrong patch feature version");
        require("true".equals(value(content, group + "/properties/property[@name='org.eclipse.equinox.p2.type.patch']/@value")),
                "Feature is not a p2 patch");
        String target = TARGET_FEATURE + ".feature.group";
        String targetRange = exactRange(TARGET_VERSION);
        require("1".equals(value(content, "count(" + group + "/patchScope/scope/requires/required)")), "Unexpected patch scopes");
        require("1".equals(value(content, "count(" + group + "/lifeCycle/required)")), "Unexpected patch lifecycles");
        require(target.equals(value(content, group + "/patchScope/scope/requires/required/@name")), "Wrong patch scope");
        require(targetRange.equals(value(content, group + "/patchScope/scope/requires/required/@range")), "Unpinned patch scope");
        require(target.equals(value(content, group + "/lifeCycle/required/@name")), "Wrong patch lifecycle");
        require(targetRange.equals(value(content, group + "/lifeCycle/required/@range")), "Unpinned patch lifecycle");
        require("1".equals(value(content, "count(" + group + "/changes/change)")), "Expected one bundle replacement");
        require(BUNDLE.equals(value(content, group + "/changes/change/from/required/@name")), "Wrong replaced bundle");
        require(BUNDLE.equals(value(content, group + "/changes/change/to/required/@name")), "Wrong replacement bundle");
        require(exactRange(version).equals(value(content, group + "/changes/change/to/required/@range")), "Unpinned replacement");
        require(version.equals(value(content, "/repository/units/unit[@id='" + BUNDLE + "']/@version")), "Wrong bundle IU version");
        Path jar = repository.resolve("plugins/" + BUNDLE + "_" + version + ".jar");
        require(bundleDigest.equals(sha256(jar)), "Published bundle differs from verified build");
        verifyArtifacts(repository, version, featureVersion);
    }

    private static void verifyArtifacts(Path repository, String version, String featureVersion) throws Exception {
        Path archive = repository.resolve("artifacts.jar");
        Document document = xml(readEntry(archive, "artifacts.xml"));
        var artifacts = document.getElementsByTagName("artifact");
        require(artifacts.getLength() == 2, "Expected precisely the bundle and feature artifacts");
        Set<String> found = new HashSet<>();
        for (int i = 0; i < artifacts.getLength(); i++) {
            Element artifact = (Element) artifacts.item(i);
            String classifier = artifact.getAttribute("classifier");
            String id = artifact.getAttribute("id");
            String artifactVersion = artifact.getAttribute("version");
            boolean bundle = "osgi.bundle".equals(classifier) && BUNDLE.equals(id) && version.equals(artifactVersion);
            boolean feature = "org.eclipse.update.feature".equals(classifier) && FEATURE.equals(id)
                    && featureVersion.equals(artifactVersion);
            require(bundle || feature, "Unexpected artifact identity");
            require(found.add(id), "Duplicate artifact");
            Path file = repository.resolve((bundle ? "plugins/" : "features/") + id + "_" + artifactVersion + ".jar");
            String digest = sha256(file);
            Element properties = (Element) artifact.getElementsByTagName("properties").item(0);
            require(properties != null, "Missing artifact properties");
            for (String key : List.of("download.checksum.sha-256", "artifact.checksum.sha-256")) {
                Element property = null;
                var all = properties.getElementsByTagName("property");
                for (int j = 0; j < all.getLength(); j++) {
                    Element current = (Element) all.item(j);
                    if (key.equals(current.getAttribute("name"))) {
                        require(property == null, "Duplicate checksum metadata");
                        property = current;
                    }
                }
                if (property == null) {
                    property = document.createElement("property");
                    property.setAttribute("name", key);
                    property.setAttribute("value", digest);
                    properties.appendChild(property);
                } else {
                    require(digest.equals(property.getAttribute("value")), "Published checksum does not match bytes");
                }
            }
            properties.setAttribute("size", Integer.toString(properties.getElementsByTagName("property").getLength()));
        }
        try (var out = new ZipOutputStream(Files.newOutputStream(archive))) {
            ZipEntry entry = new ZipEntry("artifacts.xml");
            entry.setTime(0);
            out.putNextEntry(entry);
            out.write(serialize(document));
            out.closeEntry();
        }
    }

    static void addToTarget(Path target, Path repository, String version) throws Exception {
        Document document = xml(Files.readAllBytes(target));
        require("0".equals(value(document, "count(//unit[@id='" + FEATURE + ".feature.group'])")),
                "Target already contains an LTK patch");
        require("1".equals(value(document, "count(/target/locations)")), "Invalid target locations");
        require("1".equals(value(document,
                "count(/target/locations/location/repository[@location='https://download.eclipse.org/releases/2026-09/'])")),
                "The LTK patch requires the Eclipse 2026-09 target");
        Element location = document.createElement("location");
        for (var attribute : Map.of("includeAllPlatforms", "false", "includeConfigurePhase", "true",
                "includeMode", "planner", "includeSource", "true", "type", "InstallableUnit").entrySet()) {
            location.setAttribute(attribute.getKey(), attribute.getValue());
        }
        Element repo = document.createElement("repository");
        repo.setAttribute("location", repository.toAbsolutePath().toUri().toString());
        location.appendChild(repo);
        Element unit = document.createElement("unit");
        unit.setAttribute("id", FEATURE + ".feature.group");
        unit.setAttribute("version", featureVersion(version));
        location.appendChild(unit);
        document.getElementsByTagName("locations").item(0).appendChild(location);
        Files.write(target, serialize(document));
    }

    static Map<String, Integer> verifyTests(Path reports) throws Exception {
        int total = 0;
        Set<String> regressions = new HashSet<>();
        try (var paths = Files.walk(reports)) {
            for (Path report : paths.filter(p -> p.getFileName().toString().startsWith("TEST-")
                    && p.toString().endsWith(".xml")).toList()) {
                Document xml = xml(Files.readAllBytes(report));
                var suites = xml.getElementsByTagName("testsuite");
                for (int index = 0; index < suites.getLength(); index++) {
                    Element suite = (Element) suites.item(index);
                    for (String attribute : List.of("failures", "errors", "skipped")) {
                        require(!suite.hasAttribute(attribute) || "0".equals(suite.getAttribute(attribute)),
                                "LTK suite reports " + attribute + ": " + report);
                    }
                }
                var tests = xml.getElementsByTagName("testcase");
                total += tests.getLength();
                require(xml.getElementsByTagName("failure").getLength() == 0
                        && xml.getElementsByTagName("error").getLength() == 0
                        && xml.getElementsByTagName("skipped").getLength() == 0,
                        "LTK suite has failures, errors or skipped tests: " + report);
                for (int i = 0; i < tests.getLength(); i++) {
                    Element test = (Element) tests.item(i);
                    if ("org.eclipse.ltk.core.refactoring.tests.CompositeChangeTest".equals(test.getAttribute("classname"))) {
                        regressions.add(test.getAttribute("name"));
                    }
                }
            }
        }
        require(regressions.size() == 13, "Expected all 13 LTK regressions, found " + regressions.size());
        return Map.of("testCases", total, "distinctNewRegressions", regressions.size());
    }

    static Attributes headers(Path jar) throws IOException {
        try (var file = new JarFile(jar.toFile())) {
            return file.getManifest() == null ? new Attributes() : file.getManifest().getMainAttributes();
        }
    }

    static byte[] readEntry(Path jar, String name) throws IOException {
        try (var file = new JarFile(jar.toFile())) {
            var entry = file.getJarEntry(name);
            require(entry != null, "Missing " + name + " in " + jar);
            try (var in = file.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
    }

    static String sha256(Path file) throws IOException {
        try (var in = Files.newInputStream(file)) {
            return sha256(in);
        }
    }

    static String sha256(InputStream in) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) != -1) {
                digest.update(buffer, 0, length);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static Document xml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    static String value(Document document, String expression) throws Exception {
        return XPathFactory.newInstance().newXPath().evaluate(expression, document);
    }

    static byte[] serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        var out = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document), new StreamResult(out));
        return out.toByteArray();
    }

    private static String symbolicName(Attributes attributes) {
        return normalize(attributes.getValue("Bundle-SymbolicName")).split(";", 2)[0].strip();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    private static String exactRange(String version) {
        return "[" + version + "," + version + "]";
    }

    static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }
}
