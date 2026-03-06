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
package org.sandbox.mining.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.mining.core.config.KnownRulesStore;
import org.sandbox.mining.core.report.DslEnhancementReporter;
import org.sandbox.mining.core.report.DslEnhancementReporter.DslLimitationGroup;
import org.sandbox.mining.core.report.DslEnhancementReporter.IssueDescriptor;

/**
 * Tests for {@link DslEnhancementReporter}.
 */
class DslEnhancementReporterTest {

	@TempDir
	Path tempDir;

	@Test
	void testEmptyStoreProducesNoGroups() {
		DslEnhancementReporter reporter = new DslEnhancementReporter();
		KnownRulesStore store = new KnownRulesStore();
		List<DslLimitationGroup> groups = reporter.groupByLimitation(store);
		assertTrue(groups.isEmpty());
	}

	@Test
	void testEmptyStoreProducesEmptyReport() {
		DslEnhancementReporter reporter = new DslEnhancementReporter();
		KnownRulesStore store = new KnownRulesStore();
		String report = reporter.generateReport(store);
		assertEquals("", report);
	}

	@Test
	void testGroupsBitwiseRules() throws IOException {
		DslEnhancementReporter reporter = new DslEnhancementReporter();
		KnownRulesStore store = buildStoreWithRules(
				rule("rule1", "bitwise OR simplification", "Performance",
						"NEEDS_DSL_EXTENSION", "abc123"));

		List<DslLimitationGroup> groups = reporter.groupByLimitation(store);
		assertEquals(1, groups.size());
		assertEquals("Bitwise operators in patterns/replacements", groups.get(0).getLimitation());
		assertEquals(1, groups.get(0).getCount());
	}

	@Test
	void testGroupsTryWithResourcesRules() throws IOException {
		DslEnhancementReporter reporter = new DslEnhancementReporter();
		KnownRulesStore store = buildStoreWithRules(
				rule("rule1", "try-with-resources conversion", "Modernization",
						"NEEDS_DSL_EXTENSION", "abc123"));

		List<DslLimitationGroup> groups = reporter.groupByLimitation(store);
		assertEquals(1, groups.size());
		assertTrue(groups.get(0).getLimitation().contains("Statement"));
	}

	@Test
	void testGroupsGenericRules() throws IOException {
		DslEnhancementReporter reporter = new DslEnhancementReporter();
		KnownRulesStore store = buildStoreWithRules(
				rule("rule1", "generic type parameter matching", "Collections",
						"NEEDS_DSL_EXTENSION", "abc123"));

		List<DslLimitationGroup> groups = reporter.groupByLimitation(store);
		assertEquals(1, groups.size());
		assertTrue(groups.get(0).getLimitation().contains("generics"));
	}

	@Test
	void testSkipsDiscoveredRules() throws IOException {
		DslEnhancementReporter reporter = new DslEnhancementReporter();
		KnownRulesStore store = buildStoreWithRules(
				rule("rule1", "some pattern", "Collections",
						"DISCOVERED", "abc123"));

		List<DslLimitationGroup> groups = reporter.groupByLimitation(store);
		assertTrue(groups.isEmpty());
	}

	@Test
	void testSortsByCountDescending() throws IOException {
		DslEnhancementReporter reporter = new DslEnhancementReporter();
		KnownRulesStore store = buildStoreWithRules(
				rule("rule1", "bitwise OR", "Cat", "NEEDS_DSL_EXTENSION", "a1"),
				rule("rule2", "try-with-resources wrap", "Cat", "NEEDS_DSL_EXTENSION", "a2"),
				rule("rule3", "try-with-resources close", "Cat", "NEEDS_DSL_EXTENSION", "a3"));

		List<DslLimitationGroup> groups = reporter.groupByLimitation(store);
		assertEquals(2, groups.size());
		assertTrue(groups.get(0).getCount() >= groups.get(1).getCount());
	}

	@Test
	void testGenerateReport() throws IOException {
		DslEnhancementReporter reporter = new DslEnhancementReporter();
		KnownRulesStore store = buildStoreWithRules(
				rule("rule1", "bitwise OR simplification", "Performance",
						"NEEDS_DSL_EXTENSION", "abc1234"));

		String report = reporter.generateReport(store);
		assertFalse(report.isEmpty());
		assertTrue(report.contains("DSL Enhancement Needs"));
		assertTrue(report.contains("Bitwise"));
		assertTrue(report.contains("abc1234"));
	}

	@Test
	void testGenerateIssueDescriptors() throws IOException {
		DslEnhancementReporter reporter = new DslEnhancementReporter();
		KnownRulesStore store = buildStoreWithRules(
				rule("rule1", "bitwise OR simplification", "Performance",
						"NEEDS_DSL_EXTENSION", "abc123"),
				rule("rule2", "generic type narrowing", "Collections",
						"NEEDS_DSL_EXTENSION", "def456"));

		List<IssueDescriptor> issues = reporter.generateIssueDescriptors(store);
		assertEquals(2, issues.size());
		for (IssueDescriptor issue : issues) {
			assertTrue(issue.title().contains("DSL Enhancement"));
			assertFalse(issue.body().isEmpty());
		}
	}

	@Test
	void testInferLimitationForOtherCategory() throws IOException {
		// Test "Other DSL limitations" category via end-to-end
		DslEnhancementReporter reporter = new DslEnhancementReporter();
		KnownRulesStore store = buildStoreWithRules(
				rule("rule1", "some unknown pattern", "Cat",
						"NEEDS_DSL_EXTENSION", "abc123"));

		List<DslLimitationGroup> groups = reporter.groupByLimitation(store);
		assertEquals(1, groups.size());
		assertEquals("Other DSL limitations", groups.get(0).getLimitation());
	}

	// ---- Helpers ----

	private String rule(String id, String summary, String category, String status, String commit) {
		return String.format(
				"{\"id\":\"%s\",\"summary\":\"%s\",\"category\":\"%s\",\"status\":\"%s\",\"sourceCommit\":\"%s\",\"dslRule\":\"$x.old()\\n=> $x.new()\\n;;\\n\"}", //$NON-NLS-1$
				id, summary, category, status, commit);
	}

	private static int fileCounter;

	private KnownRulesStore buildStoreWithRules(String... ruleJsons) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("{\"version\":1,\"rules\":["); //$NON-NLS-1$
		for (int i = 0; i < ruleJsons.length; i++) {
			if (i > 0) sb.append(',');
			sb.append(ruleJsons[i]);
		}
		sb.append("]}"); //$NON-NLS-1$
		Path file = tempDir.resolve("known-rules-" + (++fileCounter) + ".json"); //$NON-NLS-1$ //$NON-NLS-2$
		Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
		return KnownRulesStore.load(file);
	}
}

