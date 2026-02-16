/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.nullability;

import java.util.List;

/**
 * The result of a nullability analysis for a single expression.
 *
 * @param status the determined null status
 * @param reason human-readable explanation
 * @param evidence lines of evidence (e.g., null-check locations)
 * @since 1.2.6
 */
public record NullabilityResult(NullStatus status, String reason, List<String> evidence) {

	/** Convenience constant for an unknown result. */
	static final NullabilityResult UNKNOWN = new NullabilityResult(
			NullStatus.UNKNOWN, "unknown", List.of()); //$NON-NLS-1$
}
