/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

/** Stable lifecycle outcome for one coordinated-cleanup candidate. */
public enum MultiFileCandidateOutcome {
	/** Candidate was found but has not yet passed semantic validation. */
	FOUND,
	/** Candidate is semantically applicable and represented by a retained plan entry. */
	APPLICABLE,
	/** Candidate was refused because its complete migration could not be proved safe. */
	REJECTED,
	/** Candidate has local edits in the immutable coordinated plan. */
	TRANSFORMED
}
