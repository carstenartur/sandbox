/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class LineDelimiterPreserverTest {

	private static final byte[] UTF_8_BOM= { (byte) 0xef, (byte) 0xbb, (byte) 0xbf };
	private static final byte[] UTF_16_LE_BOM= { (byte) 0xff, (byte) 0xfe };
	private static final byte[] UTF_16_BE_BOM= { (byte) 0xfe, (byte) 0xff };

	@Test
	void preservesUtf8CrLf() throws Exception {
		byte[] original= "first\r\nsecond\r\n".getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$
		byte[] transformed= "first changed\nsecond\n".getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$

		assertArrayEquals("first changed\r\nsecond\r\n".getBytes(StandardCharsets.UTF_8), //$NON-NLS-1$
				LineDelimiterPreserver.preserve(original, transformed, StandardCharsets.UTF_8.name()));
	}

	@Test
	void preservesUtf8Bom() throws Exception {
		byte[] original= withBom(UTF_8_BOM, "first\r\nsecond\r\n".getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$
		byte[] transformed= "first changed\nsecond\n".getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$
		byte[] expected= withBom(UTF_8_BOM,
				"first changed\r\nsecond\r\n".getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$

		assertArrayEquals(expected,
				LineDelimiterPreserver.preserve(original, transformed, StandardCharsets.UTF_8.name()));
	}

	@Test
	void preservesUtf16LeBomWithoutConfusingCodeUnitBytesForNewlines() throws Exception {
		String originalText= "\u010afirst\r\nsecond\r\n"; //$NON-NLS-1$
		String transformedText= "\u010afirst changed\nsecond\n"; //$NON-NLS-1$
		byte[] original= withBom(UTF_16_LE_BOM, originalText.getBytes(StandardCharsets.UTF_16LE));
		byte[] transformed= transformedText.getBytes(StandardCharsets.UTF_16LE);
		byte[] expected= withBom(UTF_16_LE_BOM,
				"\u010afirst changed\r\nsecond\r\n".getBytes(StandardCharsets.UTF_16LE)); //$NON-NLS-1$

		assertArrayEquals(expected,
				LineDelimiterPreserver.preserve(original, transformed, StandardCharsets.UTF_16LE.name()));
	}

	@Test
	void preservesUtf16BeBom() throws Exception {
		byte[] original= withBom(UTF_16_BE_BOM,
				"first\r\nsecond\r\n".getBytes(StandardCharsets.UTF_16BE)); //$NON-NLS-1$
		byte[] transformed= "first changed\nsecond\n".getBytes(StandardCharsets.UTF_16BE); //$NON-NLS-1$
		byte[] expected= withBom(UTF_16_BE_BOM,
				"first changed\r\nsecond\r\n".getBytes(StandardCharsets.UTF_16BE)); //$NON-NLS-1$

		assertArrayEquals(expected,
				LineDelimiterPreserver.preserve(original, transformed, StandardCharsets.UTF_16BE.name()));
	}

	@Test
	void preservesBomlessUtf16Le() throws Exception {
		byte[] original= "first\r\nsecond\r\n".getBytes(StandardCharsets.UTF_16LE); //$NON-NLS-1$
		byte[] transformed= "first changed\nsecond\n".getBytes(StandardCharsets.UTF_16LE); //$NON-NLS-1$
		byte[] expected= "first changed\r\nsecond\r\n".getBytes(StandardCharsets.UTF_16LE); //$NON-NLS-1$

		assertArrayEquals(expected,
				LineDelimiterPreserver.preserve(original, transformed, StandardCharsets.UTF_16LE.name()));
	}

	@Test
	void leavesAmbiguousMixedOriginalDelimitersUntouched() throws Exception {
		byte[] original= "first\r\nsecond\n".getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$
		byte[] transformed= "changed\ncontent\n".getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$

		assertSame(transformed,
				LineDelimiterPreserver.preserve(original, transformed, StandardCharsets.UTF_8.name()));
	}

	@Test
	void rejectsMalformedTransformedInput() {
		byte[] original= "first\r\nsecond\r\n".getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$
		byte[] malformed= { (byte) 0xc3, 0x28 };

		assertThrows(IOException.class,
				() -> LineDelimiterPreserver.preserve(original, malformed, StandardCharsets.UTF_8.name()));
	}

	private static byte[] withBom(byte[] bom, byte[] content) {
		byte[] result= new byte[bom.length + content.length];
		System.arraycopy(bom, 0, result, 0, bom.length);
		System.arraycopy(content, 0, result, bom.length, content.length);
		return result;
	}
}
