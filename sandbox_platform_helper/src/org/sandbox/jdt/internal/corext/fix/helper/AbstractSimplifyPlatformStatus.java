/*******************************************************************************
 * Copyright (c) 2021, 2026 Carsten Hammer.
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
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.SimplifyPlatformStatusFixCore;
import org.sandbox.jdt.internal.corext.util.ImportUtils;

/**
 * Shared implementation for semantics-preserving simplification of
 * {@link Status} constructor calls.
 *
 * <p>The five-argument constructor carries an explicit plug-in identifier and
 * status code. A factory call such as {@code Status.warning(message)} derives a
 * different identifier from the calling class, so this cleanup must not replace
 * the constructor with a factory unless that identity equivalence has been
 * proven. The conservative transformation implemented here removes only a
 * compile-time {@link IStatus#OK} code and retains the original severity,
 * plug-in identifier, message and throwable:</p>
 *
 * <pre>
 * new Status(severity, pluginId, IStatus.OK, message, throwable)
 *     -> new Status(severity, pluginId, message, throwable)
 * </pre>
 */
public abstract class AbstractSimplifyPlatformStatus {

	private final int expectedSeverity;

	protected AbstractSimplifyPlatformStatus(int expectedSeverity) {
		this.expectedSeverity= expectedSeverity;
	}

	/** Adds an import and returns a usable name for a generated type reference. */
	protected static Name addImport(String typeName, final CompilationUnitRewrite cuRewrite, AST ast) {
		return ImportUtils.addImport(typeName, cuRewrite.getImportRewrite(), ast);
	}

	/** Returns the compile-time integer value of an expression, or {@code null}. */
	protected static Integer constantIntValue(Expression expression) {
		Object value= expression.resolveConstantExpressionValue();
		return value instanceof Integer integer ? integer : null;
	}

	/** Tests an expression by compile-time value instead of source spelling. */
	protected static boolean hasConstantIntValue(Expression expression, int expected) {
		Integer value= constantIntValue(expression);
		return value != null && value.intValue() == expected;
	}

	public abstract String getPreview(boolean afterRefactoring);

	/** Finds exact {@link Status} constructors whose code is provably OK. */
	public void find(SimplifyPlatformStatusFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesProcessed)
			throws CoreException {
		try {
			ReferenceHolder<ASTNode, Object> holder= ReferenceHolder.createForNodes();
			HelperVisitorFactory.forClassInstanceCreation(Status.class)
				.in(compilationUnit)
				.excluding(nodesProcessed)
				.processEach(holder, (visited, data) -> {
					if (nodesProcessed.contains(visited) || visited.arguments().size() != 5) {
						return false;
					}

					ITypeBinding typeBinding= visited.resolveTypeBinding();
					if (typeBinding == null
							|| !Status.class.getName().equals(typeBinding.getErasure().getQualifiedName())) {
						return false;
					}

					List<Expression> arguments= visited.arguments();
					if (!hasConstantIntValue(arguments.get(0), expectedSeverity)
							|| !hasConstantIntValue(arguments.get(2), IStatus.OK)) {
						return false;
					}

					operations.add(fixcore.rewrite(visited, data));
					nodesProcessed.add(visited);
					return false;
				});
		} catch (Exception exception) {
			throw new CoreException(Status.error("Problem while finding Status simplifications", exception)); //$NON-NLS-1$
		}
	}

	/** Removes only the redundant OK code and retains every other argument. */
	public void rewrite(SimplifyPlatformStatusFixCore cleanup, final ClassInstanceCreation visited,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group,
			ReferenceHolder<ASTNode, Object> holder) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ImportRemover remover= cuRewrite.getImportRemover();

		ClassInstanceCreation simplifiedStatus= ast.newClassInstanceCreation();
		Name statusName= addImport(Status.class.getName(), cuRewrite, ast);
		simplifiedStatus.setType(ast.newSimpleType(statusName));

		List<Expression> originalArguments= visited.arguments();
		List<Expression> simplifiedArguments= simplifiedStatus.arguments();
		simplifiedArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(originalArguments.get(0))));
		simplifiedArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(originalArguments.get(1))));
		simplifiedArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(originalArguments.get(3))));
		simplifiedArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(originalArguments.get(4))));

		ASTNodes.replaceButKeepComment(rewrite, visited, simplifiedStatus, group);
		remover.registerRemovedNode(visited);
	}
}
