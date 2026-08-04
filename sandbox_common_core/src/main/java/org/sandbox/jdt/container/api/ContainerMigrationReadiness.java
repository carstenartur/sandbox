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

/**
 * Final execution gate assembled from semantic flow, signature and bridge-policy
 * planning.
 *
 * <p>The status is deliberately monotonic: missing proof is more restrictive than a
 * user-selectable policy, and a fatal inconsistency rejects the migration entirely.</p>
 */
public record ContainerMigrationReadiness(
		TargetContainerContract targetContract,
		ExecutionStatus status,
		List<ExecutionBlocker> blockers) {

	public ContainerMigrationReadiness {
		Objects.requireNonNull(targetContract, "targetContract"); //$NON-NLS-1$
		Objects.requireNonNull(status, "status"); //$NON-NLS-1$
		blockers= List.copyOf(Objects.requireNonNull(blockers, "blockers")); //$NON-NLS-1$
		if (status == ExecutionStatus.AUTOMATIC && !blockers.isEmpty()) {
			throw new IllegalArgumentException(
					"An automatic migration cannot retain execution blockers"); //$NON-NLS-1$
		}
		if (status == ExecutionStatus.REJECTED
				&& blockers.stream().noneMatch(blocker -> blocker.severity() == BlockerSeverity.FATAL)) {
			throw new IllegalArgumentException(
					"A rejected migration requires at least one fatal blocker"); //$NON-NLS-1$
		}
	}

	/** Returns whether any form of source rewrite may currently be offered. */
	public boolean isExecutable() {
		return status == ExecutionStatus.AUTOMATIC
				|| status == ExecutionStatus.INTERACTIVE_POLICY
				|| status == ExecutionStatus.INTERACTIVE_BREAKING;
	}

	/** One explicit reason that limits or prevents execution. */
	public record ExecutionBlocker(
			BlockerProperty property,
			BlockerSeverity severity,
			String sourceId,
			String explanation) {

		public ExecutionBlocker {
			Objects.requireNonNull(property, "property"); //$NON-NLS-1$
			Objects.requireNonNull(severity, "severity"); //$NON-NLS-1$
			sourceId= requiredText(sourceId, "sourceId"); //$NON-NLS-1$
			explanation= requiredText(explanation, "explanation"); //$NON-NLS-1$
		}
	}

	public enum ExecutionStatus {
		REJECTED,
		REPORT_ONLY,
		INTERACTIVE_POLICY,
		INTERACTIVE_BREAKING,
		AUTOMATIC
	}

	public enum BlockerSeverity {
		FATAL,
		PROOF_REQUIRED,
		POLICY_REQUIRED,
		BREAKING_CHANGE
	}

	public enum BlockerProperty {
		FLOW,
		TARGET_CONTRACT,
		ORDER,
		UNIQUENESS,
		MUTABILITY,
		NULLS,
		ALIASING,
		CONCURRENCY,
		SIGNATURES,
		ADAPTER_FORM
	}

	private static String requiredText(String value, String fieldName) {
		String text= Objects.requireNonNull(value, fieldName).strip();
		if (text.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must not be empty"); //$NON-NLS-1$
		}
		return text;
	}
}
