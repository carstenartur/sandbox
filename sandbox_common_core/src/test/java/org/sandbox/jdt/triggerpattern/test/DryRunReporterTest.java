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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.DryRunReporter;
import org.sandbox.jdt.triggerpattern.api.DryRunReporter.ReportEntry;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;

/**
 * Tests for Phase 6.5: Dry-Run / Reporting.
 * 
 * @see DryRunReporter
 */
public class DryRunReporterTest {

	@Test
	public void testAnalyzeSimpleExpression() {
		String code = "class Test { void m() { int x = 1 + 0; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		Pattern srcPattern = new Pattern("$x + 0", PatternKind.EXPRESSION); //$NON-NLS-1$
		RewriteAlternative alt = new RewriteAlternative("$x", null); //$NON-NLS-1$
		TransformationRule rule = new TransformationRule(
				"Remove addition of zero", srcPattern, null, List.of(alt)); //$NON-NLS-1$

		DryRunReporter reporter = new DryRunReporter();
		List<ReportEntry> entries = reporter.analyze(cu, List.of(rule));

		assertFalse(entries.isEmpty(), "Should find at least one match"); //$NON-NLS-1$
		ReportEntry entry = entries.get(0);
		assertTrue(entry.lineNumber() > 0, "Line number should be positive"); //$NON-NLS-1$
		assertNotNull(entry.matchedCode(), "Matched code should not be null"); //$NON-NLS-1$
		assertTrue(entry.matchedCode().contains("1 + 0"), //$NON-NLS-1$
				"Matched code should contain '1 + 0', got: " + entry.matchedCode()); //$NON-NLS-1$
		assertTrue(entry.hasReplacement(), "Should have a replacement"); //$NON-NLS-1$
	}

	@Test
	public void testAnalyzeNoMatch() {
		String code = "class Test { void m() { int x = 1 + 2; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		Pattern srcPattern = new Pattern("$x + 0", PatternKind.EXPRESSION); //$NON-NLS-1$
		RewriteAlternative alt = new RewriteAlternative("$x", null); //$NON-NLS-1$
		TransformationRule rule = new TransformationRule(
				"Remove addition of zero", srcPattern, null, List.of(alt)); //$NON-NLS-1$

		DryRunReporter reporter = new DryRunReporter();
		List<ReportEntry> entries = reporter.analyze(cu, List.of(rule));

		assertTrue(entries.isEmpty(), "Should find no matches"); //$NON-NLS-1$
	}

	@Test
	public void testAnalyzeHintOnlyRule() {
		String code = "class Test { void m() { String x = \"hello\".toString(); } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		// Hint-only rule (no rewrite alternatives)
		Pattern srcPattern = new Pattern("$x.toString()", PatternKind.METHOD_CALL); //$NON-NLS-1$
		TransformationRule rule = new TransformationRule(
				"Unnecessary toString() call", srcPattern, null, List.of()); //$NON-NLS-1$

		DryRunReporter reporter = new DryRunReporter();
		List<ReportEntry> entries = reporter.analyze(cu, List.of(rule));

		assertFalse(entries.isEmpty(), "Should find the toString() call"); //$NON-NLS-1$
		assertFalse(entries.get(0).hasReplacement(),
				"Hint-only rule should not have replacement"); //$NON-NLS-1$
	}

	@Test
	public void testAnalyzeMultipleMatches() {
		String code = "class Test { void m() { int a = 1 + 0; int b = 2 + 0; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		Pattern srcPattern = new Pattern("$x + 0", PatternKind.EXPRESSION); //$NON-NLS-1$
		RewriteAlternative alt = new RewriteAlternative("$x", null); //$NON-NLS-1$
		TransformationRule rule = new TransformationRule(
				"Remove addition of zero", srcPattern, null, List.of(alt)); //$NON-NLS-1$

		DryRunReporter reporter = new DryRunReporter();
		List<ReportEntry> entries = reporter.analyze(cu, List.of(rule));

		assertEquals(2, entries.size(), "Should find two matches"); //$NON-NLS-1$
	}

	@Test
	public void testToJson() {
		String code = "class Test { void m() { int x = 1 + 0; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		Pattern srcPattern = new Pattern("$x + 0", PatternKind.EXPRESSION); //$NON-NLS-1$
		RewriteAlternative alt = new RewriteAlternative("$x", null); //$NON-NLS-1$
		TransformationRule rule = new TransformationRule(
				"Remove addition of zero", srcPattern, null, List.of(alt)); //$NON-NLS-1$

		DryRunReporter reporter = new DryRunReporter();
		List<ReportEntry> entries = reporter.analyze(cu, List.of(rule));

		String json = reporter.toJson(entries);
		assertNotNull(json, "JSON should not be null"); //$NON-NLS-1$
		assertTrue(json.startsWith("["), "JSON should start with ["); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(json.contains("\"line\""), "JSON should contain line field"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(json.contains("\"matched\""), "JSON should contain matched field"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(json.contains("\"pattern\""), "JSON should contain pattern field"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testToCsv() {
		String code = "class Test { void m() { int x = 1 + 0; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		Pattern srcPattern = new Pattern("$x + 0", PatternKind.EXPRESSION); //$NON-NLS-1$
		RewriteAlternative alt = new RewriteAlternative("$x", null); //$NON-NLS-1$
		TransformationRule rule = new TransformationRule(
				"Remove addition of zero", srcPattern, null, List.of(alt)); //$NON-NLS-1$

		DryRunReporter reporter = new DryRunReporter();
		List<ReportEntry> entries = reporter.analyze(cu, List.of(rule));

		String csv = reporter.toCsv(entries);
		assertNotNull(csv, "CSV should not be null"); //$NON-NLS-1$
		assertTrue(csv.startsWith("line,offset,"), "CSV should start with header"); //$NON-NLS-1$ //$NON-NLS-2$
		// Header + 1 data row
		long lineCount = csv.chars().filter(c -> c == '\n').count();
		assertTrue(lineCount >= 2, "CSV should have header + at least 1 data row"); //$NON-NLS-1$
	}

	@Test
	public void testEmptyInput() {
		DryRunReporter reporter = new DryRunReporter();

		assertTrue(reporter.analyze(null, List.of()).isEmpty(),
				"Null CU should return empty"); //$NON-NLS-1$

		String code = "class Test { }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);
		assertTrue(reporter.analyze(cu, null).isEmpty(),
				"Null rules should return empty"); //$NON-NLS-1$
		assertTrue(reporter.analyze(cu, List.of()).isEmpty(),
				"Empty rules should return empty"); //$NON-NLS-1$
	}

	@Test
	public void testJsonEscaping() {
		DryRunReporter reporter = new DryRunReporter();

		// Create a report entry with special characters
		ReportEntry entry = new ReportEntry(
				1, 0, 10,
				"code with \"quotes\" and\nnewlines", //$NON-NLS-1$
				"replacement", //$NON-NLS-1$
				"description", //$NON-NLS-1$
				"info", //$NON-NLS-1$
				"$x + 0", //$NON-NLS-1$
				null);

		String json = reporter.toJson(List.of(entry));
		assertTrue(json.contains("\\\""), "JSON should escape quotes"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(json.contains("\\n"), "JSON should escape newlines"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testReportEntryHasReplacement() {
		ReportEntry entryWithReplacement = new ReportEntry(
				1, 0, 10, "code", "replacement", null, "info", "$x", null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		assertTrue(entryWithReplacement.hasReplacement());

		ReportEntry entryWithout = new ReportEntry(
				1, 0, 10, "code", null, null, "info", "$x", null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertFalse(entryWithout.hasReplacement());
	}

	// --- Helper methods ---

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
