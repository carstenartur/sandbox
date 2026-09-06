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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

/** Read-only geometry checks for the left-aligned Hint File checkbox. Call on the UI thread. */
@SuppressWarnings("nls")
final class CodePatternsScreenshotVisibility {

	private CodePatternsScreenshotVisibility() {
	}

	static String checkboxProblem(Button button) {
		if (button.isDisposed()) {
			return "Checkbox is disposed";
		}
		if ((button.getStyle() & SWT.CHECK) == 0 || button.getAlignment() != SWT.LEFT
				|| (button.getStyle() & (SWT.RIGHT_TO_LEFT | SWT.WRAP)) != 0 || button.getImage() != null) {
			return "Expected a left-aligned, unwrapped text checkbox";
		}
		Point preferred= button.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		Point allocated= button.getSize();
		if (allocated.x < preferred.x || allocated.y < preferred.y) {
			return "Checkbox content does not fit: preferred=" + preferred + ", allocated=" + allocated;
		}
		// JDT stretches all options to the longest label. Only trailing empty space may be clipped,
		// never the native indicator, complete text, or vertical extent of this left-aligned option.
		return regionProblem(button, new Rectangle(0, 0, preferred.x, allocated.y));
	}

	static String controlProblem(Control control) {
		if (control.isDisposed()) {
			return "Control is disposed";
		}
		Point size= control.getSize();
		return regionProblem(control, new Rectangle(0, 0, size.x, size.y));
	}

	private static String regionProblem(Control control, Rectangle area) {
		if (!control.isVisible() || area.width <= 0 || area.height <= 0) {
			return "Control is hidden or has an empty area: " + area;
		}
		Rectangle bounds= control.getDisplay().map(control, null, area);
		for (Composite parent= control.getParent(); parent != null; parent= parent.getParent()) {
			Rectangle viewport= parent.getDisplay().map(parent, null, parent.getClientArea());
			if (!bounds.intersection(viewport).equals(bounds)) {
				return "Content " + bounds + " is clipped by " + parent.getClass().getSimpleName()
						+ " viewport " + viewport;
			}
		}
		return "";
	}
}
