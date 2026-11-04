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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils.DocumentationPolicy;
import org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils.PdeSchemaTarget;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/** Exact generated-header removal, with all other XML comments protected. */
class SchemaTransformationPdePreambleTest {

	private static final String DECLARATION= "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"; //$NON-NLS-1$
	private static final String PREAMBLE= "<!-- Schema file written by PDE -->"; //$NON-NLS-1$
	private static final String XSD= "http://www.w3.org/2001/XMLSchema"; //$NON-NLS-1$
	private static final String METADATA= "<meta.schema plugin=\"org.example\" id=\"items\" name=\"Items\"/>"; //$NON-NLS-1$
	private static final String SCHEMA= "<schema><annotation><appInfo>" + METADATA //$NON-NLS-1$
			+ "</appInfo></annotation><element name=\"item\"/></schema>"; //$NON-NLS-1$

	static Stream<Arguments> recognizedSchemas() {
		return Stream.of(
				Arguments.of("", "appInfo"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("", "appinfo"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of(" xmlns=\"" + XSD + "\"", "appInfo"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of(" xmlns=\"" + XSD + "\"", "appinfo")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@ParameterizedTest
	@MethodSource("recognizedSchemas") //$NON-NLS-1$
	void removesOnlyTheGeneratedPreambleAndItsLayoutSlot(String namespace, String appinfo) throws Exception {
		String source= PREAMBLE + "\n<schema" + namespace + ">\n  <annotation><" + appinfo + ">" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ METADATA + "</" + appinfo + "></annotation>\n  <element name=\"item\"/>\n</schema>"; //$NON-NLS-1$ //$NON-NLS-2$
		String compact= DECLARATION + "<schema" + namespace + "><annotation><" + appinfo + ">" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ METADATA + "</" + appinfo + "></annotation><element name=\"item\"/></schema>"; //$NON-NLS-1$ //$NON-NLS-2$
		String indented= DECLARATION + "\n<schema" + namespace + ">\n\t<annotation>\n\t\t<" + appinfo + ">\n\t\t\t" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ METADATA + "\n\t\t</" + appinfo + ">\n\t</annotation>\n\t<element name=\"item\"/>\n</schema>\n"; //$NON-NLS-1$ //$NON-NLS-2$

		assertEquals(compact, transform(source, false));
		assertEquals(indented, transform(source, true));
		assertEquals(compact, transform(compact, false));
		assertEquals(indented, transform(indented, true));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesOtherCommentsAndProcessingInstructionsInOrder(boolean indent) throws Exception {
		String source= PREAMBLE + "<?first keep?>" + "<!-- Human explanation -->" + PREAMBLE //$NON-NLS-1$ //$NON-NLS-2$
				+ SCHEMA.replace("</schema>", PREAMBLE + "</schema>") //$NON-NLS-1$ //$NON-NLS-2$
				+ "<?last keep?>" + PREAMBLE; //$NON-NLS-1$
		Document before= parse(source);
		String output= transform(source, indent);
		Document after= parse(output);

		assertEquals(List.of("PI:first:keep", "COMMENT: Human explanation ", "ELEMENT:schema", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"PI:last:keep", "COMMENT: Schema file written by PDE "), documentNodes(after)); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(5, commentCount(before));
		assertEquals(3, commentCount(after));
		assertEquals(" Schema file written by PDE ", //$NON-NLS-1$
				after.getDocumentElement().getLastChild().getNodeType() == Node.COMMENT_NODE
						? after.getDocumentElement().getLastChild().getNodeValue()
						: lastComment(after.getDocumentElement()));
		assertEquals(output, transform(output, indent));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Schema file written by PDE", //$NON-NLS-1$
			"  Schema file written by PDE ", //$NON-NLS-1$
			" Schema file written by pde ", //$NON-NLS-1$
			" Schema file written by PDE \n" }) //$NON-NLS-1$
	void preservesNonexactPreambleText(String body) throws Exception {
		String source= "<!--" + body + "-->" + SCHEMA; //$NON-NLS-1$ //$NON-NLS-2$
		for (boolean indent : new boolean[] { false, true }) {
			String output= transform(source, indent);
			assertEquals(List.of("COMMENT:" + body, "ELEMENT:schema"), documentNodes(parse(output))); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(output, transform(output, indent));
		}
	}

	static Stream<Arguments> otherContexts() {
		String sameNamespace= SCHEMA.replace("<schema>", "<schema xmlns=\"" + XSD + "\" xmlns:p=\"" + XSD + "\">"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		return Stream.of(
				Arguments.of("generic schema", "<schema><element name=\"item\"/></schema>"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("descriptor", SCHEMA.replace("schema>", "plugin>")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of("foreign root", SCHEMA.replace("<schema>", "<schema xmlns=\"urn:other\">")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of("prefixed XSD", "<xs:schema xmlns:xs=\"" + XSD + "\"><xs:annotation><xs:appInfo>" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						+ "<xs:meta.schema plugin=\"p\" id=\"i\" name=\"n\"/></xs:appInfo></xs:annotation></xs:schema>"), //$NON-NLS-1$
				Arguments.of("same-namespace prefixed annotation", sameNamespace.replace("<annotation>", "<p:annotation>") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						.replace("</annotation>", "</p:annotation>")), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("same-namespace prefixed appinfo", sameNamespace.replace("<appInfo>", "<p:appInfo>") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						.replace("</appInfo>", "</p:appInfo>")), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("same-namespace prefixed metadata", sameNamespace.replace("<meta.schema ", "<p:meta.schema ")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of("foreign metadata", SCHEMA.replace("<meta.schema ", "<meta.schema xmlns=\"urn:other\" ")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of("prefixed appinfo", SCHEMA.replace("<appInfo>", "<p:appInfo xmlns:p=\"urn:other\">") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						.replace("</appInfo>", "</p:appInfo>")), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("nested annotation", "<schema><element name=\"item\"><annotation><appInfo>" //$NON-NLS-1$ //$NON-NLS-2$
						+ METADATA + "</appInfo></annotation></element></schema>"), //$NON-NLS-1$
				Arguments.of("namespace switched annotation", SCHEMA.replace("<schema>", "<schema xmlns=\"" + XSD + "\">") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
						.replace("<annotation>", "<annotation xmlns=\"\">"))); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@ParameterizedTest(name = "{0}") //$NON-NLS-1$
	@MethodSource("otherContexts") //$NON-NLS-1$
	void preservesTheExactCommentOutsideTheRecognizedPdeContext(String context, String body) throws Exception {
		for (boolean indent : new boolean[] { false, true }) {
			String output= transform(PREAMBLE + body, indent);
			assertEquals("COMMENT: Schema file written by PDE ", documentNodes(parse(output)).getFirst(), context); //$NON-NLS-1$
			assertEquals(1, commentCount(parse(output)), context);
			assertEquals(output, transform(output, indent), context);
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesRootXmlSpaceAndItsHeader(boolean indent) throws Exception {
		String source= PREAMBLE + SCHEMA.replace("<schema>", "<schema xml:space=\"preserve\">\n  "); //$NON-NLS-1$ //$NON-NLS-2$
		String output= transform(source, indent);
		assertEquals(1, commentCount(parse(output)));
		assertTrue(parse(source).getDocumentElement().isEqualNode(parse(output).getDocumentElement()));
		assertEquals(output, transform(output, indent));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesDocumentationMixedContentUnknownPayloadAndInternalComments(boolean indent) throws Exception {
		String protectedContent= """
				<element name="item"><annotation><documentation>  Before <pre>  keep
				    spacing  </pre><code> x &lt; y </code><!-- Schema file written by PDE --> after  </documentation></annotation>
				  <unknown xml:space="preserve">  left <part/> right  </unknown>
				</element>
				<element name="blank"><annotation><documentation>   </documentation></annotation></element>
				"""; //$NON-NLS-1$
		String source= PREAMBLE + SCHEMA.replace("<element name=\"item\"/>", protectedContent); //$NON-NLS-1$
		String output= transform(source, indent);
		Document before= parse(source);
		Document after= parse(output);
		assertEquals(2, commentCount(before));
		assertEquals(1, commentCount(after));
		for (String tag : List.of("documentation", "unknown")) { //$NON-NLS-1$ //$NON-NLS-2$
			var oldNodes= before.getElementsByTagName(tag);
			var newNodes= after.getElementsByTagName(tag);
			assertEquals(oldNodes.getLength(), newNodes.getLength());
			for (int i= 0; i < oldNodes.getLength(); i++) {
				assertTrue(oldNodes.item(i).isEqualNode(newNodes.item(i)), tag);
			}
		}
		assertEquals("   ", after.getElementsByTagName("documentation").item(1).getFirstChild().getNodeValue()); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(output, transform(output, indent));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void removesPreambleWhenAHumanCommentContainsACdataExample(boolean indent) throws Exception {
		String comment= "<!-- Example syntax starts with <![CDATA[ -->"; //$NON-NLS-1$
		String output= transform(PREAMBLE + comment + SCHEMA, indent);
		assertEquals(List.of("COMMENT: Example syntax starts with <![CDATA[ ", "ELEMENT:schema"), //$NON-NLS-1$ //$NON-NLS-2$
				documentNodes(parse(output)));
		assertTrue(output.contains(comment));
		assertEquals(output, transform(output, indent));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void removesPreambleWhenAProcessingInstructionContainsACdataExample(boolean indent) throws Exception {
		String instruction= "<?example syntax starts with <![CDATA[ ?>"; //$NON-NLS-1$
		String output= transform(PREAMBLE + instruction + SCHEMA, indent);
		assertEquals(List.of("PI:example:syntax starts with <![CDATA[ ", "ELEMENT:schema"), //$NON-NLS-1$ //$NON-NLS-2$
				documentNodes(parse(output)));
		assertTrue(output.contains(instruction));
		assertEquals(output, transform(output, indent));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void retainsTheValidatedWholeSourceWhenCdataIsPresent(boolean indent) throws Exception {
		String source= DECLARATION + "\n" + PREAMBLE + "\n" + SCHEMA.replace("<element name=\"item\"/>", """
				<element name="item"><annotation><documentation><![CDATA[
				  <pre> literal  spacing </pre>
				]]></documentation></annotation></element>
				"""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals(source, transform(source, indent));
		assertEquals(source, transform(transform(source, indent), indent));
		String combined= SchemaTransformationUtils.transform(source, StandardCharsets.UTF_8, indent,
				PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER, DocumentationPolicy.TRIM_PLAIN_SECTION_BOUNDARIES);
		assertEquals(source, combined);
		assertEquals(source, SchemaTransformationUtils.transform(combined, StandardCharsets.UTF_8, indent,
				PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER, DocumentationPolicy.TRIM_PLAIN_SECTION_BOUNDARIES));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void validatesCdataContainingInputBeforeRetainingIt(boolean indent) {
		String malformed= PREAMBLE + "<schema><![CDATA[literal]]>"; //$NON-NLS-1$
		assertThrows(TransformerException.class, () -> transform(malformed, indent));
		String external= "<!DOCTYPE schema [<!ENTITY blocked SYSTEM \"file:///pde-preamble-must-not-be-read\">]>" //$NON-NLS-1$
				+ PREAMBLE + "<schema><![CDATA[literal]]>&blocked;</schema>"; //$NON-NLS-1$
		TransformerException failure= assertThrows(TransformerException.class, () -> transform(external, indent));
		assertTrue(failure.getMessage().contains("accessExternalDTD")); //$NON-NLS-1$
		assertFalse(failure.getMessage().contains("FileNotFoundException")); //$NON-NLS-1$
	}

	private static String transform(String source, boolean indent) throws Exception {
		return SchemaTransformationUtils.transform(source, StandardCharsets.UTF_8, indent);
	}

	private static Document parse(String source) throws Exception {
		DocumentBuilderFactory factory= DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
		return factory.newDocumentBuilder().parse(new InputSource(new StringReader(source)));
	}

	private static List<String> documentNodes(Document document) {
		List<String> nodes= new ArrayList<>();
		for (Node node= document.getFirstChild(); node != null; node= node.getNextSibling()) {
			switch (node.getNodeType()) {
				case Node.COMMENT_NODE -> nodes.add("COMMENT:" + node.getNodeValue()); //$NON-NLS-1$
				case Node.PROCESSING_INSTRUCTION_NODE -> nodes.add("PI:" + node.getNodeName() + ":" + node.getNodeValue()); //$NON-NLS-1$ //$NON-NLS-2$
				case Node.ELEMENT_NODE -> nodes.add("ELEMENT:" + node.getNodeName()); //$NON-NLS-1$
				default -> { }
			}
		}
		return nodes;
	}

	private static int commentCount(Node node) {
		int count= node.getNodeType() == Node.COMMENT_NODE ? 1 : 0;
		for (Node child= node.getFirstChild(); child != null; child= child.getNextSibling()) {
			count+= commentCount(child);
		}
		return count;
	}

	private static String lastComment(Node parent) {
		for (Node child= parent.getLastChild(); child != null; child= child.getPreviousSibling()) {
			if (child.getNodeType() == Node.COMMENT_NODE) {
				return child.getNodeValue();
			}
		}
		throw new AssertionError("Expected the protected comment inside the schema"); //$NON-NLS-1$
	}
}
