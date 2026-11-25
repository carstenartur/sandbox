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
package org.sandbox.jdt.core.cleanupapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.internal.common.PdeManifestImportUpdater;
import org.sandbox.jdt.internal.common.PdeManifestImportUpdater.Exporter;

public class PdeManifestImportUpdaterTest {

	private static final String PARALLEL_PACKAGE= "org.junit.jupiter.api.parallel"; //$NON-NLS-1$

	@Test
	public void addsImportToExistingFoldedHeaderWithoutReformatting() {
		String manifest= """
				Manifest-Version: 1.0
				Bundle-SymbolicName: example
				Import-Package: org.junit.jupiter.api,
				 org.junit.jupiter.api.extension;version="[5.10,6.0)",
				 org.junit.jupiter.params

				"""; //$NON-NLS-1$
		String expected= """
				Manifest-Version: 1.0
				Bundle-SymbolicName: example
				Import-Package: org.junit.jupiter.api,
				 org.junit.jupiter.api.extension;version="[5.10,6.0)",
				 org.junit.jupiter.params,
				 org.junit.jupiter.api.parallel

				"""; //$NON-NLS-1$

		assertEquals(expected, PdeManifestImportUpdater.addImport(manifest, PARALLEL_PACKAGE));
	}

	@Test
	public void preservesCrLfAndAddsHeaderBeforeNamedSections() {
		String manifest= "Manifest-Version: 1.0\r\nBundle-SymbolicName: example\r\n\r\n" //$NON-NLS-1$
				+ "Name: section\r\nValue: retained\r\n"; //$NON-NLS-1$
		String expected= "Manifest-Version: 1.0\r\nBundle-SymbolicName: example\r\n" //$NON-NLS-1$
				+ "Import-Package: org.junit.jupiter.api.parallel\r\n\r\n" //$NON-NLS-1$
				+ "Name: section\r\nValue: retained\r\n"; //$NON-NLS-1$

		assertEquals(expected, PdeManifestImportUpdater.addImport(manifest, PARALLEL_PACKAGE));
	}

	@Test
	public void preservesTerminalLineDelimiterWhenAppendingHeader() {
		String manifest= """
				Manifest-Version: 1.0
				Bundle-SymbolicName: example
				"""; //$NON-NLS-1$
		String expected= manifest + "Import-Package: org.junit.jupiter.api.parallel\n"; //$NON-NLS-1$

		assertEquals(expected, PdeManifestImportUpdater.addImport(manifest, PARALLEL_PACKAGE));
	}

	@Test
	public void existingAttributedImportIsByteIdentical() {
		byte[] original= ("Manifest-Version: 1.0\n" //$NON-NLS-1$
				+ "Import-Package: org.junit.jupiter.api.parallel;version=\"[5.10,6.0)\"\n") //$NON-NLS-1$
						.getBytes(StandardCharsets.UTF_8);

		assertSame(original, PdeManifestImportUpdater.addImport(original, PARALLEL_PACKAGE));
	}

	@Test
	public void preservesLongExporterComponentsAndTheOriginalImport() throws IOException {
		String name= "9_-." + "a".repeat(2048) + ".Core-2"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		String version= "00001.02.3." + "A".repeat(2048) + "_-9"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		byte[] original= "Manifest-Version: 1.0\r\nImport-Package: existing.api;x=1\r\n\r\n".getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$
		var exporter= new Exporter(name, version);
		byte[] updated= PdeManifestImportUpdater.addImport(original, PARALLEL_PACKAGE, exporter);
		String header= new Manifest(new ByteArrayInputStream(updated)).getMainAttributes().getValue("Import-Package"); //$NON-NLS-1$
		assertEquals("existing.api;x=1," + PARALLEL_PACKAGE + ";bundle-symbolic-name=" + name //$NON-NLS-1$ //$NON-NLS-2$
				+ ";bundle-version=\"[" + version + "," + version + "]\"", header); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertSame(updated, PdeManifestImportUpdater.addImport(updated, PARALLEL_PACKAGE, exporter));
	}

	@Test
	public void rejectsInvalidExporterNameComponents() {
		for (String name : List.of("", ".api", "api.", "api..core", "unit,io", "café", "api\nmore")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
			assertThrows(IllegalArgumentException.class, () -> new Exporter(name, "0.0.0"), name); //$NON-NLS-1$
		}
	}

	@Test
	public void acceptsOnlyThreeNumericVersionComponentsAndAnOptionalQualifier() {
		for (String version : List.of("0.0.0", "000.01.02", "1.2.3.4", "1.2.3.-_A9")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			assertEquals(version, new Exporter("_", version).version()); //$NON-NLS-1$
		}
		for (String version : List.of("", "1.2", "1..3", "1.2.3.", "1.2.3.q.1", "1.2.a", "１.2.3", "1.2.3.q+1")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
			assertThrows(IllegalArgumentException.class, () -> new Exporter("_", version), version); //$NON-NLS-1$
		}
	}

	@Test
	public void rejectsManifestInjectionThroughThePackageName() {
		assertThrows(IllegalArgumentException.class,
				() -> PdeManifestImportUpdater.addImport("Manifest-Version: 1.0\n", //$NON-NLS-1$
						"org.junit.jupiter.api.parallel,evil")); //$NON-NLS-1$
	}
}
