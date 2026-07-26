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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.HintFile;

/** Behavior tests for the promoted boolean conditional simplification. */
class BooleanConditionalSimplificationHintTest extends HintRuleTestSupport {

	private HintFile hintFile;

	@BeforeEach
	void setUp() throws Exception {
		registerBuiltInGuards();
		hintFile = loadBundledHint("misc.sandbox-hint"); //$NON-NLS-1$
	}

	@Test
	void simplifiesConditionalWithLiteralBooleanBranches() {
		String before = "class Test { boolean m(int value) { return value > 10 ? true : false; } }"; //$NON-NLS-1$
		String expected = "class Test { boolean m(int value) { return value > 10; } }"; //$NON-NLS-1$

		assertFullReplacement(hintFile, before, expected);
		assertEquals("logical.simplification.boolean-conditional", //$NON-NLS-1$
				process(hintFile, before).get(0).rule().getRuleId());
	}

	@Test
	void doesNotRewriteNegatedConditionalOrNonLiteralBranches() {
		assertNoMatch(hintFile,
				"class Test { boolean m(boolean value) { return value ? false : true; } }"); //$NON-NLS-1$
		assertNoMatch(hintFile,
				"class Test { boolean m(boolean value, boolean fallback) { return value ? true : fallback; } }"); //$NON-NLS-1$
	}
}
