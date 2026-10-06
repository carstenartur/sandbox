/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.preferences.cleanup;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

/** Readable explanatory controls in the host's horizontally scrollable page. */
final class MathematicalPreferenceControls {
	private MathematicalPreferenceControls() {
	}

	static Label wrappedLabel(int columns, Composite parent, String text) {
		Label label= new Label(parent, SWT.WRAP);
		label.setText(text);
		label.setLayoutData(readableColumn(columns));
		return label;
	}

	static Button unavailableOption(int columns, Composite parent, String text) {
		Button button= new Button(parent, SWT.CHECK | SWT.WRAP);
		button.setText(text);
		button.setEnabled(false);
		button.setLayoutData(readableColumn(columns));
		return button;
	}

	private static GridData readableColumn(int columns) {
		// Do not stretch text to a wider host column: scrolling cannot reveal an
		// entire control that is itself wider than the viewport.
		GridData data= new GridData(SWT.BEGINNING, SWT.CENTER, true, false, columns, 1);
		data.widthHint= 440;
		data.minimumWidth= 0;
		return data;
	}
}
