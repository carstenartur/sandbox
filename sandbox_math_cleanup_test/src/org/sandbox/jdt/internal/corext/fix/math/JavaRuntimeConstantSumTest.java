/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavaRuntimeConstantSumTest extends JavaContextTestSupport {
    private static String source(String width,String loop) {
        return "public class Sample {public static "+width+" run(int a,int b,"+width+" initial){"+width+" sum=initial;"+loop+"return sum;}}";
    }
    private static MathematicalAnalysis.VerifiedRegion receipt(String source) {
        return analyze(source).evidence().stream().filter(e->e.runtimeIteration()!=null).findFirst().orElseThrow();
    }
    @Test void constantUpdatesUsePrimitiveArithmeticAcrossCrossoverAndAccumulatorWrap() throws Exception {
        for(String width:List.of("int","long")) for(String term:List.of("1","-3",width.equals("int")?"2147483647":"9223372036854775807L")) {
            String original=source(width,"for(int i=a;i<b;i++)sum+="+term+";");
            var evidence=receipt(original);String generated=changed(original);
            assertFalse(generated.contains("java.math.BigInteger"),generated);
            assertFalse(generated.contains("for(int i=a;i<b;i++)"),generated);
            assertFalse(generated.contains("count>="),generated);
            assertFalse(generated.contains("(int)("),generated);
            assertTrue(generated.length()<1400,generated);
            assertTrue(evidence.runtimeIteration().minimumIterations()<4096);
            assertTrue(evidence.cost().candidateScore()<evidence.cost().sourceScore());
            int crossover=Math.toIntExact(evidence.runtimeIteration().minimumIterations());
            Class<?> type=width.equals("int")?int.class:long.class;
            try(var before=compile(original);var after=compile(generated)) {
                var reference=before.loadClass("Sample").getMethod("run",int.class,int.class,type);
                var actual=after.loadClass("Sample").getMethod("run",int.class,int.class,type);
                for(int distance:new int[]{-17,0,1,crossover-1,crossover,crossover+1,50000})
                    for(long initial:new long[]{0,17,width.equals("int")?Integer.MIN_VALUE:Long.MIN_VALUE,width.equals("int")?Integer.MAX_VALUE:Long.MAX_VALUE}) {
                        Object value=width.equals("int")?(Object)(int)initial:initial;
                        assertEquals(reference.invoke(null,0,distance,value),actual.invoke(null,0,distance,value));
                    }
            }
        }
    }
    @Test void generalHeadersRetainEmptyOnceStrideAndLiveCounterSemantics() throws Exception {
        for(String loop:List.of("for(int i=a;i<=b;i+=3)sum-=7;","for(int i=a;i>=b;i-=3)sum+=5;","for(int i=a;i>b;i--)sum+=5;",
                "int i=a;while(i<b){sum+=1;i++;}sum+=i;",
                "int i=a;do{sum+=1;i+=3;}while(i<=b);sum+=i;",
                "int i=a;do{sum+=1;i-=3;}while(i>=b);sum+=i;",
                "int i=99;for(i=a;i<b;i++)sum+=1;sum+=i;")) {
            String original=source("int",loop),generated=changed(original);
            assertFalse(generated.contains("java.math.BigInteger"),generated);
            try(var before=compile(original);var after=compile(generated)) {
                var reference=before.loadClass("Sample").getMethod("run",int.class,int.class,int.class);
                var actual=after.loadClass("Sample").getMethod("run",int.class,int.class,int.class);
                for(int[] input:new int[][]{{0,-17},{0,0},{0,1},{0,100},{0,50000},{50000,0},
                        {Integer.MIN_VALUE+20,Integer.MIN_VALUE+21},{Integer.MAX_VALUE-20,Integer.MAX_VALUE-21}})
                    assertEquals(reference.invoke(null,input[0],input[1],Integer.MAX_VALUE),actual.invoke(null,input[0],input[1],Integer.MAX_VALUE),loop);
            }
        }
    }
    @Test void nativeHeaderOverflowAndHugeIntCountArePreserved() throws Exception {
        String original=source("int","for(int i=a-16;i<a+b;i++)sum+=1;"),generated=changed(original);
        assertFalse(generated.contains("java.math.BigInteger"));
        try(var before=compile(original);var after=compile(generated)) {
            var reference=before.loadClass("Sample").getMethod("run",int.class,int.class,int.class);
            var actual=after.loadClass("Sample").getMethod("run",int.class,int.class,int.class);
            for(int[] input:new int[][]{{Integer.MAX_VALUE,50000},{Integer.MIN_VALUE,0},{0,16},{17,-20}})
                assertEquals(reference.invoke(null,input[0],input[1],17),actual.invoke(null,input[0],input[1],17));
        }
        // 2^32-1 iterations: hand-derived results, without executing the original loop.
        for(String width:List.of("int","long"))for(String loop:List.of("for(int i=a;i<b;i++)sum+=1;",
                "for(int i=a;i>b;i--)sum+=1;","for(int i=a;i<=b-1;i++)sum+=1;"))
            try(var after=compile(changed(source(width,loop)))) {
                Object initial=width.equals("int")?(Object)17:17L;
                Object expected=width.equals("int")?(Object)16:4294967312L;
                boolean descending=loop.contains("i--");
                assertEquals(expected,after.loadClass("Sample").getMethod("run",int.class,int.class,width.equals("int")?int.class:long.class)
                        .invoke(null,descending?Integer.MAX_VALUE:Integer.MIN_VALUE,descending?Integer.MIN_VALUE:Integer.MAX_VALUE,initial));
            }
        try(var after=compile(changed(source("int","for(int i=a;i<=b;i+=2)sum+=1;")))) {
            assertEquals(-2147483632,after.loadClass("Sample").getMethod("run",int.class,int.class,int.class)
                    .invoke(null,Integer.MIN_VALUE,Integer.MAX_VALUE-2,17));
        }
    }
    @Test void overflowingGeneralCounterTakesOriginalFallback() throws Exception {
        String loop="for(int i=a;i<=b;i+=3)sum+=1;";
        String generated=changed(source("int",loop));assertFalse(generated.contains("java.math.BigInteger"));
        try(var probe=compile(generated.replace(loop,"sum=-123;"))) {
            var run=probe.loadClass("Sample").getMethod("run",int.class,int.class,int.class);
            assertEquals(-123,run.invoke(null,Integer.MAX_VALUE-100,Integer.MAX_VALUE,17));
            assertEquals(51,run.invoke(null,0,100,17));
        }
    }
    @Test void orderedConstantUpdatesAndMultipleTargetsKeepTheirPrimitiveRings() throws Exception {
        String original=source("int","int other=initial;for(int i=a;i<b;i++){sum+=2147483647;sum+=2147483647;other-=7;}sum+=other;");
        String generated=changed(original);assertFalse(generated.contains("java.math.BigInteger"));
        try(var before=compile(original);var after=compile(generated)) {
            var reference=before.loadClass("Sample").getMethod("run",int.class,int.class,int.class);
            var actual=after.loadClass("Sample").getMethod("run",int.class,int.class,int.class);
            for(int n:new int[]{-1,0,1,15,16,17,50000})for(int initial:new int[]{Integer.MIN_VALUE,17,Integer.MAX_VALUE})
                assertEquals(reference.invoke(null,0,n,initial),actual.invoke(null,0,n,initial));
        }
    }
    @Test void otherModelsAndHeaderWidthsKeepTheirExistingExactTemplates() {
        for(String loop:List.of("for(long i=a;i<b;i++)sum+=1;","for(int i=a;i<(long)b;i++)sum+=1;",
                "for(int i=a;i<b;i++)sum+=i;","for(int i=a;i<b;i++)sum+=a;",
                "for(int i=a;i<b;i++)sum*=3;","for(int i=a;i<b;i++){if(i%2==0)sum+=1;else sum+=2;}"))
            assertTrue(changed(source("int",loop)).contains("java.math.BigInteger"),loop);
        String excessive=source("int","for(int i=a;i<b;i+=9223372036854775807L)sum+=1;");
        assertTrue(analyze(excessive).evidence().stream().noneMatch(e->e.runtimeIteration()!=null));
    }
    @Test void forgedStrategyCoefficientsInitialCostAndTemplateCannotReuseProof() {
        String source=source("int","for(int i=a;i<b;i++)sum+=1;");
        var receipt=receipt(source);var proof=receipt.runtimeIteration();var model=proof.sums().getFirst();
        for(var forged:List.of(proof.withStrategy(RuntimeIterationEvidence.EmissionStrategy.BIG_INTEGER),
                proof.withStrategy(RuntimeIterationEvidence.EmissionStrategy.PRIMITIVE_GUARDED),
                new RuntimeIterationEvidence(proof.sourceSha256(),proof.bindings(),List.of(
                        new de.regelsuche.sdk.optimization.iteration.PolynomialSumPlan(model.source(),model.initial(),List.of(new de.regelsuche.ast.NumberExpr(2)))),
                        null,null,null,proof.strategy()),
                new RuntimeIterationEvidence(proof.sourceSha256(),proof.bindings(),List.of(
                        new de.regelsuche.sdk.optimization.iteration.PolynomialSumPlan(model.source(),new de.regelsuche.ast.NumberExpr(0),model.coefficients())),
                        null,null,null,proof.strategy())))
            reject(source,receipt,forged,receipt.replacement(),receipt.cost());
        reject(source,receipt,proof,receipt.replacement(),proof.withStrategy(RuntimeIterationEvidence.EmissionStrategy.BIG_INTEGER).cost(options().goal()));
        String mutated=receipt.replacement().replace("count<0L","count>0L");assertNotEquals(receipt.replacement(),mutated);
        reject(source,receipt,proof,mutated,receipt.cost());
    }
    private static void reject(String source,MathematicalAnalysis.VerifiedRegion receipt,RuntimeIterationEvidence proof,String replacement,
            de.regelsuche.sdk.optimization.OptimizationResult.CostAssessment cost) {
        var plan=new JavaSourceRewritePlan("Source.java",JavaFiniteSumOptimizer.hash(source),source.length(),receipt.offset(),receipt.length(),List.of(),List.of(),java.util.Map.of(),
                List.of(new JavaSourceRewritePlan.Change(receipt.offset(),receipt.length(),JavaFiniteSumOptimizer.hash(receipt.original()),replacement)));
        var fake=new MathematicalAnalysis.VerifiedRegion(receipt.offset(),receipt.length(),null,cost,receipt.original(),replacement,null,plan,null,null,proof);
        String generated=source.substring(0,receipt.offset())+replacement+source.substring(receipt.offset()+receipt.length());
        var edit=new MathematicalAnalysis.Replacement(receipt.offset(),receipt.length(),replacement,"forged");
        assertThrows(IllegalArgumentException.class,()->JavaRuntimeIterationOptimizer.verify(JavaEarlyExitGuardFactsTest.parse(source),
                JavaEarlyExitGuardFactsTest.parse(generated),source,generated,fake,List.of(edit),options(),500000,()->false));
    }
}
