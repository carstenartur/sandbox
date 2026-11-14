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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CleanUpPreferenceUtil;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.preferences.cleanup.CleanUpProfileVersioner;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager.CustomProfile;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager.Profile;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileStore;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.pde.core.target.ITargetPlatformService;
import org.eclipse.pde.core.target.LoadTargetDefinitionJob;
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
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.Test;
import org.osgi.framework.FrameworkUtil;

/** Replays the installed headless Status cleanup against the same pinned Workbench source. */
public class PinnedStatusPreviewSWTBotTest {
    private static final String PROJECT = "org.eclipse.jdt.ui";
    private static final String SOURCE = "ui/org/eclipse/jdt/internal/ui/text/java/ProposalSorterHandle.java";
    private static final String PROFILE = "cleanup.simplify_status_creation";
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
        resolvePinnedTarget();
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
                assertArrayEquals(repo.open(tree.getObjectId(0)).getBytes(Charset.defaultCharset()), before);
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
        selectStatusProfile();
        var effective = CleanUpPreferenceUtil.loadOptions(new ProjectScope(project));
        assertEquals(Set.of(PROFILE), effective.entrySet().stream()
                .filter(entry -> CleanUpOptions.TRUE.equals(entry.getValue())).map(Map.Entry::getKey)
                .collect(Collectors.toSet()), "Effective cleanup profile for the pinned JDT project");
        var javaProject = JavaCore.create(project);
        assertNotNull(javaProject.findType("org.eclipse.core.runtime.Status"), "Missing Status from pinned PDE target");
        assertNotNull(javaProject.findType("java.lang.String"), "Missing Java system library");

        Path temp = Files.createTempFile(evidence, "platform-status-", ".png");
        try {
            CleanupWorkbenchDriver.run(scenario, PreviewContract.FILE_COMBINED_DIFF, () -> {
                var wizard = driver.openCleanUpWizard(selectPinnedFile(bot, file));
                var configured = wizard.bot().radio("Use configured profiles");
                if (!configured.isSelected()) {
                    configured.click();
                }
                var preview = driver.openCleanUpPreview(wizard, file.getName());
                var change = CleanupWizardDriver.findTreeItemContaining(preview.tree(), file.getName());
                assertNotNull(change);
                assertTrue(change.isChecked());
                change.select();
                SWTBotShell shell = preview.shell();
                UIThreadRunnable.syncExec(shell.display, () -> {
                    Rectangle screen = shell.widget.getMonitor().getClientArea();
                    shell.widget.setBounds(screen.x, screen.y, Math.min(screen.width, 1900), Math.min(screen.height, 1000));
                    shell.widget.layout(true, true);
                });
                bot.waitUntil(new DefaultCondition() {
                    @Override public boolean test() {
                        return UIThreadRunnable.syncExec(shell.display, (Result<Boolean>) () ->
                                showVisibleDiff(shell.widget, "JavaPlugin.getPluginId(), IStatus.OK, message",
                                        "JavaPlugin.getPluginId(), message"));
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
                    // Remapping the native shell exposes the whole dialog, including GTK button shadows
                    // outside their widgets' allocation and both scrolled comparison panes.
                    shell.widget.setVisible(false);
                    return bounds;
                });
                UIThreadRunnable.syncExec(shell.display, () -> {
                    shell.widget.setVisible(true);
                    shell.widget.forceActive();
                    shell.display.update();
                });
                bot.sleep(650);
                UIThreadRunnable.syncExec(shell.display, () -> {
                    shell.widget.redraw(0, 0, area.width, area.height, true);
                    shell.display.update();
                });
                bot.sleep(650);
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
            Path capture = evidence.resolve("platform-status-pinned.png");
            Path sidecar = evidence.resolve("platform-status-pinned.provenance.json");
            Files.move(temp, capture, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(sidecar, new GsonBuilder().setPrettyPrinting().create().toJson(provenance) + "\n", UTF_8);
            Path published = sandbox.resolve("sandbox_platform_helper_help/images");
            assertEquals(-1L, Files.mismatch(published.resolve(capture.getFileName()), capture),
                    "The regenerated pinned preview differs from the published Help image: " + capture);
            assertEquals(-1L, Files.mismatch(published.resolve(sidecar.getFileName()), sidecar),
                    "The regenerated pinned preview provenance differs from Help: " + sidecar);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static SWTBotTreeItem selectPinnedFile(SWTWorkbenchBot bot, IFile file) {
        CleanupWizardDriver.workbenchShell().activate();
        var display = PlatformUI.getWorkbench().getDisplay();
        var viewer = UIThreadRunnable.syncExec(display, (Result<TreeViewer>) () -> {
            try {
                var page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                var view = page.showView(EXPLORER);
                var tree = (TreeViewer) view.getSite().getSelectionProvider();
                // Scope Project Explorer to the pinned project even when Oomph has configured working sets.
                tree.setInput(file.getProject());
                view.setFocus();
                return tree;
            } catch (org.eclipse.ui.PartInitException error) {
                throw new IllegalStateException(error);
            }
        });
        var element = JavaCore.create(file);
        bot.waitUntil(new DefaultCondition() {
            @Override public boolean test() {
                return UIThreadRunnable.syncExec(display, (Result<Boolean>) () -> {
                    // The navigator may still be populating the source folder and package asynchronously.
                    for (Object target : new Object[] {element, file}) {
                        viewer.setSelection(new StructuredSelection(target), true);
                        var rows = viewer.getTree().getSelection();
                        if (rows.length == 1 && (element.equals(rows[0].getData()) || file.equals(rows[0].getData()))) {
                            return true;
                        }
                    }
                    return false;
                });
            }
            @Override public String getFailureMessage() {
                return "Project Explorer did not reveal " + file.getFullPath() + ". Visible roots: "
                        + UIThreadRunnable.syncExec(display, (Result<String>) () -> Arrays.stream(viewer.getTree().getItems())
                                .map(row -> row.getText()).collect(Collectors.joining(", ")));
            }
        });
        var row = UIThreadRunnable.syncExec(display, (Result<SWTBotTreeItem>) () ->
                new SWTBotTreeItem(viewer.getTree().getSelection()[0]));
        row.select();
        assertTrue(row.isSelected());
        assertEquals(file.getName(), row.getText());
        return row;
    }

    private static void resolvePinnedTarget() throws Exception {
        var context = FrameworkUtil.getBundle(ITargetPlatformService.class).getBundleContext();
        var reference = context.getServiceReference(ITargetPlatformService.class);
        assertNotNull(reference, "The pinned workspace requires PDE target support");
        try {
            var target = context.getService(reference).getWorkspaceTargetDefinition();
            assertEquals("Sandbox upstream JDT migration QA target", target.getName());
            assertNotNull(Platform.getBundle("org.eclipse.oomph.targlets.core"), "Missing Oomph Targlet provider");
            var status = target.resolve(new NullProgressMonitor());
            assertTrue(status.isOK(), status::toString);
            assertTrue(target.getBundles().length > 0, "Pinned target contains no bundles");
            var load = new LoadTargetDefinitionJob(target);
            load.schedule();
            load.join();
            assertTrue(load.getResult().isOK(), () -> load.getResult().toString());
        } finally {
            context.ungetService(reference);
        }
    }

    private static void selectStatusProfile() throws CoreException {
        var defaults = JavaPlugin.getDefault().getCleanUpRegistry().getDefaultOptions(CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS);
        var settings = new HashMap<String, String>();
        for (String key : defaults.getKeys()) {
            String value = defaults.getValue(key);
            settings.put(key, CleanUpOptions.TRUE.equals(value) ? CleanUpOptions.FALSE : value);
        }
        settings.put(PROFILE, CleanUpOptions.TRUE);
        var profile = new CustomProfile("Sandbox pinned Platform Status", settings,
                CleanUpProfileVersioner.CURRENT_VERSION, CleanUpProfileVersioner.PROFILE_KIND);
        var store = new ProfileStore(CleanUpConstants.CLEANUP_PROFILES, new CleanUpProfileVersioner());
        var scope = InstanceScope.INSTANCE;
        var existing = store.readProfiles(scope);
        var profiles = existing == null ? new ArrayList<Profile>() : new ArrayList<>(existing);
        profiles.removeIf(previous -> previous.getID().equals(profile.getID()));
        profiles.add(profile);
        store.writeProfiles(profiles, scope);
        scope.getNode(JavaUI.ID_PLUGIN).put(CleanUpConstants.CLEANUP_PROFILE, profile.getID());
    }

    /**
     * A real two-sided LTK comparison must show the removed and added fragments
     * in different visible source panes at the instant of capture. Finding both
     * strings somewhere in the dialog (or scrolling one pane twice) is not proof.
     */
    private static boolean showVisibleDiff(Shell shell, String before, String after) {
        var panes = new java.util.ArrayList<StyledText>();
        var controls = new ArrayDeque<Control>();
        controls.add(shell);
        while (!controls.isEmpty()) {
            Control control = controls.removeFirst();
            if (!control.isVisible()) {
                continue;
            }
            if (control instanceof StyledText pane) {
                panes.add(pane);
            }
            if (control instanceof Composite parent) {
                java.util.Collections.addAll(controls, parent.getChildren());
            }
        }
        for (StyledText original : panes) {
            if (!showDiffFragment(original, before)) {
                continue;
            }
            for (StyledText modified : panes) {
                if (modified != original && showDiffFragment(modified, after)
                        && showDiffFragment(original, before)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean showDiffFragment(StyledText pane, String fragment) {
        int start = pane.getText().indexOf(fragment);
        if (start < 0) {
            return false;
        }
        int line = pane.getLineAtOffset(start);
        String content = pane.getLine(line);
        int lineOffset = pane.getOffsetAtLine(line);
        int codeStart = lineOffset + content.length() - content.stripLeading().length();
        int codeEnd = lineOffset + content.stripTrailing().length() - 1;
        pane.setTopIndex(Math.max(0, line - 2));
        Rectangle text = pane.getTextBounds(codeStart, codeEnd);
        Rectangle client = pane.getClientArea();
        int desired = Math.max(0, pane.getHorizontalPixel() + text.x - client.x - 8);
        if (desired != pane.getHorizontalPixel()) {
            pane.setHorizontalPixel(desired);
        }
        text = pane.getTextBounds(codeStart, codeEnd);
        pane.redraw();
        pane.update();
        return text.equals(text.intersection(client));
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
