/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.OptimizationResult.CostAssessment;
import de.regelsuche.search.program.PreparedJointComputation.Cost;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.SimpleName;

/** Conservative source-adapter costs, separate from the SDK's scalar value graph. */
final class JavaArrayCaptureCost {
    private JavaArrayCaptureCost() { }

    static CostAssessment assess(JavaComputationRegion region, JavaComputationEmitter.Emission emission,
            CostAssessment cost, OptimizationGoal goal) {
        if (region.arrayReads().isEmpty()) return cost;
        int captures = region.arrayReads().size();
        long captureWork = captures + references(region, emission);
        Cost arithmetic = cost.candidateCost();
        // Every capture store and emitted local reference costs one primitive work unit.
        // Keeping all N captures live adds N storage units on top of the scalar peak;
        // this deliberately overcounts any lifetime already covered by a graph input.
        Cost candidate = new Cost(arithmetic.operationWork() + captureWork,
                arithmetic.peakLiveStorage() + captures, arithmetic.retainedOutputStorage(), arithmetic.outputBindings(),
                Math.toIntExact(arithmetic.operationCount() + captureWork), arithmetic.inspectionWork());
        long workWeight = goal == OptimizationGoal.LOWER_ESTIMATED_RUNTIME ? 10 : 1;
        long storageWeight = goal == OptimizationGoal.READABILITY ? 0 : goal == OptimizationGoal.LOWER_ALLOCATION ? 10 : 1;
        long score = cost.candidateScore() + captureWork * workWeight + captures * storageWeight;
        // All admitted source operations are primitive, total, and one work unit each.
        long sourceWork = Math.max(region.trace().occurrences().size(), cost.sourceCost().operationWork());
        boolean runtime = cost.estimatedRuntimeImprovement()
                && candidate.operationWork() + cost.checkWork() + cost.fallbackOperations() < sourceWork;
        return new CostAssessment(cost.sourceScore(), score, cost.checkWork(), cost.fallbackOperations(),
                cost.sourceCost(), candidate, runtime);
    }

    private static long references(JavaComputationRegion region, JavaComputationEmitter.Emission emission) {
        Set<String> names = new HashSet<>();
        for (var read : region.arrayReads()) {
            if (!names.add(read.javaName()) || !read.javaName().equals(region.inputNames().get(read.inputId())))
                throw new IllegalArgumentException("ARRAY_CAPTURE_COST_UNQUALIFIED"); //$NON-NLS-1$
        }
        // Count the actual Java emission, including inlined leaves and every output use.
        // Scheduled-node counts alone can miss references duplicated by source emission.
        StringBuilder code = new StringBuilder(emission.statements());
        emission.outputValues().values().forEach(value -> code.append("return ").append(value).append(";\n")); //$NON-NLS-1$ //$NON-NLS-2$
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_STATEMENTS);
        parser.setSource(code.toString().toCharArray());
        long[] count = {0};
        parser.createAST(null).accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) {
                if ((node.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0)
                    throw new IllegalArgumentException("ARRAY_CAPTURE_COST_UNQUALIFIED"); //$NON-NLS-1$
            }
            @Override public boolean visit(SimpleName name) {
                if (names.contains(name.getIdentifier())) {
                    if (name.isDeclaration()) throw new IllegalArgumentException("ARRAY_CAPTURE_COST_UNQUALIFIED"); //$NON-NLS-1$
                    count[0]++;
                }
                return true;
            }
        });
        return count[0];
    }
}
