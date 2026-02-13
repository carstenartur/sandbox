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

import org.eclipse.jdt.core.dom.ClassInstanceCreation;

/**
 * Fluent builder for visiting class instance creation expressions (new expressions).
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>
 * // Find all "new String(...)" constructions
 * HelperVisitorFactory.forClassInstanceCreation(String.class)
 *     .in(compilationUnit)
 *     .excluding(nodesprocessed)
 *     .processEach(holder, (creation, h) -&gt; {
 *         processStringCreation(creation, h);
 *         return true;
 *     });
 * </pre>
 * 
 * @author Carsten Hammer
 * @since 1.16
 */
public class ClassInstanceCreationVisitorBuilder extends HelperVisitorBuilder<ClassInstanceCreation> {
    
    private final Class<?> targetClass;
    
    /**
     * Creates a new class instance creation visitor builder for a specific class.
     * 
     * @param targetClass the class to match (e.g., String.class)
     */
    public ClassInstanceCreationVisitorBuilder(Class<?> targetClass) {
        this.targetClass = targetClass;
    }
    
    @Override
    protected <V, H> void executeVisitors(ReferenceHolder<V, H> holder, 
            BiPredicate<ClassInstanceCreation, ReferenceHolder<V, H>> processor) {
        HelperVisitorFactory.callClassInstanceCreationVisitor(targetClass, compilationUnit,
                holder, nodesprocessed, processor);
    }
}
