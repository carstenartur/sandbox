/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.List;
import java.util.Map;
import de.regelsuche.ast.iteration.FieldInversionReduction;
import de.regelsuche.math.algorithms.iteration.FieldInversionPlan;
import de.regelsuche.math.algorithms.iteration.FieldInversionSearch.CheckedMove;

/** Mathematical counts are for two selected rows on the exact-class fast path. */
public record FieldInversionEvidence(FieldInversionReduction source, FieldInversionPlan plan,
        FieldInversionPlan.Cost minimumCost, List<CheckedMove> witness, String receiverBinding,
        String fieldTypeBinding, String divisionBinding, String multiplicationBinding, String powerBinding,
        String originalStatementsHash, Map<String, String> localDefinitionHashes) {
    public FieldInversionEvidence {
        witness = List.copyOf(witness);
        localDefinitionHashes = Map.copyOf(localDefinitionHashes);
    }

    public String description() {
        return "For two selected rows the verified byte-field path reduces inversions from " //$NON-NLS-1$
                + minimumCost.originalInversions() + " to " + minimumCost.plannedInversions() //$NON-NLS-1$
                + ". Field multiplications outside inversion change from " + minimumCost.originalMultiplications() + " to " //$NON-NLS-1$ //$NON-NLS-2$
                + minimumCost.plannedMultiplications() + ". Other receiver classes and shorter inputs take the original path."; //$NON-NLS-1$
    }
}
