/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Safe workspace boundary for XML cleanup reads, transformations and writes.
 *
 * <p>The effective charset is resolved in this order: byte-order mark, an
 * unambiguous UTF-16 byte signature, XML declaration, Eclipse resource charset,
 * then UTF-8. A BOM and XML declaration must agree. Supported BOMs are UTF-8,
 * UTF-16LE and UTF-16BE and are preserved exactly.</p>
 *
 * <p>Dirty connected text buffers are refused. A prepared transformation stores
 * both the resource modification stamp and the original bytes; both are checked
 * again immediately before writing so external changes cannot be overwritten
 * silently.</p>
 */
public final class XMLResourceSupport {

	private static final String PLUGIN_ID= "sandbox_xml_cleanup"; //$NON-NLS-1$
	private static final int MAX_INPUT_BYTES= 16 * 1024 * 1024;
	private static final byte[] UTF_8_BOM= { (byte) 0xef, (byte) 0xbb, (byte) 0xbf };
	private static final byte[] UTF_16_BE_BOM= { (byte) 0xfe, (byte) 0xff };
	private static final byte[] UTF_16_LE_BOM= { (byte) 0xff, (byte) 0xfe };
	private static final Pattern XML_ENCODING= Pattern.compile(
			"(?is)^\\s*<\\?xml\\s+[^>]*?encoding\\s*=\\s*(['\"])([^'\"]+)\\1"); //$NON-NLS-1$

	private XMLResourceSupport() {
	}

	/** Immutable source snapshot used for optimistic conflict detection. */
	public record Snapshot(byte[] originalBytes, String content, Charset charset,
			byte[] bom, long modificationStamp, String lineDelimiter) {
		public Snapshot {
			originalBytes= originalBytes.clone();
			bom= bom.clone();
		}

		@Override
		public byte[] originalBytes() {
			return originalBytes.clone();
		}

		@Override
		public byte[] bom() {
			return bom.clone();
		}
	}

	/** Prepared, validated transformation that can later be applied safely. */
	public record Transformation(Snapshot snapshot, String transformedContent,
			byte[] transformedBytes) {
		public Transformation {
			transformedBytes= transformedBytes.clone();
		}

		@Override
		public byte[] transformedBytes() {
			return transformedBytes.clone();
		}

		public boolean changed() {
			return !Arrays.equals(snapshot.originalBytes, transformedBytes);
		}
	}

	/** Reads, transforms and validates an XML resource without changing it. */
	public static Transformation prepare(IFile file, boolean enableIndent,
			IProgressMonitor monitor) throws CoreException {
		checkCanceled(monitor);
		ensureNoDirtyBuffer(file);
		Snapshot snapshot= read(file);
		checkCanceled(monitor);
		String transformed;
		try {
			transformed= SchemaTransformationUtils.transform(snapshot.content,
					snapshot.charset, enableIndent);
		} catch (Exception e) {
			throw error("Failed to transform XML resource: " + file.getFullPath(), e); //$NON-NLS-1$
		}
		transformed= normalizeLineDelimiters(transformed, snapshot.lineDelimiter);
		validateWellFormed(transformed);
		byte[] encoded= encode(transformed, snapshot.charset, snapshot.bom);
		return new Transformation(snapshot, transformed, encoded);
	}

	/** Applies a prepared transformation after rechecking dirty state and content. */
	public static void write(IFile file, Transformation transformation,
			IProgressMonitor monitor) throws CoreException {
		checkCanceled(monitor);
		ensureNoDirtyBuffer(file);
		Snapshot snapshot= transformation.snapshot;
		if (!file.exists()) {
			throw error("XML resource no longer exists: " + file.getFullPath(), null); //$NON-NLS-1$
		}
		byte[] current= readBytes(file);
		if (file.getModificationStamp() != snapshot.modificationStamp
				|| !Arrays.equals(current, snapshot.originalBytes)) {
			throw error("XML resource changed after analysis: " + file.getFullPath(), null); //$NON-NLS-1$
		}
		validateWellFormed(transformation.transformedContent);
		try (ByteArrayInputStream input= new ByteArrayInputStream(transformation.transformedBytes)) {
			file.setContents(input, IResource.KEEP_HISTORY, monitor);
			String currentCharset= file.getCharset(true);
			if (!snapshot.charset.name().equalsIgnoreCase(currentCharset)) {
				file.setCharset(snapshot.charset.name(), monitor);
			}
		} catch (IOException e) {
			throw error("Failed to close XML content stream", e); //$NON-NLS-1$
		}
	}

	static Snapshot read(IFile file) throws CoreException {
		byte[] bytes= readBytes(file);
		String resourceCharset= file.getCharset(true);
		Decoded decoded= decode(bytes, resourceCharset);
		return new Snapshot(bytes, decoded.content, decoded.charset, decoded.bom,
				file.getModificationStamp(), detectLineDelimiter(decoded.content));
	}

	static Decoded decode(byte[] bytes, String resourceCharset) throws CoreException {
		byte[] bom= new byte[0];
		Charset byteCharset= null;
		int offset= 0;
		if (startsWith(bytes, UTF_8_BOM)) {
			bom= UTF_8_BOM;
			byteCharset= StandardCharsets.UTF_8;
			offset= UTF_8_BOM.length;
		} else if (startsWith(bytes, UTF_16_BE_BOM)) {
			bom= UTF_16_BE_BOM;
			byteCharset= StandardCharsets.UTF_16BE;
			offset= UTF_16_BE_BOM.length;
		} else if (startsWith(bytes, UTF_16_LE_BOM)) {
			bom= UTF_16_LE_BOM;
			byteCharset= StandardCharsets.UTF_16LE;
			offset= UTF_16_LE_BOM.length;
		} else if (startsWith(bytes, new byte[] { 0x00, 0x3c, 0x00, 0x3f })) {
			byteCharset= StandardCharsets.UTF_16BE;
		} else if (startsWith(bytes, new byte[] { 0x3c, 0x00, 0x3f, 0x00 })) {
			byteCharset= StandardCharsets.UTF_16LE;
		}

		String declaredEncoding= declaredEncoding(bytes, offset, byteCharset);
		Charset declaredCharset= toCharset(declaredEncoding, "XML declaration"); //$NON-NLS-1$
		Charset metadataCharset= toCharset(resourceCharset, "resource metadata"); //$NON-NLS-1$
		Charset effective= byteCharset != null ? byteCharset
				: declaredCharset != null ? declaredCharset
				: metadataCharset != null ? metadataCharset : StandardCharsets.UTF_8;

		if (byteCharset != null && declaredCharset != null
				&& !equivalent(byteCharset, declaredCharset)) {
			throw error("XML byte order and declaration disagree: " //$NON-NLS-1$
					+ byteCharset.name() + " vs " + declaredCharset.name(), null); //$NON-NLS-1$
		}

		byte[] contentBytes= Arrays.copyOfRange(bytes, offset, bytes.length);
		try {
			String content= effective.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(contentBytes)).toString();
			return new Decoded(content, effective, bom);
		} catch (CharacterCodingException e) {
			throw error("XML bytes are not valid " + effective.name(), e); //$NON-NLS-1$
		}
	}

	static record Decoded(String content, Charset charset, byte[] bom) {
		Decoded {
			bom= bom.clone();
		}

		@Override
		public byte[] bom() {
			return bom.clone();
		}
	}

	static void validateWellFormed(String content) throws CoreException {
		try {
			DocumentBuilderFactory factory= DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false); //$NON-NLS-1$
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); //$NON-NLS-1$
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); //$NON-NLS-1$
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); //$NON-NLS-1$
			factory.newDocumentBuilder().parse(new InputSource(new StringReader(content)));
		} catch (ParserConfigurationException | SAXException | IOException e) {
			throw error("XML cleanup output is not well-formed", e); //$NON-NLS-1$
		}
	}

	private static byte[] readBytes(IFile file) throws CoreException {
		try (InputStream input= file.getContents()) {
			byte[] bytes= input.readNBytes(MAX_INPUT_BYTES + 1);
			if (bytes.length > MAX_INPUT_BYTES) {
				throw error("XML resource exceeds the 16 MiB cleanup limit: " //$NON-NLS-1$
						+ file.getFullPath(), null);
			}
			return bytes;
		} catch (IOException e) {
			throw error("Failed to read XML resource: " + file.getFullPath(), e); //$NON-NLS-1$
		}
	}

	private static void ensureNoDirtyBuffer(IFile file) throws CoreException {
		ITextFileBuffer buffer= FileBuffers.getTextFileBufferManager()
				.getTextFileBuffer(file.getFullPath(), LocationKind.IFILE);
		if (buffer != null && buffer.isDirty()) {
			throw error("XML resource has unsaved editor changes: " //$NON-NLS-1$
					+ file.getFullPath(), null);
		}
	}

	private static String declaredEncoding(byte[] bytes, int offset,
			Charset byteCharset) throws CoreException {
		int length= Math.min(bytes.length - offset, 1024);
		if (length <= 0) {
			return null;
		}
		Charset probeCharset= byteCharset != null ? byteCharset : StandardCharsets.ISO_8859_1;
		String prefix;
		try {
			prefix= probeCharset.newDecoder().decode(
					ByteBuffer.wrap(bytes, offset, length)).toString();
		} catch (CharacterCodingException e) {
			throw error("Could not inspect XML declaration", e); //$NON-NLS-1$
		}
		Matcher matcher= XML_ENCODING.matcher(prefix);
		return matcher.find() ? matcher.group(2).trim() : null;
	}

	private static Charset toCharset(String name, String source) throws CoreException {
		if (name == null || name.isBlank()) {
			return null;
		}
		try {
			return Charset.forName(name);
		} catch (IllegalArgumentException e) {
			throw error("Unsupported XML charset in " + source + ": " + name, e); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static boolean equivalent(Charset first, Charset second) {
		String firstName= first.name().toUpperCase(Locale.ROOT);
		String secondName= second.name().toUpperCase(Locale.ROOT);
		if (firstName.equals(secondName)) {
			return true;
		}
		return firstName.startsWith("UTF-16") && secondName.equals("UTF-16") //$NON-NLS-1$ //$NON-NLS-2$
				|| secondName.startsWith("UTF-16") && firstName.equals("UTF-16"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static byte[] encode(String content, Charset charset, byte[] bom)
			throws CoreException {
		byte[] body;
		try {
			ByteBuffer encoded= charset.newEncoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.encode(java.nio.CharBuffer.wrap(content));
			body= new byte[encoded.remaining()];
			encoded.get(body);
		} catch (CharacterCodingException e) {
			throw error("Transformed XML cannot be represented as " + charset.name(), e); //$NON-NLS-1$
		}
		byte[] result= Arrays.copyOf(bom, bom.length + body.length);
		System.arraycopy(body, 0, result, bom.length, body.length);
		return result;
	}

	private static String detectLineDelimiter(String content) {
		return content.contains("\r\n") ? "\r\n" : "\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static String normalizeLineDelimiters(String content, String delimiter) {
		String normalized= content.replace("\r\n", "\n").replace('\r', '\n'); //$NON-NLS-1$ //$NON-NLS-2$
		return "\n".equals(delimiter) ? normalized : normalized.replace("\n", delimiter); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static boolean startsWith(byte[] content, byte[] prefix) {
		if (content.length < prefix.length) {
			return false;
		}
		for (int i= 0; i < prefix.length; i++) {
			if (content[i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new org.eclipse.core.runtime.OperationCanceledException();
		}
	}

	private static CoreException error(String message, Throwable cause) {
		return new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, message, cause));
	}
}
