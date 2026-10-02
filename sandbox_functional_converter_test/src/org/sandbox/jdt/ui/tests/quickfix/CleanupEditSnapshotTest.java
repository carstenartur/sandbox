/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.CopySourceEdit;
import org.eclipse.text.edits.CopyTargetEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.junit.jupiter.api.Test;

class CleanupEditSnapshotTest {
	@Test
	void snapshotSurvivesMutationOfTheActualChange() {
		MultiTextEdit actual = new MultiTextEdit();
		actual.addChild(new ReplaceEdit(0, 3, "after"));
		CleanupEditSnapshot snapshot = new CleanupEditSnapshot(actual, 1);
		actual.removeChildren();
		String diagnostic = snapshot.describe("old", "old");
		assertTrue(diagnostic.contains("Edit changes input: true"), diagnostic);
		assertTrue(diagnostic.contains("Observed matches detached edit: false"), diagnostic);
		assertTrue(diagnostic.endsWith("Detached result:\nafter"), diagnostic);
	}

	@Test
	void repeatedDiagnosticsDoNotConsumeCopyEditsOrChangeTheOriginal() throws Exception {
		MultiTextEdit actual = new MultiTextEdit();
		CopySourceEdit source = new CopySourceEdit(0, 2);
		actual.addChild(source);
		actual.addChild(new CopyTargetEdit(3, source));
		CleanupEditSnapshot snapshot = new CleanupEditSnapshot(actual, 1);
		String first = snapshot.describe("abc", "abcab");
		assertTrue(first.contains("Observed matches detached edit: true"), first);
		assertEquals(first, snapshot.describe("abc", "abcab"));
		Document document = new Document("abc");
		actual.apply(document);
		assertEquals("abcab", document.get());
		assertEquals(first, snapshot.describe("abc", "abcab"));
	}

	@Test
	void malformedEvidenceDoesNotHideTheOriginalAssertion() {
		CleanupEditSnapshot snapshot = new CleanupEditSnapshot(new ReplaceEdit(100, 1, "x"), 1);
		String diagnostic = snapshot.describe("abc", "abc");
		assertTrue(diagnostic.contains("Detached edit application failed:"), diagnostic);
		assertFalse(diagnostic.contains("Observed matches detached edit: true"), diagnostic);
	}

	@Test
	void emptyAndMissingEditsAreDistinguishedFromLostApplication() {
		String empty = new CleanupEditSnapshot(new MultiTextEdit(), 1).describe("abc", "abc");
		assertTrue(empty.contains("Edit changes input: false"), empty);
		assertTrue(empty.contains("Observed matches detached edit: true"), empty);
		String missing = new CleanupEditSnapshot(null, 1).describe("abc", "abc");
		assertTrue(missing.contains("Detached comparison unavailable:"), missing);
		String ambiguous = new CleanupEditSnapshot(new ReplaceEdit(0, 1, "x"), 1).describe(null, "xbc");
		assertTrue(ambiguous.contains("Detached comparison unavailable:"), ambiguous);
	}
}
