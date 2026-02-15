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

import java.util.function.BiPredicate;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;

/**
 * Fluent builder for visiting annotations of all types.
 * 
 * <p>This builder matches <b>all annotation types</b> regardless of whether they have parameters:</p>
 * <ul>
 * <li>{@code MarkerAnnotation} - annotations without parameters (e.g., {@code @Override})</li>
 * <li>{@code SingleMemberAnnotation} - annotations with a single value (e.g., {@code @SuppressWarnings("unchecked")})</li>
 * <li>{@code NormalAnnotation} - annotations with named parameters (e.g., {@code @RequestMapping(path="/api", method=GET)})</li>
 * </ul>
 * 
 * <p><b>Difference from underlying HelperVisitor:</b> The {@code HelperVisitorFactory.callMarkerAnnotationVisitor()} 
 * method only matches {@code MarkerAnnotation} nodes (annotations without parameters). This fluent API 
 * abstracts away that limitation by calling visitors for all three annotation types, providing a more 
 * intuitive and flexible interface.</p>
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>
 * // Simple annotation visitor - matches all annotation types
 * HelperVisitorFactory.forAnnotation("java.lang.Deprecated")
 *     .in(compilationUnit)
 *     .excluding(nodesprocessed)
 *     .processEach((annotation, holder) -&gt; {
 *         addOperation(annotation);
 *         return true;
 *     });
 *     
 * // Include import declarations
 * HelperVisitorFactory.forAnnotation("org.junit.Before")
 *     .andImports()
 *     .in(compilationUnit)
 *     .excluding(nodesprocessed)
 *     .processEach((node, holder) -&gt; {
 *         addOperation(node);
 *         return true;
 *     });
 * </pre>
 * 
 * <p><b>Note on mixed node types:</b> When {@code andImports()} is enabled, the processor
 * will receive {@code Annotation} nodes (of any annotation type) and {@code ImportDeclaration} nodes.
 * This is intentional to match the pattern used in JUnit cleanup plugins where a single processor
 * handles all related nodes polymorphically. Use {@code instanceof} checks if you need
 * to handle different node types differently.</p>
 * 
 * @author Carsten Hammer
 * @since 1.15
 */
public class AnnotationVisitorBuilder extends HelperVisitorBuilder<ASTNode> {
    
    private final String annotationFQN;
    private boolean includeImports = false;
    
    /**
     * Creates a new annotation visitor builder.
     * 
     * @param annotationFQN the fully qualified name of the annotation to find
     */
    public AnnotationVisitorBuilder(String annotationFQN) {
        this.annotationFQN = annotationFQN;
    }
    
    /**
     * Configures the builder to also process import declarations for this annotation.
     * 
     * @return this builder for chaining
     */
    public AnnotationVisitorBuilder andImports() {
        this.includeImports = true;
        return this;
    }
    
    @Override
    protected <V, H> void executeVisitors(ReferenceHolder<V, H> holder, 
            BiPredicate<ASTNode, ReferenceHolder<V, H>> processor) {
        // Use the HelperVisitor stored in the ReferenceHolder to track continuation state
        // This ensures early termination works correctly when visiting multiple annotation types
        HelperVisitor<ReferenceHolder<V, H>, V, H> helperVisitor = holder.getHelperVisitor();
        if (helperVisitor == null) {
            helperVisitor = new HelperVisitor<>(nodesprocessed, holder);
            holder.setHelperVisitor(helperVisitor);
        }
        
        // Track continuation state using an array to allow modification from lambdas
        // IMPORTANT: Do NOT store this in the holder as it would pollute the user's data
        // and break indexing when users call holder.size() to get the next key
        final boolean[] shouldContinue = { true };
        
        // Create a single adapter that handles exclusion, continuation, and delegation
        // for all node types (annotations and imports) uniformly
        BiPredicate<MarkerAnnotation, ReferenceHolder<V, H>> markerAdapter =
                createNodeAdapter(processor, shouldContinue);
        BiPredicate<SingleMemberAnnotation, ReferenceHolder<V, H>> singleMemberAdapter =
                createNodeAdapter(processor, shouldContinue);
        BiPredicate<NormalAnnotation, ReferenceHolder<V, H>> normalAdapter =
                createNodeAdapter(processor, shouldContinue);
        
        // Call visitors for all three annotation types to match annotations regardless of parameters
        if (shouldContinue[0]) {
            HelperVisitorFactory.callMarkerAnnotationVisitor(annotationFQN, compilationUnit, 
                    holder, nodesprocessed, markerAdapter);
        }
        if (shouldContinue[0]) {
            HelperVisitorFactory.callSingleMemberAnnotationVisitor(annotationFQN, compilationUnit,
                    holder, nodesprocessed, singleMemberAdapter);
        }
        if (shouldContinue[0]) {
            HelperVisitorFactory.callNormalAnnotationVisitor(annotationFQN, compilationUnit,
                    holder, nodesprocessed, normalAdapter);
        }
        
        // Optionally include import declarations
        if (shouldContinue[0] && includeImports) {
            BiPredicate<ImportDeclaration, ReferenceHolder<V, H>> importAdapter =
                    createNodeAdapter(processor, shouldContinue);
            HelperVisitorFactory.callImportDeclarationVisitor(annotationFQN, compilationUnit,
                    holder, nodesprocessed, importAdapter);
        }
    }
    
    /**
     * Creates a type-safe adapter BiPredicate that handles exclusion checking,
     * continuation tracking, and delegation to the main processor.
     * 
     * @param <N> the specific AST node type (e.g., MarkerAnnotation, ImportDeclaration)
     * @param <V> the key type of the reference holder
     * @param <H> the value type of the reference holder
     * @param processor the main processor to delegate to
     * @param shouldContinue shared continuation flag (modified on processor returning false)
     * @return a BiPredicate adapter for the specific node type
     */
    private <N extends ASTNode, V, H> BiPredicate<N, ReferenceHolder<V, H>> createNodeAdapter(
            BiPredicate<ASTNode, ReferenceHolder<V, H>> processor, boolean[] shouldContinue) {
        return (N node, ReferenceHolder<V, H> h) -> {
            if (nodesprocessed != null && nodesprocessed.contains(node)) {
                return true; // Skip this node but continue processing others
            }
            if (!shouldContinue[0]) {
                return false;
            }
            boolean result = processor.test(node, h);
            if (!result) {
                shouldContinue[0] = false;
            }
            return result;
        };
    }
}
