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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter that maps {@code float[]} to {@code byte[]} for database
 * persistence.
 * <p>
 * Hibernate ORM does not natively support {@code float[]} as a basic column
 * type on all databases. This converter serializes the float array as
 * little-endian IEEE 754 bytes (4 bytes per float) and deserializes back on
 * read.
 * </p>
 * <p>
 * The converter is applied automatically via
 * {@link jakarta.persistence.Convert @Convert} on the
 * {@link JavaBlobIndex#semanticEmbedding} field.
 * </p>
 */
@Converter
public class FloatArrayConverter
		implements AttributeConverter<float[], byte[]> {

	@Override
	public byte[] convertToDatabaseColumn(float[] attribute) {
		if (attribute == null) {
			return null;
		}
		ByteBuffer buffer = ByteBuffer
				.allocate(attribute.length * Float.BYTES)
				.order(ByteOrder.LITTLE_ENDIAN);
		for (float f : attribute) {
			buffer.putFloat(f);
		}
		return buffer.array();
	}

	@Override
	public float[] convertToEntityAttribute(byte[] dbData) {
		if (dbData == null) {
			return null;
		}
		ByteBuffer buffer = ByteBuffer.wrap(dbData)
				.order(ByteOrder.LITTLE_ENDIAN);
		float[] result = new float[dbData.length / Float.BYTES];
		for (int i = 0; i < result.length; i++) {
			result[i] = buffer.getFloat();
		}
		return result;
	}
}
