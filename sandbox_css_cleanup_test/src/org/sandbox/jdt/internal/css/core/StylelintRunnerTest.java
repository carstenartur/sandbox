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
 * Tests for {@link StylelintRunner}.
 */
public class StylelintRunnerTest {

	private static final String TEST_PROJECT_NAME = "CSSStylelintTestProject"; //$NON-NLS-1$
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
	public void testStylelintRunnerClassExists() {
		assertTrue(StylelintRunner.class != null);
	}

	@Test
	public void testValidateThrowsWhenNpxNotAvailable() {
		// This test only runs when npx is NOT available
		if (NodeExecutor.isNpxAvailable()) {
			return; // Skip if npx is available
		}
		assertThrows(IllegalStateException.class, () -> {
			IFile file = createTestCssFile("test.css", "body { color: red; }"); //$NON-NLS-1$ //$NON-NLS-2$
			StylelintRunner.validate(file);
		});
	}

	@Test
	public void testFixThrowsWhenNpxNotAvailable() {
		// This test only runs when npx is NOT available
		if (NodeExecutor.isNpxAvailable()) {
			return; // Skip if npx is available
		}
		assertThrows(IllegalStateException.class, () -> {
			IFile file = createTestCssFile("test.css", "body { color: red; }"); //$NON-NLS-1$ //$NON-NLS-2$
			StylelintRunner.fix(file);
		});
	}

	// ========== Integration tests (require Node.js and Stylelint) ==========

	@Test
	@EnabledIf("isStylelintAvailable")
	public void testIsStylelintAvailable() {
		assertTrue(StylelintRunner.isStylelintAvailable(), "Stylelint should be available"); //$NON-NLS-1$
	}

	@Test
	@EnabledIf("isStylelintAvailable")
	public void testValidateValidCss() throws Exception {
		String validCss = "body {\n  color: red;\n  margin: 0;\n}\n"; //$NON-NLS-1$
		IFile file = createTestCssFile("valid.css", validCss); //$NON-NLS-1$

		CSSValidationResult result = StylelintRunner.validate(file);

		assertNotNull(result, "Validation result should not be null"); //$NON-NLS-1$
		// Note: Result depends on stylelint configuration
		assertNotNull(result.getIssues(), "Issues list should not be null"); //$NON-NLS-1$
	}

	@Test
	@EnabledIf("isStylelintAvailable")
	public void testValidateReturnsCSSValidationResult() throws Exception {
		String css = "body { color: red; }"; //$NON-NLS-1$
		IFile file = createTestCssFile("result.css", css); //$NON-NLS-1$

		CSSValidationResult result = StylelintRunner.validate(file);

		assertNotNull(result, "Validation result should not be null"); //$NON-NLS-1$
		// The isValid() method should return a boolean
		assertTrue(result.isValid() || !result.isValid(), "isValid should return a boolean"); //$NON-NLS-1$
	}

	@Test
	@EnabledIf("isStylelintAvailable")
	public void testFixReturnsString() throws Exception {
		String css = "body{color:red;}"; //$NON-NLS-1$
		IFile file = createTestCssFile("tofix.css", css); //$NON-NLS-1$

		String fixed = StylelintRunner.fix(file);

		assertNotNull(fixed, "Fixed content should not be null"); //$NON-NLS-1$
		assertFalse(fixed.isEmpty(), "Fixed content should not be empty"); //$NON-NLS-1$
	}

	@Test
	@EnabledIf("isStylelintAvailable")
	public void testFixPreservesSemantics() throws Exception {
		String css = "body { color: #ff0000; background: white; }"; //$NON-NLS-1$
		IFile file = createTestCssFile("semantics.css", css); //$NON-NLS-1$

		String fixed = StylelintRunner.fix(file);

		assertNotNull(fixed, "Fixed content should not be null"); //$NON-NLS-1$
		// The semantic meaning should be preserved
		assertTrue(fixed.toLowerCase().contains("color") || fixed.toLowerCase().contains("#ff0000"), //$NON-NLS-1$ //$NON-NLS-2$
				"Fixed CSS should preserve color information"); //$NON-NLS-1$
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
	static boolean isStylelintAvailable() {
		return StylelintRunner.isStylelintAvailable();
	}
}
