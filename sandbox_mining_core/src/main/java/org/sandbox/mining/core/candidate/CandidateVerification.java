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

import java.time.Instant;

/**
 * Structured result of deterministic candidate verification.
 *
 * @param successful whether all verification stages passed
 * @param stage final stage reached
 * @param message human-readable diagnostic
 * @param verifiedAt ISO-8601 timestamp
 * @param verifierVersion verifier implementation version
 * @param matches number of matches produced for the positive example
 * @param replacements number of positive matches with a replacement
 */
public record CandidateVerification(
		boolean successful,
		Stage stage,
		String message,
		String verifiedAt,
		String verifierVersion,
		int matches,
		int replacements) {

	/** Deterministic verification stages. */
	public enum Stage {
		SCHEMA,
		DSL_VALIDATION,
		DSL_PARSE,
		BEFORE_PARSE,
		BEFORE_MATCH,
		AFTER_REWRITE,
		NEGATIVE_PARSE,
		NEGATIVE_MATCH,
		SUCCESS
	}

	/** Creates a failed verification result. */
	public static CandidateVerification failure(Stage stage, String message,
			String verifierVersion, int matches, int replacements) {
		return new CandidateVerification(false, stage, message, Instant.now().toString(),
				verifierVersion, matches, replacements);
	}

	/** Creates a successful verification result. */
	public static CandidateVerification success(String verifierVersion, int matches,
			int replacements) {
		return new CandidateVerification(true, Stage.SUCCESS,
				"Candidate behavior verified", Instant.now().toString(), //$NON-NLS-1$
				verifierVersion, matches, replacements);
	}
}
