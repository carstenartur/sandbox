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
package org.sandbox.jdt.triggerpattern.cleanup;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
import org.eclipse.text.edits.TextEditGroup;

/**
 * Reusable utility for removing checked exceptions from method throws clauses,
 * catch clauses, and simplifying empty try statements after exception removal.
 *
 * <p>This class was extracted from {@code AbstractExplicitEncoding} to be
 * accessible to both the encoding plugin and the trigger-pattern DSL engine.
 * It is generic enough to work with <b>any</b> checked exception, not just
 * {@code UnsupportedEncodingException}.</p>
 *
 * @since 1.3.5
 */
public class ExceptionCleanupHelper {

	private ExceptionCleanupHelper() {
		// utility class – not instantiable
	}

	/**
	 * Removes the specified checked exception from the enclosing method's throws
	 * clause or from catch clauses in a try statement.
	 *
	 * @param visited         the AST node that was modified, must not be null
	 * @param exceptionFQN    fully qualified name of the exception to remove
	 *                        (e.g. {@code "java.io.UnsupportedEncodingException"})
	 * @param exceptionSimple simple name of the exception
	 *                        (e.g. {@code "UnsupportedEncodingException"})
	 * @param group           the text edit group for tracking changes
	 * @param rewrite         the AST rewrite context
	 * @param importRemover   the import remover for tracking removed nodes
	 */
	public static void removeCheckedException(
			ASTNode visited,
			String exceptionFQN,
			String exceptionSimple,
			TextEditGroup group,
			ASTRewrite rewrite,
			ImportRemover importRemover) {

		ASTNode parent = findEnclosingMethodOrTry(visited);
		if (parent == null) {
			return;
		}

		if (parent instanceof MethodDeclaration method) {
			removeExceptionFromMethodThrows(method, exceptionFQN, exceptionSimple, rewrite, group, importRemover);
		} else if (parent instanceof TryStatement tryStatement) {
			int removedCount = removeExceptionFromTryCatch(tryStatement, exceptionFQN, exceptionSimple, rewrite, group, importRemover);
			simplifyEmptyTryStatement(tryStatement, rewrite, group, removedCount);
		}
	}

	// ------------------------------------------------------------------
	// package-private helpers (visible for testing inside the same package)
	// ------------------------------------------------------------------

	static ASTNode findEnclosingMethodOrTry(ASTNode node) {
		if (node == null) {
			return null;
		}
		ASTNode tryStmt = ASTNodes.getFirstAncestorOrNull(node, TryStatement.class);
		ASTNode methodDecl = ASTNodes.getFirstAncestorOrNull(node, MethodDeclaration.class);
		if (tryStmt != null) {
			return tryStmt;
		}
		return methodDecl;
	}

	static boolean isTargetException(Type type, String exceptionSimple) {
		return type.toString().equals(exceptionSimple);
	}

	static void removeExceptionFromMethodThrows(
			MethodDeclaration method,
			String exceptionFQN,
			String exceptionSimple,
			ASTRewrite rewrite,
			TextEditGroup group,
			ImportRemover importRemover) {

		ListRewrite throwsRewrite = rewrite.getListRewrite(method,
				MethodDeclaration.THROWN_EXCEPTION_TYPES_PROPERTY);
		List<Type> thrownExceptions = method.thrownExceptionTypes();
		for (Type exceptionType : thrownExceptions) {
			if (isTargetException(exceptionType, exceptionSimple)) {
				throwsRewrite.remove(exceptionType, group);
				importRemover.registerRemovedNode(exceptionType);
			}
		}
	}

	static boolean removeExceptionFromUnionType(
			UnionType unionType,
			CatchClause catchClause,
			String exceptionSimple,
			ASTRewrite rewrite,
			TextEditGroup group) {

		ListRewrite unionRewrite = rewrite.getListRewrite(unionType, UnionType.TYPES_PROPERTY);
		List<Type> types = unionType.types();

		List<Type> typesToRemove = types.stream()
				.filter(t -> isTargetException(t, exceptionSimple))
				.toList();

		typesToRemove.forEach(type -> unionRewrite.remove(type, group));

		int remainingCount = types.size() - typesToRemove.size();
		if (remainingCount == 1) {
			Type remainingType = types.stream()
					.filter(type -> !typesToRemove.contains(type))
					.findFirst()
					.orElse(null);
			if (remainingType != null) {
				rewrite.replace(unionType, remainingType, group);
			}
		} else if (remainingCount == 0) {
			rewrite.remove(catchClause, group);
			return true;
		}
		return false;
	}

	static int removeExceptionFromTryCatch(
			TryStatement tryStatement,
			String exceptionFQN,
			String exceptionSimple,
			ASTRewrite rewrite,
			TextEditGroup group,
			ImportRemover importRemover) {

		int removedCount = 0;
		List<CatchClause> catchClauses = tryStatement.catchClauses();
		for (CatchClause catchClause : catchClauses) {
			SingleVariableDeclaration exception = catchClause.getException();
			Type exceptionType = exception.getType();

			if (exceptionType instanceof UnionType unionType) {
				if (removeExceptionFromUnionType(unionType, catchClause, exceptionSimple, rewrite, group)) {
					removedCount++;
				}
			} else if (isTargetException(exceptionType, exceptionSimple)) {
				rewrite.remove(catchClause, group);
				importRemover.registerRemovedNode(catchClause);
				removedCount++;
			}
		}
		return removedCount;
	}

	static void simplifyEmptyTryStatement(TryStatement tryStatement, ASTRewrite rewrite, TextEditGroup group, int removedCatchCount) {
		int remainingCatchClauses = tryStatement.catchClauses().size() - removedCatchCount;
		if (remainingCatchClauses > 0 || tryStatement.getFinally() != null) {
			return;
		}

		Block tryBlock = tryStatement.getBody();
		boolean hasResources = !tryStatement.resources().isEmpty();
		boolean hasStatements = !tryBlock.statements().isEmpty();

		if (!hasResources && !hasStatements) {
			rewrite.remove(tryStatement, group);
		} else if (!hasResources && tryStatement.getParent() instanceof Block parentBlock) {
			// Inline statements from try body into the parent block,
			// replacing the try statement with its individual statements
			// to avoid producing an orphaned { ... } block.
			// NOTE: Callers must register child rewrites (e.g., replaceAndRemoveNLS)
			// BEFORE invoking removeUnsupportedEncodingException (which triggers
			// this method). createMoveTarget marks nodes as moved, and
			// replaceAndRemoveNLS fails silently on already-moved nodes.
			ListRewrite parentListRewrite = rewrite.getListRewrite(parentBlock, Block.STATEMENTS_PROPERTY);
			List<?> tryStatements = tryBlock.statements();
			for (int i = tryStatements.size() - 1; i >= 0; i--) {
				ASTNode stmt = (ASTNode) tryStatements.get(i);
				ASTNode moved = rewrite.createMoveTarget(stmt);
				parentListRewrite.insertAfter(moved, tryStatement, group);
			}
			rewrite.remove(tryStatement, group);
		}
	}
}
