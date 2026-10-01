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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.Job;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.ui.refactoring.ChangePreviewViewerInput;
import org.eclipse.ltk.ui.refactoring.IChangePreviewViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.text.edits.ReplaceEdit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.osgi.framework.FrameworkUtil;

/**
 * Runs coordinated preview scenarios only in the optional product path that
 * installs the pinned JDT UI and LTK replacement bundles.
 */
public class SandboxAtomicPreviewPatchedJdtSWTBotTest {

    private static final String JDT_UI_BUNDLE = "org.eclipse.jdt.ui";
    private static final String COORDINATED_CHANGE =
            "org.eclipse.jdt.internal.corext.fix.CoordinatedCleanUpChange";
    private static final String CLEANUP_PREVIEW_PROJECT = "SandboxCleanupPreviewProject";

    private static SandboxHelpScreenshotsSWTBotTest screenshots;

    @BeforeAll
    public static void setUp() throws Exception {
        var jdtUi = Platform.getBundle(JDT_UI_BUNDLE);
        assertNotNull(jdtUi, "The JDT UI bundle must be installed in the SWTBot runtime");
        jdtUi.loadClass(COORDINATED_CHANGE);
        verifyPatchedLtkRuntime();

        SandboxHelpScreenshotsSWTBotTest.setUp();
        screenshots = new SandboxHelpScreenshotsSWTBotTest();
        CoordinatedJUnitPreviewSWTBotScenario.prepareFixture();
        preparePreviewFixture();
    }

    private static void verifyPatchedLtkRuntime() throws Exception {
        String evidencePath = System.getenv("SANDBOX_LTK_PATCH_EVIDENCE");
        assertNotNull(evidencePath, "The optional atomic host requires Maven-verified LTK provenance");
        Properties evidence = new Properties();
        try (var input = Files.newInputStream(Path.of(evidencePath))) {
            evidence.load(input);
        }
        var ltk = Platform.getBundle("org.eclipse.ltk.core.refactoring");
        assertNotNull(ltk, "The LTK bundle must be installed");
        assertEquals(ltk, FrameworkUtil.getBundle(CompositeChange.class),
                "The test must use the resolved replacement, not another installed LTK bundle");
        assertEquals(evidence.getProperty("bundleVersion"), ltk.getVersion().toString(),
                "Loaded LTK version must match the verified build");
        try (var input = CompositeChange.class.getResourceAsStream("CompositeChange.class")) {
            assertNotNull(input, "The loaded LTK class bytes must be readable");
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            assertEquals(evidence.getProperty("implementationClassSha256"), digest,
                    "Loaded LTK implementation bytes must match the Maven-tested artifact");
        }
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("LtkFileCountProbe");
        CompositeChange probe = new CompositeChange("Distinct affected files");
        probe.add(new TextFileChange("first", project.getFile("First.java")));
        probe.add(new TextFileChange("second", project.getFile("Second.java")));
        try {
            assertEquals(2, probe.getFilenumber(), "The screenshot host must count two real file identities, not one node");
        } finally {
            probe.dispose();
        }
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
    public void completeSafetyDetailsFitAtTheDocumentedPreviewSize() throws Exception {
        Throwable[] failure = new Throwable[1];
        Display.getDefault().syncExec(() -> {
            try {
                assertSafetyDetailsFit();
            } catch (Throwable exception) {
                failure[0] = exception;
            }
        });
        if (failure[0] instanceof Exception exception) {
            throw exception;
        }
        if (failure[0] instanceof Error error) {
            throw error;
        }
    }

    private static void assertSafetyDetailsFit() throws Exception {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("SandboxAtomicLayoutFixture");
        assertFalse(project.exists(), "Refusing to overwrite an existing layout fixture");
        project.create(null);
        Shell shell = null;
        Change candidate = null;
        try {
            project.open(null);
            var description = project.getDescription();
            description.setNatureIds(new String[] { JavaCore.NATURE_ID });
            project.setDescription(description, null);
            JavaCore.create(project).setRawClasspath(new IClasspathEntry[] {
                    JavaCore.newSourceEntry(project.getFullPath()),
                    JavaCore.newContainerEntry(new org.eclipse.core.runtime.Path("org.eclipse.jdt.launching.JRE_CONTAINER"))
            }, null);
            List<Change> changes = new ArrayList<>();
            List<ICompilationUnit> units = new ArrayList<>();
            for (String name : List.of("FirstResource.java", "FirstTest.java")) {
                String source = "public class " + name.replace(".java", "") + " {}\n";
                var file = project.getFile(name);
                file.create(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), true, null);
                units.add(JavaCore.createCompilationUnitFrom(file));
                TextFileChange change = new TextFileChange(name, file);
                change.setTextType("java");
                change.setEdit(new ReplaceEdit(source.indexOf("public class"), 6, "final "));
                changes.add(change);
            }
            project.build(IncrementalProjectBuilder.FULL_BUILD, null);
            assertNoJavaErrors(project);
            String scope = "The cleanup changes multiple source units and is safe only for the proven closed project scope.";
            candidate = (Change) Platform.getBundle(JDT_UI_BUNDLE).loadClass(COORDINATED_CHANGE)
                    .getConstructor(String.class, String.class, List.class, List.class, List.class, Change[].class)
                    .newInstance("Migrates 1 instance rule field(s) together with demo.junit.preview.FirstResource.",
                            scope, List.of("layout-fixture"), List.of(
                                    "Selection is atomic: all required source changes are applied together or not at all.",
                                    "The selected compilation units form a closed coordinated JUnit migration scope.",
                                    "Affected source files: 2", scope), units, changes.toArray(Change[]::new));
            shell = new Shell(Display.getCurrent());
            shell.setLayout(new FillLayout());
            shell.setSize(1280, 456);
            IChangePreviewViewer viewer = (IChangePreviewViewer) Platform.getBundle("sandbox_common")
                    .loadClass("org.sandbox.jdt.cleanup.multifile.ui.CoordinatedCleanUpPreviewViewer")
                    .getConstructor().newInstance();
            viewer.createControl(shell);
            viewer.setInput(new ChangePreviewViewerInput(candidate));
            shell.open();
            shell.layout(true, true);
            shell.update();
            Text details = descendantControls(shell).stream().filter(Text.class::isInstance).map(Text.class::cast)
                    .filter(text -> text.getText().contains("Affected source files: 2")).findFirst().orElseThrow();
            assertTrue(details.getText().endsWith(scope), "The complete safety evidence must be present");
            int requiredHeight = details.computeSize(details.getClientArea().width, SWT.DEFAULT, true).y;
            assertTrue(requiredHeight <= details.getSize().y,
                    "Clipped safety evidence: required height " + requiredHeight + ", available " + details.getSize().y);
            assertEquals(0, details.getTopIndex(), "Evidence must be visible from its beginning");
        } finally {
            if (shell != null && !shell.isDisposed()) {
                shell.dispose();
            }
            if (candidate != null) {
                candidate.dispose();
            }
            project.delete(true, true, null);
        }
    }

    private static List<Control> descendantControls(Composite parent) {
        List<Control> result = new ArrayList<>();
        for (Control child : parent.getChildren()) {
            result.add(child);
            if (child instanceof Composite composite) {
                result.addAll(descendantControls(composite));
            }
        }
        return result;
    }

    @Test
    public void coordinatedIntToEnumPreviewIsAtomic() throws Exception {
        try {
            CleanupWorkbenchDriver.run(CleanupScreenshotScenarios.INT_TO_ENUM, PreviewContract.ATOMIC_CANDIDATE,
                    screenshots::coordinatedIntToEnumPreviewIsAtomic);
        } catch (AssertionError | RuntimeException failure) {
            printWorkspaceLog();
            throw failure;
        }
    }

    @Test
    public void coordinatedJUnitCandidatesAreAtomicAndIndependentlySelectable() throws Exception {
        try {
            CoordinatedJUnitPreviewSWTBotScenario.run();
        } catch (AssertionError | RuntimeException failure) {
            printWorkspaceLog();
            throw failure;
        }
    }

    private static void preparePreviewFixture() throws Exception {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(CLEANUP_PREVIEW_PROJECT);
        assertTrue(project.exists(), "The deterministic coordinated Cleanup preview project must exist");

        NullProgressMonitor monitor = new NullProgressMonitor();
        ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, monitor);
        Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, monitor);
        assertNoJavaErrors(project);
    }

    private static void assertNoJavaErrors(IProject project) throws Exception {
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
                "The coordinated Cleanup preview fixture must compile before SWTBot QA:\n" + errors);
    }

    private static void printWorkspaceLog() {
        try {
            Path log = Platform.getLogFileLocation().toFile().toPath();
            if (Files.isRegularFile(log)) {
                System.out.println("[help-screenshots] Eclipse workspace log after failure:\n"
                        + Files.readString(log));
            } else {
                System.out.println("[help-screenshots] Eclipse workspace log does not exist: " + log);
            }
        } catch (IOException | RuntimeException exception) {
            System.out.println("[help-screenshots] Could not read the Eclipse workspace log: "
                    + exception.getMessage());
        }
    }
}
