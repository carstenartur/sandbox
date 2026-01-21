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
package org.sandbox.functional.core.builder;

import java.util.stream.Collectors;
import org.sandbox.functional.core.model.*;
import org.sandbox.functional.core.operation.*;
import org.sandbox.functional.core.terminal.*;

/**
 * Generates stream code strings from a LoopModel.
 * 
 * <p>This builder is AST-independent and generates plain Java code strings.
 * JDT-specific AST generation is handled by JdtStreamGenerator in the
 * sandbox_functional_converter module.</p>
 * 
 * <p>Example:</p>
 * <pre>
 * String code = new StreamCodeBuilder(loopModel).build();
 * // Returns: "list.stream().filter(item -> item != null).forEach(item -> System.out.println(item))"
 * </pre>
 */
public class StreamCodeBuilder {
    
    private final LoopModel model;
    
    public StreamCodeBuilder(LoopModel model) {
        this.model = model;
    }
    
    /**
     * Builds the complete stream pipeline code.
     * 
     * @return the generated stream code as a string
     */
    public String build() {
        StringBuilder sb = new StringBuilder();
        
        // Source
        sb.append(buildSource());
        
        // Operations
        for (Operation op : model.getOperations()) {
            sb.append(buildOperation(op));
        }
        
        // Terminal
        if (model.getTerminal() != null) {
            sb.append(buildTerminal(model.getTerminal()));
        }
        
        return sb.toString();
    }
    
    private String buildSource() {
        SourceDescriptor source = model.getSource();
        if (source == null) return "";
        
        String expr = source.getExpression();
        
        return switch (source.getType()) {
            case COLLECTION -> expr + ".stream()";
            case ARRAY -> "Arrays.stream(" + expr + ")";
            case ITERABLE -> "StreamSupport.stream(" + expr + ".spliterator(), false)";
            case STREAM -> expr;
            case INT_RANGE -> "IntStream.range(0, " + expr + ")";
            default -> expr + ".stream()";
        };
    }
    
    private String buildOperation(Operation op) {
        String expr = op.expression();
        String varName = model.getElement() != null ? 
                         model.getElement().getVariableName() : "x";
        
        return switch (op) {
            case FilterOp f -> ".filter(" + varName + " -> " + f.expression() + ")";
            case MapOp m -> ".map(" + varName + " -> " + m.expression() + ")";
            case FlatMapOp fm -> ".flatMap(" + varName + " -> " + fm.expression() + ")";
            case PeekOp p -> ".peek(" + varName + " -> " + p.expression() + ")";
            case DistinctOp d -> ".distinct()";
            case SortOp s -> s.expression() != null ? 
                             ".sorted(" + s.expression() + ")" : ".sorted()";
            case LimitOp l -> ".limit(" + l.maxSize() + ")";
            case SkipOp sk -> ".skip(" + sk.count() + ")";
        };
    }
    
    private String buildTerminal(TerminalOperation terminal) {
        String varName = model.getElement() != null ? 
                         model.getElement().getVariableName() : "x";
        
        return switch (terminal) {
            case ForEachTerminal fe -> {
                String body = String.join("; ", fe.bodyStatements());
                String method = fe.ordered() ? ".forEachOrdered" : ".forEach";
                yield method + "(" + varName + " -> " + body + ")";
            }
            case CollectTerminal c -> {
                String collector = switch (c.collectorType()) {
                    case TO_LIST -> "Collectors.toList()";
                    case TO_SET -> "Collectors.toSet()";
                    case TO_MAP -> "Collectors.toMap(...)";
                    case JOINING -> "Collectors.joining()";
                    case GROUPING_BY -> "Collectors.groupingBy(...)";
                    case CUSTOM -> "...";
                };
                yield ".collect(" + collector + ")";
            }
            case ReduceTerminal r -> ".reduce(" + r.identity() + ", " + r.accumulator() + ")";
            case CountTerminal ct -> ".count()";
            case FindTerminal f -> f.findFirst() ? ".findFirst()" : ".findAny()";
            case MatchTerminal m -> "." + m.operationType() + "(" + varName + " -> " + m.predicate() + ")";
        };
    }
    
    /**
     * Returns whether the model can be converted to a stream pipeline.
     */
    public boolean canBuild() {
        if (model == null || model.getSource() == null) return false;
        if (model.getMetadata() != null && model.getMetadata().hasBreak()) return false;
        return true;
    }
    
    /**
     * Returns required imports for the generated code.
     */
    public java.util.Set<String> getRequiredImports() {
        java.util.Set<String> imports = new java.util.HashSet<>();
        
        if (model.getSource() != null) {
            switch (model.getSource().getType()) {
                case ARRAY -> imports.add("java.util.Arrays");
                case ITERABLE -> imports.add("java.util.stream.StreamSupport");
                case INT_RANGE -> imports.add("java.util.stream.IntStream");
                default -> {}
            }
        }
        
        if (model.getTerminal() instanceof CollectTerminal) {
            imports.add("java.util.stream.Collectors");
        }
        
        return imports;
    }
}
