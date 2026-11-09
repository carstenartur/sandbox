/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

class StrictCompilerOptionsTest {
    @Test void allWarningsReportsNlsAndUnusedParametersWithoutPerCaseOverrides() {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource("class Calculation { static String f(int unused) { return \"text\"; }}".toCharArray());
        parser.setUnitName("Calculation.java");
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setResolveBindings(true);
        parser.setCompilerOptions(StrictCompilerOptions.allWarnings(17));
        var problems = ((CompilationUnit) parser.createAST(null)).getProblems();
        for (int id : List.of(IProblem.ArgumentIsNeverUsed, IProblem.NonExternalizedStringLiteral)) {
            assertTrue(Arrays.stream(problems).anyMatch(problem -> problem.getID() == id && problem.isWarning()),
                    Arrays.toString(problems));
        }
    }

    @Test void strengtheningCannotDowngradeDefaultErrors() {
        var options = StrictCompilerOptions.strengthen(Map.of(JavaCore.COMPILER_PB_NULL_SPECIFICATION_VIOLATION, JavaCore.IGNORE));
        assertEquals(JavaCore.ERROR, options.get(JavaCore.COMPILER_PB_NULL_SPECIFICATION_VIOLATION));
    }

    @Test void inputParserReportsUnnecessaryCasts() {
        var ast = MathTestSupport.parse("class Calculation { int f(int x) { return (int)x; }}");
        assertTrue(Arrays.stream(ast.getProblems()).anyMatch(problem ->
                problem.getID() == IProblem.UnnecessaryCast && problem.isWarning()),
                Arrays.toString(ast.getProblems()));
    }

    @Test void analysisRetainsTheInputWarningProfile() {
        String source = "class Calculation { int f(int x) { return (x+1)-1; }}";
        var analysis = MathTestSupport.analyze(MathTestSupport.parse(source), source, options(), null, -1, 0);
        assertEquals(JavaCore.WARNING, analysis.compilerOptions().get(JavaCore.COMPILER_PB_UNNECESSARY_TYPE_CHECK));
        assertTrue(analysis.changed(), analysis.diagnostics().toString());
    }

    @Test void analysisDoesNotReplaceAnIntentionallyStaleInputAst() {
        String source = "class Calculation { int f(int x) { return (x+1)-1; }}";
        var ast = MathTestSupport.parse(source);
        var analysis = MathTestSupport.analyze(ast, source.replace("x+1", "x+2"), options(), null, -1, 0);
        assertFalse(analysis.changed());
        assertTrue(analysis.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("STALE_AST_SOURCE")),
                analysis.diagnostics().toString());
    }

    private static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true, Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.READABILITY, 200_000, 5_000, false, 17, List.of());
    }
}
