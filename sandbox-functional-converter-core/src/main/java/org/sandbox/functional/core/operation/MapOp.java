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

/**
 * Represents a map operation in a stream pipeline.
 */
public final class MapOp implements Operation {
    private final String expression;
    private final String targetType;
    private final String outputVariableName;
    private final boolean sideEffect;
    private final List<String> associatedComments;
    
    /**
     * Creates a MapOp with all parameters.
     * @param expression the mapping expression
     * @param targetType the target type (can be null)
     * @param outputVariableName the variable name for the output of this map (can be null)
     * @param sideEffect if true, this is a side-effect map: map(var -> { stmt; return var; })
     */
    public MapOp(String expression, String targetType, String outputVariableName, boolean sideEffect) {
        this.expression = Objects.requireNonNull(expression, "expression must not be null");
        this.targetType = targetType;
        this.outputVariableName = outputVariableName;
        this.sideEffect = sideEffect;
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
        return "map"; 
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
        return sideEffect == mapOp.sideEffect &&
               expression.equals(mapOp.expression) && 
               Objects.equals(targetType, mapOp.targetType) &&
               Objects.equals(outputVariableName, mapOp.outputVariableName);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(expression, targetType, outputVariableName, sideEffect);
    }
    
    @Override
    public String toString() {
        return "MapOp[expression=" + expression + 
               ", targetType=" + targetType + 
               ", comments=" + associatedComments.size() + "]";
    }
}
