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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sandbox.functional.core.model.ElementDescriptor;
import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.operation.FilterOp;
import org.sandbox.functional.core.operation.MapOp;
import org.sandbox.functional.core.terminal.ForEachTerminal;

/**
 * Tests for {@link LoopModelBuilder}.
 */
class LoopModelBuilderTest {
    
    @Test
    void testBuildSimpleForEach() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("item", "String", false)
            .forEach(List.of("System.out.println(item)"))
            .build();
        
        assertThat(model.getSource().expression()).isEqualTo("list");
        assertThat(model.getElement().variableName()).isEqualTo("item");
        assertThat(model.getTerminal()).isInstanceOf(ForEachTerminal.class);
    }
    
    @Test
    void testBuildFilterMapPipeline() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
            .element("s", "String", false)
            .filter("s != null")
            .map("s.toUpperCase()")
            .forEach(List.of("System.out.println(s)"), true)
            .build();
        
        assertThat(model.getOperations()).hasSize(2);
        assertThat(model.getOperations().get(0)).isInstanceOf(FilterOp.class);
        assertThat(model.getOperations().get(1)).isInstanceOf(MapOp.class);
    }
    
    @Test
    void testValidation() {
        LoopModelBuilder builder = new LoopModelBuilder();
        assertThat(builder.isValid()).isFalse();
        
        builder.source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
               .element("x", "String", false);
        assertThat(builder.isValid()).isTrue();
    }
    
    @Test
    void testConvertibilityCheck() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("x", "String", false)
            .metadata(true, false, false, false, false) // hasBreak = true
            .build();
        
        assertThat(model.isConvertible()).isFalse();
    }
    
    @Test
    void testConvertibilityCheckWithReturn() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("x", "String", false)
            .metadata(false, false, true, false, false) // hasReturn = true
            .build();
        
        assertThat(model.isConvertible()).isFalse();
    }
    
    @Test
    void testConvertibilityCheckWithoutProblems() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("x", "String", false)
            .metadata(false, false, false, false, true)
            .build();
        
        assertThat(model.isConvertible()).isTrue();
    }
    
    @Test
    void testConvertibilityCheckNoMetadata() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("x", "String", false)
            .build();
        
        assertThat(model.isConvertible()).isTrue();
    }
    
    @Test
    void testSourceWithDescriptor() {
        SourceDescriptor source = new SourceDescriptor(
            SourceDescriptor.SourceType.ARRAY, "arr", "int");
        
        LoopModel model = new LoopModelBuilder()
            .source(source)
            .element("n", "int", false)
            .forEach(List.of("sum += n"))
            .build();
        
        assertThat(model.getSource()).isEqualTo(source);
    }
    
    @Test
    void testElementWithDescriptor() {
        ElementDescriptor element = new ElementDescriptor("item", "String", true);
        
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element(element)
            .forEach(List.of("process(item)"))
            .build();
        
        assertThat(model.getElement()).isEqualTo(element);
        assertThat(model.getElement().isFinal()).isTrue();
    }
    
    @Test
    void testAllOperationTypes() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
            .element("s", "String", false)
            .filter("s != null")
            .map("s.toUpperCase()", "String")
            .distinct()
            .sorted()
            .limit(10)
            .skip(2)
            .peek("System.out.println(s)")
            .build();
        
        assertThat(model.getOperations()).hasSize(7);
    }
    
    @Test
    void testAllTerminalTypes() {
        // forEach
        LoopModel forEachModel = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("item", "String", false)
            .forEach(List.of("System.out.println(item)"), false)
            .build();
        assertThat(forEachModel.getTerminal()).isInstanceOf(ForEachTerminal.class);
        
        // count
        LoopModel countModel = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("item", "String", false)
            .count()
            .build();
        assertThat(countModel.getTerminal().operationType()).isEqualTo("count");
        
        // findFirst
        LoopModel findFirstModel = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("item", "String", false)
            .findFirst()
            .build();
        assertThat(findFirstModel.getTerminal().operationType()).isEqualTo("findFirst");
        
        // anyMatch
        LoopModel anyMatchModel = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("item", "String", false)
            .anyMatch("item.length() > 5")
            .build();
        assertThat(anyMatchModel.getTerminal().operationType()).isEqualTo("anyMatch");
    }
    
    @Test
    void testMapWithoutTargetType() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
            .element("s", "String", false)
            .map("s.length()")
            .build();
        
        assertThat(model.getOperations()).hasSize(1);
        MapOp mapOp = (MapOp) model.getOperations().get(0);
        assertThat(mapOp.targetType()).isNull();
    }
    
    @Test
    void testSortedWithComparator() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
            .element("s", "String", false)
            .sorted("Comparator.naturalOrder()")
            .build();
        
        assertThat(model.getOperations()).hasSize(1);
    }
}
