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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.results.Result;
import org.eclipse.swtbot.swt.finder.results.VoidResult;
import org.eclipse.swtbot.swt.finder.utils.SWTUtils;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.ui.PlatformUI;

/**
 * Replaces generic default-state cleanup-tab captures with focused, enabled
 * documentation states. The TriggerPattern tab remains owned by its dedicated
 * deterministic hint-file setup in {@link SandboxHelpScreenshotsSWTBotTest}.
 */
final class FocusedCleanupConfigurationScreenshots {

	private record CleanupTab(String label, String helpBundle, String fileName) {
	}

	private static final List<CleanupTab> TABS= List.of(
			new CleanupTab("Explicit Encoding (Sandbox)", "sandbox_encoding_quickfix_help", //$NON-NLS-1$ //$NON-NLS-2$
					"explicit-encoding-cleanup.png"), //$NON-NLS-1$
			new CleanupTab("Functional Converter (Sandbox)", "sandbox_functional_converter_help", //$NON-NLS-1$ //$NON-NLS-2$
					"functional-converter-cleanup.png"), //$NON-NLS-1$
			new CleanupTab("Int to Enum (Sandbox)", "sandbox_int_to_enum_help", //$NON-NLS-1$ //$NON-NLS-2$
					"int-to-enum-cleanup.png"), //$NON-NLS-1$
			new CleanupTab("JFace Cleanup (Sandbox)", "sandbox_jface_cleanup_help", //$NON-NLS-1$ //$NON-NLS-2$
					"jface-cleanup.png"), //$NON-NLS-1$
			new CleanupTab("JUnit Migration (Sandbox)", "sandbox_junit_cleanup_help", //$NON-NLS-1$ //$NON-NLS-2$
					"junit-migration-cleanup.png"), //$NON-NLS-1$
			new CleanupTab("Method Reuse (Sandbox)", "sandbox_method_reuse_help", //$NON-NLS-1$ //$NON-NLS-2$
					"method-reuse-cleanup.png"), //$NON-NLS-1$
			new CleanupTab("Platform Status (Sandbox)", "sandbox_platform_helper_help", //$NON-NLS-1$ //$NON-NLS-2$
					"platform-status-cleanup.png"), //$NON-NLS-1$
			new CleanupTab("Use General Type (Sandbox)", "sandbox_use_general_type_help", //$NON-NLS-1$ //$NON-NLS-2$
					"use-general-type-cleanup.png"), //$NON-NLS-1$
			new CleanupTab("XML Cleanup (Sandbox)", "sandbox_xml_cleanup_help", //$NON-NLS-1$ //$NON-NLS-2$
					"xml-cleanup.png")); //$NON-NLS-1$

	private static final int SCREENSHOT_CLIENT_WIDTH= 1280;
	private static final int SCREENSHOT_CLIENT_HEIGHT= 900;
	private static final String OUTPUT_PROPERTY= "sandbox.help.screenshot.output"; //$NON-NLS-1$

	private FocusedCleanupConfigurationScreenshots() {
	}

	static void capture() throws IOException {
		SWTWorkbenchBot bot= new SWTWorkbenchBot();
		Path outputRoot= SandboxCheckout.locate(OUTPUT_PROPERTY);
		openPreferences(bot);
		SWTBotShell preferences= bot.shell("Preferences").activate(); //$NON-NLS-1$
		selectPreferencePath(bot, preferences.bot().tree(), "Java", "Code Style", "Clean Up"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		clickButton(preferences, "Edit...", "Edit…"); //$NON-NLS-1$ //$NON-NLS-2$

		SWTBotShell profileDialog= bot.activeShell();
		prepareForScreenshot(bot, profileDialog);
		for (CleanupTab tab : TABS) {
			clickButton(profileDialog, "Deselect All"); //$NON-NLS-1$
			profileDialog.bot().tabItem(tab.label()).activate();
			bot.sleep(250);
			int selected= selectVisibleCleanupCheckboxes(bot, profileDialog);
			assertTrue(selected > 0, () -> "No enabled cleanup option was selected for " + tab.label()); //$NON-NLS-1$
			bot.sleep(500);
			capture(profileDialog, outputRoot, tab.helpBundle(), tab.fileName());
			System.out.println("[help-screenshots] Captured focused enabled cleanup tab: " + tab.label()); //$NON-NLS-1$
		}

		clickButton(profileDialog, "Cancel"); //$NON-NLS-1$
		waitForShellToClose(bot, profileDialog, "Cleanup profile dialog"); //$NON-NLS-1$
		preferences.activate();
		clickButton(preferences, "Cancel"); //$NON-NLS-1$
		waitForShellToClose(bot, preferences, "Preferences"); //$NON-NLS-1$
	}

	private static int selectVisibleCleanupCheckboxes(SWTWorkbenchBot bot, SWTBotShell shell) {
		int selected= 0;
		for (int pass= 0; pass < 4; pass++) {
			int changed= UIThreadRunnable.syncExec(shell.display, new Result<Integer>() {
				@Override
				public Integer run() {
					int count= 0;
					Deque<Control> pending= new ArrayDeque<>();
					pending.add(shell.widget);
					while (!pending.isEmpty()) {
						Control control= pending.removeFirst();
						if (control instanceof Button button
								&& (button.getStyle() & SWT.CHECK) != 0
								&& button.isVisible() && button.isEnabled()
								&& !button.getText().toLowerCase(java.util.Locale.ROOT).contains("best effort") //$NON-NLS-1$
								&& !button.getSelection()) {
							button.setSelection(true);
							Event event= new Event();
							event.widget= button;
							button.notifyListeners(SWT.Selection, event);
							count++;
						}
						if (control instanceof Composite composite) {
							for (Control child : composite.getChildren()) {
								pending.addLast(child);
							}
						}
					}
					return count;
				}
			});
			selected+= changed;
			if (changed == 0) {
				break;
			}
			bot.sleep(150);
		}
		return selected;
	}

	private static void openPreferences(SWTWorkbenchBot bot) {
		SWTBotShell workbench= UIThreadRunnable.syncExec(Display.getDefault(), new Result<SWTBotShell>() {
			@Override
			public SWTBotShell run() {
				return new SWTBotShell(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
			}
		}).activate();
		workbench.bot().menu("Window").menu("Preferences...").click(); //$NON-NLS-1$ //$NON-NLS-2$
		bot.shell("Preferences").activate(); //$NON-NLS-1$
	}

	private static void selectPreferencePath(SWTWorkbenchBot bot, SWTBotTree tree, String first, String... rest) {
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
		for (String label : labels) {
			try {
				shell.bot().button(label).click();
				return;
			} catch (org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException exception) {
				// Try the next platform spelling.
			}
		}
		throw new IllegalStateException("None of the expected buttons is visible: " + List.of(labels)); //$NON-NLS-1$
	}

	private static void waitForShellToClose(SWTWorkbenchBot bot, SWTBotShell shell, String description) {
		bot.waitUntil(new org.eclipse.swtbot.swt.finder.waits.DefaultCondition() {
			@Override
			public boolean test() {
				return shell.widget.isDisposed();
			}

			@Override
			public String getFailureMessage() {
				return description + " did not close"; //$NON-NLS-1$
			}
		});
	}

	private static void prepareForScreenshot(SWTWorkbenchBot bot, SWTBotShell shell) {
		UIThreadRunnable.syncExec(shell.display, new VoidResult() {
			@Override
			public void run() {
				Rectangle trim= shell.widget.computeTrim(0, 0,
						SCREENSHOT_CLIENT_WIDTH, SCREENSHOT_CLIENT_HEIGHT);
				shell.widget.setBounds(20, 20, trim.width, trim.height);
				shell.widget.layout(true, true);
			}
		});
		shell.activate();
		bot.sleep(500);
	}

	private static void capture(SWTBotShell shell, Path outputRoot, String helpBundle, String fileName)
			throws IOException {
		Path imageDirectory= outputRoot.resolve(helpBundle).resolve("images"); //$NON-NLS-1$
		Files.createDirectories(imageDirectory);
		Path image= imageDirectory.resolve(fileName);
		Rectangle clientBounds= UIThreadRunnable.syncExec(shell.display, new Result<Rectangle>() {
			@Override
			public Rectangle run() {
				Rectangle clientArea= shell.widget.getClientArea();
				return shell.widget.getDisplay().map(shell.widget, null, clientArea);
			}
		});
		assertTrue(SWTUtils.captureScreenshot(image.toString(), clientBounds),
				() -> "Could not capture " + image); //$NON-NLS-1$
		assertTrue(Files.isRegularFile(image) && Files.size(image) > 0,
				() -> "Screenshot was not written: " + image); //$NON-NLS-1$
	}
}
