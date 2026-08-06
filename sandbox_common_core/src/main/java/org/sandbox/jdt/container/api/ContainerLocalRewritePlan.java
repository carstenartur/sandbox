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
 * Immutable, AST-free rewrite description for one local semantic container member.
 *
 * <p>A later Eclipse-dependent resolver revalidates the binding and every expected
 * occurrence against the current AST before editing. Optional verification edits can
 * retain source uses whose corresponding declarations are changed by the same
 * aggregate multi-file plan.</p>
 */
public record ContainerLocalRewritePlan(
		String compilationUnitHandle,
		String bindingKey,
		String targetInterfaceType,
		String targetImplementationType,
		TargetContainerContract targetContract,
		List<LocalEdit> edits) {

	public ContainerLocalRewritePlan {
		compilationUnitHandle= requiredText(
				compilationUnitHandle, "compilationUnitHandle"); //$NON-NLS-1$
		bindingKey= requiredText(bindingKey, "bindingKey"); //$NON-NLS-1$
		targetInterfaceType= requiredText(targetInterfaceType, "targetInterfaceType"); //$NON-NLS-1$
		targetImplementationType= requiredText(
				targetImplementationType, "targetImplementationType"); //$NON-NLS-1$
		Objects.requireNonNull(targetContract, "targetContract"); //$NON-NLS-1$
		edits= List.copyOf(Objects.requireNonNull(edits, "edits")); //$NON-NLS-1$
		validateEdits(edits);
	}

	/** One local semantic rewrite or verification anchored to source evidence. */
	public record LocalEdit(
			EditKind kind,
			int sourceStart,
			int sourceLength) {

		public LocalEdit {
			Objects.requireNonNull(kind, "kind"); //$NON-NLS-1$
			if (sourceStart < 0 || sourceLength < 0) {
				throw new IllegalArgumentException("Source range must not be negative"); //$NON-NLS-1$
			}
		}
	}

	/** Result that preserves rejection explanations instead of returning null. */
	public record PlanningResult(
			Optional<ContainerLocalRewritePlan> plan,
			List<PlanningDiagnostic> diagnostics) {

		public PlanningResult {
			plan= Objects.requireNonNull(plan, "plan"); //$NON-NLS-1$
			diagnostics= List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics")); //$NON-NLS-1$
			if (plan.isPresent() == !diagnostics.isEmpty()) {
				throw new IllegalArgumentException(
						"A local rewrite result must contain either one plan or diagnostics"); //$NON-NLS-1$
			}
		}

		public static PlanningResult accepted(ContainerLocalRewritePlan plan) {
			return new PlanningResult(Optional.of(plan), List.of());
		}

		public static PlanningResult rejected(List<PlanningDiagnostic> diagnostics) {
			if (diagnostics.isEmpty()) {
				throw new IllegalArgumentException("A rejected rewrite plan requires diagnostics"); //$NON-NLS-1$
			}
			return new PlanningResult(Optional.empty(), diagnostics);
		}

		public boolean ready() {
			return plan.isPresent();
		}
	}

	/** One fail-closed eligibility explanation. */
	public record PlanningDiagnostic(
			DiagnosticKind kind,
			String message) {

		public PlanningDiagnostic {
			Objects.requireNonNull(kind, "kind"); //$NON-NLS-1$
			message= requiredText(message, "message"); //$NON-NLS-1$
		}
	}

	public enum EditKind {
		CHANGE_LOCAL_DECLARATION,
		REPLACE_EMPTY_ARRAY_INITIALIZER,
		REMOVE_ARRAY_GROWTH,
		REPLACE_TAIL_WRITE_WITH_ADD,
		REPLACE_LENGTH_WITH_SIZE,
		/** Verifies an allowed encounter-order traversal requiring no source edit. */
		VERIFY_ENCOUNTER_ITERATION,
		/** Verifies an unchanged argument whose target signature migrates atomically. */
		VERIFY_ARGUMENT_TRANSFER
	}

	public enum DiagnosticKind {
		NOT_AUTOMATIC,
		FLOW_NOT_STRICTLY_LOCAL,
		SOURCE_BINDING_MISMATCH,
		UNSUPPORTED_TARGET,
		UNSUPPORTED_ELEMENT_DOMAIN,
		POSITIONAL_SEMANTICS,
		MISSING_APPEND_PATTERN,
		UNBALANCED_APPEND_PATTERN,
		UNSUPPORTED_EVIDENCE
	}

	private static void validateEdits(List<LocalEdit> edits) {
		if (edits.isEmpty()) {
			throw new IllegalArgumentException("A local rewrite plan requires edits"); //$NON-NLS-1$
		}
		Set<EditKind> kinds= EnumSet.noneOf(EditKind.class);
		for (LocalEdit edit : edits) {
			kinds.add(edit.kind());
		}
		if (!kinds.contains(EditKind.CHANGE_LOCAL_DECLARATION)
				|| !kinds.contains(EditKind.REPLACE_EMPTY_ARRAY_INITIALIZER)
				|| !kinds.contains(EditKind.REMOVE_ARRAY_GROWTH)
				|| !kinds.contains(EditKind.REPLACE_TAIL_WRITE_WITH_ADD)) {
			throw new IllegalArgumentException(
					"A local append-array rewrite requires declaration, initializer, growth and append edits"); //$NON-NLS-1$
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
