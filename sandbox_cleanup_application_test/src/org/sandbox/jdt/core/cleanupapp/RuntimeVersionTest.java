package org.sandbox.jdt.core.cleanupapp;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;

/** Verifies that reports identify the installed cleanup bundle rather than an obsolete snapshot. */
public class RuntimeVersionTest {

	private static final String VERSION_PROPERTY = "sandbox.cleanup.version"; //$NON-NLS-1$

	private String originalVersion;

	@BeforeEach
	public void rememberVersionOverride() {
		originalVersion = System.getProperty(VERSION_PROPERTY);
	}

	@AfterEach
	public void restoreVersionOverride() {
		if (originalVersion == null) {
			System.clearProperty(VERSION_PROPERTY);
		} else {
			System.setProperty(VERSION_PROPERTY, originalVersion);
		}
	}

	@Test
	public void reportsOwningBundleBaseVersionByDefault() {
		System.clearProperty(VERSION_PROPERTY);
		Bundle bundle = FrameworkUtil.getBundle(CodeCleanupApplication.class);
		assertNotNull(bundle, "The cleanup application must run from its OSGi bundle");
		Version version = bundle.getVersion();
		String expected = "%d.%d.%d".formatted(version.getMajor(), version.getMinor(), version.getMicro()); //$NON-NLS-1$

		String actual = CodeCleanupApplication.getToolVersion();

		assertEquals(expected, actual);
		assertFalse(actual.toUpperCase().contains("SNAPSHOT")); //$NON-NLS-1$
	}

	@Test
	public void honorsExplicitVersionOverride() {
		System.setProperty(VERSION_PROPERTY, "test-version"); //$NON-NLS-1$

		assertEquals("test-version", CodeCleanupApplication.getToolVersion()); //$NON-NLS-1$
	}
}
