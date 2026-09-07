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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.ArgumentTransfer;

/**
 * Immutable aggregate plan for one closed-source local-array to parameter-list
 * migration.
 *
 * <p>One local source may feed a complete source-resolved parameter atomicity group,
 * including interface declarations and all editable implementations/overrides. The
 * unchanged call expressions are tied to exact target method handles and parameter
 * indices. Every local and parameter member is re-resolved before the existing
 * coordinated cleanup lifecycle emits edits.</p>
 */
public record ClosedSourceParameterMigrationPlan(
		TargetContainerContract targetContract,
		ContainerLocalRewritePlan callerPlan,
		List<ContainerParameterRewritePlan> parameterPlans) {

	/** Compatibility constructor for the existing single-parameter vertical slice. */
	public ClosedSourceParameterMigrationPlan(
			TargetContainerContract targetContract,
			ContainerLocalRewritePlan callerPlan,
			ContainerParameterRewritePlan parameterPlan) {
		this(targetContract, callerPlan, List.of(parameterPlan));
	}

	public ClosedSourceParameterMigrationPlan {
		Objects.requireNonNull(targetContract, "targetContract"); //$NON-NLS-1$
		Objects.requireNonNull(callerPlan, "callerPlan"); //$NON-NLS-1$
		parameterPlans= List.copyOf(
				Objects.requireNonNull(parameterPlans, "parameterPlans")); //$NON-NLS-1$
		if (parameterPlans.isEmpty()) {
			throw new IllegalArgumentException(
					"A closed-source parameter migration requires parameter rewrites"); //$NON-NLS-1$
		}
		if (!targetContract.equals(callerPlan.targetContract())) {
			throw new IllegalArgumentException(
					"All closed-source migration members must share one target contract"); //$NON-NLS-1$
		}
		for (ContainerParameterRewritePlan parameterPlan : parameterPlans) {
			if (!targetContract.equals(parameterPlan.targetContract())) {
				throw new IllegalArgumentException(
						"All closed-source migration members must share one target contract"); //$NON-NLS-1$
			}
		}
		validateUniqueParameterTargets(parameterPlans);
		validateArgumentTransfers(callerPlan, parameterPlans);
	}

	/** Returns affected compilation-unit handles in deterministic execution order. */
	public List<String> affectedCompilationUnitHandles() {
		Set<String> handles= new LinkedHashSet<>();
		handles.add(callerPlan.compilationUnitHandle());
		for (ContainerParameterRewritePlan parameterPlan : parameterPlans) {
			handles.add(parameterPlan.compilationUnitHandle());
		}
		return List.copyOf(handles);
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
		PARAMETER_REWRITE_REJECTED
	}

	private static void validateUniqueParameterTargets(
			List<ContainerParameterRewritePlan> parameterPlans) {
		Set<ParameterTarget> targets= HashSet.newHashSet(parameterPlans.size());
		for (ContainerParameterRewritePlan parameterPlan : parameterPlans) {
			ParameterTarget target= new ParameterTarget(
					parameterPlan.methodJavaElementHandle(),
					parameterPlan.parameterIndex());
			if (!targets.add(target)) {
				throw new IllegalArgumentException(
						"Closed-source parameter targets must be unique: " + target); //$NON-NLS-1$
			}
		}
	}

	private static void validateArgumentTransfers(
			ContainerLocalRewritePlan caller,
			List<ContainerParameterRewritePlan> parameterPlans) {
		if (caller.argumentTransfers().isEmpty()) {
			throw new IllegalArgumentException(
					"A closed-source parameter migration requires an exact argument transfer"); //$NON-NLS-1$
		}
		Set<ParameterTarget> targets= HashSet.newHashSet(parameterPlans.size());
		for (ContainerParameterRewritePlan parameterPlan : parameterPlans) {
			targets.add(new ParameterTarget(
					parameterPlan.methodJavaElementHandle(),
					parameterPlan.parameterIndex()));
		}
		for (ArgumentTransfer transfer : caller.argumentTransfers()) {
			ParameterTarget target= new ParameterTarget(
					transfer.methodJavaElementHandle(),
					transfer.parameterIndex());
			if (!targets.contains(target)) {
				throw new IllegalArgumentException(
						"Caller argument target has no matching parameter rewrite: " + target); //$NON-NLS-1$
			}
		}
	}

	private static String requiredText(String value, String fieldName) {
		String text= Objects.requireNonNull(value, fieldName).strip();
		if (text.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must not be empty"); //$NON-NLS-1$
		}
		return text;
	}

	private record ParameterTarget(String methodJavaElementHandle, int parameterIndex) {
		private ParameterTarget {
			methodJavaElementHandle= requiredText(
					methodJavaElementHandle, "methodJavaElementHandle"); //$NON-NLS-1$
			if (parameterIndex < 0) {
				throw new IllegalArgumentException(
						"parameterIndex must not be negative"); //$NON-NLS-1$
			}
		}
	}
}
