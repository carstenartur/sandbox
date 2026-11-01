/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.Objects;

/** Source-control receipt, separate from the SDK proof that the two RHS values are equal. */
public record SourceNumericBranchEvidence(String sourceSha256, int offset, int length, String targetBinding,
        String thenExpression, String elseExpression, boolean conditionRetained, boolean elseValueSelected) {
    public SourceNumericBranchEvidence {
        Objects.requireNonNull(sourceSha256); Objects.requireNonNull(targetBinding);
        Objects.requireNonNull(thenExpression); Objects.requireNonNull(elseExpression);
        if (sourceSha256.length()!=64 || offset<0 || length<1 || targetBinding.isBlank())
            throw new IllegalArgumentException("INVALID_SOURCE_NUMERIC_BRANCH_RECEIPT"); //$NON-NLS-1$
    }
    public void requireSource(String source) {
        if (!sourceSha256.equals(MathematicalAnalysis.digest(source)))
            throw new IllegalArgumentException("SOURCE_NUMERIC_BRANCH_CHANGED"); //$NON-NLS-1$
    }
    /** One explicit source branch unit; it is separate from the numeric SDK operation estimate. */
    public long sourceControlWork() { return 1; }
    public long candidateControlWork() { return conditionRetained ? 1 : 0; }
}
