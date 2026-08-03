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
package org.sandbox.jdt.triggerpattern.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.triggerpattern.api.HintBindingPolicy;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/** Ensures every low-level loading path preserves the binding safety contract. */
class HintFileParserBindingPolicyTest {

	@Test
	void parsesRequiredPolicyThroughTheSharedLowLevelParser() throws Exception {
		assertEquals(HintBindingPolicy.REQUIRED, new HintFileParser().parse("""
				<!binding-policy: required>
				$x.toString()
				;;
				""").getBindingPolicy());
	}

	@Test
	void acceptsEqualDuplicatesAndRejectsConflicts() throws Exception {
		assertEquals(HintBindingPolicy.OPTIONAL, new HintFileParser().parse("""
				<!binding-policy: optional>
				<!binding-policy: OPTIONAL>
				$x.toString()
				;;
				""").getBindingPolicy());

		assertThrows(HintParseException.class, () -> new HintFileParser().parse("""
				<!binding-policy: optional>
				<!binding-policy: required>
				$x.toString()
				;;
				"""));
	}

	@Test
	void rejectsUnknownPolicyValuesAndPreservesTheCause() {
		HintParseException failure= assertThrows(HintParseException.class,
				() -> new HintFileParser().parse("""
						<!binding-policy: best-effort>
						$x.toString()
						;;
						"""));
		assertInstanceOf(IllegalArgumentException.class, failure.getCause());
	}
}
