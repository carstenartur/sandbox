/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavaFiniteSumTest extends JavaContextTestSupport {
    private static String sample(String loop) {
        return "public class Sample {public static int run(int n){int sum=n;"+loop+"return sum;}}";
    }
    @Test void arithmeticProgressionLosesLoopAndContributes5050() throws Exception {
        String source=sample("for(int i=1;i<=100;i++) sum+=i;");
        String generated=changed(source);
        assertFalse(generated.contains("for("),generated);
        assertTrue(generated.contains("5050"),generated);
        equivalent(source,generated);
    }
    @ParameterizedTest @ValueSource(strings={
        "for(int i=100;i>=1;--i) sum+=i;",
        "for(int i=1;i<=100;i++) sum+=n;",
        "for(int i=-20;i<30;i++) sum+=i*i;",
        "for(int i=1;i<100;i++) sum-=i*n;",
        "for(int i=1;i<100;i++) sum=sum+i*i*n;",
        "for(int i=1;i<100;i++) sum+=i*i*i;",
        "for(int i=1;i<=100;i++) sum+=i;for(int j=1;j<=100;j++) sum+=j;"
    }) void polynomialRangesParametersAndOverflow(String loop) throws Exception {
        String source=sample(loop),generated=changed(source);
        assertFalse(generated.contains("for("),generated);
        equivalent(source,generated);
    }
    @ParameterizedTest @ValueSource(strings={
        "for(int i=1;i<1;i++) sum+=i*n;",
        "for(int i=1;i<=1;i++) sum+=i;"
    }) void emptyAndSingletonAreSemanticallySafe(String loop) throws Exception {
        String source=sample(loop);
        var result=analyze(source);
        var document=new org.eclipse.jface.text.Document(source);
        result.newEdit().apply(document);
        equivalent(source,document.get());
    }
    @ParameterizedTest @ValueSource(strings={
        "for(int i=1;i<=Integer.MAX_VALUE;i++) sum+=i;",
        "for(int i=Integer.MIN_VALUE;i>=Integer.MIN_VALUE;i--) sum+=i;",
        "for(int i=1;i<100;i++) {sum+=i;i++;}",
        "for(int i=1;i<100;i++) {sum+=i;n++;}",
        "for(int i=1;i<100;i++) {sum+=i;break;}",
        "for(int i=1;i<100;i++) {sum+=i;return sum;}",
        "for(int i=1;i<100;i++) {sum+=i;if(n==0) throw new RuntimeException();}",
        "for(int i=1;i<100;i++) sum+=Math.abs(i);",
        "for(int i=1;i<100;i++) sum+=i/n;"
    }) void unsafeLoopsStay(String loop) {
        var result=analyze(sample(loop));
        assertTrue(result.evidence().stream().noneMatch(e->e.original().startsWith("for(")),result.toString());
    }
    @Test void longOperationsCanReadIntCounterWithoutNarrowIntermediateArithmetic() throws Exception {
        String source="public class Sample {public static long run(int n){long sum=n;for(int i=1;i<=100;i++) sum+=1L*i*i*n;return sum;}}";
        String generated=changed(source);
        assertFalse(generated.contains("for("),generated);
        equivalent(source,generated);
        String mixed=source.replace("1L*i*i*n","i*i*n");
        assertTrue(analyze(mixed).evidence().stream().noneMatch(e->e.original().startsWith("for(")));
    }
    @Test void fieldsArraysAndVolatileInputsStay() {
        for(String term:java.util.List.of("field*i","values[i]","i*call()")) {
            String source="class Sample {static volatile int field;static int call(){return 1;} int run(int n){int[] values=new int[100];int sum=0;for(int i=1;i<100;i++) sum+="+term+";return sum;}}";
            assertTrue(analyze(source).evidence().stream().noneMatch(e->e.original().startsWith("for(")));
        }
        String source="class Sample {int sum;int run(int n){for(int i=1;i<100;i++) sum+=i;return sum;}}";
        assertTrue(analyze(source).evidence().stream().noneMatch(e->e.original().startsWith("for(")));
    }
    @Test void selectionCancellationAndBudgetRemainBoundaries() {
        String source=sample("for(int i=1;i<=100;i++) sum+=i;");
        var ast=JavaEarlyExitGuardFactsTest.parse(source);
        int start=source.indexOf("for("),end=source.indexOf("return sum");
        assertFalse(MathematicalAnalysis.analyze(ast,source,options(),null,start,3).changed());
        assertTrue(MathematicalAnalysis.analyze(ast,source,options(),null,start,end-start).changed());
        var low=new MathCleanUpOptions(true,options().kinds(),options().safety(),options().goal(),1,1,false,17,java.util.List.of());
        assertFalse(MathematicalAnalysis.analyze(ast,source,low,null,-1,0).changed());
        var monitor=new org.eclipse.core.runtime.NullProgressMonitor();monitor.setCanceled(true);
        assertFalse(MathematicalAnalysis.analyze(ast,source,options(),monitor,-1,0).changed());
        assertFalse(MathematicalAnalysis.analyze(ast,source.replace("100","101"),options(),null,-1,0).changed());
    }

    @Test void longResultWidensFreeIntMultiplicationBeforeOverflow() throws Exception {
        String source="public class Sample {public static long run(int n){long sum=n;for(int i=0;i<=1;i++) sum+=1L*i*n*n;return sum;}}";
        String generated=changed(source);
        assertFalse(generated.contains("for("),generated);
        equivalent(source,generated);
    }
    @Test void numericalEvidenceIsDistinctAndReplayedInReviewAndJson() throws Exception {
        var result=analyze(sample("for(int i=1;i<=100;i++) sum+=i;"));
        var receipt=result.evidence().getFirst();
        assertNull(receipt.proof()); assertNull(receipt.scheduling()); assertNotNull(receipt.finiteSum());
        var finite=receipt.finiteSum();
        assertTrue(finite.verify(options().optimizationBudget(50000),()->false).verified());
        assertEquals(java.math.BigInteger.valueOf(100),finite.checkedCost().originalOperations());
        assertEquals(1,finite.checkedCost().resultOperations());
        assertNotEquals(finite.source().state(),((de.regelsuche.ast.VariableExpr)finite.source().initial()).symbol().orElseThrow());
        assertTrue(MathReviewReport.ranked(result.evidence(),options()).getFirst().assessment().cautions().stream().anyMatch(t->t.contains("telescoping")));
        String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result);
        assertTrue(json.contains("finiteSum")); assertTrue(json.contains("antidifference"));
    }
    @Test void tamperedProofResultCostAndSourceAreRejected() {
        String source=sample("for(int i=1;i<=100;i++) sum+=i;");
        var result=analyze(source); var receipt=result.evidence().getFirst(); var finite=receipt.finiteSum();
        var wrongResult=new FiniteSumEvidence(finite.sourceSha256(),finite.source(),finite.certificate(),new de.regelsuche.ast.NumberExpr(5051),finite.bindings(),finite.checkedCost());
        var wrongProof=new FiniteSumEvidence(finite.sourceSha256(),finite.source(),
                new de.regelsuche.sdk.optimization.iteration.FiniteIterationOptimizer.Certificate(finite.source(),new de.regelsuche.ast.NumberExpr(0)),
                finite.emitted(),finite.bindings(),finite.checkedCost());
        var wrongCost=new FiniteSumEvidence(finite.sourceSha256(),finite.source(),finite.certificate(),finite.emitted(),finite.bindings(),
                new de.regelsuche.sdk.optimization.iteration.FiniteIterationOptimizer.Cost(java.math.BigInteger.ONE,0));
        for(var fake:java.util.List.of(wrongResult,wrongProof,wrongCost)) {
            assertThrows(IllegalArgumentException.class,()->fake.verify(options().optimizationBudget(50000),()->false));
            var tampered=new MathematicalAnalysis.VerifiedRegion(receipt.offset(),receipt.length(),null,receipt.cost(),receipt.original(),receipt.replacement(),null,receipt.sourcePlan(),null,fake);
            assertEquals("UNASSESSED",MathReviewReport.ranked(java.util.List.of(tampered),options()).getFirst().assessment().disposition());
        }
        String generated=source.substring(0,receipt.offset())+receipt.replacement()+source.substring(receipt.offset()+receipt.length());
        var before=JavaEarlyExitGuardFactsTest.parse(source);var after=JavaEarlyExitGuardFactsTest.parse(generated);
        JavaFiniteSumOptimizer.verify(before,after,source,generated,receipt,result.replacements(),options(),50000,()->false);
        assertThrows(IllegalArgumentException.class,()->JavaFiniteSumOptimizer.verify(before,after,source+" ",generated,receipt,result.replacements(),options(),50000,()->false));
        String changed=generated.replace("5050","5051");
        assertThrows(IllegalArgumentException.class,()->JavaFiniteSumOptimizer.verify(before,JavaEarlyExitGuardFactsTest.parse(changed),source,changed,receipt,result.replacements(),options(),50000,()->false));
        String shadowed=generated.replace("int sum=n;","int sum=1;");
        assertThrows(IllegalArgumentException.class,()->JavaFiniteSumOptimizer.verify(before,JavaEarlyExitGuardFactsTest.parse(shadowed),source,shadowed,receipt,result.replacements(),options(),50000,()->false));
    }

    @Test void unrelatedLiteralTamperingCannotReuseSourceReceipt() {
        String source=sample("for(int i=1;i<=100;i++) sum+=i;").replace("int sum=n;","int sum=17;");
        var result=analyze(source);var receipt=result.evidence().getFirst();
        String generated=(source.substring(0,receipt.offset())+receipt.replacement()+source.substring(receipt.offset()+receipt.length())).replace("sum=17","sum=18");
        assertThrows(IllegalArgumentException.class,()->JavaFiniteSumOptimizer.verify(JavaEarlyExitGuardFactsTest.parse(source),
                JavaEarlyExitGuardFactsTest.parse(generated),source,generated,receipt,result.replacements(),options(),50000,()->false));
    }

    @Test void combinedHoistAndSumKeepOriginalDeclarationBindings() throws Exception {
        String source=sample("for(int i=0;i<8;i++){sum^=(n+7)*(n-7);}for(int j=1;j<=100;j++) sum+=j*n;");
        var result=analyze(source);
        assertEquals(1,result.evidence().stream().filter(e->e.scheduling()!=null).count(),result.diagnostics().toString());
        assertEquals(1,result.evidence().stream().filter(e->e.finiteSum()!=null).count(),result.diagnostics().toString());
        equivalent(source,changed(source));
    }
    @ParameterizedTest @ValueSource(strings={
        "for(int i=0;i<100;i++) sum+=i*i*1L;",
        "for(int i=0;i<100;i++) sum+=(long)(i*i);",
        "for(int i=0;i<100;i++) sum+=-i;",
        "for(int i=0;i<100;i++) sum+=sum*i;"
    }) void everyLongIntermediateAndAdditiveStateMustBeAdmitted(String loop) {
        String source="public class Sample {public static long run(int n){long sum=n;"+loop+"return sum;}}";
        assertTrue(analyze(source).evidence().stream().noneMatch(e->e.finiteSum()!=null));
    }
    @Test void hugeNonWrappingCountUsesBoundedProofAndCompactEmission() {
        String source=sample("for(int i=Integer.MIN_VALUE;i<Integer.MAX_VALUE;i++) sum+=i;");
        var result=analyze(source);var finite=result.evidence().getFirst().finiteSum();
        assertEquals(new java.math.BigInteger("4294967295"),finite.source().range().count());
        assertTrue(result.replacements().getFirst().replacement().length()<100);
        assertTrue(finite.verify(options().optimizationBudget(50000),()->false).verified());
    }
    @Test void inFlightCancellationAndReplayWorkLimitsFailClosed() {
        String source=sample("for(int i=1;i<=100;i++) sum+=i*i*n;");
        int[] calls={0};
        var monitor=new org.eclipse.core.runtime.NullProgressMonitor() {
            @Override public boolean isCanceled(){return ++calls[0]>80;}
        };
        var edits=new java.util.ArrayList<MathematicalAnalysis.Replacement>();
        long remaining=JavaFiniteSumOptimizer.optimize(JavaEarlyExitGuardFactsTest.parse(source),source,options(),monitor,-1,0,
                java.util.Map.of(),null,edits,new java.util.ArrayList<>(),new java.util.ArrayList<>(),1000000);
        assertTrue(edits.isEmpty());assertTrue(remaining>=0 && remaining<=1000000);assertTrue(calls[0]>80);
        var result=analyze(source);var receipt=result.evidence().getFirst();
        String generated=source.substring(0,receipt.offset())+receipt.replacement()+source.substring(receipt.offset()+receipt.length());
        assertThrows(JavaLoopInvariantAnalysis.Limit.class,()->JavaFiniteSumOptimizer.verify(JavaEarlyExitGuardFactsTest.parse(source),
                JavaEarlyExitGuardFactsTest.parse(generated),source,generated,receipt,result.replacements(),options(),1,()->false));
        assertThrows(JavaLoopInvariantAnalysis.Limit.class,()->JavaFiniteSumOptimizer.verify(JavaEarlyExitGuardFactsTest.parse(source),
                JavaEarlyExitGuardFactsTest.parse(generated),source,generated,receipt,result.replacements(),options(),50000,()->true));
    }

    @Test void constantFieldQualifiersCannotHideHeaderSideEffects() {
        String source="public class Sample {static final int LIMIT=100;static Sample next(){throw new RuntimeException();}public static int run(int n){int sum=n;for(int i=1;i<next().LIMIT;i++) sum+=i;return sum;}}";
        assertTrue(analyze(source).evidence().stream().noneMatch(e->e.finiteSum()!=null));
        String qualified="public class Sample {static final int LIMIT=100;static Sample NULL;Sample member;public static int run(int n){int sum=n;for(int i=1;i<NULL.member.LIMIT;i++) sum+=i;return sum;}}";
        assertTrue(analyze(qualified).evidence().stream().noneMatch(e->e.finiteSum()!=null));
        assertTrue(analyze(qualified.replace("NULL.member.LIMIT","NULL.LIMIT")).evidence().stream().noneMatch(e->e.finiteSum()!=null));
    }

    @Test void longModularOverflowAndSignedMinimumLiteralCompileAndExecute() throws Exception {
        String source="public class Sample {public static long run(int n){long sum=Long.MAX_VALUE;for(int i=1;i<=100;i++) sum+=1L*i*n*n;return sum;}}";
        equivalent(source,changed(source));
        String minimum="public class Sample {public static long run(int n){long sum=n;for(int i=0;i<2;i++) sum+=4611686018427387904L;return sum;}}";
        String generated=changed(minimum);
        assertFalse(generated.contains("for("),generated);
        assertTrue(generated.contains("-9223372036854775808L"),generated);
        equivalent(minimum,generated);
    }

}
