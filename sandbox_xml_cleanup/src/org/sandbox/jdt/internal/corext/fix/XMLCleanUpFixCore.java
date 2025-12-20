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
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.TightSourceRangeComputer;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.XMLCandidateHit;
import org.sandbox.jdt.internal.corext.fix.helper.XMLPlugin;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;

public enum XMLCleanUpFixCore {

	ECLIPSEPLUGIN(new XMLPlugin());

	AbstractTool<XMLCandidateHit> xmlfound;

	@SuppressWarnings("unchecked")
	XMLCleanUpFixCore(AbstractTool<? extends XMLCandidateHit> xmlsimplify) {
		this.xmlfound= (AbstractTool<XMLCandidateHit>) xmlsimplify;
	}
	
	/**
	 * Set whether to enable indentation for XML cleanup.
	 * 
	 * @param enable true to enable indentation, false for compact output (default)
	 */
	public static void setEnableIndent(boolean enable) {
		// Configure all XMLPlugin instances
		for (XMLCleanUpFixCore fixCore : XMLCleanUpFixCore.values()) {
			if (fixCore.xmlfound instanceof XMLPlugin) {
				((XMLPlugin) fixCore.xmlfound).setEnableIndent(enable);
			}
		}
	}

	public String getPreview(boolean i) {
		return xmlfound.getPreview(i);
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
			final Set<CompilationUnitRewriteOperation> operations, final Set<ASTNode> nodesprocessed,
			boolean createForOnlyIfVarUsed) {
		xmlfound.find(this, compilationUnit, operations, nodesprocessed, createForOnlyIfVarUsed);
	}

	public CompilationUnitRewriteOperation rewrite(final XMLCandidateHit hit) {
		return new CompilationUnitRewriteOperation() {
			@Override
			public void rewriteAST(final CompilationUnitRewrite cuRewrite, final LinkedProposalModelCore linkedModel)
					throws CoreException {
				TextEditGroup group= createTextEditGroup(MultiFixMessages.XMLCleanUp_description, cuRewrite);
				TightSourceRangeComputer rangeComputer;
				ASTRewrite rewrite= cuRewrite.getASTRewrite();
				if (rewrite.getExtendedSourceRangeComputer() instanceof TightSourceRangeComputer) {
					rangeComputer= (TightSourceRangeComputer) rewrite.getExtendedSourceRangeComputer();
				} else {
					rangeComputer= new TightSourceRangeComputer();
				}
				if (hit.whileStatement != null) {
					rangeComputer.addTightSourceNode(hit.whileStatement);
				}
				rewrite.setTargetSourceRangeComputer(rangeComputer);
				xmlfound.rewrite(XMLCleanUpFixCore.this, hit, cuRewrite, group);
			}
		};
	}
}
