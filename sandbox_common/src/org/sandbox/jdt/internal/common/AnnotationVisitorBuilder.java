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
import org.eclipse.jdt.core.dom.Annotation;

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
 * <p><b>Difference from underlying HelperVisitor:</b> The {@code HelperVisitor.callMarkerAnnotationVisitor()} 
 * method only matches {@code MarkerAnnotation} nodes (annotations without parameters). This fluent API 
 * abstracts away that limitation by calling visitors for all three annotation types, providing a more 
 * intuitive and flexible interface.</p>
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>
 * // Simple annotation visitor - matches all annotation types
 * HelperVisitor.forAnnotation("java.lang.Deprecated")
 *     .in(compilationUnit)
 *     .excluding(nodesprocessed)
 *     .processEach((annotation, holder) -&gt; {
 *         addOperation(annotation);
 *         return true;
 *     });
 *     
 * // Include import declarations
 * HelperVisitor.forAnnotation("org.junit.Before")
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
public class AnnotationVisitorBuilder extends HelperVisitorBuilder<Annotation> {
    
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
        // Use a flag to track if processing should continue across all visitor calls
        // This ensures early termination works correctly when visiting multiple annotation types
        boolean[] continueProcessing = {true};
        BiPredicate<ASTNode, ReferenceHolder<V, H>> wrappedProcessor = (node, h) -> {
            if (!continueProcessing[0]) {
                return false;
            }
            boolean result = processor.test(node, h);
            if (!result) {
                continueProcessing[0] = false;
            }
            return result;
        };
        
        // Call visitors for all three annotation types to match annotations regardless of parameters
        if (continueProcessing[0]) {
            HelperVisitor.callMarkerAnnotationVisitor(annotationFQN, compilationUnit, 
                    holder, nodesprocessed, wrappedProcessor);
        }
        if (continueProcessing[0]) {
            HelperVisitor.callSingleMemberAnnotationVisitor(annotationFQN, compilationUnit,
                    holder, nodesprocessed, wrappedProcessor);
        }
        if (continueProcessing[0]) {
            HelperVisitor.callNormalAnnotationVisitor(annotationFQN, compilationUnit,
                    holder, nodesprocessed, wrappedProcessor);
        }
        
        // Optionally include import declarations
        if (continueProcessing[0] && includeImports) {
            HelperVisitor.callImportDeclarationVisitor(annotationFQN, compilationUnit,
                    holder, nodesprocessed, wrappedProcessor);
        }
    }
}
