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
package org.sandbox.functional.core.transformer;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sandbox.functional.core.builder.LoopModelBuilder;
import org.sandbox.functional.core.model.*;
import org.sandbox.functional.core.renderer.StringRenderer;

class LoopModelTransformerTest {
    
    private final LoopModelTransformer<String> transformer = 
        new LoopModelTransformer<>(new StringRenderer());
    
    @Test
    void testSimpleForEach() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("item", "String", false)
            .forEach(List.of("System.out.println(item)"))
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result).isEqualTo(
            "list.stream().forEach(item -> System.out.println(item))");
    }
    
    @Test
    void testFilterMapPipeline() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
            .element("s", "String", false)
            .filter("s != null")
            .map("s.toUpperCase()")
            .forEach(List.of("process(s)"), true)
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .contains(".filter(s -> s != null)")
            .contains(".map(s -> s.toUpperCase())")
            .contains(".forEachOrdered(s -> process(s))");
    }
    
    @Test
    void testComplexPipeline() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "data", "Integer")
            .element("n", "Integer", false)
            .filter("n > 0")
            .map("n * 2")
            .distinct()
            .sorted()
            .limit(10)
            .forEach(List.of("System.out.println(n)"))
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .contains("data.stream()")
            .contains(".filter(n -> n > 0)")
            .contains(".map(n -> n * 2)")
            .contains(".distinct()")
            .contains(".sorted()")
            .contains(".limit(10)")
            .contains(".forEach(n -> System.out.println(n))");
    }
    
    @Test
    void testArraySource() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.ARRAY, "arr", "int")
            .element("x", "int", false)
            .forEach(List.of("sum += x"))
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result).startsWith("Arrays.stream(arr)");
    }
    
    @Test
    void testCollectTerminal() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
            .element("s", "String", false)
            .filter("s != null")
            .collect(org.sandbox.functional.core.terminal.CollectTerminal.CollectorType.TO_LIST, "result")
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result).contains(".filter(s -> s != null)");
        assertThat(result).contains(".toList()");
    }
    
    @Test
    void testCountTerminal() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .count()
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result).isEqualTo("list.stream().count()");
    }
    
    @Test
    void testFindFirstTerminal() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .filter("s.startsWith(\"A\")")
            .findFirst()
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result).contains(".filter(s -> s.startsWith(\"A\"))");
        assertThat(result).contains(".findFirst()");
    }
    
    @Test
    void testCanTransformWithNullModel() {
        assertThat(transformer.canTransform(null)).isFalse();
    }
    
    @Test
    void testCanTransformWithNullSource() {
        LoopModel model = new LoopModel();
        assertThat(transformer.canTransform(model)).isFalse();
    }
    
    @Test
    void testCanTransformWithBreak() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("x", "String", false)
            .metadata(true, false, false, false, false) // hasBreak = true
            .build();
        
        assertThat(transformer.canTransform(model)).isFalse();
    }
    
    @Test
    void testCanTransformValidModel() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("x", "String", false)
            .forEach(List.of("process(x)"))
            .build();
        
        assertThat(transformer.canTransform(model)).isTrue();
    }
    
    @Test
    void testTransformThrowsOnNullModel() {
        assertThatThrownBy(() -> transformer.transform(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("LoopModel and source must not be null");
    }
    
    @Test
    void testTransformThrowsOnNullSource() {
        LoopModel model = new LoopModel();
        
        assertThatThrownBy(() -> transformer.transform(model))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("LoopModel and source must not be null");
    }
    
    @Test
    void testDefaultVariableNameWhenElementIsNull() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .forEach(List.of("System.out.println(x)"))
            .build();
        
        String result = transformer.transform(model);
        
        // Should use default variable name "x" when element is null
        assertThat(result).contains("x -> ");
    }
    
    @Test
    void testPeekOperation() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .peek("logger.debug(s)")
            .forEach(List.of("process(s)"))
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result).contains(".peek(s -> logger.debug(s))");
    }
    
    @Test
    void testSkipOperation() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .skip(5)
            .forEach(List.of("process(s)"))
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result).contains(".skip(5)");
    }
}
