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

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

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
	public void existingAttributedImportIsByteIdentical() {
		byte[] original= ("Manifest-Version: 1.0\n" //$NON-NLS-1$
				+ "Import-Package: org.junit.jupiter.api.parallel;version=\"[5.10,6.0)\"\n") //$NON-NLS-1$
						.getBytes(StandardCharsets.UTF_8);

		assertSame(original, PdeManifestImportUpdater.addImport(original, PARALLEL_PACKAGE));
	}

	@Test
	public void rejectsManifestInjectionThroughThePackageName() {
		assertThrows(IllegalArgumentException.class,
				() -> PdeManifestImportUpdater.addImport("Manifest-Version: 1.0\n", //$NON-NLS-1$
						"org.junit.jupiter.api.parallel,evil")); //$NON-NLS-1$
	}
}
