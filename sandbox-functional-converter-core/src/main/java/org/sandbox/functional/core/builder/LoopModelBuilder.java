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

import java.util.ArrayList;
import java.util.List;
import org.sandbox.functional.core.model.*;
import org.sandbox.functional.core.operation.*;
import org.sandbox.functional.core.terminal.*;

/**
 * Fluent builder for constructing LoopModel instances.
 * 
 * <p>This builder is AST-independent and works with abstract data.
 * JDT-specific extraction is handled by JdtLoopExtractor in the
 * sandbox_functional_converter module.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * LoopModel model = new LoopModelBuilder()
 *     .source(SourceType.COLLECTION, "list", "String")
 *     .element("item", "String", false)
 *     .metadata(false, false, false, false, true)
 *     .filter("item != null")
 *     .map("item.toUpperCase()", "String")
 *     .forEach(List.of("System.out.println(item)"), false)
 *     .build();
 * </pre>
 */
public class LoopModelBuilder {
    
    private SourceDescriptor source;
    private ElementDescriptor element;
    private LoopMetadata metadata;
    private final List<Operation> operations = new ArrayList<>();
    private TerminalOperation terminal;
    
    public LoopModelBuilder() {}
    
    // Source configuration
    public LoopModelBuilder source(SourceDescriptor.SourceType type, 
                                   String expression, 
                                   String elementTypeName) {
        this.source = new SourceDescriptor(type, expression, elementTypeName);
        return this;
    }
    
    public LoopModelBuilder source(SourceDescriptor source) {
        this.source = source;
        return this;
    }
    
    // Element configuration
    public LoopModelBuilder element(String variableName, 
                                    String typeName, 
                                    boolean isFinal) {
        this.element = new ElementDescriptor(variableName, typeName, isFinal);
        return this;
    }
    
    public LoopModelBuilder element(ElementDescriptor element) {
        this.element = element;
        return this;
    }
    
    // Metadata configuration
    public LoopModelBuilder metadata(boolean hasBreak, 
                                     boolean hasContinue, 
                                     boolean hasReturn,
                                     boolean modifiesCollection, 
                                     boolean requiresOrdering) {
        this.metadata = new LoopMetadata(hasBreak, hasContinue, hasReturn, 
                                         modifiesCollection, requiresOrdering);
        return this;
    }
    
    public LoopModelBuilder metadata(LoopMetadata metadata) {
        this.metadata = metadata;
        return this;
    }
    
    // Operation shortcuts
    public LoopModelBuilder filter(String expression) {
        this.operations.add(new FilterOp(expression));
        return this;
    }
    
    public LoopModelBuilder map(String expression, String targetType) {
        this.operations.add(new MapOp(expression, targetType));
        return this;
    }
    
    public LoopModelBuilder map(String expression, String targetType, String outputVariableName) {
        this.operations.add(new MapOp(expression, targetType, outputVariableName));
        return this;
    }
    
    public LoopModelBuilder map(String expression) {
        this.operations.add(new MapOp(expression, null));
        return this;
    }
    
    public LoopModelBuilder flatMap(String expression) {
        this.operations.add(new FlatMapOp(expression));
        return this;
    }
    
    public LoopModelBuilder peek(String expression) {
        this.operations.add(new PeekOp(expression));
        return this;
    }
    
    public LoopModelBuilder distinct() {
        this.operations.add(new DistinctOp());
        return this;
    }
    
    public LoopModelBuilder sorted() {
        this.operations.add(new SortOp(null));
        return this;
    }
    
    public LoopModelBuilder sorted(String comparatorExpression) {
        this.operations.add(new SortOp(comparatorExpression));
        return this;
    }
    
    public LoopModelBuilder limit(long maxSize) {
        this.operations.add(new LimitOp(maxSize));
        return this;
    }
    
    public LoopModelBuilder skip(long count) {
        this.operations.add(new SkipOp(count));
        return this;
    }
    
    public LoopModelBuilder operation(Operation op) {
        this.operations.add(op);
        return this;
    }
    
    /**
     * Returns whether any intermediate operations (filter, map, etc.) have been added.
     * @return true if operations exist
     */
    public boolean hasOperations() {
        return !this.operations.isEmpty();
    }
    
    // Terminal shortcuts
    public LoopModelBuilder forEach(List<String> bodyStatements, boolean ordered) {
        this.terminal = new ForEachTerminal(bodyStatements, ordered);
        return this;
    }
    
    public LoopModelBuilder forEach(List<String> bodyStatements) {
        return forEach(bodyStatements, false);
    }
    
    public LoopModelBuilder collect(CollectTerminal.CollectorType type, 
                                    String targetVariable) {
        this.terminal = new CollectTerminal(type, targetVariable);
        return this;
    }
    
    public LoopModelBuilder reduce(String identity, 
                                   String accumulator, 
                                   String combiner,
                                   ReduceTerminal.ReduceType type) {
        this.terminal = new ReduceTerminal(identity, accumulator, combiner, type);
        return this;
    }
    
    public LoopModelBuilder count() {
        this.terminal = new CountTerminal();
        return this;
    }
    
    public LoopModelBuilder findFirst() {
        this.terminal = new FindTerminal(true);
        return this;
    }
    
    public LoopModelBuilder findAny() {
        this.terminal = new FindTerminal(false);
        return this;
    }
    
    public LoopModelBuilder anyMatch(String predicate) {
        this.terminal = new MatchTerminal(MatchTerminal.MatchType.ANY_MATCH, predicate);
        return this;
    }
    
    public LoopModelBuilder allMatch(String predicate) {
        this.terminal = new MatchTerminal(MatchTerminal.MatchType.ALL_MATCH, predicate);
        return this;
    }
    
    public LoopModelBuilder noneMatch(String predicate) {
        this.terminal = new MatchTerminal(MatchTerminal.MatchType.NONE_MATCH, predicate);
        return this;
    }
    
    public LoopModelBuilder terminal(TerminalOperation terminal) {
        this.terminal = terminal;
        return this;
    }
    
    // Build
    public LoopModel build() {
        LoopModel model = new LoopModel(source, element, metadata);
        operations.forEach(model::addOperation);
        if (terminal != null) {
            model.withTerminal(terminal);
        }
        return model;
    }
    
    // Validation
    public boolean isValid() {
        return source != null && element != null;
    }
}
