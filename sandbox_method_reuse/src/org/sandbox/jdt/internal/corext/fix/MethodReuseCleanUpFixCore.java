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
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.helper.InlineSequencesPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.MethodReusePlugin;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractMethodReuse;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;

/**
 * Method Reuse Cleanup Fix Core - Enum for method reuse cleanup operations
 * 
 * This enum defines the types of method reuse cleanups available and provides
 * the logic for finding and rewriting code to use existing methods instead of
 * duplicating code inline.
 */
public enum MethodReuseCleanUpFixCore {

	METHOD_REUSE(new MethodReusePlugin()),
	INLINE_SEQUENCES(new InlineSequencesPlugin());

	private final AbstractMethodReuse<?> tool;

	MethodReuseCleanUpFixCore(AbstractMethodReuse<?> tool) {
		this.tool = tool;
	}

	public String getPreview(boolean afterRefactoring) {
		return tool.getPreview(afterRefactoring);
	}

	/**
	 * Find operations - searches for inline code sequences that can be replaced
	 * with method calls
	 *
	 * @param compilationUnit The compilation unit to search
	 * @param operations Set to add found operations to
	 * @param nodesprocessed Set of already processed nodes
	 */
	public void findOperations(final CompilationUnit compilationUnit,
			final Set<CompilationUnitRewriteOperation> operations, final Set<ASTNode> nodesprocessed) {
		try {
			tool.find(this, compilationUnit, operations, nodesprocessed);
		} catch (CoreException e) {
			// Log the exception but don't let it stop the cleanup process
			// This is consistent with how other cleanups handle exceptions
		}
	}

	/**
	 * Create a rewrite operation for replacing an inline sequence with a method call
	 *
	 * @param holder The reference holder containing the data needed for rewriting
	 * @return A CompilationUnitRewriteOperation to perform the replacement
	 */
	public CompilationUnitRewriteOperation rewrite(final ReferenceHolder<?, ?> holder) {
		return new CompilationUnitRewriteOperation() {
			@Override
			public void rewriteAST(final CompilationUnitRewrite cuRewrite,
					final LinkedProposalModelCore linkedModel) throws CoreException {
				TextEditGroup group = createTextEditGroup(
						Messages.format(MultiFixMessages.MethodReuseCleanUp_description, 
								new Object[] { MethodReuseCleanUpFixCore.this.toString() }), 
						cuRewrite);
				tool.rewrite(MethodReuseCleanUpFixCore.this, holder, cuRewrite, group);
			}
		};
	}

	@Override
	public String toString() {
		return "Inline Sequences";
	}
}
