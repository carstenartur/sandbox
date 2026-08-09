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
package org.sandbox.jdt.internal.ui.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Tests provider ownership without reading or modifying a real Eclipse keyring. */
public class LlmSecureCredentialsProviderTest {

	@Test
	public void normalizesSupportedProvidersAndLegacyDefault() {
		assertEquals("GEMINI", LlmSecureCredentials.canonicalProvider(null)); //$NON-NLS-1$
		assertEquals("GEMINI", LlmSecureCredentials.canonicalProvider("")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("OPENAI", LlmSecureCredentials.canonicalProvider("openai")); //$NON-NLS-1$ //$NON-NLS-2$
		assertNull(LlmSecureCredentials.canonicalProvider("unsupported")); //$NON-NLS-1$
	}

	@Test
	public void returnsSecureKeyOnlyToOwningProvider() {
		assertEquals("secret", //$NON-NLS-1$
				LlmSecureCredentials.credentialForProvider("GEMINI", "GEMINI", "secret")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("", //$NON-NLS-1$
				LlmSecureCredentials.credentialForProvider("OPENAI", "GEMINI", "secret")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("", //$NON-NLS-1$
				LlmSecureCredentials.credentialForProvider("GEMINI", "GEMINI", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
