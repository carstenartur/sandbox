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
 * Stable search requests derived from a container flow graph.
 *
 * <p>This model does not execute JDT searches. The Eclipse-dependent multi-file layer
 * resolves these seeds to source compilation units and repeats planning to a fixed
 * point.</p>
 *
 * @param seeds deterministic, duplicate-free search requests
 */
public record ContainerFlowSearchPlan(List<SearchSeed> seeds) {

	public ContainerFlowSearchPlan {
		seeds= List.copyOf(Objects.requireNonNull(seeds, "seeds")); //$NON-NLS-1$
	}

	/** Returns whether no further source-scope search is requested. */
	public boolean isEmpty() {
		return seeds.isEmpty();
	}

	/** One JDT-search intention independent of Eclipse UI classes. */
	public record SearchSeed(
			String sourceNodeId,
			SearchKind kind,
			String bindingKey,
			String ownerKey,
			String javaElementHandle,
			int signatureIndex,
			String reason) {

		public SearchSeed {
			sourceNodeId= requiredText(sourceNodeId, "sourceNodeId"); //$NON-NLS-1$
			Objects.requireNonNull(kind, "kind"); //$NON-NLS-1$
			bindingKey= optionalText(bindingKey);
			ownerKey= optionalText(ownerKey);
			javaElementHandle= optionalText(javaElementHandle);
			if (signatureIndex < -1) {
				throw new IllegalArgumentException("signatureIndex must be -1 or a parameter index"); //$NON-NLS-1$
			}
			reason= requiredText(reason, "reason"); //$NON-NLS-1$
			if (kind == SearchKind.FIELD_REFERENCES && bindingKey.isEmpty()) {
				throw new IllegalArgumentException("Field reference search requires a binding key"); //$NON-NLS-1$
			}
			if (kind != SearchKind.FIELD_REFERENCES && ownerKey.isEmpty()) {
				throw new IllegalArgumentException("Method search requires an owner key"); //$NON-NLS-1$
			}
		}

		/** Stable key for deterministic de-duplication. */
		public String stableKey() {
			return kind + "|" + bindingKey + '|' + ownerKey + '|'
					+ javaElementHandle + '|' + signatureIndex; //$NON-NLS-1$
		}

		/** Returns whether the workspace layer can resolve the exact Java element. */
		public boolean hasJavaElementHandle() {
			return !javaElementHandle.isEmpty();
		}
	}

	public enum SearchKind {
		FIELD_REFERENCES,
		METHOD_DECLARATION,
		METHOD_CALLERS,
		METHOD_OVERRIDE_FAMILY
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
