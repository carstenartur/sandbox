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
package org.sandbox.functional.core.transformer;

import org.sandbox.functional.core.model.*;
import org.sandbox.functional.core.operation.*;
import org.sandbox.functional.core.renderer.StreamPipelineRenderer;
import org.sandbox.functional.core.terminal.*;

/**
 * Transforms a LoopModel into a stream pipeline using a renderer.
 * 
 * <p>This class is the central transformation engine. It iterates through
 * the LoopModel's operations and terminals, delegating rendering to the
 * provided {@link StreamPipelineRenderer}.</p>
 * 
 * @param <T> the output type (e.g., String for StringRenderer, Expression for ASTRenderer)
 */
public class LoopModelTransformer<T> {
    
    private final StreamPipelineRenderer<T> renderer;
    
    public LoopModelTransformer(StreamPipelineRenderer<T> renderer) {
        this.renderer = renderer;
    }
    
    /**
     * Transforms the given LoopModel into the target representation.
     * 
     * @param model the loop model to transform
     * @return the transformed result
     */
    public T transform(LoopModel model) {
        if (model == null || model.getSource() == null) {
            throw new IllegalArgumentException("LoopModel and source must not be null");
        }
        
        String varName = model.getElement() != null 
            ? model.getElement().variableName() 
            : "x";
        
        // Start with source
        T pipeline = renderer.renderSource(model.getSource());
        
        // Apply operations
        for (Operation op : model.getOperations()) {
            pipeline = applyOperation(pipeline, op, varName);
        }
        
        // Apply terminal
        if (model.getTerminal() != null) {
            pipeline = applyTerminal(pipeline, model.getTerminal(), varName);
        }
        
        return pipeline;
    }
    
    private T applyOperation(T pipeline, Operation op, String varName) {
        return switch (op) {
            case FilterOp f -> renderer.renderFilter(pipeline, f.expression(), varName);
            case MapOp m -> renderer.renderMap(pipeline, m.expression(), varName, m.targetType());
            case FlatMapOp fm -> renderer.renderFlatMap(pipeline, fm.expression(), varName);
            case PeekOp p -> renderer.renderPeek(pipeline, p.expression(), varName);
            case DistinctOp d -> renderer.renderDistinct(pipeline);
            case SortOp s -> renderer.renderSorted(pipeline, s.expression());
            case LimitOp l -> renderer.renderLimit(pipeline, l.maxSize());
            case SkipOp sk -> renderer.renderSkip(pipeline, sk.count());
        };
    }
    
    private T applyTerminal(T pipeline, TerminalOperation terminal, String varName) {
        return switch (terminal) {
            case ForEachTerminal fe -> renderer.renderForEach(
                pipeline, fe.bodyStatements(), varName, fe.ordered());
            case CollectTerminal c -> renderer.renderCollect(pipeline, c, varName);
            case ReduceTerminal r -> renderer.renderReduce(pipeline, r, varName);
            case CountTerminal ct -> renderer.renderCount(pipeline);
            case FindTerminal f -> renderer.renderFind(pipeline, f.findFirst());
            case MatchTerminal m -> renderer.renderMatch(pipeline, m, varName);
        };
    }
    
    /**
     * Checks if the model can be transformed.
     */
    public boolean canTransform(LoopModel model) {
        if (model == null || model.getSource() == null) return false;
        return model.isConvertible();
    }
}
