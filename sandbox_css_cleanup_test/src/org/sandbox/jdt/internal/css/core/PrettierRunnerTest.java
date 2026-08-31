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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

/** Tests for {@link PrettierRunner}. */
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
	public void testPrettierRunnerIsInstantiable() {
		assertNotNull(PrettierRunner.class, "PrettierRunner class should be accessible"); //$NON-NLS-1$
	}

	@Test
	public void testBlankAndEmptyOptionsNormalizeToEmptyObject() {
		assertEquals("{}", PrettierRunner.normalizeAndValidateOptions(null)); //$NON-NLS-1$
		assertEquals("{}", PrettierRunner.normalizeAndValidateOptions("  ")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("{}", PrettierRunner.normalizeAndValidateOptions(" { } ")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testValidOptionsRemainAvailableToTemporaryConfig() {
		String options = "{\"singleQuote\":true,\"printWidth\":100}"; //$NON-NLS-1$
		assertEquals(options, PrettierRunner.normalizeAndValidateOptions(options));
	}

	@Test
	public void testMalformedOrNonObjectOptionsAreRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> PrettierRunner.normalizeAndValidateOptions("{\"singleQuote\":true")); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class,
				() -> PrettierRunner.normalizeAndValidateOptions("[\"not\",\"an\",\"object\"]")); //$NON-NLS-1$
	}

	@Test
	public void testConfigPathChangesActualPrettierCommand() {
		List<String> withoutConfig = PrettierRunner.buildArguments("/workspace/test.css", null); //$NON-NLS-1$
		List<String> withConfig = PrettierRunner.buildArguments(
				"/workspace/test.css", "/workspace/.sandbox-prettier.json"); //$NON-NLS-1$ //$NON-NLS-2$

		assertFalse(withoutConfig.contains("--config")); //$NON-NLS-1$
		assertTrue(withConfig.contains("--config")); //$NON-NLS-1$
		assertEquals("/workspace/.sandbox-prettier.json", //$NON-NLS-1$
				withConfig.get(withConfig.indexOf("--config") + 1)); //$NON-NLS-1$
		assertEquals("/workspace/test.css", //$NON-NLS-1$
				withConfig.get(withConfig.indexOf("--stdin-filepath") + 1)); //$NON-NLS-1$
	}

	@Test
	public void testStdinFilepathControlsCssScssAndLessParserSelection() {
		for (String filePath : List.of(
				"/workspace/test.css", //$NON-NLS-1$
				"/workspace/test.scss", //$NON-NLS-1$
				"/workspace/test.less")) { //$NON-NLS-1$
			List<String> arguments = PrettierRunner.buildArguments(filePath, null);
			assertFalse(arguments.contains("--parser"), //$NON-NLS-1$
					"Prettier must infer the parser from --stdin-filepath"); //$NON-NLS-1$
			assertEquals(filePath,
					arguments.get(arguments.indexOf("--stdin-filepath") + 1)); //$NON-NLS-1$
		}
	}

	@Test
	public void testIsPrettierAvailable() {
		assertTrue(PrettierRunner.isPrettierAvailable(),
				"Maven must provision the pinned Prettier package"); //$NON-NLS-1$
	}

	@Test
	public void testFormatSimpleCss() throws Exception {
		IFile file = createTestCssFile("simple.css", "body{color:red;margin:0}"); //$NON-NLS-1$ //$NON-NLS-2$

		String formatted = PrettierRunner.format(file);

		assertEquals("body {\n  color: red;\n  margin: 0;\n}\n", formatted); //$NON-NLS-1$
	}

	@Test
	public void testFormatCssWithMultipleRules() throws Exception {
		String unformattedCss = ".header{font-size:16px;}.footer{padding:10px;}"; //$NON-NLS-1$
		IFile file = createTestCssFile("multi.css", unformattedCss); //$NON-NLS-1$
		String formatted = PrettierRunner.format(file);
		assertNotNull(formatted, "Formatted output should not be null"); //$NON-NLS-1$
		assertTrue(formatted.contains(".header"), "Formatted CSS should contain '.header'"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(formatted.contains(".footer"), "Formatted CSS should contain '.footer'"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testFormatPreservesSemantics() throws Exception {
		String originalCss = "body { color: #ff0000; background: white; }"; //$NON-NLS-1$
		IFile file = createTestCssFile("preserve.css", originalCss); //$NON-NLS-1$
		String formatted = PrettierRunner.format(file);
		assertNotNull(formatted, "Formatted output should not be null"); //$NON-NLS-1$
		assertTrue(formatted.toLowerCase().contains("color"), "Formatted CSS should preserve 'color' property"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(formatted.toLowerCase().contains("background"), "Formatted CSS should preserve 'background' property"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private IFile createTestCssFile(String fileName, String content) throws CoreException {
		IFile file = testProject.getFile(fileName);
		if (file.exists()) {
			file.delete(true, new NullProgressMonitor());
		}
		file.create(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), true, new NullProgressMonitor());
		return file;
	}
}
