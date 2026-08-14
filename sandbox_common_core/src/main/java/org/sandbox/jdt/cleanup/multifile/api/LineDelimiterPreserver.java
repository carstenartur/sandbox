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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Restores a source file's uniform line-delimiter convention without making
 * assumptions about the byte representation of newline characters.
 * <p>
 * The original charset and byte-order mark are preserved. Decoding and encoding
 * are strict: malformed input is reported instead of being replaced silently.
 * Files with mixed or no original line delimiters are left unchanged because no
 * single convention can be inferred safely.
 * </p>
 */
public final class LineDelimiterPreserver {

	private static final byte[] UTF_8_BOM= { (byte) 0xef, (byte) 0xbb, (byte) 0xbf };
	private static final byte[] UTF_16_BE_BOM= { (byte) 0xfe, (byte) 0xff };
	private static final byte[] UTF_16_LE_BOM= { (byte) 0xff, (byte) 0xfe };
	private static final byte[] UTF_32_BE_BOM= { 0, 0, (byte) 0xfe, (byte) 0xff };
	private static final byte[] UTF_32_LE_BOM= { (byte) 0xff, (byte) 0xfe, 0, 0 };

	private enum LineDelimiter {
		LF("\n"), //$NON-NLS-1$
		CRLF("\r\n"), //$NON-NLS-1$
		CR("\r"); //$NON-NLS-1$

		private final String value;

		LineDelimiter(String value) {
			this.value= value;
		}
	}

	private record Encoding(Charset charset, byte[] bom) {

		private String decode(byte[] content) throws CharacterCodingException {
			int offset= startsWith(content, bom) ? bom.length : 0;
			return charset.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(content, offset, content.length - offset))
					.toString();
		}

		private byte[] encode(String content) throws CharacterCodingException {
			ByteBuffer encoded= charset.newEncoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.encode(CharBuffer.wrap(content));
			byte[] result= Arrays.copyOf(bom, bom.length + encoded.remaining());
			encoded.get(result, bom.length, encoded.remaining());
			return result;
		}
	}

	private LineDelimiterPreserver() {
	}

	/**
	 * Converts all line delimiters in {@code transformed} to the single delimiter
	 * convention detected in {@code original}, while preserving charset and BOM.
	 *
	 * @param original original source bytes
	 * @param transformed transformed source bytes
	 * @param declaredCharset charset declared by the Eclipse resource
	 * @return normalized bytes, or {@code transformed} unchanged when the original
	 *         delimiter convention is not unambiguous
	 * @throws CharacterCodingException if either byte sequence cannot be decoded or
	 *         the normalized text cannot be encoded losslessly
	 */
	public static byte[] preserve(byte[] original, byte[] transformed, String declaredCharset)
			throws CharacterCodingException {
		Objects.requireNonNull(original, "original"); //$NON-NLS-1$
		Objects.requireNonNull(transformed, "transformed"); //$NON-NLS-1$
		Objects.requireNonNull(declaredCharset, "declaredCharset"); //$NON-NLS-1$

		Encoding encoding= detectEncoding(original, declaredCharset);
		LineDelimiter delimiter= detectLineDelimiter(encoding.decode(original));
		if (delimiter == null) {
			return transformed;
		}

		String transformedText= encoding.decode(transformed);
		String normalized= normalize(transformedText, delimiter.value);
		if (normalized.equals(transformedText) && startsWith(transformed, encoding.bom)) {
			return transformed;
		}
		return encoding.encode(normalized);
	}

	private static Encoding detectEncoding(byte[] original, String declaredCharset) {
		if (startsWith(original, UTF_32_BE_BOM)) {
			return new Encoding(Charset.forName("UTF-32BE"), UTF_32_BE_BOM); //$NON-NLS-1$
		}
		if (startsWith(original, UTF_32_LE_BOM)) {
			return new Encoding(Charset.forName("UTF-32LE"), UTF_32_LE_BOM); //$NON-NLS-1$
		}
		if (startsWith(original, UTF_8_BOM)) {
			return new Encoding(StandardCharsets.UTF_8, UTF_8_BOM);
		}
		if (startsWith(original, UTF_16_BE_BOM)) {
			return new Encoding(StandardCharsets.UTF_16BE, UTF_16_BE_BOM);
		}
		if (startsWith(original, UTF_16_LE_BOM)) {
			return new Encoding(StandardCharsets.UTF_16LE, UTF_16_LE_BOM);
		}

		Charset charset= Charset.forName(declaredCharset);
		String canonical= charset.name().toUpperCase(Locale.ROOT);
		if ("UTF-16".equals(canonical)) { //$NON-NLS-1$
			charset= StandardCharsets.UTF_16BE;
		} else if ("UTF-32".equals(canonical)) { //$NON-NLS-1$
			charset= Charset.forName("UTF-32BE"); //$NON-NLS-1$
		}
		return new Encoding(charset, new byte[0]);
	}

	private static LineDelimiter detectLineDelimiter(String content) {
		boolean lf= false;
		boolean crlf= false;
		boolean cr= false;
		for (int index= 0; index < content.length(); index++) {
			char character= content.charAt(index);
			if (character == '\r') {
				if (index + 1 < content.length() && content.charAt(index + 1) == '\n') {
					crlf= true;
					index++;
				} else {
					cr= true;
				}
			} else if (character == '\n') {
				lf= true;
			}
		}
		int count= (lf ? 1 : 0) + (crlf ? 1 : 0) + (cr ? 1 : 0);
		if (count != 1) {
			return null;
		}
		if (crlf) {
			return LineDelimiter.CRLF;
		}
		return lf ? LineDelimiter.LF : LineDelimiter.CR;
	}

	private static String normalize(String content, String delimiter) {
		StringBuilder normalized= new StringBuilder(content.length() + 32);
		for (int index= 0; index < content.length(); index++) {
			char character= content.charAt(index);
			if (character == '\r') {
				if (index + 1 < content.length() && content.charAt(index + 1) == '\n') {
					index++;
				}
				normalized.append(delimiter);
			} else if (character == '\n') {
				normalized.append(delimiter);
			} else {
				normalized.append(character);
			}
		}
		return normalized.toString();
	}

	private static boolean startsWith(byte[] content, byte[] prefix) {
		if (prefix.length == 0 || content.length < prefix.length) {
			return prefix.length == 0;
		}
		for (int index= 0; index < prefix.length; index++) {
			if (content[index] != prefix[index]) {
				return false;
			}
		}
		return true;
	}
}
