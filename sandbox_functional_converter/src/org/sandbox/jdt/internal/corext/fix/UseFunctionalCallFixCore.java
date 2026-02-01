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
package org.sandbox.jdt.internal.corext.fix;

import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.TargetSourceRangeComputer;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.AbstractFunctionalCall;
import org.sandbox.jdt.internal.corext.fix.helper.ConsecutiveLoopGroupDetector.ConsecutiveLoopGroup;
import org.sandbox.jdt.internal.corext.fix.helper.EnhancedForToIteratorWhile;
import org.sandbox.jdt.internal.corext.fix.helper.IteratorLoopToFunctional;
import org.sandbox.jdt.internal.corext.fix.helper.IteratorWhileToEnhancedFor;
import org.sandbox.jdt.internal.corext.fix.helper.LoopToFunctional;
import org.sandbox.jdt.internal.corext.fix.helper.LoopToFunctionalV2;
import org.sandbox.jdt.internal.corext.fix.helper.StreamConcatRefactorer;
import org.sandbox.jdt.internal.corext.fix.helper.StreamToEnhancedFor;
import org.sandbox.jdt.internal.corext.fix.helper.StreamToIteratorWhile;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;

public enum UseFunctionalCallFixCore {

	LOOP(new LoopToFunctional()),
	// V2 - ULR-based implementation running in parallel to LOOP (V1).
	// Phase 1 uses a delegation pattern: LOOP_V2 delegates to the existing implementation
	// to maintain feature parity while introducing the new ULR infrastructure.
	// Roadmap for future ULR phases:
	//   - Phase 2: Gradually switch individual loop patterns to ULR-native implementations.
	//   - Phase 3: Make ULR the primary implementation and retire legacy paths once stable.
	// Documentation note (per coding guidelines):
	// sandbox_functional_converter/ARCHITECTURE.md and sandbox_functional_converter/TODO.md
	// have been reviewed and updated to describe:
	//   (1) the V2 parallel implementation strategy,
	//   (2) the Phase 1 delegation pattern,
	//   (3) the roadmap for future ULR implementation phases.
	// Related issues: https://github.com/carstenartur/sandbox/issues/450
	//                 https://github.com/carstenartur/sandbox/issues/453
	LOOP_V2(new LoopToFunctionalV2()),
	// ITERATOR_LOOP - Iterator-based loop conversion (from PR #449)
	// Converts while-iterator and for-loop-iterator patterns to stream operations.
	// Activated January 2026 - Phase 7: Iterator pattern support
	ITERATOR_LOOP(new IteratorLoopToFunctional()),
	
	// Bidirectional Loop Transformation Support (Phase 9)
	// New enum values for bidirectional loop transformations
	// Related issues: https://github.com/carstenartur/sandbox/issues/453
	//                 https://github.com/carstenartur/sandbox/issues/549
	
	/**
	 * Stream → Enhanced for-loop transformation.
	 * Converts: {@code collection.forEach(item -> ...)} to {@code for (T item : collection) { ... }}
	 */
	STREAM_TO_FOR(new StreamToEnhancedFor()),
	
	/**
	 * Stream → Iterator while-loop transformation.
	 * Converts: {@code collection.forEach(item -> ...)} to {@code Iterator<T> it = c.iterator(); while (it.hasNext()) { T item = it.next(); ... }}
	 */
	STREAM_TO_ITERATOR(new StreamToIteratorWhile()),
	
	/**
	 * Iterator while-loop → Enhanced for-loop transformation.
	 * Converts: {@code Iterator<T> it = c.iterator(); while (it.hasNext()) { T item = it.next(); ... }} to {@code for (T item : collection) { ... }}
	 */
	ITERATOR_TO_FOR(new IteratorWhileToEnhancedFor()),
	
	/**
	 * Enhanced for-loop → Iterator while-loop transformation.
	 * Converts: {@code for (T item : collection) { ... }} to {@code Iterator<T> it = c.iterator(); while (it.hasNext()) { T item = it.next(); ... }}
	 */
	FOR_TO_ITERATOR(new EnhancedForToIteratorWhile());

	AbstractFunctionalCall<ASTNode> functionalcall;

	@SuppressWarnings("unchecked")
	UseFunctionalCallFixCore(AbstractFunctionalCall<? extends ASTNode> explicitencoding) {
		this.functionalcall=(AbstractFunctionalCall<ASTNode>) explicitencoding;
	}

	public String getPreview(boolean i) {
		return functionalcall.getPreview(i);
	}
	/**
	 * Compute set of CompilationUnitRewriteOperation to refactor supported situations using default encoding to make use of explicit calls
	 *
	 * @param compilationUnit unit to search in
	 * @param operations set of all CompilationUnitRewriteOperations created already
	 * @param nodesprocessed list to remember nodes already processed
	 */
	public void findOperations(final CompilationUnit compilationUnit,final Set<CompilationUnitRewriteOperation> operations,final Set<ASTNode> nodesprocessed) {
		functionalcall.find(this, compilationUnit, operations, nodesprocessed);
	}

	public CompilationUnitRewriteOperation rewrite(final ASTNode visited, final org.sandbox.jdt.internal.common.ReferenceHolder<ASTNode, Object> data) {
		return new CompilationUnitRewriteOperation() {
			@Override
			public void rewriteAST(final CompilationUnitRewrite cuRewrite, final LinkedProposalModelCore linkedModel) throws CoreException {
				TextEditGroup group= createTextEditGroup(Messages.format(MultiFixMessages.FunctionalCallCleanUp_description,new Object[] {UseFunctionalCallFixCore.this.toString()}), cuRewrite);
				cuRewrite.getASTRewrite().setTargetSourceRangeComputer(computer);
				functionalcall.rewrite(UseFunctionalCallFixCore.this, visited, cuRewrite, group, data);
			}
		};
	}

	/**
	 * Creates a rewrite operation for a group of consecutive loops that should be
	 * converted to Stream.concat().
	 * 
	 * <p>Phase 8 feature: Multiple consecutive for-loops adding to the same list
	 * are converted to Stream.concat() instead of being converted individually.</p>
	 * 
	 * @param group the group of consecutive loops
	 * @return the rewrite operation for the group
	 */
	public CompilationUnitRewriteOperation rewriteConsecutiveLoops(final ConsecutiveLoopGroup group) {
		return new CompilationUnitRewriteOperation() {
			@Override
			public void rewriteAST(final CompilationUnitRewrite cuRewrite, final LinkedProposalModelCore linkedModel) throws CoreException {
				TextEditGroup editGroup = createTextEditGroup(
					Messages.format(MultiFixMessages.FunctionalCallCleanUp_description,
						new Object[] { "Stream.concat() for consecutive loops" }),
					cuRewrite);
				cuRewrite.getASTRewrite().setTargetSourceRangeComputer(computer);
				
				// Create and execute the StreamConcatRefactorer
				StreamConcatRefactorer refactorer = new StreamConcatRefactorer(
					group, 
					cuRewrite.getASTRewrite(), 
					editGroup, 
					cuRewrite
				);
				
				if (refactorer.canRefactor()) {
					refactorer.refactor();
				}
			}
		};
	}

	final static TargetSourceRangeComputer computer= new TargetSourceRangeComputer() {
		@Override
		public SourceRange computeSourceRange(final ASTNode nodeWithComment) {
			if (Boolean.TRUE.equals(nodeWithComment.getProperty(ASTNodes.UNTOUCH_COMMENT))) {
				return new SourceRange(nodeWithComment.getStartPosition(), nodeWithComment.getLength());
			}
			return super.computeSourceRange(nodeWithComment);
		}
	};
}
