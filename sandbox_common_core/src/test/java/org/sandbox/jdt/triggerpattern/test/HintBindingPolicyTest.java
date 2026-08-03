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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.triggerpattern.api.HintBindingPolicy;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;
import org.sandbox.jdt.triggerpattern.internal.HintProgramParser;

class HintBindingPolicyTest {

	@Test
	void readsRequiredAndIgnoresCommentedDirectives() {
		assertEquals(HintBindingPolicy.REQUIRED, HintBindingPolicy.fromContent("""
				// <!binding-policy: optional>
				/* <!binding-policy: optional> */
				<!binding-policy: required>
				""").orElseThrow());
	}

	@Test
	void toleratesDuplicateEqualDeclarations() {
		assertEquals(HintBindingPolicy.OPTIONAL, HintBindingPolicy.fromContent("""
				<!binding-policy: optional>
				<!binding-policy: OPTIONAL>
				""").orElseThrow());
	}

	@Test
	void rejectsMalformedUnknownAndConflictingDeclarations() {
		assertThrows(IllegalArgumentException.class,
				() -> HintBindingPolicy.fromContent("<!binding-policy>")); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class,
				() -> HintBindingPolicy.fromContent("<!binding-policy: sometimes>")); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class, () -> HintBindingPolicy.fromContent("""
				<!binding-policy: optional>
				<!binding-policy: required>
				"""));
	}

	@Test
	void planAwareProgramsNeedNoDuplicatePolicyButRejectOptional() throws Exception {
		assertTrue(new HintProgramParser().parseHintFile("""
				<!requires-plan: contract>
				void $method()
				=> void $method()
				;;
				""").getBindingPolicy() == null);

		assertThrows(HintParseException.class, () -> new HintProgramParser().parseHintFile("""
				<!requires-plan: contract>
				<!binding-policy: optional>
				void $method()
				=> void $method()
				;;
				"""));
	}
}
