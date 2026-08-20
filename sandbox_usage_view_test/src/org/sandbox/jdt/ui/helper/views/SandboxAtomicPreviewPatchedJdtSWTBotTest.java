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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.eclipse.core.runtime.Platform;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs the coordinated Int-to-Enum preview only in the optional product path
 * that installs the pinned JDT UI replacement bundle.
 */
public class SandboxAtomicPreviewPatchedJdtSWTBotTest {

    private static final String JDT_UI_BUNDLE = "org.eclipse.jdt.ui";
    private static final String COORDINATED_CHANGE =
            "org.eclipse.jdt.internal.corext.fix.CoordinatedCleanUpChange";

    private static SandboxHelpScreenshotsSWTBotTest screenshots;

    @BeforeAll
    public static void setUp() throws Exception {
        var jdtUi = Platform.getBundle(JDT_UI_BUNDLE);
        assertNotNull(jdtUi, "The JDT UI bundle must be installed in the SWTBot runtime");
        jdtUi.loadClass(COORDINATED_CHANGE);

        SandboxHelpScreenshotsSWTBotTest.setUp();
        screenshots = new SandboxHelpScreenshotsSWTBotTest();
    }

    @AfterEach
    public void closeTransientDialogs() {
        screenshots.closeTransientDialogs();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        SandboxHelpScreenshotsSWTBotTest.tearDown();
    }

    @Test
    public void coordinatedIntToEnumPreviewIsAtomic() throws Exception {
        screenshots.coordinatedIntToEnumPreviewIsAtomic();
    }
}
