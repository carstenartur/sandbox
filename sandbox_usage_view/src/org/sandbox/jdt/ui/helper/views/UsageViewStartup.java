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
package org.sandbox.jdt.ui.helper.views;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.sandbox.jdt.ui.helper.views.preferences.UsageViewPreferenceConstants;

/**
 * Startup class that shows the JavaHelper View at startup if the preference is enabled.
 */
public class UsageViewStartup implements IStartup {

	@Override
	public void earlyStartup() {
		// Run in UI thread
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			// Check if plugin is properly activated
			UsageViewPlugin plugin = UsageViewPlugin.getDefault();
			if (plugin == null) {
				return;
			}
			
			IPreferenceStore store = plugin.getPreferenceStore();
			boolean showAtStartup = store.getBoolean(UsageViewPreferenceConstants.SHOW_VIEW_AT_STARTUP);
			
			if (showAtStartup) {
				IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				if (window != null) {
					IWorkbenchPage page = window.getActivePage();
					if (page != null) {
						try {
							page.showView(UsageViewPlugin.VIEW_ID);
						} catch (PartInitException e) {
							// Log error but don't fail startup
							plugin.getLog().error("Failed to show JavaHelper View at startup", e);
						}
					}
				}
			}
		});
	}
}
