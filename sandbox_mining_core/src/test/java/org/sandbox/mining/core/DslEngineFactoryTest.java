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

import org.junit.jupiter.api.Test;
import org.sandbox.mining.core.config.MiningConfig;
import org.sandbox.mining.core.engine.DslEngineFactory;

/**
 * Tests for {@link DslEngineFactory}.
 */
class DslEngineFactoryTest {

	@Test
	void testResolveProviderCliTakesPriority() {
		MiningConfig config = new MiningConfig();
		config.setLlmProvider("gemini"); //$NON-NLS-1$
		String result = DslEngineFactory.resolveProvider(config, "openai"); //$NON-NLS-1$
		assertEquals("openai", result); //$NON-NLS-1$
	}

	@Test
	void testResolveProviderConfigUsedWhenNoCli() {
		MiningConfig config = new MiningConfig();
		config.setLlmProvider("deepseek"); //$NON-NLS-1$
		String result = DslEngineFactory.resolveProvider(config, null);
		assertEquals("deepseek", result); //$NON-NLS-1$
	}

	@Test
	void testResolveProviderConfigUsedWhenBlankCli() {
		MiningConfig config = new MiningConfig();
		config.setLlmProvider("mistral"); //$NON-NLS-1$
		String result = DslEngineFactory.resolveProvider(config, "  "); //$NON-NLS-1$
		assertEquals("mistral", result); //$NON-NLS-1$
	}

	@Test
	void testResolveProviderFallsBackToNull() {
		MiningConfig config = new MiningConfig();
		String result = DslEngineFactory.resolveProvider(config, null);
		assertNull(result);
	}

	@Test
	void testResolveProviderNullConfig() {
		String result = DslEngineFactory.resolveProvider(null, null);
		assertNull(result);
	}

	@Test
	void testResolveProviderNullConfigWithCli() {
		String result = DslEngineFactory.resolveProvider(null, "qwen"); //$NON-NLS-1$
		assertEquals("qwen", result); //$NON-NLS-1$
	}
}
