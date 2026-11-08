/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

class DocumentedSourceSnapshotTest {
    private static final String SOURCE = """
            /** Runtime calculation. See {@link Integer}. */
            public class Calculation {
                /** @return the shared-factor computation */
                public static int compute(int x,int a,int b) {
                    return x*a+x*b;
                }
            }
            """;
    private static final MathCleanUpOptions OPTIONS = new MathCleanUpOptions(true,
            Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME,
            1_000_000L, 20_000, false, 8, List.of());

    @Test void structuredDocumentationIsNotAStaleSourceChange() {
        for (String documentation : List.of(JavaCore.ENABLED, JavaCore.DISABLED)) {
            var environment = environment(documentation);
            var ast = parse(SOURCE, environment);
            var analysis = analyze(ast, SOURCE, environment);
            assertTrue(analysis.changed(), documentation + ": " + analysis.diagnostics());
            assertFalse(analysis.diagnostics().stream().anyMatch(d -> d.code().equals("STALE_AST_SOURCE")));
        }
    }

    @Test void sameLengthCodeMutationAndShiftedOffsetsAreStillRejected() {
        for (String documentation : List.of(JavaCore.ENABLED, JavaCore.DISABLED)) {
            var environment = environment(documentation);
            for (String stale : List.of(SOURCE.replace("x*a+x*b", "x*a-x*b"), " " + SOURCE)) {
                var analysis = analyze(parse(SOURCE, environment), stale, environment);
                assertFalse(analysis.changed());
                assertTrue(analysis.diagnostics().stream().anyMatch(d -> d.code().equals("STALE_AST_SOURCE")),
                        analysis.diagnostics().toString());
            }
        }
    }

    private static MathematicalAnalysis.Analysis analyze(CompilationUnit ast, String source,
            MathematicalAnalysis.SourceEnvironment environment) {
        var analysis = MathematicalAnalysis.analyze(ast, source, OPTIONS, new NullProgressMonitor(), -1, 0, environment);
        assertEquals(environment.compilerOptions(), analysis.compilerOptions());
        return analysis;
    }
    private static MathematicalAnalysis.SourceEnvironment environment(String documentation) {
        var options = StrictCompilerOptions.options(8);
        options.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, documentation);
        return new MathematicalAnalysis.SourceEnvironment("Calculation.java", List.of(), List.of(), List.of(), options);
    }
    private static CompilationUnit parse(String source, MathematicalAnalysis.SourceEnvironment environment) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName("Calculation.java");
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setResolveBindings(true);
        parser.setCompilerOptions(environment.compilerOptions());
        CompilationUnit ast = (CompilationUnit) parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        return ast;
    }
}
