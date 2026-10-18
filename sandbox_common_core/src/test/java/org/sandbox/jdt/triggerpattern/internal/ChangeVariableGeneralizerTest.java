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
package org.sandbox.jdt.triggerpattern.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ChangeVariableGeneralizerTest {

	@Test
	void suggestsOnlySharedVariableShapedNamesAndDoesNothingUntilExplicitlyChosen() {
		var example = new RuleAuthoringDocument.ChangeExample(
				"byte[] encoded = text.getBytes(\"UTF-8\");\nreturn encoded;",
				"byte[] encoded = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);\nreturn encoded;");
		assertEquals(List.of("encoded", "text"), ChangeVariableGeneralizer.candidates(example));
		assertEquals(example, ChangeVariableGeneralizer.generalize(example, Set.of()));
		assertEquals(new RuleAuthoringDocument.ChangeExample(
				"byte[] encoded = $text.getBytes(\"UTF-8\");\nreturn encoded;",
				"byte[] encoded = $text.getBytes(java.nio.charset.StandardCharsets.UTF_8);\nreturn encoded;"),
				ChangeVariableGeneralizer.generalize(example, Set.of("text")));
	}

	@Test
	void sameSpellingInMethodsMembersLiteralsAndCommentsStaysUnchanged() {
		var example = new RuleAuthoringDocument.ChangeExample(
				"value.value(value, \"value\"); // value\nreturn holder.value;",
				"value.modern(value, \"value\"); // value\nreturn holder.value;");
		var result = ChangeVariableGeneralizer.generalize(example, Set.of("value"));
		assertEquals("$value.value($value, \"value\"); // value\nreturn holder.value;", result.before());
		assertEquals("$value.modern($value, \"value\"); // value\nreturn holder.value;", result.after());
	}

	@Test
	void methodReferencesTypesAndLabelsAreNotVariableSuggestions() {
		var example = new RuleAuthoringDocument.ChangeExample(
				"again: while (flag) { strings.forEach(String::trim); break again; }",
				"again: while (flag) { strings.forEach(String::strip); break again; }");
		assertEquals(List.of("flag", "strings"), ChangeVariableGeneralizer.candidates(example));
	}

	@Test
	void doesNotReplaceNamesThatWereNotSuggested() {
		var example = new RuleAuthoringDocument.ChangeExample("receiver.old()", "receiver.modern()");
		assertThrows(IllegalArgumentException.class,
				() -> ChangeVariableGeneralizer.generalize(example, Set.of("old")));
	}
}
