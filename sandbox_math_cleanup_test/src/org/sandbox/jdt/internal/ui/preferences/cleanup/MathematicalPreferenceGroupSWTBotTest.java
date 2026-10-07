/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.preferences.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Native regression for the Cocoa Group coordinate system used by showControl. */
@SuppressWarnings("nls")
class MathematicalPreferenceGroupSWTBotTest {
    private static final String TEXT = "Unsupported numerical combinations are diagnosed and left unchanged. "
            + "Estimates are not benchmark measurements.";

    @Test
    void preferencesHaveAPlainCompositeParentAndRetainTheirLayout() {
        onUi(() -> {
            Shell shell = new Shell(Display.getCurrent());
            try {
                Group frame = frame(shell);
                GridLayout layout = (GridLayout) frame.getLayout();
                Composite body = MathematicalPreferenceGroup.createBody(frame);
                assertEquals(Composite.class, body.getClass(), "Do not map child bounds from the native Group frame");
                assertSame(frame, body.getParent());
                assertSame(layout, body.getLayout(), "Move, rather than duplicate, the existing preference layout");
                assertEquals(frame.getFont(), body.getFont());
            } finally {
                shell.dispose();
            }
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 600, 900})
    void wrappingAndExcludedWarningsKeepTheExistingPreferredSize(int width) {
        onUi(() -> {
            Shell shell = new Shell(Display.getCurrent());
            try {
                Group original = frame(shell);
                Group framed = frame(shell);
                Composite body = MathematicalPreferenceGroup.createBody(framed);
                Label originalWarning = labels(original);
                Label framedWarning = labels(body);
                for (boolean excluded : new boolean[] {false, true, false}) {
                    ((GridData) originalWarning.getLayoutData()).exclude = excluded;
                    ((GridData) framedWarning.getLayoutData()).exclude = excluded;
                    originalWarning.setVisible(!excluded);
                    framedWarning.setVisible(!excluded);
                    assertEquals(original.computeSize(width, SWT.DEFAULT, true),
                            framed.computeSize(width, SWT.DEFAULT, true), "No extra margins or lost wrapping");
                }
            } finally {
                shell.dispose();
            }
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 600, 900})
    void nativeShowControlRevealsTheWholeBottomLabel(int width) {
        onUi(() -> {
            Shell shell = new Shell(Display.getCurrent());
            try {
                shell.setSize(width + 80, 320);
                ScrolledComposite scroll = new ScrolledComposite(shell, SWT.V_SCROLL | SWT.H_SCROLL);
                scroll.setBounds(10, 10, width, 180);
                Group frame = frame(scroll);
                Composite body = MathematicalPreferenceGroup.createBody(frame);
                Label bottom = labels(body);
                scroll.setContent(frame);
                scroll.setExpandHorizontal(true);
                scroll.setExpandVertical(true);
                shell.open();
                scroll.setMinSize(0, frame.computeSize(scroll.getClientArea().width, SWT.DEFAULT, true).y);
                scroll.layout(true, true);
                Rectangle mapped = shell.getDisplay().map(bottom.getParent(), scroll, bottom.getBounds());
                Point physical = scroll.toControl(bottom.toDisplay(0, 0));
                assertEquals(physical, new Point(mapped.x, mapped.y),
                        "showControl's parent mapping must agree with the actual child position");
                scroll.showControl(bottom);
                Rectangle client = scroll.getClientArea();
                Point topLeft = scroll.toControl(bottom.toDisplay(0, 0));
                Point bottomRight = scroll.toControl(bottom.toDisplay(bottom.getSize().x - 1, bottom.getSize().y - 1));
                assertTrue(scroll.getOrigin().y > 0, "The fixture must actually require vertical scrolling");
                assertTrue(client.contains(topLeft) && client.contains(bottomRight),
                        () -> "The whole label must be visible: " + topLeft + ".." + bottomRight + " in " + client);
            } finally {
                shell.dispose();
            }
        });
    }

    private static Group frame(Composite parent) {
        Group frame = new Group(parent, SWT.NONE);
        frame.setText("Verified mathematical calculations");
        GridLayout layout = new GridLayout(4, false);
        layout.marginWidth = 7;
        layout.marginHeight = 9;
        frame.setLayout(layout);
        return frame;
    }

    private static Label labels(Composite parent) {
        Label last = label(parent);
        for (int i = 1; i < 12; i++) {
            last = label(parent);
        }
        return last;
    }

    private static Label label(Composite parent) {
        Label label = new Label(parent, SWT.WRAP);
        label.setText(TEXT);
        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1);
        data.widthHint = 440;
        label.setLayoutData(data);
        return label;
    }

    private static void onUi(Runnable operation) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Display.getDefault().syncExec(() -> {
            try {
                operation.run();
            } catch (RuntimeException | Error problem) {
                failure.set(problem);
            }
        });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof RuntimeException error) throw error;
    }
}
