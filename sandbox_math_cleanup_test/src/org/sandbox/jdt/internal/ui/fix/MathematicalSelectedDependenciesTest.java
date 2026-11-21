/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.changes.MultiStateCompilationUnitChange;
import org.eclipse.jdt.internal.ui.fix.VariableDeclarationCleanUpCore;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jface.text.Document;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Analysis;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;

/** Selected helper dependencies are filtered before native changes are constructed. */
class MathematicalSelectedDependenciesTest {
    private static final NullProgressMonitor MONITOR = new NullProgressMonitor();
    private static final String SKIPPED = "MATHEMATICS_SELECTED_SOURCE_DEPENDENCY:";
    private final Map<ICompilationUnit, String> originals = new LinkedHashMap<>();
    private final Map<ICompilationUnit, byte[]> originalBytes = new LinkedHashMap<>();
    private IProject project;
    private IJavaProject java;
    private Hashtable<String, String> workspaceOptions;
    private boolean autoBuilding;
    private Map<String, String> compiler;
    private List<IClasspathEntry> rawClasspath;
    private List<IClasspathEntry> resolvedClasspath;
    private Display ownedDisplay;

    @BeforeEach void createProject() throws Exception {
        var workspace = ResourcesPlugin.getWorkspace();
        workspaceOptions = JavaCore.getOptions();
        var description = workspace.getDescription();
        autoBuilding = description.isAutoBuilding();
        description.setAutoBuilding(false);
        workspace.setDescription(description);
        var properties = new Properties();
        try (var input = getClass().getResourceAsStream("selected-dependencies-compiler.properties")) {
            assertNotNull(input, "The complete compiler fixture must be packaged with this test");
            properties.load(input);
        }
        var options = new HashMap<String, String>();
        properties.stringPropertyNames().forEach(key -> options.put(key, properties.getProperty(key)));
        compiler = Map.copyOf(options);
        assertEquals(594, compiler.size());
        assertEquals("21", compiler.get(JavaCore.COMPILER_SOURCE));
        var global = new Hashtable<>(workspaceOptions);
        global.put(JavaCore.CORE_JAVA_BUILD_RESOURCE_COPY_FILTER,
                compiler.get(JavaCore.CORE_JAVA_BUILD_RESOURCE_COPY_FILTER));
        JavaCore.setOptions(global);
        project = workspace.getRoot().getProject("MathSelectedDependencies-" + UUID.randomUUID());
        project.create(MONITOR);
        project.open(MONITOR);
        var projectDescription = project.getDescription();
        projectDescription.setNatureIds(new String[] { JavaCore.NATURE_ID });
        project.setDescription(projectDescription, MONITOR);
        var src = project.getFolder("src");
        src.create(true, true, MONITOR);
        var bin = project.getFolder("bin");
        bin.create(true, true, MONITOR);
        java = JavaCore.create(project);
        java.setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(src.getFullPath()),
                JavaRuntime.getDefaultJREContainerEntry() }, bin.getFullPath(), MONITOR);
        java.setOptions(compiler);
        assertEquals(compiler, java.getOptions(true), "Keep all original compiler options and severities");
        rawClasspath = List.of(java.getRawClasspath());
        resolvedClasspath = List.of(java.getResolvedClasspath(true));
        // The normal Tycho suite already owns its workbench display. The focused
        // native application uses the same test without a second UI framework.
        if (!PlatformUI.isWorkbenchRunning()) ownedDisplay = new Display();
    }

    @AfterEach void removeProject() throws Exception {
        try {
            if (project != null && project.exists()) project.delete(true, true, MONITOR);
        } finally {
            try {
                if (workspaceOptions != null) JavaCore.setOptions(workspaceOptions);
            } finally {
                try {
                    var workspace = ResourcesPlugin.getWorkspace();
                    var description = workspace.getDescription();
                    description.setAutoBuilding(autoBuilding);
                    workspace.setDescription(description);
                } finally {
                    if (ownedDisplay != null && !ownedDisplay.isDisposed()) ownedDisplay.dispose();
                }
            }
        }
    }

    @Test void selectedMutualHelpersStayOriginalWhileIndependentNativeChangeAppliesAndUndoes() throws Throwable {
        var first = unit("A", source("A", "B", true));
        var second = unit("B", source("B", "A", true));
        var independent = unit("Independent", source("Independent", "Independent", false));
        assertDependency(first, second);
        assertDependency(second, first);
        var independentAnalysis = analyze(independent);
        assertTrue(independentAnalysis.changed(), independentAnalysis.diagnostics().toString());
        assertTrue(independentAnalysis.sourceDependencyReceipts().isEmpty(),
                "A helper declared in the same CU is not a selected peer dependency");

        var observed = new ObservedMathematics(new MathematicalCleanUpCore(settings()));
        var refactoring = refactoring(observed, first, second, independent);
        refactoring.addCleanUp(new VariableDeclarationCleanUpCore(Map.of(
                CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL, CleanUpOptions.TRUE,
                CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_LOCAL_VARIABLES, CleanUpOptions.TRUE)));
        var status = refactoring.checkAllConditions(MONITOR);
        assertFalse(status.hasError(), status.toString());
        assertEquals(Set.of(independent.getHandleIdentifier()), observed.changes.keySet(),
                "Only the independent verified candidate may become a native mathematics change");
        assertSkipped(status, 2);
        var change = assertInstanceOf(CompositeChange.class, refactoring.createChange(MONITOR));
        assertEquals(1, change.getChildren().length, "Selected peer plans must never enter native Apply");
        var child = assertInstanceOf(MultiStateCompilationUnitChange.class, change.getChildren()[0]);
        assertEquals(independent, child.getCompilationUnit(), "Retain the real Math + final-locals host");
        performAndUndo(change, Map.of(independent, source("Independent", "Independent", true).replace("return sum - x;", "return y;")));
    }

    @Test void aNewSingleSelectionOnTheSameCleanupDoesNotRetainThePreviousPeerSelection() throws Throwable {
        var first = unit("A", source("A", "B", true));
        var second = unit("B", source("B", "A", true));
        assertDependency(first, second);
        var cleanup = new MathematicalCleanUpCore(settings());
        assertFalse(cleanup.checkPreConditions(java, new ICompilationUnit[] { first, second }, MONITOR).hasError());
        assertFalse(cleanup.checkPostConditions(MONITOR).hasError());
        var observed = new ObservedMathematics(cleanup);
        var refactoring = refactoring(observed, first);
        var status = refactoring.checkAllConditions(MONITOR);
        assertFalse(status.hasError(), status.toString());
        assertSkipped(status, 0);
        assertEquals(Set.of(first.getHandleIdentifier()), observed.changes.keySet());
        var change = refactoring.createChange(MONITOR);
        performAndUndo(change, Map.of(first, originals.get(first).replace("return sum - x;", "return y;")));
    }

    @Test void aSelectedHelperWithoutItsOwnReplacementStillDefersTheDependentCandidate() throws Exception {
        var first = unit("A", source("A", "B", true));
        var second = unit("B", "package selected;\npublic class B { public static int add(int x, int y) { return x + y; } }\n");
        assertDependency(first, second);
        assertFalse(analyze(second).changed(), "The filter must depend on selection, not a peer Math replacement");
        var baseline = CompilerDiagnosticAssertions.capture(units());
        var cleanup = new MathematicalCleanUpCore(settings());
        assertFalse(cleanup.checkPreConditions(java, units(), MONITOR).hasError());
        assertNull(cleanup.createFix(new CleanUpContext(first, parse(first))));
        assertSkipped(cleanup.checkPostConditions(MONITOR), 1);
        assertOriginals();
        baseline.assertRestored();
    }

    @Test void workingCopySelectionsUseThePrimaryCompilationUnitIdentity() throws Exception {
        var first = unit("A", source("A", "B", true));
        var second = unit("B", source("B", "A", true));
        var owner = new WorkingCopyOwner() { };
        var firstCopy = first.getWorkingCopy(owner, MONITOR);
        var secondCopy = second.getWorkingCopy(owner, MONITOR);
        try {
            assertEquals(first, firstCopy.getPrimary());
            assertEquals(second, secondCopy.getPrimary());
            assertDependency(firstCopy, second);
            var baseline = CompilerDiagnosticAssertions.capture(units());
            var cleanup = new MathematicalCleanUpCore(settings());
            assertFalse(cleanup.checkPreConditions(java, new ICompilationUnit[] { firstCopy, secondCopy }, MONITOR).hasError());
            assertNull(cleanup.createFix(new CleanUpContext(firstCopy, parse(firstCopy))));
            assertSkipped(cleanup.checkPostConditions(MONITOR), 1);
            assertOriginals();
            baseline.assertRestored();
        } finally {
            try { firstCopy.discardWorkingCopy(); }
            finally { secondCopy.discardWorkingCopy(); }
        }
    }

    private static Map<String, String> settings() {
        var values = new HashMap<>(MathCleanUpOptions.defaults(21).toMap());
        values.put(MathCleanUpOptions.CLEANUP, "true");
        values.put(MathCleanUpOptions.KINDS, "BIG_INTEGER,BYTE,SHORT,CHAR,INT,LONG,FLOAT,DOUBLE");
        values.put(MathCleanUpOptions.SAFETY, "PRESERVE_JAVA");
        values.put(MathCleanUpOptions.WORK_BUDGET, "100000");
        values.put(MathCleanUpOptions.MAX_STATES, "2000");
        values.put(MathCleanUpOptions.TIMEOUT_SECONDS, "5");
        values.put(MathCleanUpOptions.EXPLANATIONS, "NONTRIVIAL");
        return Map.copyOf(values);
    }

    private ICompilationUnit unit(String name, String source) throws Exception {
        var pack = java.getPackageFragmentRoot(project.getFolder("src")).createPackageFragment("selected", true, MONITOR);
        var unit = pack.createCompilationUnit(name + ".java", source, true, MONITOR);
        originals.put(unit, source);
        originalBytes.put(unit, bytes(unit));
        return unit;
    }

    private static String source(String owner, String helper, boolean finalLocal) {
        return "package selected;\npublic class " + owner + " {\n"
                + "    public static int add(int x, int y) { return x + y; }\n"
                + "    public static int run(int x, int y, int[] values) {\n"
                + "        " + (finalLocal ? "final " : "") + "int sum = " + helper + ".add(x, y);\n"
                + "        values[0] = sum;\n"
                + "        return sum - x;\n"
                + "    }\n}\n";
    }

    private Analysis analyze(ICompilationUnit unit) throws Exception {
        return MathematicalAnalysis.analyze(parse(unit), unit.getSource(), MathCleanUpOptions.parse(settings(), 21), MONITOR, -1, 0);
    }

    private void assertDependency(ICompilationUnit caller, ICompilationUnit helper) throws Exception {
        var result = analyze(caller);
        assertTrue(result.changed(), result.diagnostics().toString());
        assertEquals(1, result.sourceDependencyReceipts().size());
        var receipt = result.sourceDependencyReceipts().getFirst();
        var dependency = assertInstanceOf(ICompilationUnit.class, JavaCore.create(receipt.identity()));
        assertEquals(helper.getPrimary(), dependency.getPrimary());
        assertEquals(MathematicalEnvironment.digest(helper.getSource()), receipt.sha256());
        var preview = new Document(caller.getSource());
        result.newEdit().apply(preview);
        assertTrue(preview.get().contains("return y;"), preview.get());
        assertTrue(preview.get().contains("values[0] = sum;"), preview.get());
        assertTrue(preview.get().contains(helper.getElementName().replace(".java", "") + ".add(x, y)"), preview.get());
    }

    private CompilationUnit parse(ICompilationUnit unit) {
        assertEquals(compiler, java.getOptions(true));
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(unit);
        parser.setCompilerOptions(compiler);
        parser.setResolveBindings(true);
        var root = (CompilationUnit) parser.createAST(MONITOR);
        assertTrue(Arrays.stream(root.getProblems()).noneMatch(problem -> problem.isError()),
                () -> Arrays.toString(root.getProblems()));
        return root;
    }

    private static CleanUpRefactoring refactoring(ICleanUp cleanup, ICompilationUnit... selected) {
        var refactoring = new CleanUpRefactoring();
        for (var unit : selected) refactoring.addCompilationUnit(unit);
        refactoring.addCleanUp(cleanup);
        return refactoring;
    }

    private static void assertSkipped(RefactoringStatus status, long expected) {
        var entries = Arrays.stream(status.getEntries()).filter(entry -> entry.getMessage().startsWith(SKIPPED)).toList();
        assertEquals(expected, entries.size(), status.toString());
        assertTrue(entries.stream().allMatch(entry -> entry.getSeverity() == RefactoringStatus.INFO));
    }

    private void performAndUndo(Change change, Map<ICompilationUnit, String> expectedChanges) throws Throwable {
        var baseline = CompilerDiagnosticAssertions.capture(units());
        change.initializeValidationData(MONITOR);
        assertFalse(change.isValid(MONITOR).hasFatalError());
        Change undo = null;
        Throwable failure = null;
        boolean completed = false;
        try {
            undo = change.perform(MONITOR);
            completed = true;
            assertNotNull(undo, "A real native change must supply Undo");
            for (var unit : units()) {
                unit.makeConsistent(MONITOR);
                assertEquals(expectedChanges.getOrDefault(unit, originals.get(unit)), unit.getSource());
                if (!expectedChanges.containsKey(unit)) assertArrayEquals(originalBytes.get(unit), bytes(unit));
            }
            baseline.assertNoNewDiagnostics();
        } catch (Throwable problem) {
            failure = problem;
            if (!completed && change instanceof CompositeChange composite) undo = composite.getUndoUntilException();
        } finally {
            try {
                if (undo != null) {
                    undo.initializeValidationData(MONITOR);
                    undo.perform(MONITOR);
                }
                assertOriginals();
                baseline.assertRestored();
            } catch (Throwable undoFailure) {
                if (failure != null) undoFailure.addSuppressed(failure);
                throw undoFailure;
            }
        }
        if (failure != null) throw failure;
    }

    private void assertOriginals() throws Exception {
        for (var unit : units()) {
            unit.makeConsistent(MONITOR);
            assertEquals(originals.get(unit), unit.getSource(), "Native Undo must restore the complete source");
            assertArrayEquals(originalBytes.get(unit), bytes(unit), "Native Undo must restore the exact file bytes");
        }
        assertEquals(compiler, java.getOptions(true));
        assertEquals(rawClasspath, List.of(java.getRawClasspath()));
        assertEquals(resolvedClasspath, List.of(java.getResolvedClasspath(true)));
    }

    private ICompilationUnit[] units() { return originals.keySet().toArray(ICompilationUnit[]::new); }

    private static byte[] bytes(ICompilationUnit unit) throws Exception {
        try (var input = ((IFile) unit.getResource()).getContents()) { return input.readAllBytes(); }
    }

    /** Observes the actual fixes without replacing any native change or edit. */
    private static final class ObservedMathematics implements ICleanUp {
        private final MathematicalCleanUpCore delegate;
        private final Map<String, MathematicalChange> changes = new LinkedHashMap<>();
        ObservedMathematics(MathematicalCleanUpCore delegate) { this.delegate = delegate; }
        @Override public void setOptions(CleanUpOptions options) { delegate.setOptions(options); }
        @Override public CleanUpRequirements getRequirements() { return delegate.getRequirements(); }
        @Override public String[] getStepDescriptions() { return delegate.getStepDescriptions(); }
        @Override public RefactoringStatus checkPreConditions(IJavaProject project, ICompilationUnit[] units,
                IProgressMonitor monitor) throws CoreException { return delegate.checkPreConditions(project, units, monitor); }
        @Override public RefactoringStatus checkPostConditions(IProgressMonitor monitor) throws CoreException {
            return delegate.checkPostConditions(monitor);
        }
        @Override public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
            var fix = delegate.createFix(context);
            if (fix == null) return null;
            return monitor -> {
                CompilationUnitChange change = fix.createChange(monitor);
                var math = assertInstanceOf(MathematicalChange.class, change);
                assertNull(changes.put(context.getCompilationUnit().getPrimary().getHandleIdentifier(), math));
                return change;
            };
        }
    }
}
