/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Persistence-order contract for the LLM preference page. */
public class LlmPreferencePagePersistenceTest {

	@Test
	public void rejectedPreferenceSaveHasNoSecureStorageSideEffect() throws Exception {
		List<String> events= new ArrayList<>();

		boolean result= LlmPreferencePage.persistAfterPreferenceAcceptance(
				() -> {
					events.add("preferences"); //$NON-NLS-1$
					return false;
				},
				() -> events.add("credential")); //$NON-NLS-1$

		assertFalse(result);
		assertEquals(List.of("preferences"), events); //$NON-NLS-1$
	}

	@Test
	public void acceptedPreferenceSavePrecedesSecureStorageWrite() throws Exception {
		List<String> events= new ArrayList<>();

		boolean result= LlmPreferencePage.persistAfterPreferenceAcceptance(
				() -> {
					events.add("preferences"); //$NON-NLS-1$
					return true;
				},
				() -> events.add("credential")); //$NON-NLS-1$

		assertTrue(result);
		assertEquals(List.of("preferences", "credential"), events); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
