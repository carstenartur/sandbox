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
import java.util.Map;
import java.util.TreeMap;
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

	/**
	 * Accepts one strict JSON snapshot containing REST objects, an array, or gh --slurp pages.
	 * Creator metadata is required; a body marker alone is not review authority.
	 */
	static List<ReviewIssue> reconcile(List<MiningCandidate> candidates, Path snapshot) throws IOException {
		List<ReviewIssue> reviews = new ArrayList<>();
		try (var reader = Files.newBufferedReader(snapshot, StandardCharsets.UTF_8)) {
			List<JsonObject> issues = new ArrayList<>();
			JsonElement root = GSON.fromJson(reader, JsonElement.class);
			if (root == null) {
				throw new IOException("Review issue snapshot is empty: " + snapshot);
			}
			collectIssues(root, issues);
			for (JsonObject issue : issues) {
				int number = positiveInteger(issue.get("number"));
				String state = requiredString(issue, "state", false);
				String body = requiredString(issue, "body", true);
				String reason = requiredString(issue, "state_reason", true);
				boolean trusted = trustedCreator(issue);
				if (!"open".equals(state) && !"closed".equals(state)) {
					throw new IOException("Review issue snapshot contains an invalid state: " + snapshot);
				}
				if (!trusted || issue.has("pull_request")) {
					continue;
				}
				var marker = MARKER.matcher(body);
				if (!marker.find()) {
					continue;
				}
				var revision = REVISION.matcher(body);
				if (!revision.find()) {
					continue;
				}
				reviews.add(new ReviewIssue(marker.group(1), Integer.parseInt(revision.group(1)),
						number, reason, "closed".equals(state)));
			}
		} catch (RuntimeException e) {
			throw new IOException("Invalid review issue snapshot: " + snapshot, e);
		}
		reviews.sort(Comparator.comparing((ReviewIssue issue) -> !issue.rejected()).thenComparingInt(ReviewIssue::number));
		for (MiningCandidate candidate : candidates) {
			if (candidate.getStatus() != CandidateStatus.READY_FOR_REVIEW
					&& candidate.getStatus() != CandidateStatus.BEHAVIOR_VALID
					&& candidate.getStatus() != CandidateStatus.DSL_VALID
					&& candidate.getStatus() != CandidateStatus.DISCOVERED) {
				continue;
			}
			reviews.stream().filter(issue -> issue.closed() && issue.candidateId().equals(candidate.getCandidateId())
					&& issue.revision() == candidate.getRevision()).findFirst().ifPresent(issue ->
				candidate.transitionTo(issue.rejected() ? CandidateStatus.REJECTED : CandidateStatus.SUPERSEDED,
						"CandidateReviewIssues", "Review issue #" + issue.number() + " closed (" + issue.reason()
								+ "); " + (issue.rejected() ? "existing rejection retained" : "historical review; promotion not inferred")));
		}
		return List.copyOf(reviews);
	}

	/** Build creation deduplication only after migrations have settled the current revision. */
	static Map<String, Integer> index(List<MiningCandidate> candidates, List<ReviewIssue> reviews) {
		Map<String, Integer> index = new TreeMap<>();
		for (MiningCandidate candidate : candidates) {
			for (ReviewIssue issue : reviews) {
				if (issue.candidateId().equals(candidate.getCandidateId()) && issue.revision() == candidate.getRevision()) {
					index.merge(candidate.getCandidateId(), issue.number(), Math::min);
				}
			}
		}
		return index;
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

	private static int positiveInteger(JsonElement value) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException("Expected a positive integer");
		}
		int number = value.getAsBigDecimal().intValueExact();
		if (number <= 0) {
			throw new IllegalArgumentException("Expected a positive integer");
		}
		return number;
	}

	private static String requiredString(JsonObject object, String field, boolean nullable) {
		JsonElement value = object.get(field);
		if (value != null && nullable && value.isJsonNull()) {
			return "";
		}
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
			throw new IllegalArgumentException("Expected explicit " + field + " string" + (nullable ? " or null" : ""));
		}
		return value.getAsString();
	}

	private static boolean trustedCreator(JsonObject issue) {
		JsonElement value = issue.get("user");
		if (value == null) {
			throw new IllegalArgumentException("Expected explicit issue creator metadata");
		}
		if (value.isJsonNull()) {
			return false;
		}
		JsonObject user = value.getAsJsonObject();
		int id = positiveInteger(user.get("id"));
		String login = requiredString(user, "login", false);
		String type = requiredString(user, "type", false);
		// Existing review issues are created with this workflow's GITHUB_TOKEN.
		// Labels and performed_via_github_app are absent on historical reviews.
		return id == 41898282 && "github-actions[bot]".equals(login) && "Bot".equals(type);
	}

	record ReviewIssue(String candidateId, int revision, int number, String reason, boolean closed) {
		boolean rejected() {
			return "not_planned".equals(reason);
		}
	}
}
