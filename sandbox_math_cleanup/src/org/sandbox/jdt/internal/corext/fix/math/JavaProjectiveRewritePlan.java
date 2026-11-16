/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;

import de.regelsuche.ast.Expr;
import de.regelsuche.math.algorithms.iteration.ProjectiveHandoff;
import de.regelsuche.sdk.optimization.OptimizationBudget;
import de.regelsuche.sdk.optimization.iteration.ProjectiveHandoffSdk;

/** Checks the exact algebra of a projected tuple and the Java bindings of its proposed handoff. */
public final class JavaProjectiveRewritePlan {
    private final JavaStructuralRewritePlan sources;
    private final ProjectiveHandoff.Verified handoff;

    private JavaProjectiveRewritePlan(JavaStructuralRewritePlan sources, ProjectiveHandoff.Verified handoff) {
        this.sources = sources;
        this.handoff = handoff;
    }

    /**
     * The source adapter binds its recovered affine expressions to the proposed carrier and supplies
     * the expected call changes. Implementations of consuming operations still need their domain contract.
     */
    public static JavaProjectiveRewritePlan verify(Map<String, String> original, Map<String, String> replacement,
            List<String> classpath, int targetJava, List<JavaStructuralRewritePlan.Invocation> calls,
            List<Expr> affine, ProjectiveHandoff.Candidate candidate, OptimizationBudget budget, IProgressMonitor monitor) {
        var expected = List.copyOf(calls);
        if (expected.stream().noneMatch(invocation -> invocation.before() != invocation.after()))
            throw new IllegalArgumentException("PROJECTIVE_SOURCE_TRANSITION_REQUIRED"); //$NON-NLS-1$
        var checked = ProjectiveHandoffSdk.verify(List.copyOf(affine), candidate, budget, () -> monitor != null && monitor.isCanceled());
        var handoff = checked.verified().orElseThrow(() -> new IllegalArgumentException("PROJECTIVE_HANDOFF_UNVERIFIED: " + checked.reason())); //$NON-NLS-1$
        return new JavaProjectiveRewritePlan(JavaStructuralRewritePlan.verify(original, replacement, classpath, targetJava,
            expected, monitor), handoff);
    }

    public JavaStructuralRewritePlan sources() { return sources; }
    public ProjectiveHandoff.Verified handoff() { return handoff; }
}
