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
package org.sandbox.functional.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.functional.core.operation.FilterOp;
import org.sandbox.functional.core.operation.MapOp;
import org.sandbox.functional.core.terminal.ForEachTerminal;

/**
 * Tests for {@link LoopModel} pipeline support.
 */
class LoopModelPipelineTest {
    
    @Test
    void testBuildCompleteModel() {
        LoopModel model = new LoopModel()
            .setSource(new SourceDescriptor(
                SourceDescriptor.SourceType.COLLECTION, "list", "String"))
            .setElement(new ElementDescriptor("item", "String", false))
            .addOperation(new FilterOp("item != null"))
            .addOperation(new MapOp("item.toUpperCase()", "String"))
            .withTerminal(new ForEachTerminal(
                List.of("System.out.println(item)"), false));
        
        assertThat(model.getOperations()).hasSize(2);
        assertThat(model.getOperations().get(0)).isInstanceOf(FilterOp.class);
        assertThat(model.getOperations().get(1)).isInstanceOf(MapOp.class);
        assertThat(model.getTerminal()).isInstanceOf(ForEachTerminal.class);
    }
    
    @Test
    void testEmptyPipeline() {
        LoopModel model = new LoopModel();
        assertThat(model.getOperations()).isEmpty();
        assertThat(model.getTerminal()).isNull();
    }
    
    @Test
    void testFluentApiSourceReturnsThis() {
        LoopModel model = new LoopModel();
        SourceDescriptor source = new SourceDescriptor(
            SourceDescriptor.SourceType.ARRAY, "arr", "int");
        LoopModel result = model.setSource(source);
        assertThat(result).isSameAs(model);
        assertThat(model.getSource()).isEqualTo(source);
    }
    
    @Test
    void testFluentApiElementReturnsThis() {
        LoopModel model = new LoopModel();
        ElementDescriptor element = new ElementDescriptor("x", "int", false);
        LoopModel result = model.setElement(element);
        assertThat(result).isSameAs(model);
        assertThat(model.getElement()).isEqualTo(element);
    }
    
    @Test
    void testFluentApiMetadataReturnsThis() {
        LoopModel model = new LoopModel();
        LoopMetadata metadata = new LoopMetadata(false, false, false, false, true);
        LoopModel result = model.setMetadata(metadata);
        assertThat(result).isSameAs(model);
        assertThat(model.getMetadata()).isEqualTo(metadata);
    }
    
    @Test
    void testFluentApiAddOperationReturnsThis() {
        LoopModel model = new LoopModel();
        FilterOp filter = new FilterOp("x > 0");
        LoopModel result = model.addOperation(filter);
        assertThat(result).isSameAs(model);
        assertThat(model.getOperations()).contains(filter);
    }
    
    @Test
    void testFluentApiWithTerminalReturnsThis() {
        LoopModel model = new LoopModel();
        ForEachTerminal terminal = new ForEachTerminal(List.of("print(x)"));
        LoopModel result = model.withTerminal(terminal);
        assertThat(result).isSameAs(model);
        assertThat(model.getTerminal()).isEqualTo(terminal);
    }
    
    @Test
    void testAddMultipleOperations() {
        LoopModel model = new LoopModel();
        model.addOperation(new FilterOp("x > 0"));
        model.addOperation(new MapOp("x.toString()"));
        assertThat(model.getOperations()).hasSize(2);
    }
    
    @Test
    void testSetTerminal() {
        LoopModel model = new LoopModel();
        ForEachTerminal terminal = new ForEachTerminal(List.of("print(x)"));
        model.setTerminal(terminal);
        assertThat(model.getTerminal()).isEqualTo(terminal);
    }
    
    @Test
    void testToStringIncludesOperationsAndTerminal() {
        LoopModel model = new LoopModel()
            .addOperation(new FilterOp("x > 0"))
            .withTerminal(new ForEachTerminal(List.of("print(x)")));
        
        String str = model.toString();
        assertThat(str).contains("operations=");
        assertThat(str).contains("terminal=");
    }
    
    @Test
    void testEqualsIncludesOperationsAndTerminal() {
        LoopModel model1 = new LoopModel()
            .addOperation(new FilterOp("x > 0"))
            .withTerminal(new ForEachTerminal(List.of("print(x)")));
        
        LoopModel model2 = new LoopModel()
            .addOperation(new FilterOp("x > 0"))
            .withTerminal(new ForEachTerminal(List.of("print(x)")));
        
        assertThat(model1).isEqualTo(model2);
    }
    
    @Test
    void testHashCodeIncludesOperationsAndTerminal() {
        LoopModel model1 = new LoopModel()
            .addOperation(new FilterOp("x > 0"))
            .withTerminal(new ForEachTerminal(List.of("print(x)")));
        
        LoopModel model2 = new LoopModel()
            .addOperation(new FilterOp("x > 0"))
            .withTerminal(new ForEachTerminal(List.of("print(x)")));
        
        assertThat(model1.hashCode()).isEqualTo(model2.hashCode());
    }
    
    @Test
    void testGetOperationsReturnsUnmodifiableList() {
        LoopModel model = new LoopModel();
        model.addOperation(new FilterOp("x > 0"));
        
        List<org.sandbox.functional.core.operation.Operation> ops = model.getOperations();
        assertThat(ops).hasSize(1);
        
        // Verify that the returned list is unmodifiable
        org.junit.jupiter.api.Assertions.assertThrows(
            UnsupportedOperationException.class,
            () -> ops.add(new MapOp("x.toString()"))
        );
    }
    
    @Test
    void testAddOperationRejectsNull() {
        LoopModel model = new LoopModel();
        
        org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> model.addOperation(null)
        );
    }
    
    @Test
    void testWithTerminalRejectsNull() {
        LoopModel model = new LoopModel();
        
        org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> model.withTerminal(null)
        );
    }
    
    @Test
    void testAddOperationsClearedByCreatingNewModel() {
        LoopModel model = new LoopModel();
        model.addOperation(new FilterOp("x > 0"));
        assertThat(model.getOperations()).hasSize(1);
        
        // Create new model to test operations are empty
        LoopModel newModel = new LoopModel();
        assertThat(newModel.getOperations()).isEmpty();
    }
    
    @Test
    void testOperationsAreIndependent() {
        LoopModel model = new LoopModel();
        FilterOp filter = new FilterOp("x > 0");
        model.addOperation(filter);
        assertThat(model.getOperations()).hasSize(1);
        
        // Adding to model should not affect other models
        LoopModel model2 = new LoopModel();
        model2.addOperation(new MapOp("x.toString()"));
        assertThat(model.getOperations()).hasSize(1);
        assertThat(model2.getOperations()).hasSize(1);
    }
}
