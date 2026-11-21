/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.pde.internal.core.builders.SchemaTransformer;
import org.eclipse.pde.internal.core.schema.Schema;
import org.eclipse.pde.internal.core.schema.SchemaAnnotationHandler;
import org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils.DocumentationPolicy;
import org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils.PdeSchemaTarget;
import org.sandbox.jdt.ui.tests.quickfix.XMLTestUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/** Independent DOM expectations and the same real PDE consumers as the focused tests. */
@SuppressWarnings("restriction")
final class PdeXmlCorpusAssertions {

	private PdeXmlCorpusAssertions() { }

	static void assertMeaning(String before, String after, PdeSchemaTarget target,
			DocumentationPolicy documentation) throws Exception {
		Document expected= parse(before);
		if (hasCdata(expected)) {
			assertEquals(before, after, "CDATA is a whole-document preservation boundary"); //$NON-NLS-1$
			return;
		}
		Element root= expected.getDocumentElement();
		boolean pdeSchema= isPdeSchema(root);
		if (pdeSchema && !spacePreserved(root)) {
			for (Node node : children(expected)) {
				if (node == root) {
					break;
				}
				if (node.getNodeType() == Node.COMMENT_NODE
						&& " Schema file written by PDE ".equals(node.getNodeValue())) { //$NON-NLS-1$
					expected.removeChild(node);
				}
			}
		}
		var nodes= root.getElementsByTagName("*"); //$NON-NLS-1$
		List<Element> elements= new ArrayList<>();
		for (int i= 0; i < nodes.getLength(); i++) {
			elements.add((Element) nodes.item(i));
		}
		for (Element element : elements) {
			if (target == PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER && isLegacyPdeMetadata(element, root)
					&& !spacePreserved(element)) {
				String prefix= element.getPrefix();
				expected.renameNode(element, element.getNamespaceURI(), prefix == null ? "appinfo" : prefix + ":appinfo"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (documentation == DocumentationPolicy.TRIM_PLAIN_SECTION_BOUNDARIES && pdeSchema
					&& root.getNamespaceURI() == null && isPlainReferenceSection(element, root)) {
				element.setTextContent(element.getTextContent().replaceAll("^[ \\t\\r\\n]+|[ \\t\\r\\n]+$", "")); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		TransformerFactory factory= TransformerFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, ""); //$NON-NLS-1$
		StringWriter serialized= new StringWriter();
		factory.newTransformer().transform(new DOMSource(expected), new StreamResult(serialized));
		assertTrue(XMLTestUtils.isXmlSemanticallyEqualWithComments(serialized.toString(), after),
				"XML meaning, comments or processing instructions changed outside the selected policy"); //$NON-NLS-1$
	}

	static void assertConsumers(Path original, Path cleaned) throws Exception {
		assertNotEquals(original.toUri(), cleaned.toUri(), "Lazy PDE readers must read distinct files"); //$NON-NLS-1$
		assertEquals(annotation(original), annotation(cleaned), "Lightweight PDE annotation reader"); //$NON-NLS-1$
		for (boolean abbreviated : new boolean[] { false, true }) {
			Schema before= read(original, abbreviated);
			Schema after= read(cleaned, abbreviated);
			try {
				assertEquals(model(before), model(after), "PDE metadata and lazy descriptions; abbreviated=" + abbreviated); //$NON-NLS-1$
				if (!abbreviated) {
					assertEquals(html(before), html(after), "PDE generated HTML documentation"); //$NON-NLS-1$
				}
			} finally {
				before.dispose();
				after.dispose();
			}
		}
	}

	private static Map<String, Object> model(Schema schema) {
		Map<String, Object> result= new LinkedHashMap<>();
		result.put("schema", Arrays.asList(schema.getName(), schema.getPluginId(), schema.getPointId(), //$NON-NLS-1$
				schema.getDescription(), schema.getSchemaVersion(), schema.isDeperecated(), schema.isInternal()));
		for (var element : schema.getElements()) {
			String key= "element:" + element.getName(); //$NON-NLS-1$
			result.put(key, Arrays.asList(element.getDescription(), element.getLabelProperty(), element.getIconProperty(),
					element.isDeprecated(), element.getMinOccurs(), element.getMaxOccurs()));
			for (String name : element.getAttributeNames()) {
				var attribute= element.getAttribute(name);
				result.put(key + "/" + name, Arrays.asList(attribute.getDescription(), attribute.getKind(), //$NON-NLS-1$
						attribute.getBasedOn(), attribute.isTranslatable(), attribute.getUse(), attribute.getValue()));
			}
		}
		var sections= schema.getDocumentSections();
		for (int i= 0; i < sections.length; i++) {
			var section= sections[i];
			result.put("section:" + i, Arrays.asList(section.getSectionId(), section.getName(), section.getDescription())); //$NON-NLS-1$
		}
		return result;
	}

	private static List<String> annotation(Path file) throws Exception {
		SchemaAnnotationHandler handler= new SchemaAnnotationHandler();
		SAXParserFactory factory= SAXParserFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
		try (var input= Files.newInputStream(file)) {
			factory.newSAXParser().parse(input, handler);
		}
		return Arrays.asList(handler.getName(), handler.getDescription());
	}

	private static Schema read(Path file, boolean abbreviated) throws Exception {
		Schema schema= new Schema(null, file.toUri().toURL(), abbreviated);
		try (var input= Files.newInputStream(file)) {
			schema.load(input);
		}
		assertTrue(schema.isLoaded(), () -> "PDE did not load " + file); //$NON-NLS-1$
		assertTrue(schema.isValid(), () -> "PDE did not recognize " + file); //$NON-NLS-1$
		return schema;
	}

	private static String html(Schema schema) {
		StringWriter output= new StringWriter();
		new SchemaTransformer().transform(schema, new PrintWriter(output), null, SchemaTransformer.BUILD);
		return output.toString();
	}

	private static boolean isPdeSchema(Element root) {
		if (!"schema".equals(root.getNodeName()) || !(root.getNamespaceURI() == null //$NON-NLS-1$
				|| XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(root.getNamespaceURI()))) {
			return false;
		}
		for (Element annotation : elements(root)) {
			if (named(annotation, "annotation", root)) { //$NON-NLS-1$
				for (Element appinfo : elements(annotation)) {
					if (named(appinfo, "appInfo", root) || named(appinfo, "appinfo", root)) { //$NON-NLS-1$ //$NON-NLS-2$
						if (elements(appinfo).stream().anyMatch(meta -> named(meta, "meta.schema", root))) { //$NON-NLS-1$
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private static boolean isLegacyPdeMetadata(Element appinfo, Element root) {
		if (!"schema".equals(root.getLocalName()) || !(root.getNamespaceURI() == null //$NON-NLS-1$
				|| XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(root.getNamespaceURI()))
				|| !"appInfo".equals(appinfo.getLocalName()) || !sameNamespace(appinfo, root) //$NON-NLS-1$
				|| !(appinfo.getParentNode() instanceof Element annotation)
				|| !"annotation".equals(annotation.getLocalName()) || !sameNamespace(annotation, root) //$NON-NLS-1$
				|| !(annotation.getParentNode() instanceof Element owner) || nonBlankText(appinfo)) {
			return false;
		}
		Set<String> allowed= switch (owner.getLocalName()) {
			case "schema" -> Set.of("meta.schema", "meta.section"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			case "element" -> Set.of("meta.element"); //$NON-NLS-1$ //$NON-NLS-2$
			case "attribute" -> Set.of("meta.attribute"); //$NON-NLS-1$ //$NON-NLS-2$
			default -> Set.of();
		};
		List<Element> metadata= elements(appinfo);
		return !metadata.isEmpty() && metadata.stream().allMatch(meta -> sameNamespace(meta, root)
				&& allowed.contains(meta.getLocalName()) && elements(meta).isEmpty() && !nonBlankText(meta));
	}

	private static boolean isPlainReferenceSection(Element documentation, Element root) {
		if (!"documentation".equals(documentation.getNodeName()) || spacePreserved(documentation) //$NON-NLS-1$
				|| children(documentation).stream().anyMatch(node -> node.getNodeType() != Node.TEXT_NODE)
				|| documentation.getTextContent().isBlank() || documentation.getTextContent().contains("<") //$NON-NLS-1$
				|| !(documentation.getParentNode() instanceof Element annotation)
				|| !"annotation".equals(annotation.getNodeName()) || annotation.getParentNode() != root) { //$NON-NLS-1$
			return false;
		}
		List<Node> significant= children(annotation).stream()
				.filter(node -> node.getNodeType() != Node.TEXT_NODE || !node.getNodeValue().isBlank()).toList();
		if (significant.size() != 2 || significant.get(1) != documentation
				|| !(significant.get(0) instanceof Element appinfo)
				|| !Set.of("appInfo", "appinfo").contains(appinfo.getNodeName()) || nonBlankText(appinfo)) { //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		List<Element> metadata= elements(appinfo);
		return metadata.size() == 1 && "meta.section".equals(metadata.getFirst().getNodeName()) //$NON-NLS-1$
				&& elements(metadata.getFirst()).isEmpty() && !nonBlankText(metadata.getFirst())
				&& Set.of("since", "apiinfo", "implementation", "copyright").contains(metadata.getFirst().getAttribute("type")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
	}

	private static boolean named(Element element, String name, Element root) {
		return name.equals(element.getNodeName()) && sameNamespace(element, root);
	}

	private static boolean sameNamespace(Element element, Element root) {
		return Objects.equals(element.getNamespaceURI(), root.getNamespaceURI());
	}

	private static boolean nonBlankText(Element element) {
		return children(element).stream().anyMatch(node -> node.getNodeType() == Node.TEXT_NODE && !node.getNodeValue().isBlank());
	}

	private static boolean spacePreserved(Element element) {
		for (Node node= element; node instanceof Element current; node= node.getParentNode()) {
			if (current.hasAttributeNS(XMLConstants.XML_NS_URI, "space")) { //$NON-NLS-1$
				return "preserve".equals(current.getAttributeNS(XMLConstants.XML_NS_URI, "space")); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		return false;
	}

	private static boolean hasCdata(Node node) {
		return node.getNodeType() == Node.CDATA_SECTION_NODE || children(node).stream().anyMatch(PdeXmlCorpusAssertions::hasCdata);
	}

	private static List<Element> elements(Node parent) {
		return children(parent).stream().filter(Element.class::isInstance).map(Element.class::cast).toList();
	}

	private static List<Node> children(Node parent) {
		List<Node> children= new ArrayList<>();
		for (Node child= parent.getFirstChild(); child != null; child= child.getNextSibling()) {
			children.add(child);
		}
		return children;
	}

	private static Document parse(String xml) throws Exception {
		DocumentBuilderFactory factory= DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); //$NON-NLS-1$
		return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
	}
}
