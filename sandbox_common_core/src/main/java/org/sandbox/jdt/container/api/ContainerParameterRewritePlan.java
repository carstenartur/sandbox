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

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable, AST-free rewrite description for one closed-source array parameter.
 *
 * <p>The first executable slice changes a one-dimensional reference array parameter
 * to a list and translates only {@code length} reads. Enhanced-for iteration is
 * retained and revalidated. Every other parameter use rejects the complete plan.</p>
 */
public record ContainerParameterRewritePlan(
		String compilationUnitHandle,
		String methodJavaElementHandle,
		String parameterBindingKey,
		int parameterIndex,
		String targetInterfaceType,
		TargetContainerContract targetContract,
		List<ParameterEdit> edits) {

	public ContainerParameterRewritePlan {
		compilationUnitHandle= requiredText(
				compilationUnitHandle, "compilationUnitHandle"); //$NON-NLS-1$
		methodJavaElementHandle= requiredText(
				methodJavaElementHandle, "methodJavaElementHandle"); //$NON-NLS-1$
		parameterBindingKey= requiredText(
				parameterBindingKey, "parameterBindingKey"); //$NON-NLS-1$
		if (parameterIndex < 0) {
			throw new IllegalArgumentException("parameterIndex must not be negative"); //$NON-NLS-1$
		}
		targetInterfaceType= requiredText(targetInterfaceType, "targetInterfaceType"); //$NON-NLS-1$
		Objects.requireNonNull(targetContract, "targetContract"); //$NON-NLS-1$
		edits= List.copyOf(Objects.requireNonNull(edits, "edits")); //$NON-NLS-1$
		validateEdits(edits);
	}

	/** One parameter rewrite or verification anchored to immutable source evidence. */
	public record ParameterEdit(EditKind kind, int sourceStart, int sourceLength) {

		public ParameterEdit {
			Objects.requireNonNull(kind, "kind"); //$NON-NLS-1$
			if (sourceStart < 0 || sourceLength < 0) {
				throw new IllegalArgumentException("Source range must not be negative"); //$NON-NLS-1$
			}
		}
	}

	/** Planning result retaining complete fail-closed diagnostics. */
	public record PlanningResult(
			Optional<ContainerParameterRewritePlan> plan,
			List<PlanningDiagnostic> diagnostics) {

		public PlanningResult {
			plan= Objects.requireNonNull(plan, "plan"); //$NON-NLS-1$
			diagnostics= List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics")); //$NON-NLS-1$
			if (plan.isPresent() == !diagnostics.isEmpty()) {
				throw new IllegalArgumentException(
						"A parameter rewrite result must contain either one plan or diagnostics"); //$NON-NLS-1$
			}
		}

		public static PlanningResult accepted(ContainerParameterRewritePlan plan) {
			return new PlanningResult(Optional.of(plan), List.of());
		}

		public static PlanningResult rejected(List<PlanningDiagnostic> diagnostics) {
			if (diagnostics.isEmpty()) {
				throw new IllegalArgumentException(
						"A rejected parameter rewrite requires diagnostics"); //$NON-NLS-1$
			}
			return new PlanningResult(Optional.empty(), diagnostics);
		}

		public boolean ready() {
			return plan.isPresent();
		}
	}

	/** One eligibility diagnostic. */
	public record PlanningDiagnostic(DiagnosticKind kind, String message) {

		public PlanningDiagnostic {
			Objects.requireNonNull(kind, "kind"); //$NON-NLS-1$
			message= requiredText(message, "message"); //$NON-NLS-1$
		}
	}

	public enum EditKind {
		CHANGE_PARAMETER_DECLARATION,
		REPLACE_LENGTH_WITH_SIZE,
		VERIFY_ENCOUNTER_ITERATION
	}

	public enum DiagnosticKind {
		NOT_AUTOMATIC,
		SIGNATURE_PLAN_NOT_CLOSED_SOURCE,
		UNSUPPORTED_SIGNATURE_GROUP,
		SIGNATURE_MEMBER_MISMATCH,
		FLOW_NODE_MISMATCH,
		UNSUPPORTED_TARGET,
		INCOMPLETE_PARAMETER_PROFILE,
		UNSUPPORTED_PARAMETER_USAGE,
		UNSUPPORTED_EVIDENCE
	}

	private static void validateEdits(List<ParameterEdit> edits) {
		if (edits.isEmpty()) {
			throw new IllegalArgumentException("A parameter rewrite plan requires edits"); //$NON-NLS-1$
		}
		Set<EditKind> kinds= EnumSet.noneOf(EditKind.class);
		for (ParameterEdit edit : edits) {
			kinds.add(edit.kind());
		}
		if (!kinds.contains(EditKind.CHANGE_PARAMETER_DECLARATION)) {
			throw new IllegalArgumentException(
					"A parameter rewrite requires a declaration edit"); //$NON-NLS-1$
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
