/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.maven.cleanup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/** Carries existing Eclipse Java metadata into an isolated Maven-backed project. */
final class MavenEclipseMetadata {
	private static final String JRE = "org.eclipse.jdt.launching.JRE_CONTAINER";
	private static final String M2E = "org.eclipse.m2e.MAVEN2_CLASSPATH_CONTAINER";
	private static final Set<String> LANGUAGE_OPTIONS = Set.of("org.eclipse.jdt.core.compiler.source",
			"org.eclipse.jdt.core.compiler.compliance", "org.eclipse.jdt.core.compiler.codegen.targetPlatform",
			"org.eclipse.jdt.core.compiler.release");

	private MavenEclipseMetadata() {}

	static boolean canImportDirectly(Path base) throws IOException {
		if (!isJavaProject(base)) return false;
		Element classpath = read(base.resolve(".classpath"), "classpath");
		if (classpath == null) return false;
		boolean hasSource = false;
		for (Element entry : children(classpath, "classpathentry")) {
			String kind = entry.getAttribute("kind");
			String path = entry.getAttribute("path");
			if ("con".equals(kind) && container(path, M2E)) return false;
			if (("con".equals(kind) && !container(path, JRE)) || "var".equals(kind)
					|| ("src".equals(kind) && path.startsWith("/"))) throw unsupported(path);
			if ("src".equals(kind) && entry.hasAttribute("path")) hasSource = true;
		}
		return hasSource;
	}

	static void preserve(Path base, Path generated, Path patchBase, boolean mavenLanguageConfigured) throws IOException {
		if (!isJavaProject(base)) return;
		String prefix = patchBase.relativize(base).toString().replace('\\', '/');
		if (!prefix.isEmpty()) prefix += "/";
		Element original = read(base.resolve(".classpath"), "classpath");
		if (original != null) {
			Path file = generated.resolve(".classpath");
			Element target = read(file, "classpath");
			if (target == null) throw new IOException("Generated Java classpath missing: " + file);
			for (Element entry : children(original, "classpathentry")) mergeEntry(base, target, entry, prefix);
			try {
				var factory = TransformerFactory.newDefaultInstance();
				factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
				factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
				factory.newTransformer().transform(new DOMSource(target), new StreamResult(file.toFile()));
			} catch (TransformerException e) {
				throw new IOException("Cannot write generated Java classpath " + file, e);
			}
		}
		for (String name : List.of("org.eclipse.jdt.core.prefs", "org.eclipse.jdt.ui.prefs", "org.eclipse.core.resources.prefs")) {
			Path source = base.resolve(".settings").resolve(name);
			if (!Files.isRegularFile(source)) continue;
			Path destination = generated.resolve(".settings").resolve(name);
			Properties merged = properties(destination);
			var originalPrefs = properties(source);
			for (String key : originalPrefs.stringPropertyNames()) {
				if (mavenLanguageConfigured && LANGUAGE_OPTIONS.contains(key)) continue;
				String targetKey = key.startsWith("encoding//") ? "encoding//" + prefix + key.substring(10) : key;
				merged.setProperty(targetKey, originalPrefs.getProperty(key));
			}
			try (var out = Files.newOutputStream(destination)) { merged.store(out, null); }
		}
	}

	private static void mergeEntry(Path base, Element target, Element original, String prefix) throws IOException {
		String kind = original.getAttribute("kind");
		String path = original.getAttribute("path");
		Element existing;
		switch (kind) {
		case "src" -> {
			if (path.startsWith("/")) throw unsupported(path);
			existing = findEntry(target, kind, prefix + path);
			if (existing == null) return; // Only Maven's selected source sets are linked.
		}
		case "lib" -> {
			if (isTestEntry(original) && children(target, "classpathentry").stream()
					.noneMatch(entry -> "src".equals(entry.getAttribute("kind")) && isTestEntry(entry))) return;
			Path lib = base.resolve(path);
			if (!Files.exists(lib)) throw unsupported(path);
			path = lib.toRealPath().toString();
			existing = findEntry(target, kind, path);
		}
		case "con" -> {
			if (container(path, M2E)) {
				// IDE-level access rules or module-path overrides do not transfer to individual Maven JARs.
				if (!children(original, "accessrules").isEmpty()) throw unsupported(path + " access rules");
				for (Element attrs : children(original, "attributes")) {
					for (Element attr : children(attrs, "attribute")) {
						if (!"maven.pomderived".equals(attr.getAttribute("name"))) throw unsupported(path + " " + attr.getAttribute("name"));
					}
				}
				return;
			}
			if (!container(path, JRE)) throw unsupported(path);
			existing = findEntry(target, kind, JRE);
		}
		case "output" -> { return; } // Isolate generated classes from the consumer.
		default -> throw unsupported(kind + ": " + path);
		}
		Element copy = (Element) target.getOwnerDocument().importNode(original, true);
		copy.setAttribute("path", kind.equals("src") ? prefix + path : path);
		copy.removeAttribute("output");
		if (existing == null) {
			target.appendChild(copy);
		} else {
			// The selected Maven model determines which entries are visible to main and test sources.
			Element originalTest = testAttribute(copy);
			if (originalTest != null) originalTest.getParentNode().removeChild(originalTest);
			Element mavenTest = testAttribute(existing);
			if (mavenTest != null) {
				var groups = children(copy, "attributes");
				Element attrs = groups.isEmpty() ? (Element) copy.appendChild(
						target.getOwnerDocument().createElement("attributes")) : groups.getFirst();
				attrs.appendChild(mavenTest.cloneNode(true));
			}
			target.replaceChild(copy, existing);
		}
	}

	private static Element testAttribute(Element entry) {
		for (Element group : children(entry, "attributes")) {
			for (Element attr : children(group, "attribute")) {
				if ("test".equals(attr.getAttribute("name"))) return attr;
			}
		}
		return null;
	}

	private static boolean isTestEntry(Element entry) {
		Element attribute = testAttribute(entry);
		return attribute != null && Boolean.parseBoolean(attribute.getAttribute("value"));
	}

	private static IOException unsupported(String path) {
		return new IOException("Cannot resolve Eclipse classpath entry " + path
				+ " in an isolated Maven project. Set SANDBOX_WORKSPACE to an Eclipse workspace with this classpath configured.");
	}

	private static Element findEntry(Element root, String kind, String path) {
		for (Element entry : children(root, "classpathentry")) {
			if (kind.equals(entry.getAttribute("kind")) && path.equals(entry.getAttribute("path"))) return entry;
		}
		return null;
	}

	private static boolean container(String path, String id) {
		return path.equals(id) || path.startsWith(id + "/");
	}

	private static boolean isJavaProject(Path base) throws IOException {
		Element project = read(base.resolve(".project"), "projectDescription");
		if (project == null) return false;
		for (Element natures : children(project, "natures")) {
			for (Element nature : children(natures, "nature")) {
				if ("org.eclipse.jdt.core.javanature".equals(nature.getTextContent().strip())) return true;
			}
		}
		return false;
	}

	private static Properties properties(Path file) throws IOException {
		var values = new Properties();
		if (Files.isRegularFile(file)) {
			try (var in = Files.newInputStream(file)) { values.load(in); }
		}
		return values;
	}

	private static Element read(Path file, String root) throws IOException {
		if (!Files.isRegularFile(file) || Files.size(file) == 0) return null;
		try {
			var factory = DocumentBuilderFactory.newDefaultInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			Element element = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
			if (!root.equals(element.getTagName())) throw new IOException("Expected <" + root + "> in " + file);
			return element;
		} catch (SAXException | ParserConfigurationException e) {
			throw new IOException("Cannot read Eclipse metadata " + file + ": " + e.getMessage(), e);
		}
	}

	private static List<Element> children(Element element, String name) {
		List<Element> result = new ArrayList<>();
		for (var node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element child && name.equals(child.getTagName())) result.add(child);
		}
		return result;
	}
}
