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
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.TightSourceRangeComputer;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.ShiftOutOfRangeHelper;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;

/**
 * Enum containing shift out of range transformation types.
 */
public enum ShiftOutOfRangeFixCore {
	/**
	 * Replace out-of-range shift amounts with the masked effective value.
	 */
	SHIFT_AMOUNT(new ShiftOutOfRangeHelper());

	ShiftOutOfRangeHelper shiftHelper;

	ShiftOutOfRangeFixCore(ShiftOutOfRangeHelper helper) {
		this.shiftHelper = helper;
	}

	public String getPreview(boolean enabled) {
		return shiftHelper.getPreview(enabled);
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
		shiftHelper.find(this, compilationUnit, operations, nodesProcessed);
	}

	public CompilationUnitRewriteOperationWithSourceRange rewrite(final InfixExpression visited, final long maskedValue) {
		return new CompilationUnitRewriteOperationWithSourceRange() {
			@Override
			public void rewriteASTInternal(final CompilationUnitRewrite cuRewrite, final LinkedProposalModelCore linkedModel)
					throws CoreException {
				TextEditGroup group = createTextEditGroup(MultiFixMessages.ShiftOutOfRangeCleanUp_description, cuRewrite);
				TightSourceRangeComputer rangeComputer;
				ASTRewrite rewrite = cuRewrite.getASTRewrite();
				if (rewrite.getExtendedSourceRangeComputer() instanceof TightSourceRangeComputer) {
					rangeComputer = (TightSourceRangeComputer) rewrite.getExtendedSourceRangeComputer();
				} else {
					rangeComputer = new TightSourceRangeComputer();
				}
				rangeComputer.addTightSourceNode(visited);
				rewrite.setTargetSourceRangeComputer(rangeComputer);
				shiftHelper.rewrite(ShiftOutOfRangeFixCore.this, visited, cuRewrite, group, maskedValue);
			}
		};
	}

	@Override
	public String toString() {
		return "Replace out-of-range shift amount";
	}
}
