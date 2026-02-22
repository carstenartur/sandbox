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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.sandbox.mining.core.llm.LlmProvider;

/**
 * Tests for {@link LlmProvider}.
 */
class LlmProviderTest {

@Test
void testFromStringGeminiLowercase() {
assertEquals(LlmProvider.GEMINI, LlmProvider.fromString("gemini"));
}

@Test
void testFromStringGeminiUppercase() {
assertEquals(LlmProvider.GEMINI, LlmProvider.fromString("GEMINI"));
}

@Test
void testFromStringOpenAiLowercase() {
assertEquals(LlmProvider.OPENAI, LlmProvider.fromString("openai"));
}

@Test
void testFromStringOpenAiUppercase() {
assertEquals(LlmProvider.OPENAI, LlmProvider.fromString("OPENAI"));
}

@Test
void testFromStringMixedCase() {
assertEquals(LlmProvider.GEMINI, LlmProvider.fromString("Gemini"));
assertEquals(LlmProvider.OPENAI, LlmProvider.fromString("OpenAI"));
}

@Test
void testFromStringNullThrows() {
assertThrows(IllegalArgumentException.class, () -> LlmProvider.fromString(null));
}

@Test
void testFromStringUnknownThrows() {
assertThrows(IllegalArgumentException.class, () -> LlmProvider.fromString("anthropic"));
}

@Test
void testFromStringDeepSeek() {
assertEquals(LlmProvider.DEEPSEEK, LlmProvider.fromString("deepseek"));
assertEquals(LlmProvider.DEEPSEEK, LlmProvider.fromString("DEEPSEEK"));
assertEquals(LlmProvider.DEEPSEEK, LlmProvider.fromString("DeepSeek"));
}

@Test
void testFromStringQwen() {
assertEquals(LlmProvider.QWEN, LlmProvider.fromString("qwen"));
assertEquals(LlmProvider.QWEN, LlmProvider.fromString("QWEN"));
assertEquals(LlmProvider.QWEN, LlmProvider.fromString("Qwen"));
}

@Test
void testFromStringLlama() {
assertEquals(LlmProvider.LLAMA, LlmProvider.fromString("llama"));
assertEquals(LlmProvider.LLAMA, LlmProvider.fromString("LLAMA"));
assertEquals(LlmProvider.LLAMA, LlmProvider.fromString("Llama"));
}

@Test
void testFromStringMistral() {
assertEquals(LlmProvider.MISTRAL, LlmProvider.fromString("mistral"));
assertEquals(LlmProvider.MISTRAL, LlmProvider.fromString("MISTRAL"));
assertEquals(LlmProvider.MISTRAL, LlmProvider.fromString("Mistral"));
}
}
