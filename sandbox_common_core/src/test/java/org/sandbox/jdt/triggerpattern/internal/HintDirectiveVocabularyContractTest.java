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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.dom.ASTNode;

import org.sandbox.jdt.triggerpattern.api.HintFile;

class HintDirectiveVocabularyContractTest {

	@Test
	void everyDocumentedDirectiveHasAnAcceptedRepresentativeSyntax() throws Exception {
		Map<String, String> examples= Map.ofEntries(
				Map.entry("id", "<!id: vocabulary-contract>"), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("description", "<!description: language contract>"), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("severity", "<!severity: warning>"), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("minJavaVersion", "<!minJavaVersion: 17>"), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("tags", "<!tags: dsl, contract>"), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("include", "<!include: shared-rules>"), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("caseInsensitive", "<!caseInsensitive>"), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("suppressWarnings", "<!suppressWarnings: deprecation>"), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("treeKind", "<!treeKind: METHOD_DECLARATION>"), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("binding-policy", "<!binding-policy: required>"), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("requires-plan", "<!requires-plan: semantic-contract>"), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("foreach", "<!foreach SAMPLE: source -> target>"), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("map", "<!map SAMPLE: source => target>"), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("predicate", "<!predicate selected($node): matchesAny($node)>") //$NON-NLS-1$ //$NON-NLS-2$
		);
		assertEquals(HintLanguageVocabulary.directiveNames(), examples.keySet(),
				"Adding or removing a documented directive requires a parser-contract example."); //$NON-NLS-1$

		String source= String.join("\n", examples.values()) + """
				
				foo($node) :: selected($node)
				=> bar($node)
				;;
				""";
		HintFile hint= new HintProgramParser().parseHintFile(source);

		assertEquals("vocabulary-contract", hint.getId()); //$NON-NLS-1$
		assertEquals("language contract", hint.getDescription()); //$NON-NLS-1$
		assertEquals(17, hint.getMinJavaVersion());
		assertTrue(hint.getTags().containsAll(Set.of("dsl", "contract"))); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(hint.getIncludes().contains("shared-rules")); //$NON-NLS-1$
		assertTrue(hint.isCaseInsensitive());
		assertTrue(hint.getSuppressWarnings().contains("deprecation")); //$NON-NLS-1$
		assertTrue(hint.getTreeKindNodeTypes().contains(ASTNode.METHOD_DECLARATION));
		assertEquals(1, hint.getRules().size());
	}
}
