/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.*;
import de.regelsuche.ast.*;
import de.regelsuche.sdk.optimization.*;
import de.regelsuche.sdk.optimization.OptimizationResult.CostAssessment;
import de.regelsuche.sdk.optimization.iteration.*;
import de.regelsuche.search.program.PreparedJointComputation.Cost;
import de.regelsuche.symbol.SymbolId;

/** Untrusted runtime polynomial/linear transport; source and emitted template require separate replay. */
public record RuntimeIterationEvidence(String sourceSha256,List<FiniteSumEvidence.Binding> bindings,
        List<PolynomialSumPlan> sums,LinearIterationOptimizer.Candidate linear,PolynomialSeries.NestedPlan nested,Partition partition) {
    /** Value-only source correspondence: no AST identity can qualify a partition. */
    public record Partition(int period,int residue,boolean inclusive,boolean firstOnLow,int splitOffset,int splitLength,List<SymbolId> starts) {
        public Partition {
            starts=List.copyOf(starts);
            if(period<0 || period>32 || residue<0 || (period>0 && residue>=period)
                    || starts.size()!=(period==0?2:period) || new HashSet<>(starts).size()!=starts.size()
                    || (period==0?(splitOffset<0 || splitLength<1):(splitOffset!=-1 || splitLength!=0)))throw invalid();
        }
    }
    public RuntimeIterationEvidence(String sourceSha256,List<FiniteSumEvidence.Binding> bindings,List<PolynomialSumPlan> sums,
            LinearIterationOptimizer.Candidate linear,PolynomialSeries.NestedPlan nested) {
        this(sourceSha256,bindings,sums,linear,nested,null);
    }
    /** Conservative crossover policy, not timings: BigInteger allocations and matrix products have explicit cost. */
    public static final int MAX_EMITTED_CHARACTERS=60000, MAX_PARAMETER_BITS=1024, MAX_RESULT_BITS=65536, MAX_NAME_ATTEMPTS=16;
    public RuntimeIterationEvidence {
        Objects.requireNonNull(sourceSha256);bindings=List.copyOf(bindings);sums=List.copyOf(sums);
        if(sums.size()>(partition==null?8:32) || (sums.isEmpty()?0:1)+(linear==null?0:1)+(nested==null?0:1)!=1
                || partition!=null && (sums.size()!=partition.starts().size() || linear!=null || nested!=null)) throw new IllegalArgumentException("RUNTIME_PROOF_SHAPE");
    }
    public long verify(OptimizationBudget budget,CancellationToken token) {
        long used=0;long started=System.nanoTime();
        long timeout=budget.timeoutMillis()>Long.MAX_VALUE/1000000?Long.MAX_VALUE:budget.timeoutMillis()*1000000;
        CancellationToken bounded=()->token.isCancelled() || System.nanoTime()-started>=timeout;
        for(var plan:sums) {
            var proof=new PolynomialSumVerifier().verify(plan.source(),plan,remaining(budget,used),bounded);
            used=Math.addExact(used,proof.work());if(!proof.verified()) throw invalid();
        }
        if(linear!=null) {var proof=new LinearIterationVerifier().verify(linear.source(),linear,remaining(budget,used),bounded);used+=proof.work();if(!proof.verified()) throw invalid();}
        if(nested!=null) {var proof=nested.verify(remaining(budget,used),bounded);used+=proof.work();if(!proof.verified()) throw invalid();}
        return used;
    }
    private static OptimizationBudget remaining(OptimizationBudget budget,long used) {
        if(used>=budget.maximumWork()) throw invalid();
        return new OptimizationBudget(budget.maximumWork()-used,budget.maximumStates(),budget.maximumCandidates(),budget.timeoutMillis());
    }
    public long minimumIterations() {
        long count=4096;
        while(count*sourceOperations()<=candidateOperations(count) && count<(1L<<40)) count*=2;
        return count;
    }
    private long sourceOperations() {
        if(partition!=null)return 3+sums.stream().mapToLong(p->operations(p.source().term())+1).max().orElse(1);
        if(linear!=null) return Math.max(1,linear.source().updates().stream().mapToLong(u->operations(u.expression())).sum());
        if(nested!=null) return Math.max(1,operations(nested.source().inner().term())+1);
        return Math.max(1,sums.stream().mapToLong(p->operations(p.source().term())+1).sum());
    }
    private long candidateOperations(long count) {
        long scalar=32; // advisory allocation/arithmetic weight, explicitly not a timing measurement
        if(linear!=null) {
            long d=linear.transition().matrix().size(),bits=64-Long.numberOfLeadingZeros(count);
            long setup=linear.transition().matrix().stream().flatMap(List::stream).mapToLong(RuntimeIterationEvidence::operations).sum();
            return scalar*(64+setup+2*bits*(d*d*d+d*d));
        }
        List<PolynomialSumPlan> plans=nested==null?sums:List.of(nested.outer());
        return scalar*(64+(partition==null?0:12L*plans.size())+plans.stream().mapToLong(p->operations(p.initial())+p.coefficients().stream().mapToLong(c->operations(c)+5).sum()).sum());
    }
    private static long operations(Expr expression) {
        if(expression instanceof BinaryExpr binary)return 1+operations(binary.left())+operations(binary.right());
        return 1; // conversion/read/allocation is included in the advisory score
    }
    CostAssessment cost(OptimizationGoal goal) {
        if(goal!=OptimizationGoal.LOWER_ESTIMATED_RUNTIME)throw new IllegalArgumentException("RUNTIME_TEMPLATE_REQUIRES_RUNTIME_GOAL");
        long count=minimumIterations(),before=count*sourceOperations(),after=candidateOperations(count);
        long weight=goal==OptimizationGoal.LOWER_ESTIMATED_RUNTIME?10:1;
        return new CostAssessment(before*weight,after*weight,0,0,
                new Cost(before,0,0,1,(int)Math.min(Integer.MAX_VALUE,before),0),new Cost(after,0,0,1,(int)Math.min(Integer.MAX_VALUE,after),0),after<before);
    }
    void requireCost(CostAssessment cost,MathCleanUpOptions options) {
        verify(options.optimizationBudget(Math.min(options.workBudget(),verificationLimit())),()->Thread.currentThread().isInterrupted());
        if(!cost(options.goal()).equals(cost)) throw invalid();
    }
    long verificationLimit() {return 100000L*(partition==null?1:sums.size());}
    public String description() {
        return "Verified runtime "+(partition!=null?(partition.period()>0?"periodic polynomial sum (nonnegative indices)":"piecewise polynomial sum"):linear!=null?"linear transition by binary matrix powering":nested!=null?"nested polynomial sum":"polynomial sum in the binomial basis")
                +"; guarded crossover at "+minimumIterations()+" iterations; original execution below threshold or on failed termination/type/growth guards. "
                +"Scores use source expression operation counts and emitted degree/dimension/count-bit work with a documented 32-unit BigInteger allocation/arithmetic weight; this crossover heuristic is not a measured speedup; source bindings and the complete emitted template are replayed.";
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("RUNTIME_ITERATION_PROOF_DIFFERS");}
}
