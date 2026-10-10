/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.fix.CleanUpRefactoringWizard;
import org.eclipse.jdt.internal.ui.preferences.cleanup.CleanUpProfileVersioner;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.internal.ui.refactoring.ErrorWizardPage;
import org.eclipse.ltk.internal.ui.refactoring.RefactoringStatusDialog;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.results.Result;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.ui.PlatformUI;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;

/** Captures the real registered mathematics cleanup, with executable source provenance. */
final class MathematicsHelpScreenshots {
    private static final NullProgressMonitor MONITOR= new NullProgressMonitor();
    private static final String CLEANUP= "org.sandbox.jdt.ui.cleanup.mathematics"; //$NON-NLS-1$
    private static final String PREFIX= "cleanup.mathematics"; //$NON-NLS-1$

    private MathematicsHelpScreenshots() { }

    static void capture() throws Exception {
        Path root= SandboxCheckout.locate("sandbox.repository.root"); //$NON-NLS-1$
        Path output= SandboxCheckout.locate("sandbox.help.screenshot.output").resolve("sandbox_math_cleanup_help/images"); //$NON-NLS-1$ //$NON-NLS-2$
        try (var directories= Files.list(root.resolve("sandbox_eclipse_help_swtbot_test/fixtures/mathematics"))) { //$NON-NLS-1$
            List<Path> fixtures= directories.filter(Files::isDirectory).sorted().toList();
            assertFalse(fixtures.isEmpty(), "Mathematics screenshot fixtures must exist"); //$NON-NLS-1$
            Files.createDirectories(output);
            for (Path fixture : fixtures) captureFixture(fixture, output);
        }
    }

    private static void captureFixture(Path fixture, Path output) throws Exception {
        Properties properties= new Properties();
        try (var reader= Files.newBufferedReader(fixture.resolve("example.properties"), StandardCharsets.UTF_8)) { properties.load(reader); } //$NON-NLS-1$
        for (String key : List.of("id", "fileName", "packageName", "kinds", "goal", "beforeFragment", "afterFragment", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
                "sourceRepository", "sourcePath", "sourceLines", "excerptSha256")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            assertTrue(properties.containsKey(key), () -> "Missing fixture property: " + key); //$NON-NLS-1$
        // A source archive is not a Git checkout: retain its own verifiable identity.
        boolean gitSource= properties.getProperty("sourceCommit", "").matches("[0-9a-f]{40}"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        boolean archiveSource= !properties.getProperty("sourceArchive", "").isBlank() //$NON-NLS-1$ //$NON-NLS-2$
                && properties.getProperty("sourceArchiveSha256", "").matches("[0-9a-f]{64}"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertTrue(gitSource || archiveSource, "Fixture needs a pinned Git commit or source-archive hash"); //$NON-NLS-1$
        String id= properties.getProperty("id"), name= properties.getProperty("fileName"); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(fixture.getFileName().toString(), id);
        assertTrue(id.matches("[0-9]{2}-[a-z0-9-]+")); //$NON-NLS-1$
        byte[] original= Files.readAllBytes(fixture.resolve("before.java.txt")); //$NON-NLS-1$
        String before= new String(original, StandardCharsets.UTF_8), after= Files.readString(fixture.resolve("after.java.txt")); //$NON-NLS-1$
        assertEquals(properties.getProperty("excerptSha256"), sha256(original)); //$NON-NLS-1$
        assertNotEquals(before, after, "Each screenshot must demonstrate an actual source change"); //$NON-NLS-1$
        Path png= output.resolve(id + ".png"), provenance= output.resolve(id + ".provenance.json"); //$NON-NLS-1$ //$NON-NLS-2$
        Files.deleteIfExists(png); Files.deleteIfExists(provenance);
        SWTWorkbenchBot bot= new SWTWorkbenchBot();
        AtomicReference<CleanUpRefactoringWizard> model= new AtomicReference<>();
        AtomicReference<Throwable> failure= new AtomicReference<>(); AtomicBoolean finished= new AtomicBoolean(true);
        IProject resource= ResourcesPlugin.getWorkspace().getRoot().getProject("SandboxMathScreenshot-" + id); //$NON-NLS-1$
        assertFalse(resource.exists(), "Screenshot project must not replace an existing project"); //$NON-NLS-1$
        Throwable primaryFailure= null;
        try {
            IJavaProject project= createProject(resource); Map<String, String> profile= profile(properties); persist(project, profile);
            String bundles= properties.getProperty("bundles", ""); //$NON-NLS-1$ //$NON-NLS-2$
            if (!bundles.isBlank()) addCorpusBundles(project, bundles.split(",")); //$NON-NLS-1$
            var pack= project.getPackageFragmentRoot(resource.getFolder("src")).createPackageFragment(properties.getProperty("packageName"), true, MONITOR); //$NON-NLS-1$ //$NON-NLS-2$
            ICompilationUnit unit= pack.createCompilationUnit(name, before, true, MONITOR);
            Path source= unit.getResource().getLocation().toFile().toPath();
            assertArrayEquals(original, Files.readAllBytes(source)); assertCompiles(unit);
            RefactoringCore.getUndoManager().flush();
            CleanUpRefactoring refactoring= new CleanUpRefactoring(); refactoring.setUseOptionsFromProfile(true); refactoring.addCompilationUnit(unit);
            for (ICleanUp cleanup : JavaPlugin.getDefault().getCleanUpRegistry().createCleanUps(Set.of(CLEANUP))) refactoring.addCleanUp(cleanup);
            assertEquals(1, refactoring.getCleanUps().length, "The registered mathematics cleanup must be installed"); //$NON-NLS-1$
            finished.set(false);
            Display.getDefault().asyncExec(() -> {
                try {
                    var wizard= new CleanUpRefactoringWizard(refactoring, RefactoringWizard.DIALOG_BASED_USER_INTERFACE | RefactoringWizard.PREVIEW_EXPAND_FIRST_NODE);
                    model.set(wizard); new RefactoringWizardOpenOperation(wizard).run(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Mathematics"); //$NON-NLS-1$
                } catch (Throwable exception) { failure.set(exception); } finally { finished.set(true); }
            });
            SWTBotShell dialog= bot.shell("Clean Up").activate(); //$NON-NLS-1$
            ui(() -> { Rectangle trim= dialog.widget.computeTrim(0, 0, 1280, 900); dialog.widget.setBounds(20, 20, trim.width, trim.height); dialog.widget.layout(true, true); return null; });
            dialog.bot().radio("Use configured profiles").click(); advance(bot, dialog, model, failure); //$NON-NLS-1$
            List<TextChange> changes= new ArrayList<>(); textChanges(refactoring.createChange(MONITOR), changes);
            assertEquals(1, changes.size(), "The native preview must contain exactly the fixture file"); //$NON-NLS-1$
            assertEquals(after, changes.getFirst().getPreviewContent(MONITOR), "Actual registered cleanup preview"); //$NON-NLS-1$
            SWTBotTreeItem file= findFile(dialog.bot().tree().getAllItems(), name);
            assertTrue(file != null, "The fixture must appear in the native Changes tree"); //$NON-NLS-1$
            file.select(); assertTrue(file.isChecked());
            await(bot, () -> ui(() -> visibleSource(dialog.widget, before) != null && visibleSource(dialog.widget, after) != null), "Native before/after documents are visible"); //$NON-NLS-1$
            ui(() -> {
                StyledText originalPane= visibleSource(dialog.widget, before), resultPane= visibleSource(dialog.widget, after);
                showFragment(originalPane, properties.getProperty("beforeFragment")); showFragment(resultPane, properties.getProperty("afterFragment")); //$NON-NLS-1$ //$NON-NLS-2$
                assertFragmentVisible(originalPane, properties.getProperty("beforeFragment")); assertFragmentVisible(resultPane, properties.getProperty("afterFragment")); return null; //$NON-NLS-1$ //$NON-NLS-2$
            });
            byte[] image= stableScreenshot(bot, dialog.widget);
            button(dialog.widget, "OK", "Finish").click(); await(bot, finished::get, "Cleanup wizard completed"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            assertNull(failure.get()); assertFalse(dialog.isOpen()); assertEquals(after, unit.getSource()); assertArrayEquals(after.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(source)); assertCompiles(unit);
            assertTrue(RefactoringCore.getUndoManager().anythingToUndo()); RefactoringCore.getUndoManager().performUndo(null, MONITOR);
            assertEquals(before, unit.getSource()); assertArrayEquals(original, Files.readAllBytes(source));
            Map<String, String> evidence= new TreeMap<>(); properties.stringPropertyNames().forEach(key -> evidence.put(key, properties.getProperty(key)));
            evidence.put("before", before); evidence.put("after", after); evidence.put("afterSha256", sha256(after.getBytes(StandardCharsets.UTF_8))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            var installedCleanup= Platform.getBundle("sandbox_math_cleanup"); //$NON-NLS-1$
            assertTrue(installedCleanup != null);
            try (var sdk= java.util.Objects.requireNonNull(installedCleanup.getEntry("lib/regelsuche-optimization-sdk.jar")).openStream()) { //$NON-NLS-1$
                String sdkHash= sha256(sdk.readAllBytes());
                assertEquals(properties.getProperty("sdkSha256"), sdkHash, "Preview must use the qualified SDK artifact"); //$NON-NLS-1$ //$NON-NLS-2$
                evidence.put("sdkSha256", sdkHash); //$NON-NLS-1$
            }
            var adapter= FileLocator.getBundleFileLocation(installedCleanup).orElseThrow().toPath();
            evidence.put("adapterClassesSha256", BundleClassFingerprint.sha256(adapter)); //$NON-NLS-1$
            // The complete archive changes with the Tycho build qualifier. Retain
            // it in the execution log, not the reproducible source/image metadata.
            if (Files.isRegularFile(adapter)) System.out.println("MATHEMATICS_CAPTURE_BUNDLE " + id //$NON-NLS-1$
                    + " sha256=" + sha256(Files.readAllBytes(adapter))); //$NON-NLS-1$
            evidence.put("targetJava", project.getOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, true)); //$NON-NLS-1$
            evidence.put("screenshotSha256", sha256(image)); evidence.put("verification", "AST clean; native preview exact; apply exact; undo byte exact"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            Files.write(png, image); Files.writeString(provenance, "{\n  \"schemaVersion\": 1,\n  \"profile\": " + json(profile) + ",\n  \"evidence\": " + json(evidence) + "\n}\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        } catch (Exception | Error exception) {
            primaryFailure= exception;
            throw exception;
        } finally {
            try {
                ui(() -> { Shell shell= model.get() == null ? null : model.get().getShell(); if (shell != null && !shell.isDisposed()) { for (Shell child : shell.getShells()) child.close(); shell.close(); } return null; });
                await(bot, finished::get, "Wizard must finish before fixture deletion"); //$NON-NLS-1$
                RefactoringCore.getUndoManager().flush(); if (resource.exists()) resource.delete(true, true, MONITOR);
                assertNull(failure.get(), () -> "Asynchronous cleanup failure: " + failure.get()); //$NON-NLS-1$
            } catch (Exception | Error cleanupFailure) {
                if (primaryFailure == null) throw cleanupFailure;
                primaryFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    /** Production corpora resolve against real installed bundles, never source stubs.
     * These fixtures need no JUnit container or JDT JUnit UI dependency. */
    private static void addCorpusBundles(IJavaProject project, String[] bundleNames) throws Exception {
        List<IClasspathEntry> entries= new ArrayList<>(Arrays.asList(project.getRawClasspath()));
        for (String name : bundleNames) {
            var bundle= Platform.getBundle(name);
            assertTrue(bundle != null, () -> "Missing production corpus dependency: " + name); //$NON-NLS-1$
            var file= FileLocator.getBundleFileLocation(bundle).orElseThrow();
            entries.add(JavaCore.newLibraryEntry(org.eclipse.core.runtime.Path.fromOSString(file.getAbsolutePath()), null, null));
        }
        project.setRawClasspath(entries.toArray(IClasspathEntry[]::new), MONITOR);
    }

    private static IJavaProject createProject(IProject resource) throws Exception {
        resource.create(MONITOR); resource.open(MONITOR); resource.setDefaultCharset(StandardCharsets.UTF_8.name(), MONITOR);
        var description= resource.getDescription(); description.setNatureIds(new String[] { JavaCore.NATURE_ID }); resource.setDescription(description, MONITOR);
        resource.getFolder("src").create(true, true, MONITOR); resource.getFolder("bin").create(true, true, MONITOR); //$NON-NLS-1$ //$NON-NLS-2$
        IJavaProject project= JavaCore.create(resource);
        project.setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(resource.getFolder("src").getFullPath()), //$NON-NLS-1$
                JavaCore.newContainerEntry(new org.eclipse.core.runtime.Path("org.eclipse.jdt.launching.JRE_CONTAINER")) }, resource.getFolder("bin").getFullPath(), MONITOR); //$NON-NLS-1$ //$NON-NLS-2$
        Map<String, String> options= project.getOptions(true); JavaCore.setComplianceOptions("17", options); project.setOptions(options); return project; //$NON-NLS-1$
    }

    private static Map<String, String> profile(Properties properties) {
        Map<String, String> values= new TreeMap<>(); values.put(PREFIX, "true"); //$NON-NLS-1$
        values.put(PREFIX + ".numericKinds", properties.getProperty("kinds")); values.put(PREFIX + ".goal", properties.getProperty("goal")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        values.put(PREFIX + ".safetyProfile", "PRESERVE_JAVA"); values.put(PREFIX + ".checkedOptIn", "false"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        values.put(PREFIX + ".workBudget", properties.getProperty("workBudget", "1000000")); values.put(PREFIX + ".maxStates", properties.getProperty("maxStates", "20000")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        values.put(PREFIX + ".exclusions", ""); values.put(PREFIX + ".underflowChecks", "false"); return values; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    private static void persist(IJavaProject project, Map<String, String> profile) throws Exception {
        var preferences= new ProjectScope(project.getProject()).getNode(JavaUI.ID_PLUGIN);
        Map<String, String> options= new TreeMap<>(JavaPlugin.getDefault().getCleanUpRegistry().getDefaultOptions(CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS).getMap());
        options.replaceAll((key, value) -> "true".equals(value) ? "false" : value); options.putAll(profile); options.forEach(preferences::put); //$NON-NLS-1$ //$NON-NLS-2$
        preferences.put(CleanUpConstants.CLEANUP_PROFILE, "_Mathematics_" + project.getElementName()); //$NON-NLS-1$
        preferences.putInt(CleanUpConstants.CLEANUP_SETTINGS_VERSION_KEY, new CleanUpProfileVersioner().getCurrentVersion()); preferences.flush();
    }

    private static void assertCompiles(ICompilationUnit unit) {
        ASTParser parser= ASTParser.newParser(AST.getJLSLatest()); parser.setSource(unit); parser.setResolveBindings(true);
        for (var problem : ((CompilationUnit) parser.createAST(MONITOR)).getProblems()) assertFalse(problem.isError(), problem.toString());
    }

    private static void textChanges(Change change, List<TextChange> result) {
        if (change instanceof TextChange text) result.add(text);
        else if (change instanceof CompositeChange composite) for (Change child : composite.getChildren()) textChanges(child, result);
        else throw new AssertionError("Unexpected native preview change: " + change.getClass().getName()); //$NON-NLS-1$
    }

    private static void advance(SWTWorkbenchBot bot, SWTBotShell dialog, AtomicReference<CleanUpRefactoringWizard> model, AtomicReference<Throwable> failure) {
        Set<Shell> acknowledged= new HashSet<>();
        for (int attempt= 0; attempt < 4; attempt++) {
            IWizardPage before= ui(() -> model.get().getContainer().getCurrentPage());
            if (before != null && before.getClass().getSimpleName().contains("Preview")) return; //$NON-NLS-1$
            button(dialog.widget, "Preview >", "Next >").click(); //$NON-NLS-1$ //$NON-NLS-2$
            await(bot, () -> {
                assertNull(failure.get()); IWizardPage current= ui(() -> model.get().getContainer().getCurrentPage());
                if (current instanceof ErrorWizardPage error) {
                    var status= ui(error::getStatus); assertTrue(status != null && !status.hasError() && !status.hasFatalError(), String.valueOf(status));
                    Shell diagnostic= ui(() -> Arrays.stream(dialog.widget.getShells()).filter(shell -> !shell.isDisposed() && shell.isVisible() && shell.getData() instanceof RefactoringStatusDialog).findFirst().orElse(null));
                    if (diagnostic != null) { if (acknowledged.add(diagnostic)) button(diagnostic, "Continue").click(); return false; } //$NON-NLS-1$
                }
                return ui(() -> current != null && current != before && current.getControl() != null && current.getControl().isVisible());
            }, "Native cleanup wizard reaches its next page"); //$NON-NLS-1$
        }
        throw new AssertionError("The standard Clean Up wizard did not reach Preview"); //$NON-NLS-1$
    }

    private static SWTBotTreeItem findFile(SWTBotTreeItem[] items, String name) {
        for (SWTBotTreeItem item : items) { if (item.getText().contains(name)) return item; item.expand(); SWTBotTreeItem match= findFile(item.getItems(), name); if (match != null) return match; }
        return null;
    }

    private static StyledText visibleSource(Shell shell, String source) {
        return controls(shell).stream().filter(StyledText.class::isInstance).map(StyledText.class::cast).filter(pane -> pane.isVisible() && pane.getText().equals(source)).findFirst().orElse(null);
    }

    private static void showFragment(StyledText pane, String fragment) {
        assertFalse(fragment.isBlank()); int start= pane.getText().indexOf(fragment); assertTrue(start >= 0);
        pane.setTopIndex(Math.max(0, pane.getLineAtOffset(start) - 8)); pane.setHorizontalPixel(0);
    }

    private static void assertFragmentVisible(StyledText pane, String fragment) {
        assertTrue(pane.isVisible()); assertFalse(pane.getEditable()); int start= pane.getText().indexOf(fragment); assertTrue(start >= 0);
        Rectangle client= pane.getClientArea();
        for (int offset= start; offset < start + fragment.length(); offset++) {
            char character= pane.getText().charAt(offset); if (character == '\r' || character == '\n') continue;
            Rectangle bounds= pane.getTextBounds(offset, offset);
            assertTrue(client.contains(bounds.x, bounds.y) && client.contains(bounds.x + bounds.width - 1, bounds.y + bounds.height - 1), "The documented arithmetic fragment must fit the visible native source pane"); //$NON-NLS-1$
        }
    }

    private static byte[] stableScreenshot(SWTWorkbenchBot bot, Shell shell) {
        AtomicReference<byte[]> previous= new AtomicReference<>(), stable= new AtomicReference<>();
        await(bot, () -> { byte[] current= ui(() -> screenshot(shell)), old= previous.getAndSet(current); if (old != null && Arrays.equals(old, current)) { stable.set(current); return true; } return false; }, "Native screenshot becomes visually stable"); //$NON-NLS-1$
        return stable.get();
    }

    private static byte[] screenshot(Shell shell) {
        Rectangle client= shell.getClientArea(); assertEquals(1280, client.width); assertEquals(900, client.height);
        Rectangle screen= shell.getDisplay().map(shell, null, client); assertTrue(shell.getDisplay().getClientArea().contains(screen.x, screen.y) && shell.getDisplay().getClientArea().contains(screen.x + 1279, screen.y + 899));
        shell.update(); Image image= new Image(shell.getDisplay(), 1280, 900); GC gc= new GC(shell);
        try { gc.copyArea(image, client.x, client.y); ImageLoader loader= new ImageLoader(); loader.data= new ImageData[] { image.getImageData() }; ByteArrayOutputStream out= new ByteArrayOutputStream(); loader.save(out, SWT.IMAGE_PNG); return out.toByteArray(); }
        finally { gc.dispose(); image.dispose(); }
    }

    private static SWTBotButton button(Shell shell, String... labels) {
        return new SWTBotButton(ui(() -> controls(shell).stream().filter(Button.class::isInstance).map(Button.class::cast).filter(control -> control.isVisible() && Arrays.asList(labels).contains(control.getText().replace("&", ""))).findFirst().orElseThrow())); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static List<Control> controls(Composite parent) {
        List<Control> result= new ArrayList<>(); for (Control child : parent.getChildren()) { result.add(child); if (child instanceof Composite composite) result.addAll(controls(composite)); } return result;
    }

    private static void await(SWTWorkbenchBot bot, BooleanSupplier condition, String description) {
        bot.waitUntil(new DefaultCondition() { @Override public boolean test() { return condition.getAsBoolean(); } @Override public String getFailureMessage() { return description; } }, 30000, 100);
    }

    private record Outcome<T>(T value, Throwable failure) { }
    private static <T> T ui(Callable<T> operation) {
        Outcome<T> result= UIThreadRunnable.syncExec(Display.getDefault(), new Result<Outcome<T>>() { @Override public Outcome<T> run() { try { return new Outcome<>(operation.call(), null); } catch (Throwable failure) { return new Outcome<>(null, failure); } } });
        if (result.failure() instanceof Error error) throw error;
        if (result.failure() != null) throw new IllegalStateException(result.failure()); return result.value();
    }

    private static String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } //$NON-NLS-1$
    private static String json(Map<String, String> values) { return "{" + values.entrySet().stream().map(entry -> quote(entry.getKey()) + ": " + quote(entry.getValue())).collect(java.util.stream.Collectors.joining(",\n    ")) + "}"; } //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    private static String quote(String value) {
        StringBuilder escaped= new StringBuilder("\""); //$NON-NLS-1$
        for (char character : value.toCharArray()) switch (character) {
            case '\\' -> escaped.append("\\\\"); case '"' -> escaped.append("\\\""); case '\n' -> escaped.append("\\n"); case '\r' -> escaped.append("\\r"); case '\t' -> escaped.append("\\t"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            default -> { if (character < 0x20) escaped.append(String.format("\\u%04x", (int) character)); else escaped.append(character); } //$NON-NLS-1$
        }
        return escaped.append('"').toString();
    }
}
