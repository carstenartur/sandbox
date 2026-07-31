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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

class HintPredicateContractTest {

	@Test
	void rejectsImplicitPlaceholderCapture() {
		HintParseException failure= assertThrows(HintParseException.class,
				() -> new HintProgramParser().parse("""
						<!id: hidden-capture>
						<!predicate exact($method): isPublic($method) && paramCount($other, 0)>
						foo($node) :: exact($node)
						=> bar($node)
						;;
						"""));

		assertTrue(failure.getMessage().contains("undeclared placeholder references [$other]")); //$NON-NLS-1$
	}

	@Test
	void rejectsUnusedParameters() {
		HintParseException failure= assertThrows(HintParseException.class,
				() -> new HintProgramParser().parse("""
						<!id: unused>
						<!predicate exact($method, $unused): isPublic($method)>
						foo($node) :: exact($node, $node)
						=> bar($node)
						;;
						"""));

		assertTrue(failure.getMessage().contains("unused parameters [$unused]")); //$NON-NLS-1$
	}

	@Test
	void rejectsPredicateThatShadowsBuiltInGuard() {
		HintParseException failure= assertThrows(HintParseException.class,
				() -> new HintProgramParser().parse("""
						<!id: shadowing>
						<!predicate isPublic($method): matchesAny($method)>
						foo($node) :: isPublic($node)
						=> bar($node)
						;;
						"""));

		assertEquals(2, failure.getLineNumber());
		assertTrue(failure.getMessage().contains("shadows built-in guard isPublic")); //$NON-NLS-1$
	}
}
