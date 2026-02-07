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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Represents a tree structure of nested loops for conversion analysis.
 * 
 * <p>This class manages the hierarchical structure of loops in source code,
 * allowing bottom-up analysis (inner loops first) to determine which loops
 * can be safely converted to functional stream operations.</p>
 * 
 * <p><b>Usage Pattern:</b></p>
 * <pre>{@code
 * LoopTree tree = new LoopTree();
 * 
 * // When entering a loop:
 * LoopTreeNode node = tree.pushLoop(LoopKind.ENHANCED_FOR);
 * node.setAstNodeReference(astNode);
 * 
 * // ... analyze loop body ...
 * 
 * // When exiting a loop:
 * LoopTreeNode node = tree.popLoop();
 * // Decide if convertible based on children
 * node.setDecision(decision);
 * 
 * // After traversal:
 * List<LoopTreeNode> convertible = tree.getConvertibleNodes();
 * }</pre>
 * 
 * @since 1.0.0
 */
public class LoopTree {
    private final List<LoopTreeNode> roots = new ArrayList<>();
    private final Deque<LoopTreeNode> stack = new ArrayDeque<>();
    
    /**
     * Pushes a new loop onto the stack and adds it to the tree.
     * 
     * <p>If called when the stack is empty, the loop becomes a root.
     * Otherwise, it becomes a child of the current loop.</p>
     * 
     * @param kind the kind of loop being pushed
     * @return the newly created loop tree node
     */
    public LoopTreeNode pushLoop(LoopKind kind) {
        ScopeInfo parentScope = stack.isEmpty() ? new ScopeInfo() : stack.peek().getScopeInfo();
        ScopeInfo childScope = parentScope.createChildScope();
        
        LoopTreeNode node = new LoopTreeNode(kind, childScope);
        
        if (stack.isEmpty()) {
            roots.add(node);
        } else {
            stack.peek().addChild(node);
        }
        
        stack.push(node);
        return node;
    }
    
    /**
     * Pops the current loop from the stack.
     * 
     * <p>This should be called when exiting a loop after analysis is complete.</p>
     * 
     * @return the popped loop tree node
     * @throws java.util.NoSuchElementException if the stack is empty
     */
    public LoopTreeNode popLoop() {
        return stack.pop();
    }
    
    /**
     * Gets the current (innermost) loop being processed.
     * 
     * @return the current loop tree node, or null if the stack is empty
     */
    public LoopTreeNode current() {
        return stack.peek();
    }
    
    /**
     * Checks if currently inside a loop.
     * 
     * @return true if the stack is not empty
     */
    public boolean isInsideLoop() {
        return !stack.isEmpty();
    }
    
    /**
     * Gets all nodes marked as CONVERTIBLE in the tree.
     * 
     * <p>This performs a depth-first traversal and collects all nodes
     * that have been marked as convertible after analysis.</p>
     * 
     * @return an unmodifiable list of convertible nodes
     */
    public List<LoopTreeNode> getConvertibleNodes() {
        List<LoopTreeNode> result = new ArrayList<>();
        for (LoopTreeNode root : roots) {
            collectConvertible(root, result);
        }
        return Collections.unmodifiableList(result);
    }
    
    private void collectConvertible(LoopTreeNode node, List<LoopTreeNode> result) {
        for (LoopTreeNode child : node.getChildren()) {
            collectConvertible(child, result);
        }
        if (node.getDecision() == ConversionDecision.CONVERTIBLE) {
            result.add(node);
        }
    }
    
    /**
     * Gets the root nodes of the loop tree.
     * 
     * <p>Root nodes are top-level loops that are not nested inside
     * other loops.</p>
     * 
     * @return an unmodifiable list of root nodes
     */
    public List<LoopTreeNode> getRoots() { 
        return Collections.unmodifiableList(roots); 
    }
}
