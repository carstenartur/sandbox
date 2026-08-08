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
import org.sandbox.jdt.internal.corext.fix.helper.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.IntToEnumHelper;
import org.sandbox.jdt.internal.corext.fix.helper.IntToEnumHelper.IntConstantHolder;
import org.sandbox.jdt.internal.corext.fix.helper.SwitchIntToEnumHelper;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;

/**
 * Enum containing different types of int to enum transformations.
 */
public enum IntToEnumFixCore {
	/**
	 * Convert if-else chains using int constants to switch with enum.
	 */
	IF_ELSE_TO_SWITCH(new IntToEnumHelper()),

	/**
	 * Convert switch statements using int constants to switch with enum.
	 */
	SWITCH_INT_TO_ENUM(new SwitchIntToEnumHelper());

	AbstractTool<ReferenceHolder<Integer, IntConstantHolder>> intToEnumHelper;

	@SuppressWarnings("unchecked")
	IntToEnumFixCore(AbstractTool<? extends ReferenceHolder<Integer, IntConstantHolder>> helper) {
		this.intToEnumHelper = (AbstractTool<ReferenceHolder<Integer, IntConstantHolder>>) helper;
	}

	public String getPreview(boolean enabled) {
		return intToEnumHelper.getPreview(enabled);
	}

	/**
	 * Find operations for this transformation type.
	 * 
	 * @param compilationUnit The compilation unit to search
	 * @param operations Set to add operations to
	 * @param nodesProcessed Set of already processed nodes
	 */
	public void findOperations(final CompilationUnit compilationUnit,
			final Set<CompilationUnitRewriteOperationWithSourceRange> operations, 
			final Set<ASTNode> nodesProcessed) {
		intToEnumHelper.find(this, compilationUnit, operations, nodesProcessed);
	}

	public CompilationUnitRewriteOperationWithSourceRange rewrite(final ReferenceHolder<Integer, IntConstantHolder> hit) {
		return new CompilationUnitRewriteOperationWithSourceRange() {
			@Override
			public void rewriteASTInternal(final CompilationUnitRewrite cuRewrite, final LinkedProposalModelCore linkedModel)
					throws CoreException {
				TextEditGroup group = createTextEditGroup(MultiFixMessages.IntToEnumCleanUp_description, cuRewrite);
				TightSourceRangeComputer rangeComputer;
				ASTRewrite rewrite = cuRewrite.getASTRewrite();
				if (rewrite.getExtendedSourceRangeComputer() instanceof TightSourceRangeComputer) {
					rangeComputer = (TightSourceRangeComputer) rewrite.getExtendedSourceRangeComputer();
				} else {
					rangeComputer = new TightSourceRangeComputer();
				}
				
				// Get the first IntConstantHolder from the hit map
				IntConstantHolder holder = hit.values().stream().findFirst().orElse(null);
				if (holder != null) {
					if (holder.switchStatement != null) {
						rangeComputer.addTightSourceNode(holder.switchStatement);
					}
					if (holder.ifStatement != null) {
						rangeComputer.addTightSourceNode(holder.ifStatement);
					}
				}
				
				rewrite.setTargetSourceRangeComputer(rangeComputer);
				intToEnumHelper.rewrite(IntToEnumFixCore.this, hit, cuRewrite, group);
			}
		};
	}

	@Override
	public String toString() {
		return name();
	}
}
