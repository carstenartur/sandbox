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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.cleanup.ExceptionCleanupHelper;

/**
 * Unit tests for {@link ExceptionCleanupHelper}.
 *
 * <p>These tests verify the public API of the helper. The
 * {@code removeCheckedException} method requires full AST rewrite
 * infrastructure and is tested indirectly through the encoding cleanup
 * integration tests. Here we verify that the helper gracefully handles
 * edge cases via its public contract.</p>
 */
@DisplayName("ExceptionCleanupHelper Tests")
public class ExceptionCleanupHelperTest {

	@Test
	@DisplayName("removeCheckedException handles null visited node gracefully")
	void testRemoveCheckedExceptionNullVisited() {
		// Calling with a null visited node should not throw — it should simply return
		assertDoesNotThrow(() -> ExceptionCleanupHelper.removeCheckedException(
				null,
				"java.io.UnsupportedEncodingException", //$NON-NLS-1$
				"UnsupportedEncodingException", //$NON-NLS-1$
				null, null, null));
	}

	@Test
	@DisplayName("ExceptionCleanupHelper class is not instantiable")
	void testNotInstantiable() {
		// The class has a private constructor — verify it cannot be instantiated
		var constructors = ExceptionCleanupHelper.class.getDeclaredConstructors();
		assertEquals(1, constructors.length);
		assertFalse(constructors[0].canAccess(null));
	}
}
