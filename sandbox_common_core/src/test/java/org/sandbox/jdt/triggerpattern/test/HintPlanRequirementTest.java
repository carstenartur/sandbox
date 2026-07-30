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

import org.sandbox.jdt.triggerpattern.api.HintPlanRequirement;

/** Tests for declarative semantic-plan contracts in hint programs. */
public class HintPlanRequirementTest {

	@Test
	public void readsRequiredPlanContract() {
		String content= """
				<!id: planned-rewrite>
				<!requires-plan: junit3-hierarchy>
				$x
				=> $y
				;;
				"""; //$NON-NLS-1$

		assertEquals("junit3-hierarchy", HintPlanRequirement.fromContent(content).orElseThrow()); //$NON-NLS-1$
	}

	@Test
	public void ordinaryHintHasNoPlanRequirement() {
		assertTrue(HintPlanRequirement.fromContent("$x => $y").isEmpty()); //$NON-NLS-1$
	}

	@Test
	public void repeatedIdenticalContractIsStable() {
		String content= """
				<!requires-plan: closed-hierarchy>
				<!requires-plan: closed-hierarchy>
				"""; //$NON-NLS-1$

		assertEquals("closed-hierarchy", HintPlanRequirement.fromContent(content).orElseThrow()); //$NON-NLS-1$
	}

	@Test
	public void commentedContractsDoNotAuthorizeAProgram() {
		String content= """
				// <!requires-plan: line-comment>
				/*
				<!requires-plan: block-comment>
				*/
				$x
				=> $y
				;;
				"""; //$NON-NLS-1$

		assertTrue(HintPlanRequirement.fromContent(content).isEmpty());
	}

	@Test
	public void rejectsBlankMalformedOrConflictingContracts() {
		assertThrows(IllegalArgumentException.class,
				() -> HintPlanRequirement.fromContent("<!requires-plan: >")); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class,
				() -> HintPlanRequirement.fromContent("<!requires-plan = wrong-syntax>")); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class, () -> HintPlanRequirement.fromContent("""
				<!requires-plan: first>
				<!requires-plan: second>
				""")); //$NON-NLS-1$
	}
}