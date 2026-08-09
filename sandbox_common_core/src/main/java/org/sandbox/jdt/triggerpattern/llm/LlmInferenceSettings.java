/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.llm;

/**
 * Provider-independent generation settings for rule inference.
 *
 * @param modelName optional provider model override
 * @param maxTokens optional maximum output-token count
 * @param temperature optional sampling temperature
 */
public record LlmInferenceSettings(String modelName, Integer maxTokens, Double temperature) {

	public LlmInferenceSettings {
		modelName = modelName == null || modelName.isBlank() ? null : modelName.trim();
		if (maxTokens != null && maxTokens.intValue() <= 0) {
			throw new IllegalArgumentException("maxTokens must be positive"); //$NON-NLS-1$
		}
		if (temperature != null && (temperature.doubleValue() < 0.0 || temperature.doubleValue() > 2.0)) {
			throw new IllegalArgumentException("temperature must be between 0.0 and 2.0"); //$NON-NLS-1$
		}
	}

	/** Returns settings that leave provider/environment defaults unchanged. */
	public static LlmInferenceSettings unspecified() {
		return new LlmInferenceSettings(null, null, null);
	}
}
