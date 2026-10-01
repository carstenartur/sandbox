/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.launching.JavaRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A manual migration reminder is documentation, not a new executable member. */
@SuppressWarnings("nls")
class BestEffortMarkerDocumentationTest {
    private static final AtomicInteger IDS= new AtomicInteger();
    private IProject project;
    private IJavaProject javaProject;

    @BeforeEach
    void createProject() throws Exception {
        project= ResourcesPlugin.getWorkspace().getRoot().getProject("BestEffortMarker" + IDS.incrementAndGet());
        project.create(null);
        project.open(null);
        var description= project.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        project.setDescription(description, null);
        javaProject= JavaCore.create(project);
        var src= project.getFolder("src");
        src.create(true, true, null);
        javaProject.setRawClasspath(new org.eclipse.jdt.core.IClasspathEntry[] {
                JavaCore.newSourceEntry(src.getFullPath()),
                JavaCore.newContainerEntry(JavaRuntime.newDefaultJREContainerPath()) },
                project.getFullPath().append("bin"), null);
        var options= javaProject.getOptions(true);
        JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
        options.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, JavaCore.ENABLED);
        options.put(JavaCore.COMPILER_PB_UNUSED_PRIVATE_MEMBER, JavaCore.ERROR);
        javaProject.setOptions(options);
    }

    @AfterEach
    void deleteProject() throws Exception {
        if (project != null && project.exists()) project.delete(true, true, null);
    }

    @Test
    void reminderDoesNotAddAnUnusedPrivateMethod() throws Exception {
        check("package probe; public class Example { public int answer() { return 42; } }",
                "Example", "candidate", 1, false);
    }

    @Test
    void emptyTypesNeedNoArtificialMembers() throws Exception {
        check("package probe; public class Example {}", "Example", "candidate", 0, false);
    }

    @Test
    void existingJavadocAndTagsSurvive() throws Exception {
        check("package probe; /** Existing documentation.\n * @since 1.0\n */\npublic class Example {}",
                "Example", "candidate", 0, true);
    }

    @Test
    void reminderRemainsOnTheSelectedNestedType() throws Exception {
        check("package probe; public class Example { public static class Nested {} }",
                "Nested", "nested-candidate", 0, false);
    }

    @Test
    void distinctCandidatePrefixesDoNotHideLaterGaps() throws Exception {
        ICompilationUnit unit= unit("package probe; public class Example {}");
        var root= parse(unit);
        var type= type(root, "Example");
        var longer= gap(unit, type, "candidate-long");
        perform(root, List.of(longer));
        root= parse(unit);
        var shorter= gap(unit, type(root, "Example"), "candidate");
        assertFalse(operations(root, List.of(shorter)).isEmpty(), "Candidate prefix is not marker identity");
    }

    @Test
    void existingLegacyMethodMarkersAreNotDuplicated() throws Exception {
        ICompilationUnit unit= unit("""
                package probe;
                public class Example {
                    /** @todo Sandbox JUnit migration gap candidate (UNSUPPORTED): Existing reminder. */
                    private static void sandboxJUnitMigrationTodoUnsupported() {
                        throw new UnsupportedOperationException();
                    }
                }
                """);
        var root= parse(unit);
        assertTrue(operations(root, List.of(gap(unit, type(root, "Example"), "candidate"))).isEmpty());
    }

    private void check(String before, String target, String candidate, int methods, boolean existingDocs) throws Exception {
        ICompilationUnit unit= unit(before);
        CompilationUnit root= parse(unit);
        assertNoProblems(root);
        var gap= gap(unit, type(root, target), candidate);
        var fix= new CompilationUnitRewriteOperationsFixCore("Document migration gap", root,
                operations(root, List.of(gap)).toArray(CompilationUnitRewriteOperationWithSourceRange[]::new));
        var change= fix.createChange(null);
        try {
            String preview= change.getPreviewContent(null);
            assertEquals(preview, change.getPreviewContent(null));
            assertEquals(before, unit.getSource());
            var undo= change.perform(null);
            try {
                assertEquals(preview, unit.getSource());
                root= parse(unit);
                assertNoProblems(root);
                var changed= type(root, target);
                assertEquals(methods, changed.getMethods().length, unit.getSource());
                assertNotNull(changed.getJavadoc(), unit.getSource());
                String doc= changed.getJavadoc().toString();
                assertTrue(doc.contains("Sandbox JUnit migration gap " + candidate + " ("), doc);
                assertTrue(doc.contains("Complete this construct manually"), doc);
                assertTrue(doc.contains("sandboxJUnitMigrationTodoUnsupported"), doc);
                assertTrue(doc.contains("Manual JUnit migration required: UNSUPPORTED"), doc);
                if (existingDocs) {
                    assertTrue(doc.contains("Existing documentation."), doc);
                    assertTrue(doc.replaceAll("\\s+", " ").contains("@since 1.0"), doc);
                }
                if (!"Example".equals(target)) assertNull(type(root, "Example").getJavadoc());
                assertTrue(operations(root, List.of(gap)).isEmpty(), "Second application must be a no-op");
            } finally {
                assertNotNull(undo);
                var redo= undo.perform(null);
                if (redo != null) redo.dispose();
                undo.dispose();
            }
            assertEquals(before, unit.getSource(), "Undo must restore exact original bytes");
            assertNoProblems(parse(unit));
        } finally { change.dispose(); }
    }

    private static LinkedHashSet<CompilationUnitRewriteOperationWithSourceRange> operations(
            CompilationUnit root, List<JUnitBestEffortSupport.Gap> gaps) {
        var operations= new LinkedHashSet<CompilationUnitRewriteOperationWithSourceRange>();
        JUnitBestEffortSupport.addMarkerOperation(root, gaps, operations);
        return operations;
    }

    private static void perform(CompilationUnit root, List<JUnitBestEffortSupport.Gap> gaps)
            throws Exception {
        var change= new CompilationUnitRewriteOperationsFixCore("Document migration gap", root,
                operations(root, gaps).toArray(CompilationUnitRewriteOperationWithSourceRange[]::new)).createChange(null);
        try {
            var undo= change.perform(null);
            if (undo != null) undo.dispose();
        } finally { change.dispose(); }
    }

    private ICompilationUnit unit(String source) throws Exception {
        return javaProject.getPackageFragmentRoot(project.getFolder("src"))
                .createPackageFragment("probe", false, null)
                .createCompilationUnit("Example.java", source, false, null);
    }

    private static JUnitBestEffortSupport.Gap gap(ICompilationUnit unit, TypeDeclaration type, String candidate) {
        return new JUnitBestEffortSupport.Gap(unit.getHandleIdentifier(), type.resolveBinding().getKey(),
                type.resolveBinding().getQualifiedName(), type.getStartPosition(), candidate, "UNSUPPORTED",
                "The original construct remains unchanged.", "Complete this construct manually.");
    }

    private static CompilationUnit parse(ICompilationUnit unit) {
        ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(unit);
        parser.setResolveBindings(true);
        return (CompilationUnit) parser.createAST(null);
    }

    private static TypeDeclaration type(CompilationUnit root, String name) {
        TypeDeclaration[] result= new TypeDeclaration[1];
        root.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
            @Override public boolean visit(TypeDeclaration node) {
                if (name.equals(node.getName().getIdentifier())) result[0]= node;
                return true;
            }
        });
        assertNotNull(result[0], name);
        return result[0];
    }

    private static void assertNoProblems(CompilationUnit root) {
        assertEquals(0, root.getProblems().length, Arrays.toString(root.getProblems()));
    }
}
