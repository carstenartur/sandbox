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
package org.sandbox.functional.core.renderer;

import java.util.function.Supplier;

/**
 * Extended renderer interface that supports AST-aware callbacks.
 * 
 * <p>This interface allows implementations to receive callbacks that produce
 * AST elements directly, avoiding string parsing and preserving binding information.</p>
 * 
 * @param <T> the pipeline type (e.g., Expression for JDT)
 * @param <S> the statement type (e.g., Statement for JDT)  
 * @param <E> the expression type (e.g., Expression for JDT)
 */
public interface ASTAwareRenderer<T, S, E> extends StreamPipelineRenderer<T> {
    
    /**
     * Renders a forEach with an AST body supplier instead of strings.
     * 
     * @param pipeline the current pipeline
     * @param bodySupplier supplier that provides the body expression/statement
     * @param variableName the loop variable name
     * @param ordered whether to use forEachOrdered
     * @return the pipeline with forEach appended
     */
    default T renderForEachWithBody(T pipeline, Supplier<E> bodySupplier, 
                                     String variableName, boolean ordered) {
        throw new UnsupportedOperationException("AST-aware rendering not supported");
    }
    
    /**
     * Renders a filter with an AST predicate supplier.
     * 
     * @param pipeline the current pipeline
     * @param predicateSupplier supplier that provides the predicate expression
     * @param variableName the loop variable name
     * @return the pipeline with filter appended
     */
    default T renderFilterWithPredicate(T pipeline, Supplier<E> predicateSupplier,
                                         String variableName) {
        throw new UnsupportedOperationException("AST-aware rendering not supported");
    }
    
    /**
     * Renders a map with an AST mapper supplier.
     * 
     * @param pipeline the current pipeline
     * @param mapperSupplier supplier that provides the mapper expression
     * @param variableName the loop variable name
     * @param targetType the target type (may be null)
     * @return the pipeline with map appended
     */
    default T renderMapWithMapper(T pipeline, Supplier<E> mapperSupplier,
                                   String variableName, String targetType) {
        throw new UnsupportedOperationException("AST-aware rendering not supported");
    }
}
