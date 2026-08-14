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

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;

/**
 * Complete, deterministic option sets for the JUnit 4 migration presets.
 * <p>
 * Every non-custom preset contains an explicit value for every managed option.
 * Applying one preset therefore cannot retain hidden values from a previously
 * selected preset or from a manually configured cleanup profile.
 * </p>
 */
public final class JUnit4MigrationPresets {

	/** Presets in the same order in which they are displayed by the cleanup UI. */
	public enum Preset {
		CUSTOM,
		ALL_SUPPORTED,
		ANNOTATIONS_ONLY,
		LIFECYCLE_ONLY,
		ASSERTIONS_AND_ASSUMPTIONS_ONLY,
		RULES_ONLY
	}

	private static final List<Preset> SELECTION_ORDER= List.of(
			Preset.CUSTOM,
			Preset.ALL_SUPPORTED,
			Preset.ANNOTATIONS_ONLY,
			Preset.LIFECYCLE_ONLY,
			Preset.ASSERTIONS_AND_ASSUMPTIONS_ONLY,
			Preset.RULES_ONLY);

	private static final List<String> MANAGED_OPTIONS= List.of(
			MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT,
			MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION,
			MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME,
			MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME_OPTIMIZATION,
			MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE,
			MYCleanUpConstants.JUNIT_CLEANUP_4_TEST,
			MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_TIMEOUT,
			MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_EXPECTED,
			MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE,
			MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER,
			MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS,
			MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS,
			MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER,
			MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME,
			MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE,
			MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE,
			MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH,
			MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE,
			MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED,
			MYCleanUpConstants.JUNIT_CLEANUP_4_CATEGORY,
			MYCleanUpConstants.JUNIT_CLEANUP_4_FIX_METHOD_ORDER,
			MYCleanUpConstants.JUNIT_CLEANUP_4_RULETIMEOUT,
			MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXPECTEDEXCEPTION,
			MYCleanUpConstants.JUNIT_CLEANUP_4_RULEERRORCOLLECTOR,
			MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS,
			MYCleanUpConstants.JUNIT_CLEANUP_4_THROWINGRUNNABLE);

	private static final Map<Preset, Map<String, Boolean>> SELECTIONS= createSelections();

	private JUnit4MigrationPresets() {
	}

	/**
	 * Resolves the preset represented by a cleanup UI combo-box index.
	 *
	 * @param selectionIndex zero-based combo-box selection
	 * @return corresponding preset
	 * @throws IllegalArgumentException if the index does not identify a preset
	 */
	public static Preset fromSelectionIndex(int selectionIndex) {
		if (selectionIndex < 0 || selectionIndex >= SELECTION_ORDER.size()) {
			throw new IllegalArgumentException("Unknown JUnit 4 migration preset index: " + selectionIndex); //$NON-NLS-1$
		}
		return SELECTION_ORDER.get(selectionIndex);
	}

	/**
	 * Returns every cleanup option controlled by a non-custom preset.
	 *
	 * @return immutable option list
	 */
	public static List<String> managedOptions() {
		return MANAGED_OPTIONS;
	}

	/**
	 * Returns the complete immutable selection for a preset. The custom preset
	 * intentionally returns an empty map because selecting it must preserve the
	 * user's manually configured values.
	 *
	 * @param preset preset to resolve
	 * @return option-to-enabled mapping
	 */
	public static Map<String, Boolean> selectionFor(Preset preset) {
		return SELECTIONS.get(Objects.requireNonNull(preset));
	}

	private static Map<Preset, Map<String, Boolean>> createSelections() {
		EnumMap<Preset, Map<String, Boolean>> selections= new EnumMap<>(Preset.class);
		selections.put(Preset.CUSTOM, Map.of());
		selections.put(Preset.ALL_SUPPORTED, completeSelection(new LinkedHashSet<>(MANAGED_OPTIONS)));
		selections.put(Preset.ANNOTATIONS_ONLY, completeSelection(Set.of(
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_TIMEOUT,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_EXPECTED,
				MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS)));
		selections.put(Preset.LIFECYCLE_ONLY, completeSelection(Set.of(
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS)));
		selections.put(Preset.ASSERTIONS_AND_ASSUMPTIONS_ONLY, completeSelection(Set.of(
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME_OPTIMIZATION)));
		selections.put(Preset.RULES_ONLY, completeSelection(Set.of(
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME,
				MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETIMEOUT,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXPECTEDEXCEPTION,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEERRORCOLLECTOR)));
		return Collections.unmodifiableMap(selections);
	}

	private static Map<String, Boolean> completeSelection(Set<String> enabledOptions) {
		if (!MANAGED_OPTIONS.containsAll(enabledOptions)) {
			throw new IllegalArgumentException("Preset contains an unmanaged JUnit 4 option"); //$NON-NLS-1$
		}
		LinkedHashMap<String, Boolean> selection= new LinkedHashMap<>();
		for (String option : MANAGED_OPTIONS) {
			selection.put(option, Boolean.valueOf(enabledOptions.contains(option)));
		}
		return Collections.unmodifiableMap(selection);
	}
}
