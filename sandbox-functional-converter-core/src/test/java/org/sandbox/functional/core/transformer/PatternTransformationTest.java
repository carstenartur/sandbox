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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sandbox.functional.core.builder.LoopModelBuilder;
import org.sandbox.functional.core.model.*;
import org.sandbox.functional.core.renderer.StringRenderer;
import org.sandbox.functional.core.terminal.*;

/**
 * Tests that verify all supported loop patterns produce correct stream code
 * through the ULR pipeline: LoopModelBuilder → LoopModelTransformer → StringRenderer.
 * 
 * <p>These tests run without OSGi and verify the core transformation logic.
 * Each test corresponds to a pattern that JdtLoopExtractor detects from JDT AST.</p>
 */
@DisplayName("Pattern Transformation Tests (no OSGi)")
class PatternTransformationTest {
    
    private final LoopModelTransformer<String> transformer = 
        new LoopModelTransformer<>(new StringRenderer());
    
    // === FILTER PATTERNS ===
    
    @Test
    @DisplayName("if (cond) continue → filter(x -> !(cond)) + forEach")
    void testFilterViaContinue() {
        // Pattern: for (String s : list) { if (s == null) continue; process(s); }
        // → list.stream().filter(s -> !(s == null)).forEachOrdered(s -> process(s))
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .filter("!(s == null)")
            .forEach(List.of("process(s)"), true)
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .contains("list.stream()")
            .contains(".filter(s -> !(s == null))")
            .contains(".forEachOrdered(s -> process(s))");
    }
    
    @Test
    @DisplayName("if (cond) { body } → filter(x -> cond) + forEachOrdered")
    void testFilterViaIfGuard() {
        // Pattern: for (String s : list) { if (s != null) { System.out.println(s); } }
        // → list.stream().filter(s -> (s != null)).forEachOrdered(s -> System.out.println(s))
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .filter("(s != null)")
            .forEach(List.of("System.out.println(s)"), true)
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .contains(".filter(s -> (s != null))")
            .contains(".forEachOrdered(s -> System.out.println(s))");
    }
    
    // === MAP PATTERNS ===
    
    @Test
    @DisplayName("Type x = expr → map(var -> expr) + forEach(x -> ...)")
    void testMapViaVariableDeclaration() {
        // Pattern: for (String s : list) { String upper = s.toUpperCase(); process(upper); }
        // → list.stream().map(s -> s.toUpperCase()).forEachOrdered(upper -> process(upper))
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .map("s.toUpperCase()", "String")
            .forEach(List.of("process(upper)"), true)
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .contains(".map(s -> s.toUpperCase())")
            .contains(".forEachOrdered(s -> process(upper))");
    }
    
    // === FILTER + MAP COMBINATION ===
    
    @Test
    @DisplayName("filter + map + forEach pipeline")
    void testFilterMapForEach() {
        // Pattern: for (String s : items) { if (s != null) { String u = s.toUpperCase(); print(u); } }
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
            .element("s", "String", false)
            .filter("s != null")
            .map("s.toUpperCase()")
            .forEach(List.of("System.out.println(s)"), true)
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .contains("items.stream()")
            .contains(".filter(s -> s != null)")
            .contains(".map(s -> s.toUpperCase())")
            .contains(".forEachOrdered(s -> System.out.println(s))");
    }
    
    // === COLLECT PATTERNS ===
    
    @Test
    @DisplayName("result.add(item) → stream().collect(Collectors.toList())")
    void testCollectToList() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
            .element("s", "String", false)
            .collect(CollectTerminal.CollectorType.TO_LIST, "result")
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .contains("items.stream()")
            .contains(".toList()");
    }
    
    @Test
    @DisplayName("result.add(transform(item)) → stream().map().collect()")
    void testMapThenCollect() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
            .element("s", "String", false)
            .map("s.toUpperCase()")
            .collect(CollectTerminal.CollectorType.TO_LIST, "result")
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .contains(".map(s -> s.toUpperCase())")
            .contains(".toList()");
    }
    
    @Test
    @DisplayName("set.add(item) → stream().collect(Collectors.toSet())")
    void testCollectToSet() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
            .element("s", "String", false)
            .collect(CollectTerminal.CollectorType.TO_SET, "result")
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .contains(".collect(Collectors.toSet())");
    }
    
    @Test
    @DisplayName("filter + collect pipeline")
    void testFilterThenCollect() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
            .element("s", "String", false)
            .filter("s != null")
            .collect(CollectTerminal.CollectorType.TO_LIST, "result")
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .contains(".filter(s -> s != null)")
            .contains(".toList()");
    }
    
    // === REDUCE PATTERNS ===
    
    @Test
    @DisplayName("sum += x → stream().reduce(0, accumulator)")
    void testReduceSum() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "numbers", "Integer")
            .element("n", "Integer", false)
            .reduce("0", "(sum, n) -> sum + n", null, ReduceTerminal.ReduceType.SUM)
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .contains("numbers.stream()")
            .contains(".reduce(0, (sum, n) -> sum + n)");
    }
    
    @Test
    @DisplayName("product *= x → stream().reduce(1, accumulator)")
    void testReduceProduct() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "numbers", "Integer")
            .element("n", "Integer", false)
            .reduce("1", "(product, n) -> product * n", null, ReduceTerminal.ReduceType.PRODUCT)
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .contains(".reduce(1, (product, n) -> product * n)");
    }
    
    @Test
    @DisplayName("count++ → stream().reduce(0, (count, x) -> count + 1)")
    void testReduceCount() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
            .element("s", "String", false)
            .reduce("0", "(count, s) -> count + 1", null, ReduceTerminal.ReduceType.COUNT)
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .contains(".reduce(0, (count, s) -> count + 1)");
    }
    
    // === MATCH PATTERNS ===
    
    @Test
    @DisplayName("if (cond) return true → stream().anyMatch(x -> cond)")
    void testAnyMatch() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
            .element("s", "String", false)
            .anyMatch("s.isEmpty()")
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .contains("items.stream()")
            .contains(".anyMatch(s -> s.isEmpty())");
    }
    
    @Test
    @DisplayName("if (cond) return false → stream().noneMatch(x -> cond)")
    void testNoneMatch() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
            .element("s", "String", false)
            .noneMatch("s == null")
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .contains(".noneMatch(s -> s == null)");
    }
    
    @Test
    @DisplayName("if (!cond) return false → stream().allMatch(x -> cond)")
    void testAllMatch() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
            .element("s", "String", false)
            .allMatch("s.length() > 0")
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .contains(".allMatch(s -> s.length() > 0)");
    }
    
    // === SIMPLE FOREACH ===
    
    @Test
    @DisplayName("simple forEach without operations")
    void testSimpleForEach() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("item", "String", false)
            .forEach(List.of("System.out.println(item)"))
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .isEqualTo("list.stream().forEach(item -> System.out.println(item))");
    }
    
    @Test
    @DisplayName("forEach with ordered flag (when operations present)")
    void testOrderedForEach() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("item", "String", false)
            .filter("item != null")
            .forEach(List.of("System.out.println(item)"), true)
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .contains(".filter(item -> item != null)")
            .contains(".forEachOrdered(item -> System.out.println(item))");
    }
    
    // === NON-CONVERTIBLE MODELS ===
    
    @Test
    @DisplayName("model with break is not convertible")
    void testBreakNotConvertible() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .metadata(true, false, false, false, true) // hasBreak = true
            .forEach(List.of("process(s)"))
            .build();
        
        assertThat(transformer.canTransform(model)).isFalse();
    }
    
    @Test
    @DisplayName("model with return is not convertible")
    void testReturnNotConvertible() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .metadata(false, false, true, false, true) // hasReturn = true
            .forEach(List.of("process(s)"))
            .build();
        
        assertThat(transformer.canTransform(model)).isFalse();
    }
    
    // === ARRAY SOURCE ===
    
    @Test
    @DisplayName("array source uses Arrays.stream()")
    void testArraySourceTransformation() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.ARRAY, "arr", "String")
            .element("s", "String", false)
            .filter("s != null")
            .forEach(List.of("process(s)"), true)
            .build();
        
        String result = transformer.transform(model);
        
        assertThat(result)
            .startsWith("Arrays.stream(arr)")
            .contains(".filter(s -> s != null)")
            .contains(".forEachOrdered(s -> process(s))");
    }
}
