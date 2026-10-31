/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.functional.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.sandbox.functional.core.operation.FilterOp;
import org.sandbox.functional.core.operation.MapOp;
import org.sandbox.functional.core.operation.Operation;
import org.sandbox.functional.core.terminal.ForEachTerminal;

/**
 * Numeric iteration inspection, independent of converter rendering admission.
 * Intrinsic sum (especially floating point sum) is not an identity left fold.
 * This descriptor deliberately does not implement the converter's TerminalOperation.
 * The pipeline is captured by value; later edits to the input or an accessor result
 * cannot change this inspection, including operation comments and terminal bodies.
 */
public record NumericIteration(LoopModel pipeline, String elementType, Execution execution,
        EncounterOrder encounterOrder, Terminal terminal) {
    public enum Execution { SEQUENTIAL, PARALLEL, UNKNOWN }
    public enum EncounterOrder { ORDERED, UNORDERED, UNKNOWN }
    public NumericIteration {
        pipeline = copyPipeline(Objects.requireNonNull(pipeline));
        Objects.requireNonNull(elementType);
        Objects.requireNonNull(execution);
        Objects.requireNonNull(encounterOrder);
        Objects.requireNonNull(terminal);
    }

    /** Returns an independent editable copy of the inspected pipeline. */
    @Override
    public LoopModel pipeline() {
        return copyPipeline(pipeline);
    }

    private static LoopModel copyPipeline(LoopModel original) {
        var copy = new LoopModel(original.getSource(), original.getElement(), original.getMetadata());
        for (Operation operation : original.getOperations()) {
            // Map and filter comments are mutable; the other sealed operation types are values.
            if (operation instanceof MapOp map) {
                var copiedMap = new MapOp(map.expression(), map.targetType(), map.outputVariableName(),
                        map.isSideEffect(), map.function(), MapOp.Kind.fromMethod(map.operationType()));
                copiedMap.addComments(map.getComments());
                copy.addOperation(copiedMap);
            } else if (operation instanceof FilterOp filter) {
                var copiedFilter = new FilterOp(filter.expression(), filter.function());
                copiedFilter.addComments(filter.getComments());
                copy.addOperation(copiedFilter);
            } else {
                copy.addOperation(operation);
            }
        }
        if (original.getTerminal() instanceof ForEachTerminal forEach) {
            var statements = forEach.bodyStatements();
            copy.setTerminal(new ForEachTerminal(statements == null ? null : new ArrayList<>(statements),
                    forEach.ordered(), forEach.function()));
        } else {
            // All other sealed terminal types, source, element and metadata are immutable values.
            copy.setTerminal(original.getTerminal());
        }
        return copy;
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
