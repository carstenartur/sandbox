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
package org.sandbox.mining.core.candidate;

/**
 * Auditable lifecycle transition for a mining candidate.
 *
 * @param from previous status
 * @param to new status
 * @param actor component or reviewer responsible for the transition
 * @param reason reason for the transition
 * @param occurredAt ISO-8601 timestamp
 */
public record CandidateTransition(
		CandidateStatus from,
		CandidateStatus to,
		String actor,
		String reason,
		String occurredAt) {
}
