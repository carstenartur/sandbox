/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sandbox.mining.core.astdiff.PlaceholderGeneralizer;
import org.sandbox.mining.core.astdiff.PlaceholderGeneralizer.GeneralizedPair;

/**
 * Tests for {@link PlaceholderGeneralizer}.
 */
class PlaceholderGeneralizerTest {

	private final PlaceholderGeneralizer generalizer = new PlaceholderGeneralizer();

	@Test
	void testGeneralizeSharedIdentifier() {
		GeneralizedPair result = generalizer.generalize(
				"new String(buf, \"UTF-8\")",
				"new String(buf, StandardCharsets.UTF_8)");

		assertNotNull(result);
		// "buf" appears in both sides and should be replaced with a placeholder
		assertFalse(result.placeholderMap().isEmpty());
		assertTrue(result.pattern().contains("$v"));
		assertTrue(result.replacement().contains("$v"));
	}

	@Test
	void testGeneralizeNullBefore() {
		GeneralizedPair result = generalizer.generalize(null, "after");
		assertNotNull(result);
		assertEquals("", result.pattern());
		assertEquals("after", result.replacement());
	}

	@Test
	void testGeneralizeNullAfter() {
		GeneralizedPair result = generalizer.generalize("before", null);
		assertNotNull(result);
		assertEquals("before", result.pattern());
		assertEquals("", result.replacement());
	}

	@Test
	void testGeneralizeNoSharedIdentifiers() {
		GeneralizedPair result = generalizer.generalize("abc()", "xyz()");
		assertNotNull(result);
		// "abc" and "xyz" don't appear in each other
		assertTrue(result.placeholderMap().isEmpty());
	}

	@Test
	void testGeneralizeStrings() {
		GeneralizedPair result = generalizer.generalizeStrings(
				"new String(data, \"UTF-8\")",
				"new String(data, StandardCharsets.UTF_8)");

		assertNotNull(result);
		// "UTF-8" literal should be replaced since it doesn't appear in after
		assertFalse(result.placeholderMap().isEmpty());
		assertTrue(result.pattern().contains("$s"));
	}

	@Test
	void testGeneralizeStringsNullBefore() {
		GeneralizedPair result = generalizer.generalizeStrings(null, "after");
		assertNotNull(result);
		assertEquals("", result.pattern());
	}

	@Test
	void testGeneralizeStringsSameString() {
		GeneralizedPair result = generalizer.generalizeStrings(
				"print(\"hello\")",
				"println(\"hello\")");
		// "hello" appears in both, so it should NOT be generalized
		assertTrue(result.placeholderMap().isEmpty());
	}

	@Test
	void testGeneralizeIntegers() {
		GeneralizedPair result = generalizer.generalizeIntegers(
				"buffer = new byte[1024];",
				"buffer = new byte[bufferSize];");

		assertNotNull(result);
		// 1024 doesn't appear in after
		assertFalse(result.placeholderMap().isEmpty());
		assertTrue(result.pattern().contains("$n"));
	}

	@Test
	void testGeneralizeIntegersNullBefore() {
		GeneralizedPair result = generalizer.generalizeIntegers(null, "after");
		assertNotNull(result);
		assertEquals("", result.pattern());
	}

	@Test
	void testGeneralizeKeywordsNotReplaced() {
		GeneralizedPair result = generalizer.generalize(
				"new String(data)",
				"new String(data, charset)");

		// "new" is a keyword and should not be generalized
		assertTrue(result.pattern().contains("new"));
		assertTrue(result.replacement().contains("new"));
	}

	@Test
	void testGeneralizeMultipleSharedIdentifiers() {
		GeneralizedPair result = generalizer.generalize(
				"stream.read(buffer, offset, length)",
				"stream.read(buffer, offset, maxLength)");

		assertNotNull(result);
		// "stream", "buffer", "offset" appear in both
		assertTrue(result.placeholderMap().size() >= 2);
	}
}
