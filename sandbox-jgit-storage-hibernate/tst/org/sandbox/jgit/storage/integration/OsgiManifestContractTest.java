/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jgit.storage.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

/** Contract test for the generated OSGi metadata of the consumer bridge. */
class OsgiManifestContractTest {

	@Test
	void exportsSandboxBoundaryAndImportsReleasedCoreApi() throws IOException {
		Path manifestPath= Path.of("target", "classes", "META-INF", "MANIFEST.MF"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		assertTrue(Files.isRegularFile(manifestPath),
				() -> "Generated OSGi manifest is missing: " + manifestPath.toAbsolutePath()); //$NON-NLS-1$

		Manifest manifest;
		try (InputStream input= Files.newInputStream(manifestPath)) {
			manifest= new Manifest(input);
		}
		Attributes attributes= manifest.getMainAttributes();

		assertEquals("sandbox-jgit-storage-hibernate", //$NON-NLS-1$
				attributes.getValue("Bundle-SymbolicName")); //$NON-NLS-1$

		String exports= attributes.getValue("Export-Package"); //$NON-NLS-1$
		assertNotNull(exports, "The bridge must declare its exported OSGi API"); //$NON-NLS-1$
		assertTrue(exports.contains("org.sandbox.jgit.storage.integration"), //$NON-NLS-1$
				() -> "Sandbox integration boundary is not exported: " + exports); //$NON-NLS-1$

		String imports= attributes.getValue("Import-Package"); //$NON-NLS-1$
		assertNotNull(imports, "The bridge must declare its imported OSGi packages"); //$NON-NLS-1$
		assertTrue(imports.contains("io.github.carstenartur.jgit.storage.hibernate"), //$NON-NLS-1$
				() -> "Released Core API packages are absent from Import-Package: " + imports); //$NON-NLS-1$
	}
}
