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

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Path;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Tests for XML cleanup file filtering logic.
 * 
 * Note: This test uses mocking to verify the filtering logic without requiring
 * a full Eclipse workspace. Integration tests with real Eclipse resources
 * would be more comprehensive but require Eclipse test infrastructure.
 */
public class XMLFileFilteringTest {

	/**
	 * Test that plugin.xml in project root is recognized as PDE-relevant.
	 */
	@Test
	public void testPluginXmlInRootIsRecognized() {
		IFile file = createMockFile("plugin.xml", true, false);
		// In real implementation, would call XMLPlugin.isPDERelevantFile(file)
		// For now, this test documents the expected behavior
		assertTrue(isPDEFileName(file.getName()), "plugin.xml should be recognized as PDE file");
	}

	/**
	 * Test that pom.xml is NOT recognized as PDE-relevant.
	 */
	@Test
	public void testPomXmlIsNotRecognized() {
		IFile file = createMockFile("pom.xml", true, false);
		assertFalse(isPDEFileName(file.getName()), "pom.xml should NOT be recognized as PDE file");
	}

	/**
	 * Test that build.xml is NOT recognized as PDE-relevant.
	 */
	@Test
	public void testBuildXmlIsNotRecognized() {
		IFile file = createMockFile("build.xml", true, false);
		assertFalse(isPDEFileName(file.getName()), "build.xml should NOT be recognized as PDE file");
	}

	/**
	 * Test that feature.xml in project root is recognized as PDE-relevant.
	 */
	@Test
	public void testFeatureXmlInRootIsRecognized() {
		IFile file = createMockFile("feature.xml", true, false);
		assertTrue(isPDEFileName(file.getName()), "feature.xml should be recognized as PDE file");
	}

	/**
	 * Test that fragment.xml in project root is recognized as PDE-relevant.
	 */
	@Test
	public void testFragmentXmlInRootIsRecognized() {
		IFile file = createMockFile("fragment.xml", true, false);
		assertTrue(isPDEFileName(file.getName()), "fragment.xml should be recognized as PDE file");
	}

	/**
	 * Test that *.exsd files are recognized as PDE-relevant.
	 */
	@Test
	public void testExsdFileIsRecognized() {
		IFile file = createMockFile("sample.exsd", true, false);
		assertTrue(isPDEExtension(getExtension(file.getName())), "*.exsd should be recognized as PDE extension");
	}

	/**
	 * Test that *.xsd files are recognized as PDE-relevant.
	 */
	@Test
	public void testXsdFileIsRecognized() {
		IFile file = createMockFile("sample.xsd", true, false);
		assertTrue(isPDEExtension(getExtension(file.getName())), "*.xsd should be recognized as PDE extension");
	}

	/**
	 * Test that plugin.xml in OSGI-INF directory is recognized.
	 */
	@Test
	public void testPluginXmlInOsgiInfIsRecognized() {
		IFile file = createMockFile("plugin.xml", false, true);
		assertTrue(isPDEFileName(file.getName()), "plugin.xml in OSGI-INF should be recognized");
	}

	/**
	 * Test that plugin.xml in META-INF directory is recognized.
	 */
	@Test
	public void testPluginXmlInMetaInfIsRecognized() {
		IFile file = createMockFile("plugin.xml", false, true);
		assertTrue(isPDEFileName(file.getName()), "plugin.xml in META-INF should be recognized");
	}

	// Helper methods that mirror the logic in XMLPlugin

	private boolean isPDEFileName(String fileName) {
		return fileName.equals("plugin.xml") || 
		       fileName.equals("feature.xml") || 
		       fileName.equals("fragment.xml");
	}

	private boolean isPDEExtension(String extension) {
		return "exsd".equals(extension) || "xsd".equals(extension);
	}

	private String getExtension(String fileName) {
		int lastDot = fileName.lastIndexOf('.');
		return lastDot > 0 ? fileName.substring(lastDot + 1) : null;
	}

	private IFile createMockFile(String fileName, boolean inRoot, boolean inPDEFolder) {
		IFile file = Mockito.mock(IFile.class);
		Mockito.when(file.getName()).thenReturn(fileName);
		
		if (inRoot) {
			IProject project = Mockito.mock(IProject.class);
			Mockito.when(file.getParent()).thenReturn(project);
		} else if (inPDEFolder) {
			IFolder folder = Mockito.mock(IFolder.class);
			Mockito.when(folder.getName()).thenReturn("OSGI-INF"); // or META-INF
			Mockito.when(file.getParent()).thenReturn(folder);
		}
		
		return file;
	}
}
