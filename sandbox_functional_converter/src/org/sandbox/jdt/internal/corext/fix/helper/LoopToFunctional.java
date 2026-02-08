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
import org.sandbox.functional.core.tree.ConversionDecision;
import org.sandbox.functional.core.tree.LoopKind;
import org.sandbox.functional.core.tree.LoopTree;
import org.sandbox.functional.core.tree.LoopTreeNode;

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
		
		// PHASE 9: Use LoopTree for nested loop analysis
		// Continue with individual loop processing for non-grouped loops using LoopTree
		ReferenceHolder<Integer, FunctionalHolder> dataHolder = new ReferenceHolder<>();
		ReferenceHolder<String, Object> treeHolder = new ReferenceHolder<>();
		
		// Initialize the LoopTree in the shared holder
		LoopTree tree = new LoopTree();
		treeHolder.put("tree", tree);
		
		// Use BiPredicate (visit) and BiConsumer (endVisit) for tree-based analysis
		HelperVisitor.callEnhancedForStatementVisitor(compilationUnit, dataHolder, nodesprocessed,
				// Visit (BiPredicate): pushLoop and continue traversal
				(visited, aholder) -> visitLoop(visited, treeHolder, nodesprocessed),
				// EndVisit (BiConsumer): popLoop and make conversion decision
				(visited, aholder) -> endVisitLoop(visited, treeHolder, compilationUnit));
		
		// After traversal, collect convertible nodes and add operations
		List<LoopTreeNode> convertibleNodes = tree.getConvertibleNodes();
		for (LoopTreeNode node : convertibleNodes) {
			EnhancedForStatement loopStatement = (EnhancedForStatement) node.getAstNodeReference();
			if (loopStatement != null && !nodesprocessed.contains(loopStatement)) {
				ReferenceHolder<ASTNode, Object> sharedDataHolder = new ReferenceHolder<>();
				operations.add(fixcore.rewrite(loopStatement, sharedDataHolder));
				nodesprocessed.add(loopStatement);
			}
		}
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

	/**
	 * Visit handler for entering a loop node.
	 * 
	 * <p>PHASE 9: This method is called when visiting an EnhancedForStatement.
	 * It pushes a new node onto the LoopTree and sets the AST reference.</p>
	 * 
	 * @param visited the EnhancedForStatement being visited
	 * @param treeHolder the holder containing the LoopTree
	 * @param nodesprocessed the set of already processed nodes
	 * @return true to continue visiting children, false to skip
	 */
	private boolean visitLoop(EnhancedForStatement visited, 
			ReferenceHolder<String, Object> treeHolder,
			Set<ASTNode> nodesprocessed) {
		// Skip loops that have already been processed (e.g., as part of a consecutive loop group)
		if (nodesprocessed.contains(visited)) {
			return false; // Don't visit children of already-processed loops
		}
		
		// Get the LoopTree from the holder
		LoopTree tree = (LoopTree) treeHolder.get("tree");
		if (tree == null) {
			return false;
		}
		
		// Push a new loop node onto the tree
		LoopTreeNode node = tree.pushLoop(LoopKind.ENHANCED_FOR);
		
		// Set the AST node reference for later rewriting
		node.setAstNodeReference(visited);
		
		// Populate ScopeInfo by scanning the loop body
		LoopBodyScopeScanner scanner = new LoopBodyScopeScanner(visited);
		scanner.scan();
		scanner.populateScopeInfo(node.getScopeInfo());
		
		// Store the scanner for access in endVisitLoop (to check referenced variables)
		treeHolder.put("scanner_" + System.identityHashCode(visited), scanner);
		
		// Continue visiting children (nested loops)
		return true;
	}
	
	/**
	 * EndVisit handler for exiting a loop node.
	 * 
	 * <p>PHASE 9: This method is called when exiting an EnhancedForStatement.
	 * It pops the node from the tree and makes a conversion decision based on
	 * preconditions and whether any descendant loops are convertible.</p>
	 * 
	 * <p>The conversion decision now takes ScopeInfo into account: if an inner loop
	 * uses variables that are modified in the current (outer) loop's scope, the
	 * inner loop may need special handling.</p>
	 * 
	 * @param visited the EnhancedForStatement being exited
	 * @param treeHolder the holder containing the LoopTree
	 * @param compilationUnit the compilation unit for analysis
	 */
	private void endVisitLoop(EnhancedForStatement visited,
			ReferenceHolder<String, Object> treeHolder,
			CompilationUnit compilationUnit) {
		// Get the LoopTree from the holder
		LoopTree tree = (LoopTree) treeHolder.get("tree");
		if (tree == null || !tree.isInsideLoop()) {
			return;
		}
		
		// Verify this is the correct node to pop (guard against stack corruption)
		LoopTreeNode currentNode = tree.current();
		if (currentNode == null || currentNode.getAstNodeReference() != visited) {
			return; // Stack mismatch - visitLoop must have returned false, so no pushLoop occurred
		}
		
		// Pop the current loop node
		LoopTreeNode node = tree.popLoop();
		
		// Make conversion decision based on bottom-up analysis
		// If any descendant is convertible, skip this loop
		if (node.hasConvertibleDescendant()) {
			node.setDecision(ConversionDecision.SKIPPED_INNER_CONVERTED);
			return;
		}
		
		// Check ScopeInfo: if this loop references variables that are modified
		// in an ANCESTOR loop's scope, it cannot be converted (lambda capture requires
		// effectively final variables).
		// NOTE: We only check PARENT scopes, not the current loop's own scope.
		// Reducer patterns (like sum += item) modify variables in their own scope,
		// which is handled correctly by PreconditionsChecker and converted to .reduce().
		LoopBodyScopeScanner scanner = (LoopBodyScopeScanner) treeHolder.get("scanner_" + System.identityHashCode(visited));
		if (scanner != null && node.getParent() != null) {
			// Walk up the tree and check if any referenced variable is modified in ancestor scopes
			LoopTreeNode parent = node.getParent();
			while (parent != null) {
				for (String referencedVar : scanner.getReferencedVariables()) {
					if (parent.getScopeInfo().getModifiedVariables().contains(referencedVar)) {
						node.setDecision(ConversionDecision.NOT_CONVERTIBLE);
						return;
					}
				}
				parent = parent.getParent();
			}
		}
		
		// Check preconditions for conversion
		PreconditionsChecker pc = new PreconditionsChecker(visited, compilationUnit);
		if (!pc.isSafeToRefactor()) {
			node.setDecision(ConversionDecision.NOT_CONVERTIBLE);
			return;
		}
		
		// Check if the loop can be analyzed for stream conversion
		StreamPipelineBuilder builder = new StreamPipelineBuilder(visited, pc);
		if (!builder.analyze()) {
			node.setDecision(ConversionDecision.NOT_CONVERTIBLE);
			return;
		}
		
		// Loop is convertible
		node.setDecision(ConversionDecision.CONVERTIBLE);
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
			// Loop cannot be safely refactored to functional style
			// Return true to continue visiting children - inner loops may still be convertible
			return false;
		}
		// Check if the loop can be analyzed for stream conversion
		StreamPipelineBuilder builder = new StreamPipelineBuilder(visited, pc);
		if (!builder.analyze()) {
			// Cannot convert this loop to functional style
			// Return true to continue visiting children - inner loops may still be convertible
			return false;
		}
		// V1 doesn't need to store data in the holder, but we pass it to maintain signature compatibility
		operations.add(fixcore.rewrite(visited, sharedDataHolder));
		nodesprocessed.add(visited);
		// Return false to prevent visiting children since this loop was converted
		// (children are now part of the lambda expression)
		return false;
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
