/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.functional.core.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class NumericIterationTest {
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
