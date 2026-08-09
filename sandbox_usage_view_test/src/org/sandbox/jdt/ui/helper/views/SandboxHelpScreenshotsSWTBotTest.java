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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.results.Result;
import org.eclipse.swtbot.swt.finder.results.VoidResult;
import org.eclipse.swtbot.swt.finder.utils.SWTUtils;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Generates the screenshots embedded in the independently installable Eclipse
 * Help bundles.
 * <p>
 * The screenshots deliberately come from a real Eclipse workbench and are
 * written directly into the current checkout. On a graphical workstation run:
 * </p>
 *
 * <pre>
 * mvn -f sandbox_help_build/pom.xml \
 *     -Phelp-screenshots clean verify
 * </pre>
 *
 * <p>On a headless Linux machine, prepend a virtual display:</p>
 *
 * <pre>
 * xvfb-run --auto-servernum --server-args="-screen 0 1600x1200x24" \
 *     mvn -f sandbox_help_build/pom.xml \
 *     -Phelp-screenshots clean verify
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

    private static final int SCREENSHOT_CLIENT_WIDTH = 1280;
    private static final int SCREENSHOT_CLIENT_HEIGHT = 900;
    private static final int GTK_SETTLE_MILLIS = 1_500;
    private static final String OUTPUT_PROPERTY = "sandbox.help.screenshot.output";
    private static final String OTHER_PROJECT = "AnotherOpenProject";
    private static final String DOCUMENTATION_PROJECT = "SandboxHelpTriggerPattern";
    private static final String REFACTORING_MINING_VIEW = "org.sandbox.jdt.views.refactoringMining";
    private static final String PROJECT_EXPLORER_VIEW = "org.eclipse.ui.navigator.ProjectExplorer";
    private static final String HINT_FILE_CLEANUP_LABEL = "Apply transformation rules from .sandbox-hint files";
    private static SWTWorkbenchBot bot;
    private static Path outputRoot;
    private static IProject otherProject;
    private static IProject documentationProject;

    @BeforeAll
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        outputRoot = SandboxCheckout.locate(OUTPUT_PROPERTY);
        closeWelcomeView();
        otherProject = createEmptyProject(OTHER_PROJECT);
        documentationProject = createDocumentationProject();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        closeDialogIfOpen();
        deleteProject(documentationProject);
        deleteProject(otherProject);
    }

    @Test
    public void captureCleanupConfigurationTabs() throws IOException {
        openPreferences();
        SWTBotShell preferences = bot.shell("Preferences").activate();
        selectPreferencePath(preferences.bot().tree(), "Java", "Code Style", "Clean Up");
        clickButton(preferences, "Edit...", "Edit…");

        SWTBotShell profileDialog = bot.activeShell();
        prepareForScreenshot(profileDialog);

        for (CleanupTab tab : CLEANUP_TABS) {
            profileDialog.bot().tabItem(tab.label()).activate();
            if ("Code Patterns (Sandbox)".equals(tab.label())) {
                stabilizeHintFileCleanup(profileDialog);
            } else {
                bot.sleep(300);
            }
            capture(profileDialog, tab.helpBundle(), tab.fileName());
        }

        clickButton(profileDialog, "Cancel");
        clickButton(preferences, "Cancel");
    }

    @Test
    public void captureCssCleanupPreferences() throws IOException {
        openPreferences();
        SWTBotShell preferences = bot.shell("Preferences").activate();
        selectPreferencePath(preferences.bot().tree(), "CSS Cleanup");
        prepareForScreenshot(preferences);
        capture(preferences, "sandbox_css_cleanup_help", "css-cleanup-preferences.png");
        clickButton(preferences, "Cancel");
    }

    @Test
    public void captureRuleInferencePreferences() throws IOException {
        openPreferences();
        SWTBotShell preferences = bot.shell("Preferences").activate();
        selectPreferencePath(preferences.bot().tree(), "Java", "LLM Rule Inference");
        prepareForScreenshot(preferences);
        capture(preferences, "sandbox_triggerpattern_help", "llm-rule-inference-preferences.png");
        clickButton(preferences, "Cancel");
    }

    @Test
    public void captureRefactoringMiningWorkflow() throws Exception {
        SWTBotShell workbench = workbenchShell().activate();
        showView(workbench, REFACTORING_MINING_VIEW);
        SWTBotView miningView = bot.viewByTitle("Refactoring Mining");
        var miningToolbarButtons = miningView.getToolbarButtons();
        assertTrue(!miningToolbarButtons.isEmpty(), "Refactoring Mining toolbar must expose Analyze Project");
        // Analyze Project is deliberately the first action contributed by the view.
        miningToolbarButtons.get(0).click();

        SWTBotShell projectSelection = bot.shell("Select project for Refactoring Mining").activate();
        assertTrue(projectSelection.bot().table().rowCount() >= 2,
                "Refactoring Mining must make the target explicit in a multi-project workspace");
        projectSelection.bot().table().select(DOCUMENTATION_PROJECT);
        clickButton(projectSelection, "OK");

        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() {
                return miningView.bot().table().rowCount() >= 2;
            }

            @Override
            public String getFailureMessage() {
                return "The selected deterministic Git project did not appear in Refactoring Mining";
            }
        }, 10_000);
        bot.sleep(500);
        prepareForScreenshot(workbench);
        capture(workbench, "sandbox_triggerpattern_help", "refactoring-mining-view.png");
        miningView.close();
    }

    @Test
    public void captureNewHintRuleWizard() throws Exception {
        SWTBotShell workbench = workbenchShell().activate();
        showView(workbench, PROJECT_EXPLORER_VIEW);
        SWTBotView projectExplorer = bot.viewByTitle("Project Explorer");
        projectExplorer.bot().tree().getTreeItem(DOCUMENTATION_PROJECT).select();

        workbench.bot().menu("File").menu("New").menu("Other...").click();
        SWTBotShell newWizard = bot.activeShell();
        SWTBotTreeItem category = newWizard.bot().tree().getTreeItem("Sandbox TriggerPattern").expand();
        category.getNode("Sandbox Hint File").select();
        clickButton(newWizard, "Next >");

        SWTBotShell hintWizard = bot.activeShell();
        clickButton(hintWizard, "Next >");
        hintWizard = bot.activeShell();
        hintWizard.bot().textWithLabel("Source Pattern:").setText("$s.getBytes(\"UTF-8\")");
        hintWizard.bot().textWithLabel("Guard (optional):").setText("sourceVersionGE(7)");
        hintWizard.bot().textWithLabel("Replacement:")
                .setText("$s.getBytes(java.nio.charset.StandardCharsets.UTF_8)");
        bot.sleep(300);

        prepareForScreenshot(hintWizard);
        capture(hintWizard, "sandbox_triggerpattern_help", "new-hint-rule-wizard.png");
        clickButton(hintWizard, "Cancel");
        projectExplorer.close();
    }

    private static void stabilizeHintFileCleanup(SWTBotShell profileDialog) {
        var hintFileCleanup = profileDialog.bot().checkBox(HINT_FILE_CLEANUP_LABEL);
        if (!hintFileCleanup.isChecked()) {
            hintFileCleanup.click();
        }
        hintFileCleanup.setFocus();

        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() {
                return hintFileCleanup.isChecked()
                        && !profileDialog.bot().button("Apply").isEnabled()
                        && !profileDialog.bot().button("OK").isEnabled();
            }

            @Override
            public String getFailureMessage() {
                return "Code Patterns cleanup profile did not reach its stable built-in-profile state";
            }
        }, 5_000);

        UIThreadRunnable.syncExec(profileDialog.display, new VoidResult() {
            @Override
            public void run() {
                profileDialog.widget.layout(true, true);
                profileDialog.widget.update();
                // SWTBot's checkbox click leaves the pointer inside the scrollable
                // cleanup pane. GTK then paints its overlay scrollbars in a transient
                // hover/fade state, causing otherwise identical screenshots to differ
                // by a handful of color values. Park the pointer outside the dialog
                // and let that state expire before taking the reference image.
                profileDialog.widget.getDisplay().setCursorLocation(0, 0);
            }
        });
        bot.sleep(GTK_SETTLE_MILLIS);
        UIThreadRunnable.syncExec(profileDialog.display, new VoidResult() {
            @Override
            public void run() {
                profileDialog.widget.update();
            }
        });
    }

    private static IProject createDocumentationProject() throws Exception {
        IProject project = createEmptyProject(DOCUMENTATION_PROJECT);

        Path repository = project.getLocation().toFile().toPath();
        Path javaFile = repository.resolve("Example.java");
        try (Git git = Git.init().setDirectory(repository.toFile()).call()) {
            Files.writeString(javaFile,
                    "class Example { String decode(byte[] bytes) throws Exception { return new String(bytes, \"UTF-8\"); } }\n",
                    StandardCharsets.UTF_8);
            git.add().addFilepattern("Example.java").call();
            commit(git, "Use explicit charset name", "2026-01-01T10:00:00Z");

            Files.writeString(javaFile,
                    "import java.nio.charset.StandardCharsets;\n"
                    + "class Example { String decode(byte[] bytes) { return new String(bytes, StandardCharsets.UTF_8); } }\n",
                    StandardCharsets.UTF_8);
            git.add().addFilepattern("Example.java").call();
            commit(git, "Replace charset name with StandardCharsets", "2026-01-02T10:00:00Z");
        }
        return project;
    }

    private static IProject createEmptyProject(String projectName) throws Exception {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        NullProgressMonitor monitor = new NullProgressMonitor();
        if (project.exists()) {
            project.delete(true, true, monitor);
        }
        project.create(monitor);
        project.open(monitor);
        return project;
    }

    private static void deleteProject(IProject project) throws Exception {
        if (project != null && project.exists()) {
            project.delete(true, true, new NullProgressMonitor());
        }
    }

    private static void commit(Git git, String message, String timestamp) throws Exception {
        PersonIdent identity = new PersonIdent("Sandbox Help", "help@example.invalid",
                Date.from(Instant.parse(timestamp)), TimeZone.getTimeZone("UTC"));
        git.commit().setMessage(message).setAuthor(identity).setCommitter(identity).call();
    }

    private static SWTBotShell workbenchShell() {
        return UIThreadRunnable.syncExec(Display.getDefault(), new Result<SWTBotShell>() {
            @Override
            public SWTBotShell run() {
                return new SWTBotShell(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
            }
        });
    }

    private static void showView(SWTBotShell workbench, String viewId) {
        UIThreadRunnable.syncExec(workbench.display, new VoidResult() {
            @Override
            public void run() {
                try {
                    PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(viewId);
                } catch (PartInitException exception) {
                    throw new IllegalStateException("Could not open view " + viewId, exception);
                }
            }
        });
        bot.sleep(300);
    }

    private static void openPreferences() {
        SWTBotShell workbench = workbenchShell().activate();
        // Global bot.menu(...) first resolves the active shell. On GTK there is
        // a brief gap after a modal dialog closes where no active shell exists.
        // Bind the menu lookup to the known workbench shell instead.
        workbench.bot().menu("Window").menu("Preferences...").click();
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

    private static void prepareForScreenshot(SWTBotShell shell) {
        UIThreadRunnable.syncExec(shell.display, new VoidResult() {
            @Override
            public void run() {
                Rectangle trim = shell.widget.computeTrim(0, 0,
                        SCREENSHOT_CLIENT_WIDTH, SCREENSHOT_CLIENT_HEIGHT);
                shell.widget.setBounds(20, 20, trim.width, trim.height);
                shell.widget.layout(true, true);
            }
        });
        shell.activate();
        bot.sleep(500);
    }

    private static void capture(SWTBotShell shell, String helpBundle, String fileName)
            throws IOException {
        Path imageDirectory = outputRoot.resolve(helpBundle).resolve("images");
        Files.createDirectories(imageDirectory);
        Path image = imageDirectory.resolve(fileName);
        Rectangle clientBounds = UIThreadRunnable.syncExec(shell.display,
                new Result<Rectangle>() {
                    @Override
                    public Rectangle run() {
                        Rectangle clientArea = shell.widget.getClientArea();
                        return shell.widget.getDisplay().map(shell.widget, null, clientArea);
                    }
                });
        assertTrue(SWTUtils.captureScreenshot(image.toString(), clientBounds),
                () -> "Could not capture " + image);
        assertTrue(Files.isRegularFile(image) && Files.size(image) > 0,
                () -> "Screenshot was not written: " + image);
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
            SWTBotShell workbench = workbenchShell();
            if (active.widget != workbench.widget) {
                active.close();
            }
        } catch (WidgetNotFoundException exception) {
            // Workbench already closed.
        }
    }
}
