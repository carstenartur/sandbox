/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.functional.core.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.functional.core.operation.FilterOp;
import org.sandbox.functional.core.operation.LimitOp;
import org.sandbox.functional.core.operation.MapOp;
import org.sandbox.functional.core.terminal.CountTerminal;
import org.sandbox.functional.core.terminal.ForEachTerminal;

class NumericIterationTest {
    enum MutationPath { CONSTRUCTOR_ARGUMENT, ACCESSOR_RESULT }

    @ParameterizedTest
    @EnumSource(MutationPath.class)
    void pipelineStructureIsIsolatedFromExternalMutation(MutationPath path) {
        var original = pipeline();
        var iteration = inspect(original);
        int hashBefore = iteration.hashCode();
        var exposed = path == MutationPath.CONSTRUCTOR_ARGUMENT ? original : iteration.pipeline();

        exposed.setSource(new SourceDescriptor(SourceDescriptor.SourceType.ARRAY, "other", "long"));
        exposed.setElement(new ElementDescriptor("other", "long"));
        exposed.setMetadata(new LoopMetadata(true, false, false, false, false, false, false));
        exposed.addOperation(new LimitOp(3));
        exposed.setTerminal(new CountTerminal());

        var snapshot = iteration.pipeline();
        assertEquals(new SourceDescriptor(SourceDescriptor.SourceType.STREAM, "IntStream.range(0, 10)", "int"),
                snapshot.getSource());
        assertEquals(new ElementDescriptor("x", "int"), snapshot.getElement());
        assertEquals(LoopMetadata.safe(), snapshot.getMetadata());
        assertEquals(2, snapshot.getOperations().size());
        var map = assertInstanceOf(MapOp.class, snapshot.getOperations().get(0));
        assertEquals("mapToLong", map.operationType());
        assertEquals("long", map.targetType());
        assertEquals("mapped", map.outputVariableName());
        assertEquals(new FunctionalExpression("x -> (long) x", "x", "int", "long",
                "java.util.function.IntToLongFunction", false, false), map.function());
        assertEquals(List.of("consume(mapped);"),
                assertInstanceOf(ForEachTerminal.class, snapshot.getTerminal()).bodyStatements());
        assertEquals(hashBefore, iteration.hashCode(), "External mutation must not change descriptor identity");
    }

    @ParameterizedTest
    @EnumSource(MutationPath.class)
    void nestedOperationCommentsAndTerminalStatementsAreIsolated(MutationPath path) {
        var original = pipeline();
        var iteration = inspect(original);
        var exposed = path == MutationPath.CONSTRUCTOR_ARGUMENT ? original : iteration.pipeline();

        assertInstanceOf(MapOp.class, exposed.getOperations().get(0)).addComment("changed map");
        assertInstanceOf(FilterOp.class, exposed.getOperations().get(1)).addComment("changed filter");
        assertInstanceOf(ForEachTerminal.class, exposed.getTerminal()).bodyStatements().add("changed();");

        var snapshot = iteration.pipeline();
        assertEquals(List.of("original map"),
                assertInstanceOf(MapOp.class, snapshot.getOperations().get(0)).getComments());
        var filter = assertInstanceOf(FilterOp.class, snapshot.getOperations().get(1));
        assertEquals(List.of("original filter"), filter.getComments());
        assertEquals(new FunctionalExpression("mapped -> mapped > 0", "mapped", "long", "boolean",
                "java.util.function.LongPredicate", false, false), filter.function());
        var terminal = assertInstanceOf(ForEachTerminal.class, snapshot.getTerminal());
        assertEquals(List.of("consume(mapped);"), terminal.bodyStatements());
        assertTrue(terminal.ordered());
    }

    @Test
    void anIncompletePipelineRemainsInspectable() {
        var snapshot = inspect(new LoopModel()).pipeline();
        assertNull(snapshot.getSource());
        assertNull(snapshot.getElement());
        assertNull(snapshot.getMetadata());
        assertNull(snapshot.getTerminal());
        assertTrue(snapshot.getOperations().isEmpty());
    }

    private static NumericIteration inspect(LoopModel model) {
        return new NumericIteration(model, "long", NumericIteration.Execution.SEQUENTIAL,
                NumericIteration.EncounterOrder.ORDERED,
                new NumericIteration.Terminal("count", "long", false, null, null, ".count()"));
    }

    private static LoopModel pipeline() {
        var model = new LoopModel(
                new SourceDescriptor(SourceDescriptor.SourceType.STREAM, "IntStream.range(0, 10)", "int"),
                new ElementDescriptor("x", "int"), LoopMetadata.safe());
        var map = new MapOp("x -> (long) x", "long", "mapped", false,
                new FunctionalExpression("x -> (long) x", "x", "int", "long",
                        "java.util.function.IntToLongFunction", false, false), MapOp.Kind.TO_LONG);
        map.addComment("original map");
        var filter = new FilterOp("mapped -> mapped > 0",
                new FunctionalExpression("mapped -> mapped > 0", "mapped", "long", "boolean",
                        "java.util.function.LongPredicate", false, false));
        filter.addComment("original filter");
        model.addOperation(map).addOperation(filter);
        model.setTerminal(new ForEachTerminal(new ArrayList<>(List.of("consume(mapped);")), true));
        return model;
    }

    @Test void terminalsRetainIntrinsicOptionalAndBinaryShape() {
        var model=new LoopModel(new SourceDescriptor(SourceDescriptor.SourceType.STREAM,"IntStream.empty()","int"),
                new ElementDescriptor("x","int"),null);
        var min=new NumericIteration(model, "int", NumericIteration.Execution.SEQUENTIAL, NumericIteration.EncounterOrder.ORDERED,
                new NumericIteration.Terminal("min", "java.util.OptionalInt", true, null, null, ".min()"));
        assertTrue(min.terminal().optional());
        assertEquals("min",min.terminal().operation());
        assertNull(min.terminal().identity());
        var accumulator=new NumericIteration.Accumulator("Integer::sum",List.of("int","int"),"int","java.util.function.IntBinaryOperator");
        var reduce=new NumericIteration(model,"int",NumericIteration.Execution.SEQUENTIAL,NumericIteration.EncounterOrder.ORDERED,
                new NumericIteration.Terminal("reduce","int",false,"7",accumulator,".reduce(7,Integer::sum)"));
        assertEquals(List.of("int","int"),reduce.terminal().accumulator().inputTypes());
        assertEquals("7",reduce.terminal().identity());
        assertNull(model.getTerminal(),"Inspection must not install a renderable converter terminal");
    }
}
