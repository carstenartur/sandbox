/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import javax.tools.ToolProvider;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.IVMInstall;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;

/** Complete bound units reduced from the pinned SWT and JDT Core corpus failures. */
@SuppressWarnings("nls")
class UpstreamLoopCompilationTest {
    @TempDir
    Path temporary;

    private IProject resource;
    private IJavaProject project;
    private boolean autoBuilding;
    private IVMInstall originalVm;
    private IVMInstall createdVm;

    @BeforeEach
    void createProject() throws Exception {
        // ImportRewrite needs the ordinary JDT preference initializer even in a headless launch.
        JavaPlugin.getDefault().getPreferenceStore();
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

    @Test
    void keepsAccumulatorObservedBeforeItsUpdate() throws Exception {
        check("""
                public static void run(Iterable<String> values) {
                    int count = 0;
                    for (String value : values) {
                        if (value == null) throw new NullPointerException(Integer.toString(count));
                        count++;
                    }
                }
                """, false);
    }

    @Test
    void preservesSupportedStandaloneAccumulator() throws Exception {
        String methods = """
                public static int run(java.util.List<String> values) {
                    int count = 0;
                    for (String value : values) { if (value != null) count++; }
                    return count;
                }
                """;
        String generated = check(methods, true);
        assertExecution(methods, generated, 2, java.util.List.class, Arrays.asList("first", null, "last"));
    }

    @Test
    void preservesMinMaxAccumulatorInEitherArgumentPosition() throws Exception {
        String methods = """
                public static int run(int[] values) {
                    int maximum = Integer.MIN_VALUE;
                    for (int value : values) maximum = Math.max(value, maximum);
                    int minimum = Integer.MAX_VALUE;
                    for (int value : values) minimum = Math.min(minimum, value);
                    return maximum - minimum;
                }
                """;
        String generated = check(methods, true);
        assertTrue(generated.contains("Math::max"), generated);
        assertTrue(generated.contains("Math::min"), generated);
        assertExecution(methods, generated, 14, int[].class, new int[] { -5, 0, 9 });
    }

    @Test
    void keepsAccumulatorReadInsideMappedValue() throws Exception {
        check("""
                public static int run(int[] values) {
                    int sum = 1;
                    for (int value : values) sum += sum * value;
                    return sum;
                }
                """, false);
    }

    @Test
    void retainsShadowedMathReducerArgumentOrder() throws Exception {
        check("""
                static class Math { static int max(int first, int second) { return first - second; } }
                public static int run(int[] values) {
                    int maximum = 0;
                    for (int value : values) maximum = Math.max(value, maximum);
                    return maximum;
                }
                """, false);
    }

    @Test
    void keepsAdditionalInfixAccumulatorOperands() throws Exception {
        String methods = """
                public static int run(int[] values) {
                    int sum = 10;
                    for (int value : values) sum = sum + value + 1;
                    return sum;
                }
                """;
        String generated = check(methods, false);
        assertExecution(methods, generated, 22, int[].class, new int[] { 4, -2, 7 });
    }

    @Test
    void preservesPostfixDecrementResult() throws Exception {
        String methods = """
                public static int run(int[] values) {
                    int count = 10;
                    for (int value : values) count--;
                    return count;
                }
                """;
        String generated = check(methods, true);
        assertExecution(methods, generated, 7, int[].class, new int[] { 4, -2, 7 });
    }

    @Test
    void parenthesizesCastBeforeIteratorInvocation() throws Exception {
        checkSource("""
                package probe;
                public class Sample {
                    public static void run(Object values) {
                        for (Object value : (java.util.Collection<?>) values) System.out.println(value);
                    }
                }
                """, LoopTargetFormat.WHILE_LOOP, true);
    }

    @Test
    void removesErasedLoopVariableTypeImport() throws Exception {
        String generated = checkSource("""
                package probe;
                import java.util.Map.Entry;
                public class Sample {
                    public static void run(java.util.Map<String, String> values) {
                        for (Entry<String, String> entry : values.entrySet()) System.out.println(entry);
                    }
                }
                """, LoopTargetFormat.STREAM, true);
        assertFalse(generated.contains("import java.util.Map.Entry;"), generated);
    }

    @Test
    void removesIteratorImportDeclaredInClassicForHeader() throws Exception {
        String generated = checkSource("""
                package probe;
                import java.util.Iterator;
                public class Sample {
                    public static void run(java.util.Set<?> values) {
                        for (final Iterator<?> iterator = values.iterator(); iterator.hasNext();) {
                            Object value = iterator.next();
                            System.out.println(value);
                        }
                    }
                }
                """, LoopTargetFormat.FOR_LOOP, true);
        assertFalse(generated.contains("import java.util.Iterator;"), generated);
    }

    @Test
    void retainsImportUsedOutsideConvertedLoop() throws Exception {
        String generated = checkSource("""
                package probe;
                import java.util.Map.Entry;
                public class Sample {
                    public static void run(java.util.Map<String, String> values, Entry<String, String> retained) {
                        for (Entry<String, String> entry : values.entrySet()) System.out.println(entry);
                        System.out.println(retained);
                    }
                }
                """, LoopTargetFormat.STREAM, true);
        assertTrue(generated.contains("import java.util.Map.Entry;"), generated);
    }

    @Test
    void keepsTaggedStringOnItsOriginalLineWhenLoweringStream() throws Exception {
        checkSource("""
                package probe;
                public class Sample {
                    public static void run(java.util.List<String> values) {
                        values.forEach(value -> System.out.println("value: " + value)); //$NON-NLS-1$
                    }
                }
                """, LoopTargetFormat.FOR_LOOP, false);
    }

    @Test
    void keepsTaggedStringOnItsOriginalLineWhenRaisingLoop() throws Exception {
        check("""
                public static void run(java.util.List<String> values) {
                    for (String value : values) {
                        System.out.println("value: " + value); //$NON-NLS-1$
                    }
                }
                """, false);
    }

    @Test
    void doesNotExposePrivateInheritedIteratorTypeAnnotation() throws Exception {
        checkSource("""
                package probe;
                class Source {
                    @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE)
                    private @interface Hidden { }
                    abstract static class Values implements Iterable<@Hidden String> { }
                }
                public class Sample {
                    public static void run(Source.Values values) {
                        for (String value : values) System.out.println(value);
                    }
                }
                """, LoopTargetFormat.WHILE_LOOP, false);
    }

    @Test
    void handlesUnbracedIndexLoopWithTrailingComment() throws Exception {
        check("""
                public static void run(String[] values) {
                    for (int i = 0; i < values.length; i++)
                        System.out.println(values[i]); // preserve this comment
                }
                """, true);
    }

    @Test
    void handlesUnbracedConditionalBodyWithTrailingComment() throws Exception {
        String generated = check("""
                public static void run(String[] values) {
                    for (int i = 0; i < values.length; i++)
                        if (values[i] != null) System.out.println(values[i]); // preserve this comment
                }
                """, true);
        assertTrue(generated.contains("preserve this comment"), generated);
    }

    @Test
    void keepsBorrowedCloseablesAsEnhancedForVariables() throws Exception {
        checkSource("""
                package probe;
                public class Sample {
                    public static void run(java.util.List<java.util.jar.JarFile> files) {
                        for (java.util.jar.JarFile file : files) System.out.println(file.getName());
                    }
                }
                """, LoopTargetFormat.WHILE_LOOP, false);
    }

    @Test
    void keepsResourceClosingLoopDiagnosticContract() throws Exception {
        checkSource("""
                package probe;
                public class Sample {
                    public static void run(java.util.List<java.net.Socket> sockets) {
                        for (java.net.Socket socket : sockets) {
                            try { socket.close(); }
                            catch (java.io.IOException exception) { System.err.println(exception); }
                        }
                    }
                }
                """, LoopTargetFormat.WHILE_LOOP, false);
    }

    @Test
    void preservesCommentBeforeIteratorLowering() throws Exception {
        String generated = checkSource("""
                package probe;
                public class Sample {
                    public static void run(java.util.List<String> values) {
                        // Preserve the reason for processing these values.
                        for (String value : values) System.out.println(value);
                    }
                }
                """, LoopTargetFormat.WHILE_LOOP, true);
        assertTrue(generated.contains("Preserve the reason for processing these values."), generated);
    }

    @Test
    void keepsRawCollectionPipelineElementTypes() throws Exception {
        check("""
                @SuppressWarnings("rawtypes")
                public static void run(java.util.List values) {
                    for (Object value : values) {
                        String text = (String) value;
                        System.out.println(text.trim());
                    }
                }
                """, false);
    }

    @Test
    void keepsRawIterableWithoutUncheckedConsumerWarning() throws Exception {
        check("""
                @SuppressWarnings("rawtypes")
                public static void run(Iterable values) {
                    for (Object value : values) System.out.println(value);
                }
                """, false);
    }

    @Test
    void keepsTypedIteratorOverRawClassicForSource() throws Exception {
        checkSource("""
                package probe;
                public class Sample {
                    @SuppressWarnings("rawtypes")
                    public static void run(java.util.List values) {
                        for (java.util.Iterator<String> iterator = values.iterator(); iterator.hasNext();) {
                            String value = iterator.next();
                            System.out.println(value);
                        }
                    }
                }
                """, LoopTargetFormat.FOR_LOOP, false);
    }

    @Test
    void keepsTypedIteratorOverRawWhileSource() throws Exception {
        checkSource("""
                package probe;
                public class Sample {
                    @SuppressWarnings("rawtypes")
                    public static void run(java.util.List values) {
                        java.util.Iterator<String> iterator = values.iterator();
                        while (iterator.hasNext()) {
                            String value = iterator.next();
                            System.out.println(value);
                        }
                    }
                }
                """, LoopTargetFormat.FOR_LOOP, false);
    }

    @Test
    void doesNotRenderStringConcatenationAsNumericSum() throws Exception {
        String methods = """
                public static String run(String[] values) {
                    String result = "";
                    for (String value : values) result += value;
                    return result;
                }
                """;
        String generated = check(methods, false);
        assertExecution(methods, generated, "firstnulllast", String[].class, new String[] { "first", null, "last" });
    }

    @Test
    void retainsCastPrecedenceForTerminalMatch() throws Exception {
        String methods = """
                public static boolean run(Object values) {
                    for (Object value : (java.util.List<?>) values) {
                        if (value == null) return true;
                    }
                    return false;
                }
                """;
        String generated = check(methods, true);
        assertExecution(methods, generated, true, Object.class, Arrays.asList("first", null));
    }

    @Test
    void retainsSideEffectsBeforeMappedConditionalBody() throws Exception {
        String methods = """
                public static String run(String[] values) {
                    StringBuilder result = new StringBuilder();
                    for (String value : values) {
                        result.append(value);
                        String trace = value.trim();
                        if (!trace.isEmpty()) {
                            trace = trace.replace('a', 'b');
                            result.append(trace);
                        }
                    }
                    return result.toString();
                }
                """;
        String generated = check(methods, true);
        assertExecution(methods, generated, " a bcc", String[].class, new String[] { " a ", "c" });
    }

    private String check(String methods, boolean expectedChange) throws Exception {
        String original = "package probe; public class Sample {\n" + methods + "}\n";
        return checkSource(original, LoopTargetFormat.STREAM, expectedChange);
    }

    private void assertExecution(String methods, String generated, Object expected,
            Class<?> parameterType, Object argument) throws Exception {
        String original = "package probe; public class Sample {\n" + methods + "}\n";
        assertEquals(expected, execute(original, "original", parameterType, argument), "Original fixture result");
        assertEquals(expected, execute(generated, "generated", parameterType, argument), generated);
    }

    private Object execute(String source, String directory, Class<?> parameterType, Object argument) throws Exception {
        Path output = Files.createDirectories(temporary.resolve(directory));
        Path file = output.resolve("Sample.java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "The runtime equivalence tests require a full JDK");
        var diagnostics = new ByteArrayOutputStream();
        assertEquals(0, compiler.run(null, diagnostics, diagnostics, "--release", "21", "-encoding", "UTF-8",
                "-proc:none", "-d", output.toString(), file.toString()), diagnostics.toString(StandardCharsets.UTF_8));
        try (var loader = new URLClassLoader(new URL[] { output.toUri().toURL() }, ClassLoader.getPlatformClassLoader())) {
            return loader.loadClass("probe.Sample").getMethod("run", parameterType).invoke(null, argument);
        }
    }

    private String checkSource(String original, LoopTargetFormat target, boolean expectedChange) throws Exception {
        ICompilationUnit unit = project.getPackageFragmentRoot(resource.getFolder("src"))
                .createPackageFragment("probe", false, null).createCompilationUnit("Sample.java", original, false, null);
        var diagnostics = CompilerDiagnosticAssertions.capture(unit);
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(unit);
        parser.setResolveBindings(true);
        var root = (CompilationUnit) parser.createAST(null);
        assertFalse(Arrays.stream(root.getProblems()).anyMatch(problem -> problem.isError()),
                Arrays.toString(root.getProblems()));
        var fix = LoopConversionService.analyze(root, LoopConversionService.handlers(target))
                .createFix("Upstream compilation regression");
        if (!expectedChange && fix == null) {
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
            assertTrue(expectedChange, "Keep unsupported semantics and source contracts unchanged");
            return generated;
        } finally {
            undo.perform(null);
            diagnostics.assertRestored();
        }
    }
}
