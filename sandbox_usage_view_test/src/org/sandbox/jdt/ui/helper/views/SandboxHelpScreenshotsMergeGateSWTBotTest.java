package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.ltk.core.refactoring.IUndoManager;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseBundleClasspath;

public class SandboxHelpScreenshotsMergeGateSWTBotTest {

    private static final String CLEANUP_PREVIEW_PROJECT = "SandboxCleanupPreviewProject";
    private static final List<String> SHADOW_PLATFORM_SOURCES = List.of(
            "src/org/eclipse/core/runtime/IProgressMonitor.java",
            "src/org/eclipse/core/runtime/SubMonitor.java",
            "src/org/eclipse/core/runtime/SubProgressMonitor.java",
            "src/org/eclipse/jface/viewers/ViewerComparator.java",
            "src/org/eclipse/jface/viewers/ViewerSorter.java");

    private static SandboxHelpScreenshotsSWTBotTest screenshots;

    @BeforeAll
    public static void setUp() throws Exception {
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
    public void captureCleanupConfigurationTabs() throws IOException {
        screenshots.captureCleanupConfigurationTabs();
    }

    @Test
    public void captureCssCleanupPreferences() throws IOException {
        screenshots.captureCssCleanupPreferences();
    }

    @Test
    public void captureRuleInferencePreferences() throws IOException {
        screenshots.captureRuleInferencePreferences();
    }

    @Test
    public void captureRefactoringMiningWorkflow() throws Exception {
        screenshots.captureRefactoringMiningWorkflow();
    }

    @Test
    public void captureNewHintRuleWizard() throws Exception {
        screenshots.captureNewHintRuleWizard();
    }

    @Test
    public void captureRealCleanupPreviewAndVerifyIndependentSelection() throws Exception {
        IUndoManager undoManager = RefactoringCore.getUndoManager();
        undoManager.flush();
        try {
            IProject previewProject = ResourcesPlugin.getWorkspace().getRoot().getProject(CLEANUP_PREVIEW_PROJECT);
            useRealTargetPlatformBindings(previewProject);

            IFile singleFile = previewProject.getFile("src/demo/single/SingleFileCleanup.java");
            assertTrue(singleFile.exists(), "The deterministic single-file preview fixture must exist");
            String before = readFile(singleFile);

            screenshots.captureRealCleanupPreviewAndVerifyIndependentSelection();

            assertTrue(undoManager.anythingToUndo(),
                    "The single-file Cleanup operation must remain available for aggregate undo verification");
            undoManager.performUndo(null, new NullProgressMonitor());
            assertEquals(before, readFile(singleFile),
                    "Undo must restore the single-file preview fixture byte-for-byte");
        } finally {
            undoManager.flush();
        }
    }

    private static void useRealTargetPlatformBindings(IProject project) throws Exception {
        assertTrue(project.exists(), "The deterministic Cleanup preview project must exist");
        NullProgressMonitor monitor = new NullProgressMonitor();
        for (String path : SHADOW_PLATFORM_SOURCES) {
            IFile source = project.getFile(path);
            if (source.exists()) {
                source.delete(true, monitor);
            }
        }

        EclipseBundleClasspath.addBundles(JavaCore.create(project),
                "org.eclipse.equinox.common",
                "org.eclipse.jface",
                "org.eclipse.swt");
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
                "The real target-platform Cleanup preview fixture must compile before SWTBot QA:\n" + errors);
    }

    private static String readFile(IFile file) throws Exception {
        try (InputStream input = file.getContents()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
