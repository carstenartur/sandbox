/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavaFiniteSumFormsTest extends JavaContextTestSupport {
    @ParameterizedTest @ValueSource(strings={
        "for(int i=-20;i<100;i+=3) sum+=i*n;",
        "for(int i=100;i>=-20;i-=3) sum+=i*n;",
        "for(int i=-20;i<100;i=i+3) sum+=i*n;",
        "for(int i=100;i>=-20;i=i-3) sum+=i*n;",
        "for(int i=-20;i<100;i=3+i) sum+=i*n;",
        "for(int i=0;i<100;i++){final int square=i*i;int term=square*n;sum+=term;}",
        "for(int i=0;i<100;i++){sum+=i;sum-=i*i;}",
        "for(int i=0;i<100;i++){/*inner*/sum+=i/*term*/;}"
    }) void admittedFormsCompileAndPreserveModularResults(String loop) throws Exception {
        String source="public class Sample {public static int run(int n){int sum=n;"+loop+"return sum;}}";
        String generated=changed(source);
        assertFalse(generated.contains("for("),generated);
        if(source.contains("/*inner*/")) {assertTrue(generated.contains("/*inner*/"));assertTrue(generated.contains("/*term*/"));}
        equivalent(source,generated);
    }
    @Test void profitableWholeLoopBeatsOverlappingHoist() throws Exception {
        String source="public class Sample {public static int run(int n){int sum=n;for(int i=0;i<100;i++) sum+=(n+7)*(n-7);return sum;}}";
        var analysis=analyze(source);
        assertEquals(1,analysis.evidence().stream().filter(e->e.finiteSum()!=null).count(),analysis.toString());
        String generated=changed(source);assertFalse(generated.contains("for("),generated);equivalent(source,generated);
    }
    @Test void independentAccumulatorsPreserveAllOutputs() throws Exception {
        String source="public class Sample {public static int run(int n){int sum=n,other=n;for(int i=0;i<100;i++){sum+=i*n;other-=i*i;}return sum^other;}}";
        String generated=changed(source);assertFalse(generated.contains("for("),generated);equivalent(source,generated);
    }
    @ParameterizedTest @ValueSource(strings={
        "for(long i=-20L;i<100L;i+=3L) sum+=i*n;",
        "for(long i=100L;i>=-20L;i=i-3L) sum+=i*n;"
    }) void longCountersUseMathematicalCounts(String loop) throws Exception {
        String source="public class Sample {public static long run(int n){long sum=n;"+loop+"return sum;}}";
        String generated=changed(source);assertFalse(generated.contains("for("),generated);equivalent(source,generated);
    }
    @ParameterizedTest @ValueSource(strings={
        "int i=1;while(i<=100){sum+=i;i+=3;}",
        "int i=100;while(i>=1){sum+=i;i-=3;}",
        "int i=1;do{sum+=i;i+=3;}while(i<=100);"
    }) void canonicalLoopsPreserveLiveFinalCounter(String loop) throws Exception {
        String source="public class Sample {public static int run(int n){int sum=n;"+loop+"return sum^i;}}";
        String generated=changed(source);assertFalse(generated.contains("while("),generated);equivalent(source,generated);
    }
    @ParameterizedTest @ValueSource(strings={
        "java.util.stream.IntStream.rangeClosed(-20,100).map(i->i*i*n).sum()",
        "java.util.stream.IntStream.range(-20,100).sum()"
    }) void sequentialIntRangesUseSameProof(String expression) throws Exception {
        String source="public class Sample {public static int run(int n){return "+expression+";}}";
        String generated=changed(source);assertFalse(generated.contains(".sum()"),generated);equivalent(source,generated);
    }
    @Test void singletonDoAndEmptyWhileRetainTheirExecutionCounts() throws Exception {
        for(String loop:java.util.List.of("int i=101;do{sum+=i*n;i+=3;}while(i<=100);",
                "int i=101;while(i<=100){sum+=i*n;i+=3;}")) {
            String source="public class Sample {public static int run(int n){int sum=n;"+loop+"return sum^i;}}";
            var result=analyze(source);var document=new org.eclipse.jface.text.Document(source);result.newEdit().apply(document);
            equivalent(source,document.get());
        }
    }
    @Test void streamCommentsAndExplicitSequentialStageSurvive() throws Exception {
        String source="public class Sample {public static int run(int n){return java.util.stream.IntStream.range(0,100).sequential().map(i->i/*term*/*n).sum();}}";
        String generated=changed(source);assertFalse(generated.contains(".sum()"),generated);
        assertTrue(generated.contains("/*term*/"));equivalent(source,generated);
    }
    @Test void sequentialLongRangeUsesLongRing() throws Exception {
        String source="public class Sample {public static long run(int n){return java.util.stream.LongStream.rangeClosed(-20L,100L).map(i->i*i*n).sum();}}";
        String generated=changed(source);assertFalse(generated.contains(".sum()"),generated);equivalent(source,generated);
    }
    @Test void signedMinimumLiteralAndHugeNonunitRangeRemainBounded() throws Exception {
        String source="public class Sample {public static long run(int n){long sum=n;for(long i=-9223372036854775808L;i<-9223372036854775700L;i+=3L) sum+=i*n;return sum;}}";
        String generated=changed(source);assertFalse(generated.contains("for("),generated);equivalent(source,generated);
        String huge="class Sample {int run(int n){int sum=n;for(int i=Integer.MIN_VALUE;i<Integer.MAX_VALUE;i+=2) sum+=i;return sum;}}";
        // The last update wraps; a mathematically finite index range cannot justify this Java loop.
        assertTrue(analyze(huge).evidence().stream().noneMatch(e->e.finiteSum()!=null));
        String safe=huge.replace("Integer.MAX_VALUE","Integer.MAX_VALUE-1");
        var evidence=analyze(safe).evidence().stream().filter(e->e.finiteSum()!=null).findFirst().orElseThrow();
        assertEquals(new java.math.BigInteger("2147483647"),evidence.finiteSum().source().range().count());
        assertTrue(evidence.replacement().length()<100);
    }
    @Test void combinedMultipleOutputsStreamsAndLiveCounterRejectTamperedReplay() throws Exception {
        String source="public class Sample {public static int run(int n){int sum=n,other=n;int i=1;while(i<=100){sum+=i*n;other-=i*i;i+=3;}return (sum^other^i)+java.util.stream.IntStream.range(0,100).sum();}}";
        var result=analyze(source);
        assertEquals(2,result.evidence().stream().filter(e->e.finiteSum()!=null).count(),result.diagnostics().toString());
        var receipt=result.evidence().stream().filter(e->e.finiteSum()!=null && !e.finiteSum().additional().isEmpty()).findFirst().orElseThrow();
        assertEquals(java.math.BigInteger.valueOf(136),receipt.finiteSum().totalCost().originalOperations());
        assertTrue(receipt.finiteSum().description().contains("136"),receipt.finiteSum().description());
        assertNotEquals("UNASSESSED",MathReviewReport.ranked(java.util.List.of(receipt),options()).getFirst().assessment().disposition());
        String generated=changed(source);equivalent(source,generated);
        var before=JavaEarlyExitGuardFactsTest.parse(source);
        JavaFiniteSumOptimizer.verify(before,JavaEarlyExitGuardFactsTest.parse(generated),source,generated,receipt,result.replacements(),options(),50000,()->false);
        String corrupted=generated.replace("i = 103;","i = 104;");
        assertNotEquals(generated,corrupted);
        assertThrows(IllegalArgumentException.class,()->JavaFiniteSumOptimizer.verify(before,JavaEarlyExitGuardFactsTest.parse(corrupted),source,corrupted,receipt,result.replacements(),options(),50000,()->false));
        var proof=receipt.finiteSum();var extra=proof.additional().getFirst();
        var forgedExtra=new FiniteSumEvidence.ReductionProof(extra.source(),extra.certificate(),new de.regelsuche.ast.NumberExpr(0),extra.bindings(),extra.checkedCost());
        var forged=new FiniteSumEvidence(proof.sourceSha256(),proof.source(),proof.certificate(),proof.emitted(),proof.bindings(),proof.checkedCost(),java.util.List.of(forgedExtra));
        assertThrows(IllegalArgumentException.class,()->forged.verify(options().optimizationBudget(50000),()->false));
    }
    @ParameterizedTest @ValueSource(strings={
        "java.util.stream.IntStream.range(0,100).parallel().map(i->i*n).sum()",
        "java.util.stream.IntStream.range(0,100).map(i->Math.abs(i)).sum()",
        "java.util.stream.IntStream.range(0,100).map(i->field*i).sum()"
    }) void parallelAndEffectfulStreamsStay(String expression) {
        String source="class Sample {static int field;static int run(int n){return "+expression+";}}";
        assertTrue(analyze(source).evidence().stream().noneMatch(e->e.finiteSum()!=null));
    }
    @ParameterizedTest @ValueSource(strings={
        "for(int i=0;i<100;i+=0) sum+=i;",
        "for(int i=Integer.MAX_VALUE-1;i<=Integer.MAX_VALUE;i+=2) sum+=i;",
        "for(int i=0;i<100;i++){int t=i*i;t++;sum+=t;}",
        "for(int i=0;i<100;i++){sum+=i;sum+=sum;}",
        "for(int i=0;i<100;i++){int t=call();sum+=t;}",
        "int i=0;while(i<100){sum+=i;if(n==0)continue;i++;}"
    }) void effectsDependentStateAndWrappingCountersStay(String loop) {
        String source="class Sample {static int call(){return 1;}static int run(int n){int sum=n;"+loop+"return sum;}}";
        assertTrue(analyze(source).evidence().stream().noneMatch(e->e.finiteSum()!=null));
    }
    @Test void headerConstantsObeyExpressionTraversalLimits() {
        String bound="(".repeat(80)+"100"+")".repeat(80);
        String source="class Sample {int run(int n){int sum=n;for(int i=0;i<"+bound+";i++)sum+=i;return sum;}}";
        var unit=JavaEarlyExitGuardFactsTest.parse(source);
        var loops=new java.util.ArrayList<org.eclipse.jdt.core.dom.ForStatement>();
        unit.accept(new org.eclipse.jdt.core.dom.ASTVisitor(){
            @Override public boolean visit(org.eclipse.jdt.core.dom.ForStatement loop){loops.add(loop);return false;}
        });
        assertThrows(IllegalArgumentException.class,()->JavaFiniteSumAnalysis.inspect(unit,loops.getFirst(),new JavaLoopInvariantAnalysis.Work(50000,()->false)));
    }
}
