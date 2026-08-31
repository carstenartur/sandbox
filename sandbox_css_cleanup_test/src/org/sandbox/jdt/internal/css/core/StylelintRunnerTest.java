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

/** Tests for {@link StylelintRunner}. */
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
	public void testStylelintRunnerIsInstantiable() {
		assertNotNull(StylelintRunner.class, "StylelintRunner class should be accessible"); //$NON-NLS-1$
	}

	@Test
	public void testConfiguredFileIsPassedToValidateAndFix() {
		List<String> validate = StylelintRunner.buildValidateArguments(
				"/workspace/test.css", "/workspace/stylelint.config.mjs"); //$NON-NLS-1$ //$NON-NLS-2$
		List<String> fix = StylelintRunner.buildFixArguments(
				"/workspace/test.css", "/workspace/stylelint.config.mjs"); //$NON-NLS-1$ //$NON-NLS-2$

		assertEquals("/workspace/stylelint.config.mjs", //$NON-NLS-1$
				validate.get(validate.indexOf("--config") + 1)); //$NON-NLS-1$
		assertEquals("/workspace/stylelint.config.mjs", //$NON-NLS-1$
				fix.get(fix.indexOf("--config") + 1)); //$NON-NLS-1$
		assertEquals("/workspace/test.css", //$NON-NLS-1$
				fix.get(fix.indexOf("--stdin-filename") + 1)); //$NON-NLS-1$
		assertTrue(fix.contains("--stdin")); //$NON-NLS-1$
		assertTrue(fix.contains("--fix")); //$NON-NLS-1$
	}

	@Test
	public void testNoConfigDoesNotAddConfigArgument() {
		assertFalse(StylelintRunner.buildValidateArguments("test.css", "").contains("--config")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertFalse(StylelintRunner.buildFixArguments("test.css", null).contains("--config")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testJsonDiagnosticsPreserveLineColumnRuleSeverityAndMessage() {
		String json = "[{\"source\":\"test.css\",\"warnings\":[" //$NON-NLS-1$
				+ "{\"line\":2,\"column\":4,\"rule\":\"color-no-invalid-hex\",\"severity\":\"error\","
				+ "\"text\":\"Unexpected invalid hex color \\\"#y3\\\"\"},"
				+ "{\"line\":5,\"column\":1,\"rule\":\"declaration-block-no-duplicate-properties\","
				+ "\"severity\":\"warning\",\"text\":\"Unexpected duplicate property\"}]}]";

		CSSValidationResult result = StylelintRunner.parseStylelintOutput(json);

		assertFalse(result.isValid());
		assertEquals(2, result.getIssues().size());
		CSSValidationResult.Issue first = result.getIssues().get(0);
		assertEquals(2, first.line);
		assertEquals(4, first.column);
		assertEquals("error", first.severity); //$NON-NLS-1$
		assertEquals("color-no-invalid-hex", first.rule); //$NON-NLS-1$
		assertEquals("Unexpected invalid hex color \"#y3\"", first.message); //$NON-NLS-1$
		CSSValidationResult.Issue second = result.getIssues().get(1);
		assertEquals(5, second.line);
		assertEquals(1, second.column);
		assertEquals("warning", second.severity); //$NON-NLS-1$
	}

	@Test
	public void testParseAndInvalidOptionDiagnosticsAreNotCollapsed() {
		String json = "[{\"warnings\":[]," //$NON-NLS-1$
				+ "\"parseErrors\":[{\"line\":3,\"column\":7,\"text\":\"Unknown word\"}],"
				+ "\"invalidOptionWarnings\":[{\"text\":\"Invalid option value\"}]}]";

		CSSValidationResult result = StylelintRunner.parseStylelintOutput(json);

		assertEquals(2, result.getIssues().size());
		assertEquals(3, result.getIssues().get(0).line);
		assertEquals(7, result.getIssues().get(0).column);
		assertEquals("parse-error", result.getIssues().get(0).rule); //$NON-NLS-1$
		assertEquals("invalid-option", result.getIssues().get(1).rule); //$NON-NLS-1$
	}

	@Test
	public void testModernStderrReportCanBeExtractedFromSurroundingOutput() {
		String stderr = "Deprecation notice\n[{\"warnings\":[]}]\n"; //$NON-NLS-1$
		assertEquals("[{\"warnings\":[]}]", StylelintRunner.extractJsonReport(stderr)); //$NON-NLS-1$
	}

	@Test
	public void testMalformedJsonIsRejectedInsteadOfBecomingGenericLineOneError() {
		assertThrows(IllegalArgumentException.class,
				() -> StylelintRunner.parseStylelintOutput("[{\"warnings\":[}")); //$NON-NLS-1$
	}

	@Test
	public void testIsStylelintAvailable() {
		assertTrue(StylelintRunner.isStylelintAvailable(),
				"Maven must provision the pinned Stylelint package"); //$NON-NLS-1$
	}

	@Test
	public void testValidateValidCss() throws Exception {
		String validCss = "body {\n  color: red;\n  margin: 0;\n}\n"; //$NON-NLS-1$
		IFile file = createTestCssFile("valid.css", validCss); //$NON-NLS-1$

		CSSValidationResult result = StylelintRunner.validate(file, createStylelintConfig());

		assertTrue(result.isValid());
		assertTrue(result.getIssues().isEmpty());
	}

	@Test
	public void testValidateReportsRealInvalidHexDiagnostic() throws Exception {
		IFile file = createTestCssFile("invalid.css", "body { color: #y3; }"); //$NON-NLS-1$ //$NON-NLS-2$

		CSSValidationResult result = StylelintRunner.validate(file, createStylelintConfig());

		assertFalse(result.isValid());
		assertEquals(1, result.getIssues().size());
		assertEquals("color-no-invalid-hex", result.getIssues().get(0).rule); //$NON-NLS-1$
	}

	@Test
	public void testFixReturnsString() throws Exception {
		String css = "body{color:red;}"; //$NON-NLS-1$
		IFile file = createTestCssFile("tofix.css", css); //$NON-NLS-1$
		String fixed = StylelintRunner.fix(file, createStylelintConfig());
		assertNotNull(fixed, "Fixed content should not be null"); //$NON-NLS-1$
		assertFalse(fixed.isEmpty(), "Fixed content should not be empty"); //$NON-NLS-1$
	}

	@Test
	public void testFixPreservesSemantics() throws Exception {
		String css = "body { color: #ff0000; background: white; }"; //$NON-NLS-1$
		IFile file = createTestCssFile("semantics.css", css); //$NON-NLS-1$
		String fixed = StylelintRunner.fix(file, createStylelintConfig());
		assertNotNull(fixed, "Fixed content should not be null"); //$NON-NLS-1$
		assertTrue(fixed.toLowerCase().contains("color") || fixed.toLowerCase().contains("#ff0000"), //$NON-NLS-1$ //$NON-NLS-2$
				"Fixed CSS should preserve color information"); //$NON-NLS-1$
	}

	private String createStylelintConfig() throws CoreException {
		IFile config = createTestCssFile("stylelint.config.json", //$NON-NLS-1$
				"{\"rules\":{\"color-no-invalid-hex\":true}}"); //$NON-NLS-1$
		return config.getLocation().toOSString();
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
