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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/** PDE consumes metadata attributes; documentation and extension payloads remain text-sensitive. */
class SchemaTransformationPdeMetadataTest {

	@ParameterizedTest
	@ValueSource(strings = { "appInfo", "appinfo" }) //$NON-NLS-1$ //$NON-NLS-2$
	void compactsRecognizedMetadataWithoutChangingItsSpelling(String appinfo) throws Exception {
		for (String namespace : new String[] { "", " xmlns=\"http://www.w3.org/2001/XMLSchema\"" }) { //$NON-NLS-1$ //$NON-NLS-2$
			String source= """
					<schema%s>
					    <annotation>
					        <%s>\s\s
					            <meta.schema plugin="org.example" id="items" name="Items">\s\s
					            </meta.schema>\s\s
					        </%s>\s\s
					        <documentation>   </documentation>
					    </annotation>
					    <element name="item">
					        <annotation><%s>
					            <meta.element labelAttribute="name" icon="icons/item.png"> \t </meta.element>
					        </%s></annotation>
					        <complexType><attribute name="class"><annotation><%s>
					            <meta.attribute kind="java" basedOn=":org.example.Item">  </meta.attribute>
					        </%s></annotation></attribute></complexType>
					    </element>
					    <annotation><%s>
					        <meta.section type="examples">  </meta.section>
					    </%s><documentation>&lt;pre&gt;  literal  &lt;/pre&gt;  </documentation></annotation>
					</schema>
					""".formatted(namespace, appinfo, appinfo, appinfo, appinfo, appinfo, appinfo, appinfo, appinfo); //$NON-NLS-1$
			String expected= ("<schema%s><annotation><%s>" //$NON-NLS-1$
					+ "<meta.schema plugin=\"org.example\" id=\"items\" name=\"Items\"/>" //$NON-NLS-1$
					+ "</%s><documentation>   </documentation></annotation><element name=\"item\">" //$NON-NLS-1$
					+ "<annotation><%s><meta.element labelAttribute=\"name\" icon=\"icons/item.png\"/></%s></annotation>" //$NON-NLS-1$
					+ "<complexType><attribute name=\"class\"><annotation><%s>" //$NON-NLS-1$
					+ "<meta.attribute kind=\"java\" basedOn=\":org.example.Item\"/></%s></annotation></attribute></complexType>" //$NON-NLS-1$
					+ "</element><annotation><%s><meta.section type=\"examples\"/></%s>" //$NON-NLS-1$
					+ "<documentation>&lt;pre&gt;  literal  &lt;/pre&gt;  </documentation></annotation></schema>") //$NON-NLS-1$
					.formatted(namespace, appinfo, appinfo, appinfo, appinfo, appinfo, appinfo, appinfo, appinfo);
			String transformed= transform(source, false);
			assertTrue(parse(expected).isEqualNode(parse(transformed)), transformed);
			assertTrue(transformed.contains("<meta.section type=\"examples\"/>"), transformed); //$NON-NLS-1$
			assertNotNull(parse(transformed).getElementsByTagName("documentation").item(0).getFirstChild()); //$NON-NLS-1$
			assertEquals(transformed, transform(transformed, false));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "appInfo", "appinfo" }) //$NON-NLS-1$ //$NON-NLS-2$
	void indentsMetadataWithTabsAndRemovesStructuralTrailingSpaces(String appinfo) throws Exception {
		String source= "<schema><annotation><" + appinfo + ">  \n    <meta.schema name=\"Items\"> \t </meta.schema>  \n</" //$NON-NLS-1$ //$NON-NLS-2$
				+ appinfo + "></annotation></schema>"; //$NON-NLS-1$
		String expected= """
				<?xml version="1.0" encoding="UTF-8"?>
				<schema>
					<annotation>
						<%s>
							<meta.schema name="Items"/>
						</%s>
					</annotation>
				</schema>
				""".formatted(appinfo, appinfo); //$NON-NLS-1$
		String transformed= transform(source, true);
		assertEquals(expected, transformed);
		assertEquals(transformed, transform(transformed, true));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesUnknownOrMixedAppinfoPayloads(boolean indent) throws Exception {
		for (String payload : new String[] {
				" \n\t ", //$NON-NLS-1$
				"\n  <meta.schema/>\n  <unknown><pre>  literal  \n</pre></unknown>\n", //$NON-NLS-1$
				"\n  <meta.schema xmlns=\"urn:other\"/>\n", //$NON-NLS-1$
				"before <meta.schema/> after", //$NON-NLS-1$
				"\n  <meta.schema><unknown/> \n </meta.schema>\n", //$NON-NLS-1$
				"\n  <meta.schema>  literal  </meta.schema>\n", //$NON-NLS-1$
				"\n  <meta.attribute kind=\"java\"/>\n" }) { //$NON-NLS-1$
			String source= "<schema><annotation><appinfo>" + payload + "</appinfo></annotation></schema>"; //$NON-NLS-1$ //$NON-NLS-2$
			assertSubtreePreserved(source, "appinfo", indent); //$NON-NLS-1$
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesEffectiveXmlSpaceInMetadata(boolean indent) throws Exception {
		assertSubtreePreserved("<schema><annotation><appinfo xml:space=\"preserve\">\n  <meta.schema/>\n</appinfo></annotation></schema>", //$NON-NLS-1$
				"appinfo", indent); //$NON-NLS-1$
		assertSubtreePreserved("<schema xml:space=\"preserve\">\n  <annotation><appinfo>\n  <meta.schema/>\n</appinfo></annotation></schema>", //$NON-NLS-1$
				"schema", indent); //$NON-NLS-1$
		assertSubtreePreserved("<schema><annotation><appinfo><meta.schema xml:space=\"preserve\"> \n\t </meta.schema></appinfo></annotation></schema>", //$NON-NLS-1$
				"meta.schema", indent); //$NON-NLS-1$
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesWhitespaceOnlyCdataInStructuralContainers(boolean indent) throws Exception {
		for (String content : new String[] {
				"<schema><![CDATA[ \n\t ]]><annotation><appinfo><meta.schema/></appinfo></annotation></schema>", //$NON-NLS-1$
				"<schema><annotation><appinfo><![CDATA[ \n\t ]]><meta.schema/></appinfo></annotation></schema>", //$NON-NLS-1$
				"<schema><annotation><appinfo><meta.schema><![CDATA[ \n\t ]]></meta.schema></appinfo></annotation></schema>" }) { //$NON-NLS-1$
			assertSubtreePreserved(content, "schema", indent); //$NON-NLS-1$
			assertEquals(content, transform(content, indent));
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesMetadataOutsideRecognizedAnnotationContext(boolean indent) throws Exception {
		for (String source : new String[] {
				"<root><annotation><appinfo>\n  <meta.schema>  </meta.schema>\n</appinfo></annotation></root>", //$NON-NLS-1$
				"<schema><element><annotation><appinfo>\n  <meta.schema>  </meta.schema>\n</appinfo></annotation></element></schema>", //$NON-NLS-1$
				"<schema><appinfo>\n  <meta.schema>  </meta.schema>\n</appinfo></schema>" }) { //$NON-NLS-1$
			assertSubtreePreserved(source, "appinfo", indent); //$NON-NLS-1$
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"<schema><![CDATA[ ]]><unclosed></schema>", //$NON-NLS-1$
			"<!DOCTYPE schema [<!ENTITY external SYSTEM 'file:///unavailable'>]><schema><![CDATA[ ]]>&external;</schema>" }) //$NON-NLS-1$
	void validatesCdataFilesBeforeRetainingTheirSource(String source) {
		assertThrows(TransformerException.class, () -> transform(source, false));
	}

	private static void assertSubtreePreserved(String source, String name, boolean indent) throws Exception {
		String transformed= transform(source, indent);
		Element before= parse(source);
		Element after= parse(transformed);
		assertTrue((before.getTagName().equals(name) ? before : before.getElementsByTagName(name).item(0)).isEqualNode(
				after.getTagName().equals(name) ? after : after.getElementsByTagName(name).item(0)), transformed);
		assertEquals(transformed, transform(transformed, indent));
	}

	private static String transform(String source, boolean indent) throws Exception {
		return SchemaTransformationUtils.transform(source, StandardCharsets.UTF_8, indent);
	}

	private static Element parse(String source) throws Exception {
		DocumentBuilderFactory factory= DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setCoalescing(true);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
		return factory.newDocumentBuilder().parse(new InputSource(new StringReader(source))).getDocumentElement();
	}
}
