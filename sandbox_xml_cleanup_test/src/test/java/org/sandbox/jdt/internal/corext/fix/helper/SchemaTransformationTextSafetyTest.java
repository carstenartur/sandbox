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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.ui.tests.quickfix.XMLTestUtils;

/** Regression tests for text-safe XML indentation normalization. */
class SchemaTransformationTextSafetyTest {

	private static final String TEXT_CONTENT= """
			<?xml version="1.0" encoding="UTF-8"?>
			<schema xmlns="http://www.w3.org/2001/XMLSchema">
			    <annotation>
			        <documentation>
			           Text whose trailing indentation belongs to the text node.
			        </documentation>
			    </annotation>
			</schema>
			"""; //$NON-NLS-1$

	@Test
	void compactTransformationPreservesTextNodeWhitespace() throws Exception {
		assertSemanticsPreserved(false);
	}

	@Test
	void indentedTransformationPreservesTextNodeWhitespace() throws Exception {
		assertSemanticsPreserved(true);
	}

	@Test
	void markerLocationIgnoresIndentationThatBelongsToText() {
		assertNull(SchemaTransformationUtils.firstConvertibleMarkupIndentation("""
				<documentation>
				   Meaningful text.
				    </documentation>
				""")); //$NON-NLS-1$
		assertNotNull(SchemaTransformationUtils.firstConvertibleMarkupIndentation("""
				<root>
				    <child/>
				</root>
				""")); //$NON-NLS-1$
	}

	private static void assertSemanticsPreserved(boolean enableIndent) throws Exception {
		String transformed= SchemaTransformationUtils.transform(TEXT_CONTENT,
				StandardCharsets.UTF_8, enableIndent);
		assertTrue(XMLTestUtils.isXmlSemanticallyEqualWithComments(TEXT_CONTENT, transformed),
				() -> "Transformation changed XML text content:\n" + transformed); //$NON-NLS-1$
	}
}
