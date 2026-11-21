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

import java.util.function.BooleanSupplier;

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
                int[] paints = { 0 };
                original.addPaintListener(event -> {
                    paints[0]++;
                    if (original.getTopIndex() > 0) {
                        // Native compare repaint can restore its initially selected difference.
                        original.setTopIndex(0);
                        restored[0] = true;
                    }
                });
                shell.open();
                shell.layout(true, true);
                shell.update();

                int[] updates = { 0 };
                assertFalse(PinnedFilePreviewDriver.showVisibleDiff(shell, "@Rule", "@RegisterExtension", true, pane -> {
                    updates[0]++;
                    pane.update();
                    awaitRepaint(shell.getDisplay(), () -> restored[0], "The source pane must repaint and restore its scroll position");
                }),
                        "A fragment visible at a different scroll position is not the requested capture");
                assertEquals(1, updates[0], "The negative case must probe a single repaint boundary");
                assertTrue(paints[0] > 0, "The regression must receive a native Paint event");
                assertTrue(restored[0], "The regression must exercise a real repaint and scroll change");
                assertEquals(0, original.getTopIndex());
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
                int[] paints = { 0 };
                latePaint.addPaintListener(event -> {
                    paints[0]++;
                    if (restoreRequested[0]) {
                        original.setTopIndex(0);
                        restoreRequested[0] = false;
                        restored[0] = true;
                    }
                });
                shell.open();
                shell.layout(true, true);
                shell.update();

                int[] positions = { 0 };
                int[] updates = { 0 };
                assertFalse(PinnedFilePreviewDriver.positionComparisonForCapture(shell, () -> {
                    positions[0]++;
                    boolean positioned = PinnedFilePreviewDriver.showVisibleDiff(shell, "@Rule", "@RegisterExtension");
                    assertTrue(positioned, "The requested annotations must be positioned before the later paint");
                    assertTrue(original.getTopIndex() > 0);
                    restoreRequested[0] = true;
                    latePaint.redraw();
                    return positioned;
                }, () -> {
                    updates[0]++;
                    shell.getDisplay().update();
                    awaitRepaint(shell.getDisplay(), () -> restored[0], "The later display paint must restore the old scroll position");
                }), "A later paint cannot restore an old stable viewport and still pass capture readiness");
                assertEquals(1, positions[0], "The negative case must position the comparison once");
                assertEquals(1, updates[0], "The negative case must probe a single display update boundary");
                assertTrue(paints[0] > 0, "The negative case must receive a native Paint event");
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

    private static void awaitRepaint(Display display, BooleanSupplier observed, String failureMessage) {
        // SWT update() may defer native painting, notably on GTK. Only the test pumps
        // events at the guarded boundary; production capture waits across UI turns.
        boolean[] timedOut = { false };
        Runnable deadline = () -> timedOut[0] = true;
        display.timerExec(5_000, deadline);
        try {
            while (!observed.getAsBoolean() && !timedOut[0]) {
                if (!display.readAndDispatch()) {
                    display.sleep();
                }
            }
            assertTrue(observed.getAsBoolean(), failureMessage);
        } finally {
            display.timerExec(-1, deadline);
        }
    }

    private static StyledText sourcePane(Shell shell, String annotation) {
        StyledText pane = new StyledText(shell, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
        pane.setEditable(false);
        // The annotation remains visible at both scroll positions, so visibility alone is insufficient.
        pane.setText("header\n".repeat(8) + annotation + "\n" + "following line\n".repeat(80));
        return pane;
    }
}
