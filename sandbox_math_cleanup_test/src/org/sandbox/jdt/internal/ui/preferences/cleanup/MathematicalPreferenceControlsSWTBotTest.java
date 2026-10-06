/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.preferences.cleanup;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Reproduces a preference column wider than the visible macOS scroll area. */
class MathematicalPreferenceControlsSWTBotTest {
	private static final String DIAGNOSTICS= "Unsupported numerical combinations are diagnosed and left unchanged. Estimates are not benchmark measurements."; //$NON-NLS-1$
	private static final String UNDERFLOW= "Detect tiny-and-inexact underflow (unsupported)"; //$NON-NLS-1$

	@ParameterizedTest
	@ValueSource(ints = { 500, 597, 620 })
	void explanationCanBeRevealedWithoutClipping(int viewportWidth) {
		verifyReachable(viewportWidth, false);
	}

	@ParameterizedTest
	@ValueSource(ints = { 500, 597, 620 })
	void disabledOptionCanBeRevealedWithoutClipping(int viewportWidth) {
		verifyReachable(viewportWidth, true);
	}

	private static void verifyReachable(int viewportWidth, boolean checkbox) {
		Display display= Display.getDefault();
		display.syncExec(() -> {
			Shell shell= new Shell(display);
			try {
				ScrolledComposite scroll= new ScrolledComposite(shell, SWT.H_SCROLL | SWT.V_SCROLL);
				scroll.setBounds(0, 0, viewportWidth, 200);
				Composite content= new Composite(scroll, SWT.NONE);
				GridLayout layout= new GridLayout(1, false);
				layout.marginWidth= 4;
				content.setLayout(layout);
				Control control= checkbox
						? MathematicalPreferenceControls.unavailableOption(1, content, UNDERFLOW)
						: MathematicalPreferenceControls.wrappedLabel(1, content, DIAGNOSTICS);
				// The native host may retain a wider column for its other preferences.
				content.setSize(viewportWidth + 32, 160);
				scroll.setContent(content);
				content.layout(true, true);
				scroll.showControl(control);
				var viewport= display.map(scroll, null, scroll.getClientArea());
				var bounds= display.map(content, null, control.getBounds());
				assertTrue(bounds.x >= viewport.x && bounds.x + bounds.width <= viewport.x + viewport.width,
						() -> "The whole control must fit after reveal: " + bounds + " in " + viewport); //$NON-NLS-1$ //$NON-NLS-2$
				assertTrue(control.getSize().y >= control.computeSize(control.getSize().x, SWT.DEFAULT).y,
						"The entire wrapped text must remain visible"); //$NON-NLS-1$
				if (control instanceof Button button) {
					assertFalse(button.getEnabled(), "An unsupported option must remain disabled"); //$NON-NLS-1$
					assertFalse(button.getSelection());
				}
			} finally {
				shell.dispose();
			}
		});
	}
}
