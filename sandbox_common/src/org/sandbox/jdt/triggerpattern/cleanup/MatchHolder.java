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
package org.sandbox.jdt.triggerpattern.cleanup;

import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Expression;

/**
 * Interface for holder objects that store match information for TriggerPattern cleanups.
 * 
 * <p>This interface provides a type-safe contract for holder objects, replacing the
 * reflection-based approach used previously. Implementing this interface allows
 * {@link AbstractPatternCleanupPlugin#processRewriteWithRule} to access holder data
 * without reflection.</p>
 * 
 * <p>Implementations should store the matched AST node and any placeholder bindings
 * from the pattern match.</p>
 * 
 * @since 1.2.5
 */
public interface MatchHolder {
    
    /**
     * Returns the primary matched AST node.
     * 
     * @return the matched node, never null
     */
    ASTNode getMinv();
    
    /**
     * Returns the placeholder bindings from the pattern match.
     * 
     * <p>The map contains placeholder names (e.g., "$x", "$args$") as keys
     * and their bound AST nodes or lists as values.</p>
     * 
     * @return the bindings map, never null (may be empty)
     */
    Map<String, Object> getBindings();
    
    /**
     * Returns the matched node as an Annotation, if applicable.
     * 
     * <p>Default implementation returns null. Override this method if the holder
     * stores annotation matches.</p>
     * 
     * @return the annotation, or null if the matched node is not an annotation
     */
    default Annotation getAnnotation() {
        ASTNode node = getMinv();
        return node instanceof Annotation ? (Annotation) node : null;
    }
    
    /**
     * Gets a placeholder binding as an Expression.
     * 
     * <p>Default implementation looks up the binding in {@link #getBindings()}
     * and returns it if it's an Expression.</p>
     * 
     * @param placeholder the placeholder name (e.g., "$x")
     * @return the bound expression, or null if not found or not an expression
     */
    default Expression getBindingAsExpression(String placeholder) {
        Object value = getBindings().get(placeholder);
        return value instanceof Expression ? (Expression) value : null;
    }
}
