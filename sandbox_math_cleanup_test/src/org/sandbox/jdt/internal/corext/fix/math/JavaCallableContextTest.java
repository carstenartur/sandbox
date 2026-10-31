/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import javax.tools.ToolProvider;
import org.eclipse.jface.text.Document;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

class JavaCallableContextTest extends JavaContextTestSupport {
    @Test void directExpressionExtractionKeepsTheResolvedTypeBeforeSamWidening() {
        String source = "class Sample { java.util.function.IntToLongFunction make(){return x -> (x+1)-1;} }";
        var ast = JavaEarlyExitGuardFactsTest.parse(source);
        Expression[] body = {null};
        ast.accept(new ASTVisitor() {
            @Override public boolean visit(LambdaExpression lambda) { body[0] = (Expression)lambda.getBody(); return false; }
        });
        var extraction = new JavaComputationExtractor().extractRuntimeExpression(ast, source, body[0], options());
        assertEquals(1, extraction.regions().size(), extraction.diagnostics().toString());
        var region = extraction.regions().getFirst();
        assertEquals(NumericKind.INT, region.outputs().getFirst().declaredKind());
        assertEquals("(x+1)-1", source.substring(region.start(), region.start()+region.length()));
    }
    @ParameterizedTest @EnumSource(SafetyProfile.class)
    void newContextsRequireThePreserveJavaPolicy(SafetyProfile safety) {
        String source = "class Sample { java.util.function.IntUnaryOperator make(){return x -> (x+1)-1;} }";
        var options = new MathCleanUpOptions(true, EnumSet.allOf(NumericKind.class), safety,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, safety == SafetyProfile.CHECKED_THROW, 17, List.of());
        assertEquals(safety == SafetyProfile.PRESERVE_JAVA,
                MathematicalAnalysis.analyze(JavaEarlyExitGuardFactsTest.parse(source), source, options, null, -1, 0).changed());
    }

    @Test void blockLambdaReturnUsesItsSamInsteadOfTheEnclosingMethod() throws Exception {
        String source = "public class Sample { public static java.util.function.IntUnaryOperator make() {"
                + " return x -> { int y=x+1; /* retain */ return y-1; }; }"
                + " public static long run(int x) { return make().applyAsInt(x); }}";
        String generated = changed(source);
        assertTrue(generated.contains("/* retain */"));
        equivalent(source, generated);
    }
    @Test void expressionLambdaRemainsAnExpression() throws Exception {
        String source = "public class Sample { public static long run(int n) {"
                + " java.util.function.IntUnaryOperator f=x -> (x+1)-1; return f.applyAsInt(n); }}";
        String generated = changed(source);
        assertFalse(generated.contains("-> {"), generated);
        equivalent(source, generated);
    }
    @Test void fieldInitializerLambdaStillExecutesItsArithmeticOnInvocation() throws Exception {
        String source = "public class Sample { static java.util.function.IntUnaryOperator f=x -> (x+1)-1;"
                + " public static long run(int n){return f.applyAsInt(n);} }";
        equivalent(source, changed(source));
    }
    @Test void twoLambdaReplacementsKeepBothSamBindingsAfterTheirOffsetsMove() throws Exception {
        String source = "public class Sample { public static long run(int n){"
                + "java.util.function.IntUnaryOperator f=x -> (x+1)-1;"
                + "java.util.function.IntUnaryOperator g=y -> (y-1)+1; return g.applyAsInt(f.applyAsInt(n)); }}";
        assertEquals(2, analyze(source).replacements().size());
        equivalent(source, changed(source));
    }
    @Test void wideningOccursAfterTheIntLambdaExpression() throws Exception {
        String source = "public class Sample { public static long run(int n) {"
                + " java.util.function.IntToLongFunction f=x -> (x+1)-1; return f.applyAsLong(n); }}";
        equivalent(source, changed(source));
    }
    @Test void narrowingReturnRetainsTheSamType() throws Exception {
        String source = "public class Sample { interface Narrow { byte apply(int x); }"
                + " public static long run(int n) { Narrow f=x -> { return (byte)((x+1)-1); }; return f.apply(n); }}";
        equivalent(source, changed(source));
    }
    @Test void nestedLambdasUseTheirOwnParametersAndReturnTypes() throws Exception {
        String source = "public class Sample { public static long run(int n) {"
                + " java.util.function.IntFunction<java.util.function.IntUnaryOperator> f=a -> b -> ((a+b)+1)-1;"
                + " return f.apply(n).applyAsInt(7); }}";
        equivalent(source, changed(source));
    }
    @Test void twoArgumentReducerStaysInsideItsCallable() throws Exception {
        String source = "public class Sample { public static long run(int n) {"
                + " java.util.function.IntBinaryOperator f=(a,b) -> ((a+b)+1)-1; return f.applyAsInt(n,7); }}";
        equivalent(source, changed(source));
    }
    @Test void longCallableUsesLongArithmetic() throws Exception {
        String source = "public class Sample { public static long run(int n) {"
                + " java.util.function.LongUnaryOperator f=x -> (x+1L)-1L; return f.applyAsLong(((long)n)<<32); }}";
        equivalent(source, changed(source, "(x+1L)-1L"));
    }
    @Test void unknownMethodReferencesRemainUnchanged() {
        String source = "class Sample { java.util.function.IntUnaryOperator make(){ return Math::abs; }}";
        assertFalse(analyze(source).changed());
    }
    @Test void blockLambdaKeepsDeferredFieldReadsInsideInvocation() throws Exception {
        String source = "public class Sample { final int value; Sample(int value){this.value=value;}"
                + " public static long run(int n) { java.util.function.IntUnaryOperator f=x -> {"
                + " int a=newHolder.value; return a+((x+1)-1)+((x+2)-2)+((x+3)-3); }; newHolder=new Sample(n); return f.applyAsInt(7); }"
                + " static Sample newHolder; }";
        String staticGenerated = changed(source);
        assertTrue(staticGenerated.indexOf("newHolder.value") > staticGenerated.indexOf("-> {"), staticGenerated);
        equivalent(source, staticGenerated);
        String supported = source.replace("newHolder.value", "holder.value").replace("java.util.function.IntUnaryOperator f=",
                "Sample holder=new Sample(n); java.util.function.IntUnaryOperator f=");
        String generated = changed(supported);
        assertTrue(generated.indexOf("holder.value") > generated.indexOf("-> {"), generated);
        equivalent(supported, generated);
    }
    @Test void expressionLambdaDoesNotMoveRequiredCapturesOutsideTheBody() {
        String source = "class Sample { final int value; Sample(int x){value=x;}"
                + " java.util.function.IntUnaryOperator make(){ return x -> (value+x)+0; }}";
        assertFalse(analyze(source).changed());
    }
    @Test void statementExpressionLambdaKeepsOverloadSelection() {
        String source = "class Sample { private static int id(int x){return x;}"
                + " static int choose(java.util.function.IntUnaryOperator f){return 1;}"
                + " static int choose(java.util.function.IntConsumer f){return 2;}"
                + " int run(){return choose((java.util.function.IntUnaryOperator) (x -> id((x+1)-1)));}}";
        String generated = changed(source);
        assertFalse(generated.contains("-> {"), generated);
        assertTrue(generated.contains("IntUnaryOperator"));
    }
    @Test void mathematicalOptInDoesNotApplyAnEnclosingMethodContractToALambda() {
        String source = "import java.math.BigInteger; class Sample { java.util.function.UnaryOperator<BigInteger> make(){"
                + " return x -> { return x.modPow(BigInteger.TWO, BigInteger.valueOf(17)); }; }}";
        var options = new MathCleanUpOptions(true, EnumSet.allOf(NumericKind.class), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of(), true);
        assertFalse(MathematicalAnalysis.analyze(JavaEarlyExitGuardFactsTest.parse(source), source, options, null, -1, 0).changed());
    }
}

abstract class JavaContextTestSupport {
    @TempDir Path temporary;
    static MathematicalAnalysis.Analysis analyze(String source) {
        return MathematicalAnalysis.analyze(JavaEarlyExitGuardFactsTest.parse(source), source, options(), null, -1, 0);
    }
    static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true, EnumSet.allOf(NumericKind.class), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of());
    }
    static String changed(String source) {
        return changed(source, null);
    }
    static String changed(String source, String anchor) {
        var analysis = analyze(source);
        assertTrue(analysis.changed(), analysis.diagnostics().toString());
        if (anchor != null) assertTrue(analysis.evidence().stream().anyMatch(e -> e.original().equals(anchor)),
                analysis.evidence().toString());
        var document = new Document(source);
        try {
            var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
            String generated = document.get();
            JavaEarlyExitGuardFactsTest.parse(generated);
            undo.apply(document); assertEquals(source, document.get());
            return generated;
        } catch (Exception failure) { throw new AssertionError(failure); }
    }
    void equivalent(String source, String generated) throws Exception {
        try (var before = compile(source); var after = compile(generated)) {
            var original = before.loadClass("Sample").getMethod("run", int.class);
            var optimized = after.loadClass("Sample").getMethod("run", int.class);
            for (int input : new int[]{Integer.MIN_VALUE, -129, -1, 0, 1, 127, 128, Integer.MAX_VALUE})
                assertEquals(original.invoke(null, input), optimized.invoke(null, input), "input=" + input);
        }
    }
    private URLClassLoader compile(String source) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "compiled");
        Path file = directory.resolve("Sample.java"); Files.writeString(file, source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-d", directory.toString(), file.toString()));
        return new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
}
