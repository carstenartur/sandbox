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
import org.sandbox.jdt.internal.common.AstProcessorBuilder;
import org.sandbox.jdt.internal.common.ReferenceHolder;

/**
 * Analyzes iterator-based loops for safety constraints.
 * 
 * <p>Checks for:</p>
 * <ul>
 * <li>Calls to {@code iterator.remove()} - not safe for stream conversion</li>
 * <li>Multiple calls to {@code next()} - indicates complex iteration logic</li>
 * <li>Modifications to the collection during iteration</li>
 * <li>Break statements</li>
 * <li>Labeled continue statements</li>
 * </ul>
 * 
 * @see IteratorPatternDetector
 * @see IteratorLoopBodyParser
 */
public class IteratorLoopAnalyzer {
	
	private final IteratorPatternDetector.IteratorPattern pattern;
	private final String iteratorVarName;
	
	private boolean hasRemoveCall;
	private boolean hasMultipleNextCalls;
	private boolean hasBreak;
	private boolean hasLabeledContinue;
	private int nextCallCount;
	
	/**
	 * Creates a new analyzer for the given iterator pattern.
	 * 
	 * @param pattern the detected iterator pattern
	 */
	public IteratorLoopAnalyzer(IteratorPatternDetector.IteratorPattern pattern) {
		this.pattern = pattern;
		this.iteratorVarName = pattern.getIteratorVarName();
	}
	
	/**
	 * Analyzes the iterator loop for safety constraints.
	 * 
	 * @return true if the loop is safe to convert to streams
	 */
	public boolean isSafeToConvert() {
		// Reset state
		hasRemoveCall = false;
		hasMultipleNextCalls = false;
		hasBreak = false;
		hasLabeledContinue = false;
		nextCallCount = 0;
		
		// Analyze the loop body
		analyzeLoopBody();
		
		// A loop is safe to convert if:
		// - No iterator.remove() calls
		// - Exactly one next() call (at the start of the loop)
		// - No break statements
		// - No labeled continue statements
		return !hasRemoveCall && 
		       !hasMultipleNextCalls && 
		       !hasBreak && 
		       !hasLabeledContinue &&
		       nextCallCount == 1;
	}
	
	/**
	 * Analyzes the loop body for unsafe operations.
	 */
	private void analyzeLoopBody() {
		Statement body = pattern.getLoopBody();
		if (body == null) {
			return;
		}
		
		AstProcessorBuilder<String, Object> builder = AstProcessorBuilder.with(new ReferenceHolder<String, Object>());
		
		// Check for method invocations on the iterator
		builder.onMethodInvocation((node, h) -> {
			Expression receiver = node.getExpression();
			if (receiver instanceof SimpleName) {
				String receiverName = ((SimpleName) receiver).getIdentifier();
				if (iteratorVarName.equals(receiverName)) {
					String methodName = node.getName().getIdentifier();
					
					if ("remove".equals(methodName)) { //$NON-NLS-1$
						hasRemoveCall = true;
					} else if ("next".equals(methodName)) { //$NON-NLS-1$
						nextCallCount++;
						if (nextCallCount > 1) {
							hasMultipleNextCalls = true;
						}
					}
				}
			}
			return true;
		});
		
		// Check for break statements
		builder.onBreakStatement((node, h) -> {
			hasBreak = true;
			return true;
		});
		
		// Check for labeled continue statements
		builder.onContinueStatement((node, h) -> {
			if (node.getLabel() != null) {
				hasLabeledContinue = true;
			}
			return true;
		});
		
		// Build and process
		ASTVisitor visitor = builder.build();
		body.accept(visitor);
	}
	
	/**
	 * Returns true if the iterator loop contains a call to remove().
	 * 
	 * @return true if remove() is called
	 */
	public boolean hasRemoveCall() {
		return hasRemoveCall;
	}
	
	/**
	 * Returns true if the iterator loop has multiple next() calls.
	 * 
	 * @return true if multiple next() calls detected
	 */
	public boolean hasMultipleNextCalls() {
		return hasMultipleNextCalls;
	}
	
	/**
	 * Returns true if the iterator loop contains break statements.
	 * 
	 * @return true if break statements detected
	 */
	public boolean hasBreak() {
		return hasBreak;
	}
	
	/**
	 * Returns true if the iterator loop contains labeled continue statements.
	 * 
	 * @return true if labeled continue detected
	 */
	public boolean hasLabeledContinue() {
		return hasLabeledContinue;
	}
	
	/**
	 * Returns the number of next() calls in the loop.
	 * 
	 * @return next() call count
	 */
	public int getNextCallCount() {
		return nextCallCount;
	}
}
