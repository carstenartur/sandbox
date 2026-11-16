/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions.performAndCheck;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.launching.JavaRuntime;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.JavaStructuralRewritePlan;
import org.sandbox.jdt.internal.corext.fix.math.JavaStructuralRewritePlan.Invocation;
import org.sandbox.jdt.internal.corext.fix.math.JavaStructuralRewritePlan.Method;

class MathematicalStructuralChangeTest {
    @Test void previewsAppliesAndUndoesTheCompleteVerifiedSourceGroup() throws Exception {
        var monitor = new NullProgressMonitor();
        var project = ResourcesPlugin.getWorkspace().getRoot().getProject("StructuralMath-" + UUID.randomUUID());
        try {
            project.create(monitor);
            project.open(monitor);
            var description = project.getDescription();
            description.setNatureIds(new String[] { JavaCore.NATURE_ID });
            project.setDescription(description, monitor);
            var src = project.getFolder("src");
            src.create(true, true, monitor);
            var bin = project.getFolder("bin");
            bin.create(true, true, monitor);
            var java = JavaCore.create(project);
            java.setRawClasspath(new IClasspathEntry[] {
                JavaCore.newSourceEntry(src.getFullPath()), JavaRuntime.getDefaultJREContainerEntry()
            }, bin.getFullPath(), monitor);
            var compiler = java.getOptions(false);
            JavaCore.setComplianceOptions("17", compiler);
            java.setOptions(compiler);
            var pkg = java.getPackageFragmentRoot(src).createPackageFragment("example", true, monitor);

            String valuePath = "example/Value.java";
            String callerPath = "example/Caller.java";
            String valueBefore = """
                package example;
                class Value {
                    final int coordinate;
                    Value(int input) { this.coordinate = input / 2; }
                }
                """;
            String valueAfter = valueBefore.replace("Value(int input)", "Value(int input, int scale)")
                .replace("input / 2", "input / scale");
            String callerBefore = "package example; public class Caller { public void show() { new Value(4); } }\n";
            String callerAfter = callerBefore.replace("Value(4)", "Value(4, 2)");
            var value = pkg.createCompilationUnit("Value.java", valueBefore, true, monitor);
            var caller = pkg.createCompilationUnit("Caller.java", callerBefore, true, monitor);
            var replacement = Map.of(valuePath, valueAfter, callerPath, callerAfter);
            var source = Map.of(valuePath, valueBefore, callerPath, callerBefore);
            var units = Map.of(valuePath, value, callerPath, caller);
            var show = new Method("example.Caller", "show", List.of(), "void");
            var original = new Method("example.Value", "<init>", List.of("int"), "void");
            var carried = new Method("example.Value", "<init>", List.of("int", "int"), "void");
            var calls = List.of(new Invocation(show, original, 1, 0), new Invocation(show, carried, 0, 1));
            var evidence = JavaStructuralRewritePlan.verify(source, replacement, List.of(), 17, calls, monitor);
            var change = MathematicalStructuralChange.preview("Change constructor and caller", units, evidence, monitor);
            change.initializeValidationData(monitor);
            assertFalse(change.isValid(monitor).hasFatalError());
            var children = change.getChildren();
            assertEquals(2, children.length);
            for (var child : children) {
                var preview = (CompilationUnitChange) child;
                assertEquals("Value.java".equals(preview.getName()) ? valueAfter : callerAfter,
                    preview.getPreviewContent(monitor));
            }

            children[0].setEnabled(false);
            assertTrue(change.isValid(monitor).hasFatalError());
            assertThrows(CoreException.class, () -> change.perform(monitor));
            assertEquals(source, Map.of(valuePath, value.getSource(), callerPath, caller.getSource()));
            children[0].setEnabled(true);
            var undo = performAndCheck(change, monitor, value, caller);
            assertNotNull(undo);
            assertEquals(replacement, Map.of(valuePath, value.getSource(), callerPath, caller.getSource()));
            undo.perform(monitor);
            assertEquals(source, Map.of(valuePath, value.getSource(), callerPath, caller.getSource()));
        } finally {
            if (project.exists()) project.delete(true, true, monitor);
        }
    }
}
