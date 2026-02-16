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
package org.sandbox.mining.gemini;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sandbox.mining.gemini.gemini.CommitEvaluation;
import org.sandbox.mining.gemini.gemini.CommitEvaluation.TrafficLight;
import org.sandbox.mining.gemini.gemini.GeminiClient;

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
}
