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

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;

@SuppressWarnings("nls")
class PinnedFilePreviewPositionTest {

    @Test
    void repaintThatRestoresAnEarlierScrollPositionIsNotReadyForCapture() {
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault());
            try {
                shell.setLayout(new FillLayout(SWT.HORIZONTAL));
                shell.setSize(800, 500);
                StyledText original = sourcePane(shell, "@Rule");
                sourcePane(shell, "@RegisterExtension");
                boolean[] restored = { false };
                original.addPaintListener(event -> {
                    if (original.getTopIndex() > 0) {
                        // Native compare repaint can restore its initially selected difference.
                        original.setTopIndex(0);
                        restored[0] = true;
                    }
                });
                shell.open();
                shell.layout(true, true);
                shell.update();

                assertFalse(PinnedFilePreviewDriver.showVisibleDiff(shell, "@Rule", "@RegisterExtension"),
                        "A fragment visible at a different scroll position is not the requested capture");
                assertTrue(restored[0], "The regression must exercise a real repaint and scroll change");
            } finally {
                shell.dispose();
            }
        });
    }

    @Test
    void repaintAfterSuccessfulPositioningMustSurviveTheDisplayUpdate() {
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault());
            try {
                shell.setLayout(new FillLayout(SWT.HORIZONTAL));
                shell.setSize(1_200, 500);
                StyledText original = sourcePane(shell, "@Rule");
                sourcePane(shell, "@RegisterExtension");
                Canvas latePaint = new Canvas(shell, SWT.NONE);
                boolean[] restoreRequested = { false };
                boolean[] restored = { false };
                latePaint.addPaintListener(event -> {
                    if (restoreRequested[0]) {
                        original.setTopIndex(0);
                        restoreRequested[0] = false;
                        restored[0] = true;
                    }
                });
                shell.open();
                shell.layout(true, true);
                shell.update();

                assertFalse(PinnedFilePreviewDriver.positionComparisonForCapture(shell, () -> {
                    boolean positioned = PinnedFilePreviewDriver.showVisibleDiff(shell, "@Rule", "@RegisterExtension");
                    assertTrue(positioned, "The requested annotations must be positioned before the later paint");
                    assertTrue(original.getTopIndex() > 0);
                    restoreRequested[0] = true;
                    latePaint.redraw();
                    return positioned;
                }), "A later paint cannot restore an old stable viewport and still pass capture readiness");
                assertTrue(restored[0], "The negative case must exercise the later display paint");
                assertEquals(0, original.getTopIndex());
                assertTrue(PinnedFilePreviewDriver.positionComparisonForCapture(shell, () ->
                        PinnedFilePreviewDriver.showVisibleDiff(shell, "@Rule", "@RegisterExtension")),
                        "A subsequent attempt succeeds once native repaint stops changing the position");
            } finally {
                shell.dispose();
            }
        });
    }

    private static StyledText sourcePane(Shell shell, String annotation) {
        StyledText pane = new StyledText(shell, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
        pane.setEditable(false);
        // The annotation remains visible at both scroll positions, so visibility alone is insufficient.
        pane.setText("header\n".repeat(8) + annotation + "\n" + "following line\n".repeat(80));
        return pane;
    }
}
