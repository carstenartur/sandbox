/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.sdk.optimization.*;
import de.regelsuche.search.program.JointComputationPlan;
import de.regelsuche.search.program.PreparedJointComputation;
import java.util.List;
import de.regelsuche.ast.Expr;

/** Source-adapter ordering only. All selected values still require independent SDK proof. */
public final class JavaCandidateRanking implements CandidateRanking {
    private final OptimizationRequest request;
    private final JavaComputationRegion region;
    private final MathCleanUpOptions options;
    private final String original;
    private final boolean magnitudeModel;
    private final int originalLocals;
    private final java.util.Map<List<Expr>, Assessment> cached = new java.util.HashMap<>();
    public JavaCandidateRanking(OptimizationRequest request, JavaComputationRegion region, MathCleanUpOptions options, String source) {
        this.request = request; this.region = region; this.options = options;
        original = source.substring(region.start(), region.start() + region.length());
        originalLocals = options.goal() == OptimizationGoal.READABILITY ? locals(original) : 0;
        // The generic <4096 receiver safety ceiling is not an operand-size prediction.
        // Use source-specific tighter bounds; otherwise retain the established structural model.
        magnitudeModel = request.plan().inputs().entrySet().stream().filter(e -> e.getValue().equals(NumericKind.BIG_INTEGER.type()))
                .allMatch(e -> request.assumptions().stream().anyMatch(a -> a.kind() == SemanticAssumption.Kind.BIG_INTEGER_BIT_LENGTH_BOUND
                        && a.subject().equals(e.getKey()) && Integer.parseInt(a.parameter()) < 4096));
    }
    @Override public Assessment assess(JointComputationPlan plan, PreparedJointComputation prepared) {
        return assess(plan, prepared, false);
    }
    @Override public Assessment assessSource(JointComputationPlan plan, PreparedJointComputation prepared) {
        return assess(plan, prepared, true);
    }
    private Assessment assess(JointComputationPlan plan, PreparedJointComputation prepared, boolean source) {
        var resolved = plan.resolveOutputs();
        var prior = source ? null : cached.get(resolved.expressions());
        if (prior != null) return new Assessment(prior.score(), resolved.work() + resolved.expandedNodes(), prior.runtimeWork());
        var cost = prepared.cost();
        boolean bigInteger = prepared.nodes().stream().anyMatch(n -> n.type().equals(NumericKind.BIG_INTEGER.type()));
        var magnitude = !bigInteger || !magnitudeModel || request.goal() == OptimizationGoal.READABILITY ? java.util.Optional.<NumericExecutionCost.Estimate>empty()
                : source ? NumericExecutionCost.estimateSource(request, prepared) : NumericExecutionCost.estimate(request, prepared);
        long operations = magnitude.map(NumericExecutionCost.Estimate::operationWork).orElse(cost.operationWork());
        long storage = magnitude.map(NumericExecutionCost.Estimate::peakWords).orElse(cost.peakLiveStorage());
        if (source && magnitude.isEmpty()) {
            var nodeWork = new java.util.HashMap<Expr, Long>();
            prepared.nodes().forEach(node -> nodeWork.put(node.expression(), node.operation() == null ? 0 : node.operation().work()));
            long traced = request.sourceTrace().occurrences().stream().mapToLong(o -> nodeWork.getOrDefault(o.expression(), 1L)).sum();
            operations = Math.max(operations, traced);
        }
        long work = resolved.work() + resolved.expandedNodes() + 1L + prepared.nodes().size() * 12L
                + request.sourceTrace().occurrences().size() + request.assumptions().size() * (long)prepared.nodes().size();
        String code = original;
        long captures = 0, captureWork = 0, addedLocals = 0;
        if (!source) {
            var emission = new JavaComputationEmitter().emit(prepared, region.inputNames(), region.reservedNames(), options.targetJava());
            code = emission.statements() + String.join(";\n", emission.outputValues().values()); //$NON-NLS-1$
            // Source emission can duplicate an inlined operation. The scalar DAG alone misses that work.
            operations = Math.max(operations, RuntimeCalculationPolicy.arithmeticOperations(
                    emission.statements() + emission.outputValues().values().stream().map(v -> "return " + v + ";").collect(java.util.stream.Collectors.joining()))); //$NON-NLS-1$ //$NON-NLS-2$
            var plain = new OptimizationResult.CostAssessment(0, 0, 0, 0, cost, cost, false);
            var captured = JavaArrayCaptureCost.assess(region, emission, plain, request.goal());
            captures = request.goal() == OptimizationGoal.READABILITY ? 0 : captured.candidateScore();
            captureWork = captured.candidateCost().operationWork() - cost.operationWork();
            if (request.goal() == OptimizationGoal.READABILITY)
                addedLocals = Math.max(0, locals(emission.statements()) + region.arrayReads().size() - originalLocals);
        }
        long runtimeWeight = request.goal() == OptimizationGoal.LOWER_ESTIMATED_RUNTIME ? 10 : 1;
        long storageWeight = request.goal() == OptimizationGoal.READABILITY ? 0 : request.goal() == OptimizationGoal.LOWER_ALLOCATION ? 10 : 1;
        long score = operations * runtimeWeight + storage * storageWeight
                + cost.retainedOutputStorage() * storageWeight + cost.outputBindings() + captures;
        if (!source) score += 7L * region.receiverGuards().size() * runtimeWeight;
        // Readability sees the actual emission before selecting an incumbent, including guard branches.
        if (request.goal() == OptimizationGoal.READABILITY)
            score = score * 8 + addedLocals + (!source && !region.receiverGuards().isEmpty() ? 10_000 : 0);
        var result = new Assessment(Math.min(Long.MAX_VALUE / 16, score), work + (source ? 0 : code.length() * 2L),
                operations + captureWork + (source ? 0 : 7L * region.receiverGuards().size()));
        if (!source && cached.size() < 1024) cached.put(resolved.expressions(), result);
        return result;
    }
    private static int locals(String statements) {
        var parser = org.eclipse.jdt.core.dom.ASTParser.newParser(org.eclipse.jdt.core.dom.AST.getJLSLatest());
        parser.setKind(org.eclipse.jdt.core.dom.ASTParser.K_STATEMENTS); parser.setSource(statements.toCharArray());
        int[] count = {0};
        parser.createAST(null).accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
            @Override public boolean visit(org.eclipse.jdt.core.dom.VariableDeclarationFragment declaration) { count[0]++; return true; }
        });
        return count[0];
    }
}
