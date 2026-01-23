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

/*-
 * #%L
 * Sandbox common
 * %%
 * Copyright (C) 2026 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

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
 * HelperVisitor.forAnnotation("org.junit.Before")
 *     .in(compilationUnit)
 *     .excluding(nodesprocessed)
 *     .processEach((node, holder) -&gt; addOperation(node));
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
     * The processor accepts ASTNode to allow handling different node types polymorphically.
     * The processor returns a boolean - return false to stop visiting.
     * 
     * @param <V> the type of keys in the reference holder
     * @param <H> the type of values in the reference holder
     * @param processor the bi-predicate that processes each found node
     */
    public <V, H> void processEach(BiPredicate<ASTNode, ReferenceHolder<V, H>> processor) {
        ReferenceHolder<V, H> holder = new ReferenceHolder<>();
        executeVisitors(holder, processor);
    }
    
    /**
     * Terminal operation that collects all found nodes into a list.
     * 
     * @return a list of all nodes that match the visitor criteria
     */
    public List<T> collect() {
        List<T> results = new ArrayList<>();
        processEach((node, holder) -> {
            @SuppressWarnings("unchecked")
            T typedNode = (T) node;
            results.add(typedNode);
            return true; // Continue visiting
        });
        return results;
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
            BiPredicate<ASTNode, ReferenceHolder<V, H>> processor);
}
