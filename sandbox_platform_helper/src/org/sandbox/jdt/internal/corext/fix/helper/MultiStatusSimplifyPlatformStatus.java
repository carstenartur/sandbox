/*******************************************************************************
 * Copyright (c) 2025, 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.SimplifyPlatformStatusFixCore;

/**
 * Replaces a compile-time integer value of {@code 0} with the named
 * {@link IStatus#OK} constant in a {@link MultiStatus} constructor.
 *
 * <p>Application-specific, nonzero or unresolved status codes are deliberately
 * left unchanged. The cleanup must never normalize an arbitrary domain code to
 * {@code IStatus.OK}.</p>
 */
public class MultiStatusSimplifyPlatformStatus extends AbstractSimplifyPlatformStatus {

	private static final String OK_SIMPLE_NAME= "OK"; //$NON-NLS-1$

	public MultiStatusSimplifyPlatformStatus() {
		super(IStatus.OK);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "MultiStatus status = new MultiStatus(pluginId, IStatus.OK, \"message\", null);\n"; //$NON-NLS-1$
		}
		return "MultiStatus status = new MultiStatus(pluginId, 0, \"message\", null);\n"; //$NON-NLS-1$
	}

	@Override
	public void find(SimplifyPlatformStatusFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesProcessed)
			throws CoreException {
		try {
			ReferenceHolder<ASTNode, Object> holder= ReferenceHolder.createForNodes();
			HelperVisitorFactory.forClassInstanceCreation(MultiStatus.class)
				.in(compilationUnit)
				.excluding(nodesProcessed)
				.processEach(holder, (visited, data) -> {
					if (nodesProcessed.contains(visited) || visited.arguments().size() != 4) {
						return false;
					}

					ITypeBinding typeBinding= visited.resolveTypeBinding();
					if (typeBinding == null
							|| !MultiStatus.class.getName().equals(typeBinding.getErasure().getQualifiedName())) {
						return false;
					}

					List<Expression> arguments= visited.arguments();
					Expression codeArgument= arguments.get(1);
					if (!hasConstantIntValue(codeArgument, IStatus.OK)
							|| isCanonicalIStatusOkReference(codeArgument)) {
						return false;
					}

					operations.add(fixcore.rewrite(visited, data));
					nodesProcessed.add(visited);
					return false;
				});
		} catch (Exception exception) {
			throw new CoreException(Status.error("Problem while finding MultiStatus simplifications", exception)); //$NON-NLS-1$
		}
	}

	private static boolean isCanonicalIStatusOkReference(Expression expression) {
		IBinding binding= null;
		if (expression instanceof QualifiedName qualifiedName) {
			binding= qualifiedName.resolveBinding();
		} else if (expression instanceof SimpleName simpleName) {
			binding= simpleName.resolveBinding();
		}
		if (!(binding instanceof IVariableBinding variableBinding)
				|| !variableBinding.isField()
				|| !OK_SIMPLE_NAME.equals(variableBinding.getName())) {
			return false;
		}
		ITypeBinding declaringClass= variableBinding.getDeclaringClass();
		return declaringClass != null
				&& IStatus.class.getName().equals(declaringClass.getErasure().getQualifiedName());
	}

	@Override
	public void rewrite(SimplifyPlatformStatusFixCore cleanup, final ClassInstanceCreation visited,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group,
			ReferenceHolder<ASTNode, Object> holder) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ImportRemover remover= cuRewrite.getImportRemover();

		ClassInstanceCreation newMultiStatus= ast.newClassInstanceCreation();
		Name multiStatusName= addImport(MultiStatus.class.getName(), cuRewrite, ast);
		newMultiStatus.setType(ast.newSimpleType(multiStatusName));
		Name iStatusName= addImport(IStatus.class.getName(), cuRewrite, ast);

		List<Expression> arguments= visited.arguments();
		List<Expression> newArguments= newMultiStatus.arguments();
		newArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(arguments.get(0))));
		QualifiedName okConstant= ast.newQualifiedName(iStatusName, ast.newSimpleName(OK_SIMPLE_NAME));
		newArguments.add(okConstant);
		newArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(arguments.get(2))));
		newArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(arguments.get(3))));

		ASTNodes.replaceButKeepComment(rewrite, visited, newMultiStatus, group);
		remover.registerRemovedNode(visited);
	}
}
