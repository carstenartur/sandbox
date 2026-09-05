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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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

import org.eclipse.ltk.core.refactoring.IUndoManager;
import org.eclipse.ltk.core.refactoring.RefactoringCore;

import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
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

import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseBundleClasspath;

/** Real Workbench scenario for independently selectable atomic JUnit candidates. */
final class CoordinatedJUnitPreviewSWTBotScenario {

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

    private static final String CLEANUP_PREVIEW_PROJECT = "SandboxCleanupPreviewProject";
    private static final String PREVIEW_PACKAGE = "demo.junit.preview";
    private static final String PROJECT_EXPLORER_VIEW = "org.eclipse.ui.navigator.ProjectExplorer";
    private static final String OUTPUT_PROPERTY = "sandbox.help.screenshot.output";
    private static final String SCREENSHOT_FILE = "junit-coordinated-preview.png";
    private static final int SCREENSHOT_CLIENT_WIDTH = 1280;
    private static final int SCREENSHOT_CLIENT_HEIGHT = 900;

    private static final String JUNIT_MASTER_LABEL =
            "Enable JUnit migrations and compatibility rewrites";
    private static final String JUNIT_BEST_EFFORT_LABEL =
            "Best effort: migrate every proven construct and add @todo scaffolds for unresolved gaps (manual repair may be required)";
    private static final String JUNIT_EXTERNAL_RESOURCE_LABEL = "RuleExternalResource";
    private static final String FIRST_CANDIDATE_FRAGMENT = "FirstResource";
    private static final String SECOND_CANDIDATE_FRAGMENT = "SecondResource";

    private static SWTWorkbenchBot bot;
    private static Path outputRoot;

    private CoordinatedJUnitPreviewSWTBotScenario() {
    }

    static void prepareFixture() throws Exception {
        bot = new SWTWorkbenchBot();
        outputRoot = SandboxCheckout.locate(OUTPUT_PROPERTY);
        Files.deleteIfExists(outputRoot.resolve("sandbox_junit_cleanup_help")
                .resolve("images").resolve(SCREENSHOT_FILE));

        IProject project = project();
        assertTrue(project.exists(), "The deterministic coordinated Cleanup preview project must exist");
        IJavaProject javaProject = JavaCore.create(project);
        EclipseBundleClasspath.addBundles(javaProject,
                "org.junit",
                "org.junit.jupiter.api",
                "org.apiguardian.api",
                "org.opentest4j",
                "org.junit.platform.commons");

        IPackageFragmentRoot sourceRoot = javaProject.getPackageFragmentRoot(project.getFolder("src"));
        writeFixtureSources(sourceRoot);
        buildAndAssertNoErrors(project);
    }

    static void run() throws Exception {
        configureJUnitCleanupProfile();

        IFile firstResource = file("FirstResource.java");
        IFile firstTest = file("FirstTest.java");
        IFile secondResource = file("SecondResource.java");
        IFile secondTest = file("SecondTest.java");
        String firstResourceBefore = readFile(firstResource);
        String firstTestBefore = readFile(firstTest);
        String secondResourceBefore = readFile(secondResource);
        String secondTestBefore = readFile(secondTest);

        openProjectExplorer();
        SWTBotTreeItem packageNode = projectTree().getTreeItem(CLEANUP_PREVIEW_PROJECT).expand()
                .getNode("src").expand().getNode(PREVIEW_PACKAGE);
        packageNode.select();
        SWTBotShell wizard = openCleanUpWizard(packageNode);
        CleanUpPreview preview = openCleanUpPreview(wizard, FIRST_CANDIDATE_FRAGMENT);
        wizard = preview.shell();
        prepareForScreenshot(wizard);

        SWTBotTreeItem firstCandidate = findTreeItemContaining(preview.tree(), FIRST_CANDIDATE_FRAGMENT);
        SWTBotTreeItem secondCandidate = findTreeItemContaining(preview.tree(), SECOND_CANDIDATE_FRAGMENT);
        assertTrue(firstCandidate != null, "Preview must expose the first coordinated JUnit candidate");
        assertTrue(secondCandidate != null, "Preview must expose the second coordinated JUnit candidate");
        assertTrue(firstCandidate.isChecked() && secondCandidate.isChecked(),
                "Both independent JUnit candidates must initially be selected");
        assertTrue(firstCandidate.getItems().length == 0 && secondCandidate.getItems().length == 0,
                "Atomic JUnit candidates must be leaves without per-file or per-edit checkboxes");

        firstCandidate.select();
        waitForCoordinatedPreviewDetails(wizard, "FirstResource.java", "FirstTest.java");
        assertTrue(currentPlainText(wizard).contains("Selection is atomic"),
                "The coordinated viewer must explain the atomic selection contract");
        capture(wizard);

        secondCandidate.uncheck();
        assertTrue(firstCandidate.isChecked(),
                "Disabling the second candidate must not disable the independent first candidate");
        assertTrue(!secondCandidate.isChecked(),
                "The second candidate must be independently deselectable as one whole unit");

        clickButtonAsync(wizard, "Finish");
        waitForShellToClose(wizard, "Clean Up wizard");

        String firstResourceAfter = readFile(firstResource);
        String firstTestAfter = readFile(firstTest);
        assertTrue(firstResourceAfter.contains("implements BeforeEachCallback, AfterEachCallback"),
                "The selected resource declaration must migrate to Jupiter callbacks");
        assertTrue(firstTestAfter.contains("@RegisterExtension"),
                "The selected rule consumer must migrate in the same operation");
        assertTrue(!firstResourceBefore.equals(firstResourceAfter)
                && !firstTestBefore.equals(firstTestAfter),
                "The selected atomic candidate must modify both required files");
        assertEquals(secondResourceBefore, readFile(secondResource),
                "The unselected resource declaration must remain byte-identical");
        assertEquals(secondTestBefore, readFile(secondTest),
                "The unselected rule consumer must remain byte-identical");

        undoLastCleanup();
        assertEquals(firstResourceBefore, readFile(firstResource),
                "Undo must restore FirstResource.java byte-exactly");
        assertEquals(firstTestBefore, readFile(firstTest),
                "Undo must restore FirstTest.java byte-exactly");
        assertEquals(secondResourceBefore, readFile(secondResource),
                "Undo must keep SecondResource.java byte-exactly unchanged");
        assertEquals(secondTestBefore, readFile(secondTest),
                "Undo must keep SecondTest.java byte-exactly unchanged");
        buildAndAssertNoErrors(project());
    }

    private static void writeFixtureSources(IPackageFragmentRoot sourceRoot) throws Exception {
        IPackageFragment fragment = sourceRoot.createPackageFragment(PREVIEW_PACKAGE, true,
                new NullProgressMonitor());
        fragment.createCompilationUnit("FirstResource.java", """
                package demo.junit.preview;

                import org.junit.rules.ExternalResource;

                public class FirstResource extends ExternalResource {
                    @Override
                    protected void before() throws Throwable {
                        System.setProperty("first-resource", "started");
                    }

                    @Override
                    protected void after() {
                        System.clearProperty("first-resource");
                    }
                }
                """, true, new NullProgressMonitor());
        fragment.createCompilationUnit("FirstTest.java", """
                package demo.junit.preview;

                import org.junit.Rule;

                public class FirstTest {
                    @Rule
                    public FirstResource resource = new FirstResource();
                }
                """, true, new NullProgressMonitor());
        fragment.createCompilationUnit("SecondResource.java", """
                package demo.junit.preview;

                import org.junit.rules.ExternalResource;

                public class SecondResource extends ExternalResource {
                    @Override
                    protected void before() throws Throwable {
                        System.setProperty("second-resource", "started");
                    }

                    @Override
                    protected void after() {
                        System.clearProperty("second-resource");
                    }
                }
                """, true, new NullProgressMonitor());
        fragment.createCompilationUnit("SecondTest.java", """
                package demo.junit.preview;

                import org.junit.Rule;

                public class SecondTest {
                    @Rule
                    public SecondResource resource = new SecondResource();
                }
                """, true, new NullProgressMonitor());
    }

    private static void configureJUnitCleanupProfile() {
        openPreferences();
        SWTBotShell preferences = bot.shell("Preferences").activate();
        selectPreferencePath(preferences.bot().tree(), "Java", "Code Style", "Clean Up");
        clickButton(preferences, "Edit...", "Edit…");
        SWTBotShell profileDialog = bot.activeShell();
        profileDialog.bot().textWithLabel("Profile name:").setText("Sandbox Coordinated JUnit Preview");
        profileDialog.bot().tabItem("JUnit Migration (Sandbox)").activate();

        ensureChecked(profileDialog, JUNIT_MASTER_LABEL, true);
        ensureChecked(profileDialog, JUNIT_BEST_EFFORT_LABEL, false);
        ensureChecked(profileDialog, JUNIT_EXTERNAL_RESOURCE_LABEL, true);

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
                        // A profile or preferences shell can share the title.
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
                    () -> "The Clean Up condition status blocks the preview. Visible controls:\n"
                            + visibleShellDiagnostics());
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
                        + expectedItemFragment + "'. Visible controls:\n" + visibleShellDiagnostics();
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
                return "The Clean Up preview did not expose '" + expectedItemFragment
                        + "'. Visible controls:\n" + visibleShellDiagnostics();
            }
        });
        return result[0];
    }

    private static List<SWTBotShell> candidateCleanUpShells(SWTBotShell originatingWizard) {
        return Stream.of(bot.shells()).filter(SWTBotShell::isOpen)
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
                // The page replaced the tree while SWTBot was inspecting it; retry.
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
                    if (control instanceof Button button && button.isVisible() && button.isEnabled()
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

    private static void waitForCoordinatedPreviewDetails(SWTBotShell wizard,
            String firstFile, String secondFile) {
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
                    String labels = affectedFiles.cell(0, 0) + "\n" + affectedFiles.cell(1, 0);
                    return labels.contains(firstFile) && labels.contains(secondFile);
                } catch (WidgetNotFoundException exception) {
                    return false;
                }
            }

            @Override
            public String getFailureMessage() {
                return "The coordinated JUnit preview did not expose its atomic details. "
                        + "Visible controls:\n" + visibleShellDiagnostics();
            }
        });
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
                Deque<Control> pending = new ArrayDeque<>();
                pending.add(shell.widget);
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
                return String.join("\n---\n", texts);
            }
        });
    }

    private static void capture(SWTBotShell shell) throws IOException {
        prepareForScreenshot(shell);
        Path imageDirectory = outputRoot.resolve("sandbox_junit_cleanup_help").resolve("images");
        Files.createDirectories(imageDirectory);
        Path image = imageDirectory.resolve(SCREENSHOT_FILE);
        var finish = shell.bot().button("Finish"); //$NON-NLS-1$
        bot.waitUntil(new DefaultCondition() {
            private byte[] previousImage;

            @Override
            public boolean test() throws Exception {
                Boolean captured = UIThreadRunnable.syncExec(shell.display, new Result<Boolean>() {
                    @Override
                    public Boolean run() {
                        if (shell.widget.isDisposed() || finish.widget.isDisposed()
                                || shell.display.getActiveShell() != shell.widget
                                || !finish.widget.isEnabled()) {
                            return Boolean.FALSE;
                        }
                        if (!finish.widget.isFocusControl()) {
                            finish.widget.setFocus();
                            // Let native focus and paint events run on a later poll.
                            // Do not relayout the already sized preview while focusing.
                            return Boolean.FALSE;
                        }
                        Rectangle clientArea = shell.widget.getClientArea();
                        if (clientArea.width != SCREENSHOT_CLIENT_WIDTH
                                || clientArea.height != SCREENSHOT_CLIENT_HEIGHT) {
                            return Boolean.FALSE;
                        }
                        Rectangle clientBounds = shell.display.map(shell.widget, null, clientArea);
                        return Boolean.valueOf(SWTUtils.captureScreenshot(image.toString(), clientBounds));
                    }
                });
                if (!Boolean.TRUE.equals(captured)) {
                    previousImage = null;
                    return false;
                }
                // An active shell can still be repainting its native controls.
                // Keep the real image only after consecutive captures are identical.
                byte[] currentImage = Files.readAllBytes(image);
                boolean stable = previousImage != null && MessageDigest.isEqual(previousImage, currentImage);
                previousImage = currentImage;
                return stable;
            }

            @Override
            public String getFailureMessage() {
                return "Could not capture a stable atomic JUnit preview with Finish enabled and focused"; //$NON-NLS-1$
            }
        });
        assertTrue(Files.isRegularFile(image) && Files.size(image) > 0,
                () -> "Screenshot was not written: " + image);
    }

    private static void prepareForScreenshot(SWTBotShell shell) {
        UIThreadRunnable.syncExec(shell.display, new VoidResult() {
            @Override
            public void run() {
                Rectangle trim = shell.widget.computeTrim(0, 0,
                        SCREENSHOT_CLIENT_WIDTH, SCREENSHOT_CLIENT_HEIGHT);
                shell.widget.setBounds(20, 20, trim.width, trim.height);
                shell.widget.layout(true, true);
                shell.widget.update();
            }
        });
        shell.activate();
        waitForActiveScreenshotShell(shell);
    }

    private static void waitForActiveScreenshotShell(SWTBotShell shell) {
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() {
                try {
                    if (!shell.isOpen() || bot.activeShell().widget != shell.widget) {
                        return false;
                    }
                    Rectangle clientArea = UIThreadRunnable.syncExec(shell.display,
                            new Result<Rectangle>() {
                                @Override
                                public Rectangle run() {
                                    return shell.widget.getClientArea();
                                }
                            });
                    return clientArea.width == SCREENSHOT_CLIENT_WIDTH
                            && clientArea.height == SCREENSHOT_CLIENT_HEIGHT;
                } catch (WidgetNotFoundException exception) {
                    return false;
                }
            }

            @Override
            public String getFailureMessage() {
                return "The screenshot shell did not become active with the requested client area: "
                        + shell.getText();
            }
        });
    }

    private static void openPreferences() {
        SWTBotShell workbench = workbenchShell().activate();
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
        SWTBotTreeItem selectedItem = item;
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() {
                return selectedItem.isSelected();
            }

            @Override
            public String getFailureMessage() {
                return "The requested preference node was not selected: "
                        + selectedItem.getText();
            }
        });
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
    }

    private static String visibleShells() {
        return Stream.of(bot.shells()).filter(SWTBotShell::isOpen)
                .map(SWTBotShell::getText).toList().toString();
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
                    StringBuilder result = new StringBuilder("Shell '")
                            .append(shell.widget.getText()).append("':");
                    Deque<Control> pending = new ArrayDeque<>();
                    pending.add(shell.widget);
                    int count = 0;
                    while (!pending.isEmpty() && count < 80) {
                        Control control = pending.removeFirst();
                        result.append("\n  ").append(control.getClass().getSimpleName())
                                .append(" visible=").append(control.isVisible())
                                .append(" enabled=").append(control.isEnabled());
                        if (control instanceof Button button) {
                            result.append(" text='").append(button.getText()).append("'");
                        } else if (control instanceof Label label) {
                            result.append(" text='").append(label.getText()).append("'");
                        } else if (control instanceof Text text) {
                            result.append(" text='").append(text.getText()).append("'");
                        }
                        count++;
                        if (control instanceof Composite composite) {
                            for (Control child : composite.getChildren()) {
                                pending.addLast(child);
                            }
                        }
                    }
                    return result.toString();
                }
            }));
        }
        return String.join("\n", shells);
    }

    private static IProject project() {
        return ResourcesPlugin.getWorkspace().getRoot().getProject(CLEANUP_PREVIEW_PROJECT);
    }

    private static IFile file(String name) {
        return project().getFile("src/" + PREVIEW_PACKAGE.replace('.', '/') + "/" + name);
    }

    private static String readFile(IFile file) throws IOException {
        return Files.readString(file.getLocation().toFile().toPath(), StandardCharsets.UTF_8);
    }

    private static void buildAndAssertNoErrors(IProject project) throws Exception {
        NullProgressMonitor monitor = new NullProgressMonitor();
        ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, monitor);
        Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, monitor);
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
                "The coordinated JUnit preview fixture must compile:\n" + errors);
    }
}
