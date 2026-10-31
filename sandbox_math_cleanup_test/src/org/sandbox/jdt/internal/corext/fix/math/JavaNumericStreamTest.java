/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavaNumericStreamTest extends JavaContextTestSupport {
    @ParameterizedTest @ValueSource(strings={"sum()", "reduce(7, Integer::sum)", "reduce(Integer::sum).orElse(9)", "min().orElse(9)", "max().orElse(9)"})
    void intMapsComposeWithoutChangingTheTerminal(String terminal) throws Exception {
        String source = sample("java.util.stream.IntStream.of(n, n+1).map(x -> x+17).map(y -> y-17)."+terminal);
        String generated = changed(source);
        assertEquals(1, generated.split("\\.map\\(", -1).length-1, generated);
        assertTrue(generated.contains("."+terminal), generated);
        assertTrue(analyze(source).replacements().getFirst().description().contains("per-element"));
        equivalent(source, generated);
    }
    @Test void longMapsKeepLongOverflowAndCapturedBindings() throws Exception {
        String source = sample("java.util.stream.LongStream.of(((long)n)<<32).map(x -> x+k).map(y -> y-k).sum()")
                .replace("return java", "long k=Long.MAX_VALUE; return java");
        String generated = changed(source);
        assertEquals(1, generated.split("\\.map\\(", -1).length-1, generated);
        equivalent(source, generated);
    }
    @Test void emptyPipelineKeepsTheIdentityAndOptionalBehavior() throws Exception {
        String source=sample("java.util.stream.IntStream.empty().map(x -> x+n).map(y -> y-n).min().orElse(91)");
        equivalent(source, changed(source));
    }
    @Test void capturedNameMatchingTheOtherParameterDoesNotBecomeCaptured() throws Exception {
        String source=sample("java.util.stream.IntStream.of(n).map(x -> x+y).map(z -> z-y).sum()")
                .replace("return java", "int y=n; return java");
        equivalent(source, changed(source));
    }
    @ParameterizedTest @ValueSource(strings={"filter(x -> x>0)","peek(x -> System.out.print(x))", "parallel()", "unordered()"})
    void barriersDoNotAuthorizeTopologyChanges(String barrier) {
        String source=sample("java.util.stream.IntStream.of(n).map(x -> x+17)."+barrier+".map(y -> y-17).sum()");
        assertFalse(analyze(source).changed(), analyze(source).diagnostics().toString());
    }
    @Test void arbitraryStreamReceiverCannotLoseObservableMapCalls() {
        String source="class Sample { int run(java.util.stream.IntStream custom){return custom.map(x -> x+17).map(y -> y-17).sum();}}";
        assertFalse(analyze(source).changed());
    }
    @Test void customIntStreamProxyRetainsBothObservableMapInvocations() throws Exception {
        String source="public class Sample { public static long run(int n){ int[] calls={0};"
                +"java.util.stream.IntStream custom=(java.util.stream.IntStream)java.lang.reflect.Proxy.newProxyInstance("
                +"Sample.class.getClassLoader(), new Class<?>[]{java.util.stream.IntStream.class}, (proxy,method,args)->{"
                +"if(method.getName().equals(\"map\")){calls[0]++;return proxy;}"
                +"if(method.getName().equals(\"sum\"))return calls[0];throw new UnsupportedOperationException();});"
                +"return custom.map(x -> x+17).map(y -> y-17).sum();}}";
        assertFalse(analyze(source).changed());
        try(var compiled=compile(source)) { assertEquals(2L,compiled.loadClass("Sample").getMethod("run",int.class).invoke(null,0)); }
    }
    @Test void sameParameterSpellingHasDistinctResolvedBindings() throws Exception {
        String source=sample("java.util.stream.IntStream.of(n).map(x -> x+17).map(x -> x-17).sum()");
        equivalent(source,changed(source));
    }
    @Test void twoPipelineEditsRetainBindingsAfterOffsetsMove() throws Exception {
        String source=sample("java.util.stream.IntStream.of(n).map(x -> x+17).map(x -> x-17).sum()"
                +"+java.util.stream.IntStream.of(n).map(y -> y+19).map(y -> y-19).sum()");
        assertEquals(2,analyze(source).replacements().size(), analyze(source).diagnostics().toString());
        equivalent(source,changed(source));
    }
    @Test void scalarPredicateAfterFusedPipelineKeepsItsTargetDespiteLambdaRenumbering() throws Exception {
        String source="public class Sample { public static long run(int n){int a=java.util.stream.IntStream.of(n)"
                +".map(x -> x+17).map(y -> y-17).sum(); java.util.function.IntPredicate p=x -> (x+19)-19 > 0;"
                +"return a+(p.test(n)?1:0);}}";
        assertEquals(2,analyze(source).replacements().size(),analyze(source).diagnostics().toString());
        equivalent(source,changed(source));
    }
    @Test void primitiveArrayFactoryAndLongRangeKeepOriginalSources() throws Exception {
        String source=sample("java.util.Arrays.stream(new int[]{n}).map(x -> x+17).map(y -> y-17).sum()");
        equivalent(source,changed(source));
        String range=sample("java.util.stream.LongStream.rangeClosed(n, (long)n+1).map(x -> x+17L).map(y -> y-17L).sum()");
        equivalent(range,changed(range));
    }
    @Test void sourceOverloadAndPredicateSamRemainStable() throws Exception {
        String source="public class Sample { static int choose(int value){return value;} static long choose(long value){return 999;}"
                +"public static long run(int n){return choose(java.util.stream.IntStream.of(n).map(x -> x+17).map(y -> y-17).sum());}}";
        equivalent(source,changed(source));
    }
    @Test void numericInspectionRetainsDoubleSumAndBinaryReducerShapesWithoutRenderPermission() {
        String source="class Sample { double run(){return java.util.stream.DoubleStream.empty().map(Math::abs).sum();}"
                +"int reduce(){return java.util.stream.IntStream.of(1).reduce(7,(a,b)->a+b);} }";
        var contexts=JavaNumericIterationContexts.discover(JavaEarlyExitGuardFactsTest.parse(source),source,10000,()->false).contexts();
        assertEquals(2,contexts.size());
        var sum=contexts.getFirst().iteration();
        assertEquals("double",sum.elementType()); assertEquals("sum",sum.terminal().operation());
        assertFalse(sum.terminal().optional()); assertNull(sum.terminal().identity());
        assertNull(sum.pipeline().getTerminal());
        var reduce=contexts.getLast().iteration().terminal();
        assertEquals(java.util.List.of("int","int"),reduce.accumulator().inputTypes());
        assertEquals("7",reduce.identity());
        assertFalse(analyze(source).changed());
    }
    @Test void tinyBudgetCancellationAndSelectionDoNotEscapeTheirBoundaries() {
        String source=sample("java.util.stream.IntStream.of(n).map(x -> x+17).map(y -> y-17).sum()");
        var low=new MathCleanUpOptions(true,options().kinds(),options().safety(),options().goal(),1,1,false,17,java.util.List.of());
        assertFalse(MathematicalAnalysis.analyze(JavaEarlyExitGuardFactsTest.parse(source),source,low,null,-1,0).changed());
        var monitor=new org.eclipse.core.runtime.NullProgressMonitor();monitor.setCanceled(true);
        assertFalse(MathematicalAnalysis.analyze(JavaEarlyExitGuardFactsTest.parse(source),source,options(),monitor,-1,0).changed());
        assertFalse(MathematicalAnalysis.analyze(JavaEarlyExitGuardFactsTest.parse(source),source,options(),null,source.indexOf("run"),3).changed());
    }
    @Test void arbitrarySourceInspectionDoesNotAssertKnownSequentialEncounterOrder() {
        String source="class Sample { int run(java.util.stream.IntStream custom){return custom.map(x -> x+17).sum();}}";
        var context=JavaNumericIterationContexts.discover(JavaEarlyExitGuardFactsTest.parse(source),source,10000,()->false).contexts().getFirst();
        assertEquals(org.sandbox.functional.core.model.NumericIteration.Execution.UNKNOWN,context.iteration().execution());
        assertEquals(org.sandbox.functional.core.model.NumericIteration.EncounterOrder.UNKNOWN,context.iteration().encounterOrder());
        assertFalse(context.standardMapChain());
    }
    @Test void inspectionRetainsTheLastExplicitExecutionModeWithoutAuthorizingFusion() {
        String source=sample("java.util.stream.IntStream.of(n).parallel().sequential().map(x -> x+17).map(y -> y-17).sum()");
        var context=JavaNumericIterationContexts.discover(JavaEarlyExitGuardFactsTest.parse(source),source,10000,()->false).contexts().getFirst();
        assertEquals(org.sandbox.functional.core.model.NumericIteration.Execution.SEQUENTIAL,context.iteration().execution());
        assertFalse(context.standardMapChain());
        assertFalse(analyze(source).changed());
    }
    @Test void unknownPipelineReturnsUnusedSearchBudgetToFollowingPasses() {
        String source="class Sample { int run(java.util.stream.IntStream custom){return custom.map(x -> x+17).map(y -> y-17).sum();}}";
        long remaining=JavaNumericStreamOptimizer.optimize(JavaEarlyExitGuardFactsTest.parse(source),source,options(),
                new org.eclipse.core.runtime.NullProgressMonitor(),-1,0,java.util.Map.of(),null,
                new java.util.ArrayList<>(),new java.util.ArrayList<>(),new java.util.ArrayList<>(),new java.util.ArrayList<>(),1_000_000);
        assertTrue(remaining>990_000, "Rejected source must retain unused search allocation: "+remaining);
        assertTrue(remaining<1_000_000,"Discovery must still consume work");
    }
    @Test void methodReferencesAreNotAssumedPure() {
        assertFalse(analyze(sample("java.util.stream.IntStream.of(n).map(Math::abs).map(y -> y-17).sum()")).changed());
    }
    @Test void commentedMapSpanIsRetained() {
        assertFalse(analyze(sample("java.util.stream.IntStream.of(n).map(x -> x+17) /*keep*/ .map(y -> y-17).sum()")).changed());
    }
    @Test void filterComparisonArithmeticStaysInsideTheShortCircuit() throws Exception {
        String source=sample("java.util.stream.IntStream.of(n).filter(x -> x!=0 && (x+17)-17 > 0).count()");
        String generated=changed(source);
        assertTrue(generated.contains("x!=0 &&"), generated);
        assertTrue(generated.contains(".filter("), generated);
        equivalent(source,generated);
    }
    @Test void genericPrimitivePredicateAlsoGetsInPlaceArithmetic() throws Exception {
        String source="public class Sample { public static long run(int n){java.util.function.IntPredicate p=x -> (x+n)-n > 0; return p.test(n)?1:0;}}";
        equivalent(source,changed(source));
    }
    @Test void longFilterAndEmptyPredicateKeepTheirOriginalExecution() throws Exception {
        String source=sample("java.util.stream.LongStream.of(((long)n)<<32).filter(x -> (x+17L)-17L > 0L).sum()");
        equivalent(source,changed(source));
        String empty=sample("java.util.stream.IntStream.empty().filter(x -> (x+n)-n > 0).count()");
        equivalent(empty,changed(empty));
    }
    @Test void predicateArithmeticNeverMovesBeforeTheGuardForAThrowingOperand() throws Exception {
        String source=sample("java.util.stream.IntStream.of(n).filter(x -> x!=0 && (x+17)-17 > 0 && 1/x >= 0).count()");
        String generated=changed(source);
        assertTrue(generated.contains("x!=0 &&")); assertTrue(generated.contains("1/x >= 0"));
        equivalent(source,generated);
    }
    @Test void commentedPredicateOperandIsNotDeleted() {
        String source=sample("java.util.stream.IntStream.of(n).filter(x -> (x+/*keep*/17)-17 > 0).count()");
        var analysis=analyze(source);
        if (analysis.changed()) assertTrue(changed(source).contains("/*keep*/"));
    }
    @Test void effectsAndMixedWidthsAreNotFused() {
        assertFalse(analyze(sample("java.util.stream.IntStream.of(n).map(x -> x/17).map(y -> y*17).sum()")).changed());
        assertFalse(analyze(sample("java.util.stream.IntStream.of(n).map(x -> (int)(x+17L)).map(y -> y-17).sum()")).changed());
    }
    private static String sample(String expression) {
        return "public class Sample { public static long run(int n){return "+expression+";}}";
    }
}
