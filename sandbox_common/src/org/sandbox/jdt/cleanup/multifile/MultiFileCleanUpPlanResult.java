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
 * @param diagnostics immutable machine-readable scope and candidate diagnostics
 */
public record MultiFileCleanUpPlanResult<P>(P plan, RefactoringStatus status,
		MultiFilePlanningMetrics metrics, MultiFileCleanUpDiagnostics diagnostics) {

	/** Validates the result. */
	public MultiFileCleanUpPlanResult {
		Objects.requireNonNull(status);
		Objects.requireNonNull(metrics);
		Objects.requireNonNull(diagnostics);
		if (status.hasFatalError() && plan != null) {
			throw new IllegalArgumentException();
		}
	}

	/** Compatibility constructor for consumers without metrics or structured diagnostics. */
	public MultiFileCleanUpPlanResult(P plan, RefactoringStatus status) {
		this(plan, status, MultiFilePlanningMetrics.empty(), MultiFileCleanUpDiagnostics.empty());
	}

	/** Compatibility constructor for consumers that record metrics but no candidate details. */
	public MultiFileCleanUpPlanResult(P plan, RefactoringStatus status, MultiFilePlanningMetrics metrics) {
		this(plan, status, metrics, MultiFileCleanUpDiagnostics.empty());
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
				MultiFilePlanningMetrics.empty(), MultiFileCleanUpDiagnostics.empty());
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
		return success(plan, status, metrics, MultiFileCleanUpDiagnostics.empty());
	}

	/**
	 * Creates a successful measured and diagnosed result containing a plan.
	 *
	 * @param <P> plan type
	 * @param plan immutable plan
	 * @param status nonfatal diagnostics
	 * @param metrics planning measurements
	 * @param diagnostics structured diagnostics
	 * @return successful result
	 */
	public static <P> MultiFileCleanUpPlanResult<P> success(P plan, RefactoringStatus status,
			MultiFilePlanningMetrics metrics, MultiFileCleanUpDiagnostics diagnostics) {
		if (status.hasFatalError()) {
			throw new IllegalArgumentException("A successful plan cannot carry a fatal status"); //$NON-NLS-1$
		}
		return new MultiFileCleanUpPlanResult<>(Objects.requireNonNull(plan), status, metrics, diagnostics);
	}

	/**
	 * Creates a successful result without a plan.
	 *
	 * @param <P> plan type
	 * @return empty successful result
	 */
	public static <P> MultiFileCleanUpPlanResult<P> noPlan() {
		return new MultiFileCleanUpPlanResult<>(null, new RefactoringStatus(), MultiFilePlanningMetrics.empty(),
				MultiFileCleanUpDiagnostics.empty());
	}
}
