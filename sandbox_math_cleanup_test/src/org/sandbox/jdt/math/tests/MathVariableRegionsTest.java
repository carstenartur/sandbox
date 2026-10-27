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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(strings = { "int", "var" })
    void overwrittenVersionsDoNotHideFactoringAndTheEscapingLocalRemainsDeclared(String type) throws Exception {
        String source = observedSource(type + " result=x*a; result=result+x*b; consume(result);");
        String generated = rewrite(source, options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME));
        assertEquals(1, multiplicationCount(generated), generated);
        assertTrue(hasDeclaration(generated, "result"), generated);
        compare(source, generated);
    }

    @Test
    void separateIncomingAssignmentsAreNotCombined() {
        String source = observedSource("a=x*a; a=a+x*b; consume(a);");
        assertFalse(analyze(source, options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME)).changed());
    }

    @Test
    void anIncomingParameterAssignmentExposesItsOwnCommonFactor() throws Exception {
        String source = observedSource("a=x*a+x*b; consume(a);");
        String generated = rewrite(source, options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME));
        assertEquals(1, multiplicationCount(generated), generated);
        assertFalse(hasDeclaration(generated, "a"), generated);
        compare(source, generated);
    }

    @Test
    void anAssignmentInsideABranchPreservesTheOuterLocalAndWraparound() throws Exception {
        String source = observedSource("int result=a; if (x != 0) { result=(a-x)-(b-x); } consume(result);");
        String generated = rewrite(source, options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME));
        assertTrue(hasDeclaration(generated, "result"), generated);
        assertFalse(generated.contains("(a-x)-(b-x)"), generated);
        compare(source, generated);
    }

    @ParameterizedTest
    @ValueSource(strings = { "byte", "short", "char", "int", "long" })
    void incomingIntegralAssignmentsPreserveTheirConversion(String type) throws Exception {
        String source = observedSource(type + " result; result=(" + type + ")(x*a+x*b); consume((int)result);");
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.BYTE, NumericKind.SHORT, NumericKind.CHAR,
                NumericKind.INT, NumericKind.LONG), SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME,
                1_000_000L, 20_000, false, 17, List.of());
        String generated = rewrite(source, options);
        assertEquals(1, multiplicationCount(generated), generated);
        compare(source, generated);
    }

    @Test
    void anAssignmentCannotMoveAcrossAnObservedWriteOrAThrow() throws Exception {
        String source = observedSource("a=x*a+x*b; consume(a); a=x/0; consume(a);");
        String generated = rewrite(source, options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME));
        assertTrue(generated.contains("a=x/0;"), generated);
        for (String variant : List.of(source, generated)) {
            Method method = compile(variant);
            var failure = assertThrows(InvocationTargetException.class, () -> method.invoke(null, 17, 2, 3));
            assertInstanceOf(ArithmeticException.class, failure.getCause());
            var observed = method.getDeclaringClass().getDeclaredField("observed");
            observed.setAccessible(true);
            assertEquals(85, observed.getInt(null));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "observed=x*a+x*b;", "observed+=x*a+x*b;" })
    void fieldWriteTargetsRemainBoundaries(String statement) {
        var analysis = analyze(observedSource(statement),
                options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME));
        assertFalse(analysis.changed(), analysis.diagnostics().toString());
    }

    @Test
    void anIncomingCompoundAssignmentExposesItsOwnCommonFactor() throws Exception {
        String source = observedSource("a+=x*a+x*b; consume(a);");
        String generated = rewrite(source, options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME));
        assertEquals(1, multiplicationCount(generated), generated);
        assertFalse(hasDeclaration(generated, "a"), generated);
        compare(source, generated);
    }

    @Test
    void incomingPrimitiveAssignmentsDoNotBroadenOtherSafetyProfiles() {
        String source = observedSource("a=(x+1)-1; consume(a);");
        for (var safety : List.of(SafetyProfile.CHECKED_THROW, SafetyProfile.GUARDED_FALLBACK)) {
            assertFalse(analyze(source, options(safety, OptimizationGoal.READABILITY)).changed());
        }
    }

    @Test
    void expandingAnIncomingHelperCallAloneIsNotAUsefulRuntimeRewrite() {
        String source = observedSource("a=rotate(x,8); consume(a);").replace("static int observed;",
                "private static int rotate(int value,int distance) { return (value >>> distance) | (value << -distance); } static int observed;");
        assertFalse(analyze(source, options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME)).changed());
    }

    @Test
    void sharingOnlyImplicitPromotionsDoesNotJustifyNewLocals() {
        String source = observedSource("short result=(short)x; consume(result); "
                + "result=(short)(((result&0x00ff)<<8)|((result&0xff00)>>8)); consume(result);");
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.SHORT, NumericKind.INT),
                SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000L, 20_000, false, 17, List.of());
        assertFalse(analyze(source, options).changed());
    }

    @Test
    void newIncomingRegionsDoNotDiluteTheEstablishedSearchBudget() {
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 20_000L, 20_000, false, 17, List.of());
        String source = "class Calculation { int compute(int x,int a,int b) { return x*a+x*b; } $NOISE }";
        assertTrue(analyze(source.replace("$NOISE", ""), options).changed(), "Qualify the baseline at this same budget");
        String withNoise = source.replace("$NOISE", "void noise(int x) { " + "x=x+1; ".repeat(100) + "}");
        var result = analyze(withNoise, options);
        assertTrue(result.evidence().stream().anyMatch(e -> e.original().equals("return x*a+x*b;")),
                "New incoming assignments must use remaining work after the established regions");
    }

    @Test
    void preparationRetriesPreserveTheIncomingValueAfterAnAssignment() throws Exception {
        String incoming = "value=(value+1)+0;";
        String source = "public class Calculation { public static int compute(int x,int a,int b) {"
                + "int value=x+1; int c=" + balancedSum(0, 1024) + "; int d=x+3; int e=x+4;"
                + incoming + "int result=value-1; return result+c+d+e; }}";
        var analysis = analyze(source, options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME));
        assertTrue(analysis.diagnostics().stream().anyMatch(d -> d.code().equals("REGION_PREPARATION_SPLIT")),
                analysis.diagnostics().toString());
        assertTrue(analysis.diagnostics().stream().anyMatch(d -> d.code().equals("VERIFIED_CANDIDATE")
                && d.offset() == source.indexOf(incoming)),
                "The incoming retry must produce an edit before checking execution and Undo: " + analysis.diagnostics());
        Document document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        compare(source, document.get());
        undo.apply(document); assertEquals(source, document.get());
    }

    private static String balancedSum(int first, int count) {
        if (count == 1) return "(x+" + first + ")";
        return "(" + balancedSum(first, count / 2) + "+" + balancedSum(first + count / 2, count - count / 2) + ")";
    }

    @Test
    void incomingAssignmentsDiscoveredByRetriesAlsoWaitForEstablishedRegions() {
        String incoming = "value=(value-x)-(b-x);";
        String existing = "return x*a+x*b;";
        String source = "class Calculation { int compute(int x,int a,int b) { int value=x+1; int c="
                + balancedSum(0, 1024) + "; int d=x+3; int e=x+4;" + incoming
                + "int result=value-1; return result+c+d+e; } int established(int x,int a,int b) {" + existing + "}}";
        var analysis = analyze(source, options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME));
        assertTrue(analysis.diagnostics().stream().anyMatch(d -> d.code().equals("REGION_PREPARATION_SPLIT")),
                analysis.diagnostics().toString());
        var acceptedOffsets = analysis.diagnostics().stream().filter(d -> d.code().equals("VERIFIED_CANDIDATE"))
                .map(d -> d.offset()).toList();
        assertTrue(acceptedOffsets.contains(source.indexOf(incoming)), acceptedOffsets.toString());
        assertTrue(acceptedOffsets.contains(source.indexOf(existing)), acceptedOffsets.toString());
        assertTrue(acceptedOffsets.indexOf(source.indexOf(existing)) < acceptedOffsets.indexOf(source.indexOf(incoming)),
                "Incoming retry regions must not consume established regions' work shares: " + acceptedOffsets);
    }

    @Test
    void overwrittenThrowingEvaluationCannotDisappear() {
        String source = observedSource("int result=x/0; result=x*a+x*b; consume(result);");
        assertFalse(analyze(source, options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME)).changed());
    }

    @Test
    void overwrittenOverflowStillThrowsInTheCheckedProfile() throws Exception {
        String source = observedSource("int result=x+1; result=x*a+x*b; consume(result);");
        String generated = rewrite(source, options(SafetyProfile.CHECKED_THROW, OptimizationGoal.READABILITY));
        Method method = compile(generated);
        assertEquals(85, method.invoke(null, 17, 2, 3));
        var failure = assertThrows(InvocationTargetException.class, () -> method.invoke(null, Integer.MAX_VALUE, 0, 0));
        assertInstanceOf(ArithmeticException.class, failure.getCause());
    }

    @Test
    void anEmbeddedCommentPinsTheOverwrittenDeclaration() throws Exception {
        String source = observedSource("int result=x/* keep this explanation */*a; result=result+x*b; consume(result);");
        var analysis = analyze(source, options(SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME));
        Document document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        assertTrue(document.get().contains("/* keep this explanation */"));
        compare(source, document.get());
        undo.apply(document);
        assertEquals(source, document.get());
    }

    private static String observedSource(String body) {
        return """
                public class Calculation {
                    static int observed;
                    static void consume(int value) { observed=value; }
                    public static int compute(int x,int a,int b) {
                        $BODY
                        return observed;
                    }
                }
                """.replace("$BODY", body);
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
