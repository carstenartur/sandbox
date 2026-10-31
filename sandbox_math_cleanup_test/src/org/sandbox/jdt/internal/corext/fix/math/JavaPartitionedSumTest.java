/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavaPartitionedSumTest extends JavaContextTestSupport {
    private static String sample(String loop) {
        return "public class Sample {public static int run(int n){int sum=17;"+loop+"return sum;}}";
    }
    private static MathematicalAnalysis.VerifiedRegion partition(String source) {
        var result=analyze(source);
        return result.evidence().stream().filter(e->e.runtimeIteration()!=null).findFirst()
                .orElseThrow(()->new AssertionError(result.diagnostics().toString()));
    }
    private void compare(String source,int... inputs) throws Exception {
        var receipt=partition(source);String generated=changed(source);
        int above=Math.toIntExact(receipt.runtimeIteration().minimumIterations()+37);
        var all=new ArrayList<Integer>();for(int n:inputs)all.add(n);all.add(above);
        try(var before=compile(source);var after=compile(generated)) {
            var original=before.loadClass("Sample").getMethod("run",int.class);
            var optimized=after.loadClass("Sample").getMethod("run",int.class);
            for(int n:all)assertEquals(original.invoke(null,n),optimized.invoke(null,n),"n="+n);
        }
    }
    @ParameterizedTest @ValueSource(strings={
        "for(int i=0;i<n;i++)if(i%2==0)sum+=i*i;else sum-=i*i*i;",
        "for(int i=1;i<n;i++){if(i%3==2){sum+=i*i*i;}else {sum+=i*i;}}",
        "for(int i=2;i<n;i++)if(i%3==0)sum+=i*i;",
        "for(int i=0;i<n;i++)if(i%1==0)sum+=i*i;"
    }) void periodicSquaresCubicsAndOmittedElse(String loop) throws Exception {compare(sample(loop),-10,0,1,2,3,50000);}
    @ParameterizedTest @ValueSource(strings={"i<split","i<=split","i>split","i>=split","split>i","split>=i","split<i","split<=i"})
    void invariantSplitDirectionsClampBothIntervals(String predicate) throws Exception {
        String source=sample("int split=n/2;for(int i=0;i<n;i++)if("+predicate+")sum+=i*i;else sum-=i*i*i;");
        compare(source,-10,0,1,2,50000);
        compare(source.replace("split=n/2","split=-100"),0,1,50000);
        compare(source.replace("split=n/2","split=n+100"),0,1,50000);
    }
    @Test void negativeRemaindersUseOriginalFallback() throws Exception {
        compare(sample("for(int i=n-50000;i<n;i++)if(i%3==1)sum+=i*i;else sum-=i;"),-100,-1,0,1,50001,70000);
        assertTrue(analyze(sample("for(int i=-50000;i<50000;i++)if(i%3==1)sum+=i*i;else sum-=i;")).evidence().stream().noneMatch(e->e.runtimeIteration()!=null));
    }
    @Test void nativeBoundAndSplitOverflowAreCapturedBeforeExactArithmetic() throws Exception {
        compare(sample("int split=n*1000000000+25000;for(int i=n*1000000000;i<n*1000000000+50000;i++)if(i<split)sum+=i*i;else sum-=i*i*i;"),Integer.MIN_VALUE,-5,0,5,Integer.MAX_VALUE);
    }
    @Test void longTermsAndNonzeroInitialWrapExactly() throws Exception {
        String source="public class Sample {public static long run(int n){long sum=Long.MAX_VALUE;for(long i=1;i<n;i++)if(i%3L==1L)sum+=i*i*i;else sum-=i*i;return sum;}}";
        compare(source,-1,0,1,50000);
    }
    @Test void omittedPieceAndExtremeInclusiveSplitRemainExact() throws Exception {
        compare(sample("for(int i=-100;i<n;i++)if(i<=n-10)sum+=i*i;"),-200,-100,0,50000);
        compare(sample("for(int i=0;i<n;i++)if(i<n+Integer.MAX_VALUE)sum+=i*i;else sum-=i;"),-1,0,1,50000);
        String source="public class Sample {public static long run(int n){long sum=Long.MAX_VALUE;for(long i=Long.MAX_VALUE-50000;i<Long.MAX_VALUE;i++)if(i<=Long.MAX_VALUE)sum+=i*i*i;else sum-=i*i;return sum;}}";
        compare(source,0);
    }
    @Test void constantFiniteLimitsAndComments() throws Exception {
        String source=sample("for(int i=1;i<50000;i++){/* keep branch rationale */if(i<25000)sum+=i*i;else sum+=i*i*i;}");
        assertTrue(changed(source).contains("/* keep branch rationale */"));compare(source,0);
    }
    @ParameterizedTest @ValueSource(strings={
        "for(int i=0;i<n;i+=2)if(i%3==1)sum+=i;",
        "for(int i=0;i<n;i++)if(i%3==-1)sum+=i;",
        "for(int i=0;i<n;i++)if(i%33==1)sum+=i;",
        "for(int i=0;i<n;i++)if(i%0==0)sum+=i;",
        "for(int i=0;i<n;i++)if(i%3==1)sum+=Math.abs(i);",
        "for(int i=0;i<n;i++)if(i%3==1){sum+=i;i++;}",
        "for(int i=0;i<n;i++)if(i<sum)sum+=i;",
        "for(int i=0;i<n;i++)if(i<10)sum+=sum*i;",
        "for(int i=0;i<n;i++)if(i<n--)sum+=i;",
        "for(int i=0;i<n;i++)if(i<10)sum+=i;else break;"
    }) void effectsMutableStateAndUnsupportedControlAreRejected(String loop) {
        assertTrue(analyze(sample(loop)).evidence().stream().noneMatch(e->e.runtimeIteration()!=null));
    }
    @Test void selectionCancellationAndTightWorkFailClosed() {
        String source=sample("for(int i=0;i<n;i++)if(i%3==1)sum+=i*i;else sum-=i;");
        var ast=JavaEarlyExitGuardFactsTest.parse(source);
        assertFalse(MathematicalAnalysis.analyze(ast,source,options(),null,source.indexOf("for("),3).changed());
        var small=new MathCleanUpOptions(true,options().kinds(),options().safety(),options().goal(),10,1,false,17,List.of());
        assertFalse(MathematicalAnalysis.analyze(ast,source,small,null,-1,0).changed());
        var edits=new ArrayList<MathematicalAnalysis.Replacement>();
        var monitor=new org.eclipse.core.runtime.NullProgressMonitor();monitor.setCanceled(true);
        JavaRuntimeIterationOptimizer.optimize(ast,source,options(),monitor,-1,0,Map.of(),null,edits,new ArrayList<>(),new ArrayList<>(),500000);
        assertTrue(edits.isEmpty());
    }
    @Test void combinedPartitionsKeepIndependentInitialStates() throws Exception {
        String source=sample("for(int i=0;i<n;i++)if(i%2==0)sum+=i*i;else sum-=i;for(int j=0;j<n;j++)if(j<n-10)sum+=j;else sum+=j*j;");
        assertEquals(2,analyze(source).evidence().stream().filter(e->e.runtimeIteration()!=null).count());compare(source,-1,0,1,50000);
    }
    @Test void largestPeriodKeepsEachChildParameterScopeBounded() throws Exception {
        String source=sample("for(int i=0;i<n;i++)if(i%32==17)sum+=i;else sum-=i;");
        var ast=JavaEarlyExitGuardFactsTest.parse(source);
        var large=new MathCleanUpOptions(true,options().kinds(),options().safety(),options().goal(),5000000,20000,false,17,List.of());
        var result=MathematicalAnalysis.analyze(ast,source,large,null,-1,0);
        var proof=result.evidence().stream().filter(e->e.runtimeIteration()!=null).findFirst()
                .orElseThrow(()->new AssertionError(result.diagnostics().toString())).runtimeIteration();
        assertEquals(32,proof.sums().size());
        assertTrue(proof.sums().stream().allMatch(p->p.source().parameters().size()<16));
        var generated=new org.eclipse.jface.text.Document(source);result.newEdit().apply(generated);
        int above=Math.toIntExact(proof.minimumIterations()+37);
        try(var before=compile(source);var after=compile(generated.get())) {
            for(int n:new int[]{-1,0,1,above})assertEquals(before.loadClass("Sample").getMethod("run",int.class).invoke(null,n),after.loadClass("Sample").getMethod("run",int.class).invoke(null,n));
        }
        assertFalse(MathReviewReport.ranked(result.evidence(),large).isEmpty());
    }
    @Test void partitionTemplateAndPredicateTamperingCannotReuseProof() {
        String source=sample("for(int i=0;i<n;i++)if(i%3==1)sum+=i*i;else sum-=i;");
        var receipt=partition(source);var proof=receipt.runtimeIteration();String original=receipt.replacement();
        verify(source,receipt,original,proof);
        for(String replacement:List.of(original.replace("start.signum()>=0","start.signum()<=0"),
                original.replace("partCount0.subtract(","partCount1.subtract("),original.replace(".mod(",".remainder("),
                original.replace("result0.add(","result0.subtract("))) {
            assertNotEquals(original,replacement);
            assertThrows(IllegalArgumentException.class,()->verify(source,receipt,replacement,proof));
        }
        var p=proof.partition();
        var wrongPartition=new RuntimeIterationEvidence.Partition(p.period(),2,p.inclusive(),p.firstOnLow(),p.splitOffset(),p.splitLength(),p.starts());
        var forged=new RuntimeIterationEvidence(proof.sourceSha256(),proof.bindings(),proof.sums(),null,null,wrongPartition);
        assertThrows(IllegalArgumentException.class,()->verify(source,receipt,original,forged));
        String changedPredicate=source.replace("%3==1","%3==2");
        var rebound=new RuntimeIterationEvidence(JavaFiniteSumOptimizer.hash(changedPredicate),proof.bindings(),proof.sums(),null,null,p);
        assertThrows(IllegalArgumentException.class,()->verify(changedPredicate,receipt,original,rebound));
        long used=proof.verify(options().optimizationBudget(500000),()->false);
        assertThrows(IllegalArgumentException.class,()->proof.verify(options().optimizationBudget(used-1),()->false));
        assertThrows(IllegalArgumentException.class,()->proof.verify(options().optimizationBudget(500000),()->true));
    }
    @Test void splitBoundaryAndClampTamperingCannotReuseProof() {
        String source=sample("for(int i=0;i<n;i++)if(i<n-10)sum+=i*i;else sum-=i;");
        var receipt=partition(source);var proof=receipt.runtimeIteration();var p=proof.partition();
        var inclusive=new RuntimeIterationEvidence.Partition(0,0,!p.inclusive(),p.firstOnLow(),p.splitOffset(),p.splitLength(),p.starts());
        var forged=new RuntimeIterationEvidence(proof.sourceSha256(),proof.bindings(),proof.sums(),null,null,inclusive);
        assertThrows(IllegalArgumentException.class,()->verify(source,receipt,receipt.replacement(),forged));
        String replacement=receipt.replacement().replace(".max(java.math.BigInteger.ZERO).min(",".min(java.math.BigInteger.ZERO).min(");
        assertNotEquals(receipt.replacement(),replacement);
        assertThrows(IllegalArgumentException.class,()->verify(source,receipt,replacement,proof));
    }
    private static void verify(String source,MathematicalAnalysis.VerifiedRegion receipt,String replacement,RuntimeIterationEvidence proof) {
        String original=source.substring(receipt.offset(),receipt.offset()+receipt.length());
        var plan=new JavaSourceRewritePlan("Source.java",JavaFiniteSumOptimizer.hash(source),source.length(),receipt.offset(),receipt.length(),List.of(),List.of(),Map.of(),
                List.of(new JavaSourceRewritePlan.Change(receipt.offset(),receipt.length(),JavaFiniteSumOptimizer.hash(original),replacement)));
        var candidate=new MathematicalAnalysis.VerifiedRegion(receipt.offset(),receipt.length(),null,receipt.cost(),original,replacement,null,plan,null,null,proof);
        String generated=source.substring(0,receipt.offset())+replacement+source.substring(receipt.offset()+receipt.length());
        var edit=new MathematicalAnalysis.Replacement(receipt.offset(),receipt.length(),replacement,"candidate");
        JavaRuntimeIterationOptimizer.verify(JavaEarlyExitGuardFactsTest.parse(source),JavaEarlyExitGuardFactsTest.parse(generated),source,generated,candidate,List.of(edit),options(),1000000,()->false);
    }
    @Test void childModelAdmissionChargesRepeatedTermValidationAndPollsCancellation() {
        StringBuilder term=new StringBuilder("i");
        for(int depth=0;depth<7;depth++) {
            String previous=term.toString();
            term.setLength(0);
            term.append('(').append(previous).append('+').append(previous).append(')');
        }
        String one=sample("for(int i=0;i<n;i++)if(i%1==0)sum+=i;else sum+="+term+";");
        String many=one.replace("i%1==0","i%32==0");
        var singleWork=new JavaLoopInvariantAnalysis.Work(1000000,()->false);inspectPartition(one,singleWork);
        var manyWork=new JavaLoopInvariantAnalysis.Work(1000000,()->false);inspectPartition(many,manyWork);
        assertTrue(manyWork.used>=singleWork.used+31*255,"Repeated SDK model validation must be charged: "+singleWork.used+" / "+manyWork.used);
        assertThrows(JavaLoopInvariantAnalysis.Limit.class,()->inspectPartition(many,new JavaLoopInvariantAnalysis.Work(singleWork.used+1000,()->false)));
        int[] singlePolls={0};inspectPartition(one,new JavaLoopInvariantAnalysis.Work(1000000,()->{singlePolls[0]++;return false;}));
        int[] repeatedPolls={0};
        assertThrows(JavaLoopInvariantAnalysis.Limit.class,()->inspectPartition(many,new JavaLoopInvariantAnalysis.Work(1000000,()->++repeatedPolls[0]>singlePolls[0]+2)));
    }
    private static JavaRuntimeIterationAnalysis.Context inspectPartition(String source,JavaLoopInvariantAnalysis.Work work) {
        var ast=JavaEarlyExitGuardFactsTest.parse(source);
        int start=source.indexOf("for("),end=source.indexOf("return sum");
        return JavaRuntimeIterationAnalysis.inspect(ast,org.eclipse.jdt.core.dom.NodeFinder.perform(ast,start,end-start),work);
    }
    @Test void failedLaterChildProofsConsumeTheSharedRequestAllowance() {
        String loop="for(int i=0;i<n;i++)if(i%32==17)sum+=i;else sum-=i;";
        var context=inspectPartition(sample(loop),new JavaLoopInvariantAnalysis.Work(1000000,()->false));
        var plans=context.sums().stream().map(model->new de.regelsuche.sdk.optimization.iteration.PolynomialSumOptimizer()
                .search(model,options().optimizationBudget(100000),()->false).plan().orElseThrow()).toList();
        var first=new de.regelsuche.sdk.optimization.iteration.PolynomialSumVerifier().verify(plans.getFirst().source(),plans.getFirst(),options().optimizationBudget(250000),()->false);
        assertTrue(first.verified());assertTrue(first.work()<250000);
        var proof=new RuntimeIterationEvidence(JavaFiniteSumOptimizer.hash(sample(loop)),context.bindings(),plans,null,null,context.partition());
        assertTrue(proof.verify(options().optimizationBudget(500000),()->false)>250000);
        assertThrows(IllegalArgumentException.class,()->proof.verify(options().optimizationBudget(250000),()->false));
        String source=sample(loop.repeat(5));
        var edits=new ArrayList<MathematicalAnalysis.Replacement>();var diagnostics=new ArrayList<MathematicalAnalysis.Diagnostic>();
        long remaining=JavaRuntimeIterationOptimizer.optimize(JavaEarlyExitGuardFactsTest.parse(source),source,options(),new org.eclipse.core.runtime.NullProgressMonitor(),-1,0,Map.of(),null,edits,new ArrayList<>(),diagnostics,1000000);
        assertTrue(edits.isEmpty());assertEquals(5,diagnostics.size());
        assertTrue(remaining<500000,"Failed aggregate proofs must retain their consumed reservation: "+remaining);
    }
    @Test void failedGeneratedSourceReplayCannotRefundCompletedProofWork() {
        String fake="static class java {static class math {static class BigInteger {BigInteger(String s){}static final BigInteger ZERO=new BigInteger(\"0\"),ONE=new BigInteger(\"1\");static BigInteger valueOf(long n){return ONE;}BigInteger add(BigInteger b){return this;}BigInteger subtract(BigInteger b){return this;}BigInteger multiply(BigInteger b){return this;}BigInteger divide(BigInteger b){return this;}BigInteger mod(BigInteger b){return this;}BigInteger max(BigInteger b){return this;}int signum(){return 1;}int compareTo(BigInteger b){return 0;}int intValue(){return 0;}}}}";
        String source=sample("for(int i=0;i<n;i++)if(i%3==1)sum+=i;else sum-=i;").replace("public class Sample {","public class Sample {"+fake);
        var edits=new ArrayList<MathematicalAnalysis.Replacement>();var diagnostics=new ArrayList<MathematicalAnalysis.Diagnostic>();
        long remaining=JavaRuntimeIterationOptimizer.optimize(JavaEarlyExitGuardFactsTest.parse(source),source,options(),new org.eclipse.core.runtime.NullProgressMonitor(),-1,0,Map.of(),null,edits,new ArrayList<>(),diagnostics,1000000);
        assertTrue(edits.isEmpty());
        assertTrue(diagnostics.stream().anyMatch(d->"RUNTIME_ITERATION_REPLAY_DIFFERS".equals(d.message())),diagnostics.toString());
        assertEquals(0,remaining,"Replay failure must retain its reservation after child proof work");
    }
}
