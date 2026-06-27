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
package org.sandbox.mining.core.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MiningBudget} and {@link BudgetProfile}.
 */
class MiningBudgetTest {

	@Test
	void testFreeProfileDefaultsAreConservative() {
		MiningBudget budget = MiningBudget.from(BudgetProfile.FREE, 0, 0);

		assertEquals(50, budget.getMaxRequests());
		assertEquals(100, budget.getMaxCommits());
		assertEquals(1, BudgetProfile.FREE.recommendedCommitsPerRequest());
	}

	@Test
	void testExplicitLimitsOverrideProfileDefaults() {
		MiningBudget budget = MiningBudget.from(BudgetProfile.FREE, 7, 13);

		assertEquals(7, budget.getMaxRequests());
		assertEquals(13, budget.getMaxCommits());
	}

	@Test
	void testAllowedCommitCountIsClampedByRemainingBudget() {
		MiningBudget budget = new MiningBudget(10, 5);
		budget.recordRequest(3);

		assertEquals(2, budget.allowedCommitsForNextRequest(4));
	}

	@Test
	void testRequestLimitStopsFurtherRequests() {
		MiningBudget budget = new MiningBudget(1, 0);
		assertTrue(budget.hasRequestCapacity());

		budget.recordRequest(4);

		assertFalse(budget.hasRequestCapacity());
		assertEquals(0, budget.allowedCommitsForNextRequest(1));
		assertTrue(budget.isExhausted());
	}

	@Test
	void testUnlimitedBudgetDoesNotClamp() {
		MiningBudget budget = new MiningBudget(0, 0);

		assertEquals(100, budget.allowedCommitsForNextRequest(100));
		budget.recordRequest(100);
		assertFalse(budget.isExhausted());
	}

	@Test
	void testBudgetProfileParsingAcceptsHyphenatedNames() {
		assertEquals(BudgetProfile.FREE, BudgetProfile.parse("free")); //$NON-NLS-1$
		assertEquals(BudgetProfile.BALANCED, BudgetProfile.parse("balanced")); //$NON-NLS-1$
		assertEquals(BudgetProfile.THOROUGH, BudgetProfile.parse("thorough")); //$NON-NLS-1$
	}

	@Test
	void testNegativeLimitsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new MiningBudget(-1, 0));
		assertThrows(IllegalArgumentException.class, () -> new MiningBudget(0, -1));
	}
}
