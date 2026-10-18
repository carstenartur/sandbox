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
package org.sandbox.jdt.internal.ui.handlers;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;
import org.sandbox.jdt.internal.ui.wizard.NewSandboxHintFileWizard;
import org.sandbox.jdt.triggerpattern.internal.RuleAuthoringDocument;

/** Opens the ordinary rule wizard with evidence from the saved-to-editor change. */
public class GeneralizeChangeHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart editor = HandlerUtil.getActiveEditor(event);
		if (!(editor instanceof ITextEditor textEditor)) {
			return null;
		}
		IFile file = editor.getEditorInput().getAdapter(IFile.class);
		IDocument current = textEditor.getDocumentProvider().getDocument(editor.getEditorInput());
		if (file == null || current == null) {
			return null;
		}
		try {
			StringBuilder saved = new StringBuilder();
			try (Reader reader = new InputStreamReader(file.getContents(), file.getCharset())) {
				char[] buffer = new char[4096];
				int read;
				while ((read = reader.read(buffer)) != -1) {
					saved.append(buffer, 0, read);
				}
			}
			var change = RuleAuthoringDocument.changedLines(saved.toString(), current.get());
			if (change.isEmpty()) {
				MessageDialog.openInformation(HandlerUtil.getActiveShell(event), "Generalize this change", //$NON-NLS-1$
						"Edit the Java code first, then run this command before saving. " //$NON-NLS-1$
								+ "The wizard will show the saved code and your current change."); //$NON-NLS-1$
				return null;
			}
			NewSandboxHintFileWizard wizard = new NewSandboxHintFileWizard();
			wizard.setInitialChange(change.get().before(), change.get().after());
			wizard.init(PlatformUI.getWorkbench(), new StructuredSelection(file));
			new WizardDialog(HandlerUtil.getActiveShell(event), wizard).open();
		} catch (CoreException | IOException exception) {
			throw new ExecutionException("Could not read the saved Java source", exception); //$NON-NLS-1$
		}
		return null;
	}
}
