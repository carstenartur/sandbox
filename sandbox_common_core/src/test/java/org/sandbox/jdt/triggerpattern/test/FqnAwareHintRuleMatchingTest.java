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
package org.sandbox.jdt.triggerpattern.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.HintFile;

/**
 * Documents the intended FQN-aware DSL style.
 *
 * <p>Rules should normally express concrete API identities with fully qualified
 * names. The matcher is responsible for accepting target code that uses simple
 * names plus imports. This keeps the DSL elegant and avoids duplicating rules
 * for FQN and imported-source variants.</p>
 */
class FqnAwareHintRuleMatchingTest extends HintRuleTestSupport {

	@BeforeEach
	void setUp() {
		registerBuiltInGuards();
	}

	@Test
	void fqnMethodReceiverMatchesImportedSimpleName() throws Exception {
		HintFile hintFile = parseHint("""
				<!id: fqn-method-receiver>

				java.util.Collections.emptyList()
				=> java.util.List.of()
				;;
				"""); //$NON-NLS-1$

		assertFullReplacement(hintFile,
				"""
				import java.util.Collections;
				class Test { Object m() { return Collections.emptyList(); } }
				""", //$NON-NLS-1$
				"""
				import java.util.Collections;
				class Test { Object m() { return java.util.List.of(); } }
				"""); //$NON-NLS-1$
	}

	@Test
	void fqnMethodReceiverDoesNotMatchSameSimpleNameFromDifferentImport() throws Exception {
		HintFile hintFile = parseHint("""
				<!id: fqn-method-receiver-negative>

				java.util.Collections.emptyList()
				=> java.util.List.of()
				;;
				"""); //$NON-NLS-1$

		assertNoMatch(hintFile,
				"""
				import com.example.Collections;
				class Test { Object m() { return Collections.emptyList(); } }
				"""); //$NON-NLS-1$
	}

	@Test
	void fqnConstructorMatchesImportedSimpleName() throws Exception {
		HintFile hintFile = parseHint("""
				<!id: fqn-constructor>

				new java.util.ArrayList<>()
				=> new java.util.LinkedList<>()
				;;
				"""); //$NON-NLS-1$

		assertFullReplacement(hintFile,
				"""
				import java.util.ArrayList;
				class Test { Object m() { return new ArrayList<>(); } }
				""", //$NON-NLS-1$
				"""
				import java.util.ArrayList;
				class Test { Object m() { return new java.util.LinkedList<>(); } }
				"""); //$NON-NLS-1$
	}
}
