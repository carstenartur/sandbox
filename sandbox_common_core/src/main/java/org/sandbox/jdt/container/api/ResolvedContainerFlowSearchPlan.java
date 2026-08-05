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
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchKind;

/**
 * Exact Java-model targets produced by resolving a container flow search plan.
 *
 * <p>The original search intention is retained for every exact field or method so a
 * later parser pass can distinguish declarations, callers and override-family
 * members without repeating workspace model resolution.</p>
 */
public record ResolvedContainerFlowSearchPlan(List<ResolvedSearchTarget> targets) {

	public ResolvedContainerFlowSearchPlan {
		targets= List.copyOf(Objects.requireNonNull(targets, "targets")); //$NON-NLS-1$
		Set<String> keys= new HashSet<>();
		for (ResolvedSearchTarget target : targets) {
			if (!keys.add(target.stableKey())) {
				throw new IllegalArgumentException(
						"Duplicate resolved container flow target: " + target.stableKey()); //$NON-NLS-1$
			}
		}
	}

	/** Returns an empty, immutable resolved plan. */
	public static ResolvedContainerFlowSearchPlan empty() {
		return new ResolvedContainerFlowSearchPlan(List.of());
	}

	/** Returns whether no exact continuation target was resolved. */
	public boolean isEmpty() {
		return targets.isEmpty();
	}

	/** One exact field or method related to one original graph boundary. */
	public record ResolvedSearchTarget(
			String sourceNodeId,
			SearchKind searchKind,
			TargetKind targetKind,
			String bindingKey,
			String ownerKey,
			String javaElementHandle,
			int signatureIndex,
			String reason) {

		public ResolvedSearchTarget {
			sourceNodeId= requiredText(sourceNodeId, "sourceNodeId"); //$NON-NLS-1$
			Objects.requireNonNull(searchKind, "searchKind"); //$NON-NLS-1$
			Objects.requireNonNull(targetKind, "targetKind"); //$NON-NLS-1$
			bindingKey= optionalText(bindingKey);
			ownerKey= optionalText(ownerKey);
			javaElementHandle= requiredText(javaElementHandle, "javaElementHandle"); //$NON-NLS-1$
			if (signatureIndex < -1) {
				throw new IllegalArgumentException(
						"signatureIndex must be -1 or a parameter index"); //$NON-NLS-1$
			}
			reason= requiredText(reason, "reason"); //$NON-NLS-1$
			if (targetKind == TargetKind.FIELD && searchKind != SearchKind.FIELD_REFERENCES) {
				throw new IllegalArgumentException(
						"A resolved field must originate from a field-reference search"); //$NON-NLS-1$
			}
			if (targetKind == TargetKind.FIELD && signatureIndex != -1) {
				throw new IllegalArgumentException(
						"A resolved field cannot have a signature index"); //$NON-NLS-1$
			}
		}

		/** Stable deterministic de-duplication key. */
		public String stableKey() {
			return sourceNodeId + '|' + searchKind + '|' + targetKind + '|'
					+ javaElementHandle + '|' + signatureIndex;
		}
	}

	public enum TargetKind {
		FIELD,
		METHOD
	}

	private static String requiredText(String value, String fieldName) {
		String text= Objects.requireNonNull(value, fieldName).strip();
		if (text.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must not be empty"); //$NON-NLS-1$
		}
		return text;
	}

	private static String optionalText(String value) {
		return value == null ? "" : value.strip(); //$NON-NLS-1$
	}
}
