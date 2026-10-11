/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.sdk.optimization.CancellationToken;
import de.regelsuche.sdk.optimization.ComputationExplanations.Explanation;
import de.regelsuche.sdk.optimization.ComputationOptimizer;
import de.regelsuche.sdk.optimization.OptimizationRequest;
import java.util.List;
import org.eclipse.core.runtime.OperationCanceledException;

/** Java presentation of the retained SDK witness; no mathematical rewrite or invented steps. */
final class MathSearchDerivation {
    private MathSearchDerivation() { }

    static void append(List<String> lines, Explanation verified, OptimizationRequest request,
            JavaComputationRegion region, MathCleanUpOptions options, CancellationToken cancellation,
            int maximumTextLength) {
        checkCancellation(cancellation);
        if (verified.derivation().isEmpty()) {
            lines.add("No selected search path was recorded; value graphs are not a step-by-step derivation.");
            return;
        }
        var steps = verified.derivation().orElseThrow().steps();
        lines.add("Recorded search path:");
        if (steps.isEmpty()) {
            lines.add("No expression rewrite was selected; the value graph is unchanged and only its prepared computation may differ.");
        }
        var emitter = new JavaComputationEmitter();
        int index = 0;
        for (var step : steps) {
            checkCancellation(cancellation);
            lines.add("Search step " + (++index) + "/" + steps.size() + " [" + step.rule() + "]:");
            var plan = request.plan().withExpression(step.after());
            var emission = emitter.emit(ComputationOptimizer.prepare(plan), region.inputNames(),
                    region.reservedNames(), options.targetJava());
            emission.statements().lines().filter(line -> !line.isBlank()).forEach(lines::add);
            for (var output : region.outputs()) {
                String expression = emission.outputValues().get(output.id());
                if (expression == null) throw new IllegalArgumentException("DERIVATION_OUTPUT_MISSING");
                lines.add((output.returnValue() ? "return value" : output.javaName()) + " = " + expression + ";");
            }
            requireTextBound(lines, maximumTextLength);
        }
        lines.add("Each compound proposal is one recorded search step; no unrecorded primitive expansion is implied.");
        lines.add("Selected edges were regenerated and independently checked; the original trace still governs exceptions and evaluation order.");
        requireTextBound(lines, maximumTextLength);
        checkCancellation(cancellation);
    }

    private static void requireTextBound(List<String> lines, int maximum) {
        long length = 0;
        for (String line : lines) {
            length += (long) line.length() + 1;
            if (length > maximum) throw new IllegalArgumentException("EXPLANATION_TEXT_BOUND_EXCEEDED");
        }
    }

    private static void checkCancellation(CancellationToken cancellation) {
        if (cancellation.isCancelled() || Thread.currentThread().isInterrupted())
            throw new OperationCanceledException("DERIVATION_PRESENTATION_CANCELLED");
    }
}
