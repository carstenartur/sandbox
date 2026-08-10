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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.junit.jupiter.api.Test;

/** Verifies that the upstreamed iterator cleanup is not registered twice. */
public class ToolsCleanupRegistrationTest {

	private static final String CLEANUP_EXTENSION_POINT= "org.eclipse.jdt.ui.cleanUps"; //$NON-NLS-1$
	private static final String CLEANUP_ELEMENT= "cleanUp"; //$NON-NLS-1$
	private static final String ITERATOR_CLEANUP_ID= "org.eclipse.jdt.ui.cleanup.toolscleanup"; //$NON-NLS-1$
	private static final String JDT_UI_BUNDLE= "org.eclipse.jdt.ui"; //$NON-NLS-1$
	private static final String SANDBOX_TOOLS_BUNDLE= "sandbox_tools"; //$NON-NLS-1$

	@Test
	public void testOnlyJdtUiContributesUpstreamedIteratorCleanup() {
		IExtensionRegistry registry= Platform.getExtensionRegistry();
		assertNotNull(registry,
				"This contract test requires the Eclipse OSGi extension registry"); //$NON-NLS-1$

		List<IConfigurationElement> contributions= Arrays.stream(
				registry.getConfigurationElementsFor(CLEANUP_EXTENSION_POINT))
				.filter(element -> CLEANUP_ELEMENT.equals(element.getName()))
				.filter(element -> ITERATOR_CLEANUP_ID.equals(element.getAttribute("id"))) //$NON-NLS-1$
				.toList();

		assertEquals(1, contributions.size(),
				() -> "Expected exactly one active contribution for " + ITERATOR_CLEANUP_ID //$NON-NLS-1$
						+ ", but found " + contributors(contributions)); //$NON-NLS-1$
		assertEquals(JDT_UI_BUNDLE, contributions.getFirst().getContributor().getName(),
				"The maintained Eclipse JDT UI implementation must own the cleanup id"); //$NON-NLS-1$
		assertFalse(contributions.stream()
				.anyMatch(element -> SANDBOX_TOOLS_BUNDLE.equals(element.getContributor().getName())),
				"The Sandbox reference bundle must not register a second cleanup implementation"); //$NON-NLS-1$
	}

	private static List<String> contributors(List<IConfigurationElement> contributions) {
		return contributions.stream()
				.map(element -> element.getContributor().getName())
				.toList();
	}
}
