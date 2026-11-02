/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.OptimizationResult.CostAssessment;
import de.regelsuche.sdk.optimization.power.FixedPowerPlan;
import de.regelsuche.search.program.PreparedJointComputation.Cost;

/** Compact source scheduling and law receipts; full scalar expression graphs are request-local only. */
public record FixedPowerEvidence(String ownerBinding,String replacementHash,List<Chain> chains,List<JavaSourceContext.Receipt> dependencies,
        long sourceWork,long scheduledWork,Map<String,String> localTypeHashes) {
    public FixedPowerEvidence {
        chains=List.copyOf(chains);dependencies=List.copyOf(dependencies);localTypeHashes=Map.copyOf(localTypeHashes);
        if(chains.isEmpty() || chains.size()>8 || sourceWork<=scheduledWork || scheduledWork<1)throw new IllegalArgumentException("VALUE_POWER_COST"); //$NON-NLS-1$
    }
    public record Chain(String helper,BigInteger exponent,FixedPowerPlan plan,String multiplicationBinding,String squareBinding,
            String algebraFingerprint,String guard,String fallback,List<Law> laws,
            String receiverBinding,String powerBinding,String exponentBinding,List<SiteStep> sourcePath,List<String> guardBindings) {
        public Chain {
            laws=List.copyOf(laws);sourcePath=List.copyOf(sourcePath);guardBindings=List.copyOf(guardBindings);
            if(laws.size()!=4 || sourcePath.isEmpty() || sourcePath.size()>64)throw new IllegalArgumentException("VALUE_POWER_SOURCE_RECEIPT"); //$NON-NLS-1$
        }
    }
    /** Original caller AST location, independent of generated text offsets and documentation. */
    public record SiteStep(String property,int index) { }
    /** Compact bindings to completed SDK law checks; source dependencies still require fresh validation. */
    public record Law(String name,String checkerRevision,String sourceHash,String candidateHash,String traceHash,String assumptionsHash,long work) {
        static Law of(JavaValueAlgebraProof.Law law) {var proof=law.proof();return new Law(law.name(),proof.checkerRevision(),proof.sourceHash(),proof.candidateHash(),proof.traceHash(),proof.assumptionsHash(),proof.verificationWork());}
    }
    CostAssessment cost(OptimizationGoal goal) {
        long peak=chains.stream().mapToLong(chain->chain.plan().multiplications().size()+1L).max().orElseThrow();
        var original=new Cost(sourceWork,3,1,1,(int)Math.min(Integer.MAX_VALUE,sourceWork),0);
        var scheduled=new Cost(scheduledWork,peak,1,1,(int)Math.min(Integer.MAX_VALUE,scheduledWork),0);
        long workWeight=goal==OptimizationGoal.LOWER_ESTIMATED_RUNTIME?10:1,storageWeight=goal==OptimizationGoal.READABILITY?0:goal==OptimizationGoal.LOWER_ALLOCATION?10:1;
        return new CostAssessment(original.weighted(workWeight,storageWeight,storageWeight,0),scheduled.weighted(workWeight,storageWeight,storageWeight,0),0,0,original,scheduled,scheduledWork<sourceWork);
    }
    void requireCost(CostAssessment assessment,OptimizationGoal goal) {
        if(!cost(goal).equals(assessment))throw new IllegalArgumentException("VALUE_POWER_COST_DIFFERS"); //$NON-NLS-1$
    }
    public String description(){return "Source-proved fixed-power schedule: "+chains.size()+" guarded call(s), modeled scalar operations, guard expressions and local references/stores "+sourceWork+" -> "+scheduledWork+". Compact scalar receipts bind the proved value-algebra laws; independent power-plan replay and emitted-source binding prove scheduling. Storage conservatively counts all emitted locals. Estimates are not timings.";} //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
}
