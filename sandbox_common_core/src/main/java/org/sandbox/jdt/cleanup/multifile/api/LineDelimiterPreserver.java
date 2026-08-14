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
package org.sandbox.jdt.cleanup.multifile.api;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/** Restores a rewritten text file's original line delimiter without changing its encoding. */
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

		private final String text;

		LineDelimiter(String text) {
			this.text= text;
		}

		private static LineDelimiter detect(String content) {
			boolean foundLf= false;
			boolean foundCrLf= false;
			boolean foundCr= false;
			for (int index= 0; index < content.length(); index++) {
				char current= content.charAt(index);
				if (current == '\r') {
					if (index + 1 < content.length() && content.charAt(index + 1) == '\n') {
						foundCrLf= true;
						index++;
					} else {
						foundCr= true;
					}
				} else if (current == '\n') {
					foundLf= true;
				}
			}
			int styleCount= (foundLf ? 1 : 0) + (foundCrLf ? 1 : 0) + (foundCr ? 1 : 0);
			if (styleCount != 1) {
				return null;
			}
			if (foundCrLf) {
				return CRLF;
			}
			return foundLf ? LF : CR;
		}
	}

	private record Encoding(Charset charset, byte[] byteOrderMark) {
		private Encoding {
			byteOrderMark= byteOrderMark.clone();
		}

		@Override
		public byte[] byteOrderMark() {
			return byteOrderMark.clone();
		}
	}

	private LineDelimiterPreserver() {
	}

	/**
	 * Returns rewritten bytes with the original file's single line-delimiter style,
	 * declared character set and byte-order mark. Files without one unambiguous
	 * original delimiter are returned unchanged.
	 *
	 * @param original original file bytes
	 * @param rewritten bytes produced by the rewrite
	 * @param declaredCharset Eclipse file character set
	 * @return rewritten bytes with original text representation preserved
	 * @throws IOException if either byte sequence cannot be decoded or encoded safely
	 */
	public static byte[] preserve(byte[] original, byte[] rewritten, String declaredCharset) throws IOException {
		Objects.requireNonNull(original, "original"); //$NON-NLS-1$
		Objects.requireNonNull(rewritten, "rewritten"); //$NON-NLS-1$
		Objects.requireNonNull(declaredCharset, "declaredCharset"); //$NON-NLS-1$

		Charset declared;
		try {
			declared= Charset.forName(declaredCharset);
		} catch (IllegalArgumentException e) {
			throw new IOException("Unsupported source character set: " + declaredCharset, e); //$NON-NLS-1$
		}
		Encoding encoding= encodingOf(original, declared);
		String originalText= decode(original, encoding, "original"); //$NON-NLS-1$
		LineDelimiter delimiter= LineDelimiter.detect(originalText);
		if (delimiter == null) {
			return rewritten;
		}

		String rewrittenText= decode(rewritten, encoding, "rewritten"); //$NON-NLS-1$
		String normalized= normalize(rewrittenText, delimiter.text);
		byte[] encoded= encode(normalized, encoding);
		return Arrays.equals(encoded, rewritten) ? rewritten : encoded;
	}

	private static Encoding encodingOf(byte[] content, Charset declared) throws IOException {
		Bom bom= Bom.detect(content);
		if (bom != null) {
			if (!compatible(declared, bom.charset())) {
				throw new IOException("Source byte-order mark " + bom.charset().name() //$NON-NLS-1$
						+ " conflicts with declared character set " + declared.name()); //$NON-NLS-1$
			}
			return new Encoding(bom.charset(), bom.bytes());
		}
		String name= declared.name().toUpperCase(Locale.ROOT);
		if ("UTF-16".equals(name)) { //$NON-NLS-1$
			return new Encoding(StandardCharsets.UTF_16BE, new byte[0]);
		}
		if ("UTF-32".equals(name)) { //$NON-NLS-1$
			return new Encoding(Charset.forName("UTF-32BE"), new byte[0]); //$NON-NLS-1$
		}
		return new Encoding(declared, new byte[0]);
	}

	private static boolean compatible(Charset declared, Charset encoded) {
		String declaredName= declared.name().toUpperCase(Locale.ROOT);
		String encodedName= encoded.name().toUpperCase(Locale.ROOT);
		return declaredName.equals(encodedName)
				|| "UTF-16".equals(declaredName) && encodedName.startsWith("UTF-16") //$NON-NLS-1$ //$NON-NLS-2$
				|| "UTF-32".equals(declaredName) && encodedName.startsWith("UTF-32"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String decode(byte[] content, Encoding encoding, String label) throws IOException {
		int offset= bomOffset(content, encoding);
		try {
			return encoding.charset().newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(content, offset, content.length - offset))
					.toString();
		} catch (CharacterCodingException e) {
			throw new IOException("Cannot decode " + label + " source as " + encoding.charset().name(), e); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static int bomOffset(byte[] content, Encoding encoding) throws IOException {
		Bom actual= Bom.detect(content);
		if (actual == null) {
			return 0;
		}
		if (!actual.charset().equals(encoding.charset())) {
			throw new IOException("Rewritten source changed byte-order mark from " //$NON-NLS-1$
					+ encoding.charset().name() + " to " + actual.charset().name()); //$NON-NLS-1$
		}
		return actual.bytes().length;
	}

	private static byte[] encode(String content, Encoding encoding) throws IOException {
		try {
			ByteBuffer bytes= encoding.charset().newEncoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.encode(CharBuffer.wrap(content));
			byte[] bom= encoding.byteOrderMark();
			int encodedLength= bytes.remaining();
			byte[] result= new byte[bom.length + encodedLength];
			System.arraycopy(bom, 0, result, 0, bom.length);
			bytes.get(result, bom.length, encodedLength);
			return result;
		} catch (CharacterCodingException e) {
			throw new IOException("Cannot encode rewritten source as " + encoding.charset().name(), e); //$NON-NLS-1$
		}
	}

	private static String normalize(String content, String delimiter) {
		StringBuilder normalized= new StringBuilder(content.length() + 64);
		for (int index= 0; index < content.length(); index++) {
			char current= content.charAt(index);
			if (current == '\r') {
				if (index + 1 < content.length() && content.charAt(index + 1) == '\n') {
					index++;
				}
				normalized.append(delimiter);
			} else if (current == '\n') {
				normalized.append(delimiter);
			} else {
				normalized.append(current);
			}
		}
		return normalized.toString();
	}

	private record Bom(Charset charset, byte[] bytes) {
		private Bom {
			bytes= bytes.clone();
		}

		@Override
		public byte[] bytes() {
			return bytes.clone();
		}

		private static Bom detect(byte[] content) {
			if (startsWith(content, UTF_32_BE_BOM)) {
				return new Bom(Charset.forName("UTF-32BE"), UTF_32_BE_BOM); //$NON-NLS-1$
			}
			if (startsWith(content, UTF_32_LE_BOM)) {
				return new Bom(Charset.forName("UTF-32LE"), UTF_32_LE_BOM); //$NON-NLS-1$
			}
			if (startsWith(content, UTF_8_BOM)) {
				return new Bom(StandardCharsets.UTF_8, UTF_8_BOM);
			}
			if (startsWith(content, UTF_16_BE_BOM)) {
				return new Bom(StandardCharsets.UTF_16BE, UTF_16_BE_BOM);
			}
			if (startsWith(content, UTF_16_LE_BOM)) {
				return new Bom(StandardCharsets.UTF_16LE, UTF_16_LE_BOM);
			}
			return null;
		}
	}

	private static boolean startsWith(byte[] content, byte[] prefix) {
		if (content.length < prefix.length) {
			return false;
		}
		for (int index= 0; index < prefix.length; index++) {
			if (content[index] != prefix[index]) {
				return false;
			}
		}
		return true;
	}
}
