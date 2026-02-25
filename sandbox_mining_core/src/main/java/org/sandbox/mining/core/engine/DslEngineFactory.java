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
package org.sandbox.mining.core.engine;

import org.sandbox.mining.core.config.MiningConfig;
import org.sandbox.mining.core.llm.LlmClient;
import org.sandbox.mining.core.llm.LlmClientFactory;
import org.sandbox.mining.core.llm.LlmProvider;

/**
 * Factory for creating {@link DslSequenceEngine} instances based on configuration.
 *
 * <p>Resolution priority for the LLM provider:</p>
 * <ol>
 *   <li>Explicit CLI argument ({@code cliProvider})</li>
 *   <li>Configuration file ({@link MiningConfig#getLlmProvider()})</li>
 *   <li>Environment variable {@code LLM_PROVIDER}</li>
 *   <li>Auto-detect from available API keys</li>
 *   <li>Default: {@link LlmProvider#GEMINI}</li>
 * </ol>
 *
 * @since 1.2.6
 */
public class DslEngineFactory {

	private DslEngineFactory() {
		// utility class
	}

	/**
	 * Creates a {@link DslSequenceEngine} from a {@link MiningConfig} and optional
	 * CLI override.
	 *
	 * @param config      the mining configuration (may be {@code null})
	 * @param cliProvider explicit provider name from CLI (may be {@code null} or blank)
	 * @return a configured engine
	 */
	public static DslSequenceEngine create(MiningConfig config, String cliProvider) {
		String effectiveProvider = resolveProvider(config, cliProvider);
		LlmClient client = LlmClientFactory.createFromEnvironment(effectiveProvider);
		return new LlmDslSequenceEngine(client);
	}

	/**
	 * Creates a {@link DslSequenceEngine} for a specific {@link LlmProvider}.
	 *
	 * @param provider the LLM provider to use
	 * @return a configured engine
	 */
	public static DslSequenceEngine create(LlmProvider provider) {
		LlmClient client = LlmClientFactory.create(provider);
		return new LlmDslSequenceEngine(client);
	}

	/**
	 * Creates a {@link DslSequenceEngine} using only environment-based auto-detection.
	 *
	 * @return a configured engine
	 */
	public static DslSequenceEngine createFromEnvironment() {
		return create((MiningConfig) null, null);
	}

	/**
	 * Resolves the effective LLM provider name applying the priority order.
	 *
	 * @param config      the mining configuration (may be {@code null})
	 * @param cliProvider explicit CLI provider (may be {@code null} or blank)
	 * @return the resolved provider name, or {@code null} for environment auto-detection
	 */
	public static String resolveProvider(MiningConfig config, String cliProvider) {
		// 1. CLI argument takes highest priority
		if (cliProvider != null && !cliProvider.isBlank()) {
			return cliProvider;
		}
		// 2. Configuration file
		if (config != null) {
			String configProvider = config.getLlmProvider();
			if (configProvider != null && !configProvider.isBlank()) {
				return configProvider;
			}
		}
		// 3+4+5: Delegate to LlmClientFactory.createFromEnvironment(null)
		return null;
	}
}
