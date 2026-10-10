/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.sdk.optimization.CancellationToken;
import de.regelsuche.sdk.optimization.ComputationExplanations.Explanation;
import de.regelsuche.sdk.optimization.ComputationOptimizer;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationRequest;
import de.regelsuche.sdk.optimization.OptimizationResult.Candidate;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import org.eclipse.core.runtime.OperationCanceledException;

/** Presentation of independently checked value graphs, never a mathematical rule or proof. */
record MathExplanation(String detail, String sourceComment, boolean nontrivial) {
    private static final int MAXIMUM_TEXT_LENGTH = 16_384;

    static MathExplanation render(Explanation verified, OptimizationRequest request, Candidate candidate,
            JavaComputationRegion region, JavaComputationEmitter.Emission replacement,
            MathCleanUpOptions options, CancellationToken cancellation) {
        checkCancellation(cancellation);
        if (!verified.proof().equals(candidate.evidence())
                || !verified.assumptions().equals(request.assumptions())) {
            throw new IllegalArgumentException("EXPLANATION_BINDING_DIFFERS");
        }
        var original = new JavaComputationEmitter().emit(ComputationOptimizer.prepare(request.plan()),
                region.inputNames(), region.reservedNames(), options.targetJava());
        List<String> lines = new ArrayList<>();
        lines.add("Verified mathematics (" + options.safety() + ").");
        describeContract(lines, request, region);
        appendValues(lines, "Original values:", original, region);
        appendValues(lines, "Replacement values:", replacement, region);
        lines.add("Value graphs are not a step-by-step search proof; the original trace still governs exceptions and evaluation order.");
        lines.add("Independent numeric proof: " + String.join(", ", verified.proof().proofMethods()) + ".");
        lines.add("Estimated operation work: " + candidate.cost().sourceCost().operationWork() + " -> "
                + candidate.cost().candidateCost().operationWork() + "; not a timing measurement.");
        String detail = String.join("\n", lines);
        if (detail.length() > MAXIMUM_TEXT_LENGTH) {
            throw new IllegalArgumentException("EXPLANATION_TEXT_BOUND_EXCEEDED");
        }
        checkCancellation(cancellation);
        // Java processes Unicode escapes even inside comments. Break their spelling;
        // do not let a rendered operand or provenance string inject executable lines.
        String comments = detail.lines().map(line -> "// " + line.replace("\\u", "\\ u"))
                .collect(java.util.stream.Collectors.joining("\n", "", "\n"));
        boolean nontrivial = options.safety() != SafetyProfile.PRESERVE_JAVA
                || !verified.assumptions().isEmpty() || region.outputs().size() > 1
                || candidate.cost().sourceCost().operationWork() >= 8;
        return new MathExplanation(detail, comments, nontrivial);
    }

    private static void describeContract(List<String> lines, OptimizationRequest request, JavaComputationRegion region) {
        switch (request.safetyProfile()) {
            case CHECKED_THROW -> lines.add(MathCleanUpOptions.CHECKED_WARNING);
            case GUARDED_FALLBACK -> lines.add("The guarded replacement keeps the original calculation as fallback; retain all generated checks.");
            case PRESERVE_JAVA -> {
                var kinds = EnumSet.noneOf(NumericKind.class);
                request.sourceTrace().occurrences().forEach(o -> kinds.add(o.evaluatedKind()));
                request.plan().inputs().values().forEach(type -> kinds.add(NumericKind.fromType(type)));
                if (kinds.stream().anyMatch(kind -> kind != NumericKind.BIG_INTEGER && kind.integral()))
                    lines.add("Java integral wraparound and explicit conversions are preserved.");
                if (kinds.stream().anyMatch(NumericKind::floatingPoint))
                    lines.add("IEEE-754 evaluation is preserved under the recorded NaN-observation contract; no real-number reassociation is assumed.");
                if (kinds.contains(NumericKind.BIG_INTEGER))
                    lines.add("BigInteger value equivalence relies on the listed premises.");
            }
        }
        request.assumptions().stream().sorted(Comparator.comparing(Object::toString)).forEach(assumption -> {
            String subject = region.inputNames().getOrDefault(assumption.subject(), assumption.subject());
            lines.add("Premise: " + assumption.kind() + "(" + subject
                    + (assumption.parameter().isEmpty() ? "" : ", " + assumption.parameter()) + ").");
        });
        if (!region.receiverGuards().isEmpty()) {
            lines.add("Retain exact-receiver, non-null and magnitude guards for: "
                    + String.join(", ", region.receiverGuards().stream().sorted().toList()) + ".");
        }
    }

    private static void appendValues(List<String> lines, String heading,
            JavaComputationEmitter.Emission emission, JavaComputationRegion region) {
        lines.add(heading);
        emission.statements().lines().filter(line -> !line.isBlank()).forEach(lines::add);
        for (var output : region.outputs()) {
            String expression = emission.outputValues().get(output.id());
            if (expression == null) throw new IllegalArgumentException("EXPLANATION_OUTPUT_MISSING");
            lines.add((output.returnValue() ? "return value" : output.javaName()) + " = " + expression + ";");
        }
    }

    private static void checkCancellation(CancellationToken cancellation) {
        if (cancellation.isCancelled() || Thread.currentThread().isInterrupted())
            throw new OperationCanceledException("EXPLANATION_CANCELLED");
    }
}
