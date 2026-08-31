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
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.sandbox.jdt.triggerpattern.test.policy.JUnitXmlInventoryComparator.Comparison;
import org.sandbox.jdt.triggerpattern.test.policy.JUnitXmlInventoryComparator.Mapping;

/**
 * Tests and property-driven Maven entry point for the JUnit XML migration
 * evidence comparator.
 *
 * @since 1.3.5
 */
public class JUnitXmlInventoryComparatorTest {

	private static final String BASELINE_PROPERTY = "sandbox.junit.inventory.baseline"; //$NON-NLS-1$
	private static final String MIGRATED_PROPERTY = "sandbox.junit.inventory.migrated"; //$NON-NLS-1$
	private static final String MAPPING_PROPERTY = "sandbox.junit.inventory.mapping"; //$NON-NLS-1$
	private static final String OUTPUT_PROPERTY = "sandbox.junit.inventory.output"; //$NON-NLS-1$

	@TempDir
	Path temporaryDirectory;

	@Test
	public void identicalInventoriesPreserveIdentityStateAndMultiplicity() throws Exception {
		Path baseline = temporaryDirectory.resolve("baseline"); //$NON-NLS-1$
		Path migrated = temporaryDirectory.resolve("migrated"); //$NON-NLS-1$
		String report = """
				<testsuites>
				  <testsuite name="suite">
				    <testcase classname="example.SampleTest" name="repeated"/>
				    <testcase classname="example.SampleTest" name="repeated"/>
				    <testcase classname="example.SampleTest" name="conditional"><skipped/></testcase>
				  </testsuite>
				</testsuites>
				"""; //$NON-NLS-1$
		writeReport(baseline, "TEST-baseline.xml", report); //$NON-NLS-1$
		writeReport(migrated, "TEST-migrated.xml", report); //$NON-NLS-1$

		Comparison result = JUnitXmlInventoryComparator.compare(baseline, migrated, Mapping.empty());

		assertTrue(result.passed(), () -> result.problems().toString());
		assertEquals(3, result.baseline().tests());
		assertEquals(2, result.baseline().passed());
		assertEquals(1, result.baseline().skipped());
		assertTrue(result.unexpectedMissing().isEmpty());
		assertTrue(result.unexpectedAdded().isEmpty());
	}

	@Test
	public void stateChangesAreReportedAsMissingAndAddedTests() throws Exception {
		Path baseline = temporaryDirectory.resolve("baseline"); //$NON-NLS-1$
		Path migrated = temporaryDirectory.resolve("migrated"); //$NON-NLS-1$
		writeReport(baseline, "TEST-result.xml", suite("example.SampleTest", "changesState", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		writeReport(migrated, "TEST-result.xml", //$NON-NLS-1$
				suite("example.SampleTest", "changesState", "<skipped/>")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		Comparison result = JUnitXmlInventoryComparator.compare(baseline, migrated, Mapping.empty());

		assertFalse(result.passed());
		assertEquals("passed", result.unexpectedMissing().get(0).state()); //$NON-NLS-1$
		assertEquals("skipped", result.unexpectedAdded().get(0).state()); //$NON-NLS-1$
		assertTrue(result.problems().contains("Tests disappeared or changed state after migration")); //$NON-NLS-1$
		assertTrue(result.problems().contains("Unexpected tests appeared or changed state after migration")); //$NON-NLS-1$
	}

	@Test
	public void explicitRenamesAndAllowedPatternsRemainDeterministic() throws Exception {
		Path baseline = temporaryDirectory.resolve("baseline"); //$NON-NLS-1$
		Path migrated = temporaryDirectory.resolve("migrated"); //$NON-NLS-1$
		writeReport(baseline, "TEST-baseline.xml", """
				<testsuite name="suite">
				  <testcase classname="old.SampleTest" name="renamed"/>
				  <testcase classname="legacy.RemovedTest" name="obsolete"/>
				</testsuite>
				"""); //$NON-NLS-1$
		writeReport(migrated, "TEST-migrated.xml", """
				<testsuite name="suite">
				  <testcase classname="new.SampleTest" name="renamed"/>
				  <testcase classname="jupiter.AddedTest" name="generated"/>
				</testsuite>
				"""); //$NON-NLS-1$
		Mapping mapping = new Mapping(Map.of("old.SampleTest#renamed", "new.SampleTest#renamed"), //$NON-NLS-1$ //$NON-NLS-2$
				List.of("legacy.*#*"), List.of("jupiter.[A-Z]*#*")); //$NON-NLS-1$ //$NON-NLS-2$

		Comparison result = JUnitXmlInventoryComparator.compare(baseline, migrated, mapping);

		assertTrue(result.passed(), () -> result.problems().toString());
	}

	@Test
	public void failingTestsAndMalformedReportsFailClosed() throws Exception {
		Path baseline = temporaryDirectory.resolve("baseline"); //$NON-NLS-1$
		Path migrated = temporaryDirectory.resolve("migrated"); //$NON-NLS-1$
		writeReport(baseline, "TEST-failure.xml", //$NON-NLS-1$
				suite("example.FailingTest", "fails", "<failure message=\"boom\"/>")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		writeReport(baseline, "TEST-malformed.xml", "<testsuite>"); //$NON-NLS-1$ //$NON-NLS-2$
		writeReport(migrated, "TEST-error.xml", //$NON-NLS-1$
				suite("example.FailingTest", "fails", "<error message=\"boom\"/>")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		Comparison result = JUnitXmlInventoryComparator.compare(baseline, migrated, Mapping.empty());

		assertFalse(result.passed());
		assertTrue(result.problems().stream().anyMatch(problem -> problem.startsWith("Baseline report parse error:"))); //$NON-NLS-1$
		assertTrue(result.problems().contains("Baseline contains failing or errored tests")); //$NON-NLS-1$
		assertTrue(result.problems().contains("Migrated run contains failing or errored tests")); //$NON-NLS-1$
	}

	@Test
	public void mappingDocumentMustUseTheDeclaredTypes() throws Exception {
		Path mapping = temporaryDirectory.resolve("mapping.json"); //$NON-NLS-1$
		Files.writeString(mapping, "{\"renames\":[],\"allowedMissing\":[],\"allowedAdded\":[]}", //$NON-NLS-1$
				StandardCharsets.UTF_8);

		assertThrows(IllegalArgumentException.class, () -> JUnitXmlInventoryComparator.readMapping(mapping));
	}

	@Test
	public void configuredUpstreamEvidenceIsComparedByMaven() throws Exception {
		Map<String, String> configured = Map.of(
				BASELINE_PROPERTY, System.getProperty(BASELINE_PROPERTY, ""), //$NON-NLS-1$
				MIGRATED_PROPERTY, System.getProperty(MIGRATED_PROPERTY, ""), //$NON-NLS-1$
				MAPPING_PROPERTY, System.getProperty(MAPPING_PROPERTY, ""), //$NON-NLS-1$
				OUTPUT_PROPERTY, System.getProperty(OUTPUT_PROPERTY, "")); //$NON-NLS-1$
		long supplied = configured.values().stream().filter(value -> !value.isBlank()).count();
		if (supplied == 0) {
			return;
		}
		assertEquals(configured.size(), supplied,
				"The retained-workspace invocation must provide baseline, migrated, mapping and output paths"); //$NON-NLS-1$

		Comparison result = JUnitXmlInventoryComparator.compare(
				Path.of(configured.get(BASELINE_PROPERTY)),
				Path.of(configured.get(MIGRATED_PROPERTY)),
				JUnitXmlInventoryComparator.readMapping(Path.of(configured.get(MAPPING_PROPERTY))));
		Path output = Path.of(configured.get(OUTPUT_PROPERTY));
		result.write(output);

		assertTrue(result.passed(), () -> "JUnit migration inventory differs; see " //$NON-NLS-1$
				+ output.toAbsolutePath() + ": " + result.problems()); //$NON-NLS-1$
	}

	private static String suite(String owner, String name, String body) {
		return "<testsuite name=\"suite\"><testcase classname=\"" + owner //$NON-NLS-1$
				+ "\" name=\"" + name + "\">" + body + "</testcase></testsuite>"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static void writeReport(Path directory, String name, String content) throws IOException {
		Files.createDirectories(directory);
		Files.writeString(directory.resolve(name), content, StandardCharsets.UTF_8);
	}
}
