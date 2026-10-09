/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.ui.helper.views;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.junit.jupiter.api.Test;

/** Focused local entry point; the normal Help merge gate invokes the same driver. */
public class MathematicsHelpScreenshotsSWTBotTest {
    @Test
    public void captureRealMathematicsExamples() throws Exception {
        new SWTWorkbenchBot().views().stream().filter(view -> "Welcome".equals(view.getTitle())) //$NON-NLS-1$
                .forEach(view -> view.close());
        MathematicsHelpScreenshots.capture();
    }
}
