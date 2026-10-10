/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;
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

    @Test
    public void profileRecordsEveryRegisteredMathematicsOption() throws Exception {
        Map<String, String> defaults = JavaPlugin.getDefault().getCleanUpRegistry()
                .getDefaultOptions(CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS).getMap();
        Map<String, String> expected = new TreeMap<>();
        defaults.forEach((key, value) -> {
            if (key.startsWith("cleanup.mathematics")) expected.put(key, value); //$NON-NLS-1$
        });
        Properties properties = new Properties();
        properties.setProperty("kinds", expected.get("cleanup.mathematics.numericKinds")); //$NON-NLS-1$ //$NON-NLS-2$
        properties.setProperty("goal", expected.get("cleanup.mathematics.goal")); //$NON-NLS-1$ //$NON-NLS-2$
        properties.setProperty("workBudget", expected.get("cleanup.mathematics.workBudget")); //$NON-NLS-1$ //$NON-NLS-2$
        properties.setProperty("maxStates", expected.get("cleanup.mathematics.maxStates")); //$NON-NLS-1$ //$NON-NLS-2$
        expected.put("cleanup.mathematics", "true"); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("NONTRIVIAL", expected.get("cleanup.mathematics.explanations")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(expected, MathematicsHelpScreenshots.profile(properties), "Provenance must record the complete effective profile"); //$NON-NLS-1$
        properties.setProperty("explanations", "ALL"); //$NON-NLS-1$ //$NON-NLS-2$
        expected.put("cleanup.mathematics.explanations", "ALL"); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(expected, MathematicsHelpScreenshots.profile(properties), "Explicit fixture settings must be recorded as applied"); //$NON-NLS-1$
    }

}
