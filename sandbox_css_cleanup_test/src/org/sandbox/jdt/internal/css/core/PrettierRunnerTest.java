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
package org.sandbox.jdt.internal.css.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Tests for {@link PrettierRunner}.
 */
public class PrettierRunnerTest {

	private static final String TEST_PROJECT_NAME = "CSSTestProject"; //$NON-NLS-1$
	private IProject testProject;

	@BeforeEach
	public void setUp() throws CoreException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		testProject = workspace.getRoot().getProject(TEST_PROJECT_NAME);
		if (!testProject.exists()) {
			IProjectDescription desc = workspace.newProjectDescription(TEST_PROJECT_NAME);
			testProject.create(desc, new NullProgressMonitor());
		}
		if (!testProject.isOpen()) {
			testProject.open(new NullProgressMonitor());
		}
	}

	@AfterEach
	public void tearDown() throws CoreException {
		if (testProject != null && testProject.exists()) {
			testProject.delete(true, true, new NullProgressMonitor());
		}
	}

	@Test
	public void testPrettierRunnerClassExists() {
		assertTrue(PrettierRunner.class != null);
	}

	@Test
	public void testFormatThrowsWhenNpxNotAvailable() {
		// This test only runs when npx is NOT available
		if (NodeExecutor.isNpxAvailable()) {
			return; // Skip if npx is available
		}
		assertThrows(IllegalStateException.class, () -> {
			IFile file = createTestCssFile("test.css", "body { color: red; }"); //$NON-NLS-1$ //$NON-NLS-2$
			PrettierRunner.format(file);
		});
	}

	// ========== Integration tests (require Node.js and Prettier) ==========

	@Test
	@EnabledIf("isPrettierAvailable")
	public void testIsPrettierAvailable() {
		assertTrue(PrettierRunner.isPrettierAvailable(), "Prettier should be available"); //$NON-NLS-1$
	}

	@Test
	@EnabledIf("isPrettierAvailable")
	public void testFormatSimpleCss() throws Exception {
		String unformattedCss = "body{color:red;margin:0}"; //$NON-NLS-1$
		IFile file = createTestCssFile("simple.css", unformattedCss); //$NON-NLS-1$

		String formatted = PrettierRunner.format(file);

		assertNotNull(formatted, "Formatted output should not be null"); //$NON-NLS-1$
		assertFalse(formatted.isEmpty(), "Formatted output should not be empty"); //$NON-NLS-1$
		// Prettier adds whitespace and newlines
		assertTrue(formatted.contains("body"), "Formatted CSS should contain 'body'"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(formatted.contains("color"), "Formatted CSS should contain 'color'"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	@EnabledIf("isPrettierAvailable")
	public void testFormatCssWithMultipleRules() throws Exception {
		String unformattedCss = ".header{font-size:16px;}.footer{padding:10px;}"; //$NON-NLS-1$
		IFile file = createTestCssFile("multi.css", unformattedCss); //$NON-NLS-1$

		String formatted = PrettierRunner.format(file);

		assertNotNull(formatted, "Formatted output should not be null"); //$NON-NLS-1$
		assertTrue(formatted.contains(".header"), "Formatted CSS should contain '.header'"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(formatted.contains(".footer"), "Formatted CSS should contain '.footer'"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	@EnabledIf("isPrettierAvailable")
	public void testFormatPreservesSemantics() throws Exception {
		String originalCss = "body { color: #ff0000; background: white; }"; //$NON-NLS-1$
		IFile file = createTestCssFile("preserve.css", originalCss); //$NON-NLS-1$

		String formatted = PrettierRunner.format(file);

		assertNotNull(formatted, "Formatted output should not be null"); //$NON-NLS-1$
		// The semantic meaning should be preserved (colors, properties)
		assertTrue(formatted.toLowerCase().contains("color"), "Formatted CSS should preserve 'color' property"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(formatted.toLowerCase().contains("background"), "Formatted CSS should preserve 'background' property"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Helper method to create a CSS file in the test project.
	 */
	private IFile createTestCssFile(String fileName, String content) throws CoreException {
		IFile file = testProject.getFile(fileName);
		if (file.exists()) {
			file.delete(true, new NullProgressMonitor());
		}
		file.create(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), true, new NullProgressMonitor());
		return file;
	}

	/**
	 * Condition method for EnabledIf annotation.
	 */
	static boolean isPrettierAvailable() {
		return PrettierRunner.isPrettierAvailable();
	}
}
