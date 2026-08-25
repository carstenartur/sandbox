/*******************************************************************************
 * Copyright (c) 2025, 2026 Carsten Hammer.
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
import org.eclipse.jdt.internal.core.manipulation.JavaManipulationPlugin;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.helper.InlineSequencesPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractMethodReuse;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;

/** Operation registry for replacing an inline sequence with an existing method call. */
public enum MethodReuseCleanUpFixCore {

	INLINE_SEQUENCES(new InlineSequencesPlugin());

	private final AbstractMethodReuse<?> tool;

	MethodReuseCleanUpFixCore(AbstractMethodReuse<?> tool) {
		this.tool= tool;
	}

	public String getPreview(boolean afterRefactoring) {
		return tool.getPreview(afterRefactoring);
	}

	/** Adds every supported existing-method replacement operation. */
	public void findOperations(CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesProcessed) {
		try {
			tool.find(this, compilationUnit, operations, nodesProcessed);
		} catch (CoreException exception) {
			JavaManipulationPlugin.log(exception);
		}
	}

	/** Creates the local operation for one already validated existing-method match. */
	public CompilationUnitRewriteOperation rewrite(ReferenceHolder<?, ?> holder) {
		return new CompilationUnitRewriteOperation() {
			@Override
			public void rewriteAST(CompilationUnitRewrite cuRewrite,
					LinkedProposalModelCore linkedModel) throws CoreException {
				TextEditGroup group= createTextEditGroup(
						Messages.format(MultiFixMessages.MethodReuseCleanUp_description,
								new Object[] { MethodReuseCleanUpFixCore.this.toString() }),
						cuRewrite);
				tool.rewrite(MethodReuseCleanUpFixCore.this, holder, cuRewrite, group);
			}
		};
	}

	@Override
	public String toString() {
		return "Inline Sequences"; //$NON-NLS-1$
	}
}
