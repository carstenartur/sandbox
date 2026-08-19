/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/.
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
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;

import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.cleanup.multifile.ContainerLocalRewriteResolver.AppendPair;
import org.sandbox.jdt.cleanup.multifile.ContainerLocalRewriteResolver.ResolvedLength;
import org.sandbox.jdt.cleanup.multifile.ContainerLocalRewriteResolver.ResolvedPlan;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan;

/** Applies the first strictly local append-array to list rewrite. */
public final class ContainerLocalRewriteFix {

	private static final String DESCRIPTION= "Convert local append array to list"; //$NON-NLS-1$

	private ContainerLocalRewriteFix() {
	}

	/**
	 * Revalidates the immutable plan against the current AST and creates one ordinary
	 * local cleanup fix.
	 */
	public static ICleanUpFix create(
			ICompilationUnit unit,
			org.eclipse.jdt.core.dom.CompilationUnit root,
			ContainerLocalRewritePlan plan) throws CoreException {
		return new CompilationUnitRewriteOperationsFixCore(
				DESCRIPTION,
				root,
				new CompilationUnitRewriteOperationWithSourceRange[] {
						operation(unit, root, plan) });
	}

	/** Package-visible operation factory used by the aggregate container cleanup. */
	static CompilationUnitRewriteOperationWithSourceRange operation(
			ICompilationUnit unit,
			org.eclipse.jdt.core.dom.CompilationUnit root,
			ContainerLocalRewritePlan plan) throws CoreException {
		return new RewriteOperation(resolve(unit, root, plan));
	}

	/** Package-visible semantic resolution used by the cleanup adapter. */
	static ResolvedPlan resolve(
			ICompilationUnit unit,
			org.eclipse.jdt.core.dom.CompilationUnit root,
			ContainerLocalRewritePlan plan) throws CoreException {
		ContainerLocalArgumentTransferVerifier.verify(unit, root, plan);
		ContainerLocalRewritePlanVerifier.verifyEncounterIterations(unit, root, plan);
		return ContainerLocalRewriteResolver.resolve(unit, root, plan);
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
			String listName= imports.addImport(resolved.plan().targetInterfaceType());
			SimpleType listRawType= ast.newSimpleType(ast.newName(listName));
			ParameterizedType listType= ast.newParameterizedType(listRawType);
			listType.typeArguments().add(ASTNode.copySubtree(ast, componentType));
			rewrite.replace(resolved.declaration().getType(), listType, group);

			String implementationName=
					imports.addImport(resolved.plan().targetImplementationType());
			org.eclipse.jdt.core.dom.ClassInstanceCreation creation=
					ast.newClassInstanceCreation();
			ParameterizedType implementationType= ast.newParameterizedType(
					ast.newSimpleType(ast.newName(implementationName)));
			creation.setType(implementationType);
			rewrite.replace(resolved.initializer(), creation, group);

			for (AppendPair pair : resolved.appendPairs()) {
				rewrite.remove(pair.growthStatement(), group);
				MethodInvocation add= ast.newMethodInvocation();
				add.setExpression((Expression) ASTNode.copySubtree(
						ast, pair.arrayAccess().getArray()));
				add.setName(ast.newSimpleName("add")); //$NON-NLS-1$
				add.arguments().add(ASTNode.copySubtree(ast, pair.value()));
				rewrite.replace(pair.appendAssignment(), add, group);
			}

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
