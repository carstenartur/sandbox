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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.swt.graphics.Rectangle;

import org.junit.jupiter.api.Test;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.results.Result;
import org.eclipse.swtbot.swt.finder.results.VoidResult;
import org.eclipse.swtbot.swt.finder.utils.SWTUtils;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;

/**
 * Complete Help screenshot suite, including the real LTK Cleanup preview.
 * <p>
 * The superclass retains the existing configuration and workflow screenshots;
 * this class adds execution-preview scenarios without duplicating that inventory.
 * </p>
 */
public class AllHelpScreenshotsSWTBotTest extends SandboxHelpScreenshotsSWTBotTest {

	private static final int SCREENSHOT_CLIENT_WIDTH= 1280;
	private static final int SCREENSHOT_CLIENT_HEIGHT= 860;

	private final SWTWorkbenchBot previewBot= new SWTWorkbenchBot();
	private final Path previewOutputRoot= resolveOutputRoot();

	@Test
	public void captureRealCleanupPreviewsAndVerifySelection() throws Exception {
		try (CleanupPreviewSWTBotScenario scenario=
				CleanupPreviewSWTBotScenario.openMultipleSteps(previewBot, previewOutputRoot)) {
			scenario.prepareMultipleStepView();
			prepareForScreenshot(scenario.shell());
			capture(scenario.shell(), "sandbox_jface_cleanup_help", //$NON-NLS-1$
					"cleanup-preview-multiple-steps.png"); //$NON-NLS-1$
			scenario.finishMultipleStepSelectionAndUndo();
		}

		try (CleanupPreviewSWTBotScenario scenario=
				CleanupPreviewSWTBotScenario.openMultipleFiles(previewBot, previewOutputRoot)) {
			scenario.prepareMultipleFileView();
			prepareForScreenshot(scenario.shell());
			capture(scenario.shell(), "sandbox_jface_cleanup_help", //$NON-NLS-1$
					"cleanup-preview-multiple-files.png"); //$NON-NLS-1$
			scenario.finishMultipleFileSelectionAndUndo();
		}
	}

	private static Path resolveOutputRoot() {
		String configured= System.getProperty("sandbox.help.screenshot.output"); //$NON-NLS-1$
		assertFalse(configured == null || configured.isBlank(),
				"Missing -Dsandbox.help.screenshot.output"); //$NON-NLS-1$
		return Path.of(configured).toAbsolutePath().normalize();
	}

	private void prepareForScreenshot(SWTBotShell shell) {
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
		previewBot.sleep(500);
	}

	private void capture(SWTBotShell shell, String helpBundle, String fileName) throws IOException {
		Path imageDirectory= previewOutputRoot.resolve(helpBundle).resolve("images"); //$NON-NLS-1$
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
