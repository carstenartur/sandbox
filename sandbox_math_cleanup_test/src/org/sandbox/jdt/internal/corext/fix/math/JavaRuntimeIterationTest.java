/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavaRuntimeIterationTest extends JavaContextTestSupport {
    private static String sample(String loop) {
        return "public class Sample {public static int run(int n){int sum=17;"+loop+"return sum;}}";
    }
    private static String transformed(String source) {
        var result=analyze(source);
        assertTrue(result.replacements().stream().anyMatch(e->e.replacement().contains("$series")),result.diagnostics().toString());
        return changed(source);
    }
    private void compare(String source,String generated,int... inputs) throws Exception {
        try(var before=compile(source);var after=compile(generated)) {
            var first=before.loadClass("Sample").getMethod("run",int.class);
            var second=after.loadClass("Sample").getMethod("run",int.class);
            for(int n:inputs) assertEquals(first.invoke(null,n),second.invoke(null,n),"n="+n);
        }
    }
    @Test void runtimeCountUsesExactBinomialDivision() throws Exception {
        String source=sample("for(int i=1;i<=n;i++) sum+=i;").replace("sum=17","sum=0");
        String generated=transformed(source);
        compare(source,generated,-100,0,1,31,4096,50000);
        try(var compiled=compile(generated)) {assertEquals(1250025000,compiled.loadClass("Sample").getMethod("run",int.class).invoke(null,50000));}
    }
    @ParameterizedTest @ValueSource(strings={
        "for(int i=n-50000;i<n;i+=3) sum+=i*i*i;",
        "for(int i=n+50000;i>n;i-=3) sum+=i*i;",
        "for(int i=1;i<=n;i++) {int square=i*i;sum+=square*i*n;}",
        "int i=1;while(i<=n){sum+=i;i+=2;}sum+=i;",
        "int i=1;do{sum+=i;i+=2;}while(i<=n);sum+=i;"
    }) void runtimeBoundsStrideTemporariesAndLiveCounters(String loop) throws Exception {
        String source=sample(loop);compare(source,transformed(source),-17,0,1,100,7000);
    }
    @ParameterizedTest @ValueSource(strings={
        "for(int i=0;i<n;i++) sum=3*sum+i;",
        "for(int i=0;i<n;i++) sum*=3;",
        "int power=1;for(int i=0;i<n;i++){sum+=power;power*=3;}sum+=power;",
        "int other=2;for(int i=0;i<n;i++){sum+=other;other+=sum;}sum+=other;"
    }) void affineProductsGeometricAndOrderedCoupledStates(String loop) throws Exception {
        String source=sample(loop);int above=(int)analyze(source).evidence().stream().filter(e->e.runtimeIteration()!=null).findFirst().orElseThrow().runtimeIteration().minimumIterations()+1;compare(source,transformed(source),-1,0,1,32,4096,50000,above);
    }
    @ParameterizedTest @ValueSource(strings={
        "for(int i=0;i<n;i++) for(int j=0;j<=i;j++) sum+=j*j;",
        "for(int i=0;i<n;i++) for(int j=0;j<n;j++) sum+=i*j;"
    }) void triangularAndRectangularNestedSums(String loop) throws Exception {
        String source=sample(loop);compare(source,transformed(source),-1,0,1,100,300,5000);
    }
    @Test void runtimeSequentialRangeStream() throws Exception {
        String source="public class Sample {public static int run(int n){return java.util.stream.IntStream.rangeClosed(1,n).map(i->i*i).sum();}}";
        compare(source,transformed(source),-10,0,1,200,50000);
    }
    @Test void exactBigIntegerPreservesNullAndSubclassCalls() throws Exception {
        String source="public class Sample {public static String run(int n){java.math.BigInteger sum=java.math.BigInteger.ONE;for(int i=1;i<=n;i++) sum=sum.add(java.math.BigInteger.valueOf(i).pow(3));return sum==null?null:sum.toString();}}";
        compare(source,transformed(source),-1,0,1,100,5000);
        String nullable=source.replace("java.math.BigInteger.ONE","null");
        String generated=transformed(nullable);compare(nullable,generated,-1,0);
        try(var before=compile(nullable);var after=compile(generated)) {
            for(var loader:java.util.List.of(before,after)) {
                var error=assertThrows(java.lang.reflect.InvocationTargetException.class,()->loader.loadClass("Sample").getMethod("run",int.class).invoke(null,5000));
                assertInstanceOf(NullPointerException.class,error.getCause());
            }
        }
        String subclass="public class Sample {static class Odd extends java.math.BigInteger {Odd(){super(\"1\");}public java.math.BigInteger add(java.math.BigInteger value){return this;}}public static String run(int n){java.math.BigInteger sum=new Odd();for(int i=1;i<=n;i++) sum=sum.add(java.math.BigInteger.valueOf(i));return sum==null?null:sum.toString();}}";
        compare(subclass,transformed(subclass),-1,0,1,5000);
    }
    @Test void commentsAndCostFallbackRemainVisible() {
        String generated=transformed(sample("for(int i=1;i<=n;i++){/* retain explanation */sum+=i;}"));
        assertTrue(generated.contains("/* retain explanation */"));assertTrue(generated.contains("else"));
        assertTrue(generated.contains("for(int i=1;i<=n;i++)"));
    }
    @ParameterizedTest @ValueSource(strings={
        "for(int i=0;i<n;i++) sum+=Math.abs(i);",
        "for(int i=0;i<n;i++) {sum+=i;n--;}",
        "for(int i=0;i<n;i++) sum+=sum*i;",
        "for(int i=0;i<n;i++) sum+=i/(n+1);",
        "for(int i=0;i<n;i++) {sum+=i;break;}"
    }) void unsupportedSemanticsHaveNoRuntimeRewrite(String loop) {
        assertTrue(analyze(sample(loop)).replacements().stream().noneMatch(e->e.replacement().contains("$series")));
    }
    @Test void escapingAndIdentityObservableBigIntegerStay() {
        for(String use:java.util.List.of("return sum;", "return sum==java.math.BigInteger.ZERO;", "return System.identityHashCode(sum);", "synchronized(sum){return 1;}")) {
            String source="public class Sample {public static Object run(int n){java.math.BigInteger sum=java.math.BigInteger.ZERO;for(int i=0;i<n;i++) sum=sum.add(java.math.BigInteger.ONE);"+use+"}}";
            assertTrue(analyze(source).evidence().stream().noneMatch(e->e.runtimeIteration()!=null),use);
        }
    }
    @Test void boundCannotMoveWithCounter() {
        String source=sample("for(int i=0;i<i+n;i++) sum+=i;");
        assertTrue(analyze(source).evidence().stream().noneMatch(e->e.runtimeIteration()!=null));
    }
    @Test void exactLinearGrowthAndLongPrimitiveOutputs() throws Exception {
        String exact="public class Sample {public static int run(int n){java.math.BigInteger sum=java.math.BigInteger.ONE;for(int i=0;i<n;i++) sum=sum.multiply(java.math.BigInteger.valueOf(2));return sum.bitLength();}}";
        compare(exact,transformed(exact),-1,0,1,5000,40000);
        String wide="public class Sample {public static long run(int n){long sum=Long.MAX_VALUE;for(long i=-50000;i<n;i+=3) sum+=i*i*n;return sum;}}";
        compare(wide,transformed(wide),-50001,-1,0,1,50000);
    }
    @Test void hugeLongCountHasBoundedStandaloneEvaluation() throws Exception {
        String source=sample("for(long i=Long.MIN_VALUE;i<Long.MAX_VALUE;i++) sum+=1;");
        String generated=transformed(source);
        try(var loader=compile(generated)) {assertEquals(16,loader.loadClass("Sample").getMethod("run",int.class).invoke(null,0));}
        assertTrue(generated.length()<12000);
    }
    @Test void signedExtremaAndNativeHeaderOverflow() throws Exception {
        String source=sample("for(int i=n-10;i<n;i++) sum+=i;");
        compare(source,transformed(source),Integer.MIN_VALUE,Integer.MIN_VALUE+10,-1,0,Integer.MAX_VALUE);
        String inclusive=sample("for(int i=1;i<=n;i++) sum+=i;");
        String generated=transformed(inclusive);
        assertTrue(generated.contains("2147483647"));assertTrue(generated.contains("after.compareTo"));
        // The n=MAX_VALUE execution is intentionally not run: its retained source loop does not terminate.
    }
    @Test void actualTemplateMutationsCannotReuseCheckedModels() {
        String source=sample("for(int i=1;i<=n;i++) sum+=i;");
        var result=analyze(source);var receipt=result.evidence().stream().filter(e->e.runtimeIteration()!=null).findFirst().orElseThrow();
        var before=JavaEarlyExitGuardFactsTest.parse(source);
        String original=receipt.replacement();
        for(String replacement:java.util.List.of(original.replace(".divide(",".multiply("),original.replace("sum =","n ="),
                original.replace("after.compareTo","start.compareTo"),original.replace(".intValue()",".negate().intValue()"))) {
            assertNotEquals(original,replacement);
            var plan=new JavaSourceRewritePlan("Source.java",JavaFiniteSumOptimizer.hash(source),source.length(),receipt.offset(),receipt.length(),java.util.List.of(),java.util.List.of(),java.util.Map.of(),
                    java.util.List.of(new JavaSourceRewritePlan.Change(receipt.offset(),receipt.length(),JavaFiniteSumOptimizer.hash(receipt.original()),replacement)));
            var fake=new MathematicalAnalysis.VerifiedRegion(receipt.offset(),receipt.length(),null,receipt.cost(),receipt.original(),replacement,null,plan,null,null,receipt.runtimeIteration());
            String generated=source.substring(0,receipt.offset())+replacement+source.substring(receipt.offset()+receipt.length());
            var edit=new MathematicalAnalysis.Replacement(receipt.offset(),receipt.length(),replacement,"forged");
            assertThrows(IllegalArgumentException.class,()->JavaRuntimeIterationOptimizer.verify(before,JavaEarlyExitGuardFactsTest.parse(generated),source,generated,fake,java.util.List.of(edit),options(),500000,()->false));
        }
        String generated=source.substring(0,receipt.offset())+original+source.substring(receipt.offset()+receipt.length());
        var after=JavaEarlyExitGuardFactsTest.parse(generated);
        JavaRuntimeIterationOptimizer.verify(before,after,source,generated,receipt,result.replacements(),options(),500000,()->false);
        assertThrows(IllegalArgumentException.class,()->JavaRuntimeIterationOptimizer.verify(before,after,source+" ",generated,receipt,result.replacements(),options(),500000,()->false));
        assertThrows(IllegalArgumentException.class,()->JavaRuntimeIterationOptimizer.verify(before,JavaEarlyExitGuardFactsTest.parse(generated.replace("sum=17","sum=18")),source,generated.replace("sum=17","sum=18"),receipt,result.replacements(),options(),500000,()->false));
        assertThrows(JavaLoopInvariantAnalysis.Limit.class,()->JavaRuntimeIterationOptimizer.verify(before,after,source,generated,receipt,result.replacements(),options(),1,()->false));
        assertThrows(JavaLoopInvariantAnalysis.Limit.class,()->JavaRuntimeIterationOptimizer.verify(before,after,source,generated,receipt,result.replacements(),options(),500000,()->true));
    }
    @Test void combinedRuntimeEditsAndReviewHaveIndependentEvidence() throws Exception {
        String source=sample("for(int i=1;i<=n;i++)sum+=i;for(int j=1;j<=n;j++)sum+=j*j;");
        var result=analyze(source);assertEquals(2,result.evidence().stream().filter(e->e.runtimeIteration()!=null).count(),result.diagnostics().toString());
        compare(source,changed(source),-1,0,1,50000);
        for(var entry:MathReviewReport.ranked(result.evidence(),options()))assertNotEquals("UNASSESSED",entry.assessment().disposition());
        var json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result);assertTrue(json.contains("runtimeIteration"));
    }
    @Test void selectionsCancellationAndAggregateWorkFailClosed() {
        String source=sample("for(int i=1;i<=n;i++)sum+=i;");var ast=JavaEarlyExitGuardFactsTest.parse(source);
        assertFalse(MathematicalAnalysis.analyze(ast,source,options(),null,source.indexOf("for("),3).changed());
        var small=new MathCleanUpOptions(true,options().kinds(),options().safety(),options().goal(),10,1,false,17,java.util.List.of());
        assertFalse(MathematicalAnalysis.analyze(ast,source,small,null,-1,0).changed());
        int[] calls={0};var monitor=new org.eclipse.core.runtime.NullProgressMonitor(){@Override public boolean isCanceled(){return ++calls[0]>200;}};
        var edits=new java.util.ArrayList<MathematicalAnalysis.Replacement>();
        long remaining=JavaRuntimeIterationOptimizer.optimize(ast,source,options(),monitor,-1,0,java.util.Map.of(),null,edits,new java.util.ArrayList<>(),new java.util.ArrayList<>(),500000);
        assertTrue(edits.isEmpty());assertTrue(remaining>=0&&remaining<=500000);assertTrue(calls[0]>200);
    }
    @Test void staticallyUnprofitableAndWrappingFallbackOnlyLoopsStay() {
        for(String loop:java.util.List.of("for(int i=0;i<10;i++)sum*=3;", "for(int i=1;i<=Integer.MAX_VALUE;i++)sum+=i;", "for(int i=Integer.MIN_VALUE;i>=Integer.MIN_VALUE;i--)sum+=i;"))
            assertTrue(analyze(sample(loop)).evidence().stream().noneMatch(e->e.runtimeIteration()!=null),loop);
    }
    @Test void emittedJdkTypesCannotResolveToShadowClasses() {
        String fake="static class java {static class math {static class BigInteger {BigInteger(String s){}static final BigInteger ZERO=new BigInteger(\"0\"),ONE=new BigInteger(\"1\");static BigInteger valueOf(long n){return ONE;}BigInteger add(BigInteger b){return this;}BigInteger subtract(BigInteger b){return this;}BigInteger multiply(BigInteger b){return this;}BigInteger divide(BigInteger b){return this;}int signum(){return 1;}int compareTo(BigInteger b){return 0;}int intValue(){return 0;}}}}";
        String source=sample("for(int i=1;i<=n;i++)sum+=i;").replace("public class Sample {","public class Sample {"+fake);
        assertTrue(analyze(source).evidence().stream().noneMatch(e->e.runtimeIteration()!=null));
    }
    @Test void expandedTemporaryDagHasExplicitChargedSourceLimit() {
        var body=new StringBuilder("int t0=i;");for(int i=1;i<15;i++)body.append("int t").append(i).append("=t").append(i-1).append("+t").append(i-1).append(';');
        String source=sample("for(int i=0;i<n;i++){"+body+"sum+=t14;}");
        var ast=JavaEarlyExitGuardFactsTest.parse(source);int start=source.indexOf("for("),end=source.indexOf("return sum");
        var work=new JavaLoopInvariantAnalysis.Work(50000,()->false);
        var error=assertThrows(IllegalArgumentException.class,()->JavaRuntimeIterationAnalysis.inspect(ast,org.eclipse.jdt.core.dom.NodeFinder.perform(ast,start,end-start),work));
        assertEquals("RUNTIME_SOURCE_EXPRESSION_LIMIT",error.getMessage());assertTrue(work.used<50000);
    }
    @Test void reservationRequiresActualRuntimeAdmission() {
        assertEquals(0,JavaRuntimeIterationOptimizer.preflight(JavaEarlyExitGuardFactsTest.parse(sample("")),options(),1000000,()->false).work());
        for(String source:java.util.List.of(sample(""),sample("for(int i=0;i<n;i++)sum+=Math.abs(i);"),sample("for(int i=0;i<100;i++)sum+=i;")))
            assertFalse(JavaRuntimeIterationOptimizer.preflight(JavaEarlyExitGuardFactsTest.parse(source),options(),1000000,()->false).admitted());
        assertTrue(JavaRuntimeIterationOptimizer.preflight(JavaEarlyExitGuardFactsTest.parse(sample("for(int i=0;i<n;i++)sum+=i;")),options(),1000000,()->false).admitted());
    }
    @Test void overflowedRuntimeHeadersRemainNativeBeforeCapture() throws Exception {
        String source=sample("for(int i=n*1000000000;i<n*1000000000+50000;i++)sum+=i;");
        compare(source,transformed(source),Integer.MIN_VALUE,-5,0,5,Integer.MAX_VALUE);
    }
    @Test void degreeEightAndLongRangeStreamUseOptimizedBranch() throws Exception {
        String source=sample("for(int i=0;i<n;i++)sum+=i*i*i*i*i*i*i*i;");
        int above=(int)analyze(source).evidence().stream().filter(e->e.runtimeIteration()!=null).findFirst().orElseThrow().runtimeIteration().minimumIterations()+1;
        compare(source,transformed(source),0,1,above);
        String stream="public class Sample {public static long run(int n){return java.util.stream.LongStream.rangeClosed(1,n).sequential().map(i->i*i).sum();}}";
        compare(stream,transformed(stream),-1,0,1,50000);
    }
    @Test void runtimeTemplatesDoNotClaimReadabilityOrAllocationGains() {
        String source=sample("for(int i=1;i<=n;i++)sum+=i;");
        for(var goal:java.util.List.of(de.regelsuche.sdk.optimization.OptimizationGoal.READABILITY,de.regelsuche.sdk.optimization.OptimizationGoal.LOWER_ALLOCATION)) {
            var options=new MathCleanUpOptions(true,options().kinds(),options().safety(),goal,1000000,20000,false,17,java.util.List.of());
            var result=MathematicalAnalysis.analyze(JavaEarlyExitGuardFactsTest.parse(source),source,options,null,-1,0);
            assertTrue(result.evidence().stream().noneMatch(e->e.runtimeIteration()!=null),goal.toString());
        }
    }
    @Test void mixedWidthIntermediateDoesNotBecomeExact() {
        String source="public class Sample {public static long run(int n){long sum=0;for(int i=0;i<n;i++) sum+=i*i;return sum;}}";
        assertTrue(analyze(source).replacements().stream().noneMatch(e->e.replacement().contains("$series")));
    }
}
