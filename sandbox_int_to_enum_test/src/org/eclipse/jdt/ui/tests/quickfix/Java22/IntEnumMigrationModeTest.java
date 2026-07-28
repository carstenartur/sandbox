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
package org.eclipse.jdt.ui.tests.quickfix.Java22;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.cleanup.multifile.CleanUpImpact;
import org.sandbox.jdt.internal.corext.fix.multifile.IntEnumMigrationMode;

/** Compatibility-contract tests for integer-domain migration modes. */
public class IntEnumMigrationModeTest {

	@Test
	public void automaticModeIsClosedProjectFlowOnly() {
		IntEnumMigrationMode mode= IntEnumMigrationMode.automaticMode();

		assertEquals(IntEnumMigrationMode.CLOSED_FLOW_AUTOMATIC, mode);
		assertEquals(CleanUpImpact.PROJECT_CLOSED, mode.impact());
		assertTrue(mode.implemented());
		assertFalse(mode.explicitNumericIdentityRequired());
		assertTrue(mode.previewStatement().contains("atomically")); //$NON-NLS-1$
	}

	@Test
	public void publicNumericCompatibilityModeIsExplicitAndNotYetExecutable() {
		IntEnumMigrationMode mode= IntEnumMigrationMode.NUMERIC_ADAPTER_OPT_IN;

		assertEquals(CleanUpImpact.COMPATIBILITY_MANAGED, mode.impact());
		assertFalse(mode.implemented());
		assertTrue(mode.explicitNumericIdentityRequired());
		assertTrue(mode.previewStatement().contains("fromValue")); //$NON-NLS-1$
	}

	@Test
	public void ordinalIsNeverAnExternalIdentity() {
		for (IntEnumMigrationMode mode : IntEnumMigrationMode.values()) {
			assertFalse(mode.ordinalAllowedForExternalIdentity(), mode.name());
		}
	}
}
