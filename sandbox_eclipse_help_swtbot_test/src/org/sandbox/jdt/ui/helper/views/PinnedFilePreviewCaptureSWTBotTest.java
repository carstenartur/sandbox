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

import java.nio.file.Path;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.results.Result;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises native screenshot readiness across real SWT event turns. */
class PinnedFilePreviewCaptureSWTBotTest {

    @Test
    void captureWaitsForLateComparePaintingWithoutViewportChanges(@TempDir Path temporary) {
        Display display = Display.getDefault();
        Shell[] shell = new Shell[1];
        Canvas[] connector = new Canvas[1];
        Point[] pixel = new Point[1];
        RGB[] expected = new RGB[1];
        boolean[] blue = { false };
        boolean[] paintedBlue = { false };
        int[] positions = { 0 };
        display.syncExec(() -> {
            shell[0] = new Shell(display);
            shell[0].setLayout(new FillLayout(SWT.HORIZONTAL));
            shell[0].setBounds(30, 30, 900, 500);
            sourcePane(shell[0], "@Rule"); //$NON-NLS-1$
            sourcePane(shell[0], "@RegisterExtension"); //$NON-NLS-1$
            connector[0] = new Canvas(shell[0], SWT.NONE);
            connector[0].addPaintListener(event -> {
                event.gc.setBackground(display.getSystemColor(blue[0] ? SWT.COLOR_BLUE : SWT.COLOR_RED));
                event.gc.fillRectangle(connector[0].getClientArea());
                paintedBlue[0] |= blue[0];
            });
            shell[0].open();
            shell[0].layout(true, true);
            connector[0].setFocus();
            var bounds = connector[0].getClientArea();
            pixel[0] = display.map(connector[0], shell[0], new Point(bounds.width / 2, bounds.height / 2));
            expected[0] = display.getSystemColor(SWT.COLOR_BLUE).getRGB();
        });
        try {
            Path image = temporary.resolve("late-compare-paint.png"); //$NON-NLS-1$
            SWTWorkbenchBot bot = new SWTWorkbenchBot();
            PinnedFilePreviewDriver.capture(new SWTBotShell(shell[0]), bot, image, () -> {
                boolean positioned = PinnedFilePreviewDriver.showVisibleDiff(shell[0], "@Rule", "@RegisterExtension"); //$NON-NLS-1$ //$NON-NLS-2$
                if (positioned && ++positions[0] == 2) {
                    // Compare connectors can paint on a later UI turn without changing either source pane.
                    display.asyncExec(() -> {
                        blue[0] = true;
                        connector[0].redraw();
                    });
                }
                return positioned;
            });
            bot.waitUntil(new DefaultCondition() {
                @Override public boolean test() {
                    return UIThreadRunnable.syncExec(display, (Result<Boolean>) () -> paintedBlue[0]);
                }
                @Override public String getFailureMessage() {
                    return "The regression must execute the queued native connector repaint"; //$NON-NLS-1$
                }
            });
            ImageData captured = new ImageData(image.toString());
            assertEquals(expected[0], captured.palette.getRGB(captured.getPixel(pixel[0].x, pixel[0].y)),
                    "Stable source viewports do not prove that the native comparison has finished painting"); //$NON-NLS-1$
        } finally {
            display.syncExec(() -> shell[0].dispose());
        }
    }

    private static void sourcePane(Shell shell, String annotation) {
        StyledText pane = new StyledText(shell, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
        pane.setEditable(false);
        pane.setText("header\n".repeat(8) + annotation + "\n" + "following line\n".repeat(80)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }
}
