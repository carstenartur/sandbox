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
package org.sandbox.jdt.internal.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * Base class for fluent-style builders that simplify HelperVisitor usage.
 * 
 * <p>This builder provides a fluent API for configuring and executing AST visitors,
 * making the code more readable and reducing boilerplate.</p>
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>
 * HelperVisitorFactory.forAnnotation("org.junit.Before")
 *     .in(compilationUnit)
 *     .excluding(nodesprocessed)
 *     .processEach((node, holder) -&gt; {
 *         addOperation(node);
 *         return true; // Continue visiting
 *     });
 * </pre>
 * 
 * @param <T> the type of AST node being visited
 * 
 * @author Carsten Hammer
 * @since 1.15
 */
public abstract class HelperVisitorBuilder<T extends ASTNode> {
    
    /**
     * The compilation unit to visit.
     */
    protected CompilationUnit compilationUnit;
    
    /**
     * Set of nodes that have already been processed (to avoid duplicate processing).
     */
    protected Set<ASTNode> nodesprocessed;
    
    /**
     * Configures the compilation unit to visit.
     * 
     * @param cu the compilation unit
     * @return this builder for chaining
     */
    public HelperVisitorBuilder<T> in(CompilationUnit cu) {
        this.compilationUnit = cu;
        return this;
    }
    
    /**
     * Configures the set of nodes to exclude from processing.
     * 
     * @param processed the set of already processed nodes
     * @return this builder for chaining
     */
    public HelperVisitorBuilder<T> excluding(Set<ASTNode> processed) {
        this.nodesprocessed = processed;
        return this;
    }
    
    /**
     * Terminal operation that processes each found node with the given processor.
     * The processor accepts the specific node type T (e.g., MethodInvocation, ClassInstanceCreation).
     * The processor returns a boolean - return false to stop visiting.
     * 
     * @param <V> the type of keys in the reference holder
     * @param <H> the type of values in the reference holder
     * @param processor the bi-predicate that processes each found node
     * @throws IllegalStateException if the compilation unit was not configured via {@code in(...)}
     */
    public <V, H> void processEach(BiPredicate<T, ReferenceHolder<V, H>> processor) {
        validateState();
        ReferenceHolder<V, H> holder = new ReferenceHolder<>();
        executeVisitors(holder, processor);
    }
    
    /**
     * Terminal operation that processes each found node with the given processor,
     * using a pre-initialized ReferenceHolder for data collection.
     * The processor accepts the specific node type T (e.g., MethodInvocation, ClassInstanceCreation).
     * The processor returns a boolean - return false to stop visiting.
     * 
     * @param <V> the type of keys in the reference holder
     * @param <H> the type of values in the reference holder
     * @param holder the pre-initialized reference holder to use for data collection
     * @param processor the bi-predicate that processes each found node
     * @throws IllegalStateException if the compilation unit was not configured via {@code in(...)}
     */
    public <V, H> void processEach(ReferenceHolder<V, H> holder, BiPredicate<T, ReferenceHolder<V, H>> processor) {
        validateState();
        executeVisitors(holder, processor);
    }
    
    /**
     * Terminal operation that collects all found nodes into a list.
     * Note: This returns ASTNode instead of T to handle cases where builders
     * may visit multiple node types (e.g., when including imports alongside annotations).
     * 
     * @return a list of all nodes that match the visitor criteria
     * @throws IllegalStateException if the compilation unit was not configured via {@code in(...)}
     */
    public List<ASTNode> collect() {
        validateState();
        List<ASTNode> results = new ArrayList<>();
        processEach((node, holder) -> {
            results.add(node);
            return true; // Continue visiting
        });
        return results;
    }
    
    /**
     * Validates that required state has been configured before executing visitors.
     * 
     * @throws IllegalStateException if the compilation unit was not set
     */
    protected void validateState() {
        if (compilationUnit == null) {
            throw new IllegalStateException(
                "Compilation unit must be configured via in(...) before calling processEach() or collect()"); //$NON-NLS-1$
        }
    }
    
    /**
     * Executes the configured visitors on the compilation unit.
     * Subclasses must implement this to delegate to the appropriate HelperVisitor static methods.
     * 
     * @param <V> the type of keys in the reference holder
     * @param <H> the type of values in the reference holder
     * @param holder the reference holder for collecting data
     * @param processor the processor to call for each found node
     */
    protected abstract <V, H> void executeVisitors(ReferenceHolder<V, H> holder, 
            BiPredicate<T, ReferenceHolder<V, H>> processor);
}
