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
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

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
	 * Extracts the parameter type name from a lambda parameter.
	 *
	 * <p>If the parameter has an explicit type annotation, that type name is returned.
	 * Otherwise {@code "String"} is used as a default.</p>
	 *
	 * @param param the variable declaration (lambda parameter)
	 * @return the type name string
	 */
	public static String extractParamType(VariableDeclaration param) {
		if (param instanceof SingleVariableDeclaration svd && svd.getType() != null) {
			return svd.getType().toString();
		}
		return "String"; //$NON-NLS-1$
	}

	/**
	 * Finds simple {@code forEach} method invocations on collections/streams and registers
	 * rewrite operations for each.
	 *
	 * <p>This method encapsulates the shared detection logic used by both
	 * {@link StreamToEnhancedFor} and {@link StreamToIteratorWhile}.</p>
	 *
	 * @param fixcore the fix core instance
	 * @param compilationUnit the compilation unit to scan
	 * @param operations the set to add rewrite operations to
	 * @param nodesprocessed the set of already processed nodes
	 */
	public static void findForEachInvocations(UseFunctionalCallFixCore fixcore,
			CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations,
			Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, Object> dataHolder = ReferenceHolder.create();

		HelperVisitorFactory.callMethodInvocationVisitor("forEach", compilationUnit, dataHolder, nodesprocessed, //$NON-NLS-1$
			(visited, aholder) -> {
				if (visited.arguments().size() != 1) {
					return false;
				}

				Object arg = visited.arguments().get(0);
				if (!(arg instanceof LambdaExpression)) {
					return false;
				}

				if (!(visited.getParent() instanceof ExpressionStatement)) {
					return false;
				}

				if (StreamOperationDetector.hasChainedStreamOperations(visited)) {
					return false;
				}

				operations.add(fixcore.rewrite(visited, new ReferenceHolder<>()));
				nodesprocessed.add(visited);
				return false;
			});
	}
}
