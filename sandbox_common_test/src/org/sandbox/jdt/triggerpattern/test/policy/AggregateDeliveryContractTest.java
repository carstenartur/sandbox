/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

/** Keeps aggregate packaging distinct from capabilities and its gate executable. */
public class AggregateDeliveryContractTest {
    @Test
    public void catalogNamesTheAggregateWithoutDuplicatingCapabilities() throws Exception {
        var inventory = JsonParser.parseString(Files.readString(root().resolve("docs/capabilities.json"))).getAsJsonObject();
        assertTrue(inventory.has("aggregateFeatures"), "Missing aggregate installation catalog");
        var aggregates = inventory.getAsJsonArray("aggregateFeatures");
        assertEquals(1, aggregates.size());
        assertEquals("sandbox_feature", aggregates.get(0).getAsString());
        var capabilities = new TreeSet<String>();
        for (var item : inventory.getAsJsonArray("capabilities")) {
            assertTrue(capabilities.add(item.getAsJsonObject().get("feature").getAsString()));
        }
        assertFalse(capabilities.contains("sandbox_feature"), "An aggregate is not another cleanup capability");
        var includes = xml(root().resolve("sandbox_feature/feature.xml")).getElementsByTagName("includes");
        var members = new TreeSet<String>();
        for (int index = 0; index < includes.getLength(); index++) {
            assertTrue(members.add(((Element) includes.item(index)).getAttribute("id")));
        }
        assertEquals(capabilities, members);
    }

    @Test
    public void mavenRunsTheAggregateGateAfterTheExistingDistributionGate() throws Exception {
        var executions = xml(root().resolve("sandbox_distribution_verify/pom.xml")).getElementsByTagName("execution");
        List<String> gates = new ArrayList<>();
        for (int index = 0; index < executions.getLength(); index++) {
            Element execution = (Element) executions.item(index);
            String id = execution.getElementsByTagName("id").item(0).getTextContent();
            if (id.equals("verify-distribution") || id.equals("verify-aggregate-installation")) {
                gates.add(id);
                assertEquals("verify", execution.getElementsByTagName("phase").item(0).getTextContent());
                assertEquals("java", execution.getElementsByTagName("goal").item(0).getTextContent());
                String expected = id.equals("verify-distribution") ? "DistributionVerifier" : "AggregateInstallationVerifier";
                assertEquals("org.sandbox.distribution." + expected, execution.getElementsByTagName("mainClass").item(0).getTextContent());
                assertEquals("${maven.multiModuleProjectDirectory}", execution.getElementsByTagName("argument").item(0).getTextContent());
            }
        }
        assertEquals(List.of("verify-distribution", "verify-aggregate-installation"), gates);
    }

    private static Element xml(Path file) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
    }

    private static Path root() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (path != null && !Files.isRegularFile(path.resolve("docs/capabilities.json"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("Cannot locate Sandbox root");
        return path;
    }
}
