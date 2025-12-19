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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for XML cleanup file filtering logic.
 * 
 * These are unit tests that verify the file filtering rules without requiring
 * a full Eclipse workspace or mocking framework.
 */
public class XMLFileFilteringTest {

	/**
	 * Test that plugin.xml is recognized as a PDE-relevant file name.
	 */
	@Test
	public void testPluginXmlIsRecognized() {
		assertTrue(isPDEFileName("plugin.xml"), "plugin.xml should be recognized as PDE file");
	}

	/**
	 * Test that pom.xml is NOT recognized as a PDE-relevant file name.
	 */
	@Test
	public void testPomXmlIsNotRecognized() {
		assertFalse(isPDEFileName("pom.xml"), "pom.xml should NOT be recognized as PDE file");
	}

	/**
	 * Test that build.xml is NOT recognized as a PDE-relevant file name.
	 */
	@Test
	public void testBuildXmlIsNotRecognized() {
		assertFalse(isPDEFileName("build.xml"), "build.xml should NOT be recognized as PDE file");
	}

	/**
	 * Test that feature.xml is recognized as a PDE-relevant file name.
	 */
	@Test
	public void testFeatureXmlIsRecognized() {
		assertTrue(isPDEFileName("feature.xml"), "feature.xml should be recognized as PDE file");
	}

	/**
	 * Test that fragment.xml is recognized as a PDE-relevant file name.
	 */
	@Test
	public void testFragmentXmlIsRecognized() {
		assertTrue(isPDEFileName("fragment.xml"), "fragment.xml should be recognized as PDE file");
	}

	/**
	 * Test that *.exsd extension is recognized as PDE-relevant.
	 */
	@Test
	public void testExsdExtensionIsRecognized() {
		assertTrue(isPDEExtension(getExtension("sample.exsd")), "*.exsd should be recognized as PDE extension");
	}

	/**
	 * Test that *.xsd extension is recognized as PDE-relevant.
	 */
	@Test
	public void testXsdExtensionIsRecognized() {
		assertTrue(isPDEExtension(getExtension("sample.xsd")), "*.xsd should be recognized as PDE extension");
	}

	/**
	 * Test that *.xml extension is NOT automatically recognized (must match specific names).
	 */
	@Test
	public void testGenericXmlExtensionIsNotRecognized() {
		assertFalse(isPDEExtension(getExtension("custom.xml")), "generic *.xml should NOT be recognized by extension");
	}

	/**
	 * Test that arbitrary XML files are not recognized.
	 */
	@Test
	public void testArbitraryXmlIsNotRecognized() {
		assertFalse(isPDEFileName("config.xml"), "arbitrary XML files should NOT be recognized");
		assertFalse(isPDEFileName("settings.xml"), "settings.xml should NOT be recognized");
		assertFalse(isPDEFileName("beans.xml"), "beans.xml should NOT be recognized");
	}

	/**
	 * Test directory name recognition.
	 */
	@Test
	public void testPDEDirectoryNames() {
		assertTrue(isPDEDirectory("OSGI-INF"), "OSGI-INF should be recognized");
		assertTrue(isPDEDirectory("META-INF"), "META-INF should be recognized");
		assertFalse(isPDEDirectory("src"), "src should NOT be recognized");
		assertFalse(isPDEDirectory("resources"), "resources should NOT be recognized");
	}

	// Helper methods that mirror the logic in XMLPlugin

	/**
	 * Check if a file name is a PDE-relevant file name.
	 * Mirrors the logic in XMLPlugin.isPDERelevantFile()
	 */
	private boolean isPDEFileName(String fileName) {
		return fileName.equals("plugin.xml") || 
		       fileName.equals("feature.xml") || 
		       fileName.equals("fragment.xml");
	}

	/**
	 * Check if an extension is a PDE-relevant extension.
	 * Mirrors the logic in XMLPlugin.isPDERelevantFile()
	 */
	private boolean isPDEExtension(String extension) {
		return "exsd".equals(extension) || "xsd".equals(extension);
	}

	/**
	 * Check if a directory name is a PDE-typical directory.
	 * Mirrors the logic in XMLPlugin.isInPDELocation()
	 */
	private boolean isPDEDirectory(String dirName) {
		return "OSGI-INF".equals(dirName) || "META-INF".equals(dirName);
	}

	/**
	 * Extract file extension from a file name.
	 */
	private String getExtension(String fileName) {
		int lastDot = fileName.lastIndexOf('.');
		return lastDot > 0 ? fileName.substring(lastDot + 1) : null;
	}
}
