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
package org.sandbox.jdt.internal.ui.search;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PlatformUI;

/**
 * Opens the Eclipse Search Dialog with the Semantic Code Search page in front.
 *
 * <p>
 * Follows the same pattern as {@link OpenUpdateSearchPageAction}.
 * </p>
 */
public class OpenSemanticSearchPageAction implements IWorkbenchWindowActionDelegate {

	private IWorkbenchWindow fWindow;

	@Override
	public void init(IWorkbenchWindow window) {
		fWindow= window;
	}

	@Override
	public void run(IAction action) {
		if (fWindow == null) {
			beep();
			JavaPlugin.logErrorMessage(Messages.OpenSemanticSearchPageAction_WindowNotAvailable);
			return;
		}
		if (fWindow.getActivePage() == null) {
			beep();
			JavaPlugin.logErrorMessage(Messages.OpenSemanticSearchPageAction_PageNotAvailable);
			return;
		}
		NewSearchUI.openSearchDialog(fWindow, SemanticCodeSearchPage.PAGE_ID);
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		// do nothing — action is not selection-dependent
	}

	@Override
	public void dispose() {
		fWindow= null;
	}

	private void beep() {
		try {
			IWorkbenchWindow activeWorkbenchWindow= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			if (activeWorkbenchWindow == null) {
				return;
			}
			Shell shell= activeWorkbenchWindow.getShell();
			if (shell != null && shell.getDisplay() != null) {
				shell.getDisplay().beep();
			}
		} catch (Exception e) {
			// Ignore — beep is not critical
		}
	}
}
