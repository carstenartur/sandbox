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
}
