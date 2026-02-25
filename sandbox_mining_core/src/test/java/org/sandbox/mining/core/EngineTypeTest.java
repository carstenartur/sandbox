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
package org.sandbox.mining.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.sandbox.mining.core.engine.EngineType;

/**
 * Tests for {@link EngineType}.
 */
class EngineTypeTest {

	@Test
	void testFromStringLlm() {
		assertEquals(EngineType.LLM, EngineType.fromString("llm")); //$NON-NLS-1$
	}

	@Test
	void testFromStringUppercase() {
		assertEquals(EngineType.LLM, EngineType.fromString("LLM")); //$NON-NLS-1$
	}

	@Test
	void testFromStringMixedCase() {
		assertEquals(EngineType.LLM, EngineType.fromString("Llm")); //$NON-NLS-1$
	}

	@Test
	void testFromStringWithWhitespace() {
		assertEquals(EngineType.LLM, EngineType.fromString("  llm  ")); //$NON-NLS-1$
	}

	@Test
	void testFromStringNull() {
		assertThrows(IllegalArgumentException.class, () -> EngineType.fromString(null));
	}

	@Test
	void testFromStringUnknown() {
		assertThrows(IllegalArgumentException.class, () -> EngineType.fromString("unknown")); //$NON-NLS-1$
	}
}
