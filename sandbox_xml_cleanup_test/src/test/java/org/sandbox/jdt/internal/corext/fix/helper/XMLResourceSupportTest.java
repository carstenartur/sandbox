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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;

class XMLResourceSupportTest {

	@Test
	void decodesUtf8FromResourceMetadata() throws CoreException {
		String xml= "<?xml version=\"1.0\"?><plugin name=\"Größe\"/>"; //$NON-NLS-1$

		XMLResourceSupport.Decoded decoded= XMLResourceSupport.decode(
				xml.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8.name());

		assertEquals(StandardCharsets.UTF_8, decoded.charset());
		assertEquals(xml, decoded.content());
		assertEquals(0, decoded.bom().length);
	}

	@Test
	void preservesUtf8Bom() throws CoreException {
		String xml= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><plugin name=\"Größe\"/>"; //$NON-NLS-1$
		byte[] body= xml.getBytes(StandardCharsets.UTF_8);
		byte[] bytes= new byte[body.length + 3];
		bytes[0]= (byte) 0xef;
		bytes[1]= (byte) 0xbb;
		bytes[2]= (byte) 0xbf;
		System.arraycopy(body, 0, bytes, 3, body.length);

		XMLResourceSupport.Decoded decoded= XMLResourceSupport.decode(bytes, "ISO-8859-1"); //$NON-NLS-1$

		assertEquals(StandardCharsets.UTF_8, decoded.charset());
		assertEquals(xml, decoded.content());
		assertArrayEquals(new byte[] { (byte) 0xef, (byte) 0xbb, (byte) 0xbf }, decoded.bom());
	}

	@Test
	void decodedBomAccessorDoesNotExposeMutableState() throws CoreException {
		byte[] bytes= { (byte) 0xef, (byte) 0xbb, (byte) 0xbf, '<', 'p', '/', '>' };
		XMLResourceSupport.Decoded decoded= XMLResourceSupport.decode(bytes, StandardCharsets.UTF_8.name());

		byte[] returned= decoded.bom();
		returned[0]= 0;

		assertArrayEquals(new byte[] { (byte) 0xef, (byte) 0xbb, (byte) 0xbf }, decoded.bom());
	}

	@Test
	void declarationOverridesDifferentResourceMetadataWithoutBom() throws CoreException {
		String xml= "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><plugin name=\"Größe\"/>"; //$NON-NLS-1$

		XMLResourceSupport.Decoded decoded= XMLResourceSupport.decode(
				xml.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8.name());

		assertEquals(StandardCharsets.ISO_8859_1, decoded.charset());
		assertEquals(xml, decoded.content());
	}

	@Test
	void detectsUtf16LittleEndianBom() throws CoreException {
		String xml= "<?xml version=\"1.0\" encoding=\"UTF-16\"?><plugin name=\"Größe\"/>"; //$NON-NLS-1$
		byte[] body= xml.getBytes(StandardCharsets.UTF_16LE);
		byte[] bytes= new byte[body.length + 2];
		bytes[0]= (byte) 0xff;
		bytes[1]= (byte) 0xfe;
		System.arraycopy(body, 0, bytes, 2, body.length);

		XMLResourceSupport.Decoded decoded= XMLResourceSupport.decode(bytes, StandardCharsets.UTF_8.name());

		assertEquals(StandardCharsets.UTF_16LE, decoded.charset());
		assertEquals(xml, decoded.content());
		assertArrayEquals(new byte[] { (byte) 0xff, (byte) 0xfe }, decoded.bom());
	}

	@Test
	void rejectsBomAndDeclarationConflict() {
		String xml= "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><plugin/>"; //$NON-NLS-1$
		byte[] body= xml.getBytes(StandardCharsets.UTF_8);
		byte[] bytes= new byte[body.length + 3];
		bytes[0]= (byte) 0xef;
		bytes[1]= (byte) 0xbb;
		bytes[2]= (byte) 0xbf;
		System.arraycopy(body, 0, bytes, 3, body.length);

		CoreException exception= assertThrows(CoreException.class,
				() -> XMLResourceSupport.decode(bytes, StandardCharsets.UTF_8.name()));
		assertTrue(exception.getMessage().contains("disagree")); //$NON-NLS-1$
	}

	@Test
	void rejectsOversizedResourceWithoutReadingUnboundedContent() {
		IFile file= (IFile) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { IFile.class },
				(proxy, method, arguments) -> switch (method.getName()) {
					case "getContents" -> new FixedLengthInputStream(16 * 1024 * 1024 + 1); //$NON-NLS-1$
					case "getFullPath" -> new Path("/project/oversized.xml"); //$NON-NLS-1$ //$NON-NLS-2$
					case "hashCode" -> System.identityHashCode(proxy); //$NON-NLS-1$
					case "equals" -> proxy == arguments[0]; //$NON-NLS-1$
					default -> primitiveDefault(method.getReturnType());
				});

		CoreException exception= assertThrows(CoreException.class, () -> XMLResourceSupport.read(file));
		assertTrue(exception.getMessage().contains("16 MiB")); //$NON-NLS-1$
	}

	@Test
	void rejectsMalformedTransformationOutput() {
		assertThrows(CoreException.class,
				() -> XMLResourceSupport.validateWellFormed("<plugin><extension></plugin>")); //$NON-NLS-1$
	}

	@Test
	void acceptsNamespacesCdataMixedContentAndXmlSpace() throws CoreException {
		XMLResourceSupport.validateWellFormed("""
				<?xml version="1.0" encoding="UTF-8"?>
				<root xmlns:x="urn:test" xml:space="preserve">before<x:item><![CDATA[a < b]]></x:item>after</root>
				""");
	}

	private static Object primitiveDefault(Class<?> type) {
		if (type == boolean.class) {
			return false;
		}
		if (type == byte.class) {
			return (byte) 0;
		}
		if (type == short.class) {
			return (short) 0;
		}
		if (type == int.class) {
			return 0;
		}
		if (type == long.class) {
			return 0L;
		}
		if (type == float.class) {
			return 0F;
		}
		if (type == double.class) {
			return 0D;
		}
		if (type == char.class) {
			return '\0';
		}
		return null;
	}

	private static final class FixedLengthInputStream extends InputStream {
		private int remaining;

		FixedLengthInputStream(int length) {
			remaining= length;
		}

		@Override
		public int read() {
			if (remaining == 0) {
				return -1;
			}
			remaining--;
			return 0;
		}

		@Override
		public int read(byte[] target, int offset, int length) throws IOException {
			if (remaining == 0) {
				return -1;
			}
			int count= Math.min(remaining, length);
			java.util.Arrays.fill(target, offset, offset + count, (byte) 0);
			remaining-= count;
			return count;
		}
	}
}
