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
package org.sandbox.jdt.internal.corext.fix.multifile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.cleanup.multifile.CleanUpImpact;

class IntEnumCompatibilityModeTest {

	@Test
	void onlyClosedSourceModeIsCurrentlyImplemented() {
		assertTrue(IntEnumCompatibilityMode.CLOSED_SOURCE.implemented());
		assertEquals(CleanUpImpact.PROJECT_CLOSED, IntEnumCompatibilityMode.CLOSED_SOURCE.impact());
		assertFalse(IntEnumCompatibilityMode.CLOSED_SOURCE.explicitNumericValueRequired());

		assertFalse(IntEnumCompatibilityMode.NUMERIC_ADAPTER.implemented());
		assertEquals(CleanUpImpact.COMPATIBILITY_MANAGED, IntEnumCompatibilityMode.NUMERIC_ADAPTER.impact());
		assertTrue(IntEnumCompatibilityMode.NUMERIC_ADAPTER.explicitNumericValueRequired());

		assertFalse(IntEnumCompatibilityMode.MANUAL_EXTERNAL.implemented());
		assertEquals(CleanUpImpact.MANUAL_REFACTORING, IntEnumCompatibilityMode.MANUAL_EXTERNAL.impact());
	}

	@Test
	void ordinalIsNeverAnExternalIdentity() {
		for (IntEnumCompatibilityMode mode : IntEnumCompatibilityMode.values()) {
			assertFalse(mode.ordinalAllowedForExternalIdentity(), mode.name());
		}
	}

	@Test
	void everyModeProvidesPreviewLanguage() {
		for (IntEnumCompatibilityMode mode : IntEnumCompatibilityMode.values()) {
			assertFalse(mode.previewStatement().isBlank(), mode.name());
		}
	}
}
