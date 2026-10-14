/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.UUID;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** Exercises the actual project-source reader and the LTK preview/apply boundary. */
class MathematicalSourceContextWorkspaceTest {
    @Test void projectDefinitionsSupportPreviewUndoAndDependencyOnlyReanalysis() throws Exception {
        var monitor = new NullProgressMonitor();
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("MathSourceContext-" + UUID.randomUUID());
        try {
            project.create(monitor); project.open(monitor);
            var description = project.getDescription(); description.setNatureIds(new String[] { JavaCore.NATURE_ID });
            project.setDescription(description, monitor);
            var folder = project.getFolder("src"); folder.create(true, true, monitor);
            var bin = project.getFolder("bin"); bin.create(true, true, monitor);
            var java = JavaCore.create(project);
            java.setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(folder.getFullPath()),
                JavaRuntime.getDefaultJREContainerEntry() }, bin.getFullPath(), monitor);
            var compiler = java.getOptions(false); JavaCore.setComplianceOptions("17", compiler); java.setOptions(compiler);
            var pack = java.getPackageFragmentRoot(folder).createPackageFragment("sample", true, monitor);
            var definition = pack.createCompilationUnit("Constants.java", """
                package sample;
                import java.math.BigInteger;
                class Constants { static final BigInteger Q = new BigInteger("1fffffff", 16); }
                """, true, monitor);
            pack.createCompilationUnit("Bridge.java", """
                package sample;
                import java.math.BigInteger;
                class Bridge { static final BigInteger P = Constants.Q; }
                """, true, monitor);
            String source = """
                package sample;
                import java.math.BigInteger;
                class Sample {
                    private static final BigInteger P = Bridge.P;
                    BigInteger calculate(byte digit) {
                        byte[] magnitude = new byte[48];
                        magnitude[47] = digit;
                        BigInteger x = new BigInteger(1, magnitude);
                        if (x.compareTo(P) >= 0) throw new IllegalArgumentException("range");
                        BigInteger result = x.multiply(x).mod(P).multiply(x).mod(P)
                            .add(BigInteger.valueOf(4)).mod(P);
                        return result;
                    }
                }
                """;
            var unit = pack.createCompilationUnit("Sample.java", source, true, monitor);
            var settings = new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
            settings.put(MathCleanUpOptions.CLEANUP, "true"); settings.put(MathCleanUpOptions.KINDS, "BIG_INTEGER,INT");
            settings.put(MathCleanUpOptions.WORK_BUDGET, "1000000"); settings.put(MathCleanUpOptions.MAX_STATES, "20000");
            settings.put(MathCleanUpOptions.MATHEMATICAL_OPT_IN, "true"); settings.put(MathCleanUpOptions.EXPLANATIONS, "NONE");
            var options = MathCleanUpOptions.parse(settings, 17);
            var result = analyze(unit, source, options, monitor);
            assertEquals(1, result.replacements().size(), result.diagnostics().toString());
            assertEquals(2, result.sourceDependencies().receipts().size());
            String replacement = result.replacements().getFirst().replacement();
            assertFalse(replacement.contains("getClass()"), replacement);
            assertTrue(replacement.contains("BigInteger.valueOf(4)"), replacement);
            var environment = MathematicalEnvironment.capture(java, monitor);
            var change = new MathematicalChange("Source-backed mathematics", unit, result, options, () -> options, environment);
            change.initializeValidationData(monitor);
            assertFalse(change.isValid(monitor).hasFatalError());
            String preview = change.getPreviewContent(monitor);
            assertTrue(preview.contains("x.compareTo(P)"));
            var copied = change.getEdit().copy();
            var undo = change.perform(monitor); assertEquals(preview, unit.getSource());
            assertNotNull(undo); undo.perform(monitor); assertEquals(source, unit.getSource());
            definition.getBuffer().setContents(definition.getSource().replace("1fffffff", "1ffffffd"));
            definition.save(monitor, true);
            assertFalse(result.matches(source, java.getOptions(true)));
            assertFalse(new MathematicalQuickAssist.CachedAnalysis(result, environment)
                .matches(environment, source, java.getOptions(true)));
            var document = new Document(source);
            assertThrows(MalformedTreeException.class, () -> copied.apply(document));
            assertEquals(source, document.get());
            var fresh = analyze(unit, source, options, monitor);
            assertTrue(fresh.changed(), fresh.diagnostics().toString());
            assertTrue(fresh.matches(source, java.getOptions(true)));
            assertNotEquals(result.sourceDependencyReceipts(), fresh.sourceDependencyReceipts());
        } finally { if (project.exists()) project.delete(true, true, monitor); }
    }
    private static MathematicalAnalysis.Analysis analyze(ICompilationUnit unit, String source, MathCleanUpOptions options,
            NullProgressMonitor monitor) {
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setSource(unit); parser.setResolveBindings(true);
        var ast = (CompilationUnit)parser.createAST(monitor);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        int start = source.indexOf("BigInteger result");
        return MathematicalAnalysis.analyze(ast, source, options, monitor, start, source.indexOf(';', start) + 1 - start);
    }
}
