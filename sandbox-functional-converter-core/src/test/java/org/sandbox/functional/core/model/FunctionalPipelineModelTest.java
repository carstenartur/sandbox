/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.functional.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.functional.core.builder.LoopModelBuilder;
import org.sandbox.functional.core.operation.FilterOp;
import org.sandbox.functional.core.operation.MapOp;
import org.sandbox.functional.core.renderer.StringRenderer;
import org.sandbox.functional.core.terminal.ForEachTerminal;
import org.sandbox.functional.core.transformer.LoopModelTransformer;

class FunctionalPipelineModelTest {

    @Test
    void rendersCompleteFunctionsWithIndependentParametersAndTargetTypes() {
        var predicate = new FunctionalExpression("text -> { return !text.isEmpty(); }", "text",
                "String", "boolean", "Predicate<String>", true, false);
        var mapper = new FunctionalExpression("String::length", null,
                "String", "Number", "Function<String, Number>", true, false);
        var consumer = new FunctionalExpression("number -> consume(number)", "number",
                "Number", "void", "Consumer<Number>", false, false);
        LoopModel model = new LoopModelBuilder()
                .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
                .element("element", "String", false)
                .operation(new FilterOp("!text.isEmpty()", predicate))
                .operation(new MapOp("String::length", "Number", null, false, mapper))
                .terminal(new ForEachTerminal(List.of("consume(number)"), true, consumer)).build();

        assertEquals("items.stream().filter((Predicate<String>) (text -> { return !text.isEmpty(); }))"
                + ".map((Function<String, Number>) (String::length))"
                + ".forEachOrdered((Consumer<Number>) (number -> consume(number)))",
                new LoopModelTransformer<>(new StringRenderer()).transform(model));
    }

    @Test
    void rejectsInconsistentMapResultTypes() {
        var mapper = new FunctionalExpression("String::length", null,
                "String", "Integer", "Function<String, Integer>", true, false);
        assertThrows(IllegalArgumentException.class,
                () -> new MapOp("String::length", "Number", null, false, mapper));
    }

    @Test
    void operationEqualityIncludesResolvedFunctionTypesAndBoundaries() {
        var narrow = new FunctionalExpression("item -> select(item)", "item",
                "String", "boolean", "Predicate<String>", false, false);
        var wide = new FunctionalExpression("item -> select(item)", "item",
                "Object", "boolean", "Predicate<Object>", false, false);
        var scoped = new FunctionalExpression("item -> select(item)", "item",
                "String", "boolean", "Predicate<String>", true, false);
        assertNotEquals(new FilterOp("select(item)", narrow), new FilterOp("select(item)", wide));
        assertNotEquals(new FilterOp("select(item)", narrow), new FilterOp("select(item)", scoped));
    }
}
