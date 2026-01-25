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
import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.terminal.CollectTerminal;

/**
 * Tests for {@link StreamCodeBuilder}.
 */
class StreamCodeBuilderTest {
    
    @Test
    void testSimpleForEach() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("item", "String", false)
            .forEach(List.of("System.out.println(item)"))
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).isEqualTo(
            "list.stream().forEach(item -> System.out.println(item))");
    }
    
    @Test
    void testFilterMapForEach() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
            .element("s", "String", false)
            .filter("s != null")
            .map("s.toUpperCase()")
            .forEach(List.of("System.out.println(s)"), true)
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains(".filter(s -> s != null)");
        assertThat(code).contains(".map(s -> s.toUpperCase())");
        assertThat(code).contains(".forEachOrdered(");
    }
    
    @Test
    void testArraySource() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.ARRAY, "arr", "int")
            .element("n", "int", false)
            .forEach(List.of("System.out.println(n)"))
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).startsWith("Arrays.stream(arr)");
    }
    
    @Test
    void testRequiredImports() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.ARRAY, "arr", "int")
            .element("n", "int", false)
            .collect(CollectTerminal.CollectorType.TO_SET, "result")
            .build();
        
        var imports = new StreamCodeBuilder(model).getRequiredImports();
        
        assertThat(imports).contains("java.util.Arrays");
        assertThat(imports).contains("java.util.stream.Collectors");
    }
    
    @Test
    void testIterableSource() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.ITERABLE, "iterable", "String")
            .element("s", "String", false)
            .forEach(List.of("process(s)"))
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).startsWith("StreamSupport.stream(iterable.spliterator(), false)");
    }
    
    @Test
    void testIterableSourceImport() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.ITERABLE, "iterable", "String")
            .element("s", "String", false)
            .forEach(List.of("process(s)"))
            .build();
        
        var imports = new StreamCodeBuilder(model).getRequiredImports();
        
        assertThat(imports).contains("java.util.stream.StreamSupport");
    }
    
    @Test
    void testIntRangeSource() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.INT_RANGE, "10", "int")
            .element("i", "int", false)
            .forEach(List.of("System.out.println(i)"))
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).startsWith("IntStream.range(0, 10)");
    }
    
    @Test
    void testIntRangeSourceImport() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.INT_RANGE, "10", "int")
            .element("i", "int", false)
            .forEach(List.of("System.out.println(i)"))
            .build();
        
        var imports = new StreamCodeBuilder(model).getRequiredImports();
        
        assertThat(imports).contains("java.util.stream.IntStream");
    }
    
    @Test
    void testExplicitRangeSource() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.EXPLICIT_RANGE, "0,10", "int")
            .element("i", "int", false)
            .forEach(List.of("System.out.println(i)"))
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).startsWith("IntStream.range(0, 10)");
    }
    
    @Test
    void testExplicitRangeSourceWithVariables() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.EXPLICIT_RANGE, "start,end", "int")
            .element("i", "int", false)
            .forEach(List.of("System.out.println(i)"))
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).startsWith("IntStream.range(start, end)");
    }
    
    @Test
    void testExplicitRangeSourceImport() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.EXPLICIT_RANGE, "0,n", "int")
            .element("i", "int", false)
            .forEach(List.of("sum += i"))
            .build();
        
        var imports = new StreamCodeBuilder(model).getRequiredImports();
        
        assertThat(imports).contains("java.util.stream.IntStream");
    }
    
    @Test
    void testStreamSource() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.STREAM, "myStream", "String")
            .element("s", "String", false)
            .forEach(List.of("System.out.println(s)"))
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).startsWith("myStream");
        assertThat(code).doesNotContain(".stream()");
    }
    
    @Test
    void testDistinctOperation() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .distinct()
            .forEach(List.of("System.out.println(s)"))
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains(".distinct()");
    }
    
    @Test
    void testSortedOperation() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .sorted()
            .forEach(List.of("System.out.println(s)"))
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains(".sorted()");
    }
    
    @Test
    void testSortedWithComparator() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .sorted("Comparator.naturalOrder()")
            .forEach(List.of("System.out.println(s)"))
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains(".sorted(Comparator.naturalOrder())");
    }
    
    @Test
    void testLimitOperation() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .limit(5)
            .forEach(List.of("System.out.println(s)"))
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains(".limit(5)");
    }
    
    @Test
    void testSkipOperation() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .skip(3)
            .forEach(List.of("System.out.println(s)"))
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains(".skip(3)");
    }
    
    @Test
    void testPeekOperation() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .peek("logger.debug(s)")
            .forEach(List.of("System.out.println(s)"))
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains(".peek(s -> logger.debug(s))");
    }
    
    @Test
    void testFlatMapOperation() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "lists", "List<String>")
            .element("list", "List<String>", false)
            .flatMap("list.stream()")
            .forEach(List.of("System.out.println(list)"))
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains(".flatMap(list -> list.stream())");
    }
    
    @Test
    void testCollectTerminal() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .collect(CollectTerminal.CollectorType.TO_LIST, "result")
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains(".toList()");
    }
    
    @Test
    void testCollectToSet() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .collect(CollectTerminal.CollectorType.TO_SET, "result")
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains(".collect(Collectors.toSet())");
    }
    
    @Test
    void testCountTerminal() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .count()
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains(".count()");
    }
    
    @Test
    void testFindFirstTerminal() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .findFirst()
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains(".findFirst()");
    }
    
    @Test
    void testFindAnyTerminal() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .findAny()
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains(".findAny()");
    }
    
    @Test
    void testAnyMatchTerminal() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .anyMatch("s.length() > 5")
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains(".anyMatch(s -> s.length() > 5)");
    }
    
    @Test
    void testAllMatchTerminal() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .allMatch("s != null")
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains(".allMatch(s -> s != null)");
    }
    
    @Test
    void testNoneMatchTerminal() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .noneMatch("s.isEmpty()")
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains(".noneMatch(s -> s.isEmpty())");
    }
    
    @Test
    void testReduceTerminal() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "numbers", "Integer")
            .element("n", "Integer", false)
            .reduce("0", "(a, b) -> a + b", null, null)
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains(".reduce(0, (a, b) -> a + b)");
    }
    
    @Test
    void testCanBuildWithNullModel() {
        StreamCodeBuilder builder = new StreamCodeBuilder(null);
        
        assertThat(builder.canBuild()).isFalse();
    }
    
    @Test
    void testCanBuildWithNullSource() {
        LoopModel model = new LoopModel();
        StreamCodeBuilder builder = new StreamCodeBuilder(model);
        
        assertThat(builder.canBuild()).isFalse();
    }
    
    @Test
    void testCanBuildWithBreak() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .metadata(true, false, false, false, false)
            .build();
        
        StreamCodeBuilder builder = new StreamCodeBuilder(model);
        
        assertThat(builder.canBuild()).isFalse();
    }
    
    @Test
    void testCanBuildWithValidModel() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .forEach(List.of("System.out.println(s)"))
            .build();
        
        StreamCodeBuilder builder = new StreamCodeBuilder(model);
        
        assertThat(builder.canBuild()).isTrue();
    }
    
    @Test
    void testMultipleStatementsInForEach() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "list", "String")
            .element("s", "String", false)
            .forEach(List.of("count++", "System.out.println(s)"))
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code).contains("count++; System.out.println(s)");
    }
    
    @Test
    void testComplexPipeline() {
        LoopModel model = new LoopModelBuilder()
            .source(SourceDescriptor.SourceType.COLLECTION, "items", "String")
            .element("s", "String", false)
            .filter("s != null")
            .map("s.trim()")
            .filter("!s.isEmpty()")
            .distinct()
            .sorted()
            .limit(10)
            .forEach(List.of("System.out.println(s)"))
            .build();
        
        String code = new StreamCodeBuilder(model).build();
        
        assertThat(code)
            .contains("items.stream()")
            .contains(".filter(s -> s != null)")
            .contains(".map(s -> s.trim())")
            .contains(".filter(s -> !s.isEmpty())")
            .contains(".distinct()")
            .contains(".sorted()")
            .contains(".limit(10)")
            .contains(".forEach(s -> System.out.println(s))");
    }
}
