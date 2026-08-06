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
import org.eclipse.jdt.core.dom.Expression;
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

import org.sandbox.jdt.cleanup.multifile.ContainerParameterRewriteResolver.ResolvedLength;
import org.sandbox.jdt.cleanup.multifile.ContainerParameterRewriteResolver.ResolvedPlan;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan;

/** Applies one strictly validated closed-source array-parameter to list rewrite. */
public final class ContainerParameterRewriteFix {

	private static final String DESCRIPTION= "Convert closed array parameter to list"; //$NON-NLS-1$

	private ContainerParameterRewriteFix() {
	}

	/** Revalidates the immutable plan and creates one ordinary local cleanup fix. */
	public static ICleanUpFix create(
			ICompilationUnit unit,
			org.eclipse.jdt.core.dom.CompilationUnit root,
			ContainerParameterRewritePlan plan) throws CoreException {
		ResolvedPlan resolved= ContainerParameterRewriteResolver.resolve(unit, root, plan);
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

			Type componentType= resolved.arrayType().getElementType();
			String interfaceName= imports.addImport(
					resolved.plan().targetInterfaceType());
			ParameterizedType listType= ast.newParameterizedType(
					ast.newSimpleType(ast.newName(interfaceName)));
			listType.typeArguments().add(ASTNode.copySubtree(ast, componentType));
			rewrite.replace(resolved.parameter().getType(), listType, group);

			for (ResolvedLength length : resolved.lengths()) {
				MethodInvocation size= ast.newMethodInvocation();
				size.setExpression((Expression) ASTNode.copySubtree(
						ast, length.arrayExpression()));
				size.setName(ast.newSimpleName("size")); //$NON-NLS-1$
				rewrite.replace(length.expression(), size, group);
			}
		}

		@Override
		public String getAdditionalInfo() {
			return DESCRIPTION;
		}
	}
}
