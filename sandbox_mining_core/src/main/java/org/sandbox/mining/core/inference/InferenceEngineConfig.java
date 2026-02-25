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
package org.sandbox.mining.core.inference;

import org.sandbox.mining.core.llm.LlmProvider;

/**
 * Configuration for the {@link RuleInferenceEngine}.
 *
 * <p>Allows selecting which AI engine to use, setting confidence thresholds,
 * and controlling DSL sequence generation behavior. The different AI providers
 * (Gemini, OpenAI, DeepSeek, Qwen, Llama, Mistral) can be configured just as
 * in the refactoring mining pipeline.</p>
 *
 * <p>Use the builder-style setters for fluent configuration:</p>
 * <pre>
 *   InferenceEngineConfig config = new InferenceEngineConfig()
 *       .llmProvider(LlmProvider.OPENAI)
 *       .minConfidence(0.8)
 *       .enableAiAnalysis(true);
 * </pre>
 */
public class InferenceEngineConfig {

	private LlmProvider llmProvider = LlmProvider.GEMINI;
	private double minConfidence = 0.7;
	private boolean aiAnalysisEnabled = false;
	private boolean validateDsl = true;
	private int maxRulesPerPair = 5;
	private String targetHintFile;

	/**
	 * Creates a default configuration.
	 */
	public InferenceEngineConfig() {
		// defaults
	}

	/**
	 * Sets the LLM provider to use for AI-assisted rule inference.
	 *
	 * @param provider the provider (GEMINI, OPENAI, DEEPSEEK, QWEN, LLAMA, MISTRAL)
	 * @return this config for chaining
	 */
	public InferenceEngineConfig llmProvider(LlmProvider provider) {
		this.llmProvider = provider;
		return this;
	}

	/**
	 * Sets the minimum confidence threshold for inferred rules.
	 * Rules below this threshold are discarded.
	 *
	 * @param threshold value between 0.0 and 1.0
	 * @return this config for chaining
	 */
	public InferenceEngineConfig minConfidence(double threshold) {
		if (threshold < 0.0 || threshold > 1.0) {
			throw new IllegalArgumentException("Confidence threshold must be between 0.0 and 1.0"); //$NON-NLS-1$
		}
		this.minConfidence = threshold;
		return this;
	}

	/**
	 * Enables or disables AI-based analysis via the configured LLM provider.
	 * When disabled, only text-based heuristic analysis is performed.
	 *
	 * @param enabled true to enable AI analysis
	 * @return this config for chaining
	 */
	public InferenceEngineConfig enableAiAnalysis(boolean enabled) {
		this.aiAnalysisEnabled = enabled;
		return this;
	}

	/**
	 * Enables or disables DSL validation of generated rules.
	 *
	 * @param validate true to validate generated DSL rules
	 * @return this config for chaining
	 */
	public InferenceEngineConfig validateDsl(boolean validate) {
		this.validateDsl = validate;
		return this;
	}

	/**
	 * Sets the maximum number of rules to infer per before/after pair.
	 *
	 * @param max the maximum number of rules
	 * @return this config for chaining
	 */
	public InferenceEngineConfig maxRulesPerPair(int max) {
		if (max < 1) {
			throw new IllegalArgumentException("maxRulesPerPair must be >= 1"); //$NON-NLS-1$
		}
		this.maxRulesPerPair = max;
		return this;
	}

	/**
	 * Sets the target hint file path for generated rules.
	 *
	 * @param path the hint file path
	 * @return this config for chaining
	 */
	public InferenceEngineConfig targetHintFile(String path) {
		this.targetHintFile = path;
		return this;
	}

	public LlmProvider getLlmProvider() {
		return llmProvider;
	}

	public double getMinConfidence() {
		return minConfidence;
	}

	public boolean isAiAnalysisEnabled() {
		return aiAnalysisEnabled;
	}

	public boolean isValidateDsl() {
		return validateDsl;
	}

	public int getMaxRulesPerPair() {
		return maxRulesPerPair;
	}

	public String getTargetHintFile() {
		return targetHintFile;
	}
}
