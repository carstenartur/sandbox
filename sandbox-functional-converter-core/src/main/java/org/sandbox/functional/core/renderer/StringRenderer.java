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
 * Renderer that generates Java code strings.
 * 
 * <p>This implementation is used for testing in the core module.
 * For production use with JDT, see ASTRenderer in sandbox_functional_converter.</p>
 */
public class StringRenderer implements StreamPipelineRenderer<String> {
    
    @Override
    public String renderSource(SourceDescriptor source) {
        if (source == null) return "";
        String expr = source.expression();
        
        return switch (source.type()) {
            case COLLECTION -> expr + ".stream()";
            case ARRAY -> "Arrays.stream(" + expr + ")";
            case ITERABLE -> "StreamSupport.stream(" + expr + ".spliterator(), false)";
            case STREAM -> expr;
            case INT_RANGE -> "IntStream.range(0, " + expr + ")";
            case EXPLICIT_RANGE -> {
                // Parse start and end from expression (format: "start,end")
                String[] parts = expr.split(",");
                if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
                    throw new IllegalArgumentException("Invalid EXPLICIT_RANGE expression: '" + expr
                            + "'. Expected format 'start,end' with non-empty expressions.");
                }
                yield "IntStream.range(" + parts[0].trim() + ", " + parts[1].trim() + ")";
            }
            default -> expr + ".stream()";
        };
    }
    
    @Override
    public String renderFilter(String pipeline, String expression, String variableName) {
        return pipeline + ".filter(" + variableName + " -> " + expression + ")";
    }
    
    @Override
    public String renderFilterOp(String pipeline, FilterOp filterOp, String variableName) {
        if (filterOp.hasComments()) {
            return renderBlockLambda(pipeline, "filter", filterOp.getComments(),
                    "return " + filterOp.expression() + ";", variableName);
        }
        return renderFilter(pipeline, filterOp.expression(), variableName);
    }
    
    @Override
    public String renderMap(String pipeline, String expression, String variableName, String targetType) {
        return pipeline + ".map(" + variableName + " -> " + expression + ")";
    }
    
    @Override
    public String renderMapOp(String pipeline, MapOp mapOp, String variableName) {
        if (mapOp.isSideEffect()) {
            return pipeline + ".map(" + variableName + " -> { " + mapOp.expression() + "; return " + variableName + "; })";
        }
        if (mapOp.hasComments()) {
            return renderBlockLambda(pipeline, "map", mapOp.getComments(),
                    "return " + mapOp.expression() + ";", variableName);
        }
        return renderMap(pipeline, mapOp.expression(), variableName, mapOp.targetType());
    }
    
    @Override
    public String renderFlatMap(String pipeline, String expression, String variableName) {
        return pipeline + ".flatMap(" + variableName + " -> " + expression + ")";
    }
    
    @Override
    public String renderPeek(String pipeline, String expression, String variableName) {
        return pipeline + ".peek(" + variableName + " -> " + expression + ")";
    }
    
    @Override
    public String renderDistinct(String pipeline) {
        return pipeline + ".distinct()";
    }
    
    @Override
    public String renderSorted(String pipeline, String comparatorExpression) {
        if (comparatorExpression == null || comparatorExpression.isEmpty()) {
            return pipeline + ".sorted()";
        }
        return pipeline + ".sorted(" + comparatorExpression + ")";
    }
    
    @Override
    public String renderLimit(String pipeline, long maxSize) {
        return pipeline + ".limit(" + maxSize + ")";
    }
    
    @Override
    public String renderSkip(String pipeline, long count) {
        return pipeline + ".skip(" + count + ")";
    }
    
    @Override
    public String renderForEach(String pipeline, List<String> bodyStatements, 
                                 String variableName, boolean ordered) {
        String body = String.join("; ", bodyStatements);
        String method = ordered ? ".forEachOrdered" : ".forEach";
        return pipeline + method + "(" + variableName + " -> " + body + ")";
    }
    
    @Override
    public String renderCollect(String pipeline, CollectTerminal terminal, String variableName) {
        // Use modern Java 16+ .toList() for conciseness (project targets Java 21)
        // For other collectors, use Collectors API
        String collector = switch (terminal.collectorType()) {
            case TO_LIST -> ".toList()";  // Java 16+ - more concise than Collectors.toList()
            case TO_SET -> ".collect(Collectors.toSet())";
            case TO_MAP -> ".collect(Collectors.toMap(...))";
            case JOINING -> ".collect(Collectors.joining())";
            case GROUPING_BY -> ".collect(Collectors.groupingBy(...))";
            case CUSTOM -> ".collect(...)";
        };
        
        return pipeline + collector;
    }
    
    @Override
    public String renderReduce(String pipeline, ReduceTerminal terminal, String variableName) {
        return pipeline + ".reduce(" + terminal.identity() + ", " + terminal.accumulator() + ")";
    }
    
    @Override
    public String renderCount(String pipeline) {
        return pipeline + ".count()";
    }
    
    @Override
    public String renderFind(String pipeline, boolean findFirst) {
        return pipeline + (findFirst ? ".findFirst()" : ".findAny()");
    }
    
    @Override
    public String renderMatch(String pipeline, MatchTerminal terminal, String variableName) {
        return pipeline + "." + terminal.operationType() + "(" + variableName + " -> " + terminal.predicate() + ")";
    }
    
    /**
     * Renders a block-lambda with comments.
     * Generates code like: {@code pipeline.method(var -> { // comment\n return expr; })}
     */
    private String renderBlockLambda(String pipeline, String method, List<String> comments,
                                      String returnStatement, String variableName) {
        StringBuilder sb = new StringBuilder();
        sb.append(pipeline).append('.').append(method).append('(').append(variableName).append(" -> {\n");
        for (String comment : comments) {
            sb.append("    // ").append(comment).append('\n');
        }
        sb.append("    ").append(returnStatement).append('\n');
        sb.append("})");
        return sb.toString();
    }
}
