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
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.jdt.core.dom.*;

/**
 * Analyzes iterator loops for safety and convertibility.
 * 
 * <p>This analyzer checks for:</p>
 * <ul>
 *   <li>iterator.remove() calls (not safe for stream conversion)</li>
 *   <li>Multiple iterator.next() calls (not safe)</li>
 *   <li>break statements (requires special handling)</li>
 *   <li>labeled continue statements (not safe)</li>
 * </ul>
 */
public class IteratorLoopAnalyzer {
    
    /**
     * Analysis result for an iterator loop.
     */
    public static class SafetyAnalysis {
        public final boolean isSafe;
        public final String reason; // reason if not safe
        public final boolean hasRemove;
        public final boolean hasMultipleNext;
        public final boolean hasBreak;
        public final boolean hasLabeledContinue;
        
        private SafetyAnalysis(boolean isSafe, String reason, boolean hasRemove, 
                               boolean hasMultipleNext, boolean hasBreak, boolean hasLabeledContinue) {
            this.isSafe = isSafe;
            this.reason = reason;
            this.hasRemove = hasRemove;
            this.hasMultipleNext = hasMultipleNext;
            this.hasBreak = hasBreak;
            this.hasLabeledContinue = hasLabeledContinue;
        }
        
        public static SafetyAnalysis safe() {
            return new SafetyAnalysis(true, null, false, false, false, false);
        }
        
        public static SafetyAnalysis unsafe(String reason, boolean hasRemove, 
                                            boolean hasMultipleNext, boolean hasBreak, boolean hasLabeledContinue) {
            return new SafetyAnalysis(false, reason, hasRemove, hasMultipleNext, hasBreak, hasLabeledContinue);
        }
    }
    
    /**
     * Analyzes an iterator loop body for safety.
     * 
     * @param loopBody the loop body to analyze
     * @param iteratorVarName the name of the iterator variable
     * @return safety analysis result
     */
    public SafetyAnalysis analyze(Statement loopBody, String iteratorVarName) {
        IteratorUsageVisitor visitor = new IteratorUsageVisitor(iteratorVarName);
        loopBody.accept(visitor);
        
        if (visitor.hasRemove) {
            return SafetyAnalysis.unsafe("Iterator.remove() is not supported in stream operations", 
                                         true, visitor.hasMultipleNext, visitor.hasBreak, visitor.hasLabeledContinue);
        }
        
        if (visitor.hasMultipleNext) {
            return SafetyAnalysis.unsafe("Multiple iterator.next() calls detected", 
                                         false, true, visitor.hasBreak, visitor.hasLabeledContinue);
        }
        
        if (visitor.hasLabeledContinue) {
            return SafetyAnalysis.unsafe("Labeled continue statement is not supported", 
                                         false, false, visitor.hasBreak, true);
        }
        
        // break statements are okay - they can be converted to filter or short-circuit operations
        
        return SafetyAnalysis.safe();
    }
    
    /**
     * Visitor that checks for unsafe iterator usage patterns.
     */
    private static class IteratorUsageVisitor extends ASTVisitor {
        private final String iteratorVarName;
        private boolean hasRemove = false;
        private int nextCallCount = 0;
        private boolean hasMultipleNext = false;
        private boolean hasBreak = false;
        private boolean hasLabeledContinue = false;
        
        public IteratorUsageVisitor(String iteratorVarName) {
            this.iteratorVarName = iteratorVarName;
        }
        
        @Override
        public boolean visit(MethodInvocation node) {
            Expression expr = node.getExpression();
            if (expr instanceof SimpleName) {
                SimpleName name = (SimpleName) expr;
                if (name.getIdentifier().equals(iteratorVarName)) {
                    String methodName = node.getName().getIdentifier();
                    
                    if ("remove".equals(methodName)) {
                        hasRemove = true;
                    } else if ("next".equals(methodName)) {
                        nextCallCount++;
                        if (nextCallCount > 1) {
                            hasMultipleNext = true;
                        }
                    }
                }
            }
            return true;
        }
        
        @Override
        public boolean visit(BreakStatement node) {
            hasBreak = true;
            return false;
        }
        
        @Override
        public boolean visit(ContinueStatement node) {
            if (node.getLabel() != null) {
                hasLabeledContinue = true;
            }
            return false;
        }
        
        @Override
        public boolean visit(WhileStatement node) {
            // Don't visit nested loops
            return false;
        }
        
        @Override
        public boolean visit(ForStatement node) {
            // Don't visit nested loops
            return false;
        }
        
        @Override
        public boolean visit(EnhancedForStatement node) {
            // Don't visit nested loops
            return false;
        }
        
        @Override
        public boolean visit(DoStatement node) {
            // Don't visit nested loops
            return false;
        }
    }
}
