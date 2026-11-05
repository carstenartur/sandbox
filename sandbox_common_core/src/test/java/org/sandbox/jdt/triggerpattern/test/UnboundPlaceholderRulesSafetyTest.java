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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Rules with incomplete mined context must not emit unbound placeholders. */
class UnboundPlaceholderRulesSafetyTest extends HintRuleTestSupport {

	@Test
	void recordFlagsCandidateIsHintOnly() throws Exception {
		assertHintOnly("eclipse-api-modernization.sandbox-hint", //$NON-NLS-1$
				"jdt.internal.ui.viewsupport.javaelementimageprovider.add-record-static-adornment"); //$NON-NLS-1$
	}

	@Test
	void foldingAssignmentCandidateIsHintOnly() throws Exception {
		assertHintOnly("jdt-internal-refactoring.sandbox-hint", //$NON-NLS-1$
				"jdt.internal.folding.includelastline.fix"); //$NON-NLS-1$
	}

	private void assertHintOnly(String file, String ruleId) throws Exception {
		var rule= loadBundledHint(file).getRules().stream()
				.filter(candidate -> ruleId.equals(candidate.getRuleId())).findFirst().orElseThrow();
		assertTrue(rule.isHintOnly(), "Unbound placeholders must not be emitted: " + ruleId); //$NON-NLS-1$
	}
}
