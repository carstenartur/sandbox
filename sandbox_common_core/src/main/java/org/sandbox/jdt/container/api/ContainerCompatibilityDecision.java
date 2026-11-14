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

import java.util.Objects;

/** Immutable compatibility decision for an executable migration or a reported boundary. */
public record ContainerCompatibilityDecision(Mode mode, BridgeFeasibility bridge, String explanation) {
	public enum Mode {
		CLOSED_SOURCE_AUTOMATIC, LOSSLESS_BRIDGE, POLICY_ADAPTER, BREAKING_SOURCE_MIGRATION, MANUAL_REFACTORING
	}

	public enum BridgeFeasibility {
		NO_BRIDGE_REQUIRED,
		LOSSLESS_WITH_PROOF,
		IMPOSSIBLE_RETURN_TYPE_ONLY,
		OVERLOAD_CONFLICT,
		UNPROVEN_SEMANTICS,
		UNKNOWN_EXTERNAL_CONSUMERS
	}

	public ContainerCompatibilityDecision {
		Objects.requireNonNull(mode);
		Objects.requireNonNull(bridge);
		if (Objects.requireNonNull(explanation).isBlank()) {
			throw new IllegalArgumentException("A compatibility decision must explain the boundary."); //$NON-NLS-1$
		}
	}

	public static ContainerCompatibilityDecision closedParameter() {
		return new ContainerCompatibilityDecision(Mode.CLOSED_SOURCE_AUTOMATIC, BridgeFeasibility.NO_BRIDGE_REQUIRED,
				"The complete source parameter family and its callers change together. The old array descriptor is removed. The list preserves encounter order, duplicates and null elements."); //$NON-NLS-1$
	}

	public static ContainerCompatibilityDecision closedReturn() {
		return new ContainerCompatibilityDecision(Mode.CLOSED_SOURCE_AUTOMATIC, BridgeFeasibility.IMPOSSIBLE_RETURN_TYPE_ONLY,
				"The fresh builder becomes the internal return value. All source callers migrate together. Java cannot retain a same-name overload that differs only by return type."); //$NON-NLS-1$
	}

	public static ContainerCompatibilityDecision manual(String reason, String explanation) {
		BridgeFeasibility feasibility= switch (reason) {
			case "IMPOSSIBLE_RETURN_TYPE_ONLY" -> BridgeFeasibility.IMPOSSIBLE_RETURN_TYPE_ONLY; //$NON-NLS-1$
			case "SIGNATURE_OVERLOAD_CONFLICT" -> BridgeFeasibility.OVERLOAD_CONFLICT; //$NON-NLS-1$
			case "EXTERNALLY_VISIBLE_PARAMETER", "EXTERNAL_RETURN_DECLARATION", "REFLECTIVE_SIGNATURE_BOUNDARY", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					"SOURCE_CLOSURE_INCOMPLETE", "SOURCE_CLOSURE_NOT_SELECTED" -> //$NON-NLS-1$ //$NON-NLS-2$
				BridgeFeasibility.UNKNOWN_EXTERNAL_CONSUMERS;
			default -> BridgeFeasibility.UNPROVEN_SEMANTICS;
		};
		return new ContainerCompatibilityDecision(Mode.MANUAL_REFACTORING, feasibility, explanation);
	}

	public String summary() {
		return mode + " (" + bridge + "). " + explanation; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
