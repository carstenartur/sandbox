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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils.DocumentationPolicy;
import org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils.PdeSchemaTarget;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

class SchemaTransformationDocumentationTest {

	@ParameterizedTest
	@CsvSource({
			"false, false, false", //$NON-NLS-1$
			"false, false, true", //$NON-NLS-1$
			"false, true, false", //$NON-NLS-1$
			"false, true, true", //$NON-NLS-1$
			"true, false, false", //$NON-NLS-1$
			"true, false, true", //$NON-NLS-1$
			"true, true, false", //$NON-NLS-1$
			"true, true, true" }) //$NON-NLS-1$
	void combinesIndependentPoliciesOnTheFirstPass(boolean modernize, boolean trim, boolean indent) throws Exception {
		String source= """
				<schema>
				  <annotation><appInfo>
				    <meta.schema plugin="example" id="items" name="Items"> \t </meta.schema>
				  </appInfo><documentation>  Overview  stays&#9;raw  </documentation></annotation>
				  <annotation><appInfo>
				    <meta.section type="apiinfo"> \t </meta.section>
				  </appInfo><documentation> &#9;Alpha  beta&#10;  Gamma&#9;delta &#13; </documentation></annotation>
				</schema>
				"""; //$NON-NLS-1$
		PdeSchemaTarget target= modernize ? PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER : PdeSchemaTarget.PRESERVE;
		DocumentationPolicy policy= trim ? DocumentationPolicy.TRIM_PLAIN_SECTION_BOUNDARIES : DocumentationPolicy.PRESERVE;
		String result= SchemaTransformationUtils.transform(source, StandardCharsets.UTF_8, indent, target, policy);
		Element root= parse(result);
		assertEquals(2, root.getElementsByTagName(modernize ? "appinfo" : "appInfo").getLength(), result); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(0, root.getElementsByTagName(modernize ? "appInfo" : "appinfo").getLength(), result); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("", root.getElementsByTagName("meta.schema").item(0).getTextContent()); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("", root.getElementsByTagName("meta.section").item(0).getTextContent()); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("  Overview  stays\traw  ", root.getElementsByTagName("documentation").item(0).getTextContent()); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(trim ? "Alpha  beta\n  Gamma\tdelta" : " \tAlpha  beta\n  Gamma\tdelta \r ", //$NON-NLS-1$ //$NON-NLS-2$
				root.getElementsByTagName("documentation").item(1).getTextContent()); //$NON-NLS-1$
		assertTrue(result.contains(indent ? "\n\t<annotation>" : "<schema><annotation>"), result); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(result, SchemaTransformationUtils.transform(result, StandardCharsets.UTF_8, indent, target, policy));
		if (!trim) {
			assertEquals(result, SchemaTransformationUtils.transform(source, StandardCharsets.UTF_8, indent, target));
		}
		if (!modernize) {
			assertEquals(result, transform(source, indent, policy));
		}
		if (!modernize && !trim) {
			assertEquals(result, SchemaTransformationUtils.transform(source, StandardCharsets.UTF_8, indent));
		}
	}

	@Test
	void rejectsNullCombinedPolicySelections() {
		assertThrows(NullPointerException.class, () -> SchemaTransformationUtils.transform("<schema/>", //$NON-NLS-1$
				StandardCharsets.UTF_8, false, null, DocumentationPolicy.PRESERVE));
		assertThrows(NullPointerException.class, () -> SchemaTransformationUtils.transform("<schema/>", //$NON-NLS-1$
				StandardCharsets.UTF_8, false, PdeSchemaTarget.PRESERVE, null));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void trimsOnlyOuterXmlWhitespaceInPlainReferenceSections(boolean indent) throws Exception {
		String source= schema(section("apiinfo", """
				<documentation xml:lang="en" custom="keep"> &#9;
				  First  line
				    second&#9;line  &#13;&#10; </documentation>
				""")); //$NON-NLS-1$ //$NON-NLS-2$
		String defaultOutput= SchemaTransformationUtils.transform(source, StandardCharsets.UTF_8, indent);
		assertEquals(defaultOutput, transform(source, indent, DocumentationPolicy.PRESERVE));
		String trimmed= transform(source, indent, DocumentationPolicy.TRIM_PLAIN_SECTION_BOUNDARIES);
		var docs= parse(trimmed).getElementsByTagName("documentation"); //$NON-NLS-1$
		assertEquals("  Raw overview  ", docs.item(0).getTextContent()); //$NON-NLS-1$
		assertEquals("First  line\n    second\tline", docs.item(1).getTextContent()); //$NON-NLS-1$
		assertEquals("keep", ((Element) docs.item(1)).getAttribute("custom")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("en", ((Element) docs.item(1)).getAttributeNS(XMLConstants.XML_NS_URI, "lang")); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(parse(defaultOutput).getElementsByTagName("documentation").item(1).getTextContent().startsWith(" \t\n")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(trimmed, transform(trimmed, indent, DocumentationPolicy.TRIM_PLAIN_SECTION_BOUNDARIES));
	}

	@ParameterizedTest
	@ValueSource(strings = { "appInfo", "appinfo" }) //$NON-NLS-1$ //$NON-NLS-2$
	void recognizesBothPdeMetadataConventions(String appinfo) throws Exception {
		for (String type : new String[] { "since", "apiinfo", "implementation", "copyright" }) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			String source= schema(section(type, "<documentation>  Plain text  </documentation>")) //$NON-NLS-1$
					.replace("appInfo", appinfo); //$NON-NLS-1$
			assertEquals("Plain text", parse(trim(source)).getElementsByTagName("documentation").item(1).getTextContent()); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void retainsBlankDocumentationForReleasedPdeReaders(boolean indent) throws Exception {
		String source= schema(section("apiinfo", "<documentation> \n\t </documentation>")); //$NON-NLS-1$ //$NON-NLS-2$
		var doc= parse(transform(source, indent, DocumentationPolicy.TRIM_PLAIN_SECTION_BOUNDARIES))
				.getElementsByTagName("documentation").item(1); //$NON-NLS-1$
		assertNotNull(doc.getFirstChild());
		assertEquals(" \n\t ", doc.getTextContent()); //$NON-NLS-1$
	}

	@Test
	void preservesLiteralMarkupCommentsAndExplicitWhitespace() throws Exception {
		for (String doc : new String[] {
				"<documentation>  &lt;pre&gt;  example\n  &lt;/pre&gt;  </documentation>", //$NON-NLS-1$
				"<documentation>  &lt;code&gt;a  b&lt;/code&gt;  </documentation>", //$NON-NLS-1$
				"<documentation>  &lt;b&gt;markup&lt;/b&gt;  </documentation>", //$NON-NLS-1$
				"<documentation>  <pre>  example  </pre>  </documentation>", //$NON-NLS-1$
				"<documentation><!--first-->  text  </documentation>", //$NON-NLS-1$
				"<documentation><?keep test?>  text  </documentation>", //$NON-NLS-1$
				"<documentation xml:space=\"preserve\">  text  </documentation>" }) { //$NON-NLS-1$
			assertDocumentationUnchanged(schema(section("apiinfo", doc))); //$NON-NLS-1$
		}
		assertDocumentationUnchanged(schema(section("apiinfo", "<documentation>  text  </documentation>") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("<annotation>", "<annotation xml:space=\"preserve\">"))); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void preservesOverviewElementAttributeExamplesAndUnknownSections() throws Exception {
		String description= "<documentation>  untouched\n  text  </documentation>"; //$NON-NLS-1$
		assertDocumentationUnchanged(schema("<element name=\"item\"><annotation>" + description //$NON-NLS-1$
				+ "</annotation><complexType><attribute name=\"name\"><annotation>" + description //$NON-NLS-1$
				+ "</annotation></attribute></complexType></element>")); //$NON-NLS-1$
		for (String type : new String[] { "overview", "examples", "markup", "unknown", "since|apiinfo", "apiinfo|implementation" }) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
			assertDocumentationUnchanged(schema(section(type, description)));
		}
		assertDocumentationUnchanged(schema("<unknown><annotation>" + description + "</annotation></unknown>")); //$NON-NLS-1$ //$NON-NLS-2$
		assertDocumentationUnchanged("<schema>" + section("apiinfo", description) + "</schema>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@Test
	void preservesAmbiguousSectionOrderAndPayloads() throws Exception {
		String ordinary= section("apiinfo", "<documentation>  untouched  </documentation>"); //$NON-NLS-1$ //$NON-NLS-2$
		assertDocumentationUnchanged(schema(ordinary.replace("</appInfo>", "<unknown/></appInfo>"))); //$NON-NLS-1$ //$NON-NLS-2$
		assertDocumentationUnchanged(schema(ordinary.replace("<appInfo>", "<appInfo>unknown mixed text"))); //$NON-NLS-1$ //$NON-NLS-2$
		assertDocumentationUnchanged(schema(ordinary.replace("</annotation>", "<unknown/></annotation>"))); //$NON-NLS-1$ //$NON-NLS-2$
		assertDocumentationUnchanged(schema("<annotation><documentation>  untouched  </documentation><appInfo><meta.section type=\"apiinfo\"/></appInfo></annotation>")); //$NON-NLS-1$
		assertDocumentationUnchanged(schema(ordinary).replace("<schema>", "<schema xmlns=\"http://www.w3.org/2001/XMLSchema\">")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@ParameterizedTest
	@ValueSource(strings = { "<!-- unknown payload -->", "<?unknown payload?>" }) //$NON-NLS-1$ //$NON-NLS-2$
	void preservesReferenceBoundariesWhenAnnotationHasNonElementPayload(String payload) throws Exception {
		String metadata= "<appInfo><meta.section type=\"apiinfo\"/></appInfo>"; //$NON-NLS-1$
		String documentation= "<documentation>  untouched  </documentation>"; //$NON-NLS-1$
		for (String contents : new String[] { payload + metadata + documentation,
				metadata + payload + documentation, metadata + documentation + payload }) {
			String source= schema("<annotation>\n  " + contents + "\n</annotation>"); //$NON-NLS-1$ //$NON-NLS-2$
			for (boolean indent : new boolean[] { false, true }) {
				String transformed= transform(source, indent, DocumentationPolicy.TRIM_PLAIN_SECTION_BOUNDARIES);
				assertEquals("  untouched  ", parse(transformed).getElementsByTagName("documentation").item(1).getTextContent()); //$NON-NLS-1$ //$NON-NLS-2$
				assertTrue(transformed.contains(payload), transformed);
				assertEquals(transformed, transform(transformed, indent, DocumentationPolicy.TRIM_PLAIN_SECTION_BOUNDARIES));
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "<!-- literal <![CDATA[ token -->", "<?keep literal <![CDATA[ token?>" }) //$NON-NLS-1$ //$NON-NLS-2$
	void trimsReferenceBoundariesWhenCdataMarkerIsOnlyCommentOrInstructionText(String marker) throws Exception {
		String source= schema(marker + section("apiinfo", "<documentation>  Plain  text  </documentation>")); //$NON-NLS-1$ //$NON-NLS-2$
		for (boolean indent : new boolean[] { false, true }) {
			String transformed= transform(source, indent, DocumentationPolicy.TRIM_PLAIN_SECTION_BOUNDARIES);
			assertEquals("Plain  text", parse(transformed).getElementsByTagName("documentation").item(1).getTextContent()); //$NON-NLS-1$ //$NON-NLS-2$
			assertTrue(transformed.contains(marker), transformed);
			assertEquals(transformed, transform(transformed, indent, DocumentationPolicy.TRIM_PLAIN_SECTION_BOUNDARIES));
		}
	}

	@Test
	void keepsNonXmlWhitespaceAndInternalSpacing() throws Exception {
		String source= schema(section("apiinfo", "<documentation>  &#160;  text&#9;with  spaces  &#160;  </documentation>")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("\u00a0  text\twith  spaces  \u00a0", parse(trim(source)).getElementsByTagName("documentation").item(1).getTextContent()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void preservesSupplementaryUnicodeAcrossTrimChunks() throws Exception {
		String source= schema(section("apiinfo", "<documentation>  😀 text 😀  </documentation>")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("😀 text 😀", parse(trim(source)).getElementsByTagName("documentation").item(1).getTextContent()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void handlesLongBoundariesWithoutLinearRecursionDepth() throws Exception {
		String source= schema(section("apiinfo", "<documentation>" + " \t".repeat(10000) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ "value" + " \n".repeat(10000) + "</documentation>")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("value", parse(trim(source)).getElementsByTagName("documentation").item(1).getTextContent()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void retainsCdataSourceWithoutSerialization() throws Exception {
		String source= schema(section("apiinfo", "<documentation><![CDATA[  literal  ]]></documentation>")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(source, trim(source));
	}

	@Test
	void validatesMalformedCdataBeforeRetainingSource() {
		String source= "<schema><![CDATA[unfinished"; //$NON-NLS-1$
		for (DocumentationPolicy policy : DocumentationPolicy.values()) {
			for (boolean indent : new boolean[] { false, true }) {
				assertThrows(TransformerException.class, () -> transform(source, indent, policy));
			}
		}
	}

	@Test
	void blocksExternalEntitiesBeforeRetainingCdataSource(@TempDir Path directory) throws Exception {
		Path external= Files.writeString(directory.resolve("external.txt"), "external content"); //$NON-NLS-1$ //$NON-NLS-2$
		String source= "<!DOCTYPE schema [<!ENTITY external SYSTEM \"" + external.toUri() //$NON-NLS-1$
				+ "\">]><schema><![CDATA[  literal  ]]>&external;</schema>"; //$NON-NLS-1$
		for (DocumentationPolicy policy : DocumentationPolicy.values()) {
			for (boolean indent : new boolean[] { false, true }) {
				assertThrows(TransformerException.class, () -> transform(source, indent, policy));
			}
		}
	}

	private static String schema(String body) {
		return "<schema><annotation><appInfo><meta.schema plugin=\"example\" id=\"items\" name=\"Items\"/></appInfo><documentation>  Raw overview  </documentation></annotation>" //$NON-NLS-1$
				+ body + "</schema>"; //$NON-NLS-1$
	}

	private static String section(String type, String documentation) {
		return "<annotation><appInfo><meta.section type=\"" + type + "\"/></appInfo>" + documentation + "</annotation>"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static String trim(String source) throws Exception {
		return transform(source, false, DocumentationPolicy.TRIM_PLAIN_SECTION_BOUNDARIES);
	}

	private static String transform(String source, boolean indent, DocumentationPolicy policy) throws Exception {
		return SchemaTransformationUtils.transform(source, StandardCharsets.UTF_8, indent, policy);
	}

	private static void assertDocumentationUnchanged(String source) throws Exception {
		var before= parse(source).getElementsByTagNameNS("*", "documentation"); //$NON-NLS-1$ //$NON-NLS-2$
		var after= parse(trim(source)).getElementsByTagNameNS("*", "documentation"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(before.getLength(), after.getLength());
		for (int i= 0; i < before.getLength(); i++) {
			assertTrue(before.item(i).isEqualNode(after.item(i)), source);
		}
	}

	private static Element parse(String source) throws Exception {
		DocumentBuilderFactory factory= DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
		return factory.newDocumentBuilder().parse(new InputSource(new StringReader(source))).getDocumentElement();
	}
}
