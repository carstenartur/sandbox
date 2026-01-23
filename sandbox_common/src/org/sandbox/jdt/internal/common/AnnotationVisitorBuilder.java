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
import org.eclipse.jdt.core.dom.MarkerAnnotation;

/**
 * Fluent builder for visiting marker annotations.
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>
 * // Simple annotation visitor
 * HelperVisitor.forAnnotation("org.junit.Before")
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
 * will receive both {@code MarkerAnnotation} and {@code ImportDeclaration} nodes. This is
 * intentional to match the pattern used in JUnit cleanup plugins where a single processor
 * handles all related nodes polymorphically. Use {@code instanceof} checks if you need
 * to handle different node types differently.</p>
 * 
 * @author Carsten Hammer
 * @since 1.15
 */
public class AnnotationVisitorBuilder extends HelperVisitorBuilder<MarkerAnnotation> {
    
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
        // Call the marker annotation visitor
        HelperVisitor.callMarkerAnnotationVisitor(annotationFQN, compilationUnit, 
                holder, nodesprocessed, (node, h) -> processor.test(node, h));
        
        // Optionally include import declarations
        if (includeImports) {
            HelperVisitor.callImportDeclarationVisitor(annotationFQN, compilationUnit,
                    holder, nodesprocessed, (node, h) -> processor.test(node, h));
        }
    }
}
