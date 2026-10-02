/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

/** Test-only, detached evidence; never acquires or previews a live source buffer. */
final class CleanupEditSnapshot {
	private final TextEdit edit;
	private final int saveMode;

	CleanupEditSnapshot(TextEdit edit, int saveMode) {
		this.edit = edit == null ? null : edit.copy();
		this.saveMode = saveMode;
	}

	String describe(String input, String observed) {
		StringBuilder result = new StringBuilder("\nChange save mode: ").append(saveMode)
				.append("\nCaptured edit: ").append(edit);
		if (edit == null || input == null) {
			return result.append("\nDetached comparison unavailable: no edit or no unambiguous input snapshot").toString();
		}
		Document detached = new Document(input);
		try {
			edit.copy().apply(detached);
			result.append("\nEdit changes input: ").append(!input.equals(detached.get()))
					.append("\nObserved matches detached edit: ").append(detached.get().equals(observed))
					.append("\nDetached result:\n").append(detached.get());
		} catch (BadLocationException | MalformedTreeException failure) {
			result.append("\nDetached edit application failed: ").append(failure);
		}
		return result.toString();
	}
}
