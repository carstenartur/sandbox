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
package org.sandbox.functional.core.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sandbox.functional.core.model.LoopModel;

/**
 * Represents a node in the loop tree.
 * 
 * <p>Each node represents a loop in the source code and maintains information
 * about its scope, children (nested loops), conversion decision, and optional
 * AST node reference for integration with Eclipse JDT.</p>
 * 
 * @since 1.0.0
 */
public class LoopTreeNode {
    private final LoopKind kind;
    private final ScopeInfo scopeInfo;
    private final List<LoopTreeNode> children = new ArrayList<>();
    private LoopTreeNode parent;
    private ConversionDecision decision = ConversionDecision.PENDING;
    private LoopModel loopModel;
    private Object astNodeReference;
    
    /**
     * Creates a new loop tree node.
     * 
     * @param kind the kind of loop
     * @param scopeInfo the scope information for this loop
     */
    public LoopTreeNode(LoopKind kind, ScopeInfo scopeInfo) {
        this.kind = kind;
        this.scopeInfo = scopeInfo;
    }
    
    /**
     * Adds a child node to this node.
     * 
     * @param child the child node to add
     */
    public void addChild(LoopTreeNode child) {
        children.add(child);
        child.parent = this;
    }
    
    /**
     * Checks if any descendant node is convertible.
     * 
     * <p>This is used to determine if a parent loop should be skipped
     * because an inner loop can be converted.</p>
     * 
     * @return true if any descendant has a CONVERTIBLE decision
     */
    public boolean hasConvertibleDescendant() {
        for (LoopTreeNode child : children) {
            if (child.decision == ConversionDecision.CONVERTIBLE || child.hasConvertibleDescendant()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if all children are non-convertible.
     * 
     * <p>Children with NOT_CONVERTIBLE or SKIPPED_INNER_CONVERTED
     * decisions are considered non-convertible.</p>
     * 
     * @return true if all children are non-convertible
     */
    public boolean allChildrenNonConvertible() {
        return children.stream()
            .allMatch(c -> c.decision == ConversionDecision.NOT_CONVERTIBLE 
                        || c.decision == ConversionDecision.SKIPPED_INNER_CONVERTED);
    }
    
    /**
     * Gets the kind of loop.
     * 
     * @return the loop kind
     */
    public LoopKind getKind() { 
        return kind; 
    }
    
    /**
     * Gets the scope information for this loop.
     * 
     * @return the scope info
     */
    public ScopeInfo getScopeInfo() { 
        return scopeInfo; 
    }
    
    /**
     * Gets an unmodifiable view of child nodes.
     * 
     * @return unmodifiable list of children
     */
    public List<LoopTreeNode> getChildren() { 
        return Collections.unmodifiableList(children); 
    }
    
    /**
     * Gets the parent node.
     * 
     * @return the parent node, or null if this is a root
     */
    public LoopTreeNode getParent() { 
        return parent; 
    }
    
    /**
     * Gets the conversion decision for this loop.
     * 
     * @return the conversion decision
     */
    public ConversionDecision getDecision() { 
        return decision; 
    }
    
    /**
     * Sets the conversion decision for this loop.
     * 
     * @param decision the conversion decision
     */
    public void setDecision(ConversionDecision decision) { 
        this.decision = decision; 
    }
    
    /**
     * Gets the loop model if this loop was analyzed.
     * 
     * @return the loop model, or null if not analyzed
     */
    public LoopModel getLoopModel() { 
        return loopModel; 
    }
    
    /**
     * Sets the loop model for this node.
     * 
     * @param loopModel the loop model
     */
    public void setLoopModel(LoopModel loopModel) { 
        this.loopModel = loopModel; 
    }
    
    /**
     * Gets the AST node reference.
     * 
     * <p>This is used by the Eclipse JDT integration to link tree nodes
     * back to their source AST nodes for rewriting.</p>
     * 
     * @return the AST node reference, or null if not set
     */
    public Object getAstNodeReference() { 
        return astNodeReference; 
    }
    
    /**
     * Sets the AST node reference.
     * 
     * @param astNodeReference the AST node reference
     */
    public void setAstNodeReference(Object astNodeReference) { 
        this.astNodeReference = astNodeReference; 
    }
}
