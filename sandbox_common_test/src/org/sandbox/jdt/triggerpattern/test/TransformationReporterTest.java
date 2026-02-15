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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.TransformationReporter;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;

/**
 * Tests for {@link TransformationReporter}.
 *
 * @since 1.3.6
 */
public class TransformationReporterTest {

	@Test
	public void testGenerateCsvReport() throws IOException {
		List<TransformationResult> results = createResults();
		assertFalse(results.isEmpty(), "Should have results to report"); //$NON-NLS-1$

		TransformationReporter reporter = new TransformationReporter();
		StringWriter writer = new StringWriter();
		reporter.generateCsvReport(results, writer);

		String csv = writer.toString();
		assertNotNull(csv);
		assertTrue(csv.startsWith("line,offset,length,matched,replacement,description,severity,pattern\n"), //$NON-NLS-1$
				"CSV should start with header row"); //$NON-NLS-1$
		// Header + at least 1 data row
		long lineCount = csv.chars().filter(c -> c == '\n').count();
		assertTrue(lineCount >= 2, "CSV should have header + at least 1 data row, got " + lineCount); //$NON-NLS-1$
	}

	@Test
	public void testGenerateJsonReport() throws IOException {
		List<TransformationResult> results = createResults();
		assertFalse(results.isEmpty(), "Should have results to report"); //$NON-NLS-1$

		TransformationReporter reporter = new TransformationReporter();
		StringWriter writer = new StringWriter();
		reporter.generateJsonReport(results, writer);

		String json = writer.toString();
		assertNotNull(json);
		assertTrue(json.startsWith("["), "JSON should start with ["); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(json.contains("\"line\""), "JSON should contain line field"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(json.contains("\"matched\""), "JSON should contain matched field"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(json.contains("\"pattern\""), "JSON should contain pattern field"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(json.contains("\"replacement\""), "JSON should contain replacement field"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testEmptyResults() throws IOException {
		TransformationReporter reporter = new TransformationReporter();

		StringWriter csvWriter = new StringWriter();
		reporter.generateCsvReport(List.of(), csvWriter);
		String csv = csvWriter.toString();
		assertTrue(csv.startsWith("line,"), "Empty CSV should still have header"); //$NON-NLS-1$ //$NON-NLS-2$
		long csvLines = csv.chars().filter(c -> c == '\n').count();
		assertTrue(csvLines == 1, "Empty CSV should have only header row"); //$NON-NLS-1$

		StringWriter jsonWriter = new StringWriter();
		reporter.generateJsonReport(List.of(), jsonWriter);
		String json = jsonWriter.toString();
		assertTrue(json.startsWith("["), "Empty JSON should start with ["); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(json.trim().endsWith("]"), "Empty JSON should end with ]"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testCsvReportContainsCorrectFields() throws IOException {
		List<TransformationResult> results = createResults();
		assertFalse(results.isEmpty(), "Should have results"); //$NON-NLS-1$

		TransformationReporter reporter = new TransformationReporter();
		StringWriter writer = new StringWriter();
		reporter.generateCsvReport(results, writer);

		String csv = writer.toString();
		// Verify header fields are present
		assertTrue(csv.contains("line,"), "CSV should contain 'line' column"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(csv.contains(",matched,"), "CSV should contain 'matched' column"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(csv.contains(",replacement,"), "CSV should contain 'replacement' column"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(csv.contains(",pattern"), "CSV should contain 'pattern' column"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testJsonReportContainsCorrectFields() throws IOException {
		List<TransformationResult> results = createResults();
		assertFalse(results.isEmpty(), "Should have results"); //$NON-NLS-1$

		TransformationReporter reporter = new TransformationReporter();
		StringWriter writer = new StringWriter();
		reporter.generateJsonReport(results, writer);

		String json = writer.toString();
		assertTrue(json.contains("\"severity\""), "JSON should contain severity field"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(json.contains("\"description\""), "JSON should contain description field"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(json.trim().endsWith("]"), "JSON should end with ]"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	// --- Helper methods ---

	private List<TransformationResult> createResults() {
		Pattern srcPattern = new Pattern("$x + 0", PatternKind.EXPRESSION); //$NON-NLS-1$
		RewriteAlternative alt = new RewriteAlternative("$x", null); //$NON-NLS-1$
		TransformationRule rule = new TransformationRule(
				"Remove addition of zero", srcPattern, null, List.of(alt)); //$NON-NLS-1$
		HintFile hintFile = new HintFile();
		hintFile.setId("test"); //$NON-NLS-1$
		hintFile.addRule(rule);

		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);

		String code = "class Test { void m() { int r = 1 + 0; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		return processor.process(cu);
	}

	private CompilationUnit parseCode(String code) {
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(code.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_SOURCE, "17"); //$NON-NLS-1$
		astParser.setCompilerOptions(options);
		return (CompilationUnit) astParser.createAST(null);
	}
}
