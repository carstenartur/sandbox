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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.Properties;
import java.util.Set;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.results.Result;
import org.eclipse.swtbot.swt.finder.utils.SWTUtils;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.Test;

/** Replays the installed headless Status cleanup against the same pinned Workbench source. */
public class PinnedStatusPreviewSWTBotTest {
    private static final String PROJECT = "org.eclipse.jdt.ui";
    private static final String SOURCE = "ui/org/eclipse/jdt/internal/ui/text/java/ProposalSorterHandle.java";
    private static final String PROFILE = "cleanup.simplify_status_creation";
    private static final String LABEL = "Simplify Platform Status (e.g., new Status(...) → Status.error(...))";
    private static final String EXPLORER = "org.eclipse.ui.navigator.ProjectExplorer";

    @Test
    public void comparePinnedStatusPreviewWithHeadlessPatchAndUndo() throws Exception {
        Path configuredWorkspace = Path.of(required("SANDBOX_STATUS_WORKSPACE")).toRealPath();
        Path sandbox = SandboxCheckout.locate("sandbox.repository.root");
        var workspace = ResourcesPlugin.getWorkspace();
        Path data = workspace.getRoot().getLocation().toFile().toPath();
        assertEquals(configuredWorkspace, data.toRealPath());
        Properties pins = properties(sandbox.resolve("qa/upstream-jdt/pins.env"));
        assertEquals(pins, properties(data.resolve(".sandbox-jdt-migration-qa-pins.env")));
        var project = workspace.getRoot().getProject(PROJECT);
        IFile file = project.getFile(SOURCE);
        assertTrue(project.isOpen() && file.isAccessible(), "Open the Oomph-provisioned JDT workspace");
        Path checkout = project.getLocation().toFile().toPath().toRealPath().getParent();
        Path source = file.getLocation().toFile().toPath().toRealPath();
        assertEquals(checkout.resolve(PROJECT).resolve(SOURCE), source);
        byte[] before = Files.readAllBytes(source);
        String upstream = pins.getProperty("PIN_JDT_UI_COMMIT");
        String ref = pins.getProperty("PIN_JDT_UI_REF");
        String remote = pins.getProperty("PIN_JDT_UI_REPOSITORY");
        try (Git git = Git.open(checkout.toFile()); RevWalk walk = new RevWalk(git.getRepository())) {
            var repo = git.getRepository();
            assertEquals(remote, repo.getConfig().getString("remote", "origin", "url"));
            assertEquals(upstream, repo.resolve("HEAD^{commit}").name());
            assertEquals(upstream, repo.resolve("refs/tags/" + ref + "^{commit}").name());
            try (TreeWalk tree = TreeWalk.forPath(repo, PROJECT + "/" + SOURCE, walk.parseCommit(repo.resolve("HEAD")).getTree())) {
                assertNotNull(tree);
                assertArrayEquals(repo.open(tree.getObjectId(0)).getBytes(), before);
            }
        }

        Path evidence = Path.of(required("SANDBOX_STATUS_EVIDENCE")).toRealPath();
        var report = JsonParser.parseString(Files.readString(evidence.resolve("cleanup-check-report.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(0, report.getAsJsonArray("errors").size());
        assertEquals("sandbox-cleanup", report.get("tool").getAsString());
        assertEquals("check", report.get("mode").getAsString());
        assertEquals(0, report.get("errorCount").getAsInt());
        assertEquals(1, report.get("filesProcessed").getAsInt());
        assertEquals(1, report.get("filesChanged").getAsInt());
        var files = report.getAsJsonArray("changedFiles");
        assertEquals(1, files.size());
        assertEquals(source, Path.of(files.get(0).getAsString()).toRealPath());
        String original = new String(before, UTF_8);
        assertEquals(6, original.split("IStatus\\.OK, ", -1).length - 1);
        String expected = original.replace("IStatus.OK, ", "");
        byte[] patch = Files.readAllBytes(evidence.resolve("cleanup-check.patch"));
        assertArrayEquals(org.sandbox.jdt.triggerpattern.git.UnifiedDiffFormatter.format(SOURCE, before, expected.getBytes(UTF_8)), patch);

        var scenario = new CleanupScreenshotScenario("pinned-platform-status", PreviewContract.FILE_COMBINED_DIFF,
                PROJECT, Set.of(SOURCE), Set.of(SOURCE));
        var bot = new SWTWorkbenchBot();
        var driver = new CleanupWizardDriver(bot);
        driver.configureProfile("Sandbox pinned Platform Status", "Platform Status (Sandbox)", profile -> {
            CleanupWizardDriver.clickButton(profile, "Deselect All");
            profile.bot().tabItem("Platform Status (Sandbox)").activate();
            CleanupWizardDriver.ensureChecked(profile, LABEL, true);
        });
        Path temp = Files.createTempFile(evidence, "platform-status-", ".png");
        try {
            CleanupWorkbenchDriver.run(scenario, PreviewContract.FILE_COMBINED_DIFF, () -> {
                var element = JavaCore.create(file);
                PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
                    try {
                        var page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                        var view = page.showView(EXPLORER);
                        var selectionProvider = view.getSite().getSelectionProvider();
                        assertNotNull(selectionProvider, "Project Explorer must expose a selection provider");
                        selectionProvider.setSelection(new StructuredSelection(element));
                    } catch (org.eclipse.ui.PartInitException error) {
                        throw new IllegalStateException(error);
                    }
                });
                var explorer = bot.viewById(EXPLORER).bot().tree();
                var selected = explorer.selection();
                assertEquals(1, selected.rowCount());
                assertEquals(file.getName(), selected.get(0, 0));
                var sourceItem = UIThreadRunnable.syncExec(explorer.display,
                        (Result<org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem>) () ->
                                new org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem(explorer.widget.getSelection()[0]));
                var preview = driver.openCleanUpPreview(driver.openCleanUpWizard(sourceItem), file.getName());
                var change = CleanupWizardDriver.findTreeItemContaining(preview.tree(), file.getName());
                assertNotNull(change);
                assertTrue(change.isChecked());
                change.select();
                SWTBotShell shell = preview.shell();
                bot.waitUntil(new DefaultCondition() {
                    @Override public boolean test() {
                        return UIThreadRunnable.syncExec(shell.display, (Result<Boolean>) () ->
                                showDiffFragment(shell.widget, "JavaPlugin.getPluginId(), IStatus.OK, message")
                                && showDiffFragment(shell.widget, "JavaPlugin.getPluginId(), message"));
                    }
                    @Override public String getFailureMessage() {
                        return "Comparison does not display both Status constructor forms in " + file.getName();
                    }
                });
                var area = UIThreadRunnable.syncExec(shell.display, (Result<Rectangle>) () -> {
                    Rectangle bounds = shell.display.map(shell.widget, null, shell.widget.getClientArea());
                    assertEquals(bounds, bounds.intersection(shell.widget.getMonitor().getClientArea()));
                    shell.display.setCursorLocation(shell.widget.getMonitor().getClientArea().x,
                            shell.widget.getMonitor().getClientArea().y);
                    shell.widget.update();
                    return bounds;
                });
                assertTrue(SWTUtils.captureScreenshot(temp.toString(), area));
                CleanupWizardDriver.clickButtonAsync(shell, "Finish");
                driver.waitForShellToClose(shell, "Clean Up wizard");
                assertArrayEquals(expected.getBytes(UTF_8), Files.readAllBytes(source));
            });
            assertArrayEquals(before, Files.readAllBytes(source));
            JsonObject provenance = new JsonObject();
            provenance.addProperty("scenario", scenario.id());
            provenance.addProperty("previewContract", scenario.previewContract().name());
            provenance.addProperty("repository", remote);
            provenance.addProperty("ref", ref);
            provenance.addProperty("eclipseRelease", pins.getProperty("PIN_ECLIPSE_RELEASE"));
            provenance.addProperty("eclipsePlatformVersion", pins.getProperty("PIN_ECLIPSE_PLATFORM_VERSION"));
            provenance.addProperty("commit", upstream);
            provenance.addProperty("project", PROJECT);
            provenance.addProperty("sourcePath", SOURCE);
            provenance.addProperty("enabledOption", PROFILE);
            provenance.addProperty("headlessPatchSha256", digest(patch));
            provenance.addProperty("beforeSha256", digest(before));
            provenance.addProperty("afterSha256", digest(expected.getBytes(UTF_8)));
            provenance.addProperty("screenshotSha256", digest(Files.readAllBytes(temp)));
            Files.move(temp, evidence.resolve("platform-status-pinned.png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(evidence.resolve("platform-status-pinned.provenance.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(provenance) + "\n", StandardCharsets.UTF_8);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Scroll the real comparison pane until the complete change is visible in the capture. */
    private static boolean showDiffFragment(Shell shell, String fragment) {
        var controls = new ArrayDeque<Control>();
        controls.add(shell);
        while (!controls.isEmpty()) {
            Control control = controls.removeFirst();
            if (!control.isVisible()) {
                continue;
            }
            if (control instanceof StyledText pane) {
                int start = pane.getText().indexOf(fragment);
                if (start >= 0) {
                    pane.setTopIndex(Math.max(0, pane.getLineAtOffset(start) - 2));
                    Rectangle text = pane.getTextBounds(start, start + fragment.length() - 1);
                    Rectangle client = pane.getClientArea();
                    if (text.x < client.x || text.x + text.width > client.x + client.width) {
                        pane.setHorizontalPixel(Math.max(0, pane.getHorizontalPixel() + text.x - client.x - 12));
                        text = pane.getTextBounds(start, start + fragment.length() - 1);
                    }
                    pane.update();
                    if (text.equals(text.intersection(client))) {
                        return true;
                    }
                }
            }
            if (control instanceof Composite parent) {
                java.util.Collections.addAll(controls, parent.getChildren());
            }
        }
        return false;
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + key);
        }
        return value;
    }

    private static Properties properties(Path file) throws Exception {
        Properties result = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            result.load(input);
        }
        return result;
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
