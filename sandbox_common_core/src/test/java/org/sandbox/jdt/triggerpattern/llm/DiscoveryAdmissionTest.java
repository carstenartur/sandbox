/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation.TrafficLight;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Admission tests exercise both actual provider response parsers without HTTP. */
class DiscoveryAdmissionTest {
	private record FieldChange(String field, String value) {}

	static Stream<Arguments> rejectedResponses() {
		return Stream.of(Boolean.TRUE, Boolean.FALSE).flatMap(gemini -> Stream.of(
				new FieldChange("noCleanup", "true"), new FieldChange("noCleanup", null),
				new FieldChange("noCleanup", "\"false\""), new FieldChange("confidence", null),
				new FieldChange("confidence", "0.79"), new FieldChange("confidence", "1.01"),
				new FieldChange("confidence", "-1"), new FieldChange("confidence", "1e999"),
				new FieldChange("confidence", "\"NaN\""), new FieldChange("confidence", "\"0.9\""),
				new FieldChange("relevant", "false"), new FieldChange("canImplementInCurrentDsl", "false"),
				new FieldChange("isDuplicate", "true"), new FieldChange("sourceEvidence", null),
				new FieldChange("sourceEvidence", "{}"))
				.map(change -> Arguments.of(gemini, change.field(), change.value())));
	}

	@ParameterizedTest
	@MethodSource({ "rejectedResponses", "malformedFields" })
	void contradictoryOrIncompleteCandidateRemainsACompletedEvaluation(boolean gemini, String field, String value) {
		JsonObject candidate = candidate();
		if (value == null) candidate.remove(field);
		else candidate.add(field, JsonParser.parseString(value));
		CommitEvaluation result = parse(gemini, candidate);
		assertNotNull(result, "A rejection must not become an unanswered commit to retry");
		assertEquals("commit", result.commitHash());
		assertEquals("17", result.sourceVersion());
		assertEquals(TrafficLight.NOT_APPLICABLE, result.trafficLight());
		assertFalse(result.relevant());
		assertFalse(result.canImplementInCurrentDsl());
		assertNull(result.dslRule());
		assertNotNull(result.irrelevantReason());
	}

	static Stream<Arguments> malformedFields() {
		return Stream.of(Boolean.TRUE, Boolean.FALSE).flatMap(gemini -> Stream.of(
				new FieldChange("relevant", "{}"), new FieldChange("dslRule", "{}"),
				new FieldChange("reusability", "\"n/a\""))
				.map(change -> Arguments.of(gemini, change.field(), change.value())));
	}

	@ParameterizedTest
	@MethodSource("malformedFields")
	void batchRetainsMalformedCandidateAndItsValidNeighbor(boolean gemini, String field, String value) {
		JsonObject malformed = candidate();
		malformed.add(field, JsonParser.parseString(value));
		JsonArray input = new JsonArray();
		input.add(malformed);
		input.add(candidate());
		List<CommitEvaluation> results = parseBatch(gemini, input);
		assertEquals(List.of("a", "b"), results.stream().map(CommitEvaluation::commitHash).toList());
		CommitEvaluation rejected = results.get(0);
		assertEquals(TrafficLight.NOT_APPLICABLE, rejected.trafficLight());
		assertEquals("17", rejected.sourceVersion());
		assertFalse(rejected.relevant());
		assertFalse(rejected.canImplementInCurrentDsl());
		assertNull(rejected.dslRule());
		assertTrue(rejected.irrelevantReason().startsWith("Malformed candidate response fields"));
		assertEquals(TrafficLight.GREEN, results.get(1).trafficLight());
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void nonObjectAndMissingBatchSlotsRemainIncomplete(boolean gemini) {
		JsonArray input = new JsonArray();
		input.add(com.google.gson.JsonNull.INSTANCE);
		input.add(candidate());
		assertEquals(List.of("b"), parseBatch(gemini, input).stream().map(CommitEvaluation::commitHash).toList());
		input.remove(0);
		assertEquals(List.of("a"), parseBatch(gemini, input).stream().map(CommitEvaluation::commitHash).toList());
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void confidenceBoundaryPreservesSourceVersionAndOnlyModelEvidenceFields(boolean gemini) {
		JsonObject input = candidate();
		input.getAsJsonObject("sourceEvidence").addProperty("diffHunk", "untrusted model hunk");
		CommitEvaluation result = parse(gemini, input);
		assertEquals(TrafficLight.GREEN, result.trafficLight());
		assertEquals("17", result.sourceVersion());
		SourceEvidence evidence = result.sourceEvidence();
		assertNotNull(evidence);
		assertEquals("T.java", evidence.path());
		assertEquals("items.size() == 0", evidence.before());
		assertEquals("items.isEmpty()", evidence.after());
		assertNull(evidence.diffHunk(), "Only the local diff checker may attest a hunk");
		input.addProperty("confidence", 1.0);
		assertEquals(TrafficLight.GREEN, parse(gemini, input).trafficLight());
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void oversizedEvidenceIsRejectedAndNonGreenAssessmentStillCompletes(boolean gemini) {
		JsonObject input = candidate();
		input.getAsJsonObject("sourceEvidence").addProperty("before", "x".repeat(513));
		assertEquals(TrafficLight.NOT_APPLICABLE, parse(gemini, input).trafficLight());
		input.addProperty("trafficLight", "YELLOW");
		input.remove("confidence");
		input.remove("noCleanup");
		assertEquals(TrafficLight.YELLOW, parse(gemini, input).trafficLight());
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void explicitDeclineIgnoresMalformedUnusedFields(boolean gemini) {
		CommitEvaluation result = parse(gemini, declinedWithMalformedUnusedFields());
		assertNotNull(result, "An explicit decline must not become an unanswered commit");
		assertEquals("commit", result.commitHash());
		assertEquals("message", result.commitMessage());
		assertEquals("repo", result.repoUrl());
		assertEquals("17", result.sourceVersion());
		assertEquals("Context-dependent change", result.irrelevantReason());
		assertEquals(TrafficLight.NOT_APPLICABLE, result.trafficLight());
		assertNull(result.dslRule());
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void batchRetainsRejectedCommitAndItsValidNeighbor(boolean gemini) {
		JsonArray input = new JsonArray();
		input.add(declinedWithMalformedUnusedFields());
		input.add(candidate());
		String body = response(gemini, input.toString());
		List<CommitEvaluation> results = gemini
				? new GeminiClient("unused").parseBatchResponse(body, List.of("a", "b"), List.of("first", "second"), "repo")
				: new OpenAiClient("unused").parseBatchResponse(body, List.of("a", "b"), List.of("first", "second"), "repo");
		assertEquals(List.of("a", "b"), results.stream().map(CommitEvaluation::commitHash).toList());
		assertEquals(TrafficLight.NOT_APPLICABLE, results.get(0).trafficLight());
		assertEquals(TrafficLight.GREEN, results.get(1).trafficLight());
	}

	private static JsonObject declinedWithMalformedUnusedFields() {
		JsonObject input = candidate();
		input.addProperty("noCleanup", Boolean.TRUE);
		input.addProperty("reason", "Context-dependent change");
		input.addProperty("reusability", "n/a");
		input.add("relevant", new JsonObject());
		input.add("dslRule", new JsonObject());
		return input;
	}

	private static JsonObject candidate() {
		return JsonParser.parseString("""
				{"noCleanup":false,"confidence":0.80,"relevant":true,"trafficLight":"GREEN",
				 "canImplementInCurrentDsl":true,"dslRule":"$x.size() == 0\\n=> $x.isEmpty()\\n;;",
				 "sourceVersion":"17","sourceEvidence":{"path":"T.java",
				 "before":"items.size() == 0","after":"items.isEmpty()"}}
				""").getAsJsonObject();
	}

	private static CommitEvaluation parse(boolean gemini, JsonObject input) {
		String body = response(gemini, input.toString());
		return gemini ? new GeminiClient("unused").parseResponse(body, "commit", "message", "repo")
				: new OpenAiClient("unused").parseResponse(body, "commit", "message", "repo");
	}

	private static List<CommitEvaluation> parseBatch(boolean gemini, JsonArray input) {
		String body = response(gemini, input.toString());
		return gemini
				? new GeminiClient("unused").parseBatchResponse(body, List.of("a", "b"), List.of("first", "second"), "repo")
				: new OpenAiClient("unused").parseBatchResponse(body, List.of("a", "b"), List.of("first", "second"), "repo");
	}

	private static String response(boolean gemini, String text) {
		String escaped = new Gson().toJson(text);
		return gemini ? "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + escaped + "}]}}]}"
				: "{\"choices\":[{\"message\":{\"content\":" + escaped + "}}]}";
	}
}
