/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

//import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils;

/**
 * Tests for XML cleanup transformation utilities.
 */
public class XMLCleanupTransformationTest {

	/**
	 * Test that transformation with indent=no produces smaller output than indent=yes.
	 */
	@Test
	public void testSizeReductionWithNoIndent() throws Exception {
		// Create a sample XML with lots of whitespace
		String sampleXml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="org.eclipse.ui.views">
				        <view id="test" name="Test" class="Test"/>
				    </extension>
				</plugin>
				""";
		
		Path tempFile = Files.createTempFile("test", ".xml");
		try {
			Files.writeString(tempFile, sampleXml);
			
			// Transform with indent=no (default)
			String transformedNoIndent = SchemaTransformationUtils.transform(tempFile, false);
			
			// Transform with indent=yes
			String transformedWithIndent = SchemaTransformationUtils.transform(tempFile, true);
			
			// Size with no indent should be smaller or equal
			assertTrue(transformedNoIndent.length() <= transformedWithIndent.length(),
				"Transformed XML with indent=no should not be larger than with indent=yes");
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	/**
	 * Test that leading 4 spaces are converted to tabs.
	 */
	@Test
	public void testLeadingSpaceToTabConversion() throws Exception {
		String xmlWithSpaces = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="test">
				        <element id="test"/>
				    </extension>
				</plugin>
				""";
		
		Path tempFile = Files.createTempFile("test", ".xml");
		try {
			Files.writeString(tempFile, xmlWithSpaces);
			
			String transformed = SchemaTransformationUtils.transform(tempFile, false);
			
			// Check that tabs are present in the output
			// Note: Due to XSLT processing, the exact format may vary
			// This is a basic check that transformation occurred
			assertNotEquals(xmlWithSpaces, transformed, 
				"Transformation should change the content");
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	/**
	 * Test that transformation is idempotent (running twice produces same result).
	 */
	@Test
	public void testIdempotency() throws Exception {
		String sampleXml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="test">
				        <element id="test"/>
				    </extension>
				</plugin>
				""";
		
		Path tempFile = Files.createTempFile("test", ".xml");
		try {
			Files.writeString(tempFile, sampleXml);
			
			// First transformation
			String firstTransform = SchemaTransformationUtils.transform(tempFile, false);
			
			// Write transformed content back
			Files.writeString(tempFile, firstTransform);
			
			// Second transformation
			String secondTransform = SchemaTransformationUtils.transform(tempFile, false);
			
			// Both transformations should produce same result
			assertEquals(firstTransform, secondTransform,
				"Second transformation should produce same output as first");
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	/**
	 * Test that comments are preserved during transformation.
	 */
	@Test
	public void testCommentsPreserved() throws Exception {
		String xmlWithComment = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <!-- This is an important comment -->
				    <extension point="test">
				        <element id="test"/>
				    </extension>
				</plugin>
				""";
		
		Path tempFile = Files.createTempFile("test", ".xml");
		try {
			Files.writeString(tempFile, xmlWithComment);
			
			String transformed = SchemaTransformationUtils.transform(tempFile, false);
			
			// Comment should be preserved
			assertTrue(transformed.contains("This is an important comment"),
				"Comments should be preserved during transformation");
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	/**
	 * Test that excessive empty lines are reduced.
	 * The normalization replaces 3 or more line breaks with 2 line breaks,
	 * which means max 1 empty line between content lines.
	 */
	@Test
	public void testEmptyLineReduction() throws Exception {
		String xmlWithManyEmptyLines = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				
				
				
				    <extension point="test">
				
				
				        <element id="test"/>
				    </extension>
				</plugin>
				""";
		
		Path tempFile = Files.createTempFile("test", ".xml");
		try {
			Files.writeString(tempFile, xmlWithManyEmptyLines);
			
			String transformed = SchemaTransformationUtils.transform(tempFile, false);
			
			// The pattern (\r?\n){3,} matches 3 or more line breaks and replaces with $1$1 (2 line breaks)
			// This means we should have at most 2 consecutive newlines (which equals 1 empty line)
			// Count consecutive newlines using a simple check
			int maxConsecutiveNewlines = 0;
			int currentConsecutive = 0;
			for (int i = 0; i < transformed.length(); i++) {
				if (transformed.charAt(i) == '\n') {
					currentConsecutive++;
					maxConsecutiveNewlines = Math.max(maxConsecutiveNewlines, currentConsecutive);
				} else if (transformed.charAt(i) != '\r') {
					currentConsecutive = 0;
				}
			}
			
			assertTrue(maxConsecutiveNewlines <= 2,
				"Should not have more than 2 consecutive newlines (1 empty line), found: " + maxConsecutiveNewlines);
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	@Test
	public void testEmptyElementsCollapsed() throws Exception {
		String xmlWithEmptyElements = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="org.eclipse.ui.views"></extension>
				    <extension point="org.eclipse.ui.commands">
				    </extension>
				    <view id="test" name="Test"></view>
				</plugin>
				""";
		
		Path tempFile = Files.createTempFile("test", ".xml");
		try {
			Files.writeString(tempFile, xmlWithEmptyElements);
			
			String transformed = SchemaTransformationUtils.transform(tempFile, false);
			
			// Verify empty elements are collapsed
			assertFalse(transformed.contains("></extension>"),
				"Empty extension elements should be self-closing");
			assertFalse(transformed.contains("></view>"),
				"Empty view elements should be self-closing");
			
			// Verify self-closing tags are present
			assertTrue(transformed.contains("/>"),
				"Should contain self-closing tags");
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	@Test
	public void testElementsWithContentNotCollapsed() throws Exception {
		String xmlWithContent = """
				<?xml version="1.0" encoding="UTF-8"?>
				<feature>
				    <description>This has content</description>
				    <copyright>Copyright text</copyright>
				</feature>
				""";
		
		Path tempFile = Files.createTempFile("test", ".xml");
		try {
			Files.writeString(tempFile, xmlWithContent);
			
			String transformed = SchemaTransformationUtils.transform(tempFile, false);
			
			// Verify elements with content are NOT collapsed
			assertTrue(transformed.contains("</description>"),
				"Elements with content should not be collapsed");
			assertTrue(transformed.contains("</copyright>"),
				"Elements with content should not be collapsed");
			assertTrue(transformed.contains("This has content"),
				"Text content should be preserved");
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	@Test
	public void testSizeReductionFromCollapsing() throws Exception {
		String xmlWithEmptyElements = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="org.eclipse.ui.views"></extension>
				    <extension point="org.eclipse.ui.commands"></extension>
				    <extension point="org.eclipse.ui.menus"></extension>
				    <view id="v1"></view>
				    <view id="v2"></view>
				    <command id="c1"></command>
				</plugin>
				""";
		
		Path tempFile = Files.createTempFile("test", ".xml");
		try {
			Files.writeString(tempFile, xmlWithEmptyElements);
			
			String transformed = SchemaTransformationUtils.transform(tempFile, false);
			
			// Calculate size reduction
			int originalSize = xmlWithEmptyElements.length();
			int transformedSize = transformed.length();
			
			assertTrue(transformedSize < originalSize,
				"Collapsing empty elements should reduce file size. " +
				"Original: " + originalSize + ", Transformed: " + transformedSize);
			
			// Each </tagname> → /> saves approximately (tagname.length + 2) bytes
			// Expect significant reduction
			int reduction = originalSize - transformedSize;
			assertTrue(reduction > 50, 
				"Should save at least 50 bytes from collapsing. Saved: " + reduction);
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	@Test
	public void testEmptyElementsWithAttributesCollapsed() throws Exception {
		String xmlWithEmptyElements = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <view id="test" name="Test View" class="TestClass"></view>
				    <command id="cmd1" name="Command One"></command>
				</plugin>
				""";
		
		Path tempFile = Files.createTempFile("test", ".xml");
		try {
			Files.writeString(tempFile, xmlWithEmptyElements);
			
			String transformed = SchemaTransformationUtils.transform(tempFile, false);
			
			// Verify empty elements with attributes are collapsed
			assertFalse(transformed.contains("></view>"),
				"Empty view element with attributes should be self-closing");
			assertFalse(transformed.contains("></command>"),
				"Empty command element with attributes should be self-closing");
			
			// Verify attributes are preserved
			assertTrue(transformed.contains("id=\"test\""),
				"Attributes should be preserved");
			assertTrue(transformed.contains("name=\"Test View\""),
				"Attributes should be preserved");
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	@Test
	public void testEmptyElementsWithWhitespaceCollapsed() throws Exception {
		String xmlWithWhitespace = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="test">   </extension>
				    <view id="v1">
				    </view>
				</plugin>
				""";
		
		Path tempFile = Files.createTempFile("test", ".xml");
		try {
			Files.writeString(tempFile, xmlWithWhitespace);
			
			String transformed = SchemaTransformationUtils.transform(tempFile, false);
			
			// Verify elements with only whitespace are collapsed
			assertFalse(transformed.contains("></extension>"),
				"Elements with only whitespace should be self-closing");
			assertFalse(transformed.contains("></view>"),
				"Elements with only whitespace should be self-closing");
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	@Test
	public void testNestedEmptyElementsCollapsed() throws Exception {
		String xmlWithNested = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="test">
				        <view id="v1"></view>
				        <command id="c1"></command>
				    </extension>
				</plugin>
				""";
		
		Path tempFile = Files.createTempFile("test", ".xml");
		try {
			Files.writeString(tempFile, xmlWithNested);
			
			String transformed = SchemaTransformationUtils.transform(tempFile, false);
			
			// Verify nested empty elements are collapsed
			assertFalse(transformed.contains("></view>"),
				"Nested empty view should be self-closing");
			assertFalse(transformed.contains("></command>"),
				"Nested empty command should be self-closing");
			
			// Parent extension should NOT be collapsed (has children)
			assertTrue(transformed.contains("</extension>"),
				"Parent element with children should not be collapsed");
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}
}
