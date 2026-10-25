/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/** Whitespace-only text is content unless the enclosing vocabulary says otherwise. */
class SchemaTransformationWhitespaceTest {

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void indentedOutputSeparatesPrologAndEndsWithNewline(boolean indent) throws Exception {
		String source= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><?eclipse version=\"3.0\"?>" //$NON-NLS-1$
				+ "<!-- Copyright 2026 Example --><schema><element name=\"sample\"/></schema>"; //$NON-NLS-1$
		String expected= indent ? """
				<?xml version="1.0" encoding="UTF-8"?>
				<?eclipse version="3.0"?>
				<!-- Copyright 2026 Example -->
				<schema>
					<element name="sample"/>
				</schema>
				""" : source; //$NON-NLS-1$
		String transformed= transform(source, indent);
		assertEquals(expected, transformed);
		assertEquals(transformed, transform(transformed, indent));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesSeparatorBetweenInlineElements(boolean indent) throws Exception {
		assertContentPreserved("<p><b>A</b> <i>B</i></p>", indent); //$NON-NLS-1$
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesWhitespaceOnlyContentInUnknownElements(boolean indent) throws Exception {
		assertContentPreserved("<content><blank> \n\t </blank>\n    <child/>\n    <child/>\n</content>", indent); //$NON-NLS-1$
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesUnknownExtensionPayload(boolean indent) throws Exception {
		String source= "<plugin><extension><payload>\n    <schema>\n    <element/>\n    </schema>\n</payload></extension></plugin>"; //$NON-NLS-1$
		String transformed= transform(source, indent);
		assertTrue(parse(source).getElementsByTagName("payload").item(0).isEqualNode( //$NON-NLS-1$
				parse(transformed).getElementsByTagName("payload").item(0)), transformed); //$NON-NLS-1$
		assertEquals(transformed, transform(transformed, indent));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesDocumentationAndPreformattedMarkup(boolean indent) throws Exception {
		for (String namespace : new String[] { "", " xmlns=\"http://www.w3.org/2001/XMLSchema\"" }) { //$NON-NLS-1$ //$NON-NLS-2$
			String source= "<schema" + namespace + "><annotation><documentation><pre>\n    <b>A</b>\n    <i>B</i>\n</pre>" //$NON-NLS-1$ //$NON-NLS-2$
					+ "<p><b>A</b> <i>B</i></p></documentation></annotation></schema>"; //$NON-NLS-1$
			String transformed= transform(source, indent);
			assertTrue(parse(source).getElementsByTagNameNS("*", "documentation").item(0).isEqualNode( //$NON-NLS-1$ //$NON-NLS-2$
					parse(transformed).getElementsByTagNameNS("*", "documentation").item(0)), transformed); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(transformed, transform(transformed, indent));
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesWhitespaceOnlyDocumentationAsText(boolean indent) throws Exception {
		for (String namespace : new String[] { "", " xmlns=\"http://www.w3.org/2001/XMLSchema\"" }) { //$NON-NLS-1$ //$NON-NLS-2$
			String source= "<schema" + namespace + "><annotation><documentation>   </documentation></annotation></schema>"; //$NON-NLS-1$ //$NON-NLS-2$
			String transformed= transform(source, indent);
			var documentation= parse(transformed).getElementsByTagNameNS("*", "documentation").item(0); //$NON-NLS-1$ //$NON-NLS-2$
			// PDE Schema reads this first child even when the documentation is blank.
			assertNotNull(documentation.getFirstChild(), transformed);
			assertEquals("   ", documentation.getFirstChild().getNodeValue()); //$NON-NLS-1$
			assertEquals(transformed, transform(transformed, indent));
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesCommentsProcessingInstructionsAndCdataContent(boolean indent) throws Exception {
		assertContentPreserved("<content><!--\n    <comment/>\n--><?target\n    <instruction/>\n?>" //$NON-NLS-1$
				+ "<![CDATA[\n    <literal/>\n]]>\n    <child/>\n</content>", indent); //$NON-NLS-1$
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesInheritedXmlSpace(boolean indent) throws Exception {
		assertContentPreserved("<plugin xml:space=\"preserve\">\n    <extension>\n        <child/>\n    </extension>\n</plugin>", indent); //$NON-NLS-1$
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void xmlSpaceDefaultDoesNotMakeMixedContentWhitespaceIgnorable(boolean indent) throws Exception {
		assertContentPreserved("<root xml:space=\"preserve\"><p xml:space=\"default\"><b>A</b> <i>B</i></p></root>", indent); //$NON-NLS-1$
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesUnexpectedMixedContentInKnownContainers(boolean indent) throws Exception {
		assertContentPreserved("<plugin>before<extension/> \n    <extension/>after</plugin>", indent); //$NON-NLS-1$
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesUnrecognizedNamespace(boolean indent) throws Exception {
		assertContentPreserved("<plugin xmlns=\"urn:example\">\n    <extension/>\n</plugin>", indent); //$NON-NLS-1$
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " xmlns=\"http://www.w3.org/2001/XMLSchema\"" }) //$NON-NLS-1$ //$NON-NLS-2$
	void compactsKnownSchemaStructure(String namespace) throws Exception {
		String source= "<schema" + namespace + ">\n    <element name=\"sample\">\n        <complexType>\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "            <attribute name=\"id\"/>\n        </complexType>\n    </element>\n</schema>"; //$NON-NLS-1$
		String compact= transform(source, false);
		assertEquals("", parse(compact).getTextContent()); //$NON-NLS-1$
		assertEquals(compact, transform(compact, false));
		String indented= transform(source, true);
		assertTrue(indented.contains("\n\t<element"), indented); //$NON-NLS-1$
		assertEquals(indented, transform(indented, true));
	}

	private static void assertContentPreserved(String source, boolean indent) throws Exception {
		String transformed= transform(source, indent);
		// Deliberately retain whitespace-only nodes: XMLTestUtils removes them.
		assertTrue(parse(source).isEqualNode(parse(transformed)), transformed);
		assertEquals(transformed, transform(transformed, indent));
	}

	private static String transform(String source, boolean indent) throws Exception {
		return SchemaTransformationUtils.transform(source, StandardCharsets.UTF_8, indent);
	}

	private static Element parse(String xml) throws Exception {
		DocumentBuilderFactory factory= DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setCoalescing(true);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); //$NON-NLS-1$
		return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
	}
}
