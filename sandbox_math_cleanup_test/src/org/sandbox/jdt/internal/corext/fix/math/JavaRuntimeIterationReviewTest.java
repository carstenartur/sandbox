/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jface.text.Document;

class JavaRuntimeIterationReviewTest extends JavaContextTestSupport {
    @Test void innerTerminationUsesItsOwnWidthInBothDirections() throws Exception {
        for(boolean wideOuter:List.of(true,false)) {
            String loop="for("+(wideOuter?"long":"int")+" i=0;i<n;i++)for("+(wideOuter?"int":"long")+" j=0;j<=n;j++)sum+=1;";
            String source="public class Sample {public static long run(int n){long sum=0;"+loop+"return sum;}}";
            String generated=changed(source);
            // Replace only the retained fallback with an observable marker. Never execute the nonterminating original at MAX.
            assertTrue(generated.contains(loop));
            String probe=generated.replace(loop,"return -123L;");
            try(var loader=compile(probe)) {
                var method=loader.loadClass("Sample").getMethod("run",int.class);
                assertEquals(wideOuter?-123L:4611686016279904256L,method.invoke(null,Integer.MAX_VALUE));
                assertEquals(5000L*5001,method.invoke(null,5000));
            }
            try(var before=compile(source);var after=compile(generated)) {
                for(int n:new int[]{-1,0,1,100,5000})assertEquals(before.loadClass("Sample").getMethod("run",int.class).invoke(null,n),after.loadClass("Sample").getMethod("run",int.class).invoke(null,n));
            }
        }
    }
    @Test void earlierIdentityObservationAcrossEnclosingBackedgeRemainsObservable() throws Exception {
        String source="public class Sample {public static int run(int n){java.math.BigInteger sum=java.math.BigInteger.ONE;java.math.BigInteger initial=sum;int hits=0;for(int k=0;k<2;k++){if(sum==initial)hits++;for(int i=0;i<n;i++){sum=sum.add(java.math.BigInteger.ONE);sum=sum.subtract(java.math.BigInteger.ONE);}}return hits;}}";
        var result=analyze(source);var document=new Document(source);result.newEdit().apply(document);
        try(var before=compile(source);var after=compile(document.get())) {
            assertEquals(1,before.loadClass("Sample").getMethod("run",int.class).invoke(null,50000));
            assertEquals(1,after.loadClass("Sample").getMethod("run",int.class).invoke(null,50000));
        }
        assertTrue(result.evidence().stream().noneMatch(e->e.runtimeIteration()!=null));
    }
    @Test void collidingCommentNamingConsumesWorkBeforeBuildingOversizedOutput() {
        String base="public class Sample {public static int run(int n){int sum=0;for(int i=1;i<=n;i++)sum+=i;return sum;}}";
        var receipt=analyze(base).evidence().stream().filter(e->e.runtimeIteration()!=null).findFirst().orElseThrow();
        String source=base+"/*$series"+receipt.offset()+"_"+"x".repeat(2000)+"*/";
        var ast=JavaEarlyExitGuardFactsTest.parse(source);
        var context=JavaRuntimeIterationAnalysis.inspect(ast,NodeFinder.perform(ast,receipt.offset(),receipt.length()),new JavaLoopInvariantAnalysis.Work(100000,()->false));
        var work=new JavaLoopInvariantAnalysis.Work(32,()->false);
        assertThrows(JavaLoopInvariantAnalysis.Limit.class,()->JavaRuntimeIterationEmitter.emit(ast,source,context,receipt.runtimeIteration(),work));
        assertThrows(JavaLoopInvariantAnalysis.Limit.class,()->JavaRuntimeIterationEmitter.emit(ast,source,context,receipt.runtimeIteration(),new JavaLoopInvariantAnalysis.Work(100000,()->true)));
        String emitted=JavaRuntimeIterationEmitter.emit(ast,source,context,receipt.runtimeIteration(),new JavaLoopInvariantAnalysis.Work(500000,()->false));
        assertTrue(emitted.length()<12000,"Comment collisions must not grow generated identifiers with the comment length");
    }
    @Test void boundedOutputAppendIsAtomicAndCancellable() {
        var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        var work=new JavaLoopInvariantAnalysis.Work(100000,cancelled::get);
        var output=new JavaRuntimeIterationEmitter.Output(work);output.append("prefix");
        assertThrows(IllegalArgumentException.class,()->output.append("x".repeat(RuntimeIterationEvidence.MAX_EMITTED_CHARACTERS)));
        assertEquals("prefix",output.toString());
        cancelled.set(true);assertThrows(JavaLoopInvariantAnalysis.Limit.class,()->output.append("more"));
        cancelled.set(false);assertEquals("prefix",output.toString());
    }
    @Test void finiteNameAttemptLimitAndIntermediateFractionLimitsFailClosed() {
        String base="public class Sample {public static int run(int n){int sum=0;for(int i=1;i<=n;i++)sum+=i;return sum;}}";
        var receipt=analyze(base).evidence().stream().filter(e->e.runtimeIteration()!=null).findFirst().orElseThrow();
        var collision=new StringBuilder(base).append("/*");
        for(int attempt=0;attempt<RuntimeIterationEvidence.MAX_NAME_ATTEMPTS;attempt++)collision.append("$series").append(receipt.offset()).append('_').append(attempt).append("_ ");
        String source=collision.append("*/").toString();var ast=JavaEarlyExitGuardFactsTest.parse(source);
        var context=JavaRuntimeIterationAnalysis.inspect(ast,NodeFinder.perform(ast,receipt.offset(),receipt.length()),new JavaLoopInvariantAnalysis.Work(100000,()->false));
        var exhausted=assertThrows(IllegalArgumentException.class,()->JavaRuntimeIterationEmitter.emit(ast,source,context,receipt.runtimeIteration(),new JavaLoopInvariantAnalysis.Work(500000,()->false)));
        assertEquals("RUNTIME_NAME_ATTEMPT_LIMIT",exhausted.getMessage());
        var original=receipt.runtimeIteration();var model=original.sums().getFirst();
        var huge=new de.regelsuche.ast.NumberExpr(new de.regelsuche.scalar.ExactRational(java.math.BigInteger.ONE,java.math.BigInteger.ONE.shiftLeft(RuntimeIterationEvidence.MAX_RESULT_BITS)));
        var halfBound=new de.regelsuche.ast.NumberExpr(new de.regelsuche.scalar.ExactRational(java.math.BigInteger.ONE,java.math.BigInteger.ONE.shiftLeft(33000)));
        var product=new de.regelsuche.ast.BinaryExpr(halfBound,de.regelsuche.ast.BinaryOperator.MUL,halfBound);
        var power=new de.regelsuche.ast.BinaryExpr(halfBound,de.regelsuche.ast.BinaryOperator.POW,new de.regelsuche.ast.NumberExpr(3));
        var baseAst=JavaEarlyExitGuardFactsTest.parse(base);
        for(var coefficient:List.of(huge,product,power)) {
            var forged=new de.regelsuche.sdk.optimization.iteration.PolynomialSumPlan(model.source(),model.initial(),List.of(coefficient));
            var proof=new RuntimeIterationEvidence(original.sourceSha256(),original.bindings(),List.of(forged),null,null);
            var rejected=assertThrows(IllegalArgumentException.class,()->JavaRuntimeIterationEmitter.emit(baseAst,base,context,proof,new JavaLoopInvariantAnalysis.Work(500000,()->false)));
            assertEquals("RUNTIME_EMISSION_DENOMINATOR_LIMIT",rejected.getMessage());
        }
    }
    @Test void everyEnclosingRepetitionKindPreservesEarlierIdentityUses() throws Exception {
        String body="if(sum==initial)hits++;for(int i=0;i<n;i++){sum=sum.add(java.math.BigInteger.ONE);sum=sum.subtract(java.math.BigInteger.ONE);}";
        for(String loop:List.of("while(k++<2){"+body+"}","do{"+body+"}while(++k<2);","for(int each:new int[]{0,1}){"+body+"}")) {
            String source="public class Sample {public static int run(int n){java.math.BigInteger sum=java.math.BigInteger.ONE;java.math.BigInteger initial=sum;int hits=0;int k=0;"+loop+"return hits;}}";
            var result=analyze(source);var document=new Document(source);result.newEdit().apply(document);
            assertTrue(result.evidence().stream().noneMatch(e->e.runtimeIteration()!=null));
            try(var before=compile(source);var after=compile(document.get())) {
                assertEquals(1,before.loadClass("Sample").getMethod("run",int.class).invoke(null,50000));
                assertEquals(1,after.loadClass("Sample").getMethod("run",int.class).invoke(null,50000));
            }
        }
    }

}
