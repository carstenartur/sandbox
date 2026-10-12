/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** JDT flattens same-operator chains; their constant left prefix still belongs to the source formula. */
class RuntimeComputedPrefixTest {
    @ParameterizedTest
    @ValueSource(strings = {"3 * 2 * x", "2 + 3 + x", "12 - 4 - x", "-3 * 2 * x", "3 * 2 * x + y", "(3 * 2 * x) * y", "0xff & 0x7f & x"})
    void preservesComputedConstantPrefixesInsideRuntimeExpressions(String expression) {
        String source = source(expression);
        var ast = parse(source);
        var extraction = new JavaComputationExtractor().extractRuntime(ast, source, options());
        assertTrue(extraction.diagnostics().stream().anyMatch(d -> d.code().equals("CONSTANT_EXPRESSION_PRESERVED")),
                () -> "Missing constant-formula boundary for " + expression + ": " + extraction.diagnostics());
        var result = MathematicalAnalysis.analyze(ast, source, options(), new NullProgressMonitor(), -1, 0);
        assertFalse(result.changed(), () -> "Constant folding is not a runtime cleanup: " + result.replacements());
    }
    @ParameterizedTest
    @ValueSource(strings = {"x * -2", "x + 2 + 3", "3 * x * 2"})
    void retainsRuntimeChainsWithoutAConstantLeftPrefix(String expression) {
        String source = source(expression);
        var extraction = new JavaComputationExtractor().extractRuntime(parse(source), source, options());
        assertFalse(extraction.regions().isEmpty(), extraction.diagnostics().toString());
        assertFalse(extraction.diagnostics().stream().anyMatch(d -> d.code().equals("CONSTANT_EXPRESSION_PRESERVED")));
    }
    @Test void ordinaryBitwiseOptimizationRemainsAvailable() {
        String source = "class Sample { int f(int u, int v, int w) { return (u & v) | (~u & w); } }";
        var result = MathematicalAnalysis.analyze(parse(source), source, options(), new NullProgressMonitor(), -1, 0);
        assertTrue(result.changed(), result.diagnostics().toString());
    }
    private static String source(String expression) { return "class Sample { int f(int x, int y) { return " + expression + "; } }"; }
    private static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true, Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of());
    }
    private static CompilationUnit parse(String source) {
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setUnitName("Sample.java"); parser.setSource(source.toCharArray());
        parser.setEnvironment(new String[0], new String[0], null, true); parser.setResolveBindings(true);
        var settings = new HashMap<String, String>(); JavaCore.setComplianceOptions("17", settings); parser.setCompilerOptions(settings);
        var ast = (CompilationUnit) parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        return ast;
    }
}
