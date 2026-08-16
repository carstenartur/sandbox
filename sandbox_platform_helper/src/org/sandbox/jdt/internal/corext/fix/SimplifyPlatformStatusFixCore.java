/*******************************************************************************
 * Copyright (c) 2021, 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix;

import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.TargetSourceRangeComputer;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.helper.AbstractSimplifyPlatformStatus;
import org.sandbox.jdt.internal.corext.fix.helper.MultiStatusSimplifyPlatformStatus;
import org.sandbox.jdt.internal.corext.fix.helper.StatusErrorSimplifyPlatformStatus;
import org.sandbox.jdt.internal.corext.fix.helper.StatusInfoSimplifyPlatformStatus;
import org.sandbox.jdt.internal.corext.fix.helper.StatusWarningSimplifyPlatformStatus;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;

/** Supported semantics-preserving Platform Status rewrite families. */
public enum SimplifyPlatformStatusFixCore {

	STATUSWARNING(new StatusWarningSimplifyPlatformStatus()),
	STATUSERROR(new StatusErrorSimplifyPlatformStatus()),
	STATUSINFO(new StatusInfoSimplifyPlatformStatus()),
	MULTISTATUS(new MultiStatusSimplifyPlatformStatus());

	private final AbstractSimplifyPlatformStatus platformStatus;

	SimplifyPlatformStatusFixCore(AbstractSimplifyPlatformStatus platformStatus) {
		this.platformStatus= platformStatus;
	}

	public String getPreview(boolean enabled) {
		return platformStatus.getPreview(enabled);
	}

	/** Finds supported rewrites while sharing one processed-node set. */
	public void findOperations(final CompilationUnit compilationUnit,
			final Set<CompilationUnitRewriteOperationWithSourceRange> operations,
			final Set<ASTNode> nodesProcessed) throws CoreException {
		platformStatus.find(this, compilationUnit, operations, nodesProcessed);
	}

	/** Creates the comment-preserving rewrite for one accepted constructor. */
	public CompilationUnitRewriteOperationWithSourceRange rewrite(final ClassInstanceCreation visited,
			ReferenceHolder<ASTNode, Object> holder) {
		return new CompilationUnitRewriteOperationWithSourceRange() {
			@Override
			public void rewriteASTInternal(final CompilationUnitRewrite cuRewrite,
					final LinkedProposalModelCore linkedModel) throws CoreException {
				TextEditGroup group= createTextEditGroup(
						Messages.format(MultiFixMessages.PlatformStatusCleanUp_description,
								new Object[] { SimplifyPlatformStatusFixCore.this.toString() }),
						cuRewrite);
				cuRewrite.getASTRewrite().setTargetSourceRangeComputer(COMPUTER);
				platformStatus.rewrite(SimplifyPlatformStatusFixCore.this, visited, cuRewrite, group, holder);
				if (SimplifyPlatformStatusFixCore.this != MULTISTATUS) {
					cuRewrite.getImportRemover().registerAddedImport(Status.class.getName());
				}
			}
		};
	}

	private static final TargetSourceRangeComputer COMPUTER= new TargetSourceRangeComputer() {
		@Override
		public SourceRange computeSourceRange(final ASTNode nodeWithComment) {
			if (Boolean.TRUE.equals(nodeWithComment.getProperty(ASTNodes.UNTOUCH_COMMENT))) {
				return new SourceRange(nodeWithComment.getStartPosition(), nodeWithComment.getLength());
			}
			return super.computeSourceRange(nodeWithComment);
		}
	};
}
