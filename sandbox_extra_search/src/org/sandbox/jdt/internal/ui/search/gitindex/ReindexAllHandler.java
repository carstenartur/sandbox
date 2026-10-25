/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search.gitindex;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.handlers.HandlerUtil;

/** Rebuilds one named server index; the old command ID remains compatible. */
public class ReindexAllHandler extends AbstractHandler {
	@Override public Object execute(ExecutionEvent event) {
		var shell= HandlerUtil.getActiveShell(event);
		String repository= GitServerJobs.repository(event, shell);
		if (repository == null) return null;
		if (!MessageDialog.openConfirm(shell, "Rebuild Server Index", //$NON-NLS-1$
				"Rebuild history for " + repository + " on " + GitSearchPreferences.restUrl() //$NON-NLS-1$ //$NON-NLS-2$
				+ "?\n\nUnreachable indexed history may be pruned. Stop other server writers first. Git refs are unchanged.")) return null; //$NON-NLS-1$
		var token= new InputDialog(shell, "Server authorization", "JGIT_ADMIN_TOKEN (not saved):", "", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				value -> value.isBlank() ? "Admin token is required." : null) { //$NON-NLS-1$
			@Override protected Control createDialogArea(Composite parent) {
				Control area= super.createDialogArea(parent);
				getText().setEchoChar('*');
				return area;
			}
		};
		if (token.open() != Window.OK) return null;
		String credential= token.getValue();
		var client= EmbeddedSearchService.getInstance().getSearchClient();
		GitServerJobs.schedule(shell, "Rebuild Server Index", monitor -> client.rebuild(repository, credential)); //$NON-NLS-1$
		return null;
	}
}
