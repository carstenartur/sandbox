/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.preferences.cleanup;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

/** Keeps preference bounds in a plain Composite coordinate system on every OS. */
final class MathematicalPreferenceGroup {
    private MathematicalPreferenceGroup() { }

    static Composite createBody(Group frame) {
        // On Cocoa, Group uses separate native frame and content views. SWT's
        // showControl maps from the parent's frame, whereas child bounds belong
        // to the content view. A plain parent avoids losing the group's title
        // offset without replacing native scrolling or adding pixel tolerances.
        Composite body = new Composite(frame, SWT.NONE);
        body.setFont(frame.getFont());
        body.setLayout(frame.getLayout());
        frame.setLayout(new FillLayout());
        return body;
    }
}
