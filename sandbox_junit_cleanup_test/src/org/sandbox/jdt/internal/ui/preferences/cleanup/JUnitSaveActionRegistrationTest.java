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
package org.sandbox.jdt.internal.ui.preferences.cleanup;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;

import org.junit.jupiter.api.Test;

/** Guards the project-wide migration against accidental Save Actions exposure. */
public class JUnitSaveActionRegistrationTest {

	private static final String CLEANUP_EXTENSION_POINT= "org.eclipse.jdt.ui.cleanUps"; //$NON-NLS-1$
	private static final String BUNDLE_ID= "sandbox_junit_cleanup"; //$NON-NLS-1$

	@Test
	public void projectWideMigrationIsNotRegisteredAsSaveAction() {
		List<IConfigurationElement> saveActionElements= Arrays.stream(Platform.getExtensionRegistry()
				.getConfigurationElementsFor(CLEANUP_EXTENSION_POINT))
				.filter(element -> BUNDLE_ID.equals(element.getContributor().getName()))
				.filter(element -> "saveAction".equals(element.getAttribute("cleanUpKind"))) //$NON-NLS-1$ //$NON-NLS-2$
				.toList();

		assertTrue(saveActionElements.isEmpty(),
				"JUnit migration requires explicit project-wide preview and must not be offered as a Save Action"); //$NON-NLS-1$
	}
}
