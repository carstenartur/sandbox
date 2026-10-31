/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.List;
import java.util.Objects;
import org.sandbox.functional.core.model.SourceDescriptor;

/** Adapter receipt for source scheduling, separate from the SDK's single-evaluation numerical proof. */
public record LoopSchedulingEvidence(Control control, EvaluationBasis evaluationBasis, List<String> invariantBindings,
        String temporaryName, String primitiveType, int loopOffset, int expressionOffset, int expressionLength,
        long scalarWork, long originalWork, long scheduledWork, int retainedPrimitiveValues) {
    public enum EvaluationBasis { TOTAL_BODY, NORMAL_LOOP_COMPLETION }
    /** SourceDescriptor is present only when it honestly describes the original iteration source. */
    public record Control(String kind, long initial, long bound, int step, long tripCount, SourceDescriptor source) {
        public Control {
            Objects.requireNonNull(kind);
            if(tripCount<2 || step!=1 && step!=-1) throw new IllegalArgumentException("INVALID_LOOP_CONTROL"); //$NON-NLS-1$
        }
    }
    public LoopSchedulingEvidence {
        Objects.requireNonNull(control); Objects.requireNonNull(evaluationBasis);
        Objects.requireNonNull(temporaryName); Objects.requireNonNull(primitiveType);
        invariantBindings=List.copyOf(invariantBindings);
        if(invariantBindings.isEmpty() || scalarWork<1 || loopOffset<0 || expressionOffset<loopOffset || expressionLength<1
                || originalWork!=Math.multiplyExact(control.tripCount(),scalarWork)
                || scheduledWork!=Math.addExact(scalarWork+1,control.tripCount()) || retainedPrimitiveValues!=1)
            throw new IllegalArgumentException("INVALID_LOOP_SCHEDULE"); //$NON-NLS-1$
    }
    public String description() {
        return "Adapter loop scheduling estimate: "+control.tripCount()+" evaluations, work "+originalWork+" -> "+scheduledWork //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                +", one retained primitive value; "+(evaluationBasis==EvaluationBasis.NORMAL_LOOP_COMPLETION //$NON-NLS-1$
                ?"conditional on normal loop completion (pinned array effects may throw)":"total straight-line body") //$NON-NLS-1$ //$NON-NLS-2$
                +". SDK proof covers one numerical evaluation, not loop scheduling; not a timing measurement."; //$NON-NLS-1$
    }
}
