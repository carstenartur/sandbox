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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ltk.core.refactoring.IUndoManager;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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
/*
 * Cleanup execution scenarios persist profiles in the shared workbench.
 * Keep the documented local generator in the same deterministic order as
 * the read-only CI merge gate and run profile-mutating scenarios last.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SandboxHelpScreenshotsSWTBotTest {

    private record CleanupTab(String label, String helpBundle, String fileName) {
    }

    private record CleanUpPreview(SWTBotShell shell, SWTBotTree tree) {
    }

    private enum CleanUpWizardPageKind {
        CONDITION_STATUS,
        PREVIEW
    }

    private record CleanUpWizardPage(SWTBotShell shell, CleanUpWizardPageKind kind,
            SWTBotTree previewTree) {

        private static CleanUpWizardPage conditionStatus(SWTBotShell shell) {
            return new CleanUpWizardPage(shell, CleanUpWizardPageKind.CONDITION_STATUS, null);
        }

        private static CleanUpWizardPage preview(SWTBotShell shell, SWTBotTree tree) {
            return new CleanUpWizardPage(shell, CleanUpWizardPageKind.PREVIEW, tree);
        }
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
    private static final String CLEANUP_PREVIEW_PROJECT = "SandboxCleanupPreviewProject";
    private static final String JFACE_MASTER_LABEL = "Modernize JFace API usage";
    private static final String JFACE_MONITOR_LABEL = "Replace SubProgressMonitor with SubMonitor";
    private static final String JFACE_VIEWER_SORTER_LABEL = "Replace ViewerSorter with ViewerComparator";
    private static final String JFACE_IMAGE_DATA_PROVIDER_LABEL =
            "Modernize Image creation for DPI/zoom (ImageDataProvider)";
    private static final String METHOD_REUSE_MASTER_LABEL =
            "Extract repeated code sequences into a shared method";
    private static final String METHOD_REUSE_INLINE_LABEL =
            "Replace inline code sequences with calls to an existing method";
    private static final String INT_TO_ENUM_MASTER_LABEL = "Convert int constants to enum/switch";
    private static final String INT_TO_ENUM_PROJECT_WIDE_LABEL =
            "Analyze all project source files for coordinated migrations";
    private static final String INT_TO_ENUM_CANDIDATE_FRAGMENT = "nested enum Status";
    private static SWTWorkbenchBot bot;
    private static Path outputRoot;
    private static IProject otherProject;
    private static IProject documentationProject;
    private static IProject cleanupPreviewProject;

    @BeforeAll
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        outputRoot = SandboxCheckout.locate(OUTPUT_PROPERTY);
        deleteGeneratedScreenshot("sandbox_jface_cleanup_help",
                "jface-cleanup-real-preview-single-file-steps.png");
        deleteGeneratedScreenshot("sandbox_jface_cleanup_help",
                "jface-cleanup-real-preview-diff-step.png");
        deleteGeneratedScreenshot("sandbox_jface_cleanup_help",
                "jface-cleanup-real-preview-multi-file-selection.png");
        deleteGeneratedScreenshot("sandbox_int_to_enum_help",
                "int-to-enum-coordinated-preview.png");
        closeWelcomeView();
        otherProject = createEmptyProject(OTHER_PROJECT);
        documentationProject = createDocumentationProject();
        cleanupPreviewProject = createCleanupPreviewProject();
    }

    @AfterEach
    public void closeTransientDialogs() {
        closeAllDialogs();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        closeAllDialogs();
        deleteProject(cleanupPreviewProject);
        deleteProject(documentationProject);
        deleteProject(otherProject);
    }

    @Test
    @Order(5)
    public void captureCleanupConfigurationTabs() throws IOException {
        openPreferences();
        SWTBotShell preferences = bot.shell("Preferences").activate();
        selectPreferencePath(preferences.bot().tree(), "Java", "Code Style", "Clean Up");
        clickButton(preferences, "Edit...", "Edit…");

        SWTBotShell profileDialog = bot.activeShell();
        prepareForScreenshot(profileDialog);

        for (CleanupTab tab : CLEANUP_TABS) {
            System.out.println("[help-screenshots] Activating cleanup tab: " + tab.label());
            profileDialog.bot().tabItem(tab.label()).activate();
            if ("Code Patterns (Sandbox)".equals(tab.label())) {
                stabilizeHintFileCleanup(profileDialog);
            } else {
                bot.sleep(300);
            }
            capture(profileDialog, tab.helpBundle(), tab.fileName());
            System.out.println("[help-screenshots] Captured cleanup tab: " + tab.label());
        }

        clickButtonAndWaitForShellToClose(profileDialog, "Cleanup profile dialog", "Cancel");
        preferences.activate();
        clickButtonAndWaitForShellToClose(preferences, "Preferences", "Cancel");
    }

    @Test
    @Order(4)
    public void captureRealCleanupPreviewAndVerifyIndependentSelection() throws Exception {
        System.out.println("[help-screenshots] Starting real Cleanup file-selection preview");
        configureJFaceCleanupProfile();
        System.out.println("[help-screenshots] Configured JFace Cleanup profile");
        IFile singleFile = cleanupPreviewProject.getFile("src/demo/single/SingleFileCleanup.java");
        String singleBefore = readFile(singleFile);

        openProjectExplorer();
        SWTBotTreeItem singleFileNode = projectTree().getTreeItem(CLEANUP_PREVIEW_PROJECT).expand()
                .getNode("src").expand().getNode("demo.single").expand().getNode("SingleFileCleanup.java");
        singleFileNode.select();
        SWTBotShell wizard = openCleanUpWizard(singleFileNode);
        clickButton(wizard, "Next >", "Next >");
        bot.sleep(500);
        prepareForScreenshot(wizard);

        SWTBotTree previewTree = wizard.bot().tree();
        SWTBotTreeItem singlePreviewFile = findTreeItemContaining(previewTree, "SingleFileCleanup.java");
        assertTrue(singlePreviewFile != null, "Preview must contain the selected file");
        assertTrue(java.util.Arrays.stream(singlePreviewFile.getItems())
                .allMatch(child -> child.getText().isBlank() && !child.isChecked()),
                "The standard LTK Cleanup preview may contain an empty decoration placeholder, "
                        + "but must not expose named or selectable per-cleanup children");
        assertTrue(singlePreviewFile.isChecked(), "The selected file must initially be enabled");
        singlePreviewFile.select();
        bot.sleep(300);

        String combinedDiff = currentDiffText(wizard);
        assertTrue(combinedDiff.contains("SubProgressMonitor"),
                "The combined file diff must show the legacy monitor API");
        assertTrue(combinedDiff.contains("SubMonitor"),
                "The combined file diff must show the SubMonitor replacement");
        assertTrue(combinedDiff.contains("ViewerSorter"),
                "The combined file diff must show the legacy sorter API");
        assertTrue(combinedDiff.contains("ViewerComparator"),
                "The combined file diff must show the ViewerComparator replacement");
        assertTrue(!combinedDiff.contains("JFACE_CLEANUP_MONITOR"),
                "The preview must not expose internal cleanup option ids");

        captureCleanUpPreview(wizard, "sandbox_jface_cleanup_help",
                "jface-cleanup-real-preview-single-file-steps.png");

        clickButtonAsync(wizard, "Finish");
        waitForShellToClose(wizard, "Clean Up wizard");
        String singleAfter = readFile(singleFile);
        assertTrue(singleAfter.contains("SubMonitor.convert"),
                "The monitor migration in the selected file must be applied");
        assertTrue(singleAfter.contains("ViewerComparator"),
                "The sorter migration in the selected file must be applied");
        assertTrue(!singleAfter.contains("SubProgressMonitor"),
                "The selected file must no longer use SubProgressMonitor");
        assertTrue(!singleAfter.contains("ViewerSorter"),
                "The selected file must no longer use ViewerSorter");
        assertTrue(!singleBefore.equals(singleAfter), "Applying the selected file must change it");

        IFile monitorFile = cleanupPreviewProject.getFile("src/demo/multi/MonitorOnly.java");
        IFile sorterFile = cleanupPreviewProject.getFile("src/demo/multi/SorterOnly.java");
        String monitorBefore = readFile(monitorFile);
        String sorterBefore = readFile(sorterFile);

        SWTBotTreeItem multiPackage = projectTree().getTreeItem(CLEANUP_PREVIEW_PROJECT).expand()
                .getNode("src").expand().getNode("demo.multi");
        multiPackage.select();
        wizard = openCleanUpWizard(multiPackage);
        clickButton(wizard, "Next >", "Next >");
        bot.sleep(500);
        prepareForScreenshot(wizard);

        previewTree = wizard.bot().tree();
        SWTBotTreeItem monitorPreviewFile = findTreeItemContaining(previewTree, "MonitorOnly.java");
        SWTBotTreeItem sorterPreviewFile = findTreeItemContaining(previewTree, "SorterOnly.java");
        assertTrue(monitorPreviewFile != null, "Preview must contain MonitorOnly.java");
        assertTrue(sorterPreviewFile != null, "Preview must contain SorterOnly.java");
        assertTrue(monitorPreviewFile.isChecked(), "MonitorOnly.java must initially be selected");
        assertTrue(sorterPreviewFile.isChecked(), "SorterOnly.java must initially be selected");

        monitorPreviewFile.select();
        bot.sleep(300);
        String monitorDiff = currentDiffText(wizard);
        assertTrue(monitorDiff.contains("SubProgressMonitor"),
                "MonitorOnly.java diff must show the legacy monitor API");
        assertTrue(monitorDiff.contains("SubMonitor"),
                "MonitorOnly.java diff must show the SubMonitor replacement");
        captureCleanUpPreview(wizard, "sandbox_jface_cleanup_help",
                "jface-cleanup-real-preview-diff-step.png");

        sorterPreviewFile.select();
        bot.sleep(300);
        String sorterDiff = currentDiffText(wizard);
        assertTrue(sorterDiff.contains("ViewerSorter"),
                "SorterOnly.java diff must show the legacy sorter API");
        assertTrue(sorterDiff.contains("ViewerComparator"),
                "SorterOnly.java diff must show the ViewerComparator replacement");
        assertTrue(!monitorDiff.equals(sorterDiff),
                "Selecting another file must update the real LTK diff viewer");

        sorterPreviewFile.uncheck();
        assertTrue(!sorterPreviewFile.isChecked(), "SorterOnly.java must be independently deselectable");
        assertTrue(monitorPreviewFile.isChecked(), "Deselecting SorterOnly.java must not disable MonitorOnly.java");
        monitorPreviewFile.select();
        bot.sleep(300);
        captureCleanUpPreview(wizard, "sandbox_jface_cleanup_help",
                "jface-cleanup-real-preview-multi-file-selection.png");

        clickButtonAsync(wizard, "Finish");
        waitForShellToClose(wizard, "Clean Up wizard");

        String monitorAfter = readFile(monitorFile);
        String sorterAfter = readFile(sorterFile);
        assertTrue(monitorAfter.contains("SubMonitor.convert"),
                "The selected monitor file must receive its cleanup");
        assertTrue(!monitorBefore.equals(monitorAfter), "The selected file must be modified");
        assertTrue(sorterBefore.equals(sorterAfter), "The deselected file must remain byte-identical");

        undoLastCleanup();
        assertTrue(monitorBefore.equals(readFile(monitorFile)), "Undo must restore MonitorOnly.java");
        assertTrue(sorterBefore.equals(readFile(sorterFile)), "Undo must keep SorterOnly.java unchanged");
    }

    @Test
    @Order(8)
    public void verifyRealMethodReuseCleanupPreviewApplyAndUndo() throws Exception {
        System.out.println("[help-screenshots] Starting real Method Reuse Cleanup preview");
        configureMethodReuseCleanupProfile();
        IFile file = cleanupPreviewProject.getFile(
                "src/demo/methodreuse/RepeatedSequence.java");
        String before = readFile(file);

        openProjectExplorer();
        SWTBotTreeItem fileNode = projectTree().getTreeItem(CLEANUP_PREVIEW_PROJECT).expand()
                .getNode("src").expand().getNode("demo.methodreuse").expand()
                .getNode("RepeatedSequence.java");
        fileNode.select();
        SWTBotShell originatingWizard = openCleanUpWizard(fileNode);
        CleanUpPreview preview = openCleanUpPreview(originatingWizard,
                "RepeatedSequence.java");
        SWTBotShell wizard = preview.shell();
        prepareForScreenshot(wizard);

        SWTBotTreeItem previewFile = findTreeItemContaining(preview.tree(),
                "RepeatedSequence.java");
        assertTrue(previewFile != null && previewFile.isChecked(),
                "The real LTK preview must contain the selected Method Reuse file");
        previewFile.select();
        bot.sleep(300);
        String diff = currentDiffText(wizard);
        assertTrue(diff.contains("private void extractedSequence(String value)"),
                "The LTK diff must show the extracted private method");
        assertTrue(diff.contains("extractedSequence(value);"),
                "The LTK diff must show replacement of the selected occurrence");
        assertTrue(diff.contains("extractedSequence(input);"),
                "The LTK diff must show replacement of the duplicate occurrence");

        clickButtonAsync(wizard, "Finish");
        waitForShellToClose(wizard, "Clean Up wizard");
        String after = readFile(file);
        assertTrue(after.contains("private void extractedSequence(String value)"),
                "Apply must create the private shared method");
        assertTrue(after.contains("extractedSequence(value);")
                && after.contains("extractedSequence(input);"),
                "Apply must replace both JDT-validated occurrences");
        assertTrue(!before.equals(after), "Apply must change the Method Reuse fixture");

        undoLastCleanup();
        assertTrue(before.equals(readFile(file)),
                "Undo must restore the Method Reuse fixture byte-for-byte");
    }

    @Test
    @Order(7)
    public void coordinatedIntToEnumPreviewIsAtomic() throws Exception {
        System.out.println("[help-screenshots] Starting coordinated Int-to-Enum Cleanup preview");
        configureIntToEnumCleanupProfile();
        System.out.println("[help-screenshots] Configured Int-to-Enum Cleanup profile");
        IFile ownerFile = cleanupPreviewProject.getFile("src/demo/coordinated/StateOwner.java");
        IFile callerFile = cleanupPreviewProject.getFile("src/demo/coordinated/StateCaller.java");
        String ownerBefore = readFile(ownerFile);
        String callerBefore = readFile(callerFile);

        openProjectExplorer();
        SWTBotTreeItem ownerNode = projectTree().getTreeItem(CLEANUP_PREVIEW_PROJECT).expand()
                .getNode("src").expand().getNode("demo.coordinated").expand().getNode("StateOwner.java");
        ownerNode.select();
        SWTBotShell wizard = openCleanUpWizard(ownerNode);
        CleanUpPreview preview = openCleanUpPreview(wizard, INT_TO_ENUM_CANDIDATE_FRAGMENT);
        wizard = preview.shell();
        prepareForScreenshot(wizard);

        SWTBotTree previewTree = preview.tree();
        SWTBotTreeItem candidate = findTreeItemContaining(previewTree, INT_TO_ENUM_CANDIDATE_FRAGMENT);
        assertTrue(candidate != null, "Preview must expose the coordinated Int-to-Enum candidate");
        candidate.select();
        waitForCoordinatedPreviewDetails(wizard);

        assertTrue(candidate.getItems().length == 0,
                "The atomic candidate must be a leaf without per-file or per-edit checkboxes");
        assertTrue(currentPlainText(wizard).contains("Selection is atomic"),
                "The coordinated viewer must explain the atomic selection contract");

        var affectedFiles = wizard.bot().table();
        assertTrue(affectedFiles.rowCount() == 2,
                "The coordinated viewer must list exactly the owner and required caller");
        String affectedLabels = affectedFiles.cell(0, 0) + "\n" + affectedFiles.cell(1, 0);
        assertTrue(affectedLabels.contains("StateOwner.java"),
                "The coordinated viewer must list StateOwner.java");
        assertTrue(affectedLabels.contains("StateCaller.java"),
                "The coordinated viewer must list StateCaller.java");

        captureCleanUpPreview(wizard, "sandbox_int_to_enum_help",
                "int-to-enum-coordinated-preview.png");

        assertTrue(candidate.isChecked(), "The coordinated candidate must initially be selected");
        candidate.uncheck();
        assertTrue(!candidate.isChecked(), "The single candidate checkbox must disable the whole migration");
        if (wizard.bot().button("Finish").isEnabled()) {
            clickButtonAsync(wizard, "Finish");
        } else {
            clickButton(wizard, "Cancel");
        }
        waitForShellToClose(wizard, "Clean Up wizard");
        assertTrue(ownerBefore.equals(readFile(ownerFile)),
                "Disabling the candidate must keep StateOwner.java byte-identical");
        assertTrue(callerBefore.equals(readFile(callerFile)),
                "Disabling the candidate must keep StateCaller.java byte-identical");

        ownerNode = projectTree().getTreeItem(CLEANUP_PREVIEW_PROJECT).expand()
                .getNode("src").expand().getNode("demo.coordinated").expand().getNode("StateOwner.java");
        ownerNode.select();
        wizard = openCleanUpWizard(ownerNode);
        preview = openCleanUpPreview(wizard, INT_TO_ENUM_CANDIDATE_FRAGMENT);
        wizard = preview.shell();
        previewTree = preview.tree();
        candidate = findTreeItemContaining(previewTree, INT_TO_ENUM_CANDIDATE_FRAGMENT);
        assertTrue(candidate != null && candidate.isChecked(),
                "Reopening the preview must present one selected atomic candidate");
        clickButtonAsync(wizard, "Finish");
        waitForShellToClose(wizard, "Clean Up wizard");

        String ownerAfter = readFile(ownerFile);
        String callerAfter = readFile(callerFile);
        assertTrue(ownerAfter.contains("enum Status"),
                "Applying the candidate must introduce the enum in StateOwner.java");
        assertTrue(callerAfter.contains("Status.PENDING"),
                "Applying the candidate must update the required caller in the same operation");
        assertTrue(!ownerBefore.equals(ownerAfter) && !callerBefore.equals(callerAfter),
                "The selected atomic candidate must modify both required files");

        undoLastCleanup();
        assertTrue(ownerBefore.equals(readFile(ownerFile)),
                "Undo must restore StateOwner.java byte-exactly");
        assertTrue(callerBefore.equals(readFile(callerFile)),
                "Undo must restore StateCaller.java byte-exactly");
    }

    @Test
    @Order(6)
    public void captureCssCleanupPreferences() throws IOException {
        openPreferences();
        SWTBotShell preferences = bot.shell("Preferences").activate();
        selectPreferencePath(preferences.bot().tree(), "CSS Cleanup");
        prepareForScreenshot(preferences);
        capture(preferences, "sandbox_css_cleanup_help", "css-cleanup-preferences.png");
        clickButton(preferences, "Cancel");
    }

    @Test
    @Order(1)
    public void captureRuleInferencePreferences() throws IOException {
        openPreferences();
        SWTBotShell preferences = bot.shell("Preferences").activate();
        selectPreferencePath(preferences.bot().tree(), "Java", "LLM Rule Inference");
        prepareForScreenshot(preferences);
        capture(preferences, "sandbox_triggerpattern_help", "llm-rule-inference-preferences.png");
        clickButton(preferences, "Cancel");
    }

    @Test
    @Order(2)
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
    @Order(3)
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
        UIThreadRunnable.syncExec(profileDialog.display, new VoidResult() {
            @Override
            public void run() {
                profileDialog.widget.layout(true, true);
                profileDialog.widget.update();
                // Leave the read-only built-in profile untouched. Clicking or focusing
                // its checkbox can start a nested GTK event loop while the cleanup
                // preview is being refreshed. Park the pointer outside the dialog and
                // let overlay-scrollbar hover state expire before taking the image.
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

    private static void configureIntToEnumCleanupProfile() {
        openPreferences();
        SWTBotShell preferences = bot.shell("Preferences").activate();
        selectPreferencePath(preferences.bot().tree(), "Java", "Code Style", "Clean Up");
        clickButton(preferences, "Edit...", "Edit…");
        SWTBotShell profileDialog = bot.activeShell();
        profileDialog.bot().textWithLabel("Profile name:").setText("Sandbox Coordinated Preview");
        profileDialog.bot().tabItem("Int to Enum (Sandbox)").activate();

        ensureChecked(profileDialog, INT_TO_ENUM_MASTER_LABEL, true);
        ensureChecked(profileDialog, INT_TO_ENUM_PROJECT_WIDE_LABEL, true);

        clickButtonAndWaitForShellToClose(profileDialog, "Cleanup profile dialog", "OK");
        preferences.activate();
        clickButtonAndWaitForShellToClose(preferences, "Preferences", "Apply and Close", "OK");
    }

    private static void configureMethodReuseCleanupProfile() {
        openPreferences();
        SWTBotShell preferences = bot.shell("Preferences").activate();
        selectPreferencePath(preferences.bot().tree(), "Java", "Code Style", "Clean Up");
        clickButton(preferences, "Edit...", "Edit…");
        SWTBotShell profileDialog = bot.activeShell();
        profileDialog.bot().textWithLabel("Profile name:")
                .setText("Sandbox Method Reuse Preview");
        profileDialog.bot().tabItem("Method Reuse (Sandbox)").activate();

        ensureChecked(profileDialog, METHOD_REUSE_MASTER_LABEL, true);
        ensureChecked(profileDialog, METHOD_REUSE_INLINE_LABEL, false);

        clickButtonAndWaitForShellToClose(profileDialog, "Cleanup profile dialog", "OK");
        preferences.activate();
        clickButtonAndWaitForShellToClose(preferences, "Preferences", "Apply and Close", "OK");
    }

    private static void configureJFaceCleanupProfile() {
        openPreferences();
        SWTBotShell preferences = bot.shell("Preferences").activate();
        selectPreferencePath(preferences.bot().tree(), "Java", "Code Style", "Clean Up");
        clickButton(preferences, "Edit...", "Edit…");
        SWTBotShell profileDialog = bot.activeShell();
        profileDialog.bot().textWithLabel("Profile name:").setText("Sandbox Help Preview");
        profileDialog.bot().tabItem("JFace Cleanup (Sandbox)").activate();

        ensureChecked(profileDialog, JFACE_MASTER_LABEL, true);
        ensureChecked(profileDialog, JFACE_MONITOR_LABEL, true);
        ensureChecked(profileDialog, JFACE_VIEWER_SORTER_LABEL, true);
        ensureChecked(profileDialog, JFACE_IMAGE_DATA_PROVIDER_LABEL, false);

        clickButtonAndWaitForShellToClose(profileDialog, "Cleanup profile dialog", "OK");
        preferences.activate();
        clickButtonAndWaitForShellToClose(preferences, "Preferences", "Apply and Close", "OK");
    }

    private static void ensureChecked(SWTBotShell shell, String label, boolean checked) {
        var checkBox = shell.bot().checkBox(label);
        if (checkBox.isChecked() != checked) {
            checkBox.click();
        }
    }

    private static void openProjectExplorer() {
        SWTBotShell workbench = workbenchShell().activate();
        showView(workbench, PROJECT_EXPLORER_VIEW);
        bot.viewByTitle("Project Explorer").setFocus();
    }

    private static SWTBotTree projectTree() {
        return bot.viewByTitle("Project Explorer").bot().tree();
    }

    private static SWTBotShell openCleanUpWizard(SWTBotTreeItem selection) {
        WidgetNotFoundException failure = null;
        for (String label : List.of("Clean Up...", "Clean Up…")) {
            try {
                selection.contextMenu("Source").menu(label).click();
                return waitForCleanUpWizard();
            } catch (WidgetNotFoundException exception) {
                failure = exception;
            }
        }
        throw failure;
    }

    private static SWTBotShell waitForCleanUpWizard() {
        SWTBotShell[] result = new SWTBotShell[1];
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() {
                for (SWTBotShell shell : bot.shells()) {
                    if (!shell.getText().startsWith("Clean Up") || !shell.isOpen()) {
                        continue;
                    }
                    try {
                        shell.bot().button("Next >");
                        shell.bot().button("Cancel");
                        result[0] = shell;
                        return true;
                    } catch (WidgetNotFoundException exception) {
                        // A cleanup profile or preferences shell can share the title.
                    }
                }
                return false;
            }

            @Override
            public String getFailureMessage() {
                return "The real Clean Up wizard did not open; visible shells: " + visibleShells();
            }
        });
        return result[0].activate();
    }

    private static CleanUpPreview openCleanUpPreview(SWTBotShell originatingWizard,
            String expectedItemFragment) {
        clickButton(originatingWizard, "Next >", "&Next >");
        CleanUpWizardPage page = waitForCleanUpPreviewOrStatusPage(originatingWizard,
                expectedItemFragment);
        if (page.kind() == CleanUpWizardPageKind.CONDITION_STATUS) {
            assertTrue(hasVisibleEnabledButton(page.shell(), "Next >"),
                    () -> "The Clean Up condition status blocks the preview. "
                            + "Visible shell controls:\n" + visibleShellDiagnostics());
            clickButton(page.shell(), "Next >", "&Next >");
            page = waitForCleanUpPreviewPage(page.shell(), expectedItemFragment);
        }
        return new CleanUpPreview(page.shell().activate(), page.previewTree());
    }

    private static CleanUpWizardPage waitForCleanUpPreviewOrStatusPage(
            SWTBotShell originatingWizard, String expectedItemFragment) {
        CleanUpWizardPage[] result = new CleanUpWizardPage[1];
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() {
                for (SWTBotShell shell : candidateCleanUpShells(originatingWizard)) {
                    SWTBotTree previewTree = visibleTreeContaining(shell, expectedItemFragment);
                    if (previewTree != null) {
                        result[0] = CleanUpWizardPage.preview(shell, previewTree);
                        return true;
                    }
                    if (hasVisibleControl(shell, "RefactoringStatusViewer")) {
                        result[0] = CleanUpWizardPage.conditionStatus(shell);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String getFailureMessage() {
                return "The Clean Up wizard exposed neither its condition status nor '"
                        + expectedItemFragment + "'. Visible shell controls:\n"
                        + visibleShellDiagnostics();
            }
        });
        return result[0];
    }

    private static CleanUpWizardPage waitForCleanUpPreviewPage(SWTBotShell originatingWizard,
            String expectedItemFragment) {
        CleanUpWizardPage[] result = new CleanUpWizardPage[1];
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() {
                for (SWTBotShell shell : candidateCleanUpShells(originatingWizard)) {
                    SWTBotTree previewTree = visibleTreeContaining(shell, expectedItemFragment);
                    if (previewTree != null) {
                        result[0] = CleanUpWizardPage.preview(shell, previewTree);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String getFailureMessage() {
                return "The Clean Up preview did not expose the expected item '"
                        + expectedItemFragment + "'. Visible shell controls:\n"
                        + visibleShellDiagnostics();
            }
        });
        return result[0];
    }

    private static List<SWTBotShell> candidateCleanUpShells(SWTBotShell originatingWizard) {
        return java.util.Arrays.stream(bot.shells())
                .filter(SWTBotShell::isOpen)
                .filter(shell -> shell.widget == originatingWizard.widget
                        || shell.getText().startsWith("Clean Up"))
                .toList();
    }

    private static SWTBotTree visibleTreeContaining(SWTBotShell shell, String expectedItemFragment) {
        for (Tree tree : visibleTreeControls(shell)) {
            try {
                SWTBotTree candidateTree = new SWTBotTree(tree);
                if (findTreeItemContaining(candidateTree, expectedItemFragment) != null) {
                    return candidateTree;
                }
            } catch (WidgetNotFoundException exception) {
                // The page replaced the tree while SWTBot was inspecting it; retry later.
            }
        }
        return null;
    }

    private static List<Tree> visibleTreeControls(SWTBotShell shell) {
        return UIThreadRunnable.syncExec(shell.display, new Result<List<Tree>>() {
            @Override
            public List<Tree> run() {
                List<Tree> trees = new ArrayList<>();
                if (shell.widget.isDisposed()) {
                    return trees;
                }
                Deque<Control> pending = new ArrayDeque<>();
                pending.add(shell.widget);
                while (!pending.isEmpty()) {
                    Control control = pending.removeFirst();
                    if (control instanceof Tree tree && tree.isVisible()) {
                        trees.add(tree);
                    }
                    if (control instanceof Composite composite) {
                        for (Control child : composite.getChildren()) {
                            pending.addLast(child);
                        }
                    }
                }
                return trees;
            }
        });
    }

    private static boolean hasVisibleControl(SWTBotShell shell, String simpleClassName) {
        return UIThreadRunnable.syncExec(shell.display, new Result<Boolean>() {
            @Override
            public Boolean run() {
                if (shell.widget.isDisposed()) {
                    return Boolean.FALSE;
                }
                Deque<Control> pending = new ArrayDeque<>();
                pending.add(shell.widget);
                while (!pending.isEmpty()) {
                    Control control = pending.removeFirst();
                    if (control.isVisible()
                            && control.getClass().getSimpleName().equals(simpleClassName)) {
                        return Boolean.TRUE;
                    }
                    if (control instanceof Composite composite) {
                        for (Control child : composite.getChildren()) {
                            pending.addLast(child);
                        }
                    }
                }
                return Boolean.FALSE;
            }
        });
    }

    private static boolean hasVisibleEnabledButton(SWTBotShell shell, String text) {
        return UIThreadRunnable.syncExec(shell.display, new Result<Boolean>() {
            @Override
            public Boolean run() {
                if (shell.widget.isDisposed()) {
                    return Boolean.FALSE;
                }
                Deque<Control> pending = new ArrayDeque<>();
                pending.add(shell.widget);
                while (!pending.isEmpty()) {
                    Control control = pending.removeFirst();
                    if (control instanceof Button button && button.isVisible()
                            && button.isEnabled()
                            && text.equals(button.getText().replace("&", ""))) {
                        return Boolean.TRUE;
                    }
                    if (control instanceof Composite composite) {
                        for (Control child : composite.getChildren()) {
                            pending.addLast(child);
                        }
                    }
                }
                return Boolean.FALSE;
            }
        });
    }

    private static void waitForCoordinatedPreviewDetails(SWTBotShell wizard) {
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() {
                if (!wizard.isOpen()) {
                    return false;
                }
                try {
                    var affectedFiles = wizard.bot().table();
                    if (!currentPlainText(wizard).contains("Selection is atomic")
                            || affectedFiles.rowCount() != 2) {
                        return false;
                    }
                    String affectedLabels = affectedFiles.cell(0, 0) + "\n"
                            + affectedFiles.cell(1, 0);
                    return affectedLabels.contains("StateOwner.java")
                            && affectedLabels.contains("StateCaller.java");
                } catch (WidgetNotFoundException exception) {
                    return false;
                }
            }

            @Override
            public String getFailureMessage() {
                return "The coordinated Cleanup preview did not expose its atomic details. "
                        + "Visible shell controls:\n" + visibleShellDiagnostics();
            }
        });
    }

    private static String visibleShellDiagnostics() {
        List<String> shells = new ArrayList<>();
        for (SWTBotShell shell : bot.shells()) {
            if (!shell.isOpen()) {
                continue;
            }
            shells.add(UIThreadRunnable.syncExec(shell.display, new Result<String>() {
                @Override
                public String run() {
                    if (shell.widget.isDisposed()) {
                        return "<disposed shell>";
                    }
                    StringBuilder description = new StringBuilder();
                    description.append("Shell '").append(shell.widget.getText()).append("':");
                    Deque<Control> pending = new ArrayDeque<>();
                    pending.add(shell.widget);
                    int count = 0;
                    while (!pending.isEmpty() && count < 80) {
                        Control control = pending.removeFirst();
                        description.append("\n  ").append(control.getClass().getSimpleName())
                                .append(" visible=").append(control.isVisible())
                                .append(" enabled=").append(control.isEnabled());
                        if (control instanceof Button button) {
                            description.append(" text='").append(button.getText()).append("'");
                        } else if (control instanceof Label label) {
                            description.append(" text='").append(label.getText()).append("'");
                        } else if (control instanceof Text text) {
                            description.append(" text='").append(text.getText()).append("'");
                        } else if (control instanceof StyledText text) {
                            description.append(" text='").append(text.getText()).append("'");
                        }
                        count++;
                        if (control instanceof Composite composite) {
                            for (Control child : composite.getChildren()) {
                                pending.addLast(child);
                            }
                        }
                    }
                    if (!pending.isEmpty()) {
                        description.append("\n  ... additional controls omitted");
                    }
                    return description.toString();
                }
            }));
        }
        return String.join("\n", shells);
    }

    private static SWTBotTreeItem findTreeItemContaining(SWTBotTree tree, String needle) {
        for (SWTBotTreeItem root : tree.getAllItems()) {
            SWTBotTreeItem match = findDescendant(root, needle);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static SWTBotTreeItem findDescendant(SWTBotTreeItem item, String needle) {
        if (item.getText().contains(needle)) {
            return item;
        }
        for (SWTBotTreeItem child : item.getItems()) {
            SWTBotTreeItem match = findDescendant(child, needle);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static String currentPlainText(SWTBotShell shell) {
        return UIThreadRunnable.syncExec(shell.display, new Result<String>() {
            @Override
            public String run() {
                return String.join("\n---\n", collectText(shell.widget));
            }
        });
    }

    private static String currentDiffText(SWTBotShell shell) {
        return UIThreadRunnable.syncExec(shell.display, new Result<String>() {
            @Override
            public String run() {
                return String.join("\n---\n", collectStyledText(shell.widget));
            }
        });
    }

    private static List<String> collectStyledText(Control root) {
        Deque<Control> pending = new ArrayDeque<>();
        pending.add(root);
        List<String> texts = new ArrayList<>();
        while (!pending.isEmpty()) {
            Control control = pending.removeFirst();
            if (control instanceof StyledText styledText) {
                texts.add(styledText.getText());
            }
            if (control instanceof Composite composite) {
                for (Control child : composite.getChildren()) {
                    pending.addLast(child);
                }
            }
        }
        return texts;
    }

    private static List<String> collectText(Control root) {
        Deque<Control> pending = new ArrayDeque<>();
        pending.add(root);
        List<String> texts = new ArrayList<>();
        while (!pending.isEmpty()) {
            Control control = pending.removeFirst();
            if (control instanceof Text text) {
                texts.add(text.getText());
            }
            if (control instanceof Composite composite) {
                for (Control child : composite.getChildren()) {
                    pending.addLast(child);
                }
            }
        }
        return texts;
    }

    private static void waitForShellToClose(SWTBotShell shell, String description) {
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() {
                return shell.widget.isDisposed();
            }

            @Override
            public String getFailureMessage() {
                return description + " did not close";
            }
        });
    }

    private static void undoLastCleanup() throws Exception {
        IUndoManager undoManager = RefactoringCore.getUndoManager();
        assertTrue(undoManager.anythingToUndo(), "Cleanup operation should be undoable");
        undoManager.performUndo(null, new NullProgressMonitor());
    }

    private static String readFile(IFile file) throws IOException {
        return Files.readString(file.getLocation().toFile().toPath(), StandardCharsets.UTF_8);
    }

    private static IProject createCleanupPreviewProject() throws Exception {
        IProject project = createEmptyProject(CLEANUP_PREVIEW_PROJECT);
        IJavaProject javaProject = JavaCore.create(project);
        var description = project.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        project.setDescription(description, new NullProgressMonitor());

        IFolder sourceFolder = project.getFolder("src");
        if (!sourceFolder.exists()) {
            sourceFolder.create(true, true, new NullProgressMonitor());
        }
        IFolder outputFolder = project.getFolder("bin");
        if (!outputFolder.exists()) {
            outputFolder.create(true, true, new NullProgressMonitor());
        }

        IClasspathEntry[] classpath = new IClasspathEntry[] {
                JavaCore.newSourceEntry(sourceFolder.getFullPath()),
                JavaCore.newContainerEntry(new org.eclipse.core.runtime.Path("org.eclipse.jdt.launching.JRE_CONTAINER")) };
        javaProject.setRawClasspath(classpath, outputFolder.getFullPath(), new NullProgressMonitor());
        writeCleanupFixtureSources(javaProject);
        project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
        return project;
    }

    private static void writeCleanupFixtureSources(IJavaProject javaProject) throws Exception {
        IPackageFragmentRoot sourceRoot = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));

        createUnit(sourceRoot, "org.eclipse.core.runtime", "IProgressMonitor.java",
                """
                        package org.eclipse.core.runtime;
                        public interface IProgressMonitor {
                            void beginTask(String name, int totalWork);
                        }
                        """);
        createUnit(sourceRoot, "org.eclipse.core.runtime", "SubMonitor.java",
                """
                        package org.eclipse.core.runtime;
                        public class SubMonitor implements IProgressMonitor {
                            public static SubMonitor convert(IProgressMonitor monitor, int work) { return new SubMonitor(); }
                            public SubMonitor split(int ticks) { return this; }
                            @Override
                            public void beginTask(String name, int totalWork) {
                            }
                        }
                        """);
        createUnit(sourceRoot, "org.eclipse.core.runtime", "SubProgressMonitor.java",
                """
                        package org.eclipse.core.runtime;
                        public class SubProgressMonitor implements IProgressMonitor {
                            public SubProgressMonitor(IProgressMonitor monitor, int work) {
                            }
                            @Override
                            public void beginTask(String name, int totalWork) {
                            }
                        }
                        """);
        createUnit(sourceRoot, "org.eclipse.jface.viewers", "ViewerComparator.java",
                """
                        package org.eclipse.jface.viewers;
                        public class ViewerComparator {
                        }
                        """);
        createUnit(sourceRoot, "org.eclipse.jface.viewers", "ViewerSorter.java",
                """
                        package org.eclipse.jface.viewers;
                        public class ViewerSorter extends ViewerComparator {
                        }
                        """);
        createUnit(sourceRoot, "demo.single", "SingleFileCleanup.java",
                """
                        package demo.single;
                        import org.eclipse.core.runtime.IProgressMonitor;
                        import org.eclipse.core.runtime.SubProgressMonitor;
                        import org.eclipse.jface.viewers.ViewerSorter;
                        public class SingleFileCleanup {
                            public void monitor(IProgressMonitor monitor) {
                                monitor.beginTask("work", 100);
                                IProgressMonitor child = new SubProgressMonitor(monitor, 40);
                            }
                            public ViewerSorter sorter() {
                                return new ViewerSorter();
                            }
                        }
                        """);
        createUnit(sourceRoot, "demo.multi", "MonitorOnly.java",
                """
                        package demo.multi;
                        import org.eclipse.core.runtime.IProgressMonitor;
                        import org.eclipse.core.runtime.SubProgressMonitor;
                        public class MonitorOnly {
                            public void monitor(IProgressMonitor monitor) {
                                monitor.beginTask("work", 10);
                                IProgressMonitor child = new SubProgressMonitor(monitor, 5);
                            }
                        }
                        """);
        createUnit(sourceRoot, "demo.multi", "SorterOnly.java",
                """
                        package demo.multi;
                        import org.eclipse.jface.viewers.ViewerSorter;
                        public class SorterOnly {
                            public ViewerSorter sorter() {
                                return new ViewerSorter();
                            }
                        }
                        """);
        createUnit(sourceRoot, "demo.methodreuse", "RepeatedSequence.java",
                """
                        package demo.methodreuse;

                        public class RepeatedSequence {
                            public void first(String value) {
                                String text = value.trim();
                                text = text.toLowerCase();
                                System.out.println(text);
                            }

                            public void second(String input) {
                                String text = input.trim();
                                text = text.toLowerCase();
                                System.out.println(text);
                            }
                        }
                        """);
        createUnit(sourceRoot, "demo.coordinated", "StateOwner.java",
                """
                        package demo.coordinated;

                        public class StateOwner {
                            static final int STATUS_PENDING = 0;
                            static final int STATUS_APPROVED = 1;

                            void process(int status) {
                                if (status == STATUS_PENDING) {
                                    System.out.println("pending");
                                } else if (status == STATUS_APPROVED) {
                                    System.out.println("approved");
                                }
                            }
                        }
                        """);
        createUnit(sourceRoot, "demo.coordinated", "StateCaller.java",
                """
                        package demo.coordinated;

                        public class StateCaller {
                            void run(StateOwner owner) {
                                owner.process(StateOwner.STATUS_PENDING);
                            }
                        }
                        """);
    }

    private static void createUnit(IPackageFragmentRoot sourceRoot, String packageName, String unitName, String contents)
            throws Exception {
        IPackageFragment fragment = sourceRoot.createPackageFragment(packageName, true, new NullProgressMonitor());
        fragment.createCompilationUnit(unitName, contents, true, new NullProgressMonitor());
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

    private static void clickButtonAndWaitForShellToClose(SWTBotShell shell, String description,
            String... labels) {
        clickButton(shell, labels);
        waitForShellToClose(shell, description);
    }

    private static void clickButtonAsync(SWTBotShell shell, String... labels) {
        WidgetNotFoundException failure = null;
        for (String label : labels) {
            try {
                var button = shell.bot().button(label);
                shell.display.asyncExec(() -> {
                    if (!button.widget.isDisposed() && button.widget.isEnabled()) {
                        Event event = new Event();
                        event.widget = button.widget;
                        button.widget.notifyListeners(SWT.Selection, event);
                    }
                });
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

    private static void captureCleanUpPreview(SWTBotShell shell, String helpBundle, String fileName)
            throws IOException {
        assertTrue(shell.isOpen(), "The Clean Up preview shell must still be open");
        assertTrue(bot.activeShell().widget == shell.widget,
                "The requested Clean Up preview shell must be active before capture");
        shell.bot().button("Finish");
        shell.bot().button("Cancel");
        capture(shell, helpBundle, fileName);
        System.out.println("[help-screenshots] Captured real Clean Up preview: " + fileName);
    }

    private static void deleteGeneratedScreenshot(String helpBundle, String fileName) throws IOException {
        Files.deleteIfExists(outputRoot.resolve(helpBundle).resolve("images").resolve(fileName));
    }

    private static String visibleShells() {
        return java.util.Arrays.stream(bot.shells()).filter(SWTBotShell::isOpen)
                .map(SWTBotShell::getText).toList().toString();
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

    private static void closeAllDialogs() {
        try {
            SWTBotShell workbench = workbenchShell();
            for (SWTBotShell shell : bot.shells()) {
                if (shell.widget == workbench.widget || shell.widget.isDisposed()) {
                    continue;
                }
                UIThreadRunnable.syncExec(shell.display, new VoidResult() {
                    @Override
                    public void run() {
                        if (!shell.widget.isDisposed()) {
                            shell.widget.close();
                        }
                    }
                });
            }
            workbench.activate();
            bot.sleep(200);
        } catch (WidgetNotFoundException exception) {
            // Workbench already closed.
        }
    }
}
