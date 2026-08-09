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
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Repository contract for the real Cleanup preview Help page and screenshots. */
class CleanupPreviewHelpStructureTest {

	private static final String MULTIPLE_STEPS= "cleanup-preview-multiple-steps.png"; //$NON-NLS-1$
	private static final String MULTIPLE_FILES= "cleanup-preview-multiple-files.png"; //$NON-NLS-1$

	@Test
	void previewPageShipsAndReferencesBothGeneratedScreenshots() throws Exception {
		Path repository= repositoryRoot();
		Path help= repository.resolve("sandbox_jface_cleanup_help"); //$NON-NLS-1$
		Path page= help.resolve("html/preview.html"); //$NON-NLS-1$
		String content= Files.readString(page, StandardCharsets.UTF_8);

		assertTrue(Files.isRegularFile(page), () -> "Missing Help page " + page); //$NON-NLS-1$
		for (String image : new String[] { MULTIPLE_STEPS, MULTIPLE_FILES }) {
			Path screenshot= help.resolve("images").resolve(image); //$NON-NLS-1$
			assertTrue(Files.isRegularFile(screenshot), () -> "Missing preview screenshot " + screenshot); //$NON-NLS-1$
			assertTrue(content.contains("../images/" + image), //$NON-NLS-1$
					() -> "Preview Help does not reference " + image); //$NON-NLS-1$
		}

		String toc= Files.readString(help.resolve("toc.xml"), StandardCharsets.UTF_8); //$NON-NLS-1$
		assertTrue(toc.contains("html/preview.html"), //$NON-NLS-1$
				"JFace Help TOC does not include the real Cleanup preview page"); //$NON-NLS-1$
	}

	private static Path repositoryRoot() {
		String configured= System.getProperty("sandbox.repository.root"); //$NON-NLS-1$
		assertTrue(configured != null && !configured.isBlank(),
				"Missing -Dsandbox.repository.root"); //$NON-NLS-1$
		return Path.of(configured).toAbsolutePath().normalize();
	}
}
