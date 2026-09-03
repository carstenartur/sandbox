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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Repository-level contract test for the feature-scoped Eclipse Help bundles.
 * <p>
 * This test is part of the normal Tycho verification and deliberately uses only
 * the checked-out files. It must not require GitHub Actions or a network service.
 * </p>
 */
public class EclipseHelpStructureTest {

	private record Family(String runtimeBundle, List<String> screenshots) {

		String helpBundle() {
			return runtimeBundle + "_help"; //$NON-NLS-1$
		}

		String featureProject() {
			return runtimeBundle + "_feature"; //$NON-NLS-1$
		}
	}

	private static final List<Family> FAMILIES= List.of(
			new Family("sandbox_cleanup_application", List.of()), //$NON-NLS-1$
			new Family("sandbox_css_cleanup", List.of("css-cleanup-preferences.png")), //$NON-NLS-1$ //$NON-NLS-2$
			new Family("sandbox_encoding_quickfix", List.of("explicit-encoding-cleanup.png")), //$NON-NLS-1$ //$NON-NLS-2$
			new Family("sandbox_functional_converter", List.of("functional-converter-cleanup.png")), //$NON-NLS-1$ //$NON-NLS-2$
			new Family("sandbox_platform_helper", List.of("platform-status-cleanup.png")), //$NON-NLS-1$ //$NON-NLS-2$
			new Family("sandbox_tools", List.of()), //$NON-NLS-1$
			new Family("sandbox_triggerpattern", List.of("code-patterns-cleanup.png", //$NON-NLS-1$ //$NON-NLS-2$
					"llm-rule-inference-preferences.png")), //$NON-NLS-1$
			new Family("sandbox_xml_cleanup", List.of("xml-cleanup.png")), //$NON-NLS-1$ //$NON-NLS-2$
			new Family("sandbox_jface_cleanup", List.of("jface-cleanup.png", //$NON-NLS-1$ //$NON-NLS-2$
					"jface-cleanup-real-preview-single-file-steps.png", //$NON-NLS-1$
					"jface-cleanup-real-preview-diff-step.png", //$NON-NLS-1$
					"jface-cleanup-real-preview-multi-file-selection.png")), //$NON-NLS-1$
			new Family("sandbox_junit_cleanup", List.of("junit-migration-cleanup.png")), //$NON-NLS-1$ //$NON-NLS-2$
			new Family("sandbox_method_reuse", List.of("method-reuse-cleanup.png")), //$NON-NLS-1$ //$NON-NLS-2$
			new Family("sandbox_int_to_enum", List.of("int-to-enum-cleanup.png")), //$NON-NLS-1$ //$NON-NLS-2$
			new Family("sandbox_use_general_type", List.of("use-general-type-cleanup.png"))); //$NON-NLS-1$ //$NON-NLS-2$

	private static final List<String> REQUIRED_HELP_FILES= List.of(
			"META-INF/MANIFEST.MF", //$NON-NLS-1$
			"build.properties", //$NON-NLS-1$
			"plugin.xml", //$NON-NLS-1$
			"toc.xml", //$NON-NLS-1$
			"contexts.xml", //$NON-NLS-1$
			"html/index.html", //$NON-NLS-1$
			"html/usage.html", //$NON-NLS-1$
			"html/reference.html", //$NON-NLS-1$
			"html/style.css"); //$NON-NLS-1$

	private static final Pattern LOCAL_HTML_REFERENCE= Pattern.compile(
			"(?:href|src)\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$
	private static final Pattern URI_SCHEME= Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:.*"); //$NON-NLS-1$

	@Test
	public void helpBundlesAreCompleteFeatureScopedAndOffline() throws Exception {
		Path repository= repositoryRoot();
		String rootPom= read(repository.resolve("pom.xml")); //$NON-NLS-1$

		for (Family family : FAMILIES) {
			validateFamily(repository, rootPom, family);
		}
	}

	private static void validateFamily(Path repository, String rootPom, Family family) throws Exception {
		Path runtime= repository.resolve(family.runtimeBundle());
		Path help= repository.resolve(family.helpBundle());
		Path feature= repository.resolve(family.featureProject());

		assertTrue(Files.isDirectory(runtime), () -> "Missing runtime project " + runtime); //$NON-NLS-1$
		assertTrue(Files.isDirectory(help), () -> "Missing Help project " + help); //$NON-NLS-1$
		assertTrue(Files.isDirectory(feature), () -> "Missing feature project " + feature); //$NON-NLS-1$
		assertTrue(rootPom.contains("<module>" + family.helpBundle() + "</module>"), //$NON-NLS-1$ //$NON-NLS-2$
				() -> "Help bundle is absent from the root reactor: " + family.helpBundle()); //$NON-NLS-1$

		for (String required : REQUIRED_HELP_FILES) {
			Path file= help.resolve(required);
			assertTrue(Files.isRegularFile(file), () -> "Missing Help resource " + file); //$NON-NLS-1$
		}

		validateManifest(help, family);
		validateBuildProperties(help);
		validatePluginContributions(help, family);
		validateXmlTopicLinks(help, help.resolve("toc.xml")); //$NON-NLS-1$
		validateXmlTopicLinks(help, help.resolve("contexts.xml")); //$NON-NLS-1$
		validateHtmlLinks(help);
		validateScreenshots(help, family);
		validateFeature(feature.resolve("feature.xml"), family); //$NON-NLS-1$
		validateDependencyDirection(runtime, family);
	}

	private static void validateManifest(Path help, Family family) throws IOException {
		String manifest= read(help.resolve("META-INF/MANIFEST.MF")); //$NON-NLS-1$
		assertTrue(manifest.contains("Bundle-SymbolicName: " + family.helpBundle() + ";singleton:=true"), //$NON-NLS-1$ //$NON-NLS-2$
				() -> "Unexpected Help bundle symbolic name in " + help); //$NON-NLS-1$
	}

	private static void validateBuildProperties(Path help) throws IOException {
		String properties= read(help.resolve("build.properties")); //$NON-NLS-1$
		for (String shipped : List.of("META-INF/", "plugin.xml", "toc.xml", "contexts.xml", "html/", "images/")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
			assertTrue(properties.contains(shipped),
					() -> help + "/build.properties does not ship " + shipped); //$NON-NLS-1$
		}
	}

	private static void validatePluginContributions(Path help, Family family) throws Exception {
		Document plugin= parseXml(help.resolve("plugin.xml")); //$NON-NLS-1$
		NodeList extensions= plugin.getElementsByTagName("extension"); //$NON-NLS-1$
		Set<String> extensionPoints= new HashSet<>();
		for (int index= 0; index < extensions.getLength(); index++) {
			Element extension= (Element) extensions.item(index);
			String point= extension.getAttribute("point"); //$NON-NLS-1$
			extensionPoints.add(point);
			if ("org.eclipse.help.toc".equals(point)) { //$NON-NLS-1$
				validateContributionFile(help, extension, "toc"); //$NON-NLS-1$
			}
			if ("org.eclipse.help.contexts".equals(point)) { //$NON-NLS-1$
				NodeList contexts= extension.getElementsByTagName("contexts"); //$NON-NLS-1$
				assertEquals(1, contexts.getLength(), () -> "Expected one contexts contribution in " + help); //$NON-NLS-1$
				Element context= (Element) contexts.item(0);
				assertEquals(family.runtimeBundle(), context.getAttribute("plugin"), //$NON-NLS-1$
						() -> "Context Help must target the runtime bundle for " + family.runtimeBundle()); //$NON-NLS-1$
				validateLocalReference(help, help.resolve("plugin.xml"), context.getAttribute("file")); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		assertTrue(extensionPoints.contains("org.eclipse.help.toc"), //$NON-NLS-1$
				() -> "Missing org.eclipse.help.toc contribution in " + help); //$NON-NLS-1$
		assertTrue(extensionPoints.contains("org.eclipse.help.contexts"), //$NON-NLS-1$
				() -> "Missing org.eclipse.help.contexts contribution in " + help); //$NON-NLS-1$
	}

	private static void validateContributionFile(Path help, Element extension, String childName) {
		NodeList children= extension.getElementsByTagName(childName);
		assertEquals(1, children.getLength(), () -> "Expected one " + childName + " contribution in " + help); //$NON-NLS-1$ //$NON-NLS-2$
		Element child= (Element) children.item(0);
		validateLocalReference(help, help.resolve("plugin.xml"), child.getAttribute("file")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void validateXmlTopicLinks(Path help, Path xml) throws Exception {
		Document document= parseXml(xml);
		NodeList topics= document.getElementsByTagName("topic"); //$NON-NLS-1$
		for (int index= 0; index < topics.getLength(); index++) {
			Element topic= (Element) topics.item(index);
			if (topic.hasAttribute("href")) { //$NON-NLS-1$
				validateLocalReference(help, xml, topic.getAttribute("href")); //$NON-NLS-1$
			}
		}
		Element root= document.getDocumentElement();
		if (root.hasAttribute("topic")) { //$NON-NLS-1$
			validateLocalReference(help, xml, root.getAttribute("topic")); //$NON-NLS-1$
		}
	}

	private static void validateHtmlLinks(Path help) throws IOException {
		Path html= help.resolve("html"); //$NON-NLS-1$
		try (Stream<Path> files= Files.walk(html)) {
			for (Path page : files.filter(Files::isRegularFile)
					.filter(EclipseHelpStructureTest::isHtmlFile)
					.toList()) {
				Matcher matcher= LOCAL_HTML_REFERENCE.matcher(read(page));
				while (matcher.find()) {
					validateLocalReference(help, page, matcher.group(1));
				}
			}
		}
	}

	private static boolean isHtmlFile(Path path) {
		Path fileName= path.getFileName();
		return fileName != null && fileName.toString().endsWith(".html"); //$NON-NLS-1$
	}

	private static void validateScreenshots(Path help, Family family) throws IOException {
		for (String screenshot : family.screenshots()) {
			Path image= help.resolve("images").resolve(screenshot); //$NON-NLS-1$
			assertTrue(Files.isRegularFile(image), () -> "Missing generated Help screenshot " + image); //$NON-NLS-1$
			assertTrue(Files.size(image) > 0, () -> "Empty generated Help screenshot " + image); //$NON-NLS-1$
		}
	}

	private static void validateFeature(Path featureXml, Family family) throws Exception {
		Document feature= parseXml(featureXml);
		NodeList plugins= feature.getElementsByTagName("plugin"); //$NON-NLS-1$
		Set<String> ids= new HashSet<>();
		for (int index= 0; index < plugins.getLength(); index++) {
			ids.add(((Element) plugins.item(index)).getAttribute("id")); //$NON-NLS-1$
		}
		assertTrue(ids.contains(family.runtimeBundle()),
				() -> featureXml + " does not install " + family.runtimeBundle()); //$NON-NLS-1$
		assertTrue(ids.contains(family.helpBundle()),
				() -> featureXml + " does not install " + family.helpBundle()); //$NON-NLS-1$
	}

	private static void validateDependencyDirection(Path runtime, Family family) throws IOException {
		for (String metadata : List.of("META-INF/MANIFEST.MF", "pom.xml")) { //$NON-NLS-1$ //$NON-NLS-2$
			Path file= runtime.resolve(metadata);
			if (Files.isRegularFile(file)) {
				assertFalse(read(file).contains(family.helpBundle()),
						() -> "Runtime project must not depend on Help bundle: " + file); //$NON-NLS-1$
			}
		}
	}

	private static void validateLocalReference(Path help, Path source, String rawReference) {
		String reference= rawReference.strip();
		if (reference.isEmpty() || reference.startsWith("#") || URI_SCHEME.matcher(reference).matches()) { //$NON-NLS-1$
			return;
		}
		if (reference.startsWith("/topic/")) { //$NON-NLS-1$
			validateTopicReference(source, rawReference, reference);
			return;
		}
		int query= reference.indexOf('?');
		int fragment= reference.indexOf('#');
		int end= reference.length();
		if (query >= 0) {
			end= Math.min(end, query);
		}
		if (fragment >= 0) {
			end= Math.min(end, fragment);
		}
		String pathPart= reference.substring(0, end);
		if (pathPart.isEmpty()) {
			return;
		}
		Path sourceDirectory= Objects.requireNonNull(source.getParent(),
				() -> "Help source has no parent directory: " + source); //$NON-NLS-1$
		Path target= sourceDirectory.resolve(pathPart).normalize();
		assertTrue(target.startsWith(help),
				() -> "Help reference escapes its bundle: " + source + " -> " + rawReference); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(Files.isRegularFile(target),
				() -> "Broken local Help reference: " + source + " -> " + rawReference); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void validateTopicReference(Path source, String rawReference, String reference) {
		String relative= reference.substring("/topic/".length()); //$NON-NLS-1$
		int slash= relative.indexOf('/');
		assertTrue(slash > 0,
				() -> "Invalid /topic/ reference: " + source + " -> " + rawReference); //$NON-NLS-1$ //$NON-NLS-2$
		String bundle= relative.substring(0, slash);
		String path= relative.substring(slash + 1);
		Path repository= repositoryRoot();
		Path target= repository.resolve(bundle).resolve(path).normalize();
		assertTrue(Files.isRegularFile(target),
				() -> "Broken /topic/ Help reference: " + source + " -> " + rawReference); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static Document parseXml(Path xml) throws Exception {
		DocumentBuilderFactory factory= DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); //$NON-NLS-1$
		factory.setExpandEntityReferences(false);
		factory.setXIncludeAware(false);
		return factory.newDocumentBuilder().parse(xml.toFile());
	}

	private static Path repositoryRoot() {
		String configured= System.getProperty("sandbox.repository.root"); //$NON-NLS-1$
		assertFalse(configured == null || configured.isBlank(),
				"Missing -Dsandbox.repository.root; Tycho must pass the checkout root to structural tests"); //$NON-NLS-1$
		Path root= Path.of(configured).toAbsolutePath().normalize();
		assertTrue(Files.isRegularFile(root.resolve("pom.xml")), //$NON-NLS-1$
				() -> "Not a Sandbox repository root: " + root); //$NON-NLS-1$
		return root;
	}

	private static String read(Path file) throws IOException {
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
