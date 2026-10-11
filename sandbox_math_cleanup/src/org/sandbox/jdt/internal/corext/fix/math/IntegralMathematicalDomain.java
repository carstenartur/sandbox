/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.sdk.optimization.IntegralDomainAnalysis;
import de.regelsuche.sdk.optimization.CancellationToken;
import de.regelsuche.search.program.JointComputationPlan;

/** Java presentation and opt-in policy; affine domain mathematics belongs to the SDK. */
final class IntegralMathematicalDomain {
    private IntegralMathematicalDomain() { }

    static boolean supports(JavaComputationRegion region, CancellationToken cancellation) {
        return IntegralDomainAnalysis.analyze(region.plan(), region.trace(), cancellation) instanceof IntegralDomainAnalysis.Domain;
    }

    static String verify(JavaComputationRegion original, JointComputationPlan replacement, CancellationToken cancellation) {
        var result = IntegralDomainAnalysis.compare(original.plan(), original.trace(), replacement, cancellation);
        if (!(result instanceof IntegralDomainAnalysis.Verified verified))
            throw new IllegalArgumentException("MATHEMATICAL_DOMAIN_NOT_PROVED");
        return "Local calculation input {@code " + original.inputNames().get(verified.source().input())
                + "}: exact no-overflow range before " + verified.source().inputRange()
                + "; after " + verified.candidate().inputRange()
                + ". Mathematical mode intentionally permits different Java overflow behavior. "
                + "These are local calculation ranges, not a proof of the whole method's preconditions.";
    }
}
