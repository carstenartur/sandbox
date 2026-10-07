/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.InputSource;

/** The serializer must not inherit the host OS line separator. */
class XMLSerializationLineSeparatorTest {

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesMeaningfulMultilineTextWithCanonicalLf(boolean indent) throws Exception {
		for (String separator : new String[] { "\n", "\r\n", "\r" }) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			String xml= "<plugin><description>first line" + separator + separator //$NON-NLS-1$
					+ "    second line</description></plugin>"; //$NON-NLS-1$
			String transformed= SchemaTransformationUtils.transform(xml, StandardCharsets.UTF_8, indent);
			assertTrue(transformed.contains("first line\n\n    second line"), //$NON-NLS-1$
					"Serialization must preserve normalized text, not substitute the OS delimiter"); //$NON-NLS-1$
			assertFalse(transformed.contains("\r"), "Serialized XML must use canonical LF"); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void generatedMarkupLayoutUsesCanonicalLf(boolean indent) throws Exception {
		String transformed= SchemaTransformationUtils.transform(
				"<plugin><extension><view id=\"sample\"/></extension></plugin>", //$NON-NLS-1$
				StandardCharsets.UTF_8, indent);
		assertFalse(transformed.contains("\r"), "Markup layout must not depend on the host OS"); //$NON-NLS-1$ //$NON-NLS-2$
		if (indent) {
			assertTrue(transformed.contains("\n"), "The indented fixture must exercise generated line breaks"); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void explicitCarriageReturnReferencesKeepTheirXmlValue(boolean indent) throws Exception {
		String xml= "<description attribute=\"a&#13;b\">first&#13;second\n\n    third</description>"; //$NON-NLS-1$
		String transformed= SchemaTransformationUtils.transform(xml, StandardCharsets.UTF_8, indent);
		var factory= DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); //$NON-NLS-1$
		var document= factory.newDocumentBuilder().parse(new InputSource(new StringReader(transformed)));
		assertEquals("a\rb", document.getDocumentElement().getAttribute("attribute")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("first\rsecond\n\n    third", document.getDocumentElement().getTextContent()); //$NON-NLS-1$
	}
}
