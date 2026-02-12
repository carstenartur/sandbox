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
import org.sandbox.jdt.internal.corext.fix.helper.ViewerSorterPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.ViewerSorterPlugin.SorterHolder;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;

public enum JfaceCleanUpFixCore {

	MONITOR(new JFacePlugin()),
	VIEWER_SORTER(new ViewerSorterPlugin());

	AbstractTool<?> jfacefound;

	JfaceCleanUpFixCore(AbstractTool<?> xmlsimplify) {
		this.jfacefound= xmlsimplify;
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

	@SuppressWarnings("unchecked")
	public <T> CompilationUnitRewriteOperationWithSourceRange rewrite(final ReferenceHolder<Integer, T> hit) {
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
	
				// Use instanceof pattern matching to handle different holder types
				if (!hit.isEmpty() && hit.values().stream().findFirst().orElse(null) instanceof MonitorHolder monitorHolder) {
					// For standalone SubProgressMonitor, use the ClassInstanceCreation node instead of minv
					if (monitorHolder.minv != null) {
						rangeComputer.addTightSourceNode(monitorHolder.minv);
					} else if (!monitorHolder.standaloneSubProgressMonitors.isEmpty()) {
						// Use the first standalone SubProgressMonitor creation
						rangeComputer.addTightSourceNode(monitorHolder.standaloneSubProgressMonitors.iterator().next());
					} else if (!monitorHolder.subProgressMonitorOnSubMonitor.isEmpty()) {
						// Use the first SubProgressMonitor on SubMonitor variable
						rangeComputer.addTightSourceNode(monitorHolder.subProgressMonitorOnSubMonitor.iterator().next());
					} else if (!monitorHolder.setofcic.isEmpty()) {
						// Use the first SubProgressMonitor creation for other cases
						rangeComputer.addTightSourceNode(monitorHolder.setofcic.iterator().next());
					}
					rewrite.setTargetSourceRangeComputer(rangeComputer);
					((AbstractTool<ReferenceHolder<Integer, MonitorHolder>>) jfacefound).rewrite(JfaceCleanUpFixCore.this, (ReferenceHolder<Integer, MonitorHolder>) hit, cuRewrite, group);
				} else if (!hit.isEmpty() && hit.values().stream().findFirst().orElse(null) instanceof SorterHolder) {
					// For SorterHolder, we don't have a single source node, so skip rangeComputer setup
					((AbstractTool<ReferenceHolder<Integer, SorterHolder>>) jfacefound).rewrite(JfaceCleanUpFixCore.this, (ReferenceHolder<Integer, SorterHolder>) hit, cuRewrite, group);
				} else {
					// Fallback for unknown types - just call rewrite without specific range computer setup
					rewrite.setTargetSourceRangeComputer(rangeComputer);
					((AbstractTool<ReferenceHolder<Integer, T>>) jfacefound).rewrite(JfaceCleanUpFixCore.this, hit, cuRewrite, group);
				}
			}
		};
	}
}
