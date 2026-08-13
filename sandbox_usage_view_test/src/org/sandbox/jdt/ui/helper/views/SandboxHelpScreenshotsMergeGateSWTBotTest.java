package org.sandbox.jdt.ui.helper.views;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SandboxHelpScreenshotsMergeGateSWTBotTest {

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
}
