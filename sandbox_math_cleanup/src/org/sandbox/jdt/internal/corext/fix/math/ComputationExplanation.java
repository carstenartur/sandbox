/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.sdk.optimization.ComputationExplanations;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Source-name mapping for the SDK's independently verified computation views.
 * These are original/replacement plans, not an invented rewrite derivation.
 * Adapter receiver checks remain distinct from the SDK's numerical obligations.
 */
public record ComputationExplanation(Map<String, String> inputNames,
        Map<String, String> outputNames, Set<String> guardedReceivers,
        ComputationExplanations.Explanation verified) {
    public ComputationExplanation {
        inputNames = Collections.unmodifiableMap(new TreeMap<>(Map.copyOf(inputNames)));
        outputNames = Collections.unmodifiableMap(new TreeMap<>(Map.copyOf(outputNames)));
        guardedReceivers = Collections.unmodifiableSet(new TreeSet<>(Set.copyOf(guardedReceivers)));
        Objects.requireNonNull(verified, "verified");
        if (!outputNames.keySet().equals(verified.original().outputs().keySet())
                || !outputNames.keySet().equals(verified.replacement().outputs().keySet())) {
            throw new IllegalArgumentException("EXPLANATION_OUTPUT_BINDINGS_DIFFER");
        }
    }

    static ComputationExplanation from(JavaComputationRegion region,
            ComputationExplanations.Explanation verified) {
        Map<String, String> outputs = new TreeMap<>();
        for (var output : region.outputs()) {
            if (outputs.put(output.id(), output.returnValue() ? "return value" : output.javaName()) != null) {
                throw new IllegalArgumentException("DUPLICATE_EXPLANATION_OUTPUT_BINDING");
            }
        }
        return new ComputationExplanation(region.inputNames(), outputs, region.receiverGuards(), verified);
    }
}
