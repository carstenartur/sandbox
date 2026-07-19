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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.google.gson.Gson;

/**
 * Applies an explicit review or promotion decision to one verified candidate.
 * Repeating an already-applied decision is a successful no-op so interrupted
 * workflows can be retried safely.
 *
 * <p>Approval always re-runs deterministic verification with the current
 * verifier before recording the human decision. Promotion is independently
 * re-verified by {@link CandidatePromotionCli}.</p>
 */
public final class CandidateReviewCli {

	private static final Gson GSON = new Gson();

	private CandidateReviewCli() {
	}

	public static void main(String[] args) {
		try {
			int result = run(args);
			if (result != 0) {
				System.exit(result);
			}
		} catch (Exception e) {
			System.err.println("Candidate review failed: " + e.getMessage()); //$NON-NLS-1$
			System.exit(1);
		}
	}

	static int run(String[] args) throws IOException {
		Path candidateFile = null;
		String action = null;
		String actor = null;
		String reason = null;

		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			case "--candidate": //$NON-NLS-1$
				candidateFile = Path.of(requireValue(args, ++i, "--candidate")); //$NON-NLS-1$
				break;
			case "--action": //$NON-NLS-1$
				action = requireValue(args, ++i, "--action"); //$NON-NLS-1$
				break;
			case "--actor": //$NON-NLS-1$
				actor = requireValue(args, ++i, "--actor"); //$NON-NLS-1$
				break;
			case "--reason": //$NON-NLS-1$
				reason = requireValue(args, ++i, "--reason"); //$NON-NLS-1$
				break;
			default:
				throw new IllegalArgumentException("Unknown option: " + args[i]); //$NON-NLS-1$
			}
		}

		if (candidateFile == null || !Files.isRegularFile(candidateFile)) {
			throw new IllegalArgumentException("--candidate must reference an existing file"); //$NON-NLS-1$
		}
		if (action == null || action.isBlank()) {
			throw new IllegalArgumentException("--action is required"); //$NON-NLS-1$
		}
		if (actor == null || actor.isBlank()) {
			throw new IllegalArgumentException("--actor is required"); //$NON-NLS-1$
		}

		MiningCandidate candidate = GSON.fromJson(
				Files.readString(candidateFile, StandardCharsets.UTF_8), MiningCandidate.class);
		if (candidate == null) {
			throw new IllegalArgumentException("Candidate JSON is empty"); //$NON-NLS-1$
		}

		String normalizedAction = action.toLowerCase(Locale.ROOT);
		switch (normalizedAction) {
		case "approve": //$NON-NLS-1$
			approve(candidate, actor, normalizeReason(reason, "Approved after human review")); //$NON-NLS-1$
			break;
		case "reject": //$NON-NLS-1$
			applyIdempotentTransition(candidate, CandidateStatus.READY_FOR_REVIEW,
					CandidateStatus.REJECTED, actor,
					requireReason(reason, "A rejection reason is required"), //$NON-NLS-1$
					"Only READY_FOR_REVIEW candidates can be rejected"); //$NON-NLS-1$
			break;
		case "promote": //$NON-NLS-1$
			applyIdempotentTransition(candidate, CandidateStatus.APPROVED,
					CandidateStatus.PROMOTED, actor,
					normalizeReason(reason, "Promotion pull request merged"), //$NON-NLS-1$
					"Only APPROVED candidates can be marked promoted"); //$NON-NLS-1$
			break;
		case "supersede": //$NON-NLS-1$
			if (candidate.getStatus() != CandidateStatus.SUPERSEDED) {
				candidate.transitionTo(CandidateStatus.SUPERSEDED, actor,
						requireReason(reason, "A supersession reason is required")); //$NON-NLS-1$
			}
			break;
		default:
			throw new IllegalArgumentException("Unsupported review action: " + action); //$NON-NLS-1$
		}

		Path absoluteCandidateFile = candidateFile.toAbsolutePath().normalize();
		Path parent = absoluteCandidateFile.getParent();
		if (parent == null) {
			throw new IOException("Candidate file has no parent directory: " + absoluteCandidateFile); //$NON-NLS-1$
		}
		CandidateStore store = new CandidateStore(parent);
		store.save(candidate);
		Path canonicalFile = parent.resolve(candidate.toFileName());
		if (!absoluteCandidateFile.equals(canonicalFile) && Files.exists(absoluteCandidateFile)) {
			Files.delete(absoluteCandidateFile);
		}
		System.out.println(candidate.getCandidateId() + " -> " + candidate.getStatus()); //$NON-NLS-1$
		return 0;
	}

	private static void approve(MiningCandidate candidate, String actor, String reason) {
		if (candidate.getStatus() == CandidateStatus.APPROVED) {
			return;
		}
		if (candidate.getStatus() != CandidateStatus.READY_FOR_REVIEW) {
			throw new IllegalStateException("Only READY_FOR_REVIEW candidates can be approved"); //$NON-NLS-1$
		}
		CandidateVerification currentVerification = new CandidateVerifier().verify(candidate);
		candidate.setVerification(currentVerification);
		if (!currentVerification.successful()) {
			throw new IllegalStateException("Candidate failed current deterministic verification at " //$NON-NLS-1$
					+ currentVerification.stage() + ": " + currentVerification.message()); //$NON-NLS-1$
		}
		candidate.transitionTo(CandidateStatus.APPROVED, actor, reason);
	}

	private static void applyIdempotentTransition(MiningCandidate candidate,
			CandidateStatus expectedSource, CandidateStatus target, String actor,
			String reason, String invalidStateMessage) {
		if (candidate.getStatus() == target) {
			return;
		}
		if (candidate.getStatus() != expectedSource) {
			throw new IllegalStateException(invalidStateMessage);
		}
		candidate.transitionTo(target, actor, reason);
	}

	private static String normalizeReason(String reason, String fallback) {
		return reason == null || reason.isBlank() ? fallback : reason.trim();
	}

	private static String requireReason(String reason, String message) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return reason.trim();
	}

	private static String requireValue(String[] args, int index, String option) {
		if (index >= args.length) {
			throw new IllegalArgumentException(option + " requires a value"); //$NON-NLS-1$
		}
		return args[index];
	}
}
