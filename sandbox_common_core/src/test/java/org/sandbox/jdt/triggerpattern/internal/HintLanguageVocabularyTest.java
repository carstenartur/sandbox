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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.RewriteActionCatalog;

class HintLanguageVocabularyTest {

	@Test
	void directiveAndOperatorVocabularyIsUniqueAndIncludesCompositionSyntax() {
		assertEquals(HintLanguageVocabulary.directives().size(),
				HintLanguageVocabulary.directiveNames().size());
		assertTrue(HintLanguageVocabulary.directiveNames().contains("predicate")); //$NON-NLS-1$
		assertTrue(HintLanguageVocabulary.directiveNames().contains("requires-plan")); //$NON-NLS-1$
		assertEquals(HintLanguageVocabulary.operators().size(),
				HintLanguageVocabulary.operators().stream().distinct().count());
		assertTrue(HintLanguageVocabulary.operators().containsAll(
				java.util.List.of("=>!", "=>", "::", ";;", "&&", "||", "!"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
	}

	@Test
	void builtInGuardVocabularyIsDerivedFromTheExecutableRegistration() {
		Map<String, GuardFunction> guards= new LinkedHashMap<>();
		BuiltInGuardRegistration.registerAll(guards);

		assertEquals(guards.keySet(), HintLanguageVocabulary.builtInGuardNames());
		for (String name : guards.keySet()) {
			assertFalse(HintLanguageVocabulary.guardDescription(name).isBlank(), name);
		}
	}

	@Test
	void actionVocabularyIsDerivedFromTheStandardSchemaCatalog() {
		assertEquals(RewriteActionCatalog.standard().names(), HintLanguageVocabulary.actionNames());
		assertEquals(HintLanguageVocabulary.actions().size(), HintLanguageVocabulary.actionNames().size());
		for (HintLanguageVocabulary.Action action : HintLanguageVocabulary.actions()) {
			assertTrue(action.syntax().startsWith("=>! " + action.name() + "(")); //$NON-NLS-1$ //$NON-NLS-2$
			assertTrue(action.replacement().startsWith(action.name() + "(")); //$NON-NLS-1$
			assertFalse(action.description().isBlank());
		}
	}
}
