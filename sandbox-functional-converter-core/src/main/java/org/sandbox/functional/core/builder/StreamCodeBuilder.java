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
        
        switch (source.getType()) {
            case COLLECTION:
                return expr + ".stream()";
            case ARRAY:
                return "Arrays.stream(" + expr + ")";
            case ITERABLE:
                return "StreamSupport.stream(" + expr + ".spliterator(), false)";
            case STREAM:
                return expr;
            case INT_RANGE:
                return "IntStream.range(0, " + expr + ")";
            default:
                return expr + ".stream()";
        }
    }
    
    private String buildOperation(Operation op) {
        String expr = op.expression();
        String varName = model.getElement() != null ? 
                         model.getElement().getVariableName() : "x";
        
        if (op instanceof FilterOp f) {
            return ".filter(" + varName + " -> " + f.expression() + ")";
        } else if (op instanceof MapOp m) {
            return ".map(" + varName + " -> " + m.expression() + ")";
        } else if (op instanceof FlatMapOp fm) {
            return ".flatMap(" + varName + " -> " + fm.expression() + ")";
        } else if (op instanceof PeekOp p) {
            return ".peek(" + varName + " -> " + p.expression() + ")";
        } else if (op instanceof DistinctOp) {
            return ".distinct()";
        } else if (op instanceof SortOp s) {
            return s.expression() != null ? 
                   ".sorted(" + s.expression() + ")" : ".sorted()";
        } else if (op instanceof LimitOp l) {
            return ".limit(" + l.maxSize() + ")";
        } else if (op instanceof SkipOp sk) {
            return ".skip(" + sk.count() + ")";
        }
        return "";
    }
    
    private String buildTerminal(TerminalOperation terminal) {
        String varName = model.getElement() != null ? 
                         model.getElement().getVariableName() : "x";
        
        if (terminal instanceof ForEachTerminal fe) {
            String body = String.join("; ", fe.bodyStatements());
            String method = fe.ordered() ? ".forEachOrdered" : ".forEach";
            return method + "(" + varName + " -> " + body + ")";
        } else if (terminal instanceof CollectTerminal c) {
            String collector;
            switch (c.collectorType()) {
                case TO_LIST:
                    collector = "Collectors.toList()";
                    break;
                case TO_SET:
                    collector = "Collectors.toSet()";
                    break;
                case TO_MAP:
                    collector = "Collectors.toMap(...)";
                    break;
                case JOINING:
                    collector = "Collectors.joining()";
                    break;
                case GROUPING_BY:
                    collector = "Collectors.groupingBy(...)";
                    break;
                case CUSTOM:
                default:
                    collector = "...";
                    break;
            }
            return ".collect(" + collector + ")";
        } else if (terminal instanceof ReduceTerminal r) {
            return ".reduce(" + r.identity() + ", " + r.accumulator() + ")";
        } else if (terminal instanceof CountTerminal) {
            return ".count()";
        } else if (terminal instanceof FindTerminal f) {
            return f.findFirst() ? ".findFirst()" : ".findAny()";
        } else if (terminal instanceof MatchTerminal m) {
            return "." + m.operationType() + "(" + varName + " -> " + m.predicate() + ")";
        }
        return "";
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
