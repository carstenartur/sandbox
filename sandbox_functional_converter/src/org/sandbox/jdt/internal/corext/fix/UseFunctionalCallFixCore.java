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
import org.sandbox.jdt.internal.corext.fix.helper.IteratorLoopToFunctional;
import org.sandbox.jdt.internal.corext.fix.helper.LoopToFunctional;
import org.sandbox.jdt.internal.corext.fix.helper.LoopToFunctionalV2;
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
	LOOP_V2(new LoopToFunctionalV2());

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
