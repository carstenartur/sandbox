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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.results.Result;
import org.eclipse.swtbot.swt.finder.utils.SWTUtils;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;

/** Captures the real Hint File option and its live preview in an unsaved documentation profile. */
@SuppressWarnings("nls")
final class CodePatternsConfigurationScreenshot {

	private static final String PROFILE= "Sandbox Code Patterns Help";
	private static final String HINT_FILE= "Apply transformation rules from .sandbox-hint files";
	private static final String BEFORE= "// Before applying .sandbox-hint rules:";
	private static final String AFTER= "// After applying .sandbox-hint rules:";
	private static final List<String> BEFORE_CODE= List.of("String s = \"\" + value;", "boolean empty = list.size() == 0;");
	private static final List<String> AFTER_CODE= List.of("String s = String.valueOf(value);", "boolean empty = list.isEmpty();");

	private record Controls(Button option, StyledText preview, List<Button> pageOptions) {
	}

	private CodePatternsConfigurationScreenshot() {
	}

	static void capture(SWTWorkbenchBot bot, SWTBotShell shell, Path outputRoot) throws IOException {
		Path image= outputRoot.resolve("sandbox_triggerpattern_help/images/code-patterns-cleanup.png");
		Files.createDirectories(outputRoot.resolve("sandbox_triggerpattern_help/images"));
		Files.deleteIfExists(image);
		// The caller cancels this edit dialog and Preferences; this profile is never persisted.
		var name= shell.bot().textWithLabel("Profile name:");
		name.setText(PROFILE);
		shell.bot().tabItem("Code Patterns (Sandbox)").activate();
		shell.bot().button("Deselect All").click();
		Controls controls= onUi(shell, () -> findControls(shell));
		onUi(shell, () -> {
			scrollToOption(controls.option());
			return Boolean.TRUE;
		});
		waitForPreview(bot, shell, controls, false);
		setHintEnabled(shell, controls.option(), true);
		waitForPreview(bot, shell, controls, true);
		// A stale or hard-coded after preview must fail this reversal as well.
		setHintEnabled(shell, controls.option(), false);
		waitForPreview(bot, shell, controls, false);
		setHintEnabled(shell, controls.option(), true);
		waitForPreview(bot, shell, controls, true);
		onUi(shell, () -> {
			scrollToOption(controls.option());
			StyledText preview= controls.preview();
			preview.setTopIndex(preview.getLineAtOffset(preview.getText().indexOf(AFTER)));
			shell.display.setCursorLocation(0, 0);
			return Boolean.TRUE;
		});
		bot.waitUntil(new DefaultCondition() {
			private byte[] previousImage;
			private String lastProblem= "No capture attempted";

			@Override
			public boolean test() throws Exception {
				boolean captured= Boolean.TRUE.equals(onUi(shell, () -> {
					if (shell.widget.isDisposed() || name.widget.isDisposed()) {
						lastProblem= "Profile dialog was disposed";
						return Boolean.FALSE;
					}
					if (!PROFILE.equals(name.widget.getText()) || !hasPreview(controls, true)) {
						lastProblem= "Profile, isolated option selection or live after preview changed";
						return Boolean.FALSE;
					}
					if (shell.display.getActiveShell() != shell.widget) {
						lastProblem= "Profile dialog is not the active shell";
						return Boolean.FALSE;
					}
					lastProblem= optionSectionProblem(controls.option());
					if (!lastProblem.isEmpty()) {
						return Boolean.FALSE;
					}
					lastProblem= CodePatternsScreenshotVisibility.checkboxProblem(controls.option());
					if (!lastProblem.isEmpty()) {
						return Boolean.FALSE;
					}
					lastProblem= CodePatternsScreenshotVisibility.controlProblem(controls.preview());
					if (!lastProblem.isEmpty()) {
						return Boolean.FALSE;
					}
					lastProblem= previewProblem(controls.preview());
					if (!lastProblem.isEmpty()) {
						return Boolean.FALSE;
					}
					Rectangle area= shell.widget.getClientArea();
					if (area.width != 1280 || area.height != 900) {
						lastProblem= "Unexpected profile client area: " + area;
						return Boolean.FALSE;
					}
					lastProblem= "Native screenshot API returned false";
					return Boolean.valueOf(SWTUtils.captureScreenshot(image.toString(),
							shell.display.map(shell.widget, null, area)));
				}));
				if (!captured) {
					previousImage= null;
					return false;
				}
				byte[] currentImage= Files.readAllBytes(image);
				boolean stable= previousImage != null && MessageDigest.isEqual(previousImage, currentImage);
				lastProblem= previousImage == null ? "Waiting for a second native image"
						: "Consecutive native images differ";
				previousImage= currentImage;
				return stable;
			}

			@Override
			public String getFailureMessage() {
				return "Hint File screenshot requires one enabled option, visible after code and stable native painting: "
						+ lastProblem;
			}
		});
		assertTrue(Files.isRegularFile(image) && Files.size(image) > 0, "Missing Code Patterns screenshot");
	}

	private static Controls findControls(SWTBotShell shell) {
		List<Button> matches= controls(shell.widget).stream().filter(Button.class::isInstance)
				.map(Button.class::cast).filter(button -> (button.getStyle() & SWT.CHECK) != 0)
				.filter(button -> HINT_FILE.equals(button.getText())).toList();
		assertEquals(1, matches.size(), "Expected the real Hint File checkbox");
		Button option= matches.getFirst();
		List<StyledText> previews= controls(shell.widget).stream().filter(StyledText.class::isInstance)
				.map(StyledText.class::cast).filter(Control::isVisible)
				.filter(text -> text.getText().contains(BEFORE)).toList();
		assertEquals(1, previews.size(), "Expected the Code Patterns before preview");
		List<Button> pageOptions= controls(option.getParent().getParent()).stream()
				.filter(Button.class::isInstance).map(Button.class::cast)
				.filter(button -> (button.getStyle() & SWT.CHECK) != 0).toList();
		return new Controls(option, previews.getFirst(), pageOptions);
	}

	private static void setHintEnabled(SWTBotShell shell, Button option, boolean enabled) {
		// Deliver the normal selection event without blocking SWTBot in a nested GTK focus loop.
		shell.display.asyncExec(() -> {
			if (!option.isDisposed() && option.isEnabled()) {
				option.setSelection(enabled);
				Event event= new Event();
				event.widget= option;
				option.notifyListeners(SWT.Selection, event);
			}
		});
	}

	private static void waitForPreview(SWTWorkbenchBot bot, SWTBotShell shell, Controls controls, boolean enabled) {
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				return Boolean.TRUE.equals(onUi(shell, () -> Boolean.valueOf(hasPreview(controls, enabled))));
			}

			@Override
			public String getFailureMessage() {
				return "The live Hint File preview did not follow selection=" + enabled;
			}
		});
	}

	private static boolean hasPreview(Controls controls, boolean enabled) {
		if (controls.option().isDisposed() || controls.preview().isDisposed()
				|| !controls.option().isEnabled() || controls.option().getSelection() != enabled
				|| controls.pageOptions().stream().anyMatch(button -> button.isDisposed()
						|| (button != controls.option() && button.getSelection()))) {
			return false;
		}
		String text= controls.preview().getText();
		String marker= enabled ? AFTER : BEFORE;
		int start= text.indexOf(marker);
		return start >= 0 && !text.contains(enabled ? BEFORE : AFTER)
				&& (enabled ? AFTER_CODE : BEFORE_CODE).stream().allMatch(text.substring(start)::contains);
	}

	static void scrollToOption(Button option) {
		ScrolledComposite scrolled= configurationPane(option);
		Composite section= option.getParent();
		// Map the native Group's outer frame from its parent. Its own (0, 0) is the
		// client origin on GTK and lies below the title that the screenshot must retain.
		Point top= option.getDisplay().map(section.getParent(), scrolled.getContent(), section.getLocation());
		scrolled.setOrigin(0, Math.max(0, top.y - 8));
		assertTrue(option.computeSize(SWT.DEFAULT, SWT.DEFAULT).x <= option.getSize().x,
				"The Hint File label is clipped horizontally");
	}

	static String optionSectionProblem(Button option) {
		if (option.isDisposed()) {
			return "Hint File option is disposed";
		}
		Composite section= option.getParent();
		if (!section.isVisible()) {
			return "Hint File section is hidden";
		}
		ScrolledComposite scrolled= configurationPane(option);
		Point top= option.getDisplay().map(section.getParent(), scrolled, section.getLocation());
		// The checkbox can remain fully visible while the section title is above the
		// viewport. Check the outer start independently before every native capture.
		return scrolled.getClientArea().contains(top) ? ""
				: "Hint File section starts outside the configuration viewport: " + top;
	}

	private static ScrolledComposite configurationPane(Button option) {
		for (Composite parent= option.getParent(); parent != null; parent= parent.getParent()) {
			if (parent instanceof ScrolledComposite scrolled) {
				return scrolled;
			}
		}
		throw new AssertionError("Hint File option has no scrollable configuration pane");
	}

	private static String previewProblem(StyledText preview) {
		String text= preview.getText();
		int start= text.indexOf(AFTER);
		for (String fact : List.of(AFTER, AFTER_CODE.get(0), AFTER_CODE.get(1))) {
			int offset= start < 0 ? -1 : text.indexOf(fact, start);
			if (offset < 0) {
				return "Missing after-preview statement: " + fact;
			}
			Rectangle textBounds= preview.getTextBounds(offset, offset + fact.length() - 1);
			if (!textBounds.intersection(preview.getClientArea()).equals(textBounds)) {
				return "Preview text " + textBounds + " is outside " + preview.getClientArea() + ": " + fact;
			}
		}
		return "";
	}

	private static List<Control> controls(Composite root) {
		List<Control> result= new ArrayList<>();
		Deque<Control> pending= new ArrayDeque<>();
		pending.add(root);
		while (!pending.isEmpty()) {
			Control control= pending.removeFirst();
			result.add(control);
			if (control instanceof Composite composite) {
				pending.addAll(List.of(composite.getChildren()));
			}
		}
		return result;
	}

	private static <T> T onUi(SWTBotShell shell, Result<T> action) {
		return UIThreadRunnable.syncExec(shell.display, action);
	}
}
