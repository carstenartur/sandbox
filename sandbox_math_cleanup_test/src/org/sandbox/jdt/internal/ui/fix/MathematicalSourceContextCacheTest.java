/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Exercises the worker's cache decision with actual source-bound analysis, without a fake optimizer. */
class MathematicalSourceContextCacheTest {
    @TempDir Path directory;
    private static final String SOURCE = """
        package sample;
        import java.math.BigInteger;
        class Sample {
            private static final BigInteger P = Constants.Q;
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
    private static final MathematicalEnvironment.Snapshot ENVIRONMENT =
        new MathematicalEnvironment.Snapshot("unchanged-binding-environment", Map.of());

    @Test void unchangedSourcesRemainReusable() throws Exception {
        var cached = cached();
        assertTrue(cached.matches(ENVIRONMENT, SOURCE, Map.of()));
    }
    @Test void dependencyInitializerChangeRequiresNewAnalysisEvenBeforeAJdtDelta() throws Exception {
        var cached = cached();
        Path definition = directory.resolve("sample/Constants.java");
        Files.writeString(definition, Files.readString(definition).replace("1fffffff", "1ffffffd"),
				StandardCharsets.UTF_8);
        assertFalse(cached.matches(ENVIRONMENT, SOURCE, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> cached.analysis().newEdit());
    }
    @Test void removedDependencyRequiresNewAnalysis() throws Exception {
        var cached = cached();
        Files.delete(directory.resolve("sample/Constants.java"));
        assertFalse(cached.matches(ENVIRONMENT, SOURCE, Map.of()));
    }
    @Test void sourceAndCompilerChangesCannotReuseACachedResult() throws Exception {
        var cached = cached();
        assertFalse(cached.matches(ENVIRONMENT, SOURCE + "\n", Map.of()));
        assertFalse(cached.matches(ENVIRONMENT, SOURCE, Map.of(JavaCore.COMPILER_SOURCE, "21")));
    }
    @Test void changedBindingEnvironmentStillRequiresNewAnalysis() throws Exception {
        var cached = cached();
        assertFalse(cached.matches(new MathematicalEnvironment.Snapshot("different", Map.of()), SOURCE, Map.of()));
    }
    private MathematicalQuickAssist.CachedAnalysis cached() throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sample"));
        Files.writeString(sources.resolve("Sample.java"), SOURCE, StandardCharsets.UTF_8);
        Files.writeString(sources.resolve("Constants.java"), """
				package sample;
				import java.math.BigInteger;
				class Constants { static final BigInteger Q = new BigInteger("1fffffff", 16); }
				""", StandardCharsets.UTF_8);
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(SOURCE.toCharArray()); parser.setUnitName("sample/Sample.java");
        parser.setEnvironment(new String[0], new String[] { directory.toString() }, new String[] { "UTF-8" }, true);
        parser.setResolveBindings(true);
        var settings = new HashMap<String,String>(); JavaCore.setComplianceOptions("17", settings); parser.setCompilerOptions(settings);
        var ast = (CompilationUnit)parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.BIG_INTEGER, NumericKind.INT),
            SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000L, 20_000, false, 17, List.of(), true);
        int start = SOURCE.indexOf("BigInteger result");
        var analysis = MathematicalAnalysis.analyze(ast, SOURCE, options, new NullProgressMonitor(), start,
            SOURCE.indexOf(';', start) + 1 - start,
            new MathematicalAnalysis.SourceEnvironment("sample/Sample.java", List.of(), List.of(directory.toString()), List.of("UTF-8")));
        assertTrue(analysis.changed(), analysis.diagnostics().toString());
        assertEquals(1, analysis.sourceDependencies().receipts().size());
        assertTrue(analysis.matches(SOURCE, Map.of()));
        return new MathematicalQuickAssist.CachedAnalysis(analysis, ENVIRONMENT);
    }
}
