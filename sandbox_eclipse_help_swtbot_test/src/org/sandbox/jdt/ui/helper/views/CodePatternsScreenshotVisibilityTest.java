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

import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
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
