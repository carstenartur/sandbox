/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** Unknown runtime inputs, real extraction/search/emission, and compiled result comparison. */
class MathVariableRegionsTest {
    @TempDir Path temporary;

    @Test
    void directReturnExposesACommonFactorWithoutInventingLocalAliases() throws Exception {
        String source = "public class Calculation { public static int compute(int x,int a,int b) { return x*a+x*b; }}";
        String generated = rewrite(source, options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME));
        assertEquals(1, multiplicationCount(generated), generated);
        assertFalse(generated.contains("= x;"), generated);
        assertFalse(generated.contains("= a;"), generated);
        assertFalse(generated.contains("= b;"), generated);
        compare(source, generated);
    }

    @Test
    void internalProductsAreNotRequiredOutputsOfAMultilineRegion() throws Exception {
        String source = """
                public class Calculation {
                    public static int compute(int x,int a,int b) {
                        int first=x*a;
                        // Keep the user's explanation even when a temporary disappears.
                        int second=x*b;
                        int result=first+second;
                        return result;
                    }
                }
                """;
        String generated = rewrite(source, options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME));
        assertEquals(1, multiplicationCount(generated), generated);
        assertFalse(hasDeclaration(generated, "first"), generated);
        assertFalse(hasDeclaration(generated, "second"), generated);
        assertTrue(generated.contains("Keep the user's explanation"), generated);
        compare(source, generated);
        assertFalse(analyze(generated, options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME)).changed(), generated);
    }

    @Test
    void returnConversionDoesNotWidenTheOriginalIntegerMultiplications() throws Exception {
        String source = "public class Calculation { public static long compute(int x,int a,int b) { return x*a+x*b; }}";
        String generated = rewrite(source, options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME));
        assertEquals(1, multiplicationCount(generated), generated);
        compare(source, generated);
    }

    @Test
    void liveLocalUsedBeyondAnEffectBoundaryRemainsDeclared() throws Exception {
        String source = """
                public class Calculation {
                    static int observed;
                    static void consume(int value) { observed=value; }
                    public static int compute(int x,int a,int b) {
                        int first=(x+1)-1;
                        int result=first+0;
                        consume(first);
                        return result+observed;
                    }
                }
                """;
        String generated = rewrite(source, options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME));
        assertTrue(hasDeclaration(generated, "first"), generated);
        assertTrue(generated.contains("consume(first)"), generated);
        compare(source, generated);
    }

    @Test
    void checkedProfileStillDetectsAnEliminatedOriginalOverflow() throws Exception {
        String source = "public class Calculation { public static int compute(int x,int a,int b) { int intermediate=x+1; int result=intermediate-1; return result; }}";
        String generated = rewrite(source, options(SafetyProfile.CHECKED_THROW, OptimizationGoal.READABILITY));
        Method method = compile(generated);
        assertEquals(37, method.invoke(null, 37, 0, 0));
        var failure = assertThrows(InvocationTargetException.class, () -> method.invoke(null, Integer.MAX_VALUE, 0, 0));
        assertInstanceOf(ArithmeticException.class, failure.getCause());
    }

    @Test
    void fallbackRetainsWraparoundAtTheOriginalOverflowBoundary() throws Exception {
        String source = "public class Calculation { public static int compute(int x,int a,int b) { int intermediate=x+1; int result=intermediate-1; return result; }}";
        String generated = rewrite(source, options(SafetyProfile.GUARDED_FALLBACK, OptimizationGoal.READABILITY));
        compare(source, generated);
    }

    @Test
    void deadThrowingOperationCannotDisappearDuringLivenessProjection() {
        String source = "public class Calculation { public static int compute(int x,int a,int b) { int unused=x/0; return x*a+x*b; }}";
        var analysis = analyze(source, options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME));
        assertFalse(analysis.changed(), analysis.diagnostics().toString());
    }

    private String rewrite(String source, MathCleanUpOptions options) throws Exception {
        var analysis = analyze(source, options);
        assertTrue(analysis.changed(), analysis.diagnostics().toString());
        Document document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        undo.apply(document);
        assertEquals(source, document.get());
        return generated;
    }
    private static MathematicalAnalysis.Analysis analyze(String source, MathCleanUpOptions options) {
        return MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options, new NullProgressMonitor(), -1, 0);
    }
    private static MathCleanUpOptions options(SafetyProfile safety, OptimizationGoal goal) {
        return new MathCleanUpOptions(true, Set.of(NumericKind.INT, NumericKind.LONG), safety, goal,
                1_000_000L, 20_000, safety == SafetyProfile.CHECKED_THROW, 17, List.of());
    }
    private static int multiplicationCount(String source) {
        int[] count = {0};
        MathTestSupport.parse(source).accept(new ASTVisitor() {
            @Override public boolean visit(InfixExpression expression) {
                if (expression.getOperator() == InfixExpression.Operator.TIMES) count[0]++;
                return true;
            }
        });
        return count[0];
    }
    private static boolean hasDeclaration(String source, String name) {
        boolean[] found = {false};
        MathTestSupport.parse(source).accept(new ASTVisitor() {
            @Override public boolean visit(VariableDeclarationFragment declaration) {
                found[0] |= declaration.getName().getIdentifier().equals(name);
                return true;
            }
        });
        return found[0];
    }
    private void compare(String source, String generated) throws Exception {
        Method before = compile(source), after = compile(generated);
        int[] values = {0, 1, -1, 17, Integer.MIN_VALUE, Integer.MAX_VALUE, 1 << 30};
        for (int x : values) for (int a : values) for (int b : values)
            assertEquals(before.invoke(null, x, a, b), after.invoke(null, x, a, b), "x=" + x + ", a=" + a + ", b=" + b);
    }
    private Method compile(String source) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "compiled-");
        Path file = directory.resolve("Calculation.java");
        Files.writeString(file, source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-d", directory.toString(), file.toString()), source);
        try (var loader = new URLClassLoader(new java.net.URL[] {directory.toUri().toURL()}, null)) {
            return loader.loadClass("Calculation").getMethod("compute", int.class, int.class, int.class);
        }
    }
}
