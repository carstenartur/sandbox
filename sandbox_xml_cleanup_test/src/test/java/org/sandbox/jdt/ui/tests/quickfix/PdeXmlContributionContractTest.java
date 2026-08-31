/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Keeps the shipped PDE XML entry points and Eclipse Help on one truthful
 * contract.
 */
public class PdeXmlContributionContractTest {

	private static final String COMMAND_ID = "org.sandbox.jdt.xml.cleanup.command"; //$NON-NLS-1$

	@Test
	public void pluginContributesTheDocumentedExplicitCommands() throws Exception {
		Path root = repositoryRoot();
		String plugin = read(root, "sandbox_xml_cleanup/plugin.xml"); //$NON-NLS-1$

		assertTrue(plugin.contains("id=\"" + COMMAND_ID + "\""), //$NON-NLS-1$ //$NON-NLS-2$
				"The PDE XML command must remain registered"); //$NON-NLS-1$
		assertTrue(plugin.contains("commandId=\"" + COMMAND_ID + "\""), //$NON-NLS-1$ //$NON-NLS-2$
				"The registered handler and menus must invoke the PDE XML command"); //$NON-NLS-1$
		assertTrue(plugin.contains("label=\"Clean Up PDE XML\""), //$NON-NLS-1$
				"The selected-resource context command must remain available"); //$NON-NLS-1$
		assertTrue(plugin.contains("label=\"XML Cleanup\""), //$NON-NLS-1$
				"The main-menu category must remain available"); //$NON-NLS-1$
		assertTrue(plugin.contains("label=\"Clean Up PDE XML Files\""), //$NON-NLS-1$
				"The main-menu command must remain available"); //$NON-NLS-1$
	}

	@Test
	public void pluginDoesNotAdvertiseAnUnproducibleMarkerQuickFix() throws Exception {
		Path root = repositoryRoot();
		String plugin = read(root, "sandbox_xml_cleanup/plugin.xml"); //$NON-NLS-1$

		assertFalse(plugin.contains("org.eclipse.core.resources.markers"), //$NON-NLS-1$
				"The bundle must not declare a marker type that no analyzer creates"); //$NON-NLS-1$
		assertFalse(plugin.contains("org.eclipse.ui.ide.markerResolution"), //$NON-NLS-1$
				"The bundle must not register an unreachable marker resolution"); //$NON-NLS-1$
		assertFalse(plugin.contains("my.exsd.cleanup.marker"), //$NON-NLS-1$
				"The obsolete private marker id must not remain in the runtime contract"); //$NON-NLS-1$
		assertFalse(Files.exists(root.resolve(
				"sandbox_xml_cleanup/src/org/sandbox/jdt/internal/corext/fix/helper/ExsdMarkerResolutionGenerator.java"))); //$NON-NLS-1$
		assertFalse(Files.exists(root.resolve(
				"sandbox_xml_cleanup/src/org/sandbox/jdt/internal/corext/fix/helper/ReplaceSpacesWithTabsQuickFix.java"))); //$NON-NLS-1$
	}

	@Test
	public void helpNamesEverySupportedEntryPointAndTheMarkerBoundary() throws Exception {
		Path root = repositoryRoot();
		String usage = read(root, "sandbox_xml_cleanup_help/html/usage.html"); //$NON-NLS-1$
		String reference = read(root, "sandbox_xml_cleanup_help/html/reference.html"); //$NON-NLS-1$

		assertTrue(usage.contains("XML Cleanup (Sandbox)"), //$NON-NLS-1$
				"Help must name the cleanup-profile entry point"); //$NON-NLS-1$
		assertTrue(usage.contains("Clean Up PDE XML</strong>"), //$NON-NLS-1$
				"Help must name the selected-resource context command"); //$NON-NLS-1$
		assertTrue(usage.contains("XML Cleanup &gt; Clean Up PDE XML Files"), //$NON-NLS-1$
				"Help must name the main-menu path"); //$NON-NLS-1$
		assertTrue(usage.contains("does not create background problem markers"), //$NON-NLS-1$
				"Help must not imply an automatic marker producer"); //$NON-NLS-1$
		assertTrue(reference.contains("does not provide a problem-marker quick fix"), //$NON-NLS-1$
				"Reference Help must state the marker-resolution boundary"); //$NON-NLS-1$
	}

	private static Path repositoryRoot() {
		Path candidate = Path.of("").toAbsolutePath().normalize(); //$NON-NLS-1$
		while (candidate != null) {
			if (Files.isRegularFile(candidate.resolve("pom.xml")) //$NON-NLS-1$
					&& Files.isRegularFile(candidate.resolve("sandbox_xml_cleanup/plugin.xml"))) { //$NON-NLS-1$
				return candidate;
			}
			candidate = candidate.getParent();
		}
		throw new IllegalStateException("Could not locate the Sandbox repository root"); //$NON-NLS-1$
	}

	private static String read(Path root, String relativePath) throws IOException {
		return Files.readString(root.resolve(relativePath), StandardCharsets.UTF_8);
	}
}
