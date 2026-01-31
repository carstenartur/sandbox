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
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.rewrite.TargetSourceRangeComputer;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.helper.MethodReuseFinder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractMethodReuse;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;

public enum MethodReuseFixCore {

	MethodReuse(new MethodReuseFinder());

	AbstractMethodReuse<ASTNode> methodreuse;

	@SuppressWarnings("unchecked")
	MethodReuseFixCore(AbstractMethodReuse<? extends ASTNode> methodreuse) {
		this.methodreuse= (AbstractMethodReuse<ASTNode>) methodreuse;
	}

	public String getPreview(boolean i) {
		return methodreuse.getPreview(i);
	}

	/**
	 * Compute set of CompilationUnitRewriteOperation to refactor supported
	 * situations using platform status instantiation
	 *
	 * @param compilationUnit unit to search in
	 * @param operations      set of all CompilationUnitRewriteOperations created
	 *                        already
	 * @param nodesprocessed  list to remember nodes already processed
	 * @throws CoreException
	 */
	public void findOperations(final CompilationUnit compilationUnit,
			final Set<CompilationUnitRewriteOperationWithSourceRange> operations, final Set<ASTNode> nodesprocessed)
					throws CoreException {
		methodreuse.find(this, compilationUnit, operations, nodesprocessed);
	}

	public CompilationUnitRewriteOperationWithSourceRange rewrite(final MethodDeclaration visited, ReferenceHolder<ASTNode, Object> holder) {
		return new CompilationUnitRewriteOperationWithSourceRange() {
			@Override
			public void rewriteASTInternal(final CompilationUnitRewrite cuRewrite, final LinkedProposalModelCore linkedModel)
					throws CoreException {
				TextEditGroup group= createTextEditGroup(
						Messages.format(MultiFixMessages.MethodReuseCleanUp_description,
								new Object[] { MethodReuseFixCore.this.toString() }),
						cuRewrite);
				cuRewrite.getASTRewrite().setTargetSourceRangeComputer(computer);
				methodreuse.rewrite(MethodReuseFixCore.this, visited, cuRewrite, group, holder);
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
