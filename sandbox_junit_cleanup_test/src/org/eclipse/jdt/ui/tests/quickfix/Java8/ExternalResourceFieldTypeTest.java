/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.ui.fix.JUnitCleanUpCore;

/** A migrated resource must remain assignable to its field, not its removed base. */
public class ExternalResourceFieldTypeTest {
    @TempDir Path temporary;
    private IProject project;

    record Case(String name, boolean classRule, String parameters, String constructor,
            String fieldType, String initializer) {
        @Override public String toString() { return name; }
    }

    static Stream<Case> cases() {
        return Stream.of(
            new Case("instance", false, "", "", "ExternalResource", "new Resource()"),
            new Case("class-rule", true, "", "", "ExternalResource", "new Resource()"),
            new Case("constructor", false, "", "Resource(String value) { events += value.length(); }",
                    "ExternalResource", "new Resource(\"abc\")"),
            new Case("parameterized", false, "<T>", "", "ExternalResource", "new Resource<String>()"),
            new Case("diamond", false, "<T>", "", "ExternalResource", "new Resource<>()"),
            new Case("qualified-type", false, "", "", "org.junit.rules.ExternalResource", "new Resource()"),
            new Case("rule-interface", false, "", "", "org.junit.rules.TestRule", "new Resource()"),
            new Case("already-concrete", false, "", "", "Resource", "new Resource()"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void keepsFieldAssignableAndLifecycleIntact(Case scenario) throws Exception {
        String before = "package fixture; import org.junit.Rule; import org.junit.ClassRule; "
                + "import org.junit.rules.ExternalResource; public class Subject { public static int events; "
                + (scenario.classRule() ? "static " : "") + "class Resource" + scenario.parameters()
                + " extends ExternalResource { " + scenario.constructor()
                + " @Override protected void before() throws Throwable { events += 2; }"
                + " @Override protected void after() { events += 3; } } "
                + (scenario.classRule() ? "@ClassRule public static " : "@Rule public ")
                + scenario.fieldType() + " resource = " + scenario.initializer() + "; @org.junit.Test public void test() {} }";
        ICompilationUnit unit = createUnit(before);
        var baseline = diagnostics(parse(unit));
        var cleanup = new JUnitCleanUpCore(Map.of("cleanup.junitcleanup", "true",
                "cleanup.junitcleanup_4_ruleexternalresource", "true", "cleanup.junitcleanup_4_test", "true"));
        var status = cleanup.checkPreConditions(unit.getJavaProject(), new ICompilationUnit[] { unit }, null);
        assertFalse(status.hasError(), status.toString());
        var fix = cleanup.createFix(new CleanUpContext(unit, parse(unit)));
        assertNotNull(fix, "The named resource must be positively modernized");
        String after;
        var change = fix.createChange(null);
        try {
            String preview = change.getPreviewContent(null);
            assertEquals(preview, change.getPreviewContent(null));
            assertEquals(before, unit.getSource());
            var undo = change.perform(new org.eclipse.core.runtime.NullProgressMonitor());
            assertNotNull(undo);
            try {
                after = unit.getSource();
                assertEquals(preview, after);
                assertNotEquals(before, after);
                var result = parse(unit);
                diagnostics(result).forEach((key, count) ->
                        assertTrue(count <= baseline.getOrDefault(key, 0L), key + "\n" + after));
                var owner = (TypeDeclaration) result.types().get(0);
                var field = Arrays.stream(owner.getFields())
                        .filter(f -> ((org.eclipse.jdt.core.dom.VariableDeclarationFragment) f.fragments().get(0))
                                .getName().getIdentifier().equals("resource"))
                        .findFirst().orElseThrow();
                var variable = (org.eclipse.jdt.core.dom.VariableDeclarationFragment) field.fragments().get(0);
                assertTrue(variable.getInitializer().resolveTypeBinding()
                        .isAssignmentCompatible(field.getType().resolveBinding()), after);
                assertTrue(after.contains("@RegisterExtension"), after);
                cleanup.checkPostConditions(null);
                var nextStatus = cleanup.checkPreConditions(unit.getJavaProject(), new ICompilationUnit[] { unit }, null);
                assertFalse(nextStatus.hasError(), nextStatus.toString());
                assertNull(cleanup.createFix(new CleanUpContext(unit, result)), "Second cleanup must be a no-op");
                cleanup.checkPostConditions(null);
            } finally {
                var redo = undo.perform(new org.eclipse.core.runtime.NullProgressMonitor());
                if (redo != null) redo.dispose();
                undo.dispose();
            }
            assertEquals(before, unit.getSource());
        } finally { change.dispose(); }
        assertEquals(execute(before, false, scenario.classRule(), unit, "before"),
                execute(after, true, scenario.classRule(), unit, "after"));
    }

    @ParameterizedTest(name = "separate resource file, compatibility={0}")
    @ValueSource(booleans = { false, true })
    void respectsCompatibilityAcrossCompilationUnits(boolean compatible) throws Exception {
        String before = "package fixture; import org.junit.Rule; import org.junit.rules.ExternalResource; "
                + "public class Subject { public static int events; @Rule public ExternalResource resource = new Resource(); }";
        String resourceBefore = "package fixture; import org.junit.rules.ExternalResource; "
                + "public class Resource extends ExternalResource {"
                + "@Override protected void before() throws Throwable { Subject.events += 2; }"
                + "@Override protected void after() { Subject.events += 3; }}";
        var subject = createUnit(before);
        var resource = ((org.eclipse.jdt.core.IPackageFragment) subject.getParent())
                .createCompilationUnit("Resource.java", resourceBefore, false, null);
        var units = new ICompilationUnit[] { subject, resource };
        var baselines = new java.util.ArrayList<Map<String, Long>>();
        for (var unit : units) baselines.add(diagnostics(parse(unit)));
        var result = org.sandbox.jdt.internal.corext.fix.multifile.JUnitMultiFilePlanner
                .create(subject.getJavaProject(), units, true, null);
        assertFalse(result.status().hasError(), result.status().toString());
        var plan = result.plan();
        assertNotNull(plan);
        assertTrue(plan.hasCoordinatedChanges());
        if (compatible) {
            // Exercise the existing compatibility-plan contract, including a resource
            // whose declaration is not in the field's compilation unit.
            plan = plan.withJUnit4CompatibilityForBlockedRuleUnits(java.util.Set.of(subject.getHandleIdentifier()));
            assertFalse(plan.junit4CompatibleExternalResourceTypes().isEmpty());
        }
        var composite = new org.eclipse.ltk.core.refactoring.CompositeChange("ExternalResource field and class");
        var previews = new java.util.ArrayList<String>();
        for (var unit : units) {
            var root = parse(unit);
            var operations = new java.util.LinkedHashSet<org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange>();
            plan.addOperationsFor(unit, root, operations, new java.util.HashSet<>());
            assertFalse(operations.isEmpty());
            var fix = new org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore(
                    "ExternalResource", root,
                    operations.toArray(org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[]::new));
            var change = fix.createChange(null);
            previews.add(change.getPreviewContent(null));
            assertEquals(previews.get(previews.size() - 1), change.getPreviewContent(null));
            composite.add(change);
        }
        assertEquals(before, subject.getSource());
        assertEquals(resourceBefore, resource.getSource());
        String after;
        String resourceAfter;
        try {
            var undo = composite.perform(new org.eclipse.core.runtime.NullProgressMonitor());
            assertNotNull(undo);
            try {
                after = subject.getSource();
                resourceAfter = resource.getSource();
                for (int i = 0; i < units.length; i++) {
                    assertEquals(previews.get(i), units[i].getSource());
                    var baseline = baselines.get(i);
                    diagnostics(parse(units[i])).forEach((key, count) ->
                            assertTrue(count <= baseline.getOrDefault(key, 0L), key));
                }
                var fieldType = ((TypeDeclaration) parse(subject).types().get(0)).getFields()[1].getType().resolveBinding();
                assertEquals(compatible ? "org.junit.rules.ExternalResource" : "fixture.Resource",
                        fieldType.getQualifiedName(), after);
                assertEquals(compatible, resourceAfter.contains("extends ExternalResource"), resourceAfter);
                assertTrue(resourceAfter.contains("BeforeEachCallback"), resourceAfter);
                var second = org.sandbox.jdt.internal.corext.fix.multifile.JUnitMultiFilePlanner
                        .create(subject.getJavaProject(), units, true, null);
                assertFalse(second.status().hasError(), second.status().toString());
                assertFalse(second.plan().hasCoordinatedChanges(), "No rule fields should remain to migrate");
            } finally {
                var redo = undo.perform(new org.eclipse.core.runtime.NullProgressMonitor());
                if (redo != null) redo.dispose();
                undo.dispose();
            }
        } finally { composite.dispose(); }
        assertEquals(before, subject.getSource());
        assertEquals(resourceBefore, resource.getSource());
        assertEquals(execute(before, false, false, subject, "before", resourceBefore),
                execute(after, true, false, subject, "after", resourceAfter));
    }

    @ParameterizedTest
    @EnumSource(JUnitCleanupCases.class)
    void preservesExistingAnonymousAndNestedCases(JUnitCleanupCases fixture) throws Exception {
        var unit = createUnit(fixture.given, "test", "MyTest.java");
        var baseline = diagnostics(parse(unit));
        var cleanup = new JUnitCleanUpCore(Map.of("cleanup.junitcleanup", "true",
                "cleanup.junitcleanup_4_ruleexternalresource", "true", "cleanup.junitcleanup_4_test", "true"));
        var status = cleanup.checkPreConditions(unit.getJavaProject(), new ICompilationUnit[] { unit }, null);
        assertFalse(status.hasError(), status.toString());
        var fix = cleanup.createFix(new CleanUpContext(unit, parse(unit)));
        assertNotNull(fix);
        var change = fix.createChange(null);
        try {
            String preview = change.getPreviewContent(null);
            assertEquals(preview, change.getPreviewContent(null));
            assertEquals(fixture.given, unit.getSource());
            var undo = change.perform(new org.eclipse.core.runtime.NullProgressMonitor());
            assertNotNull(undo);
            try {
                assertEquals(fixture.expected.replaceAll("\\s+", ""), unit.getSource().replaceAll("\\s+", ""));
                diagnostics(parse(unit)).forEach((key, count) ->
                        assertTrue(count <= baseline.getOrDefault(key, 0L), key));
            } finally {
                var redo = undo.perform(new org.eclipse.core.runtime.NullProgressMonitor());
                if (redo != null) redo.dispose();
                undo.dispose();
            }
            assertEquals(fixture.given, unit.getSource());
        } finally {
            change.dispose();
            cleanup.checkPostConditions(null);
        }
    }

    private ICompilationUnit createUnit(String source) throws Exception {
        return createUnit(source, "fixture", "Subject.java");
    }

    private ICompilationUnit createUnit(String source, String packageName, String fileName) throws Exception {
        project = ResourcesPlugin.getWorkspace().getRoot().getProject("ExternalResourceFields-" + System.nanoTime());
        project.create(null);
        project.open(null);
        var description = project.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        project.setDescription(description, null);
        var javaProject = JavaCore.create(project);
        var folder = project.getFolder("src");
        folder.create(true, true, null);
        javaProject.setRawClasspath(new IClasspathEntry[] {
            JavaCore.newSourceEntry(folder.getFullPath()),
            JavaCore.newContainerEntry(IPath.fromPortableString("org.eclipse.jdt.launching.JRE_CONTAINER")),
            JavaCore.newContainerEntry(JUnitCore.JUNIT4_CONTAINER_PATH),
            JavaCore.newContainerEntry(JUnitCore.JUNIT5_CONTAINER_PATH)
        }, project.getFullPath().append("bin"), null);
        var options = javaProject.getOptions(false);
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
        javaProject.setOptions(options);
        assertNotNull(javaProject.findType("org.junit.rules.ExternalResource"));
        assertNotNull(javaProject.findType("org.junit.jupiter.api.extension.BeforeEachCallback"));
        return javaProject.getPackageFragmentRoot(folder).createPackageFragment(packageName, false, null)
                .createCompilationUnit(fileName, source, false, null);
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

    private static Map<String, Long> diagnostics(CompilationUnit unit) {
        return Arrays.stream(unit.getProblems()).filter(p -> p.isError() || p.isWarning())
                .collect(Collectors.groupingBy(p -> p.isError() + ":" + p.getID() + ":"
                        + Arrays.toString(p.getArguments()), Collectors.counting()));
    }

    private int execute(String source, boolean migrated, boolean classRule, ICompilationUnit unit,
            String stage) throws Exception {
        return execute(source, migrated, classRule, unit, stage, null);
    }

    private int execute(String source, boolean migrated, boolean classRule, ICompilationUnit unit,
            String stage, String resourceSource) throws Exception {
        Path directory = Files.createDirectories(temporary.resolve(stage));
        Path file = Files.writeString(directory.resolve("Subject.java"), source, StandardCharsets.UTF_8);
        var jars = Arrays.stream(unit.getJavaProject().getResolvedClasspath(true))
                .filter(e -> e.getEntryKind() == IClasspathEntry.CPE_LIBRARY)
                .map(e -> e.getPath().toFile()).filter(File::isFile).distinct().toList();
        String classpath = jars.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        var arguments = new java.util.ArrayList<>(java.util.List.of("--release", "8", "-cp", classpath,
                "-d", directory.toString(), file.toString()));
        if (resourceSource != null) arguments.add(Files.writeString(directory.resolve("Resource.java"),
                resourceSource, StandardCharsets.UTF_8).toString());
        assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)));
        var urls = new java.util.ArrayList<java.net.URL>();
        urls.add(directory.toUri().toURL());
        for (File jar : jars) urls.add(jar.toURI().toURL());
        try (var loader = new URLClassLoader(urls.toArray(java.net.URL[]::new), null)) {
            Class<?> type = loader.loadClass("fixture.Subject");
            Object instance = type.getConstructor().newInstance();
            Object resource = type.getField("resource").get(instance);
            for (String action : new String[] { "before", "after" }) {
                var method = migrated
                        ? resource.getClass().getDeclaredMethod(action + (classRule ? "All" : "Each"),
                                loader.loadClass("org.junit.jupiter.api.extension.ExtensionContext"))
                        : resource.getClass().getDeclaredMethod(action);
                method.setAccessible(true);
                method.invoke(resource, migrated ? new Object[] { null } : new Object[0]);
            }
            return type.getField("events").getInt(null);
        }
    }

    @AfterEach
    void deleteProject() throws Exception {
        if (project != null && project.exists()) project.delete(true, true, null);
    }
}
