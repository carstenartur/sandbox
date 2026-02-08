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
package org.sandbox.functional.core.tree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the LoopTree structure and nested loop analysis.
 * 
 * <p>These tests verify that the LoopTree correctly handles nested loops
 * and makes proper conversion decisions based on bottom-up analysis.</p>
 */
class LoopTreeTest {

    @Test
    void singleLoop() {
        LoopTree tree = new LoopTree();
        
        LoopTreeNode node = tree.pushLoop(LoopKind.ENHANCED_FOR);
        node.getScopeInfo().addLocalVariable("item");
        tree.popLoop();
        
        node.setDecision(ConversionDecision.CONVERTIBLE);
        
        List<LoopTreeNode> convertible = tree.getConvertibleNodes();
        assertThat(convertible).hasSize(1);
        assertThat(convertible.get(0)).isSameAs(node);
        assertThat(convertible.get(0).getKind()).isEqualTo(LoopKind.ENHANCED_FOR);
    }
    
    @Test
    void nestedLoops_innerConvertible() {
        LoopTree tree = new LoopTree();
        
        // Outer loop
        LoopTreeNode outer = tree.pushLoop(LoopKind.ENHANCED_FOR);
        outer.getScopeInfo().addLocalVariable("outerItem");
        
        // Inner loop
        LoopTreeNode inner = tree.pushLoop(LoopKind.ENHANCED_FOR);
        inner.getScopeInfo().addLocalVariable("innerItem");
        tree.popLoop();
        
        // Inner is convertible
        inner.setDecision(ConversionDecision.CONVERTIBLE);
        
        tree.popLoop();
        
        // Outer should be skipped because inner is convertible
        if (outer.hasConvertibleDescendant()) {
            outer.setDecision(ConversionDecision.SKIPPED_INNER_CONVERTED);
        } else {
            outer.setDecision(ConversionDecision.CONVERTIBLE);
        }
        
        List<LoopTreeNode> convertible = tree.getConvertibleNodes();
        assertThat(convertible).hasSize(1);
        assertThat(convertible.get(0)).isSameAs(inner);
        assertThat(outer.getDecision()).isEqualTo(ConversionDecision.SKIPPED_INNER_CONVERTED);
    }
    
    @Test
    void nestedLoops_innerNotConvertible() {
        LoopTree tree = new LoopTree();
        
        // Outer loop
        LoopTreeNode outer = tree.pushLoop(LoopKind.ENHANCED_FOR);
        outer.getScopeInfo().addLocalVariable("outerItem");
        
        // Inner loop
        LoopTreeNode inner = tree.pushLoop(LoopKind.ENHANCED_FOR);
        inner.getScopeInfo().addLocalVariable("innerItem");
        tree.popLoop();
        
        // Inner is NOT convertible
        inner.setDecision(ConversionDecision.NOT_CONVERTIBLE);
        
        tree.popLoop();
        
        // Outer can be convertible since inner is not converted
        if (outer.hasConvertibleDescendant()) {
            outer.setDecision(ConversionDecision.SKIPPED_INNER_CONVERTED);
        } else {
            outer.setDecision(ConversionDecision.CONVERTIBLE);
        }
        
        List<LoopTreeNode> convertible = tree.getConvertibleNodes();
        assertThat(convertible).hasSize(1);
        assertThat(convertible.get(0)).isSameAs(outer);
        assertThat(outer.getDecision()).isEqualTo(ConversionDecision.CONVERTIBLE);
    }
    
    @Test
    void threeLevelNesting() {
        LoopTree tree = new LoopTree();
        
        // Level 1 (outermost)
        LoopTreeNode level1 = tree.pushLoop(LoopKind.ENHANCED_FOR);
        level1.getScopeInfo().addLocalVariable("item1");
        
        // Level 2 (middle)
        LoopTreeNode level2 = tree.pushLoop(LoopKind.ENHANCED_FOR);
        level2.getScopeInfo().addLocalVariable("item2");
        
        // Level 3 (innermost)
        LoopTreeNode level3 = tree.pushLoop(LoopKind.ENHANCED_FOR);
        level3.getScopeInfo().addLocalVariable("item3");
        tree.popLoop();
        
        // Level 3 is convertible
        level3.setDecision(ConversionDecision.CONVERTIBLE);
        
        tree.popLoop();
        
        // Level 2 should be skipped
        if (level2.hasConvertibleDescendant()) {
            level2.setDecision(ConversionDecision.SKIPPED_INNER_CONVERTED);
        } else {
            level2.setDecision(ConversionDecision.CONVERTIBLE);
        }
        
        tree.popLoop();
        
        // Level 1 should also be skipped
        if (level1.hasConvertibleDescendant()) {
            level1.setDecision(ConversionDecision.SKIPPED_INNER_CONVERTED);
        } else {
            level1.setDecision(ConversionDecision.CONVERTIBLE);
        }
        
        List<LoopTreeNode> convertible = tree.getConvertibleNodes();
        assertThat(convertible).hasSize(1);
        assertThat(convertible.get(0)).isSameAs(level3);
        assertThat(level2.getDecision()).isEqualTo(ConversionDecision.SKIPPED_INNER_CONVERTED);
        assertThat(level1.getDecision()).isEqualTo(ConversionDecision.SKIPPED_INNER_CONVERTED);
    }
    
    @Test
    void scopePropagation() {
        LoopTree tree = new LoopTree();
        
        // Outer loop
        LoopTreeNode outer = tree.pushLoop(LoopKind.ENHANCED_FOR);
        outer.getScopeInfo().addLocalVariable("outerVar");
        outer.getScopeInfo().addModifiedVariable("modified");
        
        // Inner loop
        LoopTreeNode inner = tree.pushLoop(LoopKind.ENHANCED_FOR);
        inner.getScopeInfo().addLocalVariable("innerVar");
        
        // Inner scope should see outer variables
        assertThat(inner.getScopeInfo().getOuterScopeVariables())
            .contains("outerVar");
        
        // Modified variables propagate
        assertThat(inner.getScopeInfo().getModifiedVariables())
            .contains("modified");
        
        assertThat(inner.getScopeInfo().isEffectivelyFinal("modified"))
            .isFalse();
        
        tree.popLoop();
        tree.popLoop();
    }
    
    @Test
    void siblingLoops() {
        LoopTree tree = new LoopTree();
        
        // First loop
        LoopTreeNode loop1 = tree.pushLoop(LoopKind.ENHANCED_FOR);
        loop1.getScopeInfo().addLocalVariable("item1");
        tree.popLoop();
        loop1.setDecision(ConversionDecision.CONVERTIBLE);
        
        // Second loop (sibling)
        LoopTreeNode loop2 = tree.pushLoop(LoopKind.ENHANCED_FOR);
        loop2.getScopeInfo().addLocalVariable("item2");
        tree.popLoop();
        loop2.setDecision(ConversionDecision.CONVERTIBLE);
        
        // Both should be convertible as they are siblings
        List<LoopTreeNode> convertible = tree.getConvertibleNodes();
        assertThat(convertible).hasSize(2);
        assertThat(convertible).contains(loop1, loop2);
        
        // Check they are roots
        List<LoopTreeNode> roots = tree.getRoots();
        assertThat(roots).hasSize(2);
        assertThat(roots).contains(loop1, loop2);
    }
    
    @Test
    void astNodeReference() {
        LoopTree tree = new LoopTree();
        
        Object mockAstNode = new Object();
        
        LoopTreeNode node = tree.pushLoop(LoopKind.ENHANCED_FOR);
        node.setAstNodeReference(mockAstNode);
        
        assertThat(node.getAstNodeReference()).isSameAs(mockAstNode);
        
        tree.popLoop();
    }
}
