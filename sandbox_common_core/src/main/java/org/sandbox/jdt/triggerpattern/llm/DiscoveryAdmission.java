/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.llm;

import java.time.Instant;

import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation.TrafficLight;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Enforces the same candidate-or-no-cleanup response contract for every provider. */
final class DiscoveryAdmission {

	private DiscoveryAdmission() {
	}

	/** An explicit decline is complete even when unused candidate fields are malformed. */
	static CommitEvaluation explicitDecline(JsonObject answer, String commitHash, String message, String repoUrl) {
		if (!isBoolean(answer.get("noCleanup"), true)) { //$NON-NLS-1$
			return null;
		}
		String reason = string(answer, "reason"); //$NON-NLS-1$
		return completedNonCandidate(answer, commitHash, message, repoUrl,
				reason == null || reason.isBlank() ? "Model returned noCleanup" : reason); //$NON-NLS-1$
	}

	static CommitEvaluation malformedFields(JsonObject answer, String commitHash, String message, String repoUrl,
			RuntimeException failure) {
		return completedNonCandidate(answer, commitHash, message, repoUrl,
				"Malformed candidate response fields: " + failure.getClass().getSimpleName()); //$NON-NLS-1$
	}

	private static CommitEvaluation completedNonCandidate(JsonObject answer, String commitHash, String message,
			String repoUrl, String reason) {
		return new CommitEvaluation(commitHash, message, repoUrl, Instant.now(), null, false,
				reason,
				false, null, 0, 0, 0, TrafficLight.NOT_APPLICABLE, string(answer, "category"), false, null, //$NON-NLS-1$
				false, null, null, null, null, string(answer, "summary"), null, null, null, null, //$NON-NLS-1$
				sourceVersion(answer), sourceEvidence(answer));
	}

	static CommitEvaluation apply(JsonObject answer, CommitEvaluation evaluation) {
		if (evaluation.trafficLight() != TrafficLight.GREEN) {
			return evaluation;
		}
		if (!isBoolean(answer.get("noCleanup"), false)) { //$NON-NLS-1$
			return evaluation.withoutCandidate("Positive response must explicitly set noCleanup=false"); //$NON-NLS-1$
		}
		JsonElement confidence = answer.get("confidence"); //$NON-NLS-1$
		if (confidence == null || !confidence.isJsonPrimitive() || !confidence.getAsJsonPrimitive().isNumber()
				|| !Double.isFinite(confidence.getAsDouble())
				|| confidence.getAsDouble() < 0.80 || confidence.getAsDouble() > 1.0) {
			return evaluation.withoutCandidate("Confidence must be a finite number in [0.80, 1]"); //$NON-NLS-1$
		}
		if (!isBoolean(answer.get("relevant"), true) //$NON-NLS-1$
				|| !isBoolean(answer.get("canImplementInCurrentDsl"), true) || evaluation.isDuplicate()) { //$NON-NLS-1$
			return evaluation.withoutCandidate("GREEN conflicts with relevance, DSL support, or duplicate status"); //$NON-NLS-1$
		}
		if (evaluation.sourceEvidence() == null) {
			return evaluation.withoutCandidate("Positive response needs a path and original before/after spans (1..512 characters)"); //$NON-NLS-1$
		}
		return evaluation;
	}

	static String sourceVersion(JsonObject answer) {
		String version = string(answer, "sourceVersion"); //$NON-NLS-1$
		return version == null || version.isBlank() ? "21" : version; //$NON-NLS-1$
	}

	static SourceEvidence sourceEvidence(JsonObject answer) {
		JsonElement value = answer.get("sourceEvidence"); //$NON-NLS-1$
		if (value == null || !value.isJsonObject()) {
			return null;
		}
		JsonObject evidence = value.getAsJsonObject();
		String path = string(evidence, "path"); //$NON-NLS-1$
		String before = string(evidence, "before"); //$NON-NLS-1$
		String after = string(evidence, "after"); //$NON-NLS-1$
		if (!shortNonblank(path) || !shortNonblank(before) || !shortNonblank(after)) {
			return null;
		}
		return new SourceEvidence(path, before, after);
	}

	private static boolean shortNonblank(String value) {
		return value != null && !value.isBlank() && value.length() <= 512;
	}

	private static boolean isBoolean(JsonElement value, boolean expected) {
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
				&& value.getAsBoolean() == expected;
	}

	private static String string(JsonObject object, String field) {
		JsonElement value = object.get(field);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
				? value.getAsString() : null;
	}
}
