/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.Comparator;
import java.util.List;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Advisory ordering of existing verified edits. Does not filter, reverify or apply them. */
public final class MathReviewReport {
    private MathReviewReport() { }
    public record Entry(int offset, int length, MathCandidateReview.Assessment assessment) { }

    public static List<Entry> ranked(List<MathematicalAnalysis.VerifiedRegion> evidence, MathCleanUpOptions options) {
        return evidence.stream().map(region -> new Entry(region.offset(), region.length(), assess(region, options)))
                .sorted(Comparator.comparingInt((Entry entry) -> priority(entry.assessment().disposition()))
                        .thenComparingInt(entry -> entry.assessment().addedBranches())
                        .thenComparingInt(entry -> entry.assessment().addedLocals())
                        .thenComparingInt(entry -> entry.assessment().changedLines() < 0 ? Integer.MAX_VALUE : entry.assessment().changedLines())
                        .thenComparing(Comparator.comparingLong((Entry entry) -> entry.assessment().estimatedScoreSaving()).reversed())
                        .thenComparingInt(Entry::offset)).toList();
    }

    private static MathCandidateReview.Assessment assess(MathematicalAnalysis.VerifiedRegion region, MathCleanUpOptions options) {
        if (region.cost() == null || region.proof() == null || region.explanation() == null && region.scheduling() == null)
            return unassessed("INSUFFICIENT_REVIEW_METADATA"); //$NON-NLS-1$
        boolean contractReview = region.proof().safetyProfile() != SafetyProfile.PRESERVE_JAVA
                || (options.mathematicalOptIn() && region.explanation() != null && region.explanation().verified().original().steps().stream()
                        .anyMatch(step -> step.kind() == NumericKind.BIG_INTEGER));
        try {
            if (region.scheduling() != null) region.scheduling().requireCost(region.proof(), region.cost(), options.goal());
            var assessment = MathCandidateReview.assess(region.original(), region.replacement(), region.cost().sourceScore(),
                    region.cost().candidateScore(), region.cost().estimatedRuntimeImprovement(), contractReview,
                    region.explanation() == null ? 0 : region.explanation().guardedReceivers().size(), options.targetJava());
            if (region.scheduling() == null) return assessment;
            var reasons = new java.util.ArrayList<>(assessment.cautions());
            reasons.add(region.scheduling().description());
            return new MathCandidateReview.Assessment(assessment.disposition(), assessment.estimatedScoreSaving(),
                    assessment.changedLines(), assessment.addedBranches(), assessment.addedLocals(), reasons);
        } catch (IllegalArgumentException unavailable) {
            // Review parsing/bounds are not permission to discard the independently verified edit.
            return unassessed(unavailable.getMessage());
        }
    }

    private static MathCandidateReview.Assessment unassessed(String reason) {
        return new MathCandidateReview.Assessment("UNASSESSED", 0, -1, -1, -1, //$NON-NLS-1$
                List.of(reason, "ESTIMATE_NOT_BENCHMARK", "TIMING_BEHAVIOR_NOT_VERIFIED")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static int priority(String disposition) {
        return switch (disposition) {
            case "COMPACT_REVIEW" -> 0; //$NON-NLS-1$
            case "GUARDED_REVIEW" -> 1; //$NON-NLS-1$
            case "LARGE_REWRITE_REVIEW" -> 2; //$NON-NLS-1$
            case "CONTRACT_REVIEW" -> 3; //$NON-NLS-1$
            case "NO_ESTIMATED_GAIN" -> 4; //$NON-NLS-1$
            default -> 5;
        };
    }
}
