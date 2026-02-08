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

/**
 * Tests for {@link Operation} implementations.
 */
class OperationTest {
    
    @Test
    void testFilterOp() {
        FilterOp filter = new FilterOp("item != null");
        assertThat(filter.expression()).isEqualTo("item != null");
        assertThat(filter.operationType()).isEqualTo("filter");
    }
    
    @Test
    void testMapOp() {
        MapOp map = new MapOp("item.toUpperCase()", null);
        assertThat(map.expression()).isEqualTo("item.toUpperCase()");
        assertThat(map.operationType()).isEqualTo("map");
    }
    
    @Test
    void testMapOpWithTargetType() {
        MapOp map = new MapOp("item.toUpperCase()", "String");
        assertThat(map.expression()).isEqualTo("item.toUpperCase()");
        assertThat(map.targetType()).isEqualTo("String");
        assertThat(map.operationType()).isEqualTo("map");
    }
    
    @Test
    void testMapOpSingleArg() {
        MapOp map = new MapOp("item.length()");
        assertThat(map.expression()).isEqualTo("item.length()");
        assertThat(map.targetType()).isNull();
        assertThat(map.operationType()).isEqualTo("map");
    }
    
    @Test
    void testFlatMapOp() {
        FlatMapOp flatMap = new FlatMapOp("item.stream()");
        assertThat(flatMap.expression()).isEqualTo("item.stream()");
        assertThat(flatMap.operationType()).isEqualTo("flatMap");
    }
    
    @Test
    void testPeekOp() {
        PeekOp peek = new PeekOp("System.out.println(item)");
        assertThat(peek.expression()).isEqualTo("System.out.println(item)");
        assertThat(peek.operationType()).isEqualTo("peek");
    }
    
    @Test
    void testDistinctOp() {
        DistinctOp distinct = new DistinctOp();
        assertThat(distinct.expression()).isNull();
        assertThat(distinct.operationType()).isEqualTo("distinct");
    }
    
    @Test
    void testSortOpNatural() {
        SortOp sort = new SortOp();
        assertThat(sort.expression()).isNull();
        assertThat(sort.operationType()).isEqualTo("sorted");
    }
    
    @Test
    void testSortOpWithComparator() {
        SortOp sort = new SortOp("Comparator.reverseOrder()");
        assertThat(sort.expression()).isEqualTo("Comparator.reverseOrder()");
        assertThat(sort.comparatorExpression()).isEqualTo("Comparator.reverseOrder()");
        assertThat(sort.operationType()).isEqualTo("sorted");
    }
    
    @Test
    void testLimitOp() {
        LimitOp limit = new LimitOp(10);
        assertThat(limit.maxSize()).isEqualTo(10);
        assertThat(limit.expression()).isEqualTo("10");
        assertThat(limit.operationType()).isEqualTo("limit");
    }
    
    @Test
    void testSkipOp() {
        SkipOp skip = new SkipOp(5);
        assertThat(skip.count()).isEqualTo(5);
        assertThat(skip.expression()).isEqualTo("5");
        assertThat(skip.operationType()).isEqualTo("skip");
    }
    
    @Test
    void testFilterOpComments() {
        FilterOp filter = new FilterOp("item != null");
        
        // Initially no comments
        assertThat(filter.hasComments()).isFalse();
        assertThat(filter.getComments()).isEmpty();
        
        // Add a single comment
        filter.addComment("Check for null values");
        assertThat(filter.hasComments()).isTrue();
        assertThat(filter.getComments()).hasSize(1);
        assertThat(filter.getComments()).containsExactly("Check for null values");
        
        // Add another comment
        filter.addComment("This is important");
        assertThat(filter.getComments()).hasSize(2);
        assertThat(filter.getComments()).containsExactly("Check for null values", "This is important");
    }
    
    @Test
    void testFilterOpCommentsIgnoreNull() {
        FilterOp filter = new FilterOp("item != null");
        
        filter.addComment(null);
        assertThat(filter.hasComments()).isFalse();
        
        filter.addComment("");
        assertThat(filter.hasComments()).isFalse();
    }
    
    @Test
    void testFilterOpAddCommentsList() {
        FilterOp filter = new FilterOp("item != null");
        
        filter.addComments(java.util.List.of("Comment 1", "Comment 2", "Comment 3"));
        assertThat(filter.hasComments()).isTrue();
        assertThat(filter.getComments()).hasSize(3);
        assertThat(filter.getComments()).containsExactly("Comment 1", "Comment 2", "Comment 3");
    }
    
    @Test
    void testMapOpComments() {
        MapOp map = new MapOp("item.toUpperCase()");
        
        // Initially no comments
        assertThat(map.hasComments()).isFalse();
        assertThat(map.getComments()).isEmpty();
        
        // Add comments
        map.addComment("Convert to uppercase");
        map.addComment("For display purposes");
        
        assertThat(map.hasComments()).isTrue();
        assertThat(map.getComments()).hasSize(2);
        assertThat(map.getComments()).containsExactly("Convert to uppercase", "For display purposes");
    }
    
    @Test
    void testMapOpAddCommentsList() {
        MapOp map = new MapOp("item.toString()");
        
        map.addComments(java.util.List.of("Convert to string", "Debugging aid"));
        assertThat(map.hasComments()).isTrue();
        assertThat(map.getComments()).hasSize(2);
    }
    
    @Test
    void testCommentsAreImmutable() {
        FilterOp filter = new FilterOp("item != null");
        filter.addComment("Comment 1");
        
        java.util.List<String> comments = filter.getComments();
        assertThat(comments).hasSize(1);
        
        // Try to modify returned list - should throw exception
        org.junit.jupiter.api.Assertions.assertThrows(
            UnsupportedOperationException.class,
            () -> comments.add("Should not work")
        );
    }
}
