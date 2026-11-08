/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.regelsuche.ast.iteration.FieldInversionBatch;
import de.regelsuche.math.algorithms.iteration.FieldBatchInversionPlan;
import de.regelsuche.math.algorithms.iteration.FieldInversionSearch.CheckedMove;

/** Checked value schedule and binding replay for an explicit mathematical batch. */
public record FieldBatchInversionEvidence(FieldInversionBatch source, FieldBatchInversionPlan plan,
        List<CheckedMove> witness, Map<String, String> inputs, List<String> bindings) {
    public FieldBatchInversionEvidence {
        witness = List.copyOf(witness);
        inputs = Map.copyOf(inputs);
        bindings = List.copyOf(bindings);
    }

    @JsonProperty(value = "originalInversions", access = JsonProperty.Access.READ_ONLY) //$NON-NLS-1$
    public int originalInversions() { return source.operands(); }

    @JsonProperty(value = "plannedInversions", access = JsonProperty.Access.READ_ONLY) //$NON-NLS-1$
    public int plannedInversions() {
        return (int) plan.steps().stream().filter(FieldBatchInversionPlan.Invert.class::isInstance).count();
    }

    @JsonProperty(value = "plannedMultiplications", access = JsonProperty.Access.READ_ONLY) //$NON-NLS-1$
    public int plannedMultiplications() { return plan.steps().size() - plannedInversions(); }

    public String description() {
        return "Guarded BigInteger batch reduces modular inversions from " + originalInversions() //$NON-NLS-1$
                + " to " + plannedInversions() + " with " + plannedMultiplications() //$NON-NLS-1$ //$NON-NLS-2$
                + " modular multiplications. The original calls run for other inputs or if product inversion fails. " //$NON-NLS-1$
                + "Mathematical mode permits changes to BigInteger reference identity, including at downstream calls."; //$NON-NLS-1$
    }
}
