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
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.TightSourceRangeComputer;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.helper.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.JFacePlugin;
import org.sandbox.jdt.internal.corext.fix.helper.JFacePlugin.MonitorHolder;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;

public enum JfaceCleanUpFixCore {

	MONITOR(new JFacePlugin());

	AbstractTool<ReferenceHolder<Integer, MonitorHolder>> jfacefound;

	@SuppressWarnings("unchecked")
	JfaceCleanUpFixCore(AbstractTool<? extends ReferenceHolder<Integer, MonitorHolder>> xmlsimplify) {
		this.jfacefound= (AbstractTool<ReferenceHolder<Integer, MonitorHolder>>) xmlsimplify;
	}

	public String getPreview(boolean i) {
		return jfacefound.getPreview(i);
	}

	/**
	 * Compute set of CompilationUnitRewriteOperation to refactor supported
	 * situations
	 *
	 * @param compilationUnit        unit to search in
	 * @param operations             set of all CompilationUnitRewriteOperations
	 *                               created already
	 * @param nodesprocessed         list to remember nodes already processed
	 * @param createForOnlyIfVarUsed true if for loop should be created only only if
	 *                               loop var used within
	 */
	public void findOperations(final CompilationUnit compilationUnit,
			final Set<CompilationUnitRewriteOperationWithSourceRange> operations, final Set<ASTNode> nodesprocessed,
			boolean createForOnlyIfVarUsed) {
		jfacefound.find(this, compilationUnit, operations, nodesprocessed, createForOnlyIfVarUsed);
	}

	public CompilationUnitRewriteOperationWithSourceRange rewrite(final ReferenceHolder<Integer, MonitorHolder> hit) {
		return new CompilationUnitRewriteOperationWithSourceRange() {
			@Override
			public void rewriteASTInternal(final CompilationUnitRewrite cuRewrite, final LinkedProposalModelCore linkedModel)
					throws CoreException {
				TextEditGroup group= createTextEditGroup(MultiFixMessages.JFaceCleanUp_description, cuRewrite);
				TightSourceRangeComputer rangeComputer;
				ASTRewrite rewrite= cuRewrite.getASTRewrite();
				if (rewrite.getExtendedSourceRangeComputer() instanceof TightSourceRangeComputer) {
					rangeComputer= (TightSourceRangeComputer) rewrite.getExtendedSourceRangeComputer();
				} else {
					rangeComputer= new TightSourceRangeComputer();
				}
				
				MonitorHolder mh = hit.get(0);
				// For standalone SubProgressMonitor, use the ClassInstanceCreation node instead of minv
				if (mh.minv != null) {
					rangeComputer.addTightSourceNode(mh.minv);
				} else if (!mh.setofcic.isEmpty()) {
					// Use the first SubProgressMonitor creation for standalone case
					rangeComputer.addTightSourceNode(mh.setofcic.iterator().next());
				}
				
				rewrite.setTargetSourceRangeComputer(rangeComputer);
				jfacefound.rewrite(JfaceCleanUpFixCore.this, hit, cuRewrite, group);
			}
		};
	}
}
