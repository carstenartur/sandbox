/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.functional.core.operation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.sandbox.functional.core.model.FunctionalExpression;

/**
 * Represents a map operation in a stream pipeline.
 */
public final class MapOp implements Operation {
    /** Preserve reference/primitive stream transitions when a pipeline is re-rendered. */
    public enum Kind {
        MAP("map"), TO_INT("mapToInt"), TO_LONG("mapToLong"), TO_DOUBLE("mapToDouble"), TO_OBJECT("mapToObj");

        private final String method;
        Kind(String method) { this.method = method; }
        public String method() { return method; }
        public static Kind fromMethod(String method) {
            for (Kind kind : values()) {
                if (kind.method.equals(method)) return kind;
            }
            throw new IllegalArgumentException("Unsupported mapping operation: " + method);
        }
    }

    private final String expression;
    private final String targetType;
    private final String outputVariableName;
    private final boolean sideEffect;
    private final FunctionalExpression function;
    private final Kind kind;
    private final List<String> associatedComments;
    
    /**
     * Creates a MapOp with all parameters.
     * @param expression the mapping expression
     * @param targetType the target type (can be null)
     * @param outputVariableName the variable name for the output of this map (can be null)
     * @param sideEffect if true, this is a side-effect map: map(var -> { stmt; return var; })
     */
    public MapOp(String expression, String targetType, String outputVariableName, boolean sideEffect) {
        this(expression, targetType, outputVariableName, sideEffect, null);
    }

    public MapOp(String expression, String targetType, String outputVariableName, boolean sideEffect,
            FunctionalExpression function) {
        this(expression, targetType, outputVariableName, sideEffect, function, Kind.MAP);
    }

    public MapOp(String expression, String targetType, String outputVariableName, boolean sideEffect,
            FunctionalExpression function, Kind kind) {
        this.expression = Objects.requireNonNull(expression, "expression must not be null");
        this.targetType = targetType;
        this.outputVariableName = outputVariableName;
        this.sideEffect = sideEffect;
        if (function != null && !Objects.equals(targetType, function.outputType())) {
            throw new IllegalArgumentException("Map target type must match the functional result type");
        }
        this.function = function;
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        if (kind != Kind.MAP && (function == null || sideEffect)) {
            throw new IllegalArgumentException("A stream type transition requires a complete mapping function");
        }
        String primitive = switch (kind) {
            case TO_INT -> "int";
            case TO_LONG -> "long";
            case TO_DOUBLE -> "double";
            default -> null;
        };
        if (primitive != null && !primitive.equals(targetType)) {
            throw new IllegalArgumentException("Mapping result does not match " + kind.method());
        }
        this.associatedComments = new ArrayList<>();
    }
    
    /**
     * Creates a MapOp with expression, target type, and output variable name.
     * @param expression the mapping expression
     * @param targetType the target type (can be null)
     * @param outputVariableName the variable name for the output of this map (can be null)
     */
    public MapOp(String expression, String targetType, String outputVariableName) {
        this(expression, targetType, outputVariableName, false);
    }
    
    /**
     * Creates a MapOp with expression and target type.
     * @param expression the mapping expression
     * @param targetType the target type (can be null)
     */
    public MapOp(String expression, String targetType) {
        this(expression, targetType, null, false);
    }
    
    /**
     * Creates a MapOp with just an expression and no target type.
     * @param expression the mapping expression
     */
    public MapOp(String expression) { 
        this(expression, null, null, false); 
    }
    
    @Override
    public String expression() {
        return expression;
    }
    
    public String targetType() {
        return targetType;
    }

    public FunctionalExpression function() {
        return function;
    }
    
    /**
     * Returns the output variable name for this map operation.
     * When chaining maps, the next operation should use this as its lambda parameter.
     * @return the output variable name, or null if not specified
     */
    public String outputVariableName() {
        return outputVariableName;
    }
    
    /**
     * Returns whether this is a side-effect map.
     * Side-effect maps render as: {@code map(var -> { statements; return var; })}
     * @return true if this is a side-effect map
     */
    public boolean isSideEffect() {
        return sideEffect;
    }
    
    @Override
    public String operationType() { 
        return kind.method();
    }
    
    /**
     * Adds a comment associated with this operation.
     * @param comment the comment text
     */
    public void addComment(String comment) {
        if (comment != null && !comment.isBlank()) {
            this.associatedComments.add(comment);
        }
    }
    
    /**
     * Adds multiple comments associated with this operation.
     * @param comments the list of comment texts
     */
    public void addComments(List<String> comments) {
        if (comments != null) {
            comments.forEach(this::addComment);
        }
    }
    
    /**
     * Returns all comments associated with this operation.
     * @return unmodifiable view of the comments list
     */
    public List<String> getComments() {
        return List.copyOf(associatedComments);
    }
    
    /**
     * Checks if this operation has any associated comments.
     * @return true if there are comments, false otherwise
     */
    public boolean hasComments() {
        return !associatedComments.isEmpty();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MapOp)) return false;
        MapOp mapOp = (MapOp) o;
        return sideEffect == mapOp.sideEffect && kind == mapOp.kind &&
               expression.equals(mapOp.expression) && 
               Objects.equals(targetType, mapOp.targetType) &&
               Objects.equals(outputVariableName, mapOp.outputVariableName) &&
               Objects.equals(function, mapOp.function);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(expression, targetType, outputVariableName, sideEffect, function, kind);
    }
    
    @Override
    public String toString() {
        return "MapOp[expression=" + expression + 
               ", targetType=" + targetType + 
               ", kind=" + kind +
               ", function=" + function +
               ", comments=" + associatedComments.size() + "]";
    }
}
