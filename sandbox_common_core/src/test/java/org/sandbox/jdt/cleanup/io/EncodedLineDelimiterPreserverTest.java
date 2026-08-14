/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class EncodedLineDelimiterPreserverTest {

	private static final byte[] UTF_8_BOM= { (byte) 0xef, (byte) 0xbb, (byte) 0xbf };
	private static final byte[] UTF_16_LE_BOM= { (byte) 0xff, (byte) 0xfe };

	@Test
	void restoresCrlfWithoutChangingUtf8Text() throws IOException {
		byte[] original= "Größe\r\nvorher\r\n".getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$
		byte[] rewritten= "Größe\nnachher\n".getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$
		byte[] expected= "Größe\r\nnachher\r\n".getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$

		assertArrayEquals(expected,
				EncodedLineDelimiterPreserver.preserve(original, rewritten, "UTF-8")); //$NON-NLS-1$
	}

	@Test
	void preservesUtf8ByteOrderMark() throws IOException {
		byte[] original= withPrefix(UTF_8_BOM, "first\r\nsecond\r\n".getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$
		byte[] rewritten= "first\nchanged\n".getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$
		byte[] expected= withPrefix(UTF_8_BOM, "first\r\nchanged\r\n".getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$

		assertArrayEquals(expected,
				EncodedLineDelimiterPreserver.preserve(original, rewritten, "UTF-8")); //$NON-NLS-1$
	}

	@Test
	void preservesUtf16EncodingBomAndCharactersContainingNewlineBytes() throws IOException {
		String originalText= "\u0a00\r\nbefore\r\n"; //$NON-NLS-1$
		String rewrittenText= "\u0a00\nafter\n"; //$NON-NLS-1$
		String expectedText= "\u0a00\r\nafter\r\n"; //$NON-NLS-1$
		byte[] original= withPrefix(UTF_16_LE_BOM, originalText.getBytes(StandardCharsets.UTF_16LE));
		byte[] rewritten= withPrefix(UTF_16_LE_BOM, rewrittenText.getBytes(StandardCharsets.UTF_16LE));
		byte[] expected= withPrefix(UTF_16_LE_BOM, expectedText.getBytes(StandardCharsets.UTF_16LE));

		assertArrayEquals(expected,
				EncodedLineDelimiterPreserver.preserve(original, rewritten, "UTF-16")); //$NON-NLS-1$
	}

	@Test
	void keepsRewrittenBytesWhenOriginalUsesMixedDelimiters() throws IOException {
		byte[] original= "first\r\nsecond\n".getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$
		byte[] rewritten= "first\nchanged\n".getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$

		assertSame(rewritten,
				EncodedLineDelimiterPreserver.preserve(original, rewritten, "UTF-8")); //$NON-NLS-1$
	}

	@Test
	void rejectsBomThatConflictsWithDeclaredEncoding() {
		byte[] original= withPrefix(UTF_16_LE_BOM, "first\r\n".getBytes(StandardCharsets.UTF_16LE)); //$NON-NLS-1$

		assertThrows(IOException.class,
				() -> EncodedLineDelimiterPreserver.preserve(original, original, "UTF-8")); //$NON-NLS-1$
	}

	private static byte[] withPrefix(byte[] prefix, byte[] content) {
		byte[] result= new byte[prefix.length + content.length];
		System.arraycopy(prefix, 0, result, 0, prefix.length);
		System.arraycopy(content, 0, result, prefix.length, content.length);
		return result;
	}
}
