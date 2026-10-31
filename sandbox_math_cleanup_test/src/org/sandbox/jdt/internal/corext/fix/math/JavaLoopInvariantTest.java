/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavaLoopInvariantTest extends JavaContextTestSupport {
    private static final String VALUE = "(n+7)*(n-7)";

    @ParameterizedTest @ValueSource(strings={
            "for(int i=0;i<8;i++){sum += VALUE;}",
            "for(int i=8;i>0;--i){sum += VALUE;}",
            "int i=0;while(i<8){sum += VALUE;i++;}",
            "int i=8;do {sum += VALUE;--i;}while(i>0);",
            "for(int i:new int[8]){sum += VALUE;}",
            "int[] values=new int[8];for(int i:values){sum += VALUE;}"
    })
    void movesTotalArithmeticAndRetainsEveryLoopControl(String loop) throws Exception {
        String source=sample(loop.replace("VALUE",VALUE));
        String generated=changed(source);
        assertTrue(generated.contains("final int _mathInvariant"),generated);
        assertEquals(1,generated.split(java.util.regex.Pattern.quote(VALUE),-1).length-1,generated);
        assertTrue(generated.indexOf(VALUE)<generated.indexOf("sum +="),generated);
        var control=analyze(source).evidence().stream().filter(e->e.scheduling()!=null).findFirst().orElseThrow().scheduling().control();
        assertEquals(8,control.tripCount());
        if(loop.contains("while") || loop.contains("--i")) assertNull(control.source());
        else assertNotNull(control.source());
        equivalent(source,generated);
    }

    @Test void arraysAndCatchObservableAssignmentsRemainPinned() throws Exception {
        String source=sample("int[] out=n==0?null:new int[3];try{for(int i=0;i<8;i++){"
                +"sum += "+VALUE+";out[i]=sum;}}catch(RuntimeException ex){return sum;} ");
        String generated=changed(source);
        assertTrue(generated.contains("out[i]=sum;"),generated);
        equivalent(source,generated);
    }

    @Test void receiptSeparatesNumericalProofSchedulingAndConditionalEstimates() {
        var analysis=analyze(sample("for(int i=0;i<8;i++){sum += "+VALUE+";}"));
        var evidence=analysis.evidence().stream().filter(e->e.scheduling()!=null).findFirst().orElseThrow();
        var schedule=evidence.scheduling();
        assertEquals(8,schedule.control().tripCount());
        assertEquals(3,schedule.scalarWork()); assertEquals(24,schedule.originalWork()); assertEquals(12,schedule.scheduledWork());
        assertEquals(1,schedule.retainedPrimitiveValues());
        assertEquals(LoopSchedulingEvidence.EvaluationBasis.TOTAL_BODY,schedule.evaluationBasis());
        assertEquals(240,evidence.cost().sourceScore()); assertEquals(121,evidence.cost().candidateScore());
        assertNotNull(evidence.proof()); assertNull(evidence.explanation()); assertNotNull(evidence.sourcePlan());
        assertTrue(MathReviewReport.ranked(analysis.evidence(),options()).getFirst().assessment().cautions().stream()
                .anyMatch(text->text.contains("one numerical evaluation, not loop scheduling")));
        var arrays=analyze(sample("int[] values=new int[8];for(int i=0;i<8;i++){sum += "+VALUE+";values[i]=sum;}"));
        var receipt=arrays.evidence().stream().filter(e->e.scheduling()!=null).findFirst().orElseThrow();
        assertEquals(LoopSchedulingEvidence.EvaluationBasis.NORMAL_LOOP_COMPLETION,receipt.scheduling().evaluationBasis());
        assertTrue(receipt.scheduling().description().contains("conditional on normal loop completion"));
    }

    @ParameterizedTest @ValueSource(strings={
            "for(int i=0;i<0;i++){sum += VALUE;}",
            "for(int i=0;i<1;i++){sum += VALUE;}",
            "for(int i=0;i<=8;i++){sum += VALUE;}",
            "for(int i=Integer.MAX_VALUE;i>=0;i++){sum += VALUE;}",
            "int i=Integer.MAX_VALUE;do {sum += VALUE;i++;}while(i<8);",
            "for(int i=0;i<8;i++,n++){sum += VALUE;}",
            "for(int i=0;i<8;i++){sum += VALUE;n++;}",
            "for(int i=0;i<8;i++){sum += VALUE;i++;}",
            "for(int i=0;i<8;i++){if(n>0) sum += VALUE;}",
            "for(int i=0;i<8;i++){sum += VALUE;if(n>0) break;}",
            "for(int i=0;i<8;i++){sum += VALUE;continue;}",
            "for(int i=0;i<8;i++){sum += VALUE;System.nanoTime();}",
            "for(int i=0;i<8;i++){sum += VALUE;sum += 1/n;}",
            "for(int i=0;i<8;i++){sum += VALUE;sum %= n;}",
            "for(int i=0;i<8;i++){int local=n;sum += (local+7)*(local-7);}",
            "if(n>0) for(int i=0;i<8;i++){sum += VALUE;}",
            "int[] values=null;for(int i:values){sum += VALUE;}",
            "int[] values=new int[8];values=null;for(int i:values){sum += VALUE;}",
            "Integer[] values={null,null,null,null,null,null,null,null};for(int i:values){sum += VALUE;}",
            "for(int i=0;i<8;i++){sum += (n+/*keep*/7)*(n-7);}",
            "for(int i=0;i<8;i++){sum += (n+7)/(n-7);}",
            "for(int i=0;i<8;i++){sum += (n+7)*(n-7);Runnable r=()->{};}"
    })
    void unsafeOrUnprofitableSchedulesAreNotHoisted(String body) {
        assertNoHoist(sample(body.replace(" += VALUE;"," += "+VALUE+";")));
    }

    @Test void inputReadsFromMemoryAndBoxingNeverMove() {
        assertNoHoist("class Sample {static int field;int run(int n){int sum=0;for(int i=0;i<8;i++){sum += (field+7)*(field-7);}return sum;}}");
        assertNoHoist("class Sample {int run(Integer n){int sum=0;for(int i=0;i<8;i++){sum += (n+7)*(n-7);}return sum;}}");
        assertNoHoist(sample("int[] values={n};for(int i=0;i<8;i++){sum += (values[0]+7)*(values[0]-7);}"));
        assertNoHoist("class Sample {int run(int n,int[] values){int sum=0;for(int i:values){sum += "+VALUE+";}return sum;}}");
    }

    @Test void labelsCommentsAndFreshNamesHaveAnExactApplyUndoEnvelope() throws Exception {
        String source=sample("int _mathInvariant0=n;outer:inner:for(int i=0;i<8;i++){/*keep*/ sum += "+VALUE+";}sum += _mathInvariant0;");
        String generated=changed(source);
        assertTrue(generated.contains("final int _mathInvariant1"),generated);
        assertTrue(generated.indexOf("final int _mathInvariant1")<generated.indexOf("outer:inner:"),generated);
        assertTrue(generated.contains("{/*keep*/ sum += _mathInvariant1;}"),generated);
        var receipt=analyze(source).evidence().stream().filter(e->e.scheduling()!=null).findFirst().orElseThrow();
        assertEquals(source.indexOf("outer:"),receipt.offset());
        receipt.sourcePlan().resolve(JavaEarlyExitGuardFactsTest.parse(source),source);
        assertThrows(IllegalArgumentException.class,()->receipt.sourcePlan().newEdit(source+" "));
        equivalent(source,generated);
    }

    @Test void explicitSelectionMustContainTheInsertionAndEntireLoop() {
        String source=sample("for(int i=0;i<8;i++){sum += "+VALUE+";}");
        var ast=JavaEarlyExitGuardFactsTest.parse(source);
        int expression=source.indexOf(VALUE),start=source.indexOf("for("),end=source.indexOf("return sum");
        assertTrue(MathematicalAnalysis.analyze(ast,source,options(),null,expression,VALUE.length()).evidence().stream().noneMatch(e->e.scheduling()!=null));
        assertTrue(MathematicalAnalysis.analyze(ast,source,options(),null,start,end-start).evidence().stream().anyMatch(e->e.scheduling()!=null));
    }

    @Test void widthsAndOverflowStayAtTheOriginalPrimitiveEvaluationWidth() throws Exception {
        String source=sample("long a=((long)n)<<32;for(int i=0;i<8;i++){sum += (int)((a+7L)*(a-7L));}");
        equivalent(source,changed(source));
        String local=sample("int a=n;for(int i=0;i<8;i++){int local=(a+7)*(a-7);sum += local;}");
        equivalent(local,changed(local));
    }

    @Test void longTripCountArithmeticCannotWrapTheHostCounter() {
        String source=sample("for(int i=Integer.MIN_VALUE;i<Integer.MAX_VALUE;i++){sum += "+VALUE+";}");
        var analysis=analyze(source);
        assertTrue(analysis.evidence().stream().anyMatch(e->e.scheduling()!=null),analysis.diagnostics().toString());
        var receipt=analysis.evidence().stream().filter(e->e.scheduling()!=null).findFirst().orElseThrow();
        assertEquals(4294967295L,receipt.scheduling().control().tripCount());
        assertEquals(12884901885L,receipt.scheduling().originalWork());
    }

    @Test void primitiveStorageWeightCanRejectAnOtherwisePositiveWorkEstimate() {
        String source=sample("for(int i=0;i<3;i++){sum += "+VALUE+";}");
        assertTrue(analyze(source).evidence().stream().anyMatch(e->e.scheduling()!=null));
        var allocation=new MathCleanUpOptions(true,options().kinds(),options().safety(),
                de.regelsuche.sdk.optimization.OptimizationGoal.LOWER_ALLOCATION,1_000_000,20_000,false,17,java.util.List.of());
        assertTrue(MathematicalAnalysis.analyze(JavaEarlyExitGuardFactsTest.parse(source),source,allocation,null,-1,0)
                .evidence().stream().noneMatch(e->e.scheduling()!=null));
    }

    @Test void explicitFinalPrimitiveArrayIsAProvenSource() throws Exception {
        String source=sample("final int[] values={n,n,n,n,n,n,n,n};for(int i:values){sum += "+VALUE+";}");
        equivalent(source,changed(source));
    }

    @Test void outerNestedLoopRetainsInnerSchedulingInItsOwnScope() throws Exception {
        String source=sample("for(int outer=0;outer<3;outer++){for(int i=0;i<8;i++){sum += "+VALUE+";}}");
        String generated=changed(source);
        assertTrue(generated.indexOf("final int _mathInvariant")>generated.indexOf("outer<3"),generated);
        equivalent(source,generated);
    }

    @Test void twoIndependentLoopEnvelopesReplayAfterEarlierOffsetsMove() throws Exception {
        String source=sample("for(int i=0;i<8;i++){sum += "+VALUE+";}for(int j=8;j>0;j--){sum += "+VALUE+";}");
        assertEquals(2,analyze(source).evidence().stream().filter(e->e.scheduling()!=null).count());
        equivalent(source,changed(source));
    }

    @Test void unsuccessfulSourceTaskRefundsOnlyUnspentSearchAllowance() {
        String source=sample("int i=0;while(i<8){sum += "+VALUE+";i++;}");
        var ast=JavaEarlyExitGuardFactsTest.parse(source);
        var task=new JavaComputationExtractor().extractTasks(ast,source,options()).tasks().stream()
                .filter(t->t.plan().outputs().size()>1).findFirst().orElseThrow();
        var attempt=JavaTaskSourceRewriter.optimizeWithRemaining(ast,source,task,options(),500000,
                new org.eclipse.core.runtime.NullProgressMonitor(),java.util.Map.of(),null);
        assertTrue(attempt.applied().isEmpty()); assertTrue(attempt.remaining()>0 && attempt.remaining()<500000);
        var edits=new java.util.ArrayList<MathematicalAnalysis.Replacement>();
        long remaining=JavaLoopInvariantOptimizer.optimize(ast,source,options(),new org.eclipse.core.runtime.NullProgressMonitor(),
                -1,0,java.util.Map.of(),null,edits,new java.util.ArrayList<>(),new java.util.ArrayList<>(),attempt.remaining());
        assertEquals(1,edits.size()); assertTrue(remaining>=0 && remaining<attempt.remaining());
    }

    @Test void lowBudgetCancellationAndStaleAstCannotAuthorizeMotion() {
        String source=sample("for(int i=0;i<8;i++){sum += "+VALUE+";}");
        var ast=JavaEarlyExitGuardFactsTest.parse(source);
        var low=new MathCleanUpOptions(true,options().kinds(),options().safety(),options().goal(),1,1,false,17,java.util.List.of());
        assertFalse(MathematicalAnalysis.analyze(ast,source,low,null,-1,0).changed());
        var monitor=new org.eclipse.core.runtime.NullProgressMonitor(); monitor.setCanceled(true);
        assertFalse(MathematicalAnalysis.analyze(ast,source,options(),monitor,-1,0).changed());
        assertFalse(MathematicalAnalysis.analyze(ast,source.replace("i<8","i<9"),options(),null,-1,0).changed());
        int[] calls={0};
        var discovery=JavaLoopInvariantAnalysis.discover(ast,1000,()->++calls[0]>=5);
        assertTrue(discovery.contexts().isEmpty()); assertTrue(discovery.work()<5);
    }

    @Test void replayRejectsTamperingWithOriginalControlsWritesAndTemporaryBinding() {
        String source=sample("for(int i=0;i<8;i++){sum += "+VALUE+";}");
        var analysis=analyze(source); var receipt=analysis.evidence().stream().filter(e->e.scheduling()!=null).findFirst().orElseThrow();
        String generated=changed(source);
        for(String corrupted:java.util.List.of(generated.replace("i<8","i<9"),generated.replace("sum +=","sum -="),
                generated.replace("sum += _mathInvariant0","sum += n"),generated.replace("final int _mathInvariant0","final long _mathInvariant0")))
            assertThrows(IllegalArgumentException.class,()->JavaLoopInvariantOptimizer.verifySchedule(
                    JavaEarlyExitGuardFactsTest.parse(source),JavaEarlyExitGuardFactsTest.parse(corrupted),source,corrupted,
                    receipt,analysis.replacements(),new JavaLoopInvariantAnalysis.Work(10000,()->false)));
    }

    @ParameterizedTest @ValueSource(strings={
            "int i=0;while(i<8){sum += VALUE;i++;}",
            "int i=0;do{sum += VALUE;i++;}while(i<8);",
            "int[] values=new int[8];for(int i:values){sum += VALUE;}"
    })
    void replayChecksControlInitializersOutsideTheEditEnvelope(String loop) {
        String source=sample(loop.replace(" += VALUE;"," += "+VALUE+";"));
        var analysis=analyze(source);var receipt=analysis.evidence().stream().filter(e->e.scheduling()!=null).findFirst().orElseThrow();
        String corrupted=changed(source).replace("int i=0","int i=1").replace("new int[8]","new int[9]");
        assertThrows(IllegalArgumentException.class,()->JavaLoopInvariantOptimizer.verifySchedule(
                JavaEarlyExitGuardFactsTest.parse(source),JavaEarlyExitGuardFactsTest.parse(corrupted),source,corrupted,
                receipt,analysis.replacements(),new JavaLoopInvariantAnalysis.Work(10000,()->false)));
    }

    @ParameterizedTest @ValueSource(strings={
            "for(int i=0;i<n;i++){sum=(n+17)-17;}",
            "for(int i:new int[]{n,n}){sum=(n+17)-17;}",
            "int i=0;while(i<n){sum=(n+17)-17;i++;}",
            "int i=0;do{sum=(n+17)-17;i++;}while(i<n);"
    })
    void generalLoopsRetainExistingInPlaceArithmeticSupport(String loop) {
        String source=sample(loop);String generated=changed(source);
        assertFalse(generated.contains("_mathInvariant"));
        assertFalse(generated.contains("(n+17)-17"));
    }

    private static void assertNoHoist(String source) {
        var analysis=analyze(source);
        assertTrue(analysis.evidence().stream().noneMatch(e->e.scheduling()!=null),analysis.evidence().toString());
    }

    private static String sample(String loop) {
        return "public class Sample { public static long run(int n){int sum=0;"+loop+"return sum;}}";
    }
}
