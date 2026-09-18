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
import java.util.Map;

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
}
