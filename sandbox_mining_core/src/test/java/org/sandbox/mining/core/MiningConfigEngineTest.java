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
package org.sandbox.mining.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.sandbox.mining.core.config.MiningConfig;

/**
 * Tests for LLM provider and engine type configuration in {@link MiningConfig}.
 */
class MiningConfigEngineTest {

	@Test
	void testParseLlmProvider() {
		String yaml = """
				mining:
				  llm-provider: openai
				  repositories: []
				"""; //$NON-NLS-1$
		MiningConfig config = MiningConfig.parse(
				new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
		assertEquals("openai", config.getLlmProvider()); //$NON-NLS-1$
	}

	@Test
	void testParseEngineType() {
		String yaml = """
				mining:
				  engine-type: llm
				  repositories: []
				"""; //$NON-NLS-1$
		MiningConfig config = MiningConfig.parse(
				new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
		assertEquals("llm", config.getEngineType()); //$NON-NLS-1$
	}

	@Test
	void testParseBothFields() {
		String yaml = """
				mining:
				  llm-provider: deepseek
				  engine-type: llm
				  batch-size: 100
				  repositories: []
				"""; //$NON-NLS-1$
		MiningConfig config = MiningConfig.parse(
				new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
		assertEquals("deepseek", config.getLlmProvider()); //$NON-NLS-1$
		assertEquals("llm", config.getEngineType()); //$NON-NLS-1$
		assertEquals(100, config.getBatchSize());
	}

	@Test
	void testParseInSettingsBlock() {
		String yaml = """
				mining:
				  settings:
				    llm-provider: mistral
				    engine-type: llm
				  repositories: []
				"""; //$NON-NLS-1$
		MiningConfig config = MiningConfig.parse(
				new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
		assertEquals("mistral", config.getLlmProvider()); //$NON-NLS-1$
		assertEquals("llm", config.getEngineType()); //$NON-NLS-1$
	}

	@Test
	void testDefaultsAreNull() {
		String yaml = """
				mining:
				  repositories: []
				"""; //$NON-NLS-1$
		MiningConfig config = MiningConfig.parse(
				new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
		assertNull(config.getLlmProvider());
		assertNull(config.getEngineType());
	}

	@Test
	void testSettersRoundTrip() {
		MiningConfig config = new MiningConfig();
		config.setLlmProvider("qwen"); //$NON-NLS-1$
		config.setEngineType("llm"); //$NON-NLS-1$
		assertEquals("qwen", config.getLlmProvider()); //$NON-NLS-1$
		assertEquals("llm", config.getEngineType()); //$NON-NLS-1$
	}
}
