/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

import java.util.Objects;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;

import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.cleanup.multifile.UniqueSequenceLocalRewriteResolver.ResolvedPlan;
import org.sandbox.jdt.container.analysis.UniqueSequencePattern.GuardedAdd;
import org.sandbox.jdt.container.api.UniqueSequenceLocalRewritePlan;

/** Applies a strictly local manually-unique sequence to ordered-set rewrite. */
public final class UniqueSequenceLocalRewriteFix {

	private static final String DESCRIPTION=
			"Convert local manually unique sequence to ordered set"; //$NON-NLS-1$

	private UniqueSequenceLocalRewriteFix() {
	}

	/** Revalidates the plan and creates one ordinary local cleanup fix. */
	public static ICleanUpFix create(
			ICompilationUnit unit,
			org.eclipse.jdt.core.dom.CompilationUnit root,
			UniqueSequenceLocalRewritePlan plan) throws CoreException {
		ResolvedPlan resolved= UniqueSequenceLocalRewriteResolver.resolve(unit, root, plan);
		return new CompilationUnitRewriteOperationsFixCore(
				DESCRIPTION,
				root,
				new CompilationUnitRewriteOperationWithSourceRange[] {
						new RewriteOperation(resolved) });
	}

	private static final class RewriteOperation
			extends CompilationUnitRewriteOperationWithSourceRange {

		private final ResolvedPlan resolved;

		RewriteOperation(ResolvedPlan resolved) {
			this.resolved= Objects.requireNonNull(resolved, "resolved"); //$NON-NLS-1$
		}

		@Override
		public void rewriteASTInternal(
				CompilationUnitRewrite cuRewrite,
				LinkedProposalModelCore linkedModel) throws CoreException {
			AST ast= cuRewrite.getRoot().getAST();
			ASTRewrite rewrite= cuRewrite.getASTRewrite();
			ImportRewrite imports= cuRewrite.getImportRewrite();
			TextEditGroup group= createTextEditGroup(DESCRIPTION, cuRewrite);

			Type elementType= (Type) resolved.declarationType().typeArguments().get(0);
			String interfaceName= imports.addImport(
					resolved.plan().targetInterfaceType());
			ParameterizedType setType= ast.newParameterizedType(
					ast.newSimpleType(ast.newName(interfaceName)));
			setType.typeArguments().add(ASTNode.copySubtree(ast, elementType));
			rewrite.replace(resolved.declaration().getType(), setType, group);

			String implementationName= imports.addImport(
					resolved.plan().targetImplementationType());
			ClassInstanceCreation creation= ast.newClassInstanceCreation();
			ParameterizedType implementationType= ast.newParameterizedType(
					ast.newSimpleType(ast.newName(implementationName)));
			creation.setType(implementationType);
			rewrite.replace(resolved.initializer(), creation, group);

			for (GuardedAdd guard : resolved.guards()) {
				MethodInvocation add= (MethodInvocation) ASTNode.copySubtree(
						ast, guard.add());
				ExpressionStatement replacement= ast.newExpressionStatement(add);
				rewrite.replace(guard.statement(), replacement, group);
			}
		}

		@Override
		public String getAdditionalInfo() {
			return DESCRIPTION;
		}
	}
}
