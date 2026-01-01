/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.helper.AbstractMethodReuse;
import org.sandbox.jdt.internal.corext.fix.helper.InlineSequencesPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.MethodReusePlugin;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;

/**
 * Enum-based fix core for method reuse detection and refactoring.
 * 
 * This enum defines the different types of method reuse fixes that can be applied:
 * <ul>
 *   <li>{@link #METHOD_REUSE} - General method similarity detection</li>
 *   <li>{@link #INLINE_SEQUENCES} - Inline code sequence replacement</li>
 * </ul>
 * 
 * Each enum value is associated with a plugin that implements the specific
 * detection and refactoring logic.
 */
public enum MethodReuseCleanUpFixCore {

	/**
	 * General method reuse detection - finds similar methods that could be refactored
	 * to share common code.
	 */
	METHOD_REUSE(new MethodReusePlugin()),
	
	/**
	 * Inline sequence detection - finds inline code sequences that match method bodies
	 * and can be replaced with method calls.
	 */
	INLINE_SEQUENCES(new InlineSequencesPlugin());

	AbstractMethodReuse<ASTNode> tool;

	@SuppressWarnings("unchecked")
	MethodReuseCleanUpFixCore(AbstractMethodReuse<? extends ASTNode> tool) {
		this.tool = (AbstractMethodReuse<ASTNode>) tool;
	}

	/**
	 * Get preview text for this fix.
	 * 
	 * @param enabled true if the fix is enabled, false otherwise
	 * @return preview string showing before/after code
	 */
	public String getPreview(boolean enabled) {
		return tool.getPreview(enabled);
	}

	/**
	 * Find all operations for this fix in the compilation unit.
	 * 
	 * @param compilationUnit the compilation unit to search
	 * @param operations set to collect the rewrite operations
	 * @param nodesprocessed set of already processed nodes to avoid duplicates
	 */
	public void findOperations(CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations,
			Set<ASTNode> nodesprocessed) {
		tool.find(this, compilationUnit, operations, nodesprocessed);
	}

	/**
	 * Create a rewrite operation for the given visited node.
	 * 
	 * @param visited the AST node to rewrite
	 * @param data holder containing node-specific data for the rewrite
	 * @return the rewrite operation
	 */
	public CompilationUnitRewriteOperation rewrite(final ASTNode visited, ReferenceHolder<ASTNode, Object> data) {
		return new CompilationUnitRewriteOperation() {
			@Override
			public void rewriteAST(final CompilationUnitRewrite cuRewrite, final LinkedProposalModelCore linkedModel)
					throws CoreException {
				TextEditGroup group = createTextEditGroup(
						Messages.format(MultiFixMessages.MethodReuseCleanUp_description,
								new Object[] { MethodReuseCleanUpFixCore.this.toString() }),
						cuRewrite);
				cuRewrite.getASTRewrite().setTargetSourceRangeComputer(computer);
				tool.rewrite(MethodReuseCleanUpFixCore.this, visited, cuRewrite, group, data);
			}
		};
	}

	final static TargetSourceRangeComputer computer = new TargetSourceRangeComputer() {
		@Override
		public SourceRange computeSourceRange(final ASTNode nodeWithComment) {
			if (Boolean.TRUE.equals(nodeWithComment.getProperty(ASTNodes.UNTOUCH_COMMENT))) {
				return new SourceRange(nodeWithComment.getStartPosition(), nodeWithComment.getLength());
			}
			return super.computeSourceRange(nodeWithComment);
		}
	};

	@Override
	public String toString() {
		return tool.toString();
	}
}
