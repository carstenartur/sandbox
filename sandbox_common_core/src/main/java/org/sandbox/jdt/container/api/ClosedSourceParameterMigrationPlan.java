/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.ArgumentTransfer;

/**
 * Immutable two-compilation-unit plan for the first closed-source local-array to
 * parameter-list migration.
 *
 * <p>The unchanged call expression connects the two local edits: the caller's array
 * declaration becomes a list and the exact callee parameter becomes the same list
 * contract. Both plans must be applied through one coordinated cleanup lifecycle.</p>
 */
public record ClosedSourceParameterMigrationPlan(
		TargetContainerContract targetContract,
		ContainerLocalRewritePlan callerPlan,
		ContainerParameterRewritePlan parameterPlan) {

	public ClosedSourceParameterMigrationPlan {
		Objects.requireNonNull(targetContract, "targetContract"); //$NON-NLS-1$
		Objects.requireNonNull(callerPlan, "callerPlan"); //$NON-NLS-1$
		Objects.requireNonNull(parameterPlan, "parameterPlan"); //$NON-NLS-1$
		if (!targetContract.equals(callerPlan.targetContract())
				|| !targetContract.equals(parameterPlan.targetContract())) {
			throw new IllegalArgumentException(
					"All closed-source migration members must share one target contract"); //$NON-NLS-1$
		}
		if (callerPlan.compilationUnitHandle()
				.equals(parameterPlan.compilationUnitHandle())) {
			throw new IllegalArgumentException(
					"The first aggregate slice requires distinct caller and parameter units"); //$NON-NLS-1$
		}
		validateArgumentTransfer(callerPlan, parameterPlan);
	}

	/** Returns the two affected compilation-unit handles in execution order. */
	public List<String> affectedCompilationUnitHandles() {
		return List.of(
				callerPlan.compilationUnitHandle(),
				parameterPlan.compilationUnitHandle());
	}

	/** Planning result retaining fail-closed aggregate diagnostics. */
	public record PlanningResult(
			Optional<ClosedSourceParameterMigrationPlan> plan,
			List<PlanningDiagnostic> diagnostics) {

		public PlanningResult {
			plan= Objects.requireNonNull(plan, "plan"); //$NON-NLS-1$
			diagnostics= List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics")); //$NON-NLS-1$
			if (plan.isPresent() == !diagnostics.isEmpty()) {
				throw new IllegalArgumentException(
						"An aggregate result must contain either one plan or diagnostics"); //$NON-NLS-1$
			}
		}

		public static PlanningResult accepted(
				ClosedSourceParameterMigrationPlan plan) {
			return new PlanningResult(Optional.of(plan), List.of());
		}

		public static PlanningResult rejected(
				List<PlanningDiagnostic> diagnostics) {
			if (diagnostics.isEmpty()) {
				throw new IllegalArgumentException(
						"A rejected aggregate migration requires diagnostics"); //$NON-NLS-1$
			}
			return new PlanningResult(Optional.empty(), diagnostics);
		}

		public boolean ready() {
			return plan.isPresent();
		}
	}

	/** One aggregate eligibility or delegated member diagnostic. */
	public record PlanningDiagnostic(DiagnosticKind kind, String message) {

		public PlanningDiagnostic {
			Objects.requireNonNull(kind, "kind"); //$NON-NLS-1$
			message= requiredText(message, "message"); //$NON-NLS-1$
		}
	}

	public enum DiagnosticKind {
		UNSUPPORTED_FLOW_TOPOLOGY,
		PROFILE_NOT_FOUND,
		RECOMMENDATION_MISMATCH,
		SIGNATURE_PLAN_MISMATCH,
		LOCAL_REWRITE_REJECTED,
		PARAMETER_REWRITE_REJECTED,
		SAME_COMPILATION_UNIT
	}

	private static void validateArgumentTransfer(
			ContainerLocalRewritePlan caller,
			ContainerParameterRewritePlan parameter) {
		if (caller.argumentTransfers().size() != 1) {
			throw new IllegalArgumentException(
					"The first aggregate slice requires one exact argument transfer"); //$NON-NLS-1$
		}
		ArgumentTransfer transfer= caller.argumentTransfers().get(0);
		if (!transfer.methodJavaElementHandle()
				.equals(parameter.methodJavaElementHandle())
				|| transfer.parameterIndex() != parameter.parameterIndex()) {
			throw new IllegalArgumentException(
					"Caller argument target and parameter rewrite must describe the same method position"); //$NON-NLS-1$
		}
	}

	private static String requiredText(String value, String fieldName) {
		String text= Objects.requireNonNull(value, fieldName).strip();
		if (text.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must not be empty"); //$NON-NLS-1$
		}
		return text;
	}
}
