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

	private static final String COMMENTED_MARKUP= """
			<?xml version="1.0" encoding="UTF-8"?>
			<plugin>
			<!--
			    <extension
			        point="org.eclipse.ui.example">
			        <example/>
			    </extension>
			-->
			    <extension point="org.eclipse.ui.real"/>
			</plugin>
			"""; //$NON-NLS-1$

	@Test
	void compactTransformationPreservesTextNodeWhitespace() throws Exception {
		assertSemanticsPreserved(TEXT_CONTENT, false);
	}

	@Test
	void indentedTransformationPreservesTextNodeWhitespace() throws Exception {
		assertSemanticsPreserved(TEXT_CONTENT, true);
	}

	@Test
	void transformationPreservesCommentedMarkup() throws Exception {
		assertSemanticsPreserved(COMMENTED_MARKUP, false);
		assertSemanticsPreserved(COMMENTED_MARKUP, true);
	}

	@Test
	void indentationInsideCommentsCdataAndProcessingInstructionsIsPreserved() {
		String source= """
				<root>
				<!--
				    <comment-content/>
				-->
				<![CDATA[
				    <cdata-content/>
				]]>
				<?target
				    <processing-instruction-content/>
				?>
				    <real-markup/>
				</root>
				"""; //$NON-NLS-1$
		String expected= source.replace("    <real-markup/>", "\t<real-markup/>"); //$NON-NLS-1$ //$NON-NLS-2$

		assertEquals(expected, SchemaTransformationUtils.convertMarkupIndentationToTabs(source));
	}

	@Test
	void unfinishedCommentContentIsNotNormalizedOrReported() {
		String source= """
				<root>
				<!--
				    <comment-content/>
				    <still-comment-content/>
				"""; //$NON-NLS-1$

		assertEquals(source, SchemaTransformationUtils.convertMarkupIndentationToTabs(source));
		assertNull(SchemaTransformationUtils.firstConvertibleMarkupIndentation(source));
	}

	@Test
	void markerLocationSkipsProtectedXmlContent() {
		SchemaTransformationUtils.IndentationFinding finding=
				SchemaTransformationUtils.firstConvertibleMarkupIndentation("""
						<root>
						<!--
						    <comment-content/>
						-->
						<![CDATA[
						    <cdata-content/>
						]]>
						<?target
						    <processing-instruction-content/>
						?>
						    <real-markup/>
						</root>
						"""); //$NON-NLS-1$

		assertNotNull(finding);
		assertEquals(11, finding.lineNumber());
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

	@Test
	void greaterThanCharacterInTextDoesNotLookLikeMarkup() {
		String source= """
				<root>
				Meaningful text >
				    <child/>
				</root>
				"""; //$NON-NLS-1$

		assertEquals(source, SchemaTransformationUtils.convertMarkupIndentationToTabs(source));
		assertNull(SchemaTransformationUtils.firstConvertibleMarkupIndentation(source));
	}

	@Test
	void greaterThanCharacterInAttributeDoesNotHideMarkupEnd() {
		String source= """
				<root description="a > b">
				    <child/>
				</root>
				"""; //$NON-NLS-1$
		String expected= source.replace("    <child/>", "\t<child/>"); //$NON-NLS-1$ //$NON-NLS-2$

		assertEquals(expected, SchemaTransformationUtils.convertMarkupIndentationToTabs(source));
		assertEquals(2, SchemaTransformationUtils.firstConvertibleMarkupIndentation(source).lineNumber());
	}

	@Test
	void protectedMarkupIsSkippedWhenFindingThePreviousMarkupEnd() {
		String source= """
				<root>
				<!-- comment > -->
				<?target value=">"?>
				    <child/>
				</root>
				"""; //$NON-NLS-1$
		String expected= source.replace("    <child/>", "\t<child/>"); //$NON-NLS-1$ //$NON-NLS-2$

		assertEquals(expected, SchemaTransformationUtils.convertMarkupIndentationToTabs(source));
		assertEquals(4, SchemaTransformationUtils.firstConvertibleMarkupIndentation(source).lineNumber());
	}

	@Test
	void protectedMarkupDoesNotHidePrecedingMeaningfulText() {
		String source= """
				<root>Meaningful text >
				<!-- comment > -->
				    <child/>
				</root>
				"""; //$NON-NLS-1$

		assertEquals(source, SchemaTransformationUtils.convertMarkupIndentationToTabs(source));
		assertNull(SchemaTransformationUtils.firstConvertibleMarkupIndentation(source));
	}

	@Test
	void indentationAfterCdataRemainsCharacterData() {
		String source= """
				<root><![CDATA[value > value]]>
				    <child/>
				</root>
				"""; //$NON-NLS-1$

		assertEquals(source, SchemaTransformationUtils.convertMarkupIndentationToTabs(source));
		assertNull(SchemaTransformationUtils.firstConvertibleMarkupIndentation(source));
	}

	private static void assertSemanticsPreserved(String source, boolean enableIndent) throws Exception {
		String transformed= SchemaTransformationUtils.transform(source,
				StandardCharsets.UTF_8, enableIndent);
		assertTrue(XMLTestUtils.isXmlSemanticallyEqualWithComments(source, transformed),
				() -> "Transformation changed XML text or comment content:\n" + transformed); //$NON-NLS-1$
	}
}
