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
package org.sandbox.functional.core.renderer;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sandbox.functional.core.model.*;
import org.sandbox.functional.core.operation.*;
import org.sandbox.functional.core.terminal.*;

class StringRendererTest {
    
    private final StringRenderer renderer = new StringRenderer();
    
    @Test
    void testRenderSourceCollection() {
        var source = new SourceDescriptor(
            SourceDescriptor.SourceType.COLLECTION, "myList", "String");
        
        assertThat(renderer.renderSource(source)).isEqualTo("myList.stream()");
    }
    
    @Test
    void testRenderSourceArray() {
        var source = new SourceDescriptor(
            SourceDescriptor.SourceType.ARRAY, "arr", "int");
        
        assertThat(renderer.renderSource(source)).isEqualTo("Arrays.stream(arr)");
    }
    
    @Test
    void testRenderSourceIterable() {
        var source = new SourceDescriptor(
            SourceDescriptor.SourceType.ITERABLE, "iterable", "String");
        
        assertThat(renderer.renderSource(source))
            .isEqualTo("StreamSupport.stream(iterable.spliterator(), false)");
    }
    
    @Test
    void testRenderSourceStream() {
        var source = new SourceDescriptor(
            SourceDescriptor.SourceType.STREAM, "myStream", "String");
        
        assertThat(renderer.renderSource(source)).isEqualTo("myStream");
    }
    
    @Test
    void testRenderSourceIntRange() {
        var source = new SourceDescriptor(
            SourceDescriptor.SourceType.INT_RANGE, "10", "int");
        
        assertThat(renderer.renderSource(source)).isEqualTo("IntStream.range(0, 10)");
    }
    
    @Test
    void testRenderSourceExplicitRange() {
        var source = new SourceDescriptor(
            SourceDescriptor.SourceType.EXPLICIT_RANGE, "0,10", "int");
        
        assertThat(renderer.renderSource(source)).isEqualTo("IntStream.range(0, 10)");
    }
    
    @Test
    void testRenderSourceExplicitRangeWithVariables() {
        var source = new SourceDescriptor(
            SourceDescriptor.SourceType.EXPLICIT_RANGE, "start,end", "int");
        
        assertThat(renderer.renderSource(source)).isEqualTo("IntStream.range(start, end)");
    }
    
    @Test
    void testRenderSourceExplicitRangeWithExpression() {
        var source = new SourceDescriptor(
            SourceDescriptor.SourceType.EXPLICIT_RANGE, "i + 1,arr.length", "int");
        
        assertThat(renderer.renderSource(source)).isEqualTo("IntStream.range(i + 1, arr.length)");
    }
    
    @Test
    void testRenderSourceExplicitRangeInvalidNoComma() {
        var source = new SourceDescriptor(
            SourceDescriptor.SourceType.EXPLICIT_RANGE, "10", "int");
        
        assertThatThrownBy(() -> renderer.renderSource(source))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid EXPLICIT_RANGE expression")
            .hasMessageContaining("Expected format 'start,end'");
    }
    
    @Test
    void testRenderSourceExplicitRangeInvalidMultipleCommas() {
        var source = new SourceDescriptor(
            SourceDescriptor.SourceType.EXPLICIT_RANGE, "0,10,20", "int");
        
        assertThatThrownBy(() -> renderer.renderSource(source))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid EXPLICIT_RANGE expression")
            .hasMessageContaining("Expected format 'start,end'");
    }
    
    @Test
    void testRenderSourceExplicitRangeInvalidEmptyStart() {
        var source = new SourceDescriptor(
            SourceDescriptor.SourceType.EXPLICIT_RANGE, ",10", "int");
        
        assertThatThrownBy(() -> renderer.renderSource(source))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid EXPLICIT_RANGE expression")
            .hasMessageContaining("non-empty expressions");
    }
    
    @Test
    void testRenderSourceExplicitRangeInvalidEmptyEnd() {
        var source = new SourceDescriptor(
            SourceDescriptor.SourceType.EXPLICIT_RANGE, "0,", "int");
        
        assertThatThrownBy(() -> renderer.renderSource(source))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid EXPLICIT_RANGE expression")
            .hasMessageContaining("non-empty expressions");
    }
    
    @Test
    void testRenderSourceExplicitRangeInvalidOnlyComma() {
        var source = new SourceDescriptor(
            SourceDescriptor.SourceType.EXPLICIT_RANGE, ",", "int");
        
        assertThatThrownBy(() -> renderer.renderSource(source))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid EXPLICIT_RANGE expression")
            .hasMessageContaining("non-empty expressions");
    }
    
    @Test
    void testRenderSourceExplicitRangeInvalidWhitespaceOnly() {
        var source = new SourceDescriptor(
            SourceDescriptor.SourceType.EXPLICIT_RANGE, " , ", "int");
        
        assertThatThrownBy(() -> renderer.renderSource(source))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid EXPLICIT_RANGE expression")
            .hasMessageContaining("non-empty expressions");
    }
    
    @Test
    void testRenderFilter() {
        String result = renderer.renderFilter("stream", "x > 0", "x");
        assertThat(result).isEqualTo("stream.filter(x -> x > 0)");
    }
    
    @Test
    void testRenderMap() {
        String result = renderer.renderMap("stream", "x.toUpperCase()", "x", "String");
        assertThat(result).isEqualTo("stream.map(x -> x.toUpperCase())");
    }
    
    @Test
    void testRenderFlatMap() {
        String result = renderer.renderFlatMap("stream", "x.stream()", "x");
        assertThat(result).isEqualTo("stream.flatMap(x -> x.stream())");
    }
    
    @Test
    void testRenderPeek() {
        String result = renderer.renderPeek("stream", "System.out.println(x)", "x");
        assertThat(result).isEqualTo("stream.peek(x -> System.out.println(x))");
    }
    
    @Test
    void testRenderDistinct() {
        String result = renderer.renderDistinct("stream");
        assertThat(result).isEqualTo("stream.distinct()");
    }
    
    @Test
    void testRenderSortedWithoutComparator() {
        String result = renderer.renderSorted("stream", null);
        assertThat(result).isEqualTo("stream.sorted()");
    }
    
    @Test
    void testRenderSortedWithComparator() {
        String result = renderer.renderSorted("stream", "Comparator.naturalOrder()");
        assertThat(result).isEqualTo("stream.sorted(Comparator.naturalOrder())");
    }
    
    @Test
    void testRenderLimit() {
        String result = renderer.renderLimit("stream", 5);
        assertThat(result).isEqualTo("stream.limit(5)");
    }
    
    @Test
    void testRenderSkip() {
        String result = renderer.renderSkip("stream", 3);
        assertThat(result).isEqualTo("stream.skip(3)");
    }
    
    @Test
    void testRenderForEach() {
        String result = renderer.renderForEach(
            "stream", List.of("System.out.println(x)"), "x", false);
        assertThat(result).isEqualTo("stream.forEach(x -> System.out.println(x))");
    }
    
    @Test
    void testRenderForEachOrdered() {
        String result = renderer.renderForEach(
            "stream", List.of("print(x)"), "x", true);
        assertThat(result).isEqualTo("stream.forEachOrdered(x -> print(x))");
    }
    
    @Test
    void testRenderForEachMultipleStatements() {
        String result = renderer.renderForEach(
            "stream", List.of("count++", "print(x)"), "x", false);
        assertThat(result).isEqualTo("stream.forEach(x -> count++; print(x))");
    }
    
    @Test
    void testRenderCollectToList() {
        var terminal = new CollectTerminal(CollectTerminal.CollectorType.TO_LIST, "result");
        String result = renderer.renderCollect("stream", terminal, "x");
        assertThat(result).isEqualTo("stream.toList()");
    }
    
    @Test
    void testRenderCollectToSet() {
        var terminal = new CollectTerminal(CollectTerminal.CollectorType.TO_SET, "result");
        String result = renderer.renderCollect("stream", terminal, "x");
        assertThat(result).isEqualTo("stream.collect(Collectors.toSet())");
    }
    
    @Test
    void testRenderReduce() {
        var terminal = new ReduceTerminal("0", "(a, b) -> a + b", null, null);
        String result = renderer.renderReduce("stream", terminal, "x");
        assertThat(result).isEqualTo("stream.reduce(0, (a, b) -> a + b)");
    }
    
    @Test
    void testRenderCount() {
        String result = renderer.renderCount("stream");
        assertThat(result).isEqualTo("stream.count()");
    }
    
    @Test
    void testRenderFindFirst() {
        String result = renderer.renderFind("stream", true);
        assertThat(result).isEqualTo("stream.findFirst()");
    }
    
    @Test
    void testRenderFindAny() {
        String result = renderer.renderFind("stream", false);
        assertThat(result).isEqualTo("stream.findAny()");
    }
    
    @Test
    void testRenderAnyMatch() {
        var terminal = new MatchTerminal(MatchTerminal.MatchType.ANY_MATCH, "x > 0");
        String result = renderer.renderMatch("stream", terminal, "x");
        assertThat(result).isEqualTo("stream.anyMatch(x -> x > 0)");
    }
    
    @Test
    void testRenderAllMatch() {
        var terminal = new MatchTerminal(MatchTerminal.MatchType.ALL_MATCH, "x != null");
        String result = renderer.renderMatch("stream", terminal, "x");
        assertThat(result).isEqualTo("stream.allMatch(x -> x != null)");
    }
    
    @Test
    void testRenderNoneMatch() {
        var terminal = new MatchTerminal(MatchTerminal.MatchType.NONE_MATCH, "x.isEmpty()");
        String result = renderer.renderMatch("stream", terminal, "x");
        assertThat(result).isEqualTo("stream.noneMatch(x -> x.isEmpty())");
    }
    
    @Test
    void testRenderFilterOpWithoutComments() {
        var filterOp = new FilterOp("x > 0");
        String result = renderer.renderFilterOp("stream", filterOp, "x");
        assertThat(result).isEqualTo("stream.filter(x -> x > 0)");
    }
    
    @Test
    void testRenderFilterOpWithComments() {
        var filterOp = new FilterOp("x > 0");
        filterOp.addComment("Only positive values");
        String result = renderer.renderFilterOp("stream", filterOp, "x");
        assertThat(result).contains(".filter(x -> {");
        assertThat(result).contains("// Only positive values");
        assertThat(result).contains("return x > 0;");
        assertThat(result).endsWith("})");
    }
    
    @Test
    void testRenderFilterOpWithMultipleComments() {
        var filterOp = new FilterOp("x != null");
        filterOp.addComment("Remove null values");
        filterOp.addComment("Ensure non-null processing");
        String result = renderer.renderFilterOp("stream", filterOp, "x");
        assertThat(result).contains("// Remove null values");
        assertThat(result).contains("// Ensure non-null processing");
        assertThat(result).contains("return x != null;");
    }
    
    @Test
    void testRenderMapOpWithoutComments() {
        var mapOp = new MapOp("x.toUpperCase()");
        String result = renderer.renderMapOp("stream", mapOp, "x");
        assertThat(result).isEqualTo("stream.map(x -> x.toUpperCase())");
    }
    
    @Test
    void testRenderMapOpWithComments() {
        var mapOp = new MapOp("x.toUpperCase()");
        mapOp.addComment("Convert to uppercase");
        String result = renderer.renderMapOp("stream", mapOp, "x");
        assertThat(result).contains(".map(x -> {");
        assertThat(result).contains("// Convert to uppercase");
        assertThat(result).contains("return x.toUpperCase();");
        assertThat(result).endsWith("})");
    }
    
    @Test
    void testRenderMapOpSideEffect() {
        var mapOp = new MapOp("list.add(x)", null, null, true);
        String result = renderer.renderMapOp("stream", mapOp, "x");
        assertThat(result).contains(".map(x -> {");
        assertThat(result).contains("list.add(x)");
        assertThat(result).contains("return x;");
    }
    
    @Test
    void testRenderMapOpSideEffectTakesPriorityOverComments() {
        var mapOp = new MapOp("list.add(x)", null, null, true);
        mapOp.addComment("Side-effect comment");
        String result = renderer.renderMapOp("stream", mapOp, "x");
        // Side-effect rendering takes priority, comment is not rendered
        assertThat(result).contains("list.add(x)");
        assertThat(result).contains("return x;");
        assertThat(result).doesNotContain("Side-effect comment");
    }
    
    @Test
    void testRenderFilterOpWithMultiLineBlockComment() {
        var filterOp = new FilterOp("x > 0");
        filterOp.addComment("First line\n * Second line\n * Third line");
        String result = renderer.renderFilterOp("stream", filterOp, "x");
        assertThat(result).contains("// First line");
        assertThat(result).contains("// Second line");
        assertThat(result).contains("// Third line");
        assertThat(result).contains("return x > 0;");
        // Should not contain raw block-comment artifacts
        assertThat(result).doesNotContain("* Second");
        assertThat(result).doesNotContain("* Third");
    }
}
