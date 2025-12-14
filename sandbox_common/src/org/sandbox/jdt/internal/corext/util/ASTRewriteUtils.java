/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.jdt.internal.corext.util;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

/**
 * Utility class for common AST rewriting operations.
 * Provides helper methods to simplify AST manipulation across cleanup implementations.
 */
public final class ASTRewriteUtils {

	private ASTRewriteUtils() {
		// Utility class - prevent instantiation
	}

	/**
	 * Adds an import to the compilation unit and returns a Name reference to it.
	 * This method should be used for every class reference added to the generated code.
	 *
	 * @param typeName a fully qualified name of a type, must not be null
	 * @param cuRewrite CompilationUnitRewrite, must not be null
	 * @param ast AST, must not be null
	 * @return simple name of a class if the import was added and fully qualified name if there was
	 *         a conflict; never null
	 */
	public static Name addImport(String typeName, CompilationUnitRewrite cuRewrite, AST ast) {
		String importedName = cuRewrite.getImportRewrite().addImport(typeName);
		return ast.newName(importedName);
	}

	/**
	 * Creates a move target for an expression, removing unnecessary parentheses.
	 * This is a common pattern when moving AST nodes from one location to another.
	 *
	 * @param rewrite the AST rewrite context, must not be null
	 * @param expression the expression to create a move target for, must not be null
	 * @return a move target for the unparenthesed expression, never null
	 */
	public static ASTNode createMoveTargetForExpression(ASTRewrite rewrite, Expression expression) {
		return ASTNodes.createMoveTarget(rewrite, ASTNodes.getUnparenthesedExpression(expression));
	}

	/**
	 * Creates a move target for an AST node, removing unnecessary parentheses if it's an expression.
	 * This is a common pattern when moving AST nodes from one location to another.
	 *
	 * @param rewrite the AST rewrite context, must not be null
	 * @param node the AST node to create a move target for, must not be null
	 * @return a move target for the unparenthesed node, never null
	 */
	public static ASTNode createMoveTargetForNode(ASTRewrite rewrite, ASTNode node) {
		if (node instanceof Expression) {
			return createMoveTargetForExpression(rewrite, (Expression) node);
		}
		return ASTNodes.createMoveTarget(rewrite, node);
	}
}
