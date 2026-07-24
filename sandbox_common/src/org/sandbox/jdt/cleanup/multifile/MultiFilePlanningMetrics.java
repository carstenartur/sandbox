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

/** Immutable measurements for one coordinated-cleanup planning run. */
public record MultiFilePlanningMetrics(int compilationUnitCount, long sourceBytes,
		long parseNanos, long planningNanos, int retainedPlanEntries) {

	/** Validates nonnegative measurements. */
	public MultiFilePlanningMetrics {
		if (compilationUnitCount < 0 || sourceBytes < 0 || parseNanos < 0 || planningNanos < 0
				|| retainedPlanEntries < 0) {
			throw new IllegalArgumentException("Planning metrics must not be negative"); //$NON-NLS-1$
		}
	}

	/** @return empty metrics for a cleanup that did not perform coordinated planning */
	public static MultiFilePlanningMetrics empty() {
		return new MultiFilePlanningMetrics(0, 0, 0, 0, 0);
	}

	/** @return metrics containing only the measured pre-parse scope */
	public static MultiFilePlanningMetrics scope(int units, long bytes) {
		return new MultiFilePlanningMetrics(units, bytes, 0, 0, 0);
	}

	/** @return a copy with parse and complete planning duration */
	public MultiFilePlanningMetrics withDurations(long parseDurationNanos, long planningDurationNanos) {
		return new MultiFilePlanningMetrics(compilationUnitCount, sourceBytes,
				Math.max(0, parseDurationNanos), Math.max(0, planningDurationNanos), retainedPlanEntries);
	}

	/** @return a copy with the number of immutable semantic entries retained by the plan */
	public MultiFilePlanningMetrics withRetainedPlanEntries(int entries) {
		return new MultiFilePlanningMetrics(compilationUnitCount, sourceBytes, parseNanos, planningNanos,
				Math.max(0, entries));
	}
}
