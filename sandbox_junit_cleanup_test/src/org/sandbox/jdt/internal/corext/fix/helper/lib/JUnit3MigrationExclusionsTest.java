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
package org.sandbox.jdt.internal.corext.fix.helper.lib;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Contract of the configurable JUnit 3 migration exclusion list. */
class JUnit3MigrationExclusionsTest {

	@AfterEach
	void clearProperty() {
		System.clearProperty(JUnit3MigrationExclusions.EXCLUDED_SUPERTYPES_PROPERTY);
	}

	@Test
	void excludesPerformanceAndDecoratorBaseTypesByDefault() {
		Set<String> excluded= JUnit3MigrationExclusions.excludedSuperTypes();
		assertTrue(excluded.contains("org.eclipse.test.performance.PerformanceTestCase")); //$NON-NLS-1$
		assertTrue(excluded.contains("junit.extensions.TestSetup")); //$NON-NLS-1$
		assertTrue(excluded.contains("junit.framework.TestSuite")); //$NON-NLS-1$
		assertFalse(excluded.contains("junit.framework.TestCase")); //$NON-NLS-1$
	}

	@Test
	void replacesDefaultsWithoutLeadingPlus() {
		System.setProperty(JUnit3MigrationExclusions.EXCLUDED_SUPERTYPES_PROPERTY, "com.example.Base"); //$NON-NLS-1$
		Set<String> excluded= JUnit3MigrationExclusions.excludedSuperTypes();
		assertTrue(excluded.contains("com.example.Base")); //$NON-NLS-1$
		assertFalse(excluded.contains("junit.extensions.TestSetup")); //$NON-NLS-1$
	}

	@Test
	void keepsDefaultsWithLeadingPlus() {
		System.setProperty(JUnit3MigrationExclusions.EXCLUDED_SUPERTYPES_PROPERTY,
				"+ com.example.Base , com.example.Other"); //$NON-NLS-1$
		Set<String> excluded= JUnit3MigrationExclusions.excludedSuperTypes();
		assertTrue(excluded.contains("com.example.Base")); //$NON-NLS-1$
		assertTrue(excluded.contains("com.example.Other")); //$NON-NLS-1$
		assertTrue(excluded.contains("junit.extensions.TestSetup")); //$NON-NLS-1$
	}

	@Test
	void treatsMissingBindingAsNotExcluded() {
		assertFalse(JUnit3MigrationExclusions.isExcluded(null));
		assertNull(JUnit3MigrationExclusions.excludedSuperType(null));
	}
}
