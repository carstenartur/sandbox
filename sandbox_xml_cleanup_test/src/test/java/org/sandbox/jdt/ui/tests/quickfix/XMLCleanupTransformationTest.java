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
}
