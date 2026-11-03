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

    boolean emitted(MathCleanUpOptions.ExplanationMode mode) {
        return mode == MathCleanUpOptions.ExplanationMode.ALL
                || mode == MathCleanUpOptions.ExplanationMode.NONTRIVIAL && nontrivial;
    }

    /** Called only after independent runtime proof/source checking; presentation grants no new premises. */
    static MathExplanation renderRuntime(JavaRuntimeIterationAnalysis.Context context, RuntimeIterationEvidence evidence,
            JavaLoopInvariantAnalysis.Work work) {
        work.tick();
        var lines = new ArrayList<String>();
        String family = evidence.partition() != null ? (evidence.partition().period() > 0 ? "periodic polynomial sum" : "piecewise polynomial sum")
                : evidence.linear() != null ? "linear transition by matrix powering" : evidence.nested() != null ? "nested polynomial sum" : "polynomial sum";
        lines.add("Verified runtime " + family + ".");
        int namesLength=0;
        for(var target:context.targets()) {
            work.charge(target.getName().length()+2L);namesLength+=target.getName().length()+2;
            if(namesLength>MAXIMUM_TEXT_LENGTH/2)throw new IllegalArgumentException("EXPLANATION_TEXT_BOUND_EXCEEDED");
        }
        String targets = context.stream() ? "stream sum" : String.join(", ", context.targets().stream().map(t -> t.getName()).toList());
        lines.add(targets + ": " + (context.width().equals("java.math.BigInteger")
                ? "exact BigInteger values; only value-observing uses are admitted."
                : "Java arithmetic modulo 2^" + (context.width().equals("int") ? 32 : 64) + "."));
        if (evidence.strategy() == RuntimeIterationEvidence.EmissionStrategy.PRIMITIVE_UNIT)
            lines.add("Primitive count and constant updates cover every count; exclusive unit-stride int headers cannot wrap.");
        else {
            lines.add("Estimated crossover: " + evidence.minimumIterations() + " iterations; original execution is the fallback below it or when a guard fails.");
            if (!context.stream()) lines.add("The final counter update must fit " + context.header().counter().getType().getName() + ".");
            if (context.partition() != null && context.partition().period() > 0) lines.add("Periodic fast path requires a nonnegative start.");
            if (context.nested() != null) lines.add("Inner counts must be nonnegative and inner counter endpoints representable.");
            if (context.bindings().stream().anyMatch(b -> b.primitiveType().equals("java.math.BigInteger")))
                lines.add("BigInteger inputs must be nonnull, exact-class instances with at most " + RuntimeIterationEvidence.MAX_PARAMETER_BITS + " bits.");
            if (context.width().equals("java.math.BigInteger") && evidence.linear() != null)
                lines.add("The matrix growth bound must fit " + RuntimeIterationEvidence.MAX_RESULT_BITS + " bits.");
        }
        lines.add("Independent numeric proof and Java source replay checked; estimated work is not a timing measurement.");
        String detail = String.join("\n", lines);
        work.charge(detail.length());
        if (detail.length() > MAXIMUM_TEXT_LENGTH) throw new IllegalArgumentException("EXPLANATION_TEXT_BOUND_EXCEEDED");
        String comments = detail.lines().map(line -> "// " + line.replace("\\u", "\\ u"))
                .collect(java.util.stream.Collectors.joining("\n", "", "\n"));
        work.charge(comments.length());
        return new MathExplanation(detail, comments, true);
    }

    static MathExplanation render(Explanation verified, OptimizationRequest request, Candidate candidate,
            JavaComputationRegion region, JavaComputationEmitter.Emission replacement,
            MathCleanUpOptions options, CancellationToken cancellation) {
        return render(verified, request, candidate, region, replacement, options, null, cancellation);
    }

    static MathExplanation render(Explanation verified, OptimizationRequest request, Candidate candidate,
            JavaComputationRegion region, JavaComputationEmitter.Emission replacement,
            MathCleanUpOptions options, String mathematicalContract, CancellationToken cancellation) {
        checkCancellation(cancellation);
        if (!verified.proof().equals(candidate.evidence())
                || !verified.assumptions().equals(request.assumptions())
                || !verified.derivation().equals(candidate.derivation())) {
            throw new IllegalArgumentException("EXPLANATION_BINDING_DIFFERS");
        }
        var original = new JavaComputationEmitter().emit(ComputationOptimizer.prepare(request.plan()),
                region.inputNames(), region.reservedNames(), options.targetJava());
        List<String> lines = new ArrayList<>();
        lines.add("Verified mathematics (" + (mathematicalContract == null ? options.safety() : "MATHEMATICAL") + ").");
        describeContract(lines, request, region, mathematicalContract);
        appendValues(lines, "Original values:", original, region);
        appendValues(lines, "Replacement values:", replacement, region);
        MathSearchDerivation.append(lines, verified, request, region, options, cancellation, MAXIMUM_TEXT_LENGTH);
        lines.add("Independent numeric proof: " + String.join(", ", verified.proof().proofMethods()) + ".");
        lines.add((region.arrayReads().isEmpty() ? "Estimated operation work: " : "Estimated scalar arithmetic work: ") //$NON-NLS-1$ //$NON-NLS-2$
                + candidate.cost().sourceCost().operationWork() + " -> "
                + candidate.cost().candidateCost().operationWork() + "; not a timing measurement.");
        if (!region.arrayReads().isEmpty()) lines.add("All " + region.arrayReads().size() //$NON-NLS-1$
                + " array reads remain in source order. Capture stores, references and held values are additional costs in the reported assessment."); //$NON-NLS-1$
        String detail = String.join("\n", lines);
        if (detail.length() > MAXIMUM_TEXT_LENGTH) {
            throw new IllegalArgumentException("EXPLANATION_TEXT_BOUND_EXCEEDED");
        }
        checkCancellation(cancellation);
        // Java processes Unicode escapes even inside comments. Break their spelling;
        // do not let a rendered operand or provenance string inject executable lines.
        String comments = detail.lines().map(line -> "// " + line.replace("\\u", "\\ u"))
                .collect(java.util.stream.Collectors.joining("\n", "", "\n"));
        boolean nontrivial = mathematicalContract != null || options.safety() != SafetyProfile.PRESERVE_JAVA
                || !verified.assumptions().isEmpty() || region.outputs().size() > 1
                || candidate.cost().sourceCost().operationWork() >= 8;
        return new MathExplanation(detail, comments, nontrivial);
    }

    private static void describeContract(List<String> lines, OptimizationRequest request, JavaComputationRegion region,
            String mathematicalContract) {
        if (mathematicalContract != null) lines.add(mathematicalContract);
        else switch (request.safetyProfile()) {
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
