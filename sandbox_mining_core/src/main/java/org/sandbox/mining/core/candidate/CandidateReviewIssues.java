/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.mining.core.candidate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;

/** Reconciles a read-only GitHub REST issue snapshot without inferring approval. */
final class CandidateReviewIssues {
	private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();

	private static final Pattern MARKER = Pattern.compile("\\A<!-- mining-candidate-id: ([a-f0-9]{64}) -->");
	private static final Pattern REVISION = Pattern.compile("(?m)^\\| Revision \\| `(\\d+)` \\|\\s*$");

	private CandidateReviewIssues() {
	}

	/** Accepts one strict JSON snapshot containing REST objects, an array, or gh --slurp pages. */
	static void reconcile(List<MiningCandidate> candidates, Path snapshot) throws IOException {
		List<ClosedIssue> closed = new ArrayList<>();
		try (var reader = Files.newBufferedReader(snapshot, StandardCharsets.UTF_8)) {
			List<JsonObject> issues = new ArrayList<>();
			JsonElement root = GSON.fromJson(reader, JsonElement.class);
			if (root == null) {
				throw new IOException("Review issue snapshot is empty: " + snapshot);
			}
			collectIssues(root, issues);
			for (JsonObject issue : issues) {
				if (!issue.has("number") || !("open".equals(string(issue, "state")) || "closed".equals(string(issue, "state")))) {
					throw new IOException("Review issue snapshot contains an issue without number/state: " + snapshot);
				}
				if (!"closed".equals(string(issue, "state")) || issue.has("pull_request")) {
					continue;
				}
				String body = string(issue, "body");
				var marker = MARKER.matcher(body);
				if (!marker.find()) {
					continue;
				}
				var revision = REVISION.matcher(body);
				if (!revision.find()) {
					continue;
				}
				closed.add(new ClosedIssue(marker.group(1), Integer.parseInt(revision.group(1)),
						issue.get("number").getAsInt(), string(issue, "state_reason")));
			}
		} catch (RuntimeException e) {
			throw new IOException("Invalid review issue snapshot: " + snapshot, e);
		}
		closed.sort(Comparator.comparing((ClosedIssue issue) -> !issue.rejected()).thenComparingInt(ClosedIssue::number));
		for (MiningCandidate candidate : candidates) {
			if (candidate.getStatus() != CandidateStatus.READY_FOR_REVIEW
					&& candidate.getStatus() != CandidateStatus.BEHAVIOR_VALID
					&& candidate.getStatus() != CandidateStatus.DSL_VALID
					&& candidate.getStatus() != CandidateStatus.DISCOVERED) {
				continue;
			}
			closed.stream().filter(issue -> issue.candidateId().equals(candidate.getCandidateId())
					&& issue.revision() == candidate.getRevision()).findFirst().ifPresent(issue ->
				candidate.transitionTo(issue.rejected() ? CandidateStatus.REJECTED : CandidateStatus.SUPERSEDED,
						"CandidateReviewIssues", "Review issue #" + issue.number() + " closed (" + issue.reason()
								+ "); " + (issue.rejected() ? "existing rejection retained" : "historical review; promotion not inferred")));
		}
	}

	private static void collectIssues(JsonElement value, List<JsonObject> issues) {
		if (value.isJsonArray()) {
			for (JsonElement element : value.getAsJsonArray()) {
				collectIssues(element, issues);
			}
		} else {
			issues.add(value.getAsJsonObject());
		}
	}

	private static String string(JsonObject object, String field) {
		return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : "";
	}

	private record ClosedIssue(String candidateId, int revision, int number, String reason) {
		boolean rejected() {
			return "not_planned".equals(reason);
		}
	}
}
