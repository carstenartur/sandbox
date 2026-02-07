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

import org.eclipse.jdt.core.dom.FieldDeclaration;

/**
 * Fluent builder for visiting field declarations.
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>
 * // Field with annotation and type
 * HelperVisitor.forField()
 *     .withAnnotation("org.junit.Rule")
 *     .ofType("org.junit.rules.TemporaryFolder")
 *     .in(compilationUnit)
 *     .excluding(nodesprocessed)
 *     .processEach((field, holder) -&gt; {
 *         addOperation(field);
 *         return true;
 *     });
 * </pre>
 * 
 * @author Carsten Hammer
 * @since 1.15
 */
public class FieldVisitorBuilder extends HelperVisitorBuilder<FieldDeclaration> {
    
    private String annotationFQN;
    private String typeFQN;
    
    /**
     * Configures the builder to find fields with the specified annotation.
     * 
     * @param annotationFQN the fully qualified name of the annotation
     * @return this builder for chaining
     */
    public FieldVisitorBuilder withAnnotation(String annotationFQN) {
        this.annotationFQN = annotationFQN;
        return this;
    }
    
    /**
     * Configures the builder to find fields of the specified type (or subtype).
     * 
     * @param typeFQN the fully qualified name of the type
     * @return this builder for chaining
     */
    public FieldVisitorBuilder ofType(String typeFQN) {
        this.typeFQN = typeFQN;
        return this;
    }
    
    @Override
    protected void validateState() {
        super.validateState();
        if (annotationFQN == null || typeFQN == null) {
            throw new IllegalStateException(
                "FieldVisitorBuilder requires both withAnnotation(...) and ofType(...) to be configured " //$NON-NLS-1$
                + "before processing. annotationFQN=" + annotationFQN + ", typeFQN=" + typeFQN); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }
    
    @Override
    protected <V, H> void executeVisitors(ReferenceHolder<V, H> holder, 
            BiPredicate<FieldDeclaration, ReferenceHolder<V, H>> processor) {
        HelperVisitor.callFieldDeclarationVisitor(annotationFQN, typeFQN, compilationUnit,
                holder, nodesprocessed, processor);
    }
}
