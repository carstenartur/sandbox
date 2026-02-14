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
     * Immutable analysis result for an iterator loop's safety.
     *
     * @param isSafe whether the loop is safe for stream conversion
     * @param reason the reason if not safe, or {@code null} if safe
     * @param hasRemove whether the loop body calls {@code iterator.remove()}
     * @param hasMultipleNext whether there are multiple {@code iterator.next()} calls
     * @param hasBreak whether the loop body contains a {@code break} statement
     * @param hasLabeledContinue whether the loop body contains a labeled {@code continue}
     */
    public record SafetyAnalysis(boolean isSafe, String reason, boolean hasRemove,
                                 boolean hasMultipleNext, boolean hasBreak, boolean hasLabeledContinue) {

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
        
        // break statements are not yet supported for conversion
        // Future enhancement: could be converted using takeWhile or filter with short-circuit
        if (visitor.hasBreak) {
            return SafetyAnalysis.unsafe("break statements in the loop body are not yet supported for stream conversion",
                                         false, false, true, false);
        }
        
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
            if (expr instanceof SimpleName name) {
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
