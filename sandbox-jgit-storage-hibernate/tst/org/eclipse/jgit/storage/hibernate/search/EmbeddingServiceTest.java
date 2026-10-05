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
package org.eclipse.jgit.storage.hibernate.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import ai.djl.repository.zoo.ZooModel;

/**
 * Unit tests for {@link EmbeddingService}.
 * <p>
 * These tests validate the static helper method
 * {@link EmbeddingService#buildEmbeddingText(String, String, String, String)}
 * and the disabled/unavailable-mode behavior. Fault injection at the model
 * loader boundary verifies optional native-runtime failures without a network
 * download. The actual model loading tests are
 * integration-level and require the DJL model to be available.
 * </p>
 */
class EmbeddingServiceTest {

	@Test
	void buildEmbeddingTextWithAllFields() {
		String result = EmbeddingService.buildEmbeddingText(
				"HttpClient", //$NON-NLS-1$
				"HTTP client with retry support", //$NON-NLS-1$
				"get(String url)\npost(String url, byte[] body)", //$NON-NLS-1$
				"com.example.http"); //$NON-NLS-1$
		assertTrue(result.contains("HttpClient")); //$NON-NLS-1$
		assertTrue(result.contains("HTTP client with retry support")); //$NON-NLS-1$
		assertTrue(result.contains("Methods: ")); //$NON-NLS-1$
		assertTrue(result.contains("Package: ")); //$NON-NLS-1$
	}

	@Test
	void buildEmbeddingTextWithNullFields() {
		String result = EmbeddingService.buildEmbeddingText(null, null,
				null, null);
		assertEquals("", result); //$NON-NLS-1$
	}

	@Test
	void buildEmbeddingTextWithEmptyFields() {
		String result = EmbeddingService.buildEmbeddingText("", "", "", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				""); //$NON-NLS-1$
		assertEquals("", result); //$NON-NLS-1$
	}

	@Test
	void buildEmbeddingTextClassNameOnly() {
		String result = EmbeddingService.buildEmbeddingText(
				"MyService", null, null, null); //$NON-NLS-1$
		assertEquals("MyService", result); //$NON-NLS-1$
	}

	@Test
	void buildEmbeddingTextDocumentationOnly() {
		String result = EmbeddingService.buildEmbeddingText(null,
				"A service for processing data", null, null); //$NON-NLS-1$
		assertEquals("A service for processing data", result); //$NON-NLS-1$
	}

	@Test
	void buildEmbeddingTextClassAndDocumentation() {
		String result = EmbeddingService.buildEmbeddingText(
				"DataProcessor", "Processes CSV files", null, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("DataProcessor: Processes CSV files", result); //$NON-NLS-1$
	}

	@Test
	void disabledServiceReturnsNull() {
		EmbeddingService service = new EmbeddingService(false, "unused", //$NON-NLS-1$
				null);
		assertNull(service.embed("test text")); //$NON-NLS-1$
		assertFalse(service.isAvailable());
		assertFalse(service.isEnabled());
	}

	@Test
	void embedReturnsNullForBlankText() {
		EmbeddingService service = new EmbeddingService(true, "unused", //$NON-NLS-1$
				null);
		assertNull(service.embed(null));
		assertNull(service.embed("")); //$NON-NLS-1$
		assertNull(service.embed("   ")); //$NON-NLS-1$
	}

	@Test
	void unavailableNativeRuntimeDisablesVectorSearchWithoutRetrying() {
		AtomicInteger attempts = new AtomicInteger();
		EmbeddingService service = new EmbeddingService(true, "unused", null) { //$NON-NLS-1$
			@Override
			ZooModel<String, float[]> loadModel() {
				attempts.incrementAndGet();
				throw new UnsatisfiedLinkError("Missing ONNX native runtime"); //$NON-NLS-1$
			}
		};
		assertNull(service.embed("first document")); //$NON-NLS-1$
		assertFalse(service.isAvailable());
		assertTrue(service.isEnabled());
		assertNull(service.embed("second document")); //$NON-NLS-1$
		assertEquals(1, attempts.get(), "Do not retry a failed native initialization for each document"); //$NON-NLS-1$
		service.close();
	}

	@Test
	void previouslyFailedNativeClassInitializationAlsoDisablesVectorSearch() {
		EmbeddingService service = new EmbeddingService(true, "unused", null) { //$NON-NLS-1$
			@Override
			ZooModel<String, float[]> loadModel() {
				throw new NoClassDefFoundError("Could not initialize native runtime class"); //$NON-NLS-1$
			}
		};
		assertFalse(service.isAvailable());
		assertNull(service.embed("document")); //$NON-NLS-1$
	}

	@Test
	void modelIoFailureStillLeavesEmbeddingsUnavailable() {
		EmbeddingService service = new EmbeddingService(true, "unused", null) { //$NON-NLS-1$
			@Override
			ZooModel<String, float[]> loadModel() throws IOException {
				throw new IOException("Unreadable model cache"); //$NON-NLS-1$
			}
		};
		assertNull(service.embed("document")); //$NON-NLS-1$
		assertFalse(service.isAvailable());
	}

	@Test
	void virtualMachineFailuresAreNotTreatedAsOptionalRuntimeUnavailability() {
		OutOfMemoryError failure = new OutOfMemoryError("Synthetic fatal VM failure"); //$NON-NLS-1$
		EmbeddingService service = new EmbeddingService(true, "unused", null) { //$NON-NLS-1$
			@Override
			ZooModel<String, float[]> loadModel() {
				throw failure;
			}
		};
		assertSame(failure, assertThrows(OutOfMemoryError.class, () -> service.embed("document"))); //$NON-NLS-1$
	}

	@Test
	void unexpectedProgrammingFailuresAreNotHiddenByTheNativeFallback() {
		IllegalStateException failure = new IllegalStateException("Unexpected loader defect"); //$NON-NLS-1$
		EmbeddingService service = new EmbeddingService(true, "unused", null) { //$NON-NLS-1$
			@Override
			ZooModel<String, float[]> loadModel() {
				throw failure;
			}
		};
		assertSame(failure, assertThrows(IllegalStateException.class, () -> service.embed("document"))); //$NON-NLS-1$
	}

	@Test
	void embeddingDimensionConstantIs384() {
		assertEquals(384, EmbeddingService.EMBEDDING_DIMENSION);
	}
}
