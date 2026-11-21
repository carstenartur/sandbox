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
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
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
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
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
import org.eclipse.pde.internal.core.ClasspathComputer;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
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
import org.osgi.framework.FrameworkUtil;

/** Compares the installed product and native file-level LTK wizard on a retained, pinned source. */
final class PinnedFilePreviewDriver {
    private static boolean targetActivated;
    private static final String EXPLORER = "org.eclipse.ui.navigator.ProjectExplorer";

    private PinnedFilePreviewDriver() {
    }

    static void compare(CleanupScreenshotScenario scenario, String sourcePath, String profileFile, String binding,
            String visibleBefore, String visibleAfter, UnaryOperator<String> expectedSource,
            String evidenceDirectory, String basename, String helpModule) throws Exception {
        Path configuredWorkspace = Path.of(required("SANDBOX_STATUS_WORKSPACE")).toRealPath();
        Path sandbox = SandboxCheckout.locate("sandbox.repository.root");
        var workspace = ResourcesPlugin.getWorkspace();
        Path data = workspace.getRoot().getLocation().toFile().toPath();
        assertEquals(configuredWorkspace, data.toRealPath());
        Properties pins = properties(sandbox.resolve("qa/upstream-jdt/pins.env"));
        assertEquals(pins, properties(data.resolve(".sandbox-jdt-migration-qa-pins.env")));
        resolvePinnedTarget();
        var project = workspace.getRoot().getProject(scenario.projectName());
        IFile file = project.getFile(sourcePath);
        assertTrue(project.isOpen() && file.isAccessible(), "Open the Oomph-provisioned JDT workspace");
        Path checkout = project.getLocation().toFile().toPath().toRealPath().getParent();
        Path source = file.getLocation().toFile().toPath().toRealPath();
        assertEquals(checkout.resolve(scenario.projectName()).resolve(sourcePath), source);
        byte[] before = Files.readAllBytes(source);
        String upstream = pins.getProperty("PIN_JDT_UI_COMMIT");
        String ref = pins.getProperty("PIN_JDT_UI_REF");
        String remote = pins.getProperty("PIN_JDT_UI_REPOSITORY");
        try (Git git = Git.open(checkout.toFile()); RevWalk walk = new RevWalk(git.getRepository())) {
            var repo = git.getRepository();
            assertEquals(remote, repo.getConfig().getString("remote", "origin", "url"));
            assertEquals(upstream, repo.resolve("HEAD^{commit}").name());
            assertEquals(upstream, repo.resolve("refs/tags/" + ref + "^{commit}").name());
            try (TreeWalk tree = TreeWalk.forPath(repo, scenario.projectName() + "/" + sourcePath, walk.parseCommit(repo.resolve("HEAD")).getTree())) {
                assertNotNull(tree);
                assertArrayEquals(repo.open(tree.getObjectId(0)).getBytes(Charset.defaultCharset()), before);
            }
        }

        Path evidence = Path.of(required("SANDBOX_STATUS_EVIDENCE")).resolve(evidenceDirectory).toRealPath();
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
        String expected = expectedSource.apply(original);
        byte[] patch = Files.readAllBytes(evidence.resolve("cleanup-check.patch"));
        assertArrayEquals(org.sandbox.jdt.triggerpattern.git.UnifiedDiffFormatter.format(sourcePath, before, expected.getBytes(UTF_8)), patch);
        Properties profile = properties(sandbox.resolve("qa/upstream-jdt/" + profileFile));
        Set<String> options = profile.stringPropertyNames().stream().filter(key -> CleanUpOptions.TRUE.equals(profile.getProperty(key)))
                .collect(Collectors.toSet());
        assertTrue(!options.isEmpty(), profileFile);
        var bot = new SWTWorkbenchBot();
        var driver = new CleanupWizardDriver(bot);
        selectProfile(profile, scenario.id());
        var effective = CleanUpPreferenceUtil.loadOptions(new ProjectScope(project));
        assertEquals(options, effective.entrySet().stream()
                .filter(entry -> CleanUpOptions.TRUE.equals(entry.getValue())).map(Map.Entry::getKey)
                .collect(Collectors.toSet()), "Effective cleanup profile for the pinned JDT project");
        var javaProject = JavaCore.create(project);
        assertNotNull(javaProject.findType(binding), "Missing " + binding + " from pinned PDE target");
        assertNotNull(javaProject.findType("java.lang.String"), "Missing Java system library");

        ICompilationUnit unit = JavaCore.createCompilationUnitFrom(file);
        var baselineDiagnostics = compilerDiagnostics(unit);
        Path temp = Files.createTempFile(evidence, basename + "-", ".png");
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
                                showVisibleDiff(shell.widget, visibleBefore, visibleAfter));
                    }
                    @Override public String getFailureMessage() {
                        return "Comparison does not display both expected source fragments in " + file.getName();
                    }
                });
                capture(shell, bot, temp);
                CleanupWizardDriver.clickButtonAsync(shell, "Finish");
                driver.waitForShellToClose(shell, "Clean Up wizard");
                assertArrayEquals(expected.getBytes(UTF_8), Files.readAllBytes(source));
                var added = new ArrayList<>(compilerDiagnostics(unit));
                baselineDiagnostics.forEach(added::remove);
                assertTrue(added.isEmpty(), () -> "Introduced Java compiler diagnostics in " + file.getFullPath() + ": " + added);
            });
            assertArrayEquals(before, Files.readAllBytes(source));
            assertEquals(baselineDiagnostics, compilerDiagnostics(unit), "Compiler diagnostics after Undo");
            JsonObject provenance = new JsonObject();
            provenance.addProperty("scenario", scenario.id());
            provenance.addProperty("previewContract", scenario.previewContract().name());
            provenance.addProperty("repository", remote);
            provenance.addProperty("ref", ref);
            provenance.addProperty("eclipseRelease", pins.getProperty("PIN_ECLIPSE_RELEASE"));
            provenance.addProperty("eclipsePlatformVersion", pins.getProperty("PIN_ECLIPSE_PLATFORM_VERSION"));
            provenance.addProperty("commit", upstream);
            provenance.addProperty("project", scenario.projectName());
            provenance.addProperty("sourcePath", sourcePath);
            if (options.size() == 1) {
                provenance.addProperty("enabledOption", options.iterator().next());
            } else {
                var enabled = new com.google.gson.JsonArray();
                options.stream().sorted().forEach(enabled::add);
                provenance.add("enabledOptions", enabled);
            }
            provenance.addProperty("headlessPatchSha256", digest(patch));
            provenance.addProperty("beforeSha256", digest(before));
            provenance.addProperty("afterSha256", digest(expected.getBytes(UTF_8)));
            provenance.addProperty("screenshotSha256", digest(Files.readAllBytes(temp)));
            Path capture = evidence.resolve(basename + ".png");
            Path sidecar = evidence.resolve(basename + ".provenance.json");
            Files.move(temp, capture, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(sidecar, new GsonBuilder().setPrettyPrinting().create().toJson(provenance) + "\n", UTF_8);
            Path published = sandbox.resolve(helpModule).resolve("images");
            assertEquals(-1L, Files.mismatch(published.resolve(capture.getFileName()), capture),
                    "The regenerated pinned preview differs from the published Help image: " + capture);
            assertEquals(-1L, Files.mismatch(published.resolve(sidecar.getFileName()), sidecar),
                    "The regenerated pinned preview provenance differs from Help: " + sidecar);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static void capture(SWTBotShell shell, SWTWorkbenchBot bot, Path temp) {
        capture(shell, bot, temp, () -> true);
    }

    static void capture(SWTBotShell shell, SWTWorkbenchBot bot, Path temp, BooleanSupplier positionComparison) {
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
        bot.waitUntil(new DefaultCondition() {
            private List<PreviewViewport> previous;

            @Override public boolean test() {
                return UIThreadRunnable.syncExec(shell.display, (Result<Boolean>) () -> {
                    // Native remapping and compare painting can restore the initial difference.
                    // Require the requested position to survive painting and another UI turn.
                    var before = previewViewports(shell.widget);
                    boolean positioned = positionComparisonForCapture(shell.widget, positionComparison);
                    var after = previewViewports(shell.widget);
                    boolean settled = positioned && before.equals(after) && after.equals(previous);
                    previous = after;
                    if (settled) {
                        assertTrue(SWTUtils.captureScreenshot(temp.toString(), area));
                    }
                    return settled;
                });
            }

            @Override public String getFailureMessage() {
                return "The requested comparison position did not settle after native window remapping";
            }
        });
    }

    static boolean positionComparisonForCapture(Shell shell, BooleanSupplier positionComparison) {
        return positionComparisonForCapture(shell, positionComparison, shell.getDisplay()::update);
    }

    static boolean positionComparisonForCapture(Shell shell, BooleanSupplier positionComparison, Runnable updateDisplay) {
        boolean positioned = positionComparison.getAsBoolean();
        var requested = previewViewports(shell);
        updateDisplay.run();
        return positioned && requested.equals(previewViewports(shell));
    }

    private record PreviewViewport(StyledText pane, String text, int topPixel, int horizontalPixel,
            Point selection, Rectangle clientArea) {
    }

    private static List<PreviewViewport> previewViewports(Shell shell) {
        return visibleSourcePanes(shell).stream().map(pane -> new PreviewViewport(pane, pane.getText(),
                pane.getTopPixel(), pane.getHorizontalPixel(), pane.getSelection(), pane.getClientArea())).toList();
    }

    // Compare multiplicities. Source positions can shift when a cleanup introduces an import.
    static List<String> compilerDiagnostics(ICompilationUnit unit) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(unit);
        parser.setResolveBindings(true);
        var ast = (CompilationUnit) parser.createAST(new NullProgressMonitor());
        return Arrays.stream(ast.getProblems())
                .filter(problem -> problem.isError() || problem.isWarning())
                .map(problem -> (problem.isError() ? "error " : "warning ") + problem.getID() + " " + problem.getMessage())
                .sorted().toList();
    }

    static SWTBotTreeItem selectPinnedFile(SWTWorkbenchBot bot, IFile file) {
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

    static void resolvePinnedTarget() throws Exception {
        if (targetActivated) {
            return;
        }
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
            // PDE updates Java project's dependency containers in a separate job after target activation.
            Job.getJobManager().join(ClasspathComputer.class, new NullProgressMonitor());
            targetActivated = true;
        } finally {
            context.ungetService(reference);
        }
    }

    static void selectProfile(Properties profileSettings, String name) throws CoreException {
        var defaults = JavaPlugin.getDefault().getCleanUpRegistry().getDefaultOptions(CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS);
        var settings = new HashMap<String, String>();
        for (String key : defaults.getKeys()) {
            String value = defaults.getValue(key);
            settings.put(key, CleanUpOptions.TRUE.equals(value) ? CleanUpOptions.FALSE : value);
        }
        profileSettings.stringPropertyNames().forEach(key -> settings.put(key, profileSettings.getProperty(key)));
        var profile = new CustomProfile(name, settings,
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
    static boolean showVisibleDiff(Shell shell, String before, String after) {
        return showVisibleDiff(shell, before, after, true);
    }

    static boolean showVisibleDiff(Shell shell, String before, String after, boolean entireLine) {
        return showVisibleDiff(shell, before, after, entireLine, StyledText::update);
    }

    static boolean showVisibleDiff(Shell shell, String before, String after, boolean entireLine,
            Consumer<StyledText> updatePane) {
        var panes = visibleSourcePanes(shell);
        for (StyledText original : panes) {
            if (!showDiffFragment(original, before, entireLine, updatePane)) {
                continue;
            }
            for (StyledText modified : panes) {
                if (modified != original && showDiffFragment(modified, after, entireLine, updatePane)
                        && showDiffFragment(original, before, entireLine, updatePane)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<StyledText> visibleSourcePanes(Shell shell) {
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
        return panes;
    }

    private static boolean showDiffFragment(StyledText pane, String fragment, boolean entireLine,
            Consumer<StyledText> updatePane) {
        int start = pane.getText().indexOf(fragment);
        if (start < 0) {
            return false;
        }
        int line = pane.getLineAtOffset(start);
        String content = pane.getLine(line);
        int lineOffset = pane.getOffsetAtLine(line);
        int codeStart = entireLine ? lineOffset + content.length() - content.stripLeading().length() : start;
        int codeEnd = entireLine ? lineOffset + content.stripTrailing().length() - 1 : start + fragment.length() - 1;
        pane.setTopIndex(Math.max(0, line - 2));
        int expectedTopPixel = pane.getTopPixel();
        Rectangle text = pane.getTextBounds(codeStart, codeEnd);
        Rectangle client = pane.getClientArea();
        int desired = Math.max(0, pane.getHorizontalPixel() + text.x - client.x - 8);
        if (desired != pane.getHorizontalPixel()) {
            pane.setHorizontalPixel(desired);
        }
        int expectedHorizontalPixel = pane.getHorizontalPixel();
        pane.redraw();
        updatePane.accept(pane);
        text = pane.getTextBounds(codeStart, codeEnd);
        return pane.getTopPixel() == expectedTopPixel && pane.getHorizontalPixel() == expectedHorizontalPixel
                && text.equals(text.intersection(pane.getClientArea()));
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + key);
        }
        return value;
    }

    static Properties properties(Path file) throws Exception {
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
