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
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.results.Result;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCheckBox;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the documentation screenshot is backed by the real cleanup
 * option rather than only by Help text or a preview string.
 */
public class JUnitBestEffortCleanupDialogSWTBotTest {

	private static final String JUNIT_TAB= "JUnit Migration (Sandbox)"; //$NON-NLS-1$
	private static final String BEST_EFFORT_LABEL=
			"Best effort: migrate every proven construct and add @todo scaffolds for unresolved gaps (manual repair may be required)"; //$NON-NLS-1$

	private SWTWorkbenchBot bot;

	@BeforeEach
	public void setUp() {
		bot= new SWTWorkbenchBot();
		closeWelcomeView();
	}

	@AfterEach
	public void tearDown() {
		closeModalShells();
	}

	@Test
	public void bestEffortMigrationIsVisibleExplicitAndDisabledByDefault() {
		SWTBotShell workbench= workbenchShell().activate();
		workbench.bot().menu("Window").menu("Preferences...").click(); //$NON-NLS-1$ //$NON-NLS-2$

		SWTBotShell preferences= bot.shell("Preferences").activate(); //$NON-NLS-1$
		selectPreferencePath(preferences.bot().tree(), "Java", "Code Style", "Clean Up"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		clickButton(preferences, "Edit...", "Edit…"); //$NON-NLS-1$ //$NON-NLS-2$

		SWTBotShell profileDialog= bot.activeShell();
		profileDialog.bot().tabItem(JUNIT_TAB).activate();
		SWTBotCheckBox bestEffort= profileDialog.bot().checkBox(BEST_EFFORT_LABEL);

		assertTrue(bestEffort.isVisible(),
				"The best-effort JUnit migration switch must be visible in the real cleanup profile dialog"); //$NON-NLS-1$
		assertTrue(bestEffort.isEnabled(),
				"The best-effort switch must be selectable when JUnit migration is enabled in the profile"); //$NON-NLS-1$
		assertFalse(bestEffort.isChecked(),
				"Best-effort migration must remain an explicit opt-in and must be disabled by default"); //$NON-NLS-1$

		bestEffort.setFocus();
		clickButton(profileDialog, "Cancel"); //$NON-NLS-1$
		clickButton(preferences, "Cancel"); //$NON-NLS-1$
	}

	private static SWTBotShell workbenchShell() {
		return UIThreadRunnable.syncExec(Display.getDefault(), new Result<SWTBotShell>() {
			@Override
			public SWTBotShell run() {
				return new SWTBotShell(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
			}
		});
	}

	private void selectPreferencePath(SWTBotTree tree, String first, String... rest) {
		SWTBotTreeItem item= tree.getTreeItem(first);
		if (rest.length > 0) {
			item.expand();
		}
		for (int index= 0; index < rest.length; index++) {
			item= item.getNode(rest[index]);
			if (index + 1 < rest.length) {
				item.expand();
			}
		}
		item.select();
		bot.sleep(300);
	}

	private static void clickButton(SWTBotShell shell, String... labels) {
		WidgetNotFoundException failure= null;
		for (String label : labels) {
			try {
				shell.bot().button(label).click();
				return;
			} catch (WidgetNotFoundException exception) {
				failure= exception;
			}
		}
		throw failure;
	}

	private void closeWelcomeView() {
		try {
			bot.viewByTitle("Welcome").close(); //$NON-NLS-1$
		} catch (WidgetNotFoundException exception) {
			// The welcome view is not shown in every test workbench.
		}
	}

	private void closeModalShells() {
		try {
			SWTBotShell workbench= workbenchShell();
			for (SWTBotShell shell : bot.shells()) {
				if (shell.widget != workbench.widget && shell.isOpen()) {
					shell.close();
				}
			}
		} catch (WidgetNotFoundException exception) {
			// Workbench already closed.
		}
	}
}
