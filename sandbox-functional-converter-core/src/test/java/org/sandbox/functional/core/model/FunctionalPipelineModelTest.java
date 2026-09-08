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
import org.sandbox.functional.core.operation.PeekOp;
import org.sandbox.functional.core.operation.StreamTypeConversionOp;
import org.sandbox.functional.core.renderer.StringRenderer;
import org.sandbox.functional.core.terminal.ForEachTerminal;
import org.sandbox.functional.core.transformer.LoopModelTransformer;

class FunctionalPipelineModelTest {

    @Test
    void retainsPrimitiveTransitionsPeekScopesAndOriginalArrayFactory() {
        var action = new FunctionalExpression("v -> { if (v < 0) return; log(v); }", "v",
                "int", "void", "IntConsumer", true, false);
        var mapper = new FunctionalExpression("String::length", null,
                "String", "int", "ToIntFunction<String>", true, false);
        var consumer = new FunctionalExpression("value -> consume(value)", "value",
                "java.lang.Double", "void", "Consumer<Double>", false, false);
        var model = new LoopModelBuilder()
                .source(new SourceDescriptor(SourceDescriptor.SourceType.ARRAY, "values", "String", "java.util.Arrays.<String>stream(values)"))
                .element("element", "String", false)
                .operation(new MapOp("String::length", "int", null, false, mapper, MapOp.Kind.TO_INT))
                .operation(new PeekOp("log(v)", action))
                .operation(new StreamTypeConversionOp(StreamTypeConversionOp.Kind.AS_DOUBLE, "int"))
                .operation(new StreamTypeConversionOp(StreamTypeConversionOp.Kind.BOXED, "double"))
                .terminal(new ForEachTerminal(List.of("consume(value)"), true, consumer)).build();
        assertEquals("java.util.Arrays.<String>stream(values).mapToInt((ToIntFunction<String>) (String::length))"
                + ".peek((IntConsumer) (v -> { if (v < 0) return; log(v); })).asDoubleStream().boxed()"
                + ".forEachOrdered((Consumer<Double>) (value -> consume(value)))",
                new LoopModelTransformer<>(new StringRenderer()).transform(model));
    }

    @Test
    void rejectsInvalidPrimitiveMappingAndPeekContracts() {
        var mapper = new FunctionalExpression("String::length", null, "String", "int", "ToIntFunction<String>", true, false);
        assertThrows(IllegalArgumentException.class, () -> new MapOp("String::length", "int", null, false, mapper, MapOp.Kind.TO_LONG));
        assertThrows(IllegalArgumentException.class, () -> new MapOp("value", "int", null, false, null, MapOp.Kind.TO_INT));
        assertThrows(IllegalArgumentException.class, () -> new PeekOp("String::length", mapper));
        assertThrows(IllegalArgumentException.class, () -> new StreamTypeConversionOp(StreamTypeConversionOp.Kind.AS_LONG, "double"));
        assertThrows(IllegalArgumentException.class, () -> new StreamTypeConversionOp(StreamTypeConversionOp.Kind.BOXED, "String"));
    }

    @Test
    void mapIdentityDistinguishesPrimitiveTransitions() {
        var mapper = new FunctionalExpression("v -> v", "v", "int", "long", "IntToLongFunction", false, false);
        var plain = new MapOp("v", "long", null, false, mapper);
        var widening = new MapOp("v", "long", null, false, mapper, MapOp.Kind.TO_LONG);
        assertNotEquals(plain, widening);
        assertEquals("mapToLong", widening.operationType());
        assertEquals("java.lang.Integer", new StreamTypeConversionOp(StreamTypeConversionOp.Kind.BOXED, "int").outputType());
        assertEquals("peek", new PeekOp("log(v)").operationType());
    }

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
