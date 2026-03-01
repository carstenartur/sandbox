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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.editor.SandboxHintRenameParticipant;

/**
 * Tests for {@link SandboxHintRenameParticipant}.
 *
 * <p>Verifies that renaming guard function references in
 * {@code .sandbox-hint} files works correctly.</p>
 *
 * @since 1.5.0
 */
public class SandboxHintRenameParticipantTest {

	private final SandboxHintRenameParticipant participant = new SandboxHintRenameParticipant();

	@Test
	public void testRenameGuardReference() throws Exception {
		String content = "$x.method() :: isValid($x)\n=> $x.newMethod()\n;;\n"; //$NON-NLS-1$
		IDocument doc = new Document(content);

		TextEdit edit = participant.computeRenameEdits(doc, "isValid", "isOk"); //$NON-NLS-1$ //$NON-NLS-2$

		assertNotNull(edit, "Should produce rename edits"); //$NON-NLS-1$

		edit.apply(doc);
		String result = doc.get();
		assertTrue(result.contains(":: isOk("), //$NON-NLS-1$
				"Should contain renamed reference: " + result); //$NON-NLS-1$
	}

	@Test
	public void testMultipleReferencesRenamed() throws Exception {
		String content = "$x.a() :: isValid($x)\n=> $x.b()\n;;\n\n$y.c() :: isValid($y)\n=> $y.d()\n;;\n"; //$NON-NLS-1$
		IDocument doc = new Document(content);

		TextEdit edit = participant.computeRenameEdits(doc, "isValid", "isOk"); //$NON-NLS-1$ //$NON-NLS-2$

		assertNotNull(edit);
		edit.apply(doc);
		String result = doc.get();
		// Both references should be renamed
		assertTrue(!result.contains(":: isValid("), //$NON-NLS-1$
				"All old references should be renamed"); //$NON-NLS-1$
	}

	@Test
	public void testCommentReferencesNotRenamed() throws Exception {
		String content = "// :: isValid() is used here\n$x.method() :: isValid($x)\n=> $x.newMethod()\n;;\n"; //$NON-NLS-1$
		IDocument doc = new Document(content);

		TextEdit edit = participant.computeRenameEdits(doc, "isValid", "isOk"); //$NON-NLS-1$ //$NON-NLS-2$

		assertNotNull(edit);
		edit.apply(doc);
		String result = doc.get();
		// Comment should still contain old name
		assertTrue(result.contains("// :: isValid()"), //$NON-NLS-1$
				"Comment references should not be renamed"); //$NON-NLS-1$
	}

	@Test
	public void testNoChangesWhenNoReferences() {
		String content = "$x.method()\n=> $x.newMethod()\n;;\n"; //$NON-NLS-1$
		IDocument doc = new Document(content);

		TextEdit edit = participant.computeRenameEdits(doc, "isValid", "isOk"); //$NON-NLS-1$ //$NON-NLS-2$

		assertNull(edit, "Should return null when no changes needed"); //$NON-NLS-1$
	}

	@Test
	public void testNullDocumentReturnsNull() {
		assertNull(participant.computeRenameEdits(null, "old", "new")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testSameNameReturnsNull() {
		IDocument doc = new Document(":: isValid("); //$NON-NLS-1$
		assertNull(participant.computeRenameEdits(doc, "isValid", "isValid")); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
