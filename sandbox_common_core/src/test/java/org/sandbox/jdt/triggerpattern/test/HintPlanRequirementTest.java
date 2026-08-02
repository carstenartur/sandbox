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
import org.sandbox.jdt.triggerpattern.api.HintPlanRequirement;

/** Tests for declarative semantic-plan and binding contracts in hint programs. */
public class HintPlanRequirementTest {

	@Test
	public void readsRequiredPlanContract() {
		String content= """
				<!id: planned-rewrite>
				<!binding-policy: required>
				<!requires-plan: junit3-hierarchy>
				$x
				=> $y
				;;
				"""; //$NON-NLS-1$

		assertEquals("junit3-hierarchy", HintPlanRequirement.fromContent(content).orElseThrow()); //$NON-NLS-1$
		assertEquals(HintBindingPolicy.REQUIRED,
				HintBindingPolicy.fromContent(content).orElseThrow());
	}

	@Test
	public void ordinaryHintHasNoPlanRequirementOrExplicitBindingPolicy() {
		String content= "$x => $y"; //$NON-NLS-1$
		assertTrue(HintPlanRequirement.fromContent(content).isEmpty());
		assertTrue(HintBindingPolicy.fromContent(content).isEmpty());
	}

	@Test
	public void ordinaryHintMaySelectOptionalBindingCompatibility() {
		String content= """
				<!binding-policy: optional>
				$x => $y
				"""; //$NON-NLS-1$
		assertTrue(HintPlanRequirement.fromContent(content).isEmpty());
		assertEquals(HintBindingPolicy.OPTIONAL,
				HintBindingPolicy.fromContent(content).orElseThrow());
	}

	@Test
	public void repeatedIdenticalContractsAreStable() {
		String content= """
				<!binding-policy: required>
				<!binding-policy: required>
				<!requires-plan: closed-hierarchy>
				<!requires-plan: closed-hierarchy>
				"""; //$NON-NLS-1$

		assertEquals("closed-hierarchy", HintPlanRequirement.fromContent(content).orElseThrow()); //$NON-NLS-1$
		assertEquals(HintBindingPolicy.REQUIRED,
				HintBindingPolicy.fromContent(content).orElseThrow());
	}

	@Test
	public void commentedContractsDoNotAuthorizeAProgram() {
		String content= """
				// <!binding-policy: required>
				// <!requires-plan: line-comment>
				/*
				<!binding-policy: required>
				<!requires-plan: block-comment>
				*/
				$x
				=> $y
				;;
				"""; //$NON-NLS-1$

		assertTrue(HintPlanRequirement.fromContent(content).isEmpty());
		assertTrue(HintBindingPolicy.fromContent(content).isEmpty());
	}

	@Test
	public void rejectsPlanWithoutRequiredBindingPolicy() {
		assertThrows(IllegalArgumentException.class,
				() -> HintPlanRequirement.fromContent("<!requires-plan: demo>")); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class, () -> HintPlanRequirement.fromContent("""
				<!binding-policy: optional>
				<!requires-plan: demo>
				""")); //$NON-NLS-1$
	}

	@Test
	public void rejectsBlankMalformedUnknownOrConflictingBindingPolicies() {
		assertThrows(IllegalArgumentException.class,
				() -> HintBindingPolicy.fromContent("<!binding-policy: >")); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class,
				() -> HintBindingPolicy.fromContent("<!binding-policy = required>")); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class,
				() -> HintBindingPolicy.fromContent("<!binding-policy: best-effort>")); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class, () -> HintBindingPolicy.fromContent("""
				<!binding-policy: optional>
				<!binding-policy: required>
				""")); //$NON-NLS-1$
	}

	@Test
	public void rejectsBlankMalformedOrConflictingPlanContracts() {
		assertThrows(IllegalArgumentException.class,
				() -> HintPlanRequirement.fromContent("<!binding-policy: required>\n<!requires-plan: >")); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class,
				() -> HintPlanRequirement.fromContent("<!binding-policy: required>\n<!requires-plan = wrong-syntax>")); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class, () -> HintPlanRequirement.fromContent("""
				<!binding-policy: required>
				<!requires-plan: first>
				<!requires-plan: second>
				""")); //$NON-NLS-1$
	}
}
