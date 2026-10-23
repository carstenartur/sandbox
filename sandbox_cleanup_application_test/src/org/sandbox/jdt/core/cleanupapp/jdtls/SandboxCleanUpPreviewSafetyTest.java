/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.core.cleanupapp.jdtls;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.sandbox.jdt.cleanup.multifile.api.IMultiFileCleanUpScopeProvider;
import org.sandbox.jdt.core.cleanupapp.jdtls.SandboxCleanUpCommands.Request;
import org.sandbox.jdt.core.cleanupapp.jdtls.SandboxCleanUpCommands.Snapshot;

/** Exercises review regressions through the real registry, Java model and preview. */
class SandboxCleanUpPreviewSafetyTest {
    private static final String METHOD_REUSE = "org.eclipse.jdt.ui.cleanup.methodreuse";
    private static final String ENCODING = "org.eclipse.jdt.ui.cleanup.explicitencoding";
    private static final String HINT = "sandbox_common.org.sandbox.jdt.triggerpattern.hint";
    private final NullProgressMonitor monitor = new NullProgressMonitor();
    private IProject project;
    private IJavaProject javaProject;

    @BeforeEach
    void createProject() throws Exception {
        Platform.getBundle("org.eclipse.jdt.ls.core").start(Bundle.START_TRANSIENT);
        JavaLanguageServerPlugin.getPreferencesManager().updateClientPrefences(new ClientCapabilities(), Map.of());
        project = ResourcesPlugin.getWorkspace().getRoot().getProject("jdtls-safety-" + UUID.randomUUID());
        project.create(monitor);
        project.open(monitor);
        var description = project.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        project.setDescription(description, monitor);
        var src = project.getFolder("src");
        src.create(true, true, monitor);
        javaProject = JavaCore.create(project);
        javaProject.setRawClasspath(new org.eclipse.jdt.core.IClasspathEntry[] {
                JavaCore.newSourceEntry(src.getFullPath()),
                JavaCore.newContainerEntry(new Path("org.eclipse.jdt.launching.JRE_CONTAINER")) }, monitor);
        var options = javaProject.getOptions(false);
        JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
        javaProject.setOptions(options);
    }

    @AfterEach
    void deleteProject() throws Exception {
        if (project != null && project.exists()) project.delete(true, true, monitor);
    }

    @Test
    void methodReuseHonorsNonDefaultMinimumBeforeProposingExtraction() throws Exception {
        var unit = unit("Repeated", """
                class Repeated {
                    void first(String value) {
                        String text = value.trim();
                        text = text.toLowerCase();
                        System.out.println(text);
                    }
                    void second(String value) {
                        String text = value.trim();
                        text = text.toLowerCase();
                        System.out.println(text);
                    }
                }
                """);
        var options = Map.of("cleanup.method_reuse", "true",
                "cleanup.method_reuse.minimum_statements", "4");
        var cleanup = new SandboxCleanUpCatalog().create(METHOD_REUSE, options);
        assertTrue(List.of(cleanup.getStepDescriptions()).stream().anyMatch(s -> s.contains("at least 4 statements")),
                "The registered wrapper must forward the numeric option to MethodReuseCleanUpCore");
        var before = snapshot(unit);
        var preview = new SandboxCleanUpCommands().preview(
                new Request(METHOD_REUSE, options, List.of(before)), cleanup, monitor);
        assertTrue(preview.edit().getDocumentChanges().isEmpty(), "Three statements do not meet a minimum of four");
        assertEquals(before.source(), unit.getSource());
    }

    @Test
    void markerWritingContributionIsRejectedWithoutChangingExistingDiagnostics() throws Exception {
        var unit = unit("Hints", "class Hints { void run() { System.out.println(1); } }");
        IMarker existing = unit.getResource().createMarker(HINT);
        existing.setAttributes(Map.of(IMarker.MESSAGE, "retain this diagnostic", IMarker.LINE_NUMBER, 2,
                IMarker.SEVERITY, IMarker.SEVERITY_WARNING));
        long id = existing.getId();
        Map<String, Object> attributes = existing.getAttributes();
        var before = snapshot(unit);
        String cleanupId = "org.eclipse.jdt.ui.cleanup.system_out";
        var entry = new SandboxCleanUpCatalog().list().stream().filter(e -> e.id().equals(cleanupId))
                .findFirst().orElseThrow();
        assertFalse(entry.available(), "A text-only preview must not run a marker-writing cleanup");
        assertThrows(IllegalArgumentException.class, () -> new SandboxCleanUpCommands().executeCommand(
                SandboxCleanUpCommands.PREVIEW, List.of(new Request(cleanupId,
                        Map.of("cleanup.system_out", "true"), List.of(before))), monitor));
        IMarker[] after = unit.getResource().findMarkers(HINT, true, IResource.DEPTH_ZERO);
        assertEquals(1, after.length);
        assertEquals(id, after[0].getId());
        assertEquals(attributes, after[0].getAttributes());
        assertEquals(before.source(), unit.getSource());
    }

    @Test
    void unknownIdOrImplementationInQualifiedBundleIsUnavailable() throws Exception {
        var original = registration(METHOD_REUSE);
        assertFalse(describeWith(original, Map.of("id", METHOD_REUSE + ".future")).available());
        assertFalse(describeWith(original, Map.of("class", original.getAttribute("class") + "Future")).available());
        assertTrue(new SandboxCleanUpCatalog().list().stream()
                .filter(e -> e.id().equals(METHOD_REUSE)).findFirst().orElseThrow().available());
    }

    @Test
    void dirtyReadOnlyWorkingCopyIsRejectedBeforeCleanupPreconditions() throws Exception {
        var unit = unit("ReadOnly", "class ReadOnly {}");
        unit.becomeWorkingCopy(monitor);
        try {
            unit.getBuffer().setContents("package p;\nclass ReadOnly { int unsaved; }\n");
            var before = snapshot(unit);
            var entered = new AtomicBoolean();
            setReadOnly(unit, true);
            try {
                assertFalse(unit.isReadOnly(), "This regression must exercise a mutable Java model over a read-only file");
                var failure = assertThrows(IllegalArgumentException.class, () -> new SandboxCleanUpCommands()
                        .preview(new Request(ENCODING, Map.of(), List.of(before)), recordingCleanup(entered), monitor));
                assertTrue(failure.getMessage().contains("Not an editable Java source"), failure.getMessage());
                assertFalse(entered.get());
                assertEquals(before.source(), unit.getSource());
            } finally {
                setReadOnly(unit, false);
            }
        } finally {
            unit.discardWorkingCopy();
        }
    }

    @Test
    void readOnlyExpandedScopeIsRejectedInsteadOfRequestingItsSnapshot() throws Exception {
        var first = unit("First", "class First {}");
        var second = unit("Second", "class Second {}");
        var entered = new AtomicBoolean();
        class ExpandingCleanup extends AbstractCleanUp implements IMultiFileCleanUpScopeProvider {
            @Override public Collection<ICompilationUnit> expandCleanUpScope(IJavaProject p,
                    Collection<ICompilationUnit> selection, IProgressMonitor m) { return List.of(second); }
            @Override public RefactoringStatus checkPreConditions(IJavaProject p, ICompilationUnit[] units,
                    IProgressMonitor m) { entered.set(true); return new RefactoringStatus(); }
        }
        setReadOnly(second, true);
        try {
            var request = new Request(ENCODING, Map.of(), List.of(snapshot(first)));
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> new SandboxCleanUpCommands().preview(request, new ExpandingCleanup(), monitor));
            assertTrue(failure.getMessage().contains("read-only"), failure.getMessage());
            assertFalse(entered.get());
        } finally {
            setReadOnly(second, false);
        }
    }

    private static AbstractCleanUp recordingCleanup(AtomicBoolean entered) {
        return new AbstractCleanUp() {
            @Override public RefactoringStatus checkPreConditions(IJavaProject p, ICompilationUnit[] units,
                    IProgressMonitor m) { entered.set(true); return new RefactoringStatus(); }
        };
    }

    private static void setReadOnly(ICompilationUnit unit, boolean readOnly) throws Exception {
        var attributes = unit.getResource().getResourceAttributes();
        assertNotNull(attributes);
        attributes.setReadOnly(readOnly);
        unit.getResource().setResourceAttributes(attributes);
        assertEquals(readOnly, unit.getResource().getResourceAttributes().isReadOnly());
    }

    private ICompilationUnit unit(String name, String source) throws Exception {
        return javaProject.getPackageFragmentRoot(project.getFolder("src")).createPackageFragment("p", true, monitor)
                .createCompilationUnit(name + ".java", "package p;\n" + source, true, monitor);
    }

    private Snapshot snapshot(ICompilationUnit unit) throws Exception {
        return new Snapshot(unit.getResource().getLocationURI().toString(), 7, unit.getSource());
    }

    private static IConfigurationElement registration(String id) {
        for (var element : Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.jdt.ui.cleanUps")) {
            if ("cleanUp".equals(element.getName()) && id.equals(element.getAttribute("id"))) return element;
        }
        throw new AssertionError("Missing registered cleanup: " + id);
    }

    private static SandboxCleanUpCatalog.Entry describeWith(IConfigurationElement original,
            Map<String, String> attributes) throws Exception {
        IConfigurationElement changed = (IConfigurationElement) Proxy.newProxyInstance(
                IConfigurationElement.class.getClassLoader(), new Class<?>[] { IConfigurationElement.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getAttribute") && attributes.containsKey(args[0])) {
                        return attributes.get(args[0]);
                    }
                    if (method.getName().equals("createExecutableExtension")) {
                        throw new AssertionError("Unqualified contributions must never be instantiated");
                    }
                    return method.invoke(original, args);
                });
        var describe = SandboxCleanUpCatalog.class.getDeclaredMethod("describe", IConfigurationElement.class);
        describe.setAccessible(true);
        return (SandboxCleanUpCatalog.Entry) describe.invoke(null, changed);
    }
}
