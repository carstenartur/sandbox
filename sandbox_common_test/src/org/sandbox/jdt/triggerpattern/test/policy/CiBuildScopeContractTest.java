/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Verifies that specialized Linux CI scope cannot weaken authoritative gates. */
public class CiBuildScopeContractTest {

	private static final String ACTIVATION_PROPERTY = "sandbox.tycho.linux-only"; //$NON-NLS-1$
	private static final String SPOTBUGS_SKIP = "-Dspotbugs.skip=true"; //$NON-NLS-1$
	private static final String LINUX_ONLY = "-D" + ACTIVATION_PROPERTY + "=true"; //$NON-NLS-1$ //$NON-NLS-2$

	@Test
	public void linuxOnlyProfilesReplaceTargetAndArchivePlatformLists() throws Exception {
		Path root = repositoryRoot();

		Element targetProfile = profile(parse(root.resolve("pom.xml")), "linux-only-tycho"); //$NON-NLS-1$ //$NON-NLS-2$
		assertActivation(targetProfile);
		Element targetPlugin = plugin(targetProfile, "org.eclipse.tycho", //$NON-NLS-1$
				"target-platform-configuration"); //$NON-NLS-1$
		Element environments = child(child(targetPlugin, "configuration"), "environments"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("override", environments.getAttribute("combine.self")); //$NON-NLS-1$ //$NON-NLS-2$
		List<Element> targetEnvironments = children(environments, "environment"); //$NON-NLS-1$
		assertEquals(1, targetEnvironments.size());
		Element linux = targetEnvironments.getFirst();
		assertEquals("linux", text(linux, "os")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("gtk", text(linux, "ws")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("x86_64", text(linux, "arch")); //$NON-NLS-1$ //$NON-NLS-2$

		Element productProfile = profile(parse(root.resolve("sandbox_product/pom.xml")), //$NON-NLS-1$
				"linux-only-product"); //$NON-NLS-1$
		assertActivation(productProfile);
		Element director = plugin(productProfile, "org.eclipse.tycho", "tycho-p2-director-plugin"); //$NON-NLS-1$ //$NON-NLS-2$
		Element formats = child(child(director, "configuration"), "formats"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("override", formats.getAttribute("combine.self")); //$NON-NLS-1$ //$NON-NLS-2$
		List<Element> archiveFormats = childElements(formats);
		assertEquals(1, archiveFormats.size());
		assertEquals("linux", name(archiveFormats.getFirst())); //$NON-NLS-1$
		assertEquals("tar.gz", archiveFormats.getFirst().getTextContent().strip()); //$NON-NLS-1$
	}

	@Test
	public void specializedLinuxGatesKeepTheirSemanticTestsAndSkipDuplicateAnalysis() throws IOException {
		Path root = repositoryRoot();
		Map<String, String> semanticMarkers = new LinkedHashMap<>();
		semanticMarkers.put(".github/workflows/eclipse-help-screenshots.yml", //$NON-NLS-1$
				"SandboxHelpScreenshotsMergeGateSWTBotTest"); //$NON-NLS-1$
		semanticMarkers.put(".github/workflows/patched-jdt-ui-atomic-help-screenshot.yml", //$NON-NLS-1$
				"SandboxAtomicPreviewPatchedJdtSWTBotTest"); //$NON-NLS-1$
		semanticMarkers.put(".github/workflows/jdt-ui-junit4-strict-qa.yml", //$NON-NLS-1$
				"run-jdt-ui-before-after.sh"); //$NON-NLS-1$
		semanticMarkers.put(".github/scripts/compare_patched_jdt_ui_with_target.sh", //$NON-NLS-1$
				"compatibility.json"); //$NON-NLS-1$

		for (Map.Entry<String, String> entry : semanticMarkers.entrySet()) {
			String content = Files.readString(root.resolve(entry.getKey()));
			assertEquals(1, occurrences(content, LINUX_ONLY), entry.getKey());
			assertTrue(content.contains(SPOTBUGS_SKIP), entry.getKey());
			assertTrue(content.contains(entry.getValue()), entry.getKey());
		}

		String strict = Files.readString(root.resolve(".github/workflows/jdt-ui-junit4-strict-qa.yml")); //$NON-NLS-1$
		assertTrue(strict.contains("--mode strict")); //$NON-NLS-1$
		assertTrue(strict.contains("VerifyWhitespaceRegression.java")); //$NON-NLS-1$
	}

	@Test
	public void authoritativeMavenAndDistributionGatesRemainFullScope() throws IOException {
		Path root = repositoryRoot();
		for (String path : List.of(".github/workflows/maven.yml", //$NON-NLS-1$
				".github/workflows/distribution-smoke.yml")) { //$NON-NLS-1$
			String content = Files.readString(root.resolve(path));
			assertFalse(content.contains(ACTIVATION_PROPERTY), path);
			assertFalse(content.contains("spotbugs.skip"), path); //$NON-NLS-1$
			assertTrue(content.contains("clean verify"), path); //$NON-NLS-1$
		}
	}

	private static void assertActivation(Element profile) {
		Element property = child(child(profile, "activation"), "property"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(ACTIVATION_PROPERTY, text(property, "name")); //$NON-NLS-1$
		assertEquals("true", text(property, "value")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static Document parse(Path path) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setExpandEntityReferences(false);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false); //$NON-NLS-1$
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); //$NON-NLS-1$
		return factory.newDocumentBuilder().parse(path.toFile());
	}

	private static Element profile(Document document, String id) {
		Element profiles = child(document.getDocumentElement(), "profiles"); //$NON-NLS-1$
		return children(profiles, "profile").stream() //$NON-NLS-1$
				.filter(candidate -> id.equals(text(candidate, "id"))) //$NON-NLS-1$
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing Maven profile " + id)); //$NON-NLS-1$
	}

	private static Element plugin(Element profile, String groupId, String artifactId) {
		Element plugins = child(child(profile, "build"), "plugins"); //$NON-NLS-1$ //$NON-NLS-2$
		return children(plugins, "plugin").stream() //$NON-NLS-1$
				.filter(candidate -> groupId.equals(text(candidate, "groupId"))) //$NON-NLS-1$
				.filter(candidate -> artifactId.equals(text(candidate, "artifactId"))) //$NON-NLS-1$
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing Maven plugin " + groupId + ':' + artifactId)); //$NON-NLS-1$
	}

	private static String text(Element parent, String childName) {
		return child(parent, childName).getTextContent().strip();
	}

	private static Element child(Element parent, String childName) {
		return children(parent, childName).stream()
				.findFirst()
				.orElseThrow(() -> new AssertionError(
						"Missing <" + childName + "> below <" + name(parent) + ">")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static List<Element> children(Element parent, String childName) {
		return childElements(parent).stream()
				.filter(candidate -> childName.equals(name(candidate)))
				.toList();
	}

	private static List<Element> childElements(Element parent) {
		List<Element> result = new ArrayList<>();
		NodeList nodes = parent.getChildNodes();
		for (int index = 0; index < nodes.getLength(); index++) {
			Node node = nodes.item(index);
			if (node instanceof Element element) {
				result.add(element);
			}
		}
		return result;
	}

	private static String name(Element element) {
		return element.getLocalName() == null ? element.getTagName() : element.getLocalName();
	}

	private static int occurrences(String content, String token) {
		int count = 0;
		int offset = 0;
		while ((offset = content.indexOf(token, offset)) >= 0) {
			count++;
			offset += token.length();
		}
		return count;
	}

	private static Path repositoryRoot() throws IOException {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(); //$NON-NLS-1$
		while (current != null) {
			if (Files.isRegularFile(current.resolve("pom.xml")) //$NON-NLS-1$
					&& Files.isRegularFile(current.resolve(".github/workflows/maven.yml"))) { //$NON-NLS-1$
				return current;
			}
			current = current.getParent();
		}
		throw new IOException("Cannot locate the Sandbox repository root"); //$NON-NLS-1$
	}
}
