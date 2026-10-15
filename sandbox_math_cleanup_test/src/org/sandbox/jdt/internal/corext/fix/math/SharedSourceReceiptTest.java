/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameRequestor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.launching.JavaRuntime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Actual project compilation-unit identities, not one-file-per-type source-path substitutes. */
class SharedSourceReceiptTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void twoTopLevelTypesMustUseTheSameCompilationUnitVersion(boolean changeBetweenReads) throws Exception {
        var monitor = new NullProgressMonitor();
        var project = ResourcesPlugin.getWorkspace().getRoot().getProject("SharedMathSource-" + UUID.randomUUID());
        try {
            project.create(monitor); project.open(monitor);
            var description = project.getDescription(); description.setNatureIds(new String[] {JavaCore.NATURE_ID});
            project.setDescription(description, monitor);
            var folder = project.getFolder("src"); folder.create(true, true, monitor);
            var bin = project.getFolder("bin"); bin.create(true, true, monitor);
            var java = JavaCore.create(project);
            java.setRawClasspath(new IClasspathEntry[] {JavaCore.newSourceEntry(folder.getFullPath()),
                    JavaRuntime.getDefaultJREContainerEntry()}, bin.getFullPath(), monitor);
            var compiler = java.getOptions(false); JavaCore.setComplianceOptions("17", compiler); java.setOptions(compiler);
            var pack = java.getPackageFragmentRoot(folder).createPackageFragment("sample", true, monitor);
            var shared = pack.createCompilationUnit("Shared.java", """
                package sample;
                import java.math.BigInteger;
                class First { static final BigInteger VALUE = BigInteger.valueOf(17); }
                class Second { static final BigInteger VALUE = BigInteger.valueOf(19); }
                """, true, monitor);
            String source = """
                package sample;
                import java.math.BigInteger;
                class Sample {
                    static final BigInteger P = First.VALUE;
                    static final BigInteger Q = Second.VALUE;
                    BigInteger first() { P.signum(); return P.add(BigInteger.ONE); }
                    BigInteger second() { Q.signum(); return Q.add(BigInteger.ONE); }
                }
                """;
            var caller = pack.createCompilationUnit("Sample.java", source, true, monitor);
            // Secondary top-level types are resolved through the project index, not by filename.
            new SearchEngine().searchAllTypeNames("sample".toCharArray(), SearchPattern.R_EXACT_MATCH, null,
                    SearchPattern.R_EXACT_MATCH, IJavaSearchConstants.TYPE,
                    SearchEngine.createJavaSearchScope(new IJavaElement[] {java}), new TypeNameRequestor() {},
                    IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, monitor);
            var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setSource(caller); parser.setResolveBindings(true);
            var ast = (CompilationUnit) parser.createAST(monitor);
            for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
            var options = new MathCleanUpOptions(true, Set.of(NumericKind.BIG_INTEGER), SafetyProfile.PRESERVE_JAVA,
                    OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of(), true);
            try (var context = JavaSourceContext.open(ast, source, null, monitor, options)) {
                var first = JavaSourceContext.proveReceivers(ast, region(source, "P"), Map.of("P", false));
                assertTrue(first.containsKey("P"), "The first source definition must resolve");
                assertEquals(BigInteger.valueOf(17), first.get("P").value());
                var receipts = context.checkpoint().receipts();
                assertEquals(1, receipts.size());
                assertEquals(shared.getHandleIdentifier(), receipts.getFirst().identity());
                if (changeBetweenReads) {
                    shared.getBuffer().setContents(shared.getSource().replace("valueOf(17)", "valueOf(23)")
                            .replace("valueOf(19)", "valueOf(29)"));
                    shared.save(monitor, true);
                }
                var second = JavaSourceContext.proveReceivers(ast, region(source, "Q"), Map.of("Q", false));
                assertEquals(receipts, context.checkpoint().receipts(), "The first source receipt must never be overwritten");
                assertEquals(!changeBetweenReads, context.checkpoint().current());
                if (changeBetweenReads) {
                    assertTrue(second.isEmpty(), "Mixed-version evidence must be rejected");
                    assertThrows(IllegalArgumentException.class, () -> context.checkpoint().requireCurrent());
                } else assertEquals(BigInteger.valueOf(19), second.get("Q").value());
            }
        } finally { if (project.exists()) project.delete(true, true, monitor); }
    }
    private static JavaComputationRegion region(String source, String receiver) {
        int start = source.indexOf("return " + receiver + ".add(");
        int length = source.indexOf(';', start) + 1 - start;
        // Only the source fact reader is under test; no numerical plan is fabricated or optimized.
        return new JavaComputationRegion(null, null, List.of(), Map.of(), Set.of(), Set.of(), Set.of(), start, length);
    }
}
