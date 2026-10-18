/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;
import org.sandbox.jdt.internal.ui.wizard.ProjectRulePreview;

/** Tries the exact current hint-editor document, including unsaved edits. */
public final class TryHintOnProjectHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart editor= HandlerUtil.getActiveEditor(event);
		if (!(editor instanceof ITextEditor textEditor)) {
			return null;
		}
		IFile file= editor.getEditorInput().getAdapter(IFile.class);
		if (file == null || !"sandbox-hint".equals(file.getFileExtension())) { //$NON-NLS-1$
			return null;
		}
		IDocument document= textEditor.getDocumentProvider().getDocument(editor.getEditorInput());
		if (document != null) {
			ProjectRulePreview.open(HandlerUtil.getActiveShell(event), JavaCore.create(file.getProject()), document.get());
		}
		return null;
	}
}
