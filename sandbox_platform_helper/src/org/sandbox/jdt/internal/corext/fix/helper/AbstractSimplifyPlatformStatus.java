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
 *
 * @param <T> node type found by the visitor
 */
public abstract class AbstractSimplifyPlatformStatus<T extends ASTNode> {

	private final int expectedSeverity;

	protected AbstractSimplifyPlatformStatus(int expectedSeverity) {
		this.expectedSeverity= expectedSeverity;
	}

	/**
	 * Adds an import for a generated type reference.
	 *
	 * @param typeName fully qualified type name
	 * @param cuRewrite compilation-unit rewrite
	 * @param ast target AST
	 * @return a simple name when the import can be added, otherwise a qualified name
	 */
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

	public void find(SimplifyPlatformStatusFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed)
			throws CoreException {
		find(fixcore, compilationUnit, operations, nodesprocessed, true);
	}

	/**
	 * Finds exact {@link Status} constructors whose status code is provably
	 * {@link IStatus#OK}. The compatibility parameter is retained for callers of
	 * the previous API; plug-in identity is now always preserved.
	 */
	public void find(SimplifyPlatformStatusFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed,
			boolean preservePluginId) throws CoreException {
		try {
			ReferenceHolder<ASTNode, Object> dataholder= ReferenceHolder.createForNodes();
			HelperVisitorFactory.forClassInstanceCreation(Status.class)
				.in(compilationUnit)
				.excluding(nodesprocessed)
				.processEach(dataholder, (visited, holder) -> {
					if (nodesprocessed.contains(visited) || visited.arguments().size() != 5) {
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

					operations.add(fixcore.rewrite(visited, holder, true));
					nodesprocessed.add(visited);
					return false;
				});
		} catch (Exception exception) {
			throw new CoreException(Status.error("Problem while finding Status simplifications", exception)); //$NON-NLS-1$
		}
	}

	public void rewrite(SimplifyPlatformStatusFixCore cleanup, final ClassInstanceCreation visited,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group,
			ReferenceHolder<ASTNode, Object> holder) {
		rewrite(cleanup, visited, cuRewrite, group, holder, true);
	}

	/**
	 * Removes only the redundant {@code IStatus.OK} argument. The original
	 * plug-in identifier is moved into the corresponding four-argument
	 * constructor and therefore cannot be silently replaced by caller inference.
	 */
	public void rewrite(SimplifyPlatformStatusFixCore cleanup, final ClassInstanceCreation visited,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group,
			ReferenceHolder<ASTNode, Object> holder, boolean preservePluginId) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ImportRemover remover= cuRewrite.getImportRemover();

		ClassInstanceCreation simplifiedStatus= ast.newClassInstanceCreation();
		Name statusName= addImport(Status.class.getName(), cuRewrite, ast);
		simplifiedStatus.setType(ast.newSimpleType(statusName));

		List<Expression> originalArguments= visited.arguments();
		List<Expression> simplifiedArguments= simplifiedStatus.arguments();
		// severity
		simplifiedArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(originalArguments.get(0))));
		// explicit String or Class<?> plug-in identity
		simplifiedArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(originalArguments.get(1))));
		// message; argument 2 (the proven OK code) is deliberately omitted
		simplifiedArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(originalArguments.get(3))));
		// throwable, including an explicit null, is retained
		simplifiedArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(originalArguments.get(4))));

		ASTNodes.replaceButKeepComment(rewrite, visited, simplifiedStatus, group);
		remover.registerRemovedNode(visited);
	}
}
