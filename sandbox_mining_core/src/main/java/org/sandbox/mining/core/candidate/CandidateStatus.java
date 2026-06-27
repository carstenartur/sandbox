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
 * Lifecycle status of a mined cleanup candidate.
 *
 * <p>Candidates progress through these states as they are validated,
 * tested, and eventually promoted into productive bundled hint files.</p>
 *
 * <pre>
 * DISCOVERED → DSL_VALID → TEST_GENERATED → TEST_PASSED → READY_FOR_PR → PROMOTED
 *                                                        ↘
 *                                                         REJECTED (at any stage)
 * </pre>
 */
public enum CandidateStatus {

	/**
	 * Initial state: candidate discovered by LLM from a commit diff.
	 * DSL rule has not yet been validated.
	 */
	DISCOVERED,

	/**
	 * DSL rule has been parsed and validated by {@code DslValidator}.
	 */
	DSL_VALID,

	/**
	 * A lightweight JUnit test skeleton has been auto-generated for this candidate.
	 */
	TEST_GENERATED,

	/**
	 * The generated test was executed and passed (beforeExample matches,
	 * replacement equals afterExample, negativeExample does NOT match).
	 */
	TEST_PASSED,

	/**
	 * Candidate has passed all validations and is ready for PR/issue creation.
	 */
	READY_FOR_PR,

	/**
	 * Candidate has been promoted into a productive bundled {@code .sandbox-hint} file.
	 */
	PROMOTED,

	/**
	 * Candidate has been rejected by a reviewer or failed automated checks.
	 */
	REJECTED
}
