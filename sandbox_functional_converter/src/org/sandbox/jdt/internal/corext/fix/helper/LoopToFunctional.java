/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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

import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.ConsecutiveLoopGroupDetector.ConsecutiveLoopGroup;

/**
 * Converts enhanced for-loops to functional stream operations.
 * 
 * <p>
 * This class implements the Eclipse JDT cleanup framework to find and transform
 * imperative for-loops into declarative stream pipelines. It integrates with
 * the Eclipse IDE's quick fix and cleanup mechanisms.
 * </p>
 * 
 * <p><b>Example Transformation:</b></p>
 * <pre>{@code
 * // Before:
 * for (Integer l : ls) {
 *     System.out.println(l);
 * }
 * 
 * // After:
 * ls.forEach(l -> {
 *     System.out.println(l);
 * });
 * }</pre>
 * 
 * <p><b>Integration with Eclipse:</b></p>
 * <p>
 * This class extends {@link AbstractFunctionalCall} and is registered as a
 * cleanup contributor in the Eclipse JDT UI framework. It participates in:
 * <ul>
 * <li>Source cleanup actions (Ctrl+Shift+F in Eclipse)</li>
 * <li>Quick fix suggestions (Ctrl+1)</li>
 * <li>Batch cleanup operations</li>
 * </ul>
 * </p>
 * 
 * <p><b>Processing Flow:</b></p>
 * <ol>
 * <li>{@link #find(UseFunctionalCallFixCore, CompilationUnit, Set, Set)}: 
 *     Visits all EnhancedForStatements and identifies convertible loops</li>
 * <li>{@link #rewrite(UseFunctionalCallFixCore, EnhancedForStatement, CompilationUnitRewrite, TextEditGroup)}:
 *     Performs the actual AST transformation for each identified loop</li>
 * <li>{@link #getPreview(boolean)}: Provides before/after preview in Eclipse UI</li>
 * </ol>
 * 
 * <p><b>Safety Checks:</b></p>
 * <p>
 * The conversion only occurs if:
 * <ul>
 * <li>{@link PreconditionsChecker} validates the loop is safe to refactor</li>
 * <li>{@link StreamPipelineBuilder} successfully analyzes the loop body</li>
 * <li>All variables are effectively final</li>
 * <li>No break, labeled continue, or exception throwing occurs</li>
 * </ul>
 * </p>
 * 
 * @see AbstractFunctionalCall
 * @see StreamPipelineBuilder
 * @see PreconditionsChecker
 * @see Refactorer
 */
public class LoopToFunctional extends AbstractFunctionalCall<EnhancedForStatement> {

	@Override
	public void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
		
		// PHASE 8: Pre-process to detect consecutive loops adding to same collection
		// This must happen before individual loop processing to avoid incorrect overwrites
		detectAndProcessConsecutiveLoops(fixcore, compilationUnit, operations, nodesprocessed);
		
		// Continue with individual loop processing for non-grouped loops
		ReferenceHolder<Integer, FunctionalHolder> dataHolder= new ReferenceHolder<>();
		ReferenceHolder<ASTNode, Object> sharedDataHolder = new ReferenceHolder<>();
		HelperVisitor.callEnhancedForStatementVisitor(compilationUnit, dataHolder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, nodesprocessed, visited, aholder, sharedDataHolder),(visited, aholder) -> {});
	}
	
	/**
	 * Detects and processes consecutive loops that add to the same collection.
	 * 
	 * <p>Phase 8 feature: Multiple consecutive for-loops adding to the same list
	 * are converted to Stream.concat() instead of being converted individually
	 * (which would cause overwrites).</p>
	 * 
	 * @param fixcore the fix core instance
	 * @param compilationUnit the compilation unit to scan
	 * @param operations the set to add operations to
	 * @param nodesprocessed the set of already processed nodes
	 */
	private void detectAndProcessConsecutiveLoops(UseFunctionalCallFixCore fixcore, 
			CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, 
			Set<ASTNode> nodesprocessed) {
		
		// Visit all blocks to find consecutive loop groups
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(Block block) {
				List<ConsecutiveLoopGroup> groups = ConsecutiveLoopGroupDetector.detectGroups(block);
				
				for (ConsecutiveLoopGroup group : groups) {
					// Create a rewrite operation for this group
					operations.add(fixcore.rewriteConsecutiveLoops(group));
					
					// Mark all loops in the group as processed to prevent individual conversion
					for (EnhancedForStatement loop : group.getLoops()) {
						nodesprocessed.add(loop);
					}
				}
				
				return true; // Continue visiting nested blocks
			}
		});
	}

	private boolean processFoundNode(UseFunctionalCallFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, EnhancedForStatement visited,
			ReferenceHolder<Integer, FunctionalHolder> dataHolder, ReferenceHolder<ASTNode, Object> sharedDataHolder) {
		// Skip loops that have already been processed (e.g., as part of a consecutive loop group)
		if (nodesprocessed.contains(visited)) {
			return false; // Don't visit children of already-processed loops
		}
		
		PreconditionsChecker pc = new PreconditionsChecker(visited, (CompilationUnit) visited.getRoot());
		if (!pc.isSafeToRefactor()) {
			// If the only reason is nested loops, try to convert inner loops independently
			if (pc.hasNestedLoop()) {
				processInnerLoops(fixcore, operations, nodesprocessed, visited, dataHolder, sharedDataHolder);
			}
			return false;
		}
		// Check if the loop can be analyzed for stream conversion
		StreamPipelineBuilder builder = new StreamPipelineBuilder(visited, pc);
		if (!builder.analyze()) {
			return false;
		}
		operations.add(fixcore.rewrite(visited, sharedDataHolder));
		nodesprocessed.add(visited);
		return false;
	}

	/**
	 * When an outer loop cannot be converted (because it contains nested loops),
	 * visit the body to find inner enhanced-for loops that can be converted independently.
	 * 
	 * <p>
	 * The visitor traverses the entire body of the outer loop and processes every nested
	 * {@link EnhancedForStatement} it encounters. For each such inner loop, the visitor
	 * does not descend into that loop's body (because {@code visit} returns {@code false}),
	 * so deeper nested enhanced-for loops are handled when their own enclosing loop is visited.
	 * </p>
	 */
	private void processInnerLoops(UseFunctionalCallFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed,
			EnhancedForStatement outerLoop,
			ReferenceHolder<Integer, FunctionalHolder> dataHolder,
			ReferenceHolder<ASTNode, Object> sharedDataHolder) {
		outerLoop.getBody().accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
			@Override
			public boolean visit(EnhancedForStatement innerLoop) {
				// Delegate to the canonical processing path so that
				// all precondition checks and conversions are handled
				// consistently with top-level loops.
				processFoundNode(fixcore, operations, nodesprocessed, innerLoop, dataHolder, sharedDataHolder);
				// Do not visit children here: processFoundNode(...) is
				// responsible for any further nested-loop handling.
				return false;
			}
		});
	}

	@Override
	public void rewrite(UseFunctionalCallFixCore upp, final EnhancedForStatement visited,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group, 
			org.sandbox.jdt.internal.common.ReferenceHolder<ASTNode, Object> data) throws CoreException {
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		PreconditionsChecker pc = new PreconditionsChecker(visited, (CompilationUnit) visited.getRoot());
		Refactorer refactorer = new Refactorer(visited, rewrite, pc, group, cuRewrite);
		// Preconditions already checked in find(), but refactorer.refactor() handles edge cases
		refactorer.refactor();
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "ls.forEach(l -> {\n	System.out.println(l);\n});\n"; //$NON-NLS-1$
		}
		return "for (Integer l : ls)\n	System.out.println(l);\n\n"; //$NON-NLS-1$
	}
}
