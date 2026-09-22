/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java10;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUpCore;

/** Uses the actual cleanup to exercise expression-name scope and API boundaries. */
class CharsetReferenceSafetyTest {
    @TempDir Path temporary;
    private IProject project;

    static Stream<Arguments> scopes() {
        return Stream.of(
            Arguments.of("parameter", "probe", """
                public static String run() { return value(7); }
                static String value(int Subject) { return name(Charset.forName("UTF-8")) + Subject; }
                """, true),
            Arguments.of("local", "probe", """
                public static String run() { int Subject = 7; return name(Charset.forName("UTF-8")) + Subject; }
                """, true),
            Arguments.of("lambda", "probe", """
                public static String run() {
                    java.util.function.IntFunction<String> f = Subject -> name(Charset.forName("UTF-8")) + Subject;
                    return f.apply(7);
                }
                """, true),
            Arguments.of("field", "probe", """
                static final int Subject = 7;
                public static String run() { return name(Charset.forName("UTF-8")) + Subject; }
                """, true),
            Arguments.of("type-parameter", "probe", """
                public static <Subject> String run() { return name(Charset.forName("UTF-8")); }
                """, true),
            Arguments.of("cache-first-unshadowed", "probe", """
                static String first() { return name(Charset.forName("UTF-8")); }
                static String second(int Subject) { return name(Charset.forName("UTF-8")) + Subject; }
                public static String run() { return first() + second(7); }
                """, true),
            Arguments.of("cache-first-shadowed", "probe", """
                static String first(int Subject) { return name(Charset.forName("UTF-8")) + Subject; }
                static String second() { return name(Charset.forName("UTF-8")); }
                public static String run() { return first(7) + second(); }
                """, true),
            Arguments.of("default-package", "", """
                public static String run() { int Subject = 7; return name(Charset.forName("UTF-8")) + Subject; }
                """, false),
            Arguments.of("package-also-shadowed", "probe", """
                public static String run() { int Subject = 7, probe = 2;
                    return name(Charset.forName("UTF-8")) + Subject + probe;
                }
                """, false),
            Arguments.of("nested-owner", "probe", """
                static class Nested {
                    static String value(int Nested) { return name(Charset.forName("UTF-8")) + Nested; }
                }
                public static String run() { return Nested.value(7); }
                """, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scopes")
    void preservesBindingsAtEveryOccurrence(String label, String packageName, String members,
            boolean aggregate) throws Exception {
        String before = (packageName.isEmpty() ? "" : "package " + packageName + ";\n")
                + "import java.nio.charset.Charset;\npublic class Subject {\n"
                + "static String name(Charset c) { return c.name(); }\n" + members + "}\n";
        ICompilationUnit unit = unit(before, packageName, JavaCore.VERSION_10);
        String after = applyAndUndo(unit, MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8, true);
        assertFalse(after.contains("Charset.forName("), after);
        assertEquals(aggregate, after.contains("private static final Charset UTF_8"), after);
        String className = packageName.isEmpty() ? "Subject" : packageName + ".Subject";
        assertEquals(execute(before, className, "before"), execute(after, className, "after"));
    }

    static Stream<Arguments> versions() {
        return Stream.of(MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR,
                MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8,
                MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8)
                .flatMap(mode -> Stream.of(Arguments.of(JavaCore.VERSION_1_6, mode, false),
                        Arguments.of(JavaCore.VERSION_1_7, mode, true)));
    }

    @ParameterizedTest(name = "source={0}, mode={1}")
    @MethodSource("versions")
    void requiresJava7InProductionDiscovery(String version, String mode, boolean changed) throws Exception {
        String before = "package probe; import java.nio.charset.Charset; public class Subject {"
                + "public static Charset value() { return Charset.forName(\"UTF-8\"); }}";
        // Current JDT parses Java 8 and newer. Build the bound, Java-6-compatible
        // input at 8, then exercise the real discovery against each legacy project level.
        ICompilationUnit unit = unit(before, "probe", JavaCore.VERSION_1_8);
        CompilationUnit root = parse(unit);
        var javaProject = unit.getJavaProject();
        javaProject.setOption(JavaCore.COMPILER_COMPLIANCE, version);
        javaProject.setOption(JavaCore.COMPILER_SOURCE, version);
        javaProject.setOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, version);
        assertEquals(version, javaProject.getOption(JavaCore.COMPILER_COMPLIANCE, true));
        assertEquals(version, javaProject.getOption(JavaCore.COMPILER_SOURCE, true));
        var operations = new java.util.LinkedHashSet<org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation>();
        var behavior = mode.equals(MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR)
                ? org.sandbox.jdt.internal.corext.fix.helper.ChangeBehavior.KEEP_BEHAVIOR
                : mode.equals(MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8)
                        ? org.sandbox.jdt.internal.corext.fix.helper.ChangeBehavior.ENFORCE_UTF8
                        : org.sandbox.jdt.internal.corext.fix.helper.ChangeBehavior.ENFORCE_UTF8_AGGREGATE;
        org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore.CHARSET.findOperations(
                root, operations, new java.util.HashSet<>(), behavior);
        assertEquals(changed ? 1 : 0, operations.size());
        assertEquals(before, unit.getSource());
    }

    private ICompilationUnit unit(String source, String packageName, String version) throws Exception {
        project = ResourcesPlugin.getWorkspace().getRoot().getProject("CharsetReference-" + System.nanoTime());
        project.create(null);
        project.open(null);
        var description = project.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        project.setDescription(description, null);
        var javaProject = JavaCore.create(project);
        var folder = project.getFolder("src");
        folder.create(true, true, null);
        javaProject.setRawClasspath(new org.eclipse.jdt.core.IClasspathEntry[] {
            JavaCore.newSourceEntry(folder.getFullPath()),
            JavaCore.newContainerEntry(new org.eclipse.core.runtime.Path("org.eclipse.jdt.launching.JRE_CONTAINER"))
        }, project.getFullPath().append("bin"), null);
        Map<String, String> options = new HashMap<>(javaProject.getOptions(true));
        JavaCore.setComplianceOptions(version, options);
        // Recent JDT no longer sets legacy levels via setComplianceOptions.
        options.put(JavaCore.COMPILER_SOURCE, version);
        options.put(JavaCore.COMPILER_COMPLIANCE, version);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, version);
        javaProject.setOptions(options);
        return javaProject.getPackageFragmentRoot(folder).createPackageFragment(packageName, false, null)
                .createCompilationUnit("Subject.java", source, false, null);
    }

    private String applyAndUndo(ICompilationUnit unit, String mode, boolean expectedChange) throws Exception {
        String before = unit.getSource();
        var baseline = diagnostics(parse(unit));
        var cleanup = new UseExplicitEncodingCleanUpCore(Map.of(
                MYCleanUpConstants.EXPLICITENCODING_CLEANUP, "true", mode, "true"));
        var fix = cleanup.createFix(new CleanUpContext(unit, parse(unit)));
        if (!expectedChange) {
            assertNull(fix);
            assertEquals(before, unit.getSource());
            return before;
        }
        assertNotNull(fix);
        var change = fix.createChange(null);
        try {
            String preview = change.getPreviewContent(null);
            assertEquals(preview, change.getPreviewContent(null));
            assertEquals(before, unit.getSource());
            var undo = change.perform(null);
            assertNotNull(undo);
            try {
                String after = unit.getSource();
                assertEquals(preview, after);
                assertNotEquals(before, after);
                diagnostics(parse(unit)).forEach((key, count) ->
                    assertTrue(count <= baseline.getOrDefault(key, 0L), key + "\n" + after));
                assertNull(cleanup.createFix(new CleanUpContext(unit, parse(unit))));
                return after;
            } finally {
                var redo = undo.perform(null);
                if (redo != null) redo.dispose();
                undo.dispose();
                assertEquals(before, unit.getSource());
            }
        } finally { change.dispose(); }
    }

    private static CompilationUnit parse(ICompilationUnit unit) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(unit);
        parser.setResolveBindings(true);
        CompilationUnit root = (CompilationUnit) parser.createAST(null);
        assertTrue(Arrays.stream(root.getProblems()).noneMatch(p -> p.isError()),
                () -> Arrays.toString(root.getProblems()));
        return root;
    }

    private static Map<String, Long> diagnostics(CompilationUnit root) {
        return Arrays.stream(root.getProblems()).filter(p -> p.isWarning() || p.isError())
                .collect(Collectors.groupingBy(p -> p.isError() + ":" + p.getID() + ":"
                        + Arrays.toString(p.getArguments()), Collectors.counting()));
    }

    private Object execute(String source, String className, String stage) throws Exception {
        Path directory = Files.createDirectories(temporary.resolve(stage));
        Path file = Files.writeString(directory.resolve("Subject.java"), source, StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, "--release", "10", "-d", directory.toString(), file.toString()));
        try (var loader = new URLClassLoader(new java.net.URL[] { directory.toUri().toURL() }, null)) {
            return loader.loadClass(className).getMethod("run").invoke(null);
        }
    }

    @AfterEach
    void deleteProject() throws Exception {
        if (project != null && project.exists()) project.delete(true, true, null);
    }
}
