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

import java.util.Set;

import org.junit.jupiter.api.Test;

/** Replays the installed headless Status cleanup against the same pinned Workbench source. */
public class PinnedStatusPreviewSWTBotTest {
    private static final String SOURCE = "ui/org/eclipse/jdt/internal/ui/text/java/ProposalSorterHandle.java";

    @Test
    public void comparePinnedStatusPreviewWithHeadlessPatchAndUndo() throws Exception {
        var scenario = new CleanupScreenshotScenario("pinned-platform-status", PreviewContract.FILE_COMBINED_DIFF,
                "org.eclipse.jdt.ui", Set.of(SOURCE), Set.of(SOURCE));
        PinnedFilePreviewDriver.compare(scenario, SOURCE, "platform-status.properties", "org.eclipse.core.runtime.Status",
                "JavaPlugin.getPluginId(), IStatus.OK, message", "JavaPlugin.getPluginId(), message", original -> {
                    assertEquals(6, original.split("IStatus\\.OK, ", -1).length - 1);
                    return original.replace("IStatus.OK, ", "");
                }, "", "platform-status-pinned", "sandbox_platform_helper_help");
    }
}
