/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.internal.corext.fix.JUnit4MigrationPresets.Preset;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;

/** Regression tests for deterministic and dependency-safe migration presets. */
public class JUnit4MigrationPresetsTest {

	@Test
	public void everyNonCustomPresetDefinesEveryManagedOption() {
		Set<String> managed= Set.copyOf(JUnit4MigrationPresets.managedOptions());

		for (Preset preset : Preset.values()) {
			Map<String, Boolean> selection= JUnit4MigrationPresets.selectionFor(preset);
			if (preset == Preset.CUSTOM) {
				assertTrue(selection.isEmpty());
			} else {
				assertEquals(managed, selection.keySet(), preset.name());
			}
		}
	}

	@Test
	public void selectingAnnotationsOnlyClearsEveryStaleUnrelatedChoice() {
		Map<String, Boolean> state= new HashMap<>();
		JUnit4MigrationPresets.managedOptions().forEach(option -> state.put(option, Boolean.TRUE));

		state.putAll(JUnit4MigrationPresets.selectionFor(Preset.ANNOTATIONS_ONLY));

		assertEquals(Set.of(
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_TIMEOUT,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_EXPECTED,
				MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS), enabledOptions(state));
		assertFalse(state.get(JUnitMigrationOptions.BEST_EFFORT).booleanValue());
		assertFalse(state.get(MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED).booleanValue());
		assertFalse(state.get(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS).booleanValue());
		assertFalse(state.get(MYCleanUpConstants.JUNIT_CLEANUP_4_THROWINGRUNNABLE).booleanValue());
	}

	@Test
	public void allSupportedPresetIsStrictAndEnablesEveryTransformation() {
		Map<String, Boolean> selection= JUnit4MigrationPresets.selectionFor(Preset.ALL_SUPPORTED_STRICT);

		assertFalse(selection.get(JUnitMigrationOptions.BEST_EFFORT).booleanValue());
		assertEquals(JUnit4MigrationPresets.managedOptions().size() - 1, enabledOptions(selection).size());
	}

	@Test
	public void focusedPresetsContainOnlyTheirAdvertisedOperations() {
		assertEquals(Set.of(
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS),
				enabledOptions(JUnit4MigrationPresets.selectionFor(Preset.LIFECYCLE_ONLY)));

		assertEquals(Set.of(
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME_OPTIMIZATION),
				enabledOptions(JUnit4MigrationPresets.selectionFor(Preset.ASSERTIONS_AND_ASSUMPTIONS_ONLY)));

		assertEquals(Set.of(
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME,
				MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETIMEOUT,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXPECTEDEXCEPTION,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEERRORCOLLECTOR),
				enabledOptions(JUnit4MigrationPresets.selectionFor(Preset.RULES_ONLY)));
	}

	@Test
	public void everyPresetRespectsOptionDependencies() {
		for (Preset preset : Preset.values()) {
			if (preset == Preset.CUSTOM) {
				continue;
			}
			Map<String, Boolean> selection= JUnit4MigrationPresets.selectionFor(preset);
			assertDependency(selection, MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION,
					MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT, preset);
			assertDependency(selection, MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME_OPTIMIZATION,
					MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME, preset);
			assertDependency(selection, MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_TIMEOUT,
					MYCleanUpConstants.JUNIT_CLEANUP_4_TEST, preset);
			assertDependency(selection, MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_EXPECTED,
					MYCleanUpConstants.JUNIT_CLEANUP_4_TEST, preset);
			assertDependency(selection, MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE,
					MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH, preset);
			assertDependency(selection, MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED,
					MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH, preset);
			assertFalse(selection.get(JUnitMigrationOptions.BEST_EFFORT).booleanValue(), preset.name());
		}
	}

	@Test
	public void rejectsUnknownComboBoxIndices() {
		assertThrows(IllegalArgumentException.class, () -> JUnit4MigrationPresets.fromSelectionIndex(-1));
		assertThrows(IllegalArgumentException.class, () -> JUnit4MigrationPresets.fromSelectionIndex(Preset.values().length));
	}

	@Test
	public void rejectsNullPreset() {
		assertThrows(NullPointerException.class, () -> JUnit4MigrationPresets.selectionFor(null));
	}

	private static void assertDependency(Map<String, Boolean> selection, String child, String parent, Preset preset) {
		if (selection.get(child).booleanValue()) {
			assertTrue(selection.get(parent).booleanValue(), preset + ": " + child + " requires " + parent); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static Set<String> enabledOptions(Map<String, Boolean> selection) {
		return selection.entrySet().stream()
				.filter(Map.Entry::getValue)
				.map(Map.Entry::getKey)
				.collect(Collectors.toUnmodifiableSet());
	}
}