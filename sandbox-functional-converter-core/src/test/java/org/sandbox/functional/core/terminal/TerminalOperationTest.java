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
package org.sandbox.functional.core.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TerminalOperation} implementations.
 */
class TerminalOperationTest {
    
    @Test
    void testForEachTerminal() {
        ForEachTerminal forEach = new ForEachTerminal(
            List.of("System.out.println(item)"), false);
        assertThat(forEach.operationType()).isEqualTo("forEach");
        assertThat(forEach.bodyStatements()).hasSize(1);
        assertThat(forEach.ordered()).isFalse();
    }
    
    @Test
    void testForEachTerminalSingleArg() {
        ForEachTerminal forEach = new ForEachTerminal(
            List.of("System.out.println(item)"));
        assertThat(forEach.operationType()).isEqualTo("forEach");
        assertThat(forEach.ordered()).isFalse();
    }
    
    @Test
    void testForEachOrderedTerminal() {
        ForEachTerminal forEachOrdered = new ForEachTerminal(
            List.of("System.out.println(item)"), true);
        assertThat(forEachOrdered.operationType()).isEqualTo("forEachOrdered");
        assertThat(forEachOrdered.ordered()).isTrue();
    }
    
    @Test
    void testCollectTerminal() {
        CollectTerminal collect = new CollectTerminal(
            CollectTerminal.CollectorType.TO_LIST, "result");
        assertThat(collect.operationType()).isEqualTo("collect");
        assertThat(collect.collectorType()).isEqualTo(CollectTerminal.CollectorType.TO_LIST);
        assertThat(collect.targetVariable()).isEqualTo("result");
    }
    
    @Test
    void testReduceTerminal() {
        ReduceTerminal reduce = new ReduceTerminal(
            "0", "(a, b) -> a + b", null, ReduceTerminal.ReduceType.SUM);
        assertThat(reduce.operationType()).isEqualTo("reduce");
        assertThat(reduce.identity()).isEqualTo("0");
        assertThat(reduce.accumulator()).isEqualTo("(a, b) -> a + b");
        assertThat(reduce.reduceType()).isEqualTo(ReduceTerminal.ReduceType.SUM);
    }
    
    @Test
    void testMatchTerminalAnyMatch() {
        MatchTerminal match = new MatchTerminal(
            MatchTerminal.MatchType.ANY_MATCH, "x > 0");
        assertThat(match.operationType()).isEqualTo("anyMatch");
        assertThat(match.matchType()).isEqualTo(MatchTerminal.MatchType.ANY_MATCH);
        assertThat(match.predicate()).isEqualTo("x > 0");
    }
    
    @Test
    void testMatchTerminalAllMatch() {
        MatchTerminal match = new MatchTerminal(
            MatchTerminal.MatchType.ALL_MATCH, "x > 0");
        assertThat(match.operationType()).isEqualTo("allMatch");
        assertThat(match.matchType()).isEqualTo(MatchTerminal.MatchType.ALL_MATCH);
    }
    
    @Test
    void testMatchTerminalNoneMatch() {
        MatchTerminal match = new MatchTerminal(
            MatchTerminal.MatchType.NONE_MATCH, "x > 0");
        assertThat(match.operationType()).isEqualTo("noneMatch");
        assertThat(match.matchType()).isEqualTo(MatchTerminal.MatchType.NONE_MATCH);
    }
    
    @Test
    void testFindFirstTerminal() {
        FindTerminal find = new FindTerminal(true);
        assertThat(find.operationType()).isEqualTo("findFirst");
        assertThat(find.findFirst()).isTrue();
    }
    
    @Test
    void testFindAnyTerminal() {
        FindTerminal find = new FindTerminal(false);
        assertThat(find.operationType()).isEqualTo("findAny");
        assertThat(find.findFirst()).isFalse();
    }
    
    @Test
    void testCountTerminal() {
        CountTerminal count = new CountTerminal();
        assertThat(count.operationType()).isEqualTo("count");
    }
}
