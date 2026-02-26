/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.mining.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation.TrafficLight;
import org.sandbox.jdt.triggerpattern.llm.GeminiClient;

/**
 * Tests for {@link GeminiClient}.
 */
class GeminiClientTest {

	@Test
	void testBuildRequestBody() {
		GeminiClient client = new GeminiClient("test-key");
		String body = client.buildRequestBody("Hello, Gemini!");

		assertNotNull(body);
		assertTrue(body.contains("Hello, Gemini!"));
		assertTrue(body.contains("contents"));
		assertTrue(body.contains("parts"));
		assertTrue(body.contains("text"));
	}

	@Test
	void testParseValidResponse() {
		GeminiClient client = new GeminiClient("test-key");

		String response = """
				{
				  "candidates": [{
				    "content": {
				      "parts": [{
				        "text": "```json\\n{\\n  \\"relevant\\": true,\\n  \\"trafficLight\\": \\"GREEN\\",\\n  \\"category\\": \\"Collections\\",\\n  \\"summary\\": \\"Replace ArrayList with List.of\\",\\n  \\"reusability\\": 4,\\n  \\"codeImprovement\\": 3,\\n  \\"implementationEffort\\": 2,\\n  \\"canImplementInCurrentDsl\\": true,\\n  \\"dslRule\\": \\"new java.util.ArrayList<>() => java.util.List.of()\\",\\n  \\"targetHintFile\\": \\"use-list-of.sandbox-hint\\"\\n}\\n```"
				      }]
				    }
				  }]
				}
				""";

		CommitEvaluation eval = client.parseResponse(response, "abc123",
				"Replace ArrayList", "https://github.com/test/repo");

		assertNotNull(eval);
		assertEquals("abc123", eval.commitHash());
		assertEquals(true, eval.relevant());
		assertEquals(TrafficLight.GREEN, eval.trafficLight());
		assertEquals("Collections", eval.category());
		assertEquals(4, eval.reusability());
	}

	@Test
	void testParseEmptyCandidates() {
		GeminiClient client = new GeminiClient("test-key");

		String response = """
				{
				  "candidates": []
				}
				""";

		CommitEvaluation eval = client.parseResponse(response, "abc123", "msg", "url");
		assertNull(eval);
	}

	@Test
	void testParseInvalidJson() {
		GeminiClient client = new GeminiClient("test-key");

		CommitEvaluation eval = client.parseResponse("not json", "abc123", "msg", "url");
		assertNull(eval);
	}

	@Test
	void testExtractJsonFromCodeBlock() {
		String text = "Here is the result:\n```json\n{\"key\": \"value\"}\n```\nDone.";
		String json = GeminiClient.extractJson(text);
		assertEquals("{\"key\": \"value\"}", json);
	}

	@Test
	void testExtractJsonFromPlainCodeBlock() {
		String text = "Result:\n```\n{\"key\": \"value\"}\n```";
		String json = GeminiClient.extractJson(text);
		assertEquals("{\"key\": \"value\"}", json);
	}

	@Test
	void testExtractJsonPlainText() {
		String text = "{\"key\": \"value\"}";
		String json = GeminiClient.extractJson(text);
		assertEquals("{\"key\": \"value\"}", json);
	}

	@Test
	void testEvaluateWithoutApiKey() throws Exception {
		GeminiClient client = new GeminiClient(null);
		CommitEvaluation result = client.evaluate("prompt", "hash", "msg", "url");
		assertNull(result);
	}

	@Test
	void testExplicitModelIsUsed() {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(30))
				.build();
		GeminiClient client = new GeminiClient("test-key", httpClient, "gemini-1.5-pro");
		assertEquals("gemini-1.5-pro", client.getModel());
	}

	@Test
	void testDefaultModelFallback() {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(30))
				.build();
		GeminiClient client = new GeminiClient("test-key", httpClient, "gemini-2.5-flash");
		assertEquals("gemini-2.5-flash", client.getModel());
	}

	@Test
	void testParseBatchResponseValidArray() {
		GeminiClient client = new GeminiClient("test-key");
		String response = """
				{
				  "candidates": [{
				    "content": {
				      "parts": [{
				        "text": "```json\\n[{\\n  \\"relevant\\": true,\\n  \\"trafficLight\\": \\"GREEN\\",\\n  \\"category\\": \\"Collections\\",\\n  \\"summary\\": \\"First commit\\",\\n  \\"reusability\\": 4,\\n  \\"codeImprovement\\": 3,\\n  \\"implementationEffort\\": 2,\\n  \\"canImplementInCurrentDsl\\": true\\n},\\n{\\n  \\"relevant\\": false,\\n  \\"trafficLight\\": \\"NOT_APPLICABLE\\",\\n  \\"summary\\": \\"Second commit\\",\\n  \\"reusability\\": 1,\\n  \\"codeImprovement\\": 1,\\n  \\"implementationEffort\\": 1,\\n  \\"canImplementInCurrentDsl\\": false\\n}]\\n```"
				      }]
				    }
				  }]
				}
				""";
		List<String> hashes = List.of("hash1", "hash2");
		List<String> messages = List.of("msg1", "msg2");

		List<CommitEvaluation> evals = client.parseBatchResponse(response, hashes, messages,
				"https://github.com/test/repo");

		assertEquals(2, evals.size());
		assertEquals("hash1", evals.get(0).commitHash());
		assertTrue(evals.get(0).relevant());
		assertEquals(TrafficLight.GREEN, evals.get(0).trafficLight());
		assertEquals("hash2", evals.get(1).commitHash());
		assertNotNull(evals.get(1));
	}

	@Test
	void testParseBatchResponseFewerElementsThanCommits() {
		GeminiClient client = new GeminiClient("test-key");
		String response = """
				{
				  "candidates": [{
				    "content": {
				      "parts": [{
				        "text": "```json\\n[{\\n  \\"relevant\\": true,\\n  \\"trafficLight\\": \\"GREEN\\",\\n  \\"summary\\": \\"Only one\\",\\n  \\"reusability\\": 5,\\n  \\"codeImprovement\\": 5,\\n  \\"implementationEffort\\": 5,\\n  \\"canImplementInCurrentDsl\\": true\\n}]\\n```"
				      }]
				    }
				  }]
				}
				""";
		List<String> hashes = List.of("hash1", "hash2", "hash3");
		List<String> messages = List.of("msg1", "msg2", "msg3");

		List<CommitEvaluation> evals = client.parseBatchResponse(response, hashes, messages,
				"https://github.com/test/repo");

		// Should return only what the response contained (1), not 3
		assertEquals(1, evals.size());
		assertEquals("hash1", evals.get(0).commitHash());
	}

	@Test
	void testParseBatchResponseMoreElementsThanCommits() {
		GeminiClient client = new GeminiClient("test-key");
		// Response with 3 elements, but only 2 commits expected
		String response = """
				{
				  "candidates": [{
				    "content": {
				      "parts": [{
				        "text": "```json\\n[{\\n  \\"relevant\\": true,\\n  \\"summary\\": \\"A\\",\\n  \\"reusability\\": 1,\\n  \\"codeImprovement\\": 1,\\n  \\"implementationEffort\\": 1,\\n  \\"canImplementInCurrentDsl\\": false,\\n  \\"trafficLight\\": \\"RED\\"\\n},{\\n  \\"relevant\\": false,\\n  \\"summary\\": \\"B\\",\\n  \\"reusability\\": 1,\\n  \\"codeImprovement\\": 1,\\n  \\"implementationEffort\\": 1,\\n  \\"canImplementInCurrentDsl\\": false,\\n  \\"trafficLight\\": \\"RED\\"\\n},{\\n  \\"relevant\\": false,\\n  \\"summary\\": \\"C\\",\\n  \\"reusability\\": 1,\\n  \\"codeImprovement\\": 1,\\n  \\"implementationEffort\\": 1,\\n  \\"canImplementInCurrentDsl\\": false,\\n  \\"trafficLight\\": \\"RED\\"\\n}]\\n```"
				      }]
				    }
				  }]
				}
				""";
		List<String> hashes = List.of("hash1", "hash2");
		List<String> messages = List.of("msg1", "msg2");

		List<CommitEvaluation> evals = client.parseBatchResponse(response, hashes, messages,
				"https://github.com/test/repo");

		// Should be capped at 2 (the number of commits)
		assertEquals(2, evals.size());
	}

	@Test
	void testParseBatchResponseInvalidJson() {
		GeminiClient client = new GeminiClient("test-key");
		List<String> hashes = List.of("hash1");
		List<String> messages = List.of("msg1");

		List<CommitEvaluation> evals = client.parseBatchResponse("not json", hashes, messages, "url");

		assertTrue(evals.isEmpty());
	}

	@Test
	void testParseBatchResponseEmptyCandidates() {
		GeminiClient client = new GeminiClient("test-key");
		String response = "{\"candidates\": []}";
		List<String> hashes = List.of("hash1");
		List<String> messages = List.of("msg1");

		List<CommitEvaluation> evals = client.parseBatchResponse(response, hashes, messages, "url");

		assertTrue(evals.isEmpty());
	}

	@Test
	void testEvaluateBatchWithoutApiKey() throws Exception {
		GeminiClient client = new GeminiClient(null);
		List<CommitEvaluation> result = client.evaluateBatch("prompt",
				List.of("hash1"), List.of("msg1"), "url");
		assertTrue(result.isEmpty());
	}

	@Test
	void testHasRemainingQuotaInitially() {
		GeminiClient client = new GeminiClient("test-key");
		assertTrue(client.hasRemainingQuota());
		assertEquals(0, client.getDailyRequestCount());
	}

	@Test
	void testIsApiUnavailableInitiallyFalse() {
		GeminiClient client = new GeminiClient("test-key");
		assertFalse(client.isApiUnavailable());
	}

	@Test
	void testSetAndGetMaxFailureDuration() {
		GeminiClient client = new GeminiClient("test-key");
		Duration custom = Duration.ofSeconds(120);
		client.setMaxFailureDuration(custom);
		assertEquals(custom, client.getMaxFailureDuration());
	}

	@Test
	void testDefaultMaxFailureDuration() {
		GeminiClient client = new GeminiClient("test-key");
		assertEquals(Duration.ofSeconds(GeminiClient.DEFAULT_MAX_FAILURE_DURATION_SECONDS),
				client.getMaxFailureDuration());
	}

	@Test
	void testIsApiUnavailableAfterTimeoutElapsed() throws InterruptedException {
		GeminiClient client = new GeminiClient("test-key");
		client.setMaxFailureDuration(Duration.ofMillis(1));
		Thread.sleep(100);
		assertTrue(client.isApiUnavailable());
	}

	// ---- repairTruncatedJson tests ----

	@Test
	void testRepairTruncatedJsonValidJson() {
		String valid = "{\"key\": \"value\"}";
		assertEquals(valid, GeminiClient.repairTruncatedJson(valid));
	}

	@Test
	void testRepairTruncatedJsonUnclosedBrace() {
		String truncated = "{\"key\": \"value\"";
		String repaired = GeminiClient.repairTruncatedJson(truncated);
		assertTrue(repaired.endsWith("}"));
		assertTrue(repaired.contains("\"key\""));
	}

	@Test
	void testRepairTruncatedJsonUnclosedBracket() {
		String truncated = "[{\"key\": \"value\"}";
		String repaired = GeminiClient.repairTruncatedJson(truncated);
		assertTrue(repaired.endsWith("]"));
	}

	@Test
	void testRepairTruncatedJsonUnclosedString() {
		String truncated = "{\"key\": \"val";
		String repaired = GeminiClient.repairTruncatedJson(truncated);
		assertTrue(repaired.endsWith("}"));
		assertTrue(repaired.contains("\"val\""));
	}

	@Test
	void testRepairTruncatedJsonTrailingComma() {
		String truncated = "[{\"key\": \"value\"},";
		String repaired = GeminiClient.repairTruncatedJson(truncated);
		assertTrue(repaired.endsWith("]"));
		assertNotNull(repaired);
	}

	@Test
	void testRepairTruncatedJsonNullAndEmpty() {
		assertNull(GeminiClient.repairTruncatedJson(null));
		assertEquals("", GeminiClient.repairTruncatedJson(""));
	}

	@Test
	void testExtractJsonFromTruncatedCodeBlock() {
		// Truncated markdown code block (no closing ```)
		String text = "```json\n{\"key\": \"value\"";
		String json = GeminiClient.extractJson(text);
		assertTrue(json.contains("\"key\""));
		assertTrue(json.endsWith("}"));
	}

	@Test
	void testExtractJsonPlainTruncated() {
		// Plain truncated JSON (no code fences)
		String text = "{\"key\": \"value\"";
		String json = GeminiClient.extractJson(text);
		assertTrue(json.endsWith("}"));
	}

	@Test
	void testFinishReasonMaxTokensSetsLastResponseTruncated() {
		GeminiClient client = new GeminiClient("test-key");
		String response = """
				{
				  "candidates": [{
				    "finishReason": "MAX_TOKENS",
				    "content": {
				      "parts": [{
				        "text": "```json\\n{\\n  \\"relevant\\": true,\\n  \\"trafficLight\\": \\"GREEN\\",\\n  \\"category\\": \\"Collections\\",\\n  \\"summary\\": \\"Replace ArrayList\\",\\n  \\"reusability\\": 4,\\n  \\"codeImprovement\\": 3,\\n  \\"implementationEffort\\": 2,\\n  \\"canImplementInCurrentDsl\\": true\\n}\\n```"
				      }]
				    }
				  }]
				}
				""";
		client.parseResponse(response, "abc123", "msg", "url");
		assertTrue(client.wasLastResponseTruncated());
	}

	@Test
	void testFinishReasonSafetyReturnsNull() {
		GeminiClient client = new GeminiClient("test-key");
		String response = """
				{
				  "candidates": [{
				    "finishReason": "SAFETY",
				    "content": {
				      "parts": [{
				        "text": "```json\\n{\\n  \\"relevant\\": true\\n}\\n```"
				      }]
				    }
				  }]
				}
				""";
		CommitEvaluation eval = client.parseResponse(response, "abc123", "msg", "url");
		assertNull(eval);
	}

	@Test
	void testFinishReasonStopNotTruncated() {
		GeminiClient client = new GeminiClient("test-key");
		String response = """
				{
				  "candidates": [{
				    "finishReason": "STOP",
				    "content": {
				      "parts": [{
				        "text": "```json\\n{\\n  \\"relevant\\": true,\\n  \\"trafficLight\\": \\"GREEN\\",\\n  \\"category\\": \\"Collections\\",\\n  \\"summary\\": \\"Replace ArrayList\\",\\n  \\"reusability\\": 4,\\n  \\"codeImprovement\\": 3,\\n  \\"implementationEffort\\": 2,\\n  \\"canImplementInCurrentDsl\\": true\\n}\\n```"
				      }]
				    }
				  }]
				}
				""";
		client.parseResponse(response, "abc123", "msg", "url");
		assertFalse(client.wasLastResponseTruncated());
	}

	@Test
	void testNullSafeCategoryDefault() {
		GeminiClient client = new GeminiClient("test-key");
		String response = """
				{
				  "candidates": [{
				    "content": {
				      "parts": [{
				        "text": "```json\\n{\\n  \\"relevant\\": true,\\n  \\"trafficLight\\": \\"GREEN\\",\\n  \\"summary\\": \\"Replace ArrayList\\",\\n  \\"reusability\\": 4,\\n  \\"codeImprovement\\": 3,\\n  \\"implementationEffort\\": 2,\\n  \\"canImplementInCurrentDsl\\": true\\n}\\n```"
				      }]
				    }
				  }]
				}
				""";
		CommitEvaluation eval = client.parseResponse(response, "abc123", "msg", "url");
		assertNotNull(eval);
		assertEquals("Uncategorized", eval.category());
	}

	@Test
	void testWasLastResponseTruncatedInitiallyFalse() {
		GeminiClient client = new GeminiClient("test-key");
		assertFalse(client.wasLastResponseTruncated());
	}

	@Test
	void testParseResponseSanitizesTriggerTags() {
		GeminiClient client = new GeminiClient("test-key");
		String response = """
				{
				  "candidates": [{
				    "content": {
				      "parts": [{
				        "text": "```json\\n{\\n  \\"relevant\\": true,\\n  \\"trafficLight\\": \\"GREEN\\",\\n  \\"summary\\": \\"Replace size check\\",\\n  \\"category\\": \\"Collections\\",\\n  \\"reusability\\": 9,\\n  \\"codeImprovement\\": 7,\\n  \\"implementationEffort\\": 2,\\n  \\"canImplementInCurrentDsl\\": true,\\n  \\"dslRule\\": \\"<trigger>\\\\n$x.size() == 0\\\\n=> $x.isEmpty()\\\\n;;\\\\n</trigger>\\"\\n}\\n```"
				      }]
				    }
				  }]
				}
				""";
		CommitEvaluation eval = client.parseResponse(response, "abc123", "msg", "url");
		assertNotNull(eval);
		assertNotNull(eval.dslRule());
		assertFalse(eval.dslRule().contains("<trigger>"));
		assertFalse(eval.dslRule().contains("</trigger>"));
		assertTrue(eval.dslRule().contains("$x.size() == 0"));
	}

	@Test
	void testParseResponseSanitizesImportTags() {
		GeminiClient client = new GeminiClient("test-key");
		String response = """
				{
				  "candidates": [{
				    "content": {
				      "parts": [{
				        "text": "```json\\n{\\n  \\"relevant\\": true,\\n  \\"trafficLight\\": \\"GREEN\\",\\n  \\"summary\\": \\"Use Map.entry\\",\\n  \\"category\\": \\"Java-Modernisierung\\",\\n  \\"reusability\\": 7,\\n  \\"codeImprovement\\": 5,\\n  \\"implementationEffort\\": 2,\\n  \\"canImplementInCurrentDsl\\": true,\\n  \\"dslRule\\": \\"new java.util.AbstractMap.SimpleEntry<>($key, $value)\\\\n=> java.util.Map.entry($key, $value)\\\\n<import>java.util.Map</import>\\\\n;;\\"\\n}\\n```"
				      }]
				    }
				  }]
				}
				""";
		CommitEvaluation eval = client.parseResponse(response, "abc123", "msg", "url");
		assertNotNull(eval);
		assertNotNull(eval.dslRule());
		assertFalse(eval.dslRule().contains("<import>"));
		assertFalse(eval.dslRule().contains("</import>"));
		assertTrue(eval.dslRule().contains("java.util.Map.entry"));
	}

	@Test
	void testParseResponsePreservesCleanDslRule() {
		GeminiClient client = new GeminiClient("test-key");
		String response = """
				{
				  "candidates": [{
				    "content": {
				      "parts": [{
				        "text": "```json\\n{\\n  \\"relevant\\": true,\\n  \\"trafficLight\\": \\"GREEN\\",\\n  \\"summary\\": \\"Clean rule\\",\\n  \\"category\\": \\"Collections\\",\\n  \\"reusability\\": 9,\\n  \\"codeImprovement\\": 7,\\n  \\"implementationEffort\\": 2,\\n  \\"canImplementInCurrentDsl\\": true,\\n  \\"dslRule\\": \\"$x.size() == 0\\\\n=> $x.isEmpty()\\\\n;;\\"\\n}\\n```"
				      }]
				    }
				  }]
				}
				""";
		CommitEvaluation eval = client.parseResponse(response, "abc123", "msg", "url");
		assertNotNull(eval);
		assertNotNull(eval.dslRule());
		assertTrue(eval.dslRule().contains("$x.size() == 0"));
		assertTrue(eval.dslRule().contains("$x.isEmpty()"));
	}
}
