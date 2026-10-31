/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.functional.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Numeric iteration inspection, independent of converter rendering admission.
 * Intrinsic sum (especially floating point sum) is not an identity left fold.
 * This descriptor deliberately does not implement the converter's TerminalOperation.
 */
public record NumericIteration(LoopModel pipeline, String elementType, Execution execution,
        EncounterOrder encounterOrder, Terminal terminal) {
    public enum Execution { SEQUENTIAL, PARALLEL, UNKNOWN }
    public enum EncounterOrder { ORDERED, UNORDERED, UNKNOWN }
    public NumericIteration {
        Objects.requireNonNull(pipeline);
        Objects.requireNonNull(elementType);
        Objects.requireNonNull(execution);
        Objects.requireNonNull(encounterOrder);
        Objects.requireNonNull(terminal);
    }

    /** Original terminal/result shape, including optional versus identity reduction. */
    public record Terminal(String operation, String resultType, boolean optional,
            String identity, Accumulator accumulator, String originalSyntax) {
        public Terminal {
            Objects.requireNonNull(operation);
            Objects.requireNonNull(resultType);
            Objects.requireNonNull(originalSyntax);
        }
    }

    /** A binary accumulator is not a unary FunctionalExpression. */
    public record Accumulator(String expression, List<String> inputTypes,
            String outputType, String functionType) {
        public Accumulator {
            Objects.requireNonNull(expression);
            inputTypes = List.copyOf(inputTypes);
            if (inputTypes.size() != 2) throw new IllegalArgumentException("A reduction accumulator is binary");
            Objects.requireNonNull(outputType);
            Objects.requireNonNull(functionType);
        }
    }
}
