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
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.jdt.ui.tests.quickfix.XMLTestUtils.assertXmlSemanticallyEqual;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils;

/** Tests the user-visible XML indentation preference. */
public class XMLIndentationPreferenceTest {

	private static final String COMPACT_XML= """
			<?xml version="1.0" encoding="UTF-8"?>
			<plugin><extension point="org.eclipse.ui.views"><view id="sample"/></extension></plugin>
			""";

	@Test
	public void testIndentPreferenceChangesSerializedLayout() throws Exception {
		Path input= Files.createTempFile("xml-indent-preference", ".xml");
		try {
			Files.writeString(input, COMPACT_XML);

			String compact= SchemaTransformationUtils.transform(input, false);
			String indented= SchemaTransformationUtils.transform(input, true);

			assertXmlSemanticallyEqual(COMPACT_XML, compact);
			assertXmlSemanticallyEqual(COMPACT_XML, indented);
			assertTrue(indented.lines().count() > compact.lines().count(),
					"Enabling indentation must add visible line structure");
			assertTrue(indented.length() > compact.length(),
					"Indented output must be larger than compact output for nested elements");
		} finally {
			Files.deleteIfExists(input);
		}
	}

	@Test
	public void testDefaultTransformationUsesCompactMode() throws Exception {
		Path input= Files.createTempFile("xml-default-indent", ".xml");
		try {
			Files.writeString(input, COMPACT_XML);

			assertEquals(SchemaTransformationUtils.transform(input, false),
					SchemaTransformationUtils.transform(input),
					"The default transformation must remain the compact mode");
		} finally {
			Files.deleteIfExists(input);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "\n", "\r\n" }) //$NON-NLS-1$ //$NON-NLS-2$
	public void testMeaningfulMultilineTextIsNotNormalized(String lineDelimiter) throws Exception {
		String xml= """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin><description>first line

				    second line</description></plugin>
				""".replace("\n", lineDelimiter); //$NON-NLS-1$
		Path input= Files.createTempFile("xml-meaningful-whitespace", ".xml");
		try {
			Files.writeString(input, xml);

			String transformed= SchemaTransformationUtils.transform(input, false);

			// XML parsers normalize physical CRLF to LF. Compare the actual text
			// nodes, retaining blank lines and leading spaces in meaningful text.
			assertXmlSemanticallyEqual(xml, transformed);
		} finally {
			Files.deleteIfExists(input);
		}
	}

	@Test
	public void semanticComparisonRejectsChangedMeaningfulWhitespace() {
		String xml= "<description>first line\n\n    second line</description>"; //$NON-NLS-1$
		assertThrows(AssertionError.class,
				() -> assertXmlSemanticallyEqual(xml, xml.replace("\n\n", "\n"))); //$NON-NLS-1$ //$NON-NLS-2$
		assertThrows(AssertionError.class,
				() -> assertXmlSemanticallyEqual(xml, xml.replace("    second line", "second line"))); //$NON-NLS-1$ //$NON-NLS-2$
		assertThrows(AssertionError.class,
				() -> assertXmlSemanticallyEqual(xml, xml.replace("second line", "changed text"))); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
