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
package org.sandbox.jdt.triggerpattern.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation.TrafficLight;

/**
 * Tests for {@link MistralClient}.
 */
class MistralClientTest {

@Test
void testDefaultModel() {
MistralClient client = new MistralClient("test-key");
assertEquals("mistral-large-latest", client.getModel());
}

@Test
void testBuildRequestBody() {
MistralClient client = new MistralClient("test-key");
String body = client.buildRequestBody("Hello!");

assertNotNull(body);
assertTrue(body.contains("Hello!"));
assertTrue(body.contains("messages"));
assertTrue(body.contains("model"));
}

@Test
void testParseValidResponse() {
MistralClient client = new MistralClient("test-key");

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
}

@Test
void testEvaluateWithoutApiKey() throws Exception {
MistralClient client = new MistralClient(null);
CommitEvaluation result = client.evaluate("prompt", "hash", "msg", "url");
assertNull(result);
}

@Test
void testEvaluateBatchWithoutApiKey() throws Exception {
MistralClient client = new MistralClient(null);
List<CommitEvaluation> result = client.evaluateBatch("prompt",
List.of("hash1"), List.of("msg1"), "url");
assertTrue(result.isEmpty());
}

@Test
void testWasLastResponseTruncatedInitiallyFalse() {
MistralClient client = new MistralClient("test-key");
assertFalse(client.wasLastResponseTruncated());
}

@Test
void testFinishReasonLengthSetsLastResponseTruncated() {
MistralClient client = new MistralClient("test-key");
String response = """
{
  "choices": [{
    "finish_reason": "length",
    "message": {
      "role": "assistant",
      "content": "```json\\n{\\n  \\"relevant\\": true,\\n  \\"trafficLight\\": \\"GREEN\\",\\n  \\"category\\": \\"Collections\\",\\n  \\"summary\\": \\"test\\",\\n  \\"reusability\\": 4,\\n  \\"codeImprovement\\": 3,\\n  \\"implementationEffort\\": 2,\\n  \\"canImplementInCurrentDsl\\": true\\n}\\n```"
    }
  }]
}
""";
client.parseResponse(response, "abc123", "msg", "url");
assertTrue(client.wasLastResponseTruncated());
}

@Test
void testFinishReasonContentFilterReturnsNull() {
MistralClient client = new MistralClient("test-key");
String response = """
{
  "choices": [{
    "finish_reason": "content_filter",
    "message": {
      "role": "assistant",
      "content": "```json\\n{\\n  \\"relevant\\": true\\n}\\n```"
    }
  }]
}
""";
CommitEvaluation eval = client.parseResponse(response, "abc123", "msg", "url");
assertNull(eval);
}

@Test
void testHasRemainingQuotaAlwaysTrue() {
MistralClient client = new MistralClient("test-key");
assertTrue(client.hasRemainingQuota());
}

@Test
void testIsApiUnavailableInitiallyFalse() {
MistralClient client = new MistralClient("test-key");
assertFalse(client.isApiUnavailable());
}
}
