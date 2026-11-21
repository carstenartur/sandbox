/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Arrays;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.IVMInstall;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;

/** Complete bound units reduced from the pinned SWT corpus failures. */
@SuppressWarnings("nls")
class UpstreamLoopCompilationTest {
    private IProject resource;
    private IJavaProject project;
    private boolean autoBuilding;
    private IVMInstall originalVm;
    private IVMInstall createdVm;

    @BeforeEach
    void createProject() throws Exception {
        var workspace = ResourcesPlugin.getWorkspace();
        var workspaceDescription = workspace.getDescription();
        autoBuilding = workspaceDescription.isAutoBuilding();
        workspaceDescription.setAutoBuilding(false);
        workspace.setDescription(workspaceDescription);
        originalVm = JavaRuntime.getDefaultVMInstall();
        if (originalVm == null) {
            var type = JavaRuntime.getVMInstallTypes()[0];
            var vm = type.createVMInstall("upstream-loop-regression");
            vm.setName("Upstream loop regression");
            vm.setInstallLocation(Path.of(System.getProperty("java.home")).toFile());
            createdVm = vm;
            JavaRuntime.setDefaultVMInstall(vm, null);
        }
        resource = workspace.getRoot().getProject("UpstreamLoopCompilation");
        resource.create(null);
        resource.open(null);
        var description = resource.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        resource.setDescription(description, null);
        resource.getFolder("src").create(true, true, null);
        project = JavaCore.create(resource);
        project.setRawClasspath(new org.eclipse.jdt.core.IClasspathEntry[] {
                JavaCore.newSourceEntry(resource.getFullPath().append("src")),
                JavaCore.newContainerEntry(JavaRuntime.newDefaultJREContainerPath()) },
                resource.getFullPath().append("bin"), null);
        var options = project.getOptions(false);
        JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
        options.put(JavaCore.COMPILER_RELEASE, JavaCore.ENABLED);
        project.setOptions(options);
    }

    @AfterEach
    void deleteProject() throws Exception {
        try {
            if (resource != null && resource.exists()) resource.delete(true, true, null);
        } finally {
            if (createdVm != null) {
                JavaRuntime.setDefaultVMInstall(originalVm, null);
                createdVm.getVMInstallType().disposeVMInstall(createdVm.getId());
            }
            var workspace = ResourcesPlugin.getWorkspace();
            var description = workspace.getDescription();
            description.setAutoBuilding(autoBuilding);
            workspace.setDescription(description);
        }
    }

    @Test
    void rendersSingleIfElseStatementInBlockLambda() throws Exception {
        check("""
                public static void run(int[] values, boolean positive) {
                    for (int value : values) {
                        if (positive) System.out.println(value);
                        else System.out.println(-value);
                    }
                }
                """, true);
    }

    @Test
    void parenthesizesCastMethodReferenceReceiver() throws Exception {
        check("""
                public static void run(java.util.List<String> values) {
                    for (String value : (Iterable<String>) values::iterator) {
                        System.out.println(value);
                    }
                }
                """, true);
    }

    @Test
    void retainsEarlierPipelineVariableInLaterStatements() throws Exception {
        check("""
                public static void run(java.util.List<String> values) {
                    for (String value : values) {
                        String trimmed = value.trim();
                        System.out.println(value + trimmed);
                    }
                }
                """, true);
    }

    @Test
    void leavesCheckedInvocationInEnclosingThrowsScope() throws Exception {
        check("""
                public static void run(java.util.List<java.nio.file.Path> values) throws java.io.IOException {
                    for (java.nio.file.Path value : values) java.nio.file.Files.size(value);
                }
                """, false);
    }

    @Test
    void leavesCheckedConstructorInEnclosingThrowsScope() throws Exception {
        check("""
                public static void run(java.util.List<java.io.File> values) throws java.io.IOException {
                    for (java.io.File value : values) new java.io.FileInputStream(value).close();
                }
                """, false);
    }

    @Test
    void keepsContinueInsideComplexFallbackLoop() throws Exception {
        check("""
                public static void run(java.util.List<String> values) {
                    for (String value : values) {
                        int length = value.length();
                        int doubled = length * 2;
                        if (doubled < 1) continue;
                        System.out.println(value + length);
                    }
                }
                """, false);
    }

    @Test
    void keepsOuterReturnInsideNestedEnhancedLoops() throws Exception {
        check("""
                public static Object run(Object[][] values) {
                    for (Object[] row : values) {
                        for (Object value : row) {
                            if (value != null) return value;
                        }
                    }
                    return null;
                }
                """, false);
    }

    @Test
    void keepsEarlyReturnWhenFallbackCannotRepresentIt() throws Exception {
        check("""
                public static boolean run(java.util.List<String> values) {
                    for (String value : values) {
                        int length = value.length();
                        int doubled = length * 2;
                        if (doubled > 0) return true;
                        System.out.println(value + length);
                    }
                    return false;
                }
                """, false);
    }

    @Test
    void keepsNonterminalBooleanReturnInEnclosingMethod() throws Exception {
        check("""
                public static boolean run(java.util.List<String> values) {
                    for (String value : values) {
                        if (value.isEmpty()) return true;
                        System.out.println(value);
                    }
                    return false;
                }
                """, false);
    }

    @Test
    void keepsBooleanReturnInsideIfElse() throws Exception {
        check("""
                public static boolean run(java.util.List<String> values) {
                    for (String value : values) {
                        if (value.isEmpty()) return true;
                        else System.out.println(value);
                    }
                    return false;
                }
                """, false);
    }

    @Test
    void retainsSupportedBooleanMatchTerminal() throws Exception {
        String generated = check("""
                public static boolean run(java.util.List<String> values) {
                    for (String value : values) {
                        if (value.isEmpty()) return true;
                    }
                    return false;
                }
                """, true);
        assertTrue(generated.contains(".anyMatch("), generated);
    }

    @Test
    void retainsSupportedLinearMapAndSimpleForEach() throws Exception {
        String generated = check("""
                public static void run(java.util.List<String> values, int[] numbers) {
                    for (String value : values) {
                        String trimmed = value.trim();
                        System.out.println(trimmed);
                    }
                    for (int number : numbers) System.out.println(number);
                }
                """, true);
        assertTrue(generated.contains(".map("), generated);
    }

    private String check(String methods, boolean expectedChange) throws Exception {
        String original = "package probe; public class Sample {\n" + methods + "}\n";
        ICompilationUnit unit = project.getPackageFragmentRoot(resource.getFolder("src"))
                .createPackageFragment("probe", false, null).createCompilationUnit("Sample.java", original, false, null);
        var diagnostics = CompilerDiagnosticAssertions.capture(unit);
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(unit);
        parser.setResolveBindings(true);
        var root = (CompilationUnit) parser.createAST(null);
        assertFalse(Arrays.stream(root.getProblems()).anyMatch(problem -> problem.isError()),
                Arrays.toString(root.getProblems()));
        var fix = LoopConversionService.analyze(root, LoopConversionService.handlers(LoopTargetFormat.STREAM))
                .createFix("Upstream compilation regression");
        if (!expectedChange) {
            assertNull(fix, "Do not move unsupported control or exception flow into a lambda");
            assertEquals(original, unit.getSource());
            return original;
        }
        assertNotNull(fix, "Supported loop conversions must remain active");
        var change = fix.createChange(null);
        var undo = change.perform(null);
        try {
            String generated = unit.getSource();
            assertNotEquals(original, generated);
            diagnostics.assertNoNewDiagnostics();
            return generated;
        } finally {
            undo.perform(null);
            diagnostics.assertRestored();
        }
    }
}
