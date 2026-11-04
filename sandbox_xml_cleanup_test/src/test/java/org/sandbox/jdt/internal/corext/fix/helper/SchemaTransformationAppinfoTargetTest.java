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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils.PdeSchemaTarget;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

class SchemaTransformationAppinfoTargetTest {

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void modernizesOnlyWhenTheCallerExplicitlySelectsTheSupportedTarget(boolean indent) throws Exception {
		String source= """
				<schema targetNamespace="org.example">
				  <annotation><appInfo source="kept" custom="attribute">
				    <meta.schema plugin="org.example" id="items" name="Items" extra="kept"/>
				  </appInfo><documentation>   </documentation></annotation>
				  <element name="item"><annotation><appInfo><meta.element labelAttribute="name"/></appInfo></annotation>
				    <complexType><attribute name="name"><annotation><appInfo><meta.attribute kind="string"/></appInfo></annotation></attribute></complexType>
				  </element>
				  <annotation><appInfo><meta.section type="examples"/></appInfo><documentation>&lt;pre&gt;  keep  &lt;/pre&gt;</documentation></annotation>
				</schema>
				"""; //$NON-NLS-1$
		String preserved= SchemaTransformationUtils.transform(source, StandardCharsets.UTF_8, indent);
		assertEquals(4, parse(preserved).getElementsByTagName("appInfo").getLength()); //$NON-NLS-1$
		assertEquals(preserved, transform(source, indent, PdeSchemaTarget.PRESERVE));
		String modernized= transform(source, indent, PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER);
		assertEquals(4, parse(modernized).getElementsByTagName("appinfo").getLength(), modernized); //$NON-NLS-1$
		assertEquals(0, parse(modernized).getElementsByTagName("appInfo").getLength()); //$NON-NLS-1$
		assertTrue(parse(preserved.replace("appInfo", "appinfo")).isEqualNode(parse(modernized))); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(modernized, transform(modernized, indent, PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void formatsRecognizedMetadataOnTheModernizingPass(boolean indent) throws Exception {
		for (String namespace : new String[] { "", " xmlns=\"http://www.w3.org/2001/XMLSchema\"" }) { //$NON-NLS-1$ //$NON-NLS-2$
			String source= """
					<schema%s><annotation><appInfo xmlns:t="urn:tokens" type="t:Name" custom="keep">
					    <meta.schema name="Items"> \t </meta.schema>
					    <!-- keep --> <?keep metadata?>
					    <meta.section type="examples">  </meta.section>
					</appInfo><documentation>  literal  </documentation></annotation></schema>
					""".formatted(namespace); //$NON-NLS-1$
			String expected= indent ? """
					<schema%s>
						<annotation>
							<appinfo xmlns:t="urn:tokens" type="t:Name" custom="keep">
								<meta.schema name="Items"/>
								<!-- keep -->
								<?keep metadata?>
								<meta.section type="examples"/>
							</appinfo>
							<documentation>  literal  </documentation>
						</annotation>
					</schema>
					""".formatted(namespace) //$NON-NLS-1$
					: ("<schema%s><annotation><appinfo xmlns:t=\"urn:tokens\" type=\"t:Name\" custom=\"keep\">" //$NON-NLS-1$
							+ "<meta.schema name=\"Items\"/><!-- keep --><?keep metadata?><meta.section type=\"examples\"/>" //$NON-NLS-1$
							+ "</appinfo><documentation>  literal  </documentation></annotation></schema>").formatted(namespace); //$NON-NLS-1$
			String result= transform(source, indent, PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER);
			assertTrue(parse(expected).isEqualNode(parse(result)), result);
			assertEquals(result, transform(result, indent, PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "xs:", "pappInfo:", "appInfo:" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	void retainsSchemaNamespaceAndPrefix(String prefix) throws Exception {
		String ns= prefix.isEmpty() ? "" : " xmlns:%s=\"http://www.w3.org/2001/XMLSchema\"" //$NON-NLS-1$ //$NON-NLS-2$
				.formatted(prefix.substring(0, prefix.length() - 1));
		String source= "<%1$sschema%2$s><%1$sannotation><%1$sappInfo custom=\"keep\"><%1$smeta.schema name=\"Items\"/></%1$sappInfo></%1$sannotation></%1$sschema>" //$NON-NLS-1$
				.formatted(prefix, ns);
		String result= transform(source, false, PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER);
		Element expected= parse(source);
		Element appinfo= (Element) expected.getElementsByTagNameNS("*", "appInfo").item(0); //$NON-NLS-1$ //$NON-NLS-2$
		expected.getOwnerDocument().renameNode(appinfo, appinfo.getNamespaceURI(), prefix + "appinfo"); //$NON-NLS-1$
		assertTrue(expected.isEqualNode(parse(result)), result);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void retainsDefaultSchemaNamespaceWithoutAddingAPrefix(boolean indent) throws Exception {
		String source= "<schema xmlns=\"http://www.w3.org/2001/XMLSchema\"><annotation><appInfo><meta.schema name=\"Items\"/></appInfo></annotation></schema>"; //$NON-NLS-1$
		String result= transform(source, indent, PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER);
		Element appinfo= (Element) parse(result).getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "appinfo").item(0); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("appinfo", appinfo.getTagName(), result); //$NON-NLS-1$
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesNamespaceDeclarationsUsedOnlyByUnknownAttributeValues(boolean indent) throws Exception {
		String source= "<schema><annotation><appInfo xmlns:t=\"urn:tokens\" type=\"t:Name\"><meta.schema name=\"Items\"/></appInfo></annotation></schema>"; //$NON-NLS-1$
		String result= transform(source, indent, PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER);
		Element appinfo= (Element) parse(result).getElementsByTagName("appinfo").item(0); //$NON-NLS-1$
		assertEquals("urn:tokens", appinfo.lookupNamespaceURI("t")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("t:Name", appinfo.getAttribute("type")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(result, transform(result, indent, PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesUnknownMixedOrOutOfContextPayloads(boolean indent) throws Exception {
		for (String source : new String[] {
				"<schema><annotation><appInfo><other value=\"keep\"/></appInfo></annotation></schema>", //$NON-NLS-1$
				"<schema><annotation><appInfo><meta.schema/><unknown/></appInfo></annotation></schema>", //$NON-NLS-1$
				"<schema><annotation><appInfo>text <meta.schema/></appInfo></annotation></schema>", //$NON-NLS-1$
				"<schema><annotation><appInfo><meta.schema><unknown/></meta.schema></appInfo></annotation></schema>", //$NON-NLS-1$
				"<schema><annotation><appInfo><meta.schema xmlns=\"urn:other\"/></appInfo></annotation></schema>", //$NON-NLS-1$
				"<schema><element><annotation><appInfo><meta.schema/></appInfo></annotation></element></schema>", //$NON-NLS-1$
				"<schema><annotation><appInfo xml:space=\"preserve\"> <meta.schema/> </appInfo></annotation></schema>", //$NON-NLS-1$
				"<schema><annotation><appInfo>   </appInfo></annotation></schema>", //$NON-NLS-1$
				"<plugin><extension><appInfo><meta.schema/></appInfo></extension></plugin>" }) { //$NON-NLS-1$
			String preserved= transform(source, indent, PdeSchemaTarget.PRESERVE);
			assertEquals(preserved, transform(source, indent, PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "<!-- <![CDATA[ -->", "<?keep <![CDATA[ ?>" }) //$NON-NLS-1$ //$NON-NLS-2$
	void modernizesWithCdataMarkersInsideCommentsAndProcessingInstructions(String marker) throws Exception {
		for (boolean indent : new boolean[] { false, true }) {
			String source= "<schema>" + marker //$NON-NLS-1$
					+ "<annotation><appInfo>  <meta.schema name=\"Items\">  </meta.schema>  </appInfo></annotation></schema>"; //$NON-NLS-1$
			String result= transform(source, indent, PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER);
			assertEquals(1, parse(result).getElementsByTagName("appinfo").getLength(), result); //$NON-NLS-1$
			assertEquals(0, parse(result).getElementsByTagName("appInfo").getLength()); //$NON-NLS-1$
			assertTrue(result.contains("<meta.schema name=\"Items\"/>"), result); //$NON-NLS-1$
			assertTrue(result.contains(marker), result);
			assertEquals(result, transform(result, indent, PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER));
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void leavesCdataSourceIntact(boolean indent) throws Exception {
		String source= "<schema><annotation><appInfo><meta.schema/></appInfo><documentation><![CDATA[  literal  ]]></documentation></annotation></schema>"; //$NON-NLS-1$
		assertEquals(source, transform(source, indent, PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER));
	}

	@Test
	void validatesCdataSourceBeforeRetainingIt(@TempDir Path directory) throws Exception {
		assertThrows(TransformerException.class, () -> transform("<schema><![CDATA[unterminated", //$NON-NLS-1$
				false, PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER));
		Path dtd= Files.writeString(directory.resolve("schema.dtd"), "<!ELEMENT schema ANY>", StandardCharsets.UTF_8); //$NON-NLS-1$ //$NON-NLS-2$
		String source= "<!DOCTYPE schema SYSTEM \"" + dtd.toUri() + "\"><schema><![CDATA[literal]]></schema>"; //$NON-NLS-1$ //$NON-NLS-2$
		assertThrows(TransformerException.class, () -> transform(source, false, PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER));
	}

	private static String transform(String source, boolean indent, PdeSchemaTarget target) throws Exception {
		return SchemaTransformationUtils.transform(source, StandardCharsets.UTF_8, indent, target);
	}

	private static Element parse(String source) throws Exception {
		DocumentBuilderFactory factory= DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
		return factory.newDocumentBuilder().parse(new InputSource(new StringReader(source))).getDocumentElement();
	}
}
