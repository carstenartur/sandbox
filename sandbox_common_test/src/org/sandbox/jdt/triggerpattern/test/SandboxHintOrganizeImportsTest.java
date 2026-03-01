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
import org.sandbox.jdt.triggerpattern.editor.SandboxHintOrganizeImports;

/**
 * Tests for {@link SandboxHintOrganizeImports}.
 *
 * <p>Verifies that imports in {@code <? ?>} blocks are correctly organized
 * (sorted, deduplicated) and that imports outside these blocks are not
 * changed.</p>
 *
 * @since 1.5.0
 */
public class SandboxHintOrganizeImportsTest {

	private final SandboxHintOrganizeImports organizer = new SandboxHintOrganizeImports();

	@Test
	public void testImportsAreSorted() throws Exception {
		String content = "<?\nimport java.util.List;\nimport java.io.File;\npublic boolean guard() { return true; }\n?>"; //$NON-NLS-1$
		IDocument doc = new Document(content);

		TextEdit edit = organizer.organizeImports(doc);

		assertNotNull(edit, "Should produce edits for unsorted imports"); //$NON-NLS-1$
		edit.apply(doc);
		String result = doc.get();
		int fileIdx = result.indexOf("import java.io.File;"); //$NON-NLS-1$
		int listIdx = result.indexOf("import java.util.List;"); //$NON-NLS-1$
		assertTrue(fileIdx < listIdx, "java.io.File should come before java.util.List: " + result); //$NON-NLS-1$
	}

	@Test
	public void testDuplicateImportsRemoved() throws Exception {
		String content = "<?\nimport java.util.List;\nimport java.util.List;\npublic boolean guard() { return true; }\n?>"; //$NON-NLS-1$
		IDocument doc = new Document(content);

		TextEdit edit = organizer.organizeImports(doc);

		assertNotNull(edit, "Should produce edits for duplicate imports"); //$NON-NLS-1$
		edit.apply(doc);
		String result = doc.get();
		int first = result.indexOf("import java.util.List;"); //$NON-NLS-1$
		int second = result.indexOf("import java.util.List;", first + 1); //$NON-NLS-1$
		assertTrue(second == -1, "Duplicate import should be removed: " + result); //$NON-NLS-1$
	}

	@Test
	public void testImportsOutsideBlockNotChanged() {
		String content = "// import java.util.List;\n$x.method()\n=> $x.newMethod()\n;;\n"; //$NON-NLS-1$
		IDocument doc = new Document(content);

		TextEdit edit = organizer.organizeImports(doc);

		assertNull(edit, "Should not modify imports outside <? ?> blocks"); //$NON-NLS-1$
	}

	@Test
	public void testNoEditsWhenAlreadyOrganized() {
		String content = "<?\nimport java.io.File;\nimport java.util.List;\npublic boolean guard() { return true; }\n?>"; //$NON-NLS-1$
		IDocument doc = new Document(content);

		TextEdit edit = organizer.organizeImports(doc);

		assertNull(edit, "Already organized imports should produce no edits"); //$NON-NLS-1$
	}

	@Test
	public void testNullDocumentReturnsNull() {
		assertNull(organizer.organizeImports(null));
	}

	@Test
	public void testBlockWithNoImports() {
		String content = "<?\npublic boolean guard() { return true; }\n?>"; //$NON-NLS-1$
		IDocument doc = new Document(content);

		TextEdit edit = organizer.organizeImports(doc);

		assertNull(edit, "Block without imports should produce no edits"); //$NON-NLS-1$
	}
}
