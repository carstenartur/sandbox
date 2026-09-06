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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.junit.jupiter.api.Test;

/** Real SWT clipping tests; no screenshot baseline or SWTBot dependency is needed. */
@SuppressWarnings("nls")
class CodePatternsScreenshotVisibilityTest {

	@Test
	void trailingEmptySpaceMayBeClippedButNotTheCheckboxLabel() {
		withCheckbox(button -> {
			assertFalse(CodePatternsScreenshotVisibility.controlProblem(button).isEmpty(),
					"Fixture must reproduce a control wider than the viewport");
			assertEquals("", CodePatternsScreenshotVisibility.checkboxProblem(button));
		});
	}

	@Test
	void eitherHorizontalLabelEdgeBeingClippedIsRejected() {
		withCheckbox(button -> {
			Point preferred= button.computeSize(SWT.DEFAULT, SWT.DEFAULT);
			Composite viewport= button.getParent().getParent();
			button.setLocation(viewport.getClientArea().width - preferred.x + 1, 10);
			assertFalse(CodePatternsScreenshotVisibility.checkboxProblem(button).isEmpty(), "Right edge");
			button.setLocation(-1, 10);
			assertFalse(CodePatternsScreenshotVisibility.checkboxProblem(button).isEmpty(), "Left indicator");
		});
	}

	@Test
	void verticalClippingIsRejectedUntilTheOptionIsScrolledIntoView() {
		withCheckbox(button -> {
			ScrolledComposite viewport= (ScrolledComposite) button.getParent().getParent();
			button.setLocation(10, viewport.getClientArea().height - button.getSize().y + 1);
			assertFalse(CodePatternsScreenshotVisibility.checkboxProblem(button).isEmpty(), "Bottom edge");
			button.setLocation(10, 300);
			assertFalse(CodePatternsScreenshotVisibility.checkboxProblem(button).isEmpty(), "Outside viewport");
			viewport.setOrigin(0, 290);
			assertEquals("", CodePatternsScreenshotVisibility.checkboxProblem(button));
		});
	}

	@Test
	void undersizedHiddenAndDisposedOptionsAreRejected() {
		withCheckbox(button -> {
			Point original= button.getSize();
			Point preferred= button.computeSize(SWT.DEFAULT, SWT.DEFAULT);
			button.setSize(preferred.x - 1, original.y);
			assertFalse(CodePatternsScreenshotVisibility.checkboxProblem(button).isEmpty(), "Narrow control");
			button.setSize(original.x, preferred.y - 1);
			assertFalse(CodePatternsScreenshotVisibility.checkboxProblem(button).isEmpty(), "Short control");
			button.setSize(original);
			button.getParent().setVisible(false);
			assertFalse(CodePatternsScreenshotVisibility.checkboxProblem(button).isEmpty(), "Hidden ancestor");
			button.dispose();
			assertFalse(CodePatternsScreenshotVisibility.checkboxProblem(button).isEmpty(), "Disposed control");
		});
	}

	@Test
	void clippingByAnOuterAncestorIsNotIgnored() {
		withCheckbox(button -> {
			Composite viewport= button.getParent().getParent();
			Shell shell= button.getShell();
			viewport.setLocation(shell.getClientArea().width - 15, 0);
			assertFalse(CodePatternsScreenshotVisibility.checkboxProblem(button).isEmpty(), "Clipped by shell");
		});
	}

	@Test
	void anOwnedDialogIsNotClippedToItsSmallerOwner() {
		withOwnedDialog((button, preview) -> {
			Shell dialog= button.getShell();
			Composite owner= dialog.getParent();
			Rectangle ownerArea= owner.getDisplay().map(owner, null, owner.getClientArea());
			for (var control : List.of(button, preview)) {
				Point size= control.getSize();
				Rectangle area= control.getDisplay().map(control, null, new Rectangle(0, 0, size.x, size.y));
				assertFalse(area.intersection(ownerArea).equals(area), "Fixture must exceed the owner window");
				assertEquals("", CodePatternsScreenshotVisibility.controlProblem(control));
			}
			assertEquals("", CodePatternsScreenshotVisibility.checkboxProblem(button));
		});
	}

	@Test
	void anOwnedDialogsOwnClientAreaStillClipsItsControls() {
		withOwnedDialog((button, preview) -> {
			Rectangle client= button.getShell().getClientArea();
			button.setLocation(client.width - button.getSize().x + 1, 10);
			assertTrue(CodePatternsScreenshotVisibility.checkboxProblem(button).contains("Shell viewport"),
					"The dialog itself must reject a clipped checkbox");
			button.setLocation(10, 10);
			assertEquals("", CodePatternsScreenshotVisibility.checkboxProblem(button));
			preview.setLocation(10, client.height - preview.getSize().y + 1);
			assertTrue(CodePatternsScreenshotVisibility.controlProblem(preview).contains("Shell viewport"),
					"The dialog itself must reject a clipped preview");
			preview.setLocation(10, 80);
			assertEquals("", CodePatternsScreenshotVisibility.controlProblem(preview));
		});
	}

	@Test
	void nativeTabFoldersKeepTheVisibleBottomOfTheirSelectedPage() {
		for (int position : new int[] { SWT.TOP, SWT.BOTTOM }) {
			withTabFolder(position, preview -> {
				assertEquals("", CodePatternsScreenshotVisibility.controlProblem(preview),
						"The selected page's visible bottom must not be clipped by the tab strip");
			});
		}
	}

	@Test
	void nativeTabFoldersStillRejectActuallyClippedPreviewControls() {
		for (int position : new int[] { SWT.TOP, SWT.BOTTOM }) {
			withTabFolder(position, preview -> {
				Rectangle page= preview.getParent().getClientArea();
				preview.setLocation(10, page.height - preview.getSize().y + 1);
				assertFalse(CodePatternsScreenshotVisibility.controlProblem(preview).isEmpty(), "Bottom edge");
				preview.setLocation(-1, 10);
				assertFalse(CodePatternsScreenshotVisibility.controlProblem(preview).isEmpty(), "Left edge");
				preview.setLocation(10, page.height - preview.getSize().y - 10);
				assertEquals("", CodePatternsScreenshotVisibility.controlProblem(preview));
			});
		}
	}

	private static void withTabFolder(int position, Consumer<StyledText> assertion) {
		Display.getDefault().syncExec(() -> {
			Shell shell= new Shell(Display.getDefault());
			try {
				shell.setSize(700, 500);
				TabFolder folder= new TabFolder(shell, position);
				folder.setBounds(10, 10, 600, 400);
				TabItem item= new TabItem(folder, SWT.NONE);
				item.setText("Code Patterns");
				Composite page= new Composite(folder, SWT.NONE);
				item.setControl(page);
				StyledText preview= new StyledText(page, SWT.BORDER);
				preview.setText("boolean empty = list.isEmpty();");
				shell.open();
				shell.layout(true, true);
				Rectangle client= page.getClientArea();
				assertTrue(client.width > 100 && client.height > 100, "The native tab page must be allocated");
				preview.setBounds(10, client.height - 70, client.width - 20, 60);
				assertion.accept(preview);
			} finally {
				shell.dispose();
			}
		});
	}

	private static void withOwnedDialog(BiConsumer<Button, StyledText> assertion) {
		Display.getDefault().syncExec(() -> {
			Shell owner= new Shell(Display.getDefault());
			try {
				owner.setSize(80, 80);
				owner.open();
				Shell dialog= new Shell(owner, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
				Button button= new Button(dialog, SWT.CHECK | SWT.LEFT);
				button.setText("Apply transformation rules from .sandbox-hint files");
				Point preferred= button.computeSize(SWT.DEFAULT, SWT.DEFAULT);
				dialog.setSize(preferred.x + 200, 300);
				button.setBounds(10, 10, preferred.x, preferred.y);
				StyledText preview= new StyledText(dialog, SWT.BORDER);
				preview.setText("boolean empty = list.isEmpty();");
				preview.setBounds(10, 80, preferred.x, 60);
				dialog.open();
				assertion.accept(button, preview);
			} finally {
				// Disposing the owner also disposes its owned dialog, including assertion failures.
				owner.dispose();
			}
		});
	}

	private static void withCheckbox(Consumer<Button> assertion) {
		Display.getDefault().syncExec(() -> {
			Shell shell= new Shell(Display.getDefault());
			try {
				ScrolledComposite viewport= new ScrolledComposite(shell, SWT.H_SCROLL | SWT.V_SCROLL);
				Composite content= new Composite(viewport, SWT.NONE);
				Button button= new Button(content, SWT.CHECK | SWT.LEFT);
				button.setText("Apply transformation rules from .sandbox-hint files");
				Point preferred= button.computeSize(SWT.DEFAULT, SWT.DEFAULT);
				int width= preferred.x + 100;
				shell.setSize(width + 100, 400);
				viewport.setBounds(10, 10, width, 200);
				content.setSize(width + 600, 1000);
				viewport.setContent(content);
				button.setBounds(10, 10, width + 500, preferred.y);
				shell.open();
				assertion.accept(button);
			} finally {
				shell.dispose();
			}
		});
	}
}
