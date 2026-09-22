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

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.testplugin.TestOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.CreateChangeOperation;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix.helper.ChangeBehavior;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava10;

/** Uses the actual JDT cleanup lifecycle and one composite apply/undo. */
class CharsetScopeTest {
    @RegisterExtension EclipseJava10 context= new EclipseJava10();

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path runtimeDirectory;

    @BeforeEach void setUp() throws Exception {
        JavaCore.setOptions(TestOptions.getDefaultOptions());
        TestOptions.initializeCodeGenerationOptions();
        JavaPlugin.getDefault().getCodeTemplateStore().load();
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void sourceInterfaceAndCallerAreUpdatedAtomically(ChangeBehavior mode) throws Exception {
        ICompilationUnit[] units= fixture();
        Map<String, String> before= sources(units);
        Map<String, Map<String, Long>> warnings= new HashMap<>();
        for (ICompilationUnit unit : units) warnings.put(unit.getElementName(), CharsetModernizationTest.diagnostics(unit));
        var cleanup= new org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUp(options(mode));
        var refactoring= new CleanUpRefactoring();
        for (ICompilationUnit unit : units) refactoring.addCompilationUnit(unit);
        refactoring.addCleanUp(cleanup);
        var create= new CreateChangeOperation(new CheckConditionsOperation(refactoring,
                CheckConditionsOperation.ALL_CONDITIONS), RefactoringStatus.FATAL);
        create.run(new NullProgressMonitor());
        assertFalse(create.getConditionCheckingStatus().hasError(), create.getConditionCheckingStatus().toString());
        Change change= create.getChange();
        assertNotNull(change);
        try {
            previews(change);
            assertEquals(before, sources(units));
            var manager= RefactoringCore.getUndoManager();
            manager.flush();
            var perform= new PerformChangeOperation(change);
            perform.setUndoManager(manager, refactoring.getName());
            ResourcesPlugin.getWorkspace().run(perform, new NullProgressMonitor());
            assertTrue(perform.changeExecuted());
            for (ICompilationUnit unit : units) {
                CharsetModernizationTest.diagnostics(unit).forEach((key, count) ->
                        assertTrue(count <= warnings.get(unit.getElementName()).getOrDefault(key, 0L), key));
                assertTrue(unit.getSource().contains("IOException"), unit.getSource()); //$NON-NLS-1$
                assertFalse(unit.getSource().contains(".name()"), unit.getSource()); //$NON-NLS-1$
            }
            var invocation= Arrays.stream(((org.eclipse.jdt.core.dom.TypeDeclaration)
                    CharsetModernizationTest.parse(units[1]).types().get(0)).getMethods()).findFirst().orElseThrow();
            var returned= (org.eclipse.jdt.core.dom.ReturnStatement) invocation.getBody().statements().get(0);
            var constructor= (org.eclipse.jdt.core.dom.ClassInstanceCreation) returned.getExpression();
            assertEquals("java.nio.charset.Charset", constructor.resolveConstructorBinding().getParameterTypes()[1].getQualifiedName()); //$NON-NLS-1$
            for (ICompilationUnit unit : units) assertNull(new UseExplicitEncodingCleanUpCore(options(mode))
                    .createFix(new CleanUpContext(unit, CharsetModernizationTest.parse(unit))));
            assertTrue(manager.anythingToUndo());
            manager.performUndo(null, new NullProgressMonitor());
            assertEquals(before, sources(units));
            for (ICompilationUnit unit : units) assertEquals(warnings.get(unit.getElementName()), CharsetModernizationTest.diagnostics(unit));
            manager.flush();
        } finally { change.dispose(); }
    }

    @Test void incompleteSelectionReportsTheMissingSourceWithoutEditing() throws Exception {
        ICompilationUnit[] units= fixture();
        var before= sources(units);
        var cleanup= new UseExplicitEncodingCleanUpCore(options(ChangeBehavior.KEEP_BEHAVIOR));
        var status= cleanup.checkPreConditions(units[1].getJavaProject(), new ICompilationUnit[] { units[1] }, new NullProgressMonitor());
        assertTrue(status.hasFatalError(), status.toString());
        assertEquals(before, sources(units));
        cleanup.checkPostConditions(new NullProgressMonitor());
    }

    @Test void scopeDiscoveryClosesTheInterfaceAndCallerChain() throws Exception {
        ICompilationUnit[] units= fixture();
        var before= sources(units);
        var cleanup= new org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUp(options(ChangeBehavior.KEEP_BEHAVIOR));
        var selected= new java.util.LinkedHashSet<ICompilationUnit>();
        selected.add(units[1]);
        for (int pass= 0; pass <= units.length; pass++) {
            var additions= cleanup.expandCleanUpScope(units[1].getJavaProject(), selected, new NullProgressMonitor());
            if (!selected.addAll(additions)) break;
        }
        assertEquals(java.util.Set.of(units), selected);
        assertEquals(before, sources(units));
        assertFalse(cleanup.checkPreConditions(units[1].getJavaProject(), selected.toArray(ICompilationUnit[]::new), null).hasError());
        var preview= cleanup.getCoordinatedCleanUpPreview(units[1].getJavaProject());
        assertEquals(1, preview.size());
        assertEquals(3, ((java.util.Collection<?>) preview.iterator().next().get("compilationUnits")).size());
        cleanup.checkPostConditions(null);
        assertTrue(cleanup.getCoordinatedCleanUpPreview(units[1].getJavaProject()).isEmpty());
    }

    @Test void crossProjectSourcesAreOutOfScopeConflicts() throws Exception {
        ICompilationUnit[] units= fixture();
        var external= org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava.createJavaProject("CharsetExternalClient", "bin"); //$NON-NLS-1$ //$NON-NLS-2$
        try {
            external.setRawClasspath(context.getDefaultClasspath(), null);
            org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava.addToClasspath(external,
                    JavaCore.newProjectEntry(context.getJavaProject().getPath()));
            var root= org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava.addSourceContainer(external, "src"); //$NON-NLS-1$
            root.createPackageFragment("external", false, null).createCompilationUnit("ExternalClient.java", //$NON-NLS-1$ //$NON-NLS-2$
                    "package external; public class ExternalClient { void run(test1.Open open, java.io.File file) { try { open.open(file); } catch (java.io.FileNotFoundException ex) { } } }", false, null); //$NON-NLS-1$
            var cleanup= new UseExplicitEncodingCleanUpCore(options(ChangeBehavior.KEEP_BEHAVIOR));
            var status= cleanup.checkPreConditions(units[1].getJavaProject(), units, null);
            assertTrue(status.hasFatalError(), status.toString());
            assertTrue(status.toString().contains("Caller outside selected Java project scope"), status.toString()); //$NON-NLS-1$
            assertFalse(status.toString().contains("additional source units required"), status.toString()); //$NON-NLS-1$
            cleanup.checkPostConditions(null);
        } finally { context.delete(external); }

        var originalClasspath= context.getJavaProject().getRawClasspath();
        var contract= org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava.createJavaProject("CharsetExternalContract", "bin"); //$NON-NLS-1$ //$NON-NLS-2$
        try {
            contract.setRawClasspath(context.getDefaultClasspath(), null);
            var root= org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava.addSourceContainer(contract, "src"); //$NON-NLS-1$
            root.createPackageFragment("external", false, null).createCompilationUnit("ExternalOpen.java", //$NON-NLS-1$ //$NON-NLS-2$
                    "package external; public interface ExternalOpen { java.util.Scanner open(java.io.File file) throws java.io.FileNotFoundException; }", false, null); //$NON-NLS-1$
            org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava.addToClasspath(context.getJavaProject(),
                    JavaCore.newProjectEntry(contract.getPath()));
            var unit= context.getSourceFolder().createPackageFragment("test1", false, null).createCompilationUnit("ExternalFactory.java", //$NON-NLS-1$ //$NON-NLS-2$
                    "package test1; public class ExternalFactory implements external.ExternalOpen { public java.util.Scanner open(java.io.File file) throws java.io.FileNotFoundException { return new java.util.Scanner(file, \"UTF-8\"); } }", false, null); //$NON-NLS-1$
            var cleanup= new UseExplicitEncodingCleanUpCore(options(ChangeBehavior.KEEP_BEHAVIOR));
            var status= cleanup.checkPreConditions(unit.getJavaProject(), new ICompilationUnit[] { unit }, null);
            assertTrue(status.hasFatalError(), status.toString());
            assertTrue(status.toString().contains("Callable contract outside selected Java project scope"), status.toString()); //$NON-NLS-1$
            cleanup.checkPostConditions(null);
        } finally {
            context.getJavaProject().setRawClasspath(originalClasspath, null);
            context.delete(contract);
        }
    }

    @Test void cancellationAndSourceEditsInvalidateThePlan() throws Exception {
        ICompilationUnit[] units= fixture();
        var cleanup= new UseExplicitEncodingCleanUpCore(options(ChangeBehavior.KEEP_BEHAVIOR));
        var monitor= new NullProgressMonitor();
        monitor.setCanceled(true);
        assertThrows(org.eclipse.core.runtime.OperationCanceledException.class,
                () -> cleanup.checkPreConditions(units[1].getJavaProject(), units, monitor));
        assertTrue(cleanup.getCoordinatedCleanUpPreview(units[1].getJavaProject()).isEmpty());
        assertFalse(cleanup.checkPreConditions(units[1].getJavaProject(), units, null).hasError());
        units[1].getBuffer().setContents(units[1].getSource() + "\n// edited after planning\n");
        var before= sources(units);
        var fix= cleanup.createFix(new CleanUpContext(units[1], CharsetModernizationTest.parse(units[1])));
        assertThrows(org.eclipse.core.runtime.CoreException.class, () -> fix.createChange(null));
        assertEquals(before, sources(units));
        cleanup.checkPostConditions(null);
    }

    @Test void staleSourceIsRejectedEvenWhenNoThrowsChangeWasNeeded() throws Exception {
        var unit= context.getSourceFolder().createPackageFragment("test1", false, null)
                .createCompilationUnit("E1.java", """
                    package test1;
                    public class E1 {
                        java.util.Scanner open(java.io.File file) throws java.io.IOException {
                            return new java.util.Scanner(file, "UTF-8");
                        }
                    }
                    """, false, null);
        var cleanup= new UseExplicitEncodingCleanUpCore(options(ChangeBehavior.KEEP_BEHAVIOR));
        assertFalse(cleanup.checkPreConditions(unit.getJavaProject(), new ICompilationUnit[] { unit }, null).hasError());
        unit.getBuffer().setContents(unit.getSource() + "\n// newer source\n");
        String before= unit.getSource();
        var fix= cleanup.createFix(new CleanUpContext(unit, CharsetModernizationTest.parse(unit)));
        assertThrows(org.eclipse.core.runtime.CoreException.class, () -> fix.createChange(null));
        assertEquals(before, unit.getSource());
        cleanup.checkPostConditions(null);
    }

    @Test void aggregateCharsetFieldsStayScopedPerCompilationUnit() throws Exception {
        Map<String, String> forward= runAggregateCharsetCleanup("aggregateforward", "E1.java", "E2.java"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        Map<String, String> reversed= runAggregateCharsetCleanup("aggregatereverse", "E2.java", "E1.java"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        assertEquals(forward.keySet(), reversed.keySet());
        for (Map.Entry<String, String> entry : forward.entrySet()) {
            String unitName= entry.getKey();
            assertEquals(normalizeAggregatePackage(entry.getValue(), "aggregateforward"), //$NON-NLS-1$
                    normalizeAggregatePackage(reversed.get(unitName), "aggregatereverse"), unitName); //$NON-NLS-1$
        }
    }

    @Test void aggregateCharsetFieldsStayScopedPerTopLevelOwnerWithinCompilationUnit() throws Exception {
        String before= """
                package probe;
                import java.nio.charset.Charset;
                public class FirstEncoding {
                    public static String value() { return Charset.forName("UTF-8").name(); }
                }
                class SecondaryEncoding {
                    static String value() { return Charset.forName("UTF-8").name(); }
                }
                """;
        String after= runSingleUnitAggregateCleanup("probe", "FirstEncoding.java", before); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(2, countOccurrences(after, "private static final Charset UTF_8 = StandardCharsets.UTF_8;"), after); //$NON-NLS-1$
        assertTrue(after.contains("return FirstEncoding.UTF_8.name();"), after); //$NON-NLS-1$
        assertTrue(after.contains("return SecondaryEncoding.UTF_8.name();"), after); //$NON-NLS-1$
    }

    @Test void aggregateCharsetReusesCompatibleExistingField() throws Exception {
        String before= """
                package probe;
                import java.nio.charset.Charset;
                import java.nio.charset.StandardCharsets;
                public class ReuseProbe {
                    private static final Charset UTF_8 = StandardCharsets.UTF_8;
                    public static String value() {
                        return Charset.forName("UTF-8").name() + ":" + UTF_8.name();
                    }
                    public static void main(String[] args) { System.out.print(value()); }
                }
                """;
        String after= runSingleUnitAggregateCleanup("probe", "ReuseProbe.java", before); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(1, countOccurrences(after, "private static final Charset UTF_8 = StandardCharsets.UTF_8;"), after); //$NON-NLS-1$
        assertTrue(after.contains("return ReuseProbe.UTF_8.name() + \":\" + UTF_8.name();"), after); //$NON-NLS-1$
        assertEquals(compileAndRun(before, "probe.ReuseProbe", runtimeDirectory.resolve("reuse-before")), //$NON-NLS-1$ //$NON-NLS-2$
                compileAndRun(after, "probe.ReuseProbe", runtimeDirectory.resolve("reuse-after"))); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test void aggregateCharsetAvoidsSelfReferentialFieldReuseDuringInitialization() throws Exception {
        String before= """
                package probe;
                import java.nio.charset.Charset;
                public class SelfProbe {
                    private static final Charset ENCODING = Charset.forName("UTF-8");
                    public static void main(String[] args) { System.out.print(ENCODING.name()); }
                }
                """;
        String after= runSingleUnitAggregateCleanup("probe", "SelfProbe.java", before); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(after.contains("private static final Charset ENCODING = StandardCharsets.UTF_8;"), after); //$NON-NLS-1$
        assertFalse(after.contains("SelfProbe.ENCODING"), after); //$NON-NLS-1$
        assertEquals(compileAndRun(before, "probe.SelfProbe", runtimeDirectory.resolve("self-before")), //$NON-NLS-1$ //$NON-NLS-2$
                compileAndRun(after, "probe.SelfProbe", runtimeDirectory.resolve("self-after"))); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test void aggregateCharsetAvoidsPrematureFieldReadsDuringStaticInitialization() throws Exception {
        String before= """
                package probe;
                import java.nio.charset.Charset;
                import java.nio.charset.StandardCharsets;
                public class ForwardProbe {
                    private static final String INITIAL = value();
                    private static final Charset UTF_8 = StandardCharsets.UTF_8;
                    public static void main(String[] args) { System.out.print(INITIAL + ":" + UTF_8.name()); }
                    static String value() { return Charset.forName("UTF-8").name(); }
                }
                """;
        String after= runSingleUnitAggregateCleanup("probe", "ForwardProbe.java", before); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(after.contains("return StandardCharsets.UTF_8.name();"), after); //$NON-NLS-1$
        assertFalse(after.contains("return ForwardProbe.UTF_8.name();"), after); //$NON-NLS-1$
        assertEquals(compileAndRun(before, "probe.ForwardProbe", runtimeDirectory.resolve("forward-before")), //$NON-NLS-1$ //$NON-NLS-2$
                compileAndRun(after, "probe.ForwardProbe", runtimeDirectory.resolve("forward-after"))); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test void aggregateCharsetCollisionGetsFreshCompatibleFieldName() throws Exception {
        String before= """
                package probe;
                import java.nio.charset.Charset;
                import java.nio.charset.StandardCharsets;
                public class CollisionProbe {
                    private static final Charset UTF_8 = StandardCharsets.ISO_8859_1;
                    public static String value() {
                        return Charset.forName("UTF-8").name() + ":" + UTF_8.name();
                    }
                    public static void main(String[] args) { System.out.print(value()); }
                }
                """;
        String after= runSingleUnitAggregateCleanup("probe", "CollisionProbe.java", before); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(after.contains("private static final Charset UTF_8_1 = StandardCharsets.UTF_8;"), after); //$NON-NLS-1$
        assertTrue(after.contains("return CollisionProbe.UTF_8_1.name() + \":\" + UTF_8.name();"), after); //$NON-NLS-1$
        assertEquals(compileAndRun(before, "probe.CollisionProbe", runtimeDirectory.resolve("collision-before")), //$NON-NLS-1$ //$NON-NLS-2$
                compileAndRun(after, "probe.CollisionProbe", runtimeDirectory.resolve("collision-after"))); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test void aggregateCharsetNonCharsetCollisionGetsFreshCompatibleFieldName() throws Exception {
        String before= """
                package probe;
                import java.nio.charset.Charset;
                public class NonCharsetCollision {
                    private static final String UTF_8 = "existing";
                    static String value() {
                        return Charset.forName("UTF-8").name() + ":" + UTF_8;
                    }
                }
                """;
        String after= runSingleUnitAggregateCleanup("probe", "NonCharsetCollision.java", before); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(after.contains("private static final String UTF_8 = \"existing\";"), after); //$NON-NLS-1$
        assertTrue(after.contains("private static final Charset UTF_8_1 = StandardCharsets.UTF_8;"), after); //$NON-NLS-1$
        assertTrue(after.contains("return NonCharsetCollision.UTF_8_1.name() + \":\" + UTF_8;"), after); //$NON-NLS-1$
    }

    @Test void aggregateCharsetFieldsStayScopedPerNestedOwner() throws Exception {
        String before= """
                package probe;
                import java.nio.charset.Charset;
                public class OuterEncoding {
                    static String value() { return Charset.forName("UTF-8").name(); }
                    static class InnerEncoding {
                        static String value() { return Charset.forName("UTF-8").name(); }
                    }
                }
                """;
        String after= runSingleUnitAggregateCleanup("probe", "OuterEncoding.java", before); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(2, countOccurrences(after, "private static final Charset UTF_8 = StandardCharsets.UTF_8;"), after); //$NON-NLS-1$
        assertTrue(after.contains("return OuterEncoding.UTF_8.name();"), after); //$NON-NLS-1$
        assertTrue(after.contains("return InnerEncoding.UTF_8.name();"), after); //$NON-NLS-1$
    }

    @Test void aggregateCharsetReservationsStayScopedPerRewriteOnSameRoot() throws Exception {
        String before= """
                package probe;
                import java.nio.charset.Charset;
                public class ReplayProbe {
                    static String value() { return Charset.forName("UTF-8").name(); }
                }
                """;
        var unit= context.getSourceFolder().createPackageFragment("probe", false, null) //$NON-NLS-1$
                .createCompilationUnit("ReplayProbe.java", before, false, null); //$NON-NLS-1$
        Map<String, Long> warnings= CharsetModernizationTest.diagnostics(unit);
        var cleanup= new UseExplicitEncodingCleanUpCore(options(ChangeBehavior.ENFORCE_UTF8_AGGREGATE));
        var root= CharsetModernizationTest.parse(unit);
        var firstFix= cleanup.createFix(new CleanUpContext(unit, root));
        assertNotNull(firstFix);
        var firstChange= firstFix.createChange(null);
        assertNotNull(firstChange);
        String firstPreview;
        try {
            firstPreview= firstChange.getPreviewContent(null);
        } finally {
            firstChange.dispose();
        }
        var secondFix= cleanup.createFix(new CleanUpContext(unit, root));
        assertNotNull(secondFix);
        var secondChange= secondFix.createChange(null);
        assertNotNull(secondChange);
        try {
            String secondPreview= secondChange.getPreviewContent(null);
            assertEquals(firstPreview, secondPreview);
            var undo= secondChange.perform(null);
            assertNotNull(undo);
            try {
                assertEquals(secondPreview, unit.getSource());
                CharsetModernizationTest.diagnostics(unit).forEach((key, count) ->
                        assertTrue(count <= warnings.getOrDefault(key, 0L), key));
                assertNull(cleanup.createFix(new CleanUpContext(unit, CharsetModernizationTest.parse(unit))));
            } finally {
                var redo= undo.perform(null);
                if (redo != null) {
                    redo.dispose();
                }
            }
        } finally {
            secondChange.dispose();
        }
        assertEquals(before, unit.getSource());
        assertEquals(warnings, CharsetModernizationTest.diagnostics(unit));
    }

    private ICompilationUnit[] fixture() throws Exception {
        var pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
        String[] names= { "Open", "Factory", "Client" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        String[] bodies= {
                "public interface Open { java.util.Scanner open(java.io.File file) throws java.io.FileNotFoundException; }", //$NON-NLS-1$
                "public class Factory implements Open { public java.util.Scanner open(java.io.File file) throws java.io.FileNotFoundException { return new java.util.Scanner(file, \"UTF-8\"); } }", //$NON-NLS-1$
                "public class Client { public java.util.Scanner run(Open factory, java.io.File file) { try { return factory.open(file); } catch (java.io.FileNotFoundException ex) { throw new IllegalStateException(ex); } } }" //$NON-NLS-1$
        };
        ICompilationUnit[] result= new ICompilationUnit[names.length];
        for (int i= 0; i < names.length; i++) result[i]= pack.createCompilationUnit(names[i] + ".java", "package test1;\n" + bodies[i], false, null); //$NON-NLS-1$ //$NON-NLS-2$
        return result;
    }
    private static void previews(Change change) throws Exception {
        if (change instanceof CompositeChange composite) for (Change child : composite.getChildren()) previews(child);
        else if (change instanceof TextChange text) assertEquals(text.getPreviewContent(null), text.getPreviewContent(null));
    }
    private static Map<String, String> sources(ICompilationUnit[] units) throws Exception {
        Map<String, String> result= new HashMap<>();
        for (ICompilationUnit unit : units) result.put(unit.getElementName(), unit.getSource());
        return result;
    }
    static Map<String, String> options(ChangeBehavior mode) {
        return Map.of(MYCleanUpConstants.EXPLICITENCODING_CLEANUP, "true", switch (mode) { //$NON-NLS-1$
            case KEEP_BEHAVIOR -> MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR;
            case ENFORCE_UTF8 -> MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8;
            case ENFORCE_UTF8_AGGREGATE -> MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8;
        }, "true"); //$NON-NLS-1$
    }

    private Map<String, String> runAggregateCharsetCleanup(String packageName, String... unitOrder) throws Exception {
        var pack= context.getSourceFolder().createPackageFragment(packageName, false, null);
        Map<String, ICompilationUnit> unitsByName= new LinkedHashMap<>();
        for (String typeName : new String[] { "E1", "E2" }) { //$NON-NLS-1$ //$NON-NLS-2$
            String source= "package " + packageName + ";\n" //$NON-NLS-1$ //$NON-NLS-2$
                    + "public class " + typeName + " {\n" //$NON-NLS-1$ //$NON-NLS-2$
                    + "    java.util.Scanner open(java.io.File file) throws java.io.FileNotFoundException {\n" //$NON-NLS-1$
                    + "        return new java.util.Scanner(file, \"UTF-8\");\n" //$NON-NLS-1$
                    + "    }\n" //$NON-NLS-1$
                    + "}\n"; //$NON-NLS-1$
            unitsByName.put(typeName + ".java", pack.createCompilationUnit(typeName + ".java", source, false, null)); //$NON-NLS-1$
        }
        Map<String, String> after= runCleanup(unitsByName, options(ChangeBehavior.ENFORCE_UTF8_AGGREGATE), unitOrder);
        for (Map.Entry<String, String> entry : after.entrySet()) {
            String typeName= entry.getKey().replace(".java", ""); //$NON-NLS-1$ //$NON-NLS-2$
            String source= entry.getValue();
            assertTrue(source.contains("private static final Charset UTF_8 = StandardCharsets.UTF_8;"), source); //$NON-NLS-1$
            assertEquals(1, countOccurrences(source, "private static final Charset UTF_8 = StandardCharsets.UTF_8;"), source); //$NON-NLS-1$
            assertTrue(source.contains(typeName + ".UTF_8"), source); //$NON-NLS-1$
            assertFalse(source.contains(("E1".equals(typeName) ? "E2" : "E1") + ".UTF_8"), source); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        return after;
    }

    private String runSingleUnitAggregateCleanup(String packageName, String unitName, String before) throws Exception {
        var pack= context.getSourceFolder().createPackageFragment(packageName, false, null);
        ICompilationUnit unit= pack.createCompilationUnit(unitName, before, false, null);
        return runCleanup(Map.of(unitName, unit), options(ChangeBehavior.ENFORCE_UTF8_AGGREGATE), unitName).get(unitName);
    }

    private Map<String, String> runCleanup(Map<String, ICompilationUnit> unitsByName, Map<String, String> cleanupOptions,
            String... unitOrder) throws Exception {
        Map<String, String> before= unitsByName.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> source(entry.getValue()), (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, Map<String, Long>> warnings= new LinkedHashMap<>();
        for (ICompilationUnit unit : unitsByName.values()) {
            warnings.put(unit.getElementName(), CharsetModernizationTest.diagnostics(unit));
        }
        var cleanup= new org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUp(cleanupOptions);
        var refactoring= new CleanUpRefactoring();
        for (String unitName : unitOrder) {
            refactoring.addCompilationUnit(unitsByName.get(unitName));
        }
        refactoring.addCleanUp(cleanup);
        var create= new CreateChangeOperation(new CheckConditionsOperation(refactoring,
                CheckConditionsOperation.ALL_CONDITIONS), RefactoringStatus.FATAL);
        create.run(new NullProgressMonitor());
        assertFalse(create.getConditionCheckingStatus().hasError(), create.getConditionCheckingStatus().toString());
        Change change= create.getChange();
        assertNotNull(change);
        try {
            previews(change);
            assertEquals(before, unitsByName.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> source(entry.getValue()), (left, right) -> left,
                            LinkedHashMap::new)));
            Map<String, String> previewMap= previewContents(change);
            var manager= RefactoringCore.getUndoManager();
            manager.flush();
            var perform= new PerformChangeOperation(change);
            perform.setUndoManager(manager, refactoring.getName());
            ResourcesPlugin.getWorkspace().run(perform, new NullProgressMonitor());
            assertTrue(perform.changeExecuted());
            Map<String, String> after= new LinkedHashMap<>();
            for (Map.Entry<String, ICompilationUnit> entry : unitsByName.entrySet()) {
                ICompilationUnit unit= entry.getValue();
                String source= unit.getSource();
                if (previewMap.containsKey(entry.getKey())) {
                    assertEquals(previewMap.get(entry.getKey()), source, entry.getKey());
                }
                CharsetModernizationTest.diagnostics(unit).forEach((key, count) ->
                        assertTrue(count <= warnings.get(unit.getElementName()).getOrDefault(key, 0L), key));
                assertNull(new UseExplicitEncodingCleanUpCore(cleanupOptions)
                        .createFix(new CleanUpContext(unit, CharsetModernizationTest.parse(unit))));
                after.put(entry.getKey(), source);
            }
            assertTrue(manager.anythingToUndo());
            manager.performUndo(null, new NullProgressMonitor());
            assertEquals(before, unitsByName.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> source(entry.getValue()), (left, right) -> left,
                            LinkedHashMap::new)));
            for (ICompilationUnit unit : unitsByName.values()) {
                assertEquals(warnings.get(unit.getElementName()), CharsetModernizationTest.diagnostics(unit));
            }
            manager.flush();
            return after;
        } finally { change.dispose(); }
    }

    private static Map<String, String> previewContents(Change change) {
        Map<String, String> previews= new LinkedHashMap<>();
        collectPreviews(change, previews);
        return previews;
    }

    private static void collectPreviews(Change change, Map<String, String> previews) {
        if (change instanceof CompositeChange composite) {
            for (Change child : composite.getChildren()) {
                collectPreviews(child, previews);
            }
            return;
        }
        if (change instanceof TextChange text && text.getModifiedElement() instanceof ICompilationUnit unit) {
            try {
                previews.put(unit.getElementName(), text.getPreviewContent(null));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }
    }

    private static String source(ICompilationUnit unit) {
        try {
            return unit.getSource();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static int countOccurrences(String source, String text) {
        int count= 0;
        for (int index= source.indexOf(text); index >= 0; index= source.indexOf(text, index + text.length())) {
            count++;
        }
        return count;
    }

    private static String normalizeAggregatePackage(String source, String packageName) {
        return source.replace("package " + packageName + ";", "package aggregate;"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String compileAndRun(String source, String className, java.nio.file.Path directory) throws Exception {
        java.nio.file.Files.createDirectories(directory);
        String simpleName= className.substring(className.lastIndexOf('.') + 1);
        var file= directory.resolve(simpleName + ".java"); //$NON-NLS-1$
        java.nio.file.Files.writeString(file, source, java.nio.charset.StandardCharsets.UTF_8);
        var compiler= javax.tools.ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        var diagnostics= new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
        try (var manager= compiler.getStandardFileManager(diagnostics, java.util.Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8)) {
            assertTrue(compiler.getTask(null, manager, diagnostics,
                    java.util.List.of("--release", "21", "-Xlint:all", "-Werror", "-d", directory.toString()), null, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    manager.getJavaFileObjects(file)).call(), diagnostics.getDiagnostics().toString());
            assertTrue(diagnostics.getDiagnostics().isEmpty(), diagnostics.getDiagnostics().toString());
        }
        try (var loader= new java.net.URLClassLoader(new java.net.URL[] { directory.toUri().toURL() }, null)) {
            var buffer= new java.io.ByteArrayOutputStream();
            var originalOut= System.out;
            try (var stream= new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8)) {
                System.setOut(stream);
                loader.loadClass(className).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            } finally {
                System.setOut(originalOut);
            }
            return buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}