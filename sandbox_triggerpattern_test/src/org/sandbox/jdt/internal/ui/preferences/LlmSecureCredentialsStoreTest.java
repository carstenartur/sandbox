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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.junit.jupiter.api.Test;

/** Exercises Secure Storage semantics without opening the process keyring. */
public class LlmSecureCredentialsStoreTest {

	@Test
	public void writesEncryptedKeyAndReadsItOnlyForOwningProvider() throws Exception {
		MemorySecureStore store = memoryStore();

		LlmSecureCredentials.storeApiKeyInSecureStore("GEMINI", "  secret  ", store.root()); //$NON-NLS-1$ //$NON-NLS-2$

		assertEquals("secret", LlmSecureCredentials.loadApiKeyFromSecureStore("GEMINI", store.root())); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("", LlmSecureCredentials.loadApiKeyFromSecureStore("OPENAI", store.root())); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(store.encrypted().containsValue(Boolean.TRUE), "The API key must be encrypted"); //$NON-NLS-1$
		assertTrue(store.encrypted().containsValue(Boolean.FALSE), "Provider ownership is non-secret metadata"); //$NON-NLS-1$
		assertTrue(store.flushes().get() > 0);
	}

	@Test
	public void deletingAnotherProvidersBlankFieldDoesNotDeleteTheStoredKey() throws Exception {
		MemorySecureStore store = memoryStore();
		LlmSecureCredentials.storeApiKeyInSecureStore("GEMINI", "secret", store.root()); //$NON-NLS-1$ //$NON-NLS-2$

		LlmSecureCredentials.storeApiKeyInSecureStore("OPENAI", "", store.root()); //$NON-NLS-1$ //$NON-NLS-2$

		assertEquals("secret", LlmSecureCredentials.loadApiKeyFromSecureStore("GEMINI", store.root())); //$NON-NLS-1$ //$NON-NLS-2$
		assertFalse(store.values().isEmpty());
	}

	@Test
	public void deletingTheOwningProvidersKeyRemovesCredentialAndMetadata() throws Exception {
		MemorySecureStore store = memoryStore();
		LlmSecureCredentials.storeApiKeyInSecureStore("GEMINI", "secret", store.root()); //$NON-NLS-1$ //$NON-NLS-2$

		LlmSecureCredentials.storeApiKeyInSecureStore("GEMINI", "", store.root()); //$NON-NLS-1$ //$NON-NLS-2$

		assertEquals("", LlmSecureCredentials.loadApiKeyFromSecureStore("GEMINI", store.root())); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(store.values().isEmpty());
	}

	@Test
	public void firstReadBindsAnOlderUnscopedSecureKeyToTheSelectedProvider() throws Exception {
		MemorySecureStore store = memoryStore();
		LlmSecureCredentials.storeApiKeyInSecureStore("GEMINI", "legacy-secure", store.root()); //$NON-NLS-1$ //$NON-NLS-2$
		String providerMetadataKey = store.encrypted().entrySet().stream()
				.filter(entry -> !entry.getValue())
				.map(Map.Entry::getKey)
				.findFirst()
				.orElseThrow();
		store.values().remove(providerMetadataKey);
		store.encrypted().remove(providerMetadataKey);
		int flushesBeforeRead = store.flushes().get();

		assertEquals("legacy-secure", //$NON-NLS-1$
				LlmSecureCredentials.loadApiKeyFromSecureStore("OPENAI", store.root())); //$NON-NLS-1$
		assertEquals("", LlmSecureCredentials.loadApiKeyFromSecureStore("GEMINI", store.root())); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(store.flushes().get() > flushesBeforeRead);
	}

	@Test
	public void unavailableSecureStorageFailsExplicitlyOnWrite() {
		assertThrows(IOException.class,
				() -> LlmSecureCredentials.storeApiKeyInSecureStore("GEMINI", "secret", null)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private record MemorySecureStore(ISecurePreferences root, Map<String, String> values,
			Map<String, Boolean> encrypted, AtomicInteger flushes) {
	}

	private static MemorySecureStore memoryStore() {
		Map<String, String> values = new HashMap<>();
		Map<String, Boolean> encrypted = new HashMap<>();
		AtomicInteger flushes = new AtomicInteger();
		ISecurePreferences root = (ISecurePreferences) Proxy.newProxyInstance(
				ISecurePreferences.class.getClassLoader(),
				new Class<?>[] { ISecurePreferences.class },
				(proxy, method, args) -> switch (method.getName()) {
				case "node" -> proxy; //$NON-NLS-1$
				case "get" -> values.getOrDefault((String) args[0], (String) args[1]); //$NON-NLS-1$
				case "put" -> { //$NON-NLS-1$
					values.put((String) args[0], (String) args[1]);
					encrypted.put((String) args[0], (Boolean) args[2]);
					yield null;
				}
				case "remove" -> { //$NON-NLS-1$
					values.remove((String) args[0]);
					encrypted.remove((String) args[0]);
					yield null;
				}
				case "flush" -> { //$NON-NLS-1$
					flushes.incrementAndGet();
					yield null;
				}
				case "toString" -> "MemorySecurePreferences"; //$NON-NLS-1$ //$NON-NLS-2$
				case "hashCode" -> System.identityHashCode(proxy); //$NON-NLS-1$
				case "equals" -> proxy == args[0]; //$NON-NLS-1$
				default -> throw new UnsupportedOperationException(method.toString());
				});
		return new MemorySecureStore(root, values, encrypted, flushes);
	}
}
