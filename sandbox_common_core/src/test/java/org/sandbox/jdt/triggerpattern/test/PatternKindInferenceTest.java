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

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

class PatternKindInferenceTest {

	@Test
	void nestedMethodCallsDoNotTurnOtherExpressionsIntoMethodCallPatterns() throws Exception {
		assertKind("$value.strip().length() == 0", PatternKind.EXPRESSION);
		assertKind("$optional.isPresent() ? $optional.get() : $default", PatternKind.EXPRESSION);
		assertKind("($value.isBlank())", PatternKind.EXPRESSION);
		assertKind("$value.isBlank()", PatternKind.METHOD_CALL);
	}

	@Test
	void arrayCreationIsAnExpressionRatherThanAClassConstructor() throws Exception {
		assertKind("new int[$size]", PatternKind.EXPRESSION);
		assertKind("new java.lang.String($bytes)", PatternKind.CONSTRUCTOR);
	}

	@Test
	void multipleStatementsAreASequenceEvenWhenTheyContainMethodCalls() throws Exception {
		assertKind("byte[] bytes = text.getBytes(\"UTF-8\");\nreturn bytes;", PatternKind.STATEMENT_SEQUENCE);
	}

	private void assertKind(String source, PatternKind expected) throws Exception {
		var hints = new HintFileParser().parse(source + "\n;;\n");
		assertEquals(1, hints.getRules().size());
		assertEquals(expected, hints.getRules().getFirst().sourcePattern().getKind());
	}
}
