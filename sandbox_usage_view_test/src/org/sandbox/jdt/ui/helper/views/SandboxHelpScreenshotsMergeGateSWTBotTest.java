package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.ltk.core.refactoring.IUndoManager;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SandboxHelpScreenshotsMergeGateSWTBotTest {

    private static final String CLEANUP_PREVIEW_PROJECT = "SandboxCleanupPreviewProject";

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
        IFile singleFile = ResourcesPlugin.getWorkspace().getRoot()
                .getProject(CLEANUP_PREVIEW_PROJECT)
                .getFile("src/demo/single/SingleFileCleanup.java");
        assertTrue(singleFile.exists(), "The deterministic single-file preview fixture must exist");
        String before = readFile(singleFile);

        screenshots.captureRealCleanupPreviewAndVerifyIndependentSelection();

        IUndoManager undoManager = RefactoringCore.getUndoManager();
        assertTrue(undoManager.anythingToUndo(),
                "The single-file Cleanup operation must remain available for aggregate undo verification");
        undoManager.performUndo(null, new NullProgressMonitor());
        assertEquals(before, readFile(singleFile),
                "Undo must restore the single-file preview fixture byte-for-byte");
    }

    private static String readFile(IFile file) throws Exception {
        try (InputStream input = file.getContents()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
