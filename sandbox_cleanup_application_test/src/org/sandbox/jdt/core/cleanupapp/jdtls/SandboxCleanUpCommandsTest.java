/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.core.cleanupapp.jdtls;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.cleanup.multifile.api.IMultiFileCleanUpScopeProvider;
import org.sandbox.jdt.core.cleanupapp.jdtls.SandboxCleanUpCommands.Request;
import org.sandbox.jdt.core.cleanupapp.jdtls.SandboxCleanUpCommands.Snapshot;

class SandboxCleanUpCommandsTest {
    private static final String ENCODING = "org.eclipse.jdt.ui.cleanup.explicitencoding";
    private static final Map<String, String> OPTIONS = Map.of(
            "cleanup.explicit_encoding", "true", "cleanup.explicit_encoding_keep_behavior", "true");
    private final NullProgressMonitor monitor = new NullProgressMonitor();
    private IProject project;
    private IJavaProject javaProject;

    @BeforeEach
    void createProject() throws Exception {
        project = ResourcesPlugin.getWorkspace().getRoot().getProject("jdtls-cleanup-" + UUID.randomUUID());
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
        assertFalse(PlatformUI.isWorkbenchRunning());
    }

    @AfterEach
    void deleteProject() throws Exception {
        if (project != null && project.exists()) project.delete(true, true, monitor);
    }

    private ICompilationUnit unit(String name, String source) throws Exception {
        var root = javaProject.getPackageFragmentRoot(project.getFolder("src"));
        return root.createPackageFragment("p", true, monitor)
                .createCompilationUnit(name + ".java", "package p;\n" + source, true, monitor);
    }

    private Snapshot snapshot(ICompilationUnit unit) throws Exception {
        return new Snapshot(unit.getResource().getLocationURI().toString(), 7, unit.getSource());
    }

    @Test
    void discoversExistingJavaCleanupsWithoutThePortedWhileToFor() throws Exception {
        var catalog = new SandboxCleanUpCatalog();
        var entries = catalog.list();
        var bundles = entries.stream().map(SandboxCleanUpCatalog.Entry::bundle).collect(Collectors.toSet());
        assertTrue(bundles.containsAll(Set.of("sandbox_encoding_quickfix", "sandbox_functional_converter",
                "sandbox_platform_helper", "sandbox_jface_cleanup", "sandbox_junit_cleanup",
                "sandbox_method_reuse", "sandbox_int_to_enum", "sandbox_math_cleanup",
                "sandbox_container_cleanup", "sandbox_use_general_type")), bundles.toString());
        assertFalse(bundles.contains("sandbox_tools"));
        for (var entry : entries) {
            if (entry.available()) assertNotNull(catalog.create(entry.id(), entry.defaults()).getRequirements());
        }
        assertFalse(PlatformUI.isWorkbenchRunning());
    }

    @Test
    void refusesTheXmlCleanupBeforeItsSideEffectingRewriteCanRun() throws Exception {
        var catalog = new SandboxCleanUpCatalog();
        var xml = catalog.list().stream().filter(e -> e.bundle().equals("sandbox_xml_cleanup")).findFirst().orElseThrow();
        assertFalse(xml.available());
        assertFalse(xml.reason().isBlank());
        assertThrows(IllegalArgumentException.class, () -> catalog.create(xml.id(), Map.of("cleanup.xmlcleanup", "true")));
    }

    @Test
    void extensionCreatesAnOfficialJdtLsDelegate() throws Exception {
        var elements = Platform.getExtensionRegistry().getConfigurationElementsFor(
                "org.eclipse.jdt.ls.core.delegateCommandHandler");
        for (var element : elements) {
            if (SandboxCleanUpCommands.class.getName().equals(element.getAttribute("class"))) {
                assertInstanceOf(IDelegateCommandHandler.class, element.createExecutableExtension("class"));
                return;
            }
        }
        fail("Sandbox delegate is missing from the actual extension registry");
    }

    @Test
    void encodingPreviewUsesCharsetOverloadAndRemovesObsoleteCatchWithoutWriting() throws Exception {
        var unit = unit("Example", """
                class Example {
                    String decode(byte[] bytes) {
                        try { return new String(bytes, "UTF-8"); }
                        catch (java.io.UnsupportedEncodingException e) { throw new AssertionError(e); }
                    }
                }
                """);
        var before = snapshot(unit);
        var commands = new SandboxCleanUpCommands();
        var result = (SandboxCleanUpCommands.Preview) commands.executeCommand(
                SandboxCleanUpCommands.PREVIEW, List.of(new Request(ENCODING, OPTIONS, List.of(before))), monitor);
        assertTrue(result.requiredUris().isEmpty());
        assertEquals(1, result.edit().getDocumentChanges().size());
        var change = result.edit().getDocumentChanges().getFirst().getLeft();
        assertEquals(before.uri(), change.getTextDocument().getUri());
        assertEquals(7, change.getTextDocument().getVersion());
        String after = apply(before.source(), change.getEdits());
        assertTrue(after.contains("StandardCharsets.UTF_8"), after);
        assertFalse(after.contains("UnsupportedEncodingException"), after);
        assertEquals(before.source(), unit.getSource(), "Preview must not mutate the working copy or file");
        assertFalse(PlatformUI.isWorkbenchRunning());
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setProject(javaProject);
        parser.setUnitName("p/Example.java");
        parser.setSource(after.toCharArray());
        parser.setResolveBindings(true);
        var ast = (CompilationUnit) parser.createAST(monitor);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        unit.getBuffer().setContents(after);
        unit.save(monitor, true);
        var again = commands.preview(new Request(ENCODING, OPTIONS, List.of(snapshot(unit))),
                new SandboxCleanUpCatalog().create(ENCODING, OPTIONS), monitor);
        assertTrue(again.edit().getDocumentChanges().isEmpty(), "Existing cleanup must remain idempotent");
    }

    @Test
    void staleSourceIsRejectedBeforeCleanupRuns() throws Exception {
        var unit = unit("Example", "class Example {}");
        var stale = new Snapshot(snapshot(unit).uri(), 7, "old editor contents");
        var entered = new AtomicBoolean();
        var cleanup = new AbstractCleanUp() {
            @Override public ICleanUpFix createFix(CleanUpContext context) {
                entered.set(true);
                return null;
            }
        };
        assertThrows(IllegalArgumentException.class, () -> new SandboxCleanUpCommands()
                .preview(new Request(ENCODING, OPTIONS, List.of(stale)), cleanup, monitor));
        assertFalse(entered.get());
    }

    @Test
    void cancellationNeverWritesSource() throws Exception {
        var unit = unit("Example", "class Example {}");
        var before = snapshot(unit);
        var cancelled = new NullProgressMonitor();
        cancelled.setCanceled(true);
        assertThrows(OperationCanceledException.class, () -> new SandboxCleanUpCommands().preview(
                new Request(ENCODING, OPTIONS, List.of(before)), new AbstractCleanUp() {}, cancelled));
        assertEquals(before.source(), unit.getSource());
    }

    @Test
    void oneInstanceSeesTheCompleteSelectionAndReleasesItsPlanOnFailure() throws Exception {
        var first = unit("First", "class First {}");
        var second = unit("Second", "class Second {}");
        var released = new AtomicBoolean();
        var cleanup = new AbstractCleanUp() {
            @Override public RefactoringStatus checkPreConditions(IJavaProject p, ICompilationUnit[] units, IProgressMonitor m) {
                assertEquals(2, units.length);
                return new RefactoringStatus();
            }
            @Override public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
                throw new CoreException(Status.error("Deliberate regression fixture"));
            }
            @Override public RefactoringStatus checkPostConditions(IProgressMonitor m) {
                released.set(true);
                return new RefactoringStatus();
            }
        };
        var request = new Request(ENCODING, OPTIONS, List.of(snapshot(first), snapshot(second)));
        assertThrows(CoreException.class, () -> new SandboxCleanUpCommands().preview(request, cleanup, monitor));
        assertTrue(released.get());
        assertEquals(request.documents().getFirst().source(), first.getSource());
    }

    @Test
    void expandedScopeNeedsEditorSnapshotsBeforeAnyFixIsCreated() throws Exception {
        var first = unit("First", "class First {}");
        var second = unit("Second", "class Second {}");
        class ExpandingCleanup extends AbstractCleanUp implements IMultiFileCleanUpScopeProvider {
            @Override public java.util.Collection<ICompilationUnit> expandCleanUpScope(IJavaProject p,
                    java.util.Collection<ICompilationUnit> units, IProgressMonitor m) { return List.of(second); }
            @Override public ICleanUpFix createFix(CleanUpContext context) {
                fail("No partial preview before all affected documents are versioned");
                return null;
            }
        }
        var result = new SandboxCleanUpCommands().preview(new Request(ENCODING, OPTIONS, List.of(snapshot(first))),
                new ExpandingCleanup(), monitor);
        assertEquals(List.of(snapshot(second).uri()), result.requiredUris());
        assertTrue(result.edit().getDocumentChanges().isEmpty());
    }

    @Test
    void invalidAndUnknownCommandsFailExplicitly() {
        var commands = new SandboxCleanUpCommands();
        assertThrows(IllegalArgumentException.class, () -> commands.executeCommand("unknown", List.of(), monitor));
        assertThrows(IllegalArgumentException.class, () -> commands.executeCommand(SandboxCleanUpCommands.PREVIEW, List.of(), monitor));
        assertThrows(IllegalArgumentException.class, () -> new SandboxCleanUpCatalog().create("not-installed", Map.of()));
    }

    private static String apply(String source, List<?> edits) throws Exception {
        var document = new Document(source);
        record Replacement(int offset, int length, String text) {}
        var replacements = new ArrayList<Replacement>();
        for (Object value : edits) {
            TextEdit edit = value instanceof TextEdit direct ? direct : (TextEdit) ((Either<?, ?>) value).getLeft();
            var range = edit.getRange();
            int start = document.getLineOffset(range.getStart().getLine()) + range.getStart().getCharacter();
            int end = document.getLineOffset(range.getEnd().getLine()) + range.getEnd().getCharacter();
            replacements.add(new Replacement(start, end - start, edit.getNewText()));
        }
        replacements.sort(Comparator.comparingInt(Replacement::offset).reversed());
        for (var replacement : replacements) document.replace(replacement.offset(), replacement.length(), replacement.text());
        return document.get();
    }
}
