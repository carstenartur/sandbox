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
package org.sandbox.functional.core.operation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sandbox.functional.core.builder.LoopModelBuilder;
import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.model.SourceDescriptor.SourceType;
import org.sandbox.functional.core.renderer.StringRenderer;
import org.sandbox.functional.core.transformer.LoopModelTransformer;

/**
 * Tests for comment preservation in loop transformations.
 * This demonstrates Phase 10 functionality.
 */
class CommentPreservationTest {
    
    @Test
    void testFilterWithoutComments() {
        // Build a simple loop with a filter operation
        FilterOp filter = new FilterOp("x > 0");
        
        LoopModel model = new LoopModelBuilder()
            .source(SourceType.COLLECTION, "list", "Integer")
            .element("x", "Integer", false)
            .metadata(false, false, false, false, true)
            .operation(filter)
            .forEach(java.util.List.of("System.out.println(x)"), false)
            .build();
        
        // Transform using StringRenderer (for easy testing)
        StringRenderer renderer = new StringRenderer();
        LoopModelTransformer<String> transformer = new LoopModelTransformer<>(renderer);
        String result = transformer.transform(model);
        
        // Should generate compact lambda without comments
        assertThat(result).contains("filter(x -> x > 0)");
        assertThat(result).doesNotContain("/*");
    }
    
    @Test
    void testFilterWithComments() {
        // Build a loop with a filter operation that has comments
        FilterOp filter = new FilterOp("x > 0");
        filter.addComment("Filter out negative values");
        filter.addComment("Only process positive integers");
        
        LoopModel model = new LoopModelBuilder()
            .source(SourceType.COLLECTION, "list", "Integer")
            .element("x", "Integer", false)
            .metadata(false, false, false, false, true)
            .operation(filter)
            .forEach(java.util.List.of("System.out.println(x)"), false)
            .build();
        
        // Verify the operation has comments
        assertThat(filter.hasComments()).isTrue();
        assertThat(filter.getComments()).hasSize(2);
        
        // StringRenderer now generates block lambda with comments
        StringRenderer renderer = new StringRenderer();
        LoopModelTransformer<String> transformer = new LoopModelTransformer<>(renderer);
        String result = transformer.transform(model);
        
        // Should use block lambda with comments
        assertThat(result).contains("// Filter out negative values");
        assertThat(result).contains("// Only process positive integers");
        assertThat(result).contains("return x > 0;");
    }
    
    @Test
    void testMapWithComments() {
        // Build a loop with a map operation that has comments
        MapOp map = new MapOp("x.toUpperCase()");
        map.addComment("Convert to uppercase");
        map.addComment("For display purposes");
        
        LoopModel model = new LoopModelBuilder()
            .source(SourceType.COLLECTION, "list", "String")
            .element("x", "String", false)
            .metadata(false, false, false, false, true)
            .operation(map)
            .forEach(java.util.List.of("System.out.println(x)"), false)
            .build();
        
        // Verify the operation has comments
        assertThat(map.hasComments()).isTrue();
        assertThat(map.getComments()).hasSize(2);
        assertThat(map.getComments()).containsExactly("Convert to uppercase", "For display purposes");
    }
    
    @Test
    void testMultipleOperationsWithComments() {
        // Build a pipeline with multiple operations, some with comments
        FilterOp filter = new FilterOp("x != null");
        filter.addComment("Remove null values");
        
        MapOp map = new MapOp("x.length()");
        // Map has no comments
        
        FilterOp filter2 = new FilterOp("len > 3");
        filter2.addComment("Only process long strings");
        
        LoopModel model = new LoopModelBuilder()
            .source(SourceType.COLLECTION, "list", "String")
            .element("x", "String", false)
            .metadata(false, false, false, false, true)
            .operation(filter)
            .operation(map)
            .operation(filter2)
            .forEach(java.util.List.of("System.out.println(len)"), false)
            .build();
        
        // Verify comment distribution
        assertThat(filter.hasComments()).isTrue();
        assertThat(map.hasComments()).isFalse();
        assertThat(filter2.hasComments()).isTrue();
    }
}
