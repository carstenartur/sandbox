/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import javax.tools.ToolProvider;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.math.tests.StrictCompilerOptions;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

class JavaMontgomeryDotProductTest {
    @TempDir Path temporary;
    private static final String SOURCE = """
        public class Dot {
            private static int red(long a) {
                int low = (int)a * 58728449;
                return (int)((a - (long)low * 8380417) >> 32);
            }
            public static int dot(short[] a, short[] b) {
                int total = 0;
                for (int i = 0; i < 7; ++i) {
                    total += red((long)a[i] * b[i]);
                }
                return Math.floorMod(total, 8380417);
            }
        }
        """;

    @Test void defersSevenReductionsAndPreservesAllCanonicalResultsAndAbruptReads() throws Exception {
        var analysis = analyze(SOURCE, true, 2_000_000, false);
        assertTrue(verified(analysis), () -> analysis.diagnostics().toString());
        var receipt = analysis.evidence().stream().map(MathematicalAnalysis.VerifiedRegion::montgomeryDot)
                .filter(java.util.Objects::nonNull).findFirst().orElseThrow().certificate();
        assertEquals(7, receipt.sourceReductions());
        assertEquals(1, receipt.targetReductions());
        String generated = apply(SOURCE, analysis);
        assertTrue(generated.contains("return Math.floorMod(red(total), 8380417);"), generated);
        assertTrue(generated.contains("total += (long)a[i] * b[i];"), generated);
        assertEquals(execute(SOURCE), execute(generated));
        assertFalse(verified(analyze(generated, true, 2_000_000, false)));
    }

    @Test void readsAnotherModulusInverseBoundAndLocalBindingsFromSource() throws Exception {
        String original = SOURCE.replace("8380417", "3").replace("58728449", "0xAAAAAAAB")
                .replace("total", "result").replace("low", "word").replace("red(", "reduceTerm(").replace("i < 7", "i < 2");
        var result = analyze(original, true, 2_000_000, false);
        assertTrue(verified(result), () -> result.diagnostics().toString());
        assertEquals(execute(original), execute(apply(original, result)));
    }

    @Test void rejectsUnprovedObservationsRangesEffectsAndReducerVariants() {
        for (String source : List.of(
                SOURCE.replace("Math.floorMod(total, 8380417)", "total"),
                SOURCE.replace("Math.floorMod(total, 8380417)", "total % 8380417"),
                SOURCE.replace("58728449", "58728447"),
                SOURCE.replace("58728449", "2147483647").replace("8380417", "2147483647"),
                SOURCE.replace(">> 32", ">>> 32"),
                SOURCE.replace("int low", "System.nanoTime(); int low"),
                SOURCE.replace("static int red", "static synchronized int red"),
                SOURCE.replace("int total", "short total"),
                SOURCE.replace("short[]", "int[]"),
                SOURCE.replace("i < 7", "i < a.length"),
                SOURCE.replace("b[i]", "b[i]++"),
                SOURCE.replace("++i", "i += 2"),
                SOURCE.replace("Math.floorMod(total, 8380417)", "Math.floorMod(total, 3329)"),
                SOURCE.replace("Math.floorMod", "Fake.floorMod").replace("public class Dot", "class Fake { static int floorMod(int x,int q){return x;} } public class Dot"))) {
            assertFalse(verified(analyze(source, true, 2_000_000, false)), source);
        }
    }

    @Test void keepsTheFirstActiveUseOfARecursivelyInitializingDefaultMethodInterface() throws Exception {
        String source = SOURCE.replace("public class Dot {", "public interface Dot { int initialized = Inputs.fill();")
                .replace("public static int dot", "default int dot").replace("i < 7", "i < 2") + """
                class Inputs {
                    static short[] left = {0, 1};
                    static short[] right = {0, 0};
                    static int fill() { right[1] = 1; return 0; }
                }
                class Parent {
                    static int observed = new Child().dot(Inputs.left, Inputs.right);
                }
                class Child extends Parent implements Dot { }
                """;
        var analysis = analyze(source, true, 2_000_000, false);
        assertEquals(8265825, initializeChild(source));
        assertEquals(8265825, initializeChild(apply(source, analysis)));
        assertFalse(verified(analysis));
    }

    private int initializeChild(String source) throws Exception {
        try (var loader = compile(source)) {
            Class.forName("Child", true, loader);
            var field = loader.loadClass("Parent").getDeclaredField("observed");
            field.setAccessible(true);
            return field.getInt(null);
        }
    }

    @Test void requiresOptInAndBudgetAndHonorsCancellation() {
        assertFalse(verified(analyze(SOURCE, false, 2_000_000, false)));
        assertFalse(verified(analyze(SOURCE, true, 1, false)));
        assertFalse(verified(analyze(SOURCE, true, 2_000_000, true)));
    }

    @Test void combinesVerifiedSitesAndRejectsStaleSource() throws Exception {
        String second = SOURCE.substring(SOURCE.indexOf("    public static int dot"), SOURCE.lastIndexOf('}'))
                .replace("int dot(", "int another(").replace("i < 7", "i < 4");
        String source = SOURCE.substring(0, SOURCE.lastIndexOf('}')) + second + "}";
        var analysis = analyze(source, true, 2_000_000, false);
        assertEquals(2, analysis.diagnostics().stream().filter(d -> d.code().equals("VERIFIED_MONTGOMERY_DOT")).count());
        String generated = apply(source, analysis);
        assertEquals(2, generated.split("floorMod\\(red\\(total\\)", -1).length - 1, generated);
        var plan = analysis.evidence().getFirst().sourcePlan();
        assertThrows(IllegalArgumentException.class, () -> plan.newEdit(source.replace("58728449", "58728447")));
    }

    private static boolean verified(MathematicalAnalysis.Analysis result) {
        return result.diagnostics().stream().anyMatch(d -> d.code().equals("VERIFIED_MONTGOMERY_DOT"));
    }

    private static MathematicalAnalysis.Analysis analyze(String source, boolean consent, long budget, boolean cancel) {
        var compiler = StrictCompilerOptions.allWarnings(17);
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(source.toCharArray());
        parser.setUnitName("Dot.java");
        parser.setEnvironment(new String[0], new String[0], new String[0], true);
        parser.setResolveBindings(true);
        parser.setCompilerOptions(compiler);
        var ast = (CompilationUnit)parser.createAST(null);
        assertFalse(Arrays.stream(ast.getProblems()).anyMatch(IProblem::isError), () -> Arrays.toString(ast.getProblems()));
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.SHORT, NumericKind.INT, NumericKind.LONG), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, budget, 4096, false, 17, List.of(), consent,
                MathCleanUpOptions.ExplanationMode.NONE, 60);
        var environment = new MathematicalAnalysis.SourceEnvironment("Dot.java", List.of(), List.of(), List.of(), compiler);
        var monitor = new NullProgressMonitor();
        monitor.setCanceled(cancel);
        return MathematicalAnalysis.analyze(ast, source, options, monitor, -1, 0, environment);
    }

    private static String apply(String source, MathematicalAnalysis.Analysis analysis) throws Exception {
        var document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String result = document.get();
        undo.apply(document);
        assertEquals(source, document.get());
        return result;
    }

    private URLClassLoader compile(String source) throws Exception {
        Path dir = Files.createTempDirectory(temporary, "dot");
        Path file = Files.writeString(dir.resolve("Dot.java"), source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-Xlint:all", "-Werror", "--release", "17",
                "-d", dir.toString(), file.toString()));
        return new URLClassLoader(new URL[] { dir.toUri().toURL() }, ClassLoader.getPlatformClassLoader());
    }

    private List<Object> execute(String source) throws Exception {
        try (var loader = compile(source)) {
            var method = loader.loadClass("Dot").getMethod("dot", short[].class, short[].class);
            var random = new Random(90153);
            var outcomes = new ArrayList<Object>();
            short[] extremes = { Short.MIN_VALUE, Short.MAX_VALUE, -1, 0, 1, Short.MAX_VALUE, Short.MIN_VALUE };
            short[][] arrays = { extremes, new short[7], new short[1], new short[0], null };
            for (short[] left : arrays) for (short[] right : arrays) {
                try { outcomes.add(method.invoke(null, left, right)); }
                catch (InvocationTargetException failure) { outcomes.add(failure.getCause().getClass()); }
            }
            for (int n = 0; n < 1024; n++) {
                short[] a = new short[7], b = new short[7];
                for (int i = 0; i < 7; i++) {
                    a[i] = (short)random.nextInt();
                    b[i] = (short)random.nextInt();
                }
                outcomes.add(method.invoke(null, a, b));
            }
            return outcomes;
        }
    }
}
