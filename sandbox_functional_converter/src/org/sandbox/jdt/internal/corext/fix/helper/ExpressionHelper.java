/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

/**
 * Shared utility methods for AST expression creation and statement handling
 * used by the various loop renderers and handlers.
 *
 * <p>This class consolidates common logic that was previously duplicated across
 * {@link ASTEnhancedForRenderer}, {@link ASTIteratorWhileRenderer},
 * {@link StreamToEnhancedFor}, {@link StreamToIteratorWhile},
 * {@link IteratorWhileHandler}, {@link EnhancedForToIteratorWhile},
 * and {@link TraditionalForHandler}.</p>
 */
public final class ExpressionHelper {

	private ExpressionHelper() {
		// Utility class — not instantiable
	}

	/** Avoid overlapping replacements when several source formats are enabled. */
	public static boolean overlapsProcessedNode(ASTNode node, Set<ASTNode> processed) {
		for (ASTNode selected : processed) {
			if (isAncestor(node, selected) || isAncestor(selected, node)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isAncestor(ASTNode ancestor, ASTNode node) {
		for (ASTNode current = node; current != null; current = current.getParent()) {
			if (current == ancestor) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Creates an AST {@link Expression} from a string expression.
	 *
	 * <p>For simple Java identifiers a {@code SimpleName} is created directly.
	 * For qualified names (e.g. {@code "this.items"}) a {@code QualifiedName} is produced.
	 * For everything else a string placeholder is used.</p>
	 *
	 * @param ast the AST factory
	 * @param rewrite the ASTRewrite (used for string placeholders)
	 * @param expressionStr the expression text
	 * @return a JDT AST {@link Expression}
	 */
	public static Expression createExpression(AST ast, ASTRewrite rewrite, String expressionStr) {
		// For simple names, create SimpleName directly
		if (expressionStr.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) { //$NON-NLS-1$
			return ast.newSimpleName(expressionStr);
		}
		// For qualified names (e.g., "this.items"), create QualifiedName
		if (expressionStr.matches("[a-zA-Z_$][a-zA-Z0-9_$.]*")) { //$NON-NLS-1$
			return ast.newName(expressionStr);
		}
		// For complex expressions, use string placeholder
		return (Expression) rewrite.createStringPlaceholder(expressionStr, ASTNode.SIMPLE_NAME);
	}

	/**
	 * Strips a trailing semicolon (and surrounding whitespace) from a statement string
	 * so that it can be used as a pure expression.
	 *
	 * @param stmtStr the statement string, potentially ending with {@code ";"}
	 * @return the trimmed string without trailing semicolon
	 */
	public static String stripTrailingSemicolon(String stmtStr) {
		String trimmed = stmtStr.trim();
		if (trimmed.endsWith(";")) { //$NON-NLS-1$
			trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
		}
		return trimmed;
	}

	/**
	 * Extracts body statements from a lambda expression as AST {@link Statement} nodes.
	 *
	 * <p>Block lambdas return their statements directly; expression lambdas are wrapped
	 * in a newly created {@link ExpressionStatement}.</p>
	 *
	 * @param lambda the lambda expression
	 * @param ast the AST factory
	 * @return the list of body statements
	 */
	public static List<Statement> extractLambdaBodyStatements(LambdaExpression lambda, AST ast) {
		List<Statement> statements = new ArrayList<>();
		ASTNode lambdaBody = lambda.getBody();

		if (lambdaBody instanceof Block block) {
			for (Object stmt : block.statements()) {
				statements.add((Statement) stmt);
			}
		} else if (lambdaBody instanceof Expression expr) {
			// Expression body: wrap in ExpressionStatement (newly created, not part of original AST)
			ExpressionStatement exprStmt = ast.newExpressionStatement(
				(Expression) ASTNode.copySubtree(ast, expr));
			statements.add(exprStmt);
		}
		return statements;
	}

	/**
	 * Converts an AST {@link Statement} (possibly a {@link Block}) into a list of expression
	 * strings with trailing semicolons stripped.
	 *
	 * <p>If the statement is a {@link Block}, each child statement is converted individually.
	 * Otherwise the statement itself is converted as a single element.</p>
	 *
	 * @param body the loop body statement
	 * @return list of expression strings (without trailing semicolons)
	 */
	public static List<String> bodyStatementsToStrings(Statement body) {
		List<String> result = new ArrayList<>();
		if (body instanceof Block block) {
			for (Object stmt : block.statements()) {
				result.add(stripTrailingSemicolon(stmt.toString()));
			}
		} else {
			result.add(stripTrailingSemicolon(body.toString()));
		}
		return result;
	}

	/**
	 * Converts a list of AST {@link Statement} nodes into expression strings
	 * with trailing semicolons stripped.
	 *
	 * @param statements the statements to convert
	 * @return list of expression strings (without trailing semicolons)
	 */
	public static List<String> bodyStatementsToStrings(List<Statement> statements) {
		List<String> result = new ArrayList<>();
		for (Statement stmt : statements) {
			result.add(stripTrailingSemicolon(stmt.toString()));
		}
		return result;
	}

}
