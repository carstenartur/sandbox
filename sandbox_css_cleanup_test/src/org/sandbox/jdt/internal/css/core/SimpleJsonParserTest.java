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
package org.sandbox.jdt.internal.css.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Regression tests for the dependency-free JSON subset used by CSS tooling. */
public class SimpleJsonParserTest {

	@Test
	public void parsesNestedObjectsArraysEscapesAndNumbers() {
		Map<String, Object> parsed = SimpleJsonParser.parseObject(
				"{\"enabled\":true,\"width\":1.25e+2,\"items\":[\"a\\n b\",null,{\"n\":-3}]}" ); //$NON-NLS-1$

		assertEquals(Boolean.TRUE, parsed.get("enabled")); //$NON-NLS-1$
		assertEquals(new BigDecimal("1.25e+2"), parsed.get("width")); //$NON-NLS-1$ //$NON-NLS-2$
		List<?> items = assertInstanceOf(List.class, parsed.get("items")); //$NON-NLS-1$
		assertEquals("a\n b", items.get(0)); //$NON-NLS-1$
		assertEquals(null, items.get(1));
		Map<?, ?> nested = assertInstanceOf(Map.class, items.get(2));
		assertEquals(new BigDecimal("-3"), nested.get("n")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void acceptsWhitespaceAroundOneCompleteValue() {
		assertTrue(SimpleJsonParser.parseObject("  { \"x\" : 1 } \n\t").containsKey("x")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void rejectsTrailingContentAndMalformedStructures() {
		assertThrows(IllegalArgumentException.class,
				() -> SimpleJsonParser.parse("{} true")); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class,
				() -> SimpleJsonParser.parse("{\"x\":}")); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class,
				() -> SimpleJsonParser.parse("[1,]")); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class,
				() -> SimpleJsonParser.parse("1e+-2")); //$NON-NLS-1$
	}

	@Test
	public void parseObjectRejectsAValidNonObjectJsonValue() {
		assertThrows(IllegalArgumentException.class,
				() -> SimpleJsonParser.parseObject("[1,2,3]")); //$NON-NLS-1$
	}
}
