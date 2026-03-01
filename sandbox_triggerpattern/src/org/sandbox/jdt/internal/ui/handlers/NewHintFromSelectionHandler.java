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
package org.sandbox.jdt.internal.ui.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;
import org.sandbox.jdt.internal.ui.wizard.NewSandboxHintFileWizard;

/**
 * Eclipse command handler that opens the {@link NewSandboxHintFileWizard}
 * with the current text selection pre-filled as source pattern.
 *
 * <p>This handler is triggered from the context menu in the Java editor
 * (Sandbox TriggerPattern &rarr; New hint file from selection&hellip;).</p>
 *
 * @since 1.5.0
 */
public class NewHintFromSelectionHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ITextSelection textSelection = getTextSelection(event);
		if (textSelection == null || textSelection.getLength() == 0) {
			return null;
		}

		NewSandboxHintFileWizard wizard = new NewSandboxHintFileWizard();
		wizard.setInitialCodeSnippet(textSelection.getText());

		IStructuredSelection resourceSelection = getResourceSelection(event);
		wizard.init(PlatformUI.getWorkbench(), resourceSelection);

		WizardDialog dialog = new WizardDialog(
				HandlerUtil.getActiveShell(event), wizard);
		dialog.open();

		return null;
	}

	/**
	 * Extracts the current text selection from the active editor.
	 */
	private static ITextSelection getTextSelection(ExecutionEvent event) {
		IEditorPart editor = HandlerUtil.getActiveEditor(event);
		if (editor instanceof ITextEditor textEditor) {
			ISelection sel = textEditor.getSelectionProvider().getSelection();
			if (sel instanceof ITextSelection textSel) {
				return textSel;
			}
		}
		return null;
	}

	/**
	 * Builds a structured selection from the resource of the active editor.
	 */
	private static IStructuredSelection getResourceSelection(ExecutionEvent event) {
		IEditorPart editor = HandlerUtil.getActiveEditor(event);
		if (editor != null) {
			IEditorInput input = editor.getEditorInput();
			if (input instanceof IAdaptable adaptable) {
				IResource resource = adaptable.getAdapter(IResource.class);
				if (resource != null) {
					return new StructuredSelection(resource);
				}
			}
		}
		return StructuredSelection.EMPTY;
	}
}
