/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MathCandidateReviewTest {
    @Test void compactVerifiedCandidateIsStillOnlyAReviewRecommendation() {
        var review = assess("return ((u & v) | ((~u) & w));", "return (w ^ (u & (v ^ w)));", false, 0);
        assertEquals("COMPACT_REVIEW", review.disposition());
        assertEquals(20, review.estimatedScoreSaving());
        assertEquals(2, review.changedLines());
        assertTrue(review.cautions().contains("ESTIMATE_NOT_BENCHMARK"));
    }
    @Test void introducedFallbackRequiresExplicitReview() {
        var review = assess("return x;", "if (x > 0) { return x; } else { return -x; }", false, 1);
        assertEquals("GUARDED_REVIEW", review.disposition()); assertEquals(1, review.addedBranches());
        assertTrue(review.cautions().contains("RECEIVER_GUARDS_RETAINED"));
    }
    @Test void contractChangesAreNotPresentedAsStrictRefactorings() {
        var review = assess("return 2 * x / 2;", "return x;", true, 0);
        assertEquals("CONTRACT_REVIEW", review.disposition());
        assertTrue(review.cautions().contains("CONTRACT_CHANGE_ALLOWED"));
    }
    @Test void addedLocalBindingsAreCountedStructurally() {
        var review = assess("return x + x;", "int t = x; int a = t; return a + a;", false, 0);
        assertEquals(2, review.addedLocals());
    }
    @Test void commentsDoNotInventBranchesOrLocals() {
        var review = assess("return x;", "// if (true) { int t = 1; }\nreturn x;", false, 0);
        assertEquals(0, review.addedBranches()); assertEquals(0, review.addedLocals());
        assertEquals(1, review.changedLines());
    }
    @Test void unchangedSurroundingLinesDoNotInflateTheDiff() {
        String prefix = "int a = 1;\n".repeat(60), suffix = "a++;\n".repeat(60);
        var review = assess(prefix + "int z = x + 0;\n" + suffix, prefix + "int z = x;\n" + suffix, false, 0);
        assertEquals(2, review.changedLines()); assertEquals("COMPACT_REVIEW", review.disposition());
    }
    @Test void generatedExplanationGrowthIsVisibleReviewCost() {
        var review = assess("return x;", "// explanation\n".repeat(50) + "return x;", false, 0);
        assertEquals("LARGE_REWRITE_REVIEW", review.disposition()); assertEquals(50, review.changedLines());
    }
    @Test void manyNewLocalsRequireReviewEvenOnOneLongLine() {
        var review = assess("return x;", "int a=1,b=2,c=3,d=4,e=5,f=6,g=7,h=8,i=9; return x;", false, 0);
        assertEquals(9, review.addedLocals()); assertEquals("LARGE_REWRITE_REVIEW", review.disposition());
    }
    @Test void missingEstimatedGainIsNotPromoted() {
        var review = MathCandidateReview.assess("return x;", "return x + 0;", 10, 10, false, false, 0);
        assertEquals("NO_ESTIMATED_GAIN", review.disposition());
    }
    @Test void positiveScoreDifferenceDoesNotOverrideAnAbsentRuntimeGain() {
        assertEquals("NO_ESTIMATED_GAIN", MathCandidateReview.assess("return x;", "return x+0;", 20, 10,
                false, false, 0).disposition());
    }
    @Test void invalidAccountingIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MathCandidateReview.assess("return x;", "return x;", -1, 0, true, false, 0));
        assertThrows(IllegalArgumentException.class, () -> MathCandidateReview.assess("return x;", "return x;", 10, 1, true, false, -1));
    }
    @Test void assessmentDoesNotClaimConstantTimeSafety() {
        assertTrue(assess("return x;", "return x + 0;", false, 0).cautions().contains("TIMING_BEHAVIOR_NOT_VERIFIED"));
    }
    @Test void malformedInputFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> assess("return x;", "return ( ;", false, 0));
    }
    private static MathCandidateReview.Assessment assess(String original, String replacement, boolean contract, int guards) {
        return MathCandidateReview.assess(original, replacement, 100, 80, true, contract, guards);
    }
}
