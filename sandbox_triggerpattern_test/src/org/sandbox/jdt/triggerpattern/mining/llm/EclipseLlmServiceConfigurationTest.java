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
package org.sandbox.jdt.triggerpattern.mining.llm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/** Tests the credential-availability contract without depending on process environment state. */
public class EclipseLlmServiceConfigurationTest {

	@Test
	public void selectedProviderRequiresItsOwnCredential() {
		Function<String, String> environment = Map.of(
				"GEMINI_API_KEY", "gemini-secret")::get; //$NON-NLS-1$ //$NON-NLS-2$

		assertFalse(EclipseLlmService.hasCredentials("OPENAI", "", environment)); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(EclipseLlmService.hasCredentials("GEMINI", "", environment)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void secureCredentialMakesExplicitProviderAvailable() {
		assertTrue(EclipseLlmService.hasCredentials("OPENAI", "secure-secret", key -> null)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void environmentProviderAlsoRequiresMatchingCredential() {
		Function<String, String> mismatched = Map.of(
				"LLM_PROVIDER", "OPENAI", //$NON-NLS-1$ //$NON-NLS-2$
				"GEMINI_API_KEY", "gemini-secret")::get; //$NON-NLS-1$ //$NON-NLS-2$
		Function<String, String> matched = Map.of(
				"LLM_PROVIDER", "OPENAI", //$NON-NLS-1$ //$NON-NLS-2$
				"OPENAI_API_KEY", "openai-secret")::get; //$NON-NLS-1$ //$NON-NLS-2$

		assertFalse(EclipseLlmService.hasCredentials("", "", mismatched)); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(EclipseLlmService.hasCredentials("", "", matched)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void autoDetectionAcceptsAnySupportedProviderCredential() {
		Function<String, String> environment = Map.of(
				"DASHSCOPE_API_KEY", "qwen-secret")::get; //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(EclipseLlmService.hasCredentials("", "", environment)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void invalidProviderPreferenceFailsClosed() {
		assertFalse(EclipseLlmService.hasCredentials("UNKNOWN", "secure-secret", key -> "other-secret")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
