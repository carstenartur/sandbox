/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.List;
import java.util.Objects;
import de.regelsuche.ast.Expr;
import de.regelsuche.ast.iteration.FiniteIteration;
import de.regelsuche.sdk.optimization.CancellationToken;
import de.regelsuche.sdk.optimization.OptimizationBudget;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.OptimizationResult.CostAssessment;
import de.regelsuche.sdk.optimization.iteration.FiniteIterationOptimizer;
import de.regelsuche.sdk.optimization.iteration.FiniteIterationVerifier;
import de.regelsuche.search.program.PreparedJointComputation.Cost;
import de.regelsuche.symbol.SymbolId;

/** Numerical telescoping evidence, distinct from scalar and scheduling evidence. Public data must be replayed. */
public record FiniteSumEvidence(String sourceSha256, FiniteIteration.Recurrence source,
        FiniteIterationOptimizer.Certificate certificate, Expr emitted, List<Binding> bindings,
        FiniteIterationOptimizer.Cost checkedCost,List<ReductionProof> additional) {
    /** Java admission limits, separate from SDK polynomial degree and proof budgets. */
    public static final int MAX_ACCUMULATORS=8, MAX_TEMPORARIES=32, MAX_ORDERED_UPDATES=32,
            MAX_EXPRESSION_NODES=4096, MAX_EXPRESSION_DEPTH=64;
    public record Binding(SymbolId symbol,int declarationOffset,String name,String primitiveType) { }
    public record ReductionProof(FiniteIteration.Recurrence source,FiniteIterationOptimizer.Certificate certificate,
            Expr emitted,List<Binding> bindings,FiniteIterationOptimizer.Cost checkedCost) {
        public ReductionProof {
            Objects.requireNonNull(source);Objects.requireNonNull(certificate);Objects.requireNonNull(emitted);
            bindings=List.copyOf(bindings);Objects.requireNonNull(checkedCost);
        }
    }
    public FiniteSumEvidence(String sourceSha256,FiniteIteration.Recurrence source,FiniteIterationOptimizer.Certificate certificate,
            Expr emitted,List<Binding> bindings,FiniteIterationOptimizer.Cost checkedCost) {
        this(sourceSha256,source,certificate,emitted,bindings,checkedCost,List.of());
    }
    public FiniteSumEvidence {
        Objects.requireNonNull(sourceSha256); Objects.requireNonNull(source); Objects.requireNonNull(certificate);
        Objects.requireNonNull(emitted); bindings=List.copyOf(bindings); Objects.requireNonNull(checkedCost);additional=List.copyOf(additional);
        if(additional.size()>=MAX_ACCUMULATORS) throw new IllegalArgumentException("FINITE_SUM_OUTPUT_LIMIT"); //$NON-NLS-1$
    }
    public FiniteIterationVerifier.Verification verify(OptimizationBudget budget,CancellationToken token) {
        long started=System.nanoTime();
        long timeout=budget.timeoutMillis()>Long.MAX_VALUE/1000000?Long.MAX_VALUE:budget.timeoutMillis()*1000000;
        // One deadline covers every proof even though the SDK creates a meter for each child call.
        CancellationToken bounded=()->token.isCancelled() || System.nanoTime()-started>=timeout;
        var proof=new FiniteIterationVerifier().verify(source,emitted,certificate,budget,bounded);
        long used=checkedWork(proof,checkedCost,budget.maximumWork());
        for(var reduction:additional) {
            long remaining=budget.maximumWork()-used;
            if(remaining<1) throw new IllegalArgumentException("FINITE_SUM_WORK_LIMIT"); //$NON-NLS-1$
            var allocation=new OptimizationBudget(remaining,budget.maximumStates(),budget.maximumCandidates(),budget.timeoutMillis());
            var checked=new FiniteIterationVerifier().verify(reduction.source(),reduction.emitted(),reduction.certificate(),allocation,bounded);
            used+=checkedWork(checked,reduction.checkedCost(),remaining);
        }
        return new FiniteIterationVerifier.Verification(true,proof.reason(),used,totalCost());
    }
    private static long checkedWork(FiniteIterationVerifier.Verification proof,FiniteIterationOptimizer.Cost expected,long permitted) {
        if(!proof.verified() || !expected.equals(proof.cost()) || proof.work()<0 || proof.work()>permitted)
            throw new IllegalArgumentException("FINITE_SUM_PROOF_OR_COST_DIFFERS"); //$NON-NLS-1$
        return proof.work();
    }
    void requireCost(CostAssessment assessment,MathCleanUpOptions options) {
        verify(options.optimizationBudget(Math.min(options.workBudget(),50000)),()->Thread.currentThread().isInterrupted());
        if(!cost(totalCost(),options.goal()).equals(assessment) || !assessment.estimatedRuntimeImprovement())
            throw new IllegalArgumentException("FINITE_SUM_REVIEW_COST_DIFFERS"); //$NON-NLS-1$
    }
    FiniteIterationOptimizer.Cost totalCost() {
        var before=checkedCost.originalOperations();long after=checkedCost.resultOperations();
        for(var reduction:additional) {before=before.add(reduction.checkedCost().originalOperations());after=Math.addExact(after,reduction.checkedCost().resultOperations());}
        return new FiniteIterationOptimizer.Cost(before,after);
    }
    static CostAssessment cost(FiniteIterationOptimizer.Cost checked,OptimizationGoal goal) {
        long before=checked.originalOperations().longValueExact(),after=checked.resultOperations();
        long weight=goal==OptimizationGoal.LOWER_ESTIMATED_RUNTIME?10:1;
        return new CostAssessment(Math.multiplyExact(before,weight),Math.multiplyExact(after,weight),0,0,
                new Cost(before,0,0,1,(int)Math.min(Integer.MAX_VALUE,before),0),
                new Cost(after,0,0,1,(int)Math.min(Integer.MAX_VALUE,after),0),after<before);
    }
    public String description() {
        var total=totalCost();
        return "Verified finite sum by telescoping; estimated arithmetic operations in the substituted recurrence "+total.originalOperations()+" -> "+total.resultOperations() //$NON-NLS-1$ //$NON-NLS-2$
                +"; Java counter termination and declaration bindings replayed separately. Estimate excludes loop control; not a timing measurement."; //$NON-NLS-1$
    }
}
