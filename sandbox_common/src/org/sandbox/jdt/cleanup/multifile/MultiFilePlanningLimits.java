/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

/** Configurable warning and hard limits applied before multi-file AST parsing. */
public record MultiFilePlanningLimits(int warningCompilationUnits, int hardCompilationUnits,
		long warningSourceBytes, long hardSourceBytes) {

	/** System property overriding the warning compilation-unit count. */
	public static final String WARNING_UNITS_PROPERTY= "org.sandbox.cleanup.planning.warningUnits"; //$NON-NLS-1$
	/** System property overriding the hard compilation-unit count. */
	public static final String HARD_UNITS_PROPERTY= "org.sandbox.cleanup.planning.hardUnits"; //$NON-NLS-1$
	/** System property overriding the warning source-byte count. */
	public static final String WARNING_BYTES_PROPERTY= "org.sandbox.cleanup.planning.warningSourceBytes"; //$NON-NLS-1$
	/** System property overriding the hard source-byte count. */
	public static final String HARD_BYTES_PROPERTY= "org.sandbox.cleanup.planning.hardSourceBytes"; //$NON-NLS-1$

	private static final int DEFAULT_WARNING_UNITS= 500;
	private static final int DEFAULT_HARD_UNITS= 2_000;
	private static final long DEFAULT_WARNING_BYTES= 32L * 1024L * 1024L;
	private static final long DEFAULT_HARD_BYTES= 128L * 1024L * 1024L;

	/** Validates limit ordering and positivity. */
	public MultiFilePlanningLimits {
		if (warningCompilationUnits <= 0 || hardCompilationUnits <= 0
				|| warningSourceBytes <= 0 || hardSourceBytes <= 0) {
			throw new IllegalArgumentException("Planning limits must be positive"); //$NON-NLS-1$
		}
		if (warningCompilationUnits > hardCompilationUnits || warningSourceBytes > hardSourceBytes) {
			throw new IllegalArgumentException("Planning warning limits must not exceed hard limits"); //$NON-NLS-1$
		}
	}

	/** @return repository defaults suitable for interactive cleanup previews */
	public static MultiFilePlanningLimits defaults() {
		return new MultiFilePlanningLimits(DEFAULT_WARNING_UNITS, DEFAULT_HARD_UNITS,
				DEFAULT_WARNING_BYTES, DEFAULT_HARD_BYTES);
	}

	/**
	 * Reads positive decimal overrides from system properties. Invalid values fail
	 * closed to the repository default instead of disabling a guard accidentally.
	 *
	 * @return effective planning limits
	 */
	public static MultiFilePlanningLimits fromSystemProperties() {
		MultiFilePlanningLimits defaults= defaults();
		int warningUnits= positiveInt(WARNING_UNITS_PROPERTY, defaults.warningCompilationUnits());
		int hardUnits= positiveInt(HARD_UNITS_PROPERTY, defaults.hardCompilationUnits());
		long warningBytes= positiveLong(WARNING_BYTES_PROPERTY, defaults.warningSourceBytes());
		long hardBytes= positiveLong(HARD_BYTES_PROPERTY, defaults.hardSourceBytes());
		if (warningUnits > hardUnits) {
			warningUnits= Math.min(defaults.warningCompilationUnits(), hardUnits);
		}
		if (warningBytes > hardBytes) {
			warningBytes= Math.min(defaults.warningSourceBytes(), hardBytes);
		}
		return new MultiFilePlanningLimits(warningUnits, hardUnits, warningBytes, hardBytes);
	}

	private static int positiveInt(String property, int fallback) {
		try {
			int value= Integer.parseInt(System.getProperty(property, "")); //$NON-NLS-1$
			return value > 0 ? value : fallback;
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static long positiveLong(String property, long fallback) {
		try {
			long value= Long.parseLong(System.getProperty(property, "")); //$NON-NLS-1$
			return value > 0 ? value : fallback;
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}
}
