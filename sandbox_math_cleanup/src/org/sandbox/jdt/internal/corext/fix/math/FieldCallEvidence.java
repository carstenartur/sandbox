/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.List;

/** Source facts that must be re-established at the specific invocation before the field proof applies. */
public record FieldCallEvidence(String callerKey, String callerHash, String helperName, String temporaryName,
        List<String> arguments, List<Input> inputs, String normWitnessKey) {
    public record Input(boolean exactClass, boolean nonnegative, boolean belowModulus) {}
    public FieldCallEvidence {
        arguments = List.copyOf(arguments);
        inputs = List.copyOf(inputs);
        if (arguments.size() != 3 || inputs.size() != 3) throw new IllegalArgumentException("Expected three field roles");
    }
}
