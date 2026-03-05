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
package org.eclipse.jgit.storage.hibernate.entity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.eclipse.jgit.storage.hibernate.search.EmbeddingService;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FloatArrayConverter}.
 */
class FloatArrayConverterTest {

	private final FloatArrayConverter converter = new FloatArrayConverter();

	@Test
	void nullConvertsToDatabaseColumnAsNull() {
		assertNull(converter.convertToDatabaseColumn(null));
	}

	@Test
	void nullConvertsToEntityAttributeAsNull() {
		assertNull(converter.convertToEntityAttribute(null));
	}

	@Test
	void roundTripPreservesValues() {
		float[] original = { 1.0f, -0.5f, 0.0f, Float.MAX_VALUE,
				Float.MIN_VALUE };
		byte[] bytes = converter.convertToDatabaseColumn(original);
		float[] restored = converter.convertToEntityAttribute(bytes);
		assertArrayEquals(original, restored);
	}

	@Test
	void emptyArrayRoundTrips() {
		float[] empty = {};
		byte[] bytes = converter.convertToDatabaseColumn(empty);
		float[] restored = converter.convertToEntityAttribute(bytes);
		assertArrayEquals(empty, restored);
	}

	@Test
	void singleElementRoundTrips() {
		float[] single = { 0.42f };
		byte[] bytes = converter.convertToDatabaseColumn(single);
		float[] restored = converter.convertToEntityAttribute(bytes);
		assertArrayEquals(single, restored);
	}

	@Test
	void embeddingDimensionRoundTrips() {
		// Test with actual embedding dimension (384)
		float[] embedding = new float[EmbeddingService.EMBEDDING_DIMENSION];
		for (int i = 0; i < embedding.length; i++) {
			embedding[i] = (float) Math.sin(i * 0.01);
		}
		byte[] bytes = converter.convertToDatabaseColumn(embedding);
		float[] restored = converter.convertToEntityAttribute(bytes);
		assertArrayEquals(embedding, restored);
	}
}
