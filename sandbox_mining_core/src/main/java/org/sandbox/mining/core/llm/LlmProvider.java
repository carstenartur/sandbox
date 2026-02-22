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
package org.sandbox.mining.core.llm;

/**
 * Supported LLM providers.
 */
public enum LlmProvider {
GEMINI,
OPENAI,
DEEPSEEK,
QWEN,
LLAMA,
MISTRAL;

/**
 * Case-insensitive lookup of provider by name.
 *
 * @param name the provider name string
 * @return the matching LlmProvider
 * @throws IllegalArgumentException if no matching provider is found
 */
public static LlmProvider fromString(String name) {
if (name == null) {
throw new IllegalArgumentException("Provider name must not be null"); //$NON-NLS-1$
}
for (LlmProvider p : values()) {
if (p.name().equalsIgnoreCase(name.trim())) {
return p;
}
}
throw new IllegalArgumentException("Unknown LLM provider: " + name); //$NON-NLS-1$
}
}
