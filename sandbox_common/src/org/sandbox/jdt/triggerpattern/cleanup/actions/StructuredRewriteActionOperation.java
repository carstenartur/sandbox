/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.cleanup.actions;

import java.util.Objects;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;
import org.sandbox.jdt.triggerpattern.api.StructuredRewriteAction;

/** Applies one selected ordered action sequence inside the coordinated rewrite. */
public final class StructuredRewriteActionOperation
		extends CompilationUnitRewriteOperationWithSourceRange {

	private final TransformationResult result;
	private final SemanticRewritePlan plan;

	public StructuredRewriteActionOperation(TransformationResult result,
			SemanticRewritePlan plan) {
		this.result= Objects.requireNonNull(result);
		this.plan= Objects.requireNonNull(plan);
	}

	@Override
	public void rewriteASTInternal(CompilationUnitRewrite cuRewrite,
			LinkedProposalModelCore linkedModel) throws CoreException {
		String description= result.description() == null
				? "Apply structured rewrite actions" : result.description(); //$NON-NLS-1$
		TextEditGroup group= createTextEditGroup(description, cuRewrite);
		StructuredRewriteActionContext context=
				new StructuredRewriteActionContext(result, plan, cuRewrite, group);
		StructuredRewriteActionRegistry registry= StructuredRewriteActionRegistry.getInstance();
		for (StructuredRewriteAction action : result.structuredActions()) {
			registry.execute(action, context);
		}
	}

	@Override
	public String getAdditionalInfo() {
		return result.description();
	}
}
