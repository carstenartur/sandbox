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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.results.Result;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.ui.PlatformUI;

/** Drives the real JDT cleanup profile and LTK wizard for all preview scenarios. */
final class CleanupWizardDriver {
    private final SWTWorkbenchBot bot;

    CleanupWizardDriver(SWTWorkbenchBot bot) {
        this.bot = bot;
    }

    record CleanUpPreview(SWTBotShell shell, SWTBotTree tree) {
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

    void configureProfile(String name, String tab, Consumer<SWTBotShell> configure) {
        openPreferences();
        SWTBotShell preferences = bot.shell("Preferences").activate();
        selectPreferencePath(preferences.bot().tree(), "Java", "Code Style", "Clean Up");
        clickButton(preferences, "Edit...", "Edit…");
        SWTBotShell profile = bot.activeShell();
        profile.bot().textWithLabel("Profile name:").setText(name);
        profile.bot().tabItem(tab).activate();
        configure.accept(profile);
        clickButtonAndWaitForShellToClose(profile, "Cleanup profile dialog", "OK");
        preferences.activate();
        clickButtonAndWaitForShellToClose(preferences, "Preferences", "Apply and Close", "OK");
    }

    SWTBotShell openCleanUpWizard(SWTBotTreeItem selection) {
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

    private SWTBotShell waitForCleanUpWizard() {
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

    CleanUpPreview openCleanUpPreview(SWTBotShell originatingWizard,
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

    private CleanUpWizardPage waitForCleanUpPreviewOrStatusPage(
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

    private CleanUpWizardPage waitForCleanUpPreviewPage(SWTBotShell originatingWizard,
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

    private List<SWTBotShell> candidateCleanUpShells(SWTBotShell originatingWizard) {
        return Arrays.stream(bot.shells())
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

    String visibleShellDiagnostics() {
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
                        if (control instanceof Table table && table.isVisible()) {
                            for (var row : table.getItems()) {
                                for (int column = 0; column < Math.max(1, table.getColumnCount()); column++) {
                                    description.append(" [").append(row.getText(column)).append("]");
                                }
                            }
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

    static SWTBotTreeItem findTreeItemContaining(SWTBotTree tree, String needle) {
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

    static SWTBotShell workbenchShell() {
        return UIThreadRunnable.syncExec(Display.getDefault(), new Result<SWTBotShell>() {
            @Override
            public SWTBotShell run() {
                return new SWTBotShell(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
            }
        });
    }

    void openPreferences() {
        SWTBotShell workbench = workbenchShell().activate();
        // Global bot.menu(...) first resolves the active shell. On GTK there is
        // a brief gap after a modal dialog closes where no active shell exists.
        // Bind the menu lookup to the known workbench shell instead.
        workbench.bot().menu("Window").menu("Preferences...").click();
        bot.shell("Preferences").activate();
    }

    void selectPreferencePath(SWTBotTree tree, String first, String... rest) {
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

    static void ensureChecked(SWTBotShell shell, String label, boolean checked) {
        var checkBox = shell.bot().checkBox(label);
        if (checkBox.isChecked() != checked) {
            checkBox.click();
        }
    }

    static void clickButton(SWTBotShell shell, String... labels) {
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

    void clickButtonAndWaitForShellToClose(SWTBotShell shell, String description,
            String... labels) {
        clickButton(shell, labels);
        waitForShellToClose(shell, description);
    }

    static void clickButtonAsync(SWTBotShell shell, String... labels) {
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

    void waitForShellToClose(SWTBotShell shell, String description) {
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

    private String visibleShells() {
        return Arrays.stream(bot.shells()).filter(SWTBotShell::isOpen)
                .map(SWTBotShell::getText).toList().toString();
    }

}
