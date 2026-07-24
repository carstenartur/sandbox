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

import java.util.Objects;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

/**
 * Result of analysing the complete selected cleanup scope.
 *
 * @param <P> immutable plan type
 * @param plan plan to retain for the subsequent per-compilation-unit fix phase;
 *             may be {@code null} when no coordinated change is required
 * @param status diagnostics produced during planning
 * @param metrics immutable scope, duration and retained-plan measurements
 */
public record MultiFileCleanUpPlanResult<P>(P plan, RefactoringStatus status,
		MultiFilePlanningMetrics metrics) {

	/** Validates the result. */
	public MultiFileCleanUpPlanResult {
		Objects.requireNonNull(status);
		Objects.requireNonNull(metrics);
		if (status.hasFatalError() && plan != null) {
			throw new IllegalArgumentException();
		}
	}

	/** Compatibility constructor for consumers that do not yet record metrics. */
	public MultiFileCleanUpPlanResult(P plan, RefactoringStatus status) {
		this(plan, status, MultiFilePlanningMetrics.empty());
	}

	/**
	 * Creates a successful result containing a plan.
	 *
	 * @param <P> plan type
	 * @param plan immutable plan
	 * @return successful result
	 */
	public static <P> MultiFileCleanUpPlanResult<P> success(P plan) {
		return new MultiFileCleanUpPlanResult<>(Objects.requireNonNull(plan), new RefactoringStatus(),
				MultiFilePlanningMetrics.empty());
	}

	/**
	 * Creates a successful measured result containing a plan.
	 *
	 * @param <P> plan type
	 * @param plan immutable plan
	 * @param status nonfatal diagnostics
	 * @param metrics planning measurements
	 * @return successful result
	 */
	public static <P> MultiFileCleanUpPlanResult<P> success(P plan, RefactoringStatus status,
			MultiFilePlanningMetrics metrics) {
		if (status.hasFatalError()) {
			throw new IllegalArgumentException("A successful plan cannot carry a fatal status"); //$NON-NLS-1$
		}
		return new MultiFileCleanUpPlanResult<>(Objects.requireNonNull(plan), status, metrics);
	}

	/**
	 * Creates a successful result without a plan.
	 *
	 * @param <P> plan type
	 * @return empty successful result
	 */
	public static <P> MultiFileCleanUpPlanResult<P> noPlan() {
		return new MultiFileCleanUpPlanResult<>(null, new RefactoringStatus(), MultiFilePlanningMetrics.empty());
	}
}
