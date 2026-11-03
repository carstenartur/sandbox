/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.swt.custom;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/** Native process fixture: observes the real callback and always delegates its side effects. */
public final class RendererScrollProbe extends StyledText {
    private boolean observing;
    private int calls, observedPixels, observedY, observedHeight;

    private RendererScrollProbe(Composite parent) { super(parent, SWT.MULTI | SWT.V_SCROLL); }

    @Override boolean scrollVertical(int pixels, boolean adjustScrollBar) {
        if (observing) {
            calls++;
            observedPixels = pixels;
            observedY = topIndexY;
            observedHeight = renderer.getLineHeight();
        }
        return super.scrollVertical(pixels, adjustScrollBar);
    }

    public static void main(String[] args) {
        Display display = new Display();
        try {
            for (int y : new int[] {-4, 0, 4}) {
                Shell shell = new Shell(display);
                try {
                    RendererScrollProbe text = new RendererScrollProbe(shell);
                    text.setBounds(0, 0, 300, 100);
                    text.setText("one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten");
                    text.renderer.reset(0, text.getLineCount());
                    text.renderer.ascent = 0;
                    text.renderer.descent = 0;
                    text.topIndex = 2;
                    text.topIndexY = y;
                    text.verticalScrollOffset = 1;
                    text.observing = true;
                    TextLayout layout = text.renderer.getTextLayout(0, SWT.LEFT_TO_RIGHT, -1, 0);
                    text.observing = false;
                    if (text.calls != 1 || text.observedY != y || text.observedHeight <= 0)
                        throw new AssertionError("Scroll branch not reached: calls=" + text.calls + " y=" + text.observedY + " expected=" + y);
                    System.out.println("SWT_SCROLL y=" + y + " pixels=" + text.observedPixels + " height=" + text.observedHeight
                            + " offset=" + text.verticalScrollOffset + " top=" + text.topIndex + " topY=" + text.topIndexY
                            + " layout=" + layout.getBounds());
                    text.renderer.disposeTextLayout(layout);
                } finally { shell.dispose(); }
            }
        } finally { display.dispose(); }
    }
}
