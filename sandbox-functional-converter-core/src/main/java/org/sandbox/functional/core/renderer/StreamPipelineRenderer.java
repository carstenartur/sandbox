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

import java.util.List;
import org.sandbox.functional.core.model.*;
import org.sandbox.functional.core.operation.*;
import org.sandbox.functional.core.terminal.*;

/**
 * Functional callback interface for rendering stream pipeline elements.
 * 
 * <p>This interface abstracts the code generation, allowing different
 * implementations for different targets:</p>
 * <ul>
 *   <li>{@code StringRenderer} - generates Java code strings (for tests)</li>
 *   <li>{@code ASTRenderer} - generates JDT AST nodes (in sandbox_functional_converter)</li>
 * </ul>
 * 
 * @param <T> the type of the rendered output (e.g., String, Expression)
 */
public interface StreamPipelineRenderer<T> {
    
    /**
     * Renders the stream source (e.g., "list.stream()" or "Arrays.stream(arr)").
     */
    T renderSource(SourceDescriptor source);
    
    /**
     * Renders a filter operation.
     */
    T renderFilter(T pipeline, String expression, String variableName);
    
    /**
     * Renders a filter operation with full Operation context (for comment preservation).
     * Default implementation delegates to the simple renderFilter method.
     * 
     * @param pipeline the current pipeline
     * @param filterOp the filter operation (may contain comments)
     * @param variableName the variable name for the lambda
     * @return the pipeline with filter appended
     */
    default T renderFilterOp(T pipeline, FilterOp filterOp, String variableName) {
        if (filterOp.function() != null) {
            return renderFunction(pipeline, filterOp.operationType(), filterOp.function());
        }
        return renderFilter(pipeline, filterOp.expression(), variableName);
    }
    
    /**
     * Renders a map operation.
     */
    T renderMap(T pipeline, String expression, String variableName, String targetType);
    
    /**
     * Renders a map operation with full Operation context (for comment preservation).
     * Default implementation delegates to the simple renderMap method.
     * 
     * @param pipeline the current pipeline
     * @param mapOp the map operation (may contain comments)
     * @param variableName the variable name for the lambda
     * @return the pipeline with map appended
     */
    default T renderMapOp(T pipeline, MapOp mapOp, String variableName) {
        if (mapOp.function() != null) {
            return renderFunction(pipeline, mapOp.operationType(), mapOp.function());
        }
        return renderMap(pipeline, mapOp.expression(), variableName, mapOp.targetType());
    }

    /** Renders a complete functional argument, preserving its own parameter scope. */
    default T renderFunction(T pipeline, String operation, FunctionalExpression function) {
        throw new UnsupportedOperationException("Complete functional arguments are not supported");
    }
    
    /**
     * Renders a flatMap operation.
     */
    T renderFlatMap(T pipeline, String expression, String variableName);
    
    /**
     * Renders a peek operation.
     */
    T renderPeek(T pipeline, String expression, String variableName);

    default T renderPeekOp(T pipeline, org.sandbox.functional.core.operation.PeekOp peek, String variableName) {
        return peek.function() == null ? renderPeek(pipeline, peek.expression(), variableName)
                : renderFunction(pipeline, peek.operationType(), peek.function());
    }

    default T renderTypeConversion(T pipeline, org.sandbox.functional.core.operation.StreamTypeConversionOp conversion) {
        throw new UnsupportedOperationException("Stream type conversions are not supported");
    }
    
    /**
     * Renders a distinct operation.
     */
    T renderDistinct(T pipeline);
    
    /**
     * Renders a sorted operation.
     */
    T renderSorted(T pipeline, String comparatorExpression);
    
    /**
     * Renders a limit operation.
     */
    T renderLimit(T pipeline, long maxSize);
    
    /**
     * Renders a skip operation.
     */
    T renderSkip(T pipeline, long count);
    
    /**
     * Renders a forEach terminal operation.
     */
    T renderForEach(T pipeline, List<String> bodyStatements, String variableName, boolean ordered);

    default T renderForEachTerminal(T pipeline, ForEachTerminal terminal, String variableName) {
        if (terminal.function() != null) {
            return renderFunction(pipeline, terminal.operationType(), terminal.function());
        }
        return renderForEach(pipeline, terminal.bodyStatements(), variableName, terminal.ordered());
    }
    
    /**
     * Renders a collect terminal operation.
     */
    T renderCollect(T pipeline, CollectTerminal terminal, String variableName);
    
    /**
     * Renders a reduce terminal operation.
     */
    T renderReduce(T pipeline, ReduceTerminal terminal, String variableName);
    
    /**
     * Renders a count terminal operation.
     */
    T renderCount(T pipeline);
    
    /**
     * Renders a find terminal operation.
     */
    T renderFind(T pipeline, boolean findFirst);
    
    /**
     * Renders a match terminal operation.
     */
    T renderMatch(T pipeline, MatchTerminal terminal, String variableName);
}
