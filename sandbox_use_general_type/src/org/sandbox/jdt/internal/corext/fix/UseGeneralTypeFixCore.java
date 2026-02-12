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
package org.sandbox.jdt.internal.corext.fix;

import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.TightSourceRangeComputer;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.helper.UseGeneralTypePlugin;
import org.sandbox.jdt.internal.corext.fix.helper.UseGeneralTypePlugin.TypeWidenHolder;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;

public enum UseGeneralTypeFixCore {

	USE_GENERAL_TYPE(new UseGeneralTypePlugin());

	UseGeneralTypePlugin tool;

	UseGeneralTypeFixCore(UseGeneralTypePlugin tool) {
		this.tool= tool;
	}

	public String getPreview(boolean i) {
		return tool.getPreview(i);
	}

	/**
	 * Compute set of CompilationUnitRewriteOperation to refactor supported
	 * situations
	 *
	 * @param compilationUnit        unit to search in
	 * @param operations             set of all CompilationUnitRewriteOperations
	 *                               created already
	 * @param nodesprocessed         list to remember nodes already processed
	 * @param createForOnlyIfVarUsed true if for loop should be created only if
	 *                               loop var used within
	 */
	public void findOperations(final CompilationUnit compilationUnit,
			final Set<CompilationUnitRewriteOperationWithSourceRange> operations, final Set<ASTNode> nodesprocessed,
			boolean createForOnlyIfVarUsed) {
		tool.find(this, compilationUnit, operations, nodesprocessed, createForOnlyIfVarUsed);
	}

	public CompilationUnitRewriteOperationWithSourceRange rewrite(final ReferenceHolder<Integer, TypeWidenHolder> hit) {
		return new CompilationUnitRewriteOperationWithSourceRange() {
			@Override
			public void rewriteASTInternal(final CompilationUnitRewrite cuRewrite, final LinkedProposalModelCore linkedModel)
					throws CoreException {
				TextEditGroup group= createTextEditGroup(MultiFixMessages.UseGeneralTypeCleanUp_description, cuRewrite);
				TightSourceRangeComputer rangeComputer;
				ASTRewrite rewrite= cuRewrite.getASTRewrite();
				if (rewrite.getExtendedSourceRangeComputer() instanceof TightSourceRangeComputer) {
					rangeComputer= (TightSourceRangeComputer) rewrite.getExtendedSourceRangeComputer();
				} else {
					rangeComputer= new TightSourceRangeComputer();
				}
	
				if (!hit.isEmpty()) {
					TypeWidenHolder holder = hit.values().stream().findFirst().orElse(null);
					if (holder != null && holder.variableDeclarationStatement != null) {
						rangeComputer.addTightSourceNode(holder.variableDeclarationStatement);
					}
					rewrite.setTargetSourceRangeComputer(rangeComputer);
					tool.rewrite(UseGeneralTypeFixCore.this, hit, cuRewrite, group);
				}
			}
		};
	}
}
