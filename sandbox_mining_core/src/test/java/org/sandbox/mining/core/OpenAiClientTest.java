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

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.mining.core.llm.CommitEvaluation;
import org.sandbox.mining.core.llm.CommitEvaluation.TrafficLight;
import org.sandbox.mining.core.llm.OpenAiClient;

/**
 * Tests for {@link OpenAiClient}.
 */
class OpenAiClientTest {

@Test
void testBuildRequestBody() {
OpenAiClient client = new OpenAiClient("test-key");
String body = client.buildRequestBody("Hello!");

assertNotNull(body);
assertTrue(body.contains("Hello!"));
assertTrue(body.contains("messages"));
assertTrue(body.contains("user"));
assertTrue(body.contains("content"));
assertTrue(body.contains("model"));
}

@Test
void testParseValidResponse() {
OpenAiClient client = new OpenAiClient("test-key");

String response = """
{
  "choices": [{
    "message": {
      "role": "assistant",
      "content": "```json\\n{\\n  \\"relevant\\": true,\\n  \\"trafficLight\\": \\"GREEN\\",\\n  \\"category\\": \\"Collections\\",\\n  \\"summary\\": \\"Replace ArrayList\\",\\n  \\"reusability\\": 4,\\n  \\"codeImprovement\\": 3,\\n  \\"implementationEffort\\": 2,\\n  \\"canImplementInCurrentDsl\\": true\\n}\\n```"
    }
  }]
}
""";

CommitEvaluation eval = client.parseResponse(response, "abc123",
"Replace ArrayList", "https://github.com/test/repo");

assertNotNull(eval);
assertEquals("abc123", eval.commitHash());
assertTrue(eval.relevant());
assertEquals(TrafficLight.GREEN, eval.trafficLight());
assertEquals("Collections", eval.category());
assertEquals(4, eval.reusability());
}

@Test
void testParseEmptyChoices() {
OpenAiClient client = new OpenAiClient("test-key");
String response = "{\"choices\": []}";
CommitEvaluation eval = client.parseResponse(response, "abc123", "msg", "url");
assertNull(eval);
}

@Test
void testParseInvalidJson() {
OpenAiClient client = new OpenAiClient("test-key");
CommitEvaluation eval = client.parseResponse("not json", "abc123", "msg", "url");
assertNull(eval);
}

@Test
void testEvaluateWithoutApiKey() throws Exception {
OpenAiClient client = new OpenAiClient(null);
CommitEvaluation result = client.evaluate("prompt", "hash", "msg", "url");
assertNull(result);
}

@Test
void testEvaluateBatchWithoutApiKey() throws Exception {
OpenAiClient client = new OpenAiClient(null);
List<CommitEvaluation> result = client.evaluateBatch("prompt",
List.of("hash1"), List.of("msg1"), "url");
assertTrue(result.isEmpty());
}

@Test
void testHasRemainingQuotaAlwaysTrue() {
OpenAiClient client = new OpenAiClient("test-key");
assertTrue(client.hasRemainingQuota());
}

@Test
void testIsApiUnavailableInitiallyFalse() {
OpenAiClient client = new OpenAiClient("test-key");
assertFalse(client.isApiUnavailable());
}

@Test
void testSetAndGetMaxFailureDuration() {
OpenAiClient client = new OpenAiClient("test-key");
Duration custom = Duration.ofSeconds(120);
client.setMaxFailureDuration(custom);
assertEquals(custom, client.getMaxFailureDuration());
}

@Test
void testDefaultMaxFailureDuration() {
OpenAiClient client = new OpenAiClient("test-key");
assertEquals(Duration.ofSeconds(OpenAiClient.DEFAULT_MAX_FAILURE_DURATION_SECONDS),
client.getMaxFailureDuration());
}

@Test
void testIsApiUnavailableAfterTimeoutElapsed() throws InterruptedException {
OpenAiClient client = new OpenAiClient("test-key");
client.setMaxFailureDuration(Duration.ofMillis(1));
Thread.sleep(100);
assertTrue(client.isApiUnavailable());
}

@Test
void testParseBatchResponseValid() {
OpenAiClient client = new OpenAiClient("test-key");
String response = """
{
  "choices": [{
    "message": {
      "content": "```json\\n[{\\n  \\"relevant\\": true,\\n  \\"trafficLight\\": \\"GREEN\\",\\n  \\"summary\\": \\"First\\",\\n  \\"reusability\\": 4,\\n  \\"codeImprovement\\": 3,\\n  \\"implementationEffort\\": 2,\\n  \\"canImplementInCurrentDsl\\": true\\n}]\\n```"
    }
  }]
}
""";
List<CommitEvaluation> evals = client.parseBatchResponse(response,
List.of("hash1"), List.of("msg1"), "url");
assertEquals(1, evals.size());
assertEquals("hash1", evals.get(0).commitHash());
assertTrue(evals.get(0).relevant());
}

@Test
void testParseBatchResponseEmpty() {
OpenAiClient client = new OpenAiClient("test-key");
List<CommitEvaluation> evals = client.parseBatchResponse("{\"choices\": []}",
List.of("hash1"), List.of("msg1"), "url");
assertTrue(evals.isEmpty());
}

@Test
void testGetDailyRequestCountInitiallyZero() {
OpenAiClient client = new OpenAiClient("test-key");
assertEquals(0, client.getDailyRequestCount());
}

@Test
void testDefaultModelUsed() {
OpenAiClient client = new OpenAiClient("test-key");
assertEquals("gpt-4o-mini", client.getModel());
}
}
