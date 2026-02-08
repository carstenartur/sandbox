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
 * Represents a filter operation in a stream pipeline.
 */
public final class FilterOp implements Operation {
    private final String expression;
    private final List<String> associatedComments;
    
    public FilterOp(String expression) {
        this.expression = Objects.requireNonNull(expression, "expression must not be null");
        this.associatedComments = new ArrayList<>();
    }
    
    @Override
    public String expression() {
        return expression;
    }
    
    @Override
    public String operationType() { 
        return "filter"; 
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
        if (!(o instanceof FilterOp)) return false;
        FilterOp filterOp = (FilterOp) o;
        return expression.equals(filterOp.expression);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(expression);
    }
    
    @Override
    public String toString() {
        return "FilterOp[expression=" + expression + ", comments=" + associatedComments.size() + "]";
    }
}
