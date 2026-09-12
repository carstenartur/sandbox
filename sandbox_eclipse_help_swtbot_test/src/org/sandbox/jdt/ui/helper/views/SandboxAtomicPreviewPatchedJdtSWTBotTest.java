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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
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

import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.osgi.framework.FrameworkUtil;

/**
 * Runs coordinated preview scenarios only in the optional product path that
 * installs the pinned JDT UI and LTK replacement bundles.
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
        verifyPatchedLtkRuntime();

        SandboxHelpScreenshotsSWTBotTest.setUp();
        screenshots = new SandboxHelpScreenshotsSWTBotTest();
        CoordinatedJUnitPreviewSWTBotScenario.prepareFixture();
        preparePreviewFixture();
    }

    private static void verifyPatchedLtkRuntime() throws Exception {
        String evidencePath = System.getenv("SANDBOX_LTK_PATCH_EVIDENCE");
        assertNotNull(evidencePath, "The optional atomic host requires Maven-verified LTK provenance");
        Properties evidence = new Properties();
        try (var input = Files.newInputStream(Path.of(evidencePath))) {
            evidence.load(input);
        }
        var ltk = Platform.getBundle("org.eclipse.ltk.core.refactoring");
        assertNotNull(ltk, "The LTK bundle must be installed");
        assertEquals(ltk, FrameworkUtil.getBundle(CompositeChange.class),
                "The test must use the resolved replacement, not another installed LTK bundle");
        assertEquals(evidence.getProperty("bundleVersion"), ltk.getVersion().toString(),
                "Loaded LTK version must match the verified build");
        try (var input = CompositeChange.class.getResourceAsStream("CompositeChange.class")) {
            assertNotNull(input, "The loaded LTK class bytes must be readable");
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            assertEquals(evidence.getProperty("implementationClassSha256"), digest,
                    "Loaded LTK implementation bytes must match the Maven-tested artifact");
        }
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("LtkFileCountProbe");
        CompositeChange probe = new CompositeChange("Distinct affected files");
        probe.add(new TextFileChange("first", project.getFile("First.java")));
        probe.add(new TextFileChange("second", project.getFile("Second.java")));
        try {
            assertEquals(2, probe.getFilenumber(), "The screenshot host must count two real file identities, not one node");
        } finally {
            probe.dispose();
        }
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
            CleanupWorkbenchDriver.run(CleanupScreenshotScenarios.INT_TO_ENUM, PreviewContract.ATOMIC_CANDIDATE,
                    screenshots::coordinatedIntToEnumPreviewIsAtomic);
        } catch (AssertionError | RuntimeException failure) {
            printWorkspaceLog();
            throw failure;
        }
    }

    @Test
    public void coordinatedJUnitCandidatesAreAtomicAndIndependentlySelectable() throws Exception {
        try {
            CoordinatedJUnitPreviewSWTBotScenario.run();
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
