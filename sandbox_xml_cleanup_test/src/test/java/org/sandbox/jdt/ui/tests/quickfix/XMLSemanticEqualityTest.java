/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for XML semantic equality comparison using DOM-based XMLTestUtils.
 */
public class XMLSemanticEqualityTest {

	/**
	 * Test that identical XML documents are semantically equal.
	 */
	@Test
	public void testIdenticalXmlIsEqual() {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="org.eclipse.ui.views">
				        <view id="test" name="Test"/>
				    </extension>
				</plugin>
				""";
		
		assertTrue(XMLTestUtils.isXmlSemanticallyEqual(xml, xml),
			"Identical XML should be semantically equal");
	}

	/**
	 * Test that XML documents with different whitespace are semantically equal.
	 */
	@Test
	public void testWhitespaceIgnored() {
		String xml1 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="org.eclipse.ui.views">
				        <view id="test" name="Test"/>
				    </extension>
				</plugin>
				""";
		
		String xml2 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin><extension point="org.eclipse.ui.views"><view id="test" name="Test"/></extension></plugin>
				""";
		
		assertTrue(XMLTestUtils.isXmlSemanticallyEqual(xml1, xml2),
			"XML with different whitespace should be semantically equal");
	}

	/**
	 * Test that XML documents with different content are not semantically equal.
	 */
	@Test
	public void testDifferentContentNotEqual() {
		String xml1 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="org.eclipse.ui.views">
				        <view id="test1" name="Test1"/>
				    </extension>
				</plugin>
				""";
		
		String xml2 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="org.eclipse.ui.views">
				        <view id="test2" name="Test2"/>
				    </extension>
				</plugin>
				""";
		
		assertFalse(XMLTestUtils.isXmlSemanticallyEqual(xml1, xml2),
			"XML with different content should not be semantically equal");
	}

	/**
	 * Test that XML documents with different element order are not semantically equal
	 * (unless the specification says otherwise, which standard XML doesn't).
	 */
	@Test
	public void testDifferentElementOrderNotEqual() {
		String xml1 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="org.eclipse.ui.views"/>
				    <extension point="org.eclipse.ui.commands"/>
				</plugin>
				""";
		
		String xml2 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="org.eclipse.ui.commands"/>
				    <extension point="org.eclipse.ui.views"/>
				</plugin>
				""";
		
		assertFalse(XMLTestUtils.isXmlSemanticallyEqual(xml1, xml2),
			"XML with different element order should not be semantically equal");
	}

	/**
	 * Test that XML documents with different attribute values are not semantically equal.
	 */
	@Test
	public void testDifferentAttributeValuesNotEqual() {
		String xml1 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="org.eclipse.ui.views">
				        <view id="test" name="Test1"/>
				    </extension>
				</plugin>
				""";
		
		String xml2 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="org.eclipse.ui.views">
				        <view id="test" name="Test2"/>
				    </extension>
				</plugin>
				""";
		
		assertFalse(XMLTestUtils.isXmlSemanticallyEqual(xml1, xml2),
			"XML with different attribute values should not be semantically equal");
	}

	/**
	 * Test that comments are ignored by default.
	 */
	@Test
	public void testCommentsIgnoredByDefault() {
		String xml1 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <!-- This is a comment -->
				    <extension point="org.eclipse.ui.views">
				        <view id="test" name="Test"/>
				    </extension>
				</plugin>
				""";
		
		String xml2 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="org.eclipse.ui.views">
				        <view id="test" name="Test"/>
				    </extension>
				</plugin>
				""";
		
		assertTrue(XMLTestUtils.isXmlSemanticallyEqual(xml1, xml2),
			"XML with different comments should be semantically equal (comments ignored by default)");
	}

	/**
	 * Test that comments are considered when using isXmlSemanticallyEqualWithComments.
	 */
	@Test
	public void testCommentsConsideredWhenRequested() {
		String xml1 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <!-- Comment 1 -->
				    <extension point="org.eclipse.ui.views">
				        <view id="test" name="Test"/>
				    </extension>
				</plugin>
				""";
		
		String xml2 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <!-- Comment 2 -->
				    <extension point="org.eclipse.ui.views">
				        <view id="test" name="Test"/>
				    </extension>
				</plugin>
				""";
		
		assertFalse(XMLTestUtils.isXmlSemanticallyEqualWithComments(xml1, xml2),
			"XML with different comments should not be equal when preserving comments");
	}

	/**
	 * Test that identical XML with comments is equal when preserving comments.
	 */
	@Test
	public void testIdenticalCommentsEqual() {
		String xml1 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <!-- This is a comment -->
				    <extension point="org.eclipse.ui.views">
				        <view id="test" name="Test"/>
				    </extension>
				</plugin>
				""";
		
		String xml2 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <!-- This is a comment -->
				    <extension point="org.eclipse.ui.views">
				        <view id="test" name="Test"/>
				    </extension>
				</plugin>
				""";
		
		assertTrue(XMLTestUtils.isXmlSemanticallyEqualWithComments(xml1, xml2),
			"XML with identical comments should be equal when preserving comments");
	}

	/**
	 * Test assertXmlSemanticallyEqual throws AssertionError on mismatch.
	 */
	@Test
	public void testAssertThrowsOnMismatch() {
		String xml1 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="test1"/>
				</plugin>
				""";
		
		String xml2 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="test2"/>
				</plugin>
				""";
		
		assertThrows(AssertionError.class, () -> {
			XMLTestUtils.assertXmlSemanticallyEqual(xml1, xml2);
		}, "assertXmlSemanticallyEqual should throw AssertionError on mismatch");
	}

	/**
	 * Test assertXmlSemanticallyEqual does not throw on match.
	 */
	@Test
	public void testAssertDoesNotThrowOnMatch() {
		String xml1 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="test"/>
				</plugin>
				""";
		
		String xml2 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin><extension point="test"/></plugin>
				""";
		
		// Should not throw
		XMLTestUtils.assertXmlSemanticallyEqual(xml1, xml2);
	}

	/**
	 * Test that empty elements are handled correctly.
	 */
	@Test
	public void testEmptyElementsEqual() {
		String xml1 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="test"></extension>
				</plugin>
				""";
		
		String xml2 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="test"/>
				</plugin>
				""";
		
		assertTrue(XMLTestUtils.isXmlSemanticallyEqual(xml1, xml2),
			"Empty elements should be equal regardless of self-closing syntax");
	}

	/**
	 * Test that invalid XML returns false rather than throwing.
	 */
	@Test
	public void testInvalidXmlReturnsFalse() {
		String validXml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="test"/>
				</plugin>
				""";
		
		String invalidXml = "<plugin><extension point=\"test\"";
		
		assertFalse(XMLTestUtils.isXmlSemanticallyEqual(validXml, invalidXml),
			"Invalid XML should return false");
		assertFalse(XMLTestUtils.isXmlSemanticallyEqual(invalidXml, validXml),
			"Invalid XML should return false");
	}

	/**
	 * Test that nested elements are compared correctly.
	 */
	@Test
	public void testNestedElementsEqual() {
		String xml1 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="org.eclipse.ui.views">
				        <category id="cat1" name="Category1">
				            <view id="view1" name="View1"/>
				        </category>
				    </extension>
				</plugin>
				""";
		
		String xml2 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin><extension point="org.eclipse.ui.views"><category id="cat1" name="Category1"><view id="view1" name="View1"/></category></extension></plugin>
				""";
		
		assertTrue(XMLTestUtils.isXmlSemanticallyEqual(xml1, xml2),
			"Nested elements with different whitespace should be semantically equal");
	}
}
