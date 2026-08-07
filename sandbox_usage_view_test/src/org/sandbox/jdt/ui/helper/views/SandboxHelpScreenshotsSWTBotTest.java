/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Generates the screenshots embedded in the independently installable Eclipse
 * Help bundles.
 * <p>
 * The screenshots deliberately come from a real Eclipse workbench. Run with:
 * </p>
 *
 * <pre>
 * xvfb-run --auto-servernum mvn -Phelp-screenshots \
 *     -pl sandbox_usage_view_test -am clean verify
 * </pre>
 */
public class SandboxHelpScreenshotsSWTBotTest {

    private record CleanupTab(String label, String helpBundle, String fileName) {
    }

    private static final List<CleanupTab> CLEANUP_TABS = List.of(
            new CleanupTab("Explicit Encoding (Sandbox)", "sandbox_encoding_quickfix_help",
                    "explicit-encoding-cleanup.png"),
            new CleanupTab("Platform Status (Sandbox)", "sandbox_platform_helper_help",
                    "platform-status-cleanup.png"),
            new CleanupTab("Functional Converter (Sandbox)", "sandbox_functional_converter_help",
                    "functional-converter-cleanup.png"),
            new CleanupTab("Code Patterns (Sandbox)", "sandbox_triggerpattern_help",
                    "code-patterns-cleanup.png"),
            new CleanupTab("XML Cleanup (Sandbox)", "sandbox_xml_cleanup_help",
                    "xml-cleanup.png"),
            new CleanupTab("JFace Cleanup (Sandbox)", "sandbox_jface_cleanup_help",
                    "jface-cleanup.png"),
            new CleanupTab("JUnit Migration (Sandbox)", "sandbox_junit_cleanup_help",
                    "junit-migration-cleanup.png"),
            new CleanupTab("Method Reuse (Sandbox)", "sandbox_method_reuse_help",
                    "method-reuse-cleanup.png"),
            new CleanupTab("Int to Enum (Sandbox)", "sandbox_int_to_enum_help",
                    "int-to-enum-cleanup.png"),
            new CleanupTab("Use General Type (Sandbox)", "sandbox_use_general_type_help",
                    "use-general-type-cleanup.png"));

    private static final String OUTPUT_PROPERTY = "sandbox.help.screenshot.output";
    private static SWTWorkbenchBot bot;
    private static Path outputRoot;

    @BeforeAll
    public static void setUp() throws IOException {
        bot = new SWTWorkbenchBot();
        outputRoot = Path.of(System.getProperty(OUTPUT_PROPERTY,
                Path.of("target", "help-screenshots").toString())).toAbsolutePath();
        Files.createDirectories(outputRoot);
        closeWelcomeView();
    }

    @AfterAll
    public static void tearDown() {
        closeDialogIfOpen();
    }

    @Test
    public void captureCleanupConfigurationTabs() throws IOException {
        openPreferences();
        SWTBotShell preferences = bot.shell("Preferences").activate();
        selectPreferencePath(preferences.bot().tree(), "Java", "Code Style", "Clean Up");
        clickButton(preferences, "Edit...", "Edit\u2026");

        SWTBotShell profileDialog = bot.activeShell();
        profileDialog.activate();
        profileDialog.maximize();

        for (CleanupTab tab : CLEANUP_TABS) {
            profileDialog.bot().tabItem(tab.label()).activate();
            bot.sleep(300);
            capture(tab.helpBundle(), tab.fileName());
        }

        clickButton(profileDialog, "Cancel");
        clickButton(preferences, "Cancel");
    }

    @Test
    public void captureCssCleanupPreferences() throws IOException {
        openPreferences();
        SWTBotShell preferences = bot.shell("Preferences").activate();
        selectPreferencePath(preferences.bot().tree(), "CSS Cleanup");
        preferences.maximize();
        bot.sleep(300);
        capture("sandbox_css_cleanup_help", "css-cleanup-preferences.png");
        clickButton(preferences, "Cancel");
    }

    @Test
    public void captureRuleInferencePreferences() throws IOException {
        openPreferences();
        SWTBotShell preferences = bot.shell("Preferences").activate();
        selectPreferencePath(preferences.bot().tree(), "Java", "LLM Rule Inference");
        preferences.maximize();
        bot.sleep(300);
        capture("sandbox_triggerpattern_help", "llm-rule-inference-preferences.png");
        clickButton(preferences, "Cancel");
    }

    private static void openPreferences() {
        bot.menu("Window").menu("Preferences").click();
        bot.shell("Preferences").activate();
    }

    private static void selectPreferencePath(SWTBotTree tree, String first, String... rest) {
        SWTBotTreeItem item = tree.getTreeItem(first);
        if (rest.length > 0) {
            item.expand();
        }
        for (int index = 0; index < rest.length; index++) {
            item = item.getNode(rest[index]);
            if (index + 1 < rest.length) {
                item.expand();
            }
        }
        item.select();
        bot.sleep(300);
    }

    private static void clickButton(SWTBotShell shell, String... labels) {
        WidgetNotFoundException failure = null;
        for (String label : labels) {
            try {
                shell.bot().button(label).click();
                return;
            } catch (WidgetNotFoundException exception) {
                failure = exception;
            }
        }
        throw failure;
    }

    private static void capture(String helpBundle, String fileName) throws IOException {
        Path image = outputRoot.resolve(helpBundle).resolve("images").resolve(fileName);
        Files.createDirectories(image.getParent());
        assertTrue(bot.captureScreenshot(image.toString()), () -> "Could not capture " + image);
        assertTrue(Files.isRegularFile(image), () -> "Screenshot was not written: " + image);
    }

    private static void closeWelcomeView() {
        try {
            bot.viewByTitle("Welcome").close();
        } catch (WidgetNotFoundException exception) {
            // The welcome view is not shown in every test workbench.
        }
    }

    private static void closeDialogIfOpen() {
        try {
            SWTBotShell active = bot.activeShell();
            if (!"Eclipse SDK".equals(active.getText())) {
                active.close();
            }
        } catch (WidgetNotFoundException exception) {
            // Workbench already closed.
        }
    }
}
