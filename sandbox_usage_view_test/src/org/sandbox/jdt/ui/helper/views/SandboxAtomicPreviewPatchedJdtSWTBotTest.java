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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.Job;

import org.eclipse.jdt.core.IJavaModelMarker;

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
    private static final String CLEANUP_PREVIEW_PROJECT = "SandboxCleanupPreviewProject";

    private static SandboxHelpScreenshotsSWTBotTest screenshots;

    @BeforeAll
    public static void setUp() throws Exception {
        var jdtUi = Platform.getBundle(JDT_UI_BUNDLE);
        assertNotNull(jdtUi, "The JDT UI bundle must be installed in the SWTBot runtime");
        jdtUi.loadClass(COORDINATED_CHANGE);

        SandboxHelpScreenshotsSWTBotTest.setUp();
        screenshots = new SandboxHelpScreenshotsSWTBotTest();
        preparePreviewFixture();
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
        try {
            screenshots.coordinatedIntToEnumPreviewIsAtomic();
        } catch (AssertionError | RuntimeException failure) {
            printWorkspaceLog();
            throw failure;
        }
    }

    private static void preparePreviewFixture() throws Exception {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(CLEANUP_PREVIEW_PROJECT);
        assertTrue(project.exists(), "The deterministic coordinated Cleanup preview project must exist");

        NullProgressMonitor monitor = new NullProgressMonitor();
        ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, monitor);
        Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, monitor);
        assertNoJavaErrors(project);
    }

    private static void assertNoJavaErrors(IProject project) throws Exception {
        IMarker[] markers = project.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER,
                true, IResource.DEPTH_INFINITE);
        String errors = Stream.of(markers)
                .filter(marker -> marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO)
                        == IMarker.SEVERITY_ERROR)
                .map(marker -> marker.getResource().getProjectRelativePath()
                        + ":" + marker.getAttribute(IMarker.LINE_NUMBER, -1)
                        + ": " + marker.getAttribute(IMarker.MESSAGE, "Unknown Java problem"))
                .collect(Collectors.joining("\n"));
        assertTrue(errors.isEmpty(),
                "The coordinated Cleanup preview fixture must compile before SWTBot QA:\n" + errors);
    }

    private static void printWorkspaceLog() {
        try {
            Path log = Platform.getLogFileLocation().toFile().toPath();
            if (Files.isRegularFile(log)) {
                System.out.println("[help-screenshots] Eclipse workspace log after failure:\n"
                        + Files.readString(log));
            } else {
                System.out.println("[help-screenshots] Eclipse workspace log does not exist: " + log);
            }
        } catch (IOException | RuntimeException exception) {
            System.out.println("[help-screenshots] Could not read the Eclipse workspace log: "
                    + exception.getMessage());
        }
    }
}
