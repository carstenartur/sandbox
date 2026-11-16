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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EmptyStatement;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
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
		if (parent == null || !canRemoveException(parent, visited, exceptionFQN, rewrite)) {
			return;
		}

		if (parent instanceof MethodDeclaration method) {
			removeExceptionFromMethodThrows(method, exceptionFQN, exceptionSimple, rewrite, group, importRemover);
		} else if (parent instanceof TryStatement tryStatement) {
			int removedCount = removeExceptionFromTryCatch(tryStatement, exceptionFQN, rewrite, group, importRemover);
			simplifyEmptyTryStatement(tryStatement, rewrite, group, removedCount);
		}
	}

	/** Checks that the exception is handled locally and its handlers can be removed safely. */
	public static boolean canRemoveExceptionLocally(ASTNode visited, ITypeBinding exception, ASTRewrite previousRewrite) {
		if (ASTNodes.getFirstAncestorOrNull(visited, LambdaExpression.class) != null) {
			// Removing a handler can change the inferred return or exception type of a lambda.
			return false;
		}
		ASTNode scope = ASTNodes.getFirstAncestorOrNull(visited, BodyDeclaration.class);
		if ((scope instanceof FieldDeclaration || scope instanceof Initializer)
				&& scope.getParent() instanceof AnonymousClassDeclaration
				&& !Modifier.isStatic(((BodyDeclaration) scope).getModifiers())) {
			// The exception may contribute to an inferred constructor throws clause.
			return false;
		}

		boolean handled = false;
		ASTRewrite candidateRewrite = ASTRewrite.create(visited.getAST());
		CheckedExceptionAnalysis.copyConvertedCalls(previousRewrite, candidateRewrite, exception.getQualifiedName());
		for (TryStatement statement : enclosingProtectedTries(visited)) {
			List<CatchClause> clauses = statement.catchClauses();
			for (CatchClause clause : clauses) {
				Type caught = clause.getException().getType();
				List<?> types = caught instanceof UnionType union ? union.types() : List.of(caught);
				for (Object type : types) {
					ITypeBinding binding = ((Type) type).resolveBinding();
					if (binding == null || binding.isRecovered()) {
						return false;
					}
					if (exception.isEqualTo(binding)) {
						// Narrowing a multi-catch can change the type of the catch parameter.
						if (caught instanceof UnionType || !canDiscardCatch(statement, clause)
								|| !canRemoveException(statement, visited, exception.getQualifiedName(), candidateRewrite)) {
							return false;
						}
						// Model the inner removal before examining an enclosing catch.
						candidateRewrite.remove(clause, null);
						handled = true;
					} else if (exception.isAssignmentCompatible(binding) || binding.isAssignmentCompatible(exception)) {
						// Other handlers could become unreachable or change a precise rethrow.
						return false;
					}
				}
			}
		}
		return handled;
	}

	private static boolean canDiscardCatch(TryStatement statement, CatchClause clause) {
		List<Statement> handler = clause.getBody().statements();
		// Limit removal to straight-line handlers and method exits. A break, continue,
		// or yield can affect reachability beyond the try even when its body completes normally.
		if (!handler.stream().allMatch(child -> isStraightLine(child) || child instanceof ReturnStatement || child instanceof ThrowStatement)) {
			return false;
		}
		if (!ASTNodes.fallsThrough(clause.getBody()) && !isStraightLine(statement.getBody())) {
			return false;
		}
		if (statement.catchClauses().size() == 1 && statement.resources().isEmpty() && statement.getFinally() == null
				&& hasLeadingStatementComment(statement)) {
			return false;
		}
		return !CheckedExceptionAnalysis.hasCheckedException(clause.getBody());
	}

	private static boolean isStraightLine(Statement statement) {
		if (statement instanceof Block block) {
			List<Statement> children = block.statements();
			return children.stream().allMatch(ExceptionCleanupHelper::isStraightLine);
		}
		return statement instanceof EmptyStatement || statement instanceof ExpressionStatement
				|| statement instanceof VariableDeclarationStatement;
	}

	private static boolean hasLeadingStatementComment(TryStatement statement) {
		if (!statement.getLocationInParent().isChildProperty() || !(statement.getRoot() instanceof CompilationUnit root)) {
			return false;
		}
		ASTNode parent = statement.getParent();
		int parentStart = parent.getStartPosition();
		if (parent instanceof IfStatement conditional) {
			ASTNode preceding = conditional.getThenStatement() == statement ? conditional.getExpression() : conditional.getThenStatement();
			parentStart = preceding.getStartPosition() + preceding.getLength();
		}
		for (Object node : root.getCommentList()) {
			Comment comment = (Comment) node;
			if (comment.getStartPosition() >= parentStart
					&& comment.getStartPosition() + comment.getLength() <= statement.getStartPosition()) {
				return true;
			}
		}
		return false;
	}

	/** Removes obsolete catches while preserving the scope of the body and caller contracts. */
	public static void removeCheckedExceptionFromEnclosingTries(ASTNode visited, String exceptionFQN,
			TextEditGroup group, ASTRewrite rewrite, ImportRemover importRemover) {
		for (TryStatement statement : enclosingProtectedTries(visited)) {
			if (!canRemoveException(statement, visited, exceptionFQN, rewrite)) {
				continue;
			}
			int removed = removeExceptionFromTryCatch(statement, exceptionFQN, rewrite, group, importRemover);
			if (removed == statement.catchClauses().size() && statement.getFinally() == null && statement.resources().isEmpty()) {
				// A block fits single-statement arms and keeps local declarations scoped to the body.
				ASTNodes.replaceButKeepComment(rewrite, statement, rewrite.createMoveTarget(statement.getBody()), group);
			}
		}
	}

	private static List<TryStatement> enclosingProtectedTries(ASTNode visited) {
		List<TryStatement> result = new ArrayList<>();
		for (ASTNode child = visited, parent = child.getParent();
				parent != null && !(parent instanceof BodyDeclaration) && !(parent instanceof LambdaExpression);
				child = parent, parent = parent.getParent()) {
			if (parent instanceof TryStatement statement && (child == statement.getBody() || statement.resources().contains(child))) {
				result.add(statement);
			}
		}
		return result;
	}

	/** Records the converted invocation and proves that no surviving expression needs the handler. */
	public static boolean canRemoveException(ASTNode scope, ASTNode visited, String exceptionFQN, ASTRewrite rewrite) {
		return CheckedExceptionAnalysis.canRemove(scope, visited, exceptionFQN, rewrite);
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

	static boolean isTargetException(Type type, String exceptionFQN) {
		var binding = type.resolveBinding();
		return binding != null && !binding.isRecovered()
				&& exceptionFQN.equals(binding.getErasure().getQualifiedName());
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
			if (isTargetException(exceptionType, exceptionFQN)) {
				throwsRewrite.remove(exceptionType, group);
				importRemover.registerRemovedNode(exceptionType);
                updateThrowsJavadoc(method, exceptionFQN, null, rewrite, group, importRemover);
			}
		}
	}

    /** Returns whether a matching documented exception was found. */
    static boolean updateThrowsJavadoc(MethodDeclaration method, String oldType, String replacement,
            ASTRewrite rewrite, TextEditGroup group, ImportRemover imports) {
        Javadoc javadoc= method.getJavadoc();
        if (javadoc == null) return false;
        boolean found= false;
        ListRewrite tags= rewrite.getListRewrite(javadoc, Javadoc.TAGS_PROPERTY);
        for (Object item : List.copyOf(tags.getRewrittenList())) {
            TagElement tag= (TagElement) item;
            if ((TagElement.TAG_THROWS.equals(tag.getTagName()) || TagElement.TAG_EXCEPTION.equals(tag.getTagName()))
                    && !tag.fragments().isEmpty() && tag.fragments().get(0) instanceof Name name
                    && name.resolveBinding() instanceof ITypeBinding type && oldType.equals(type.getErasure().getQualifiedName())) {
                if (replacement == null) {
                    tags.remove(tag, group);
                    imports.registerRemovedNode(tag);
                } else rewrite.replace(name, method.getAST().newName(replacement), group);
                found= true;
            }
        }
        return found;
    }

	static boolean removeExceptionFromUnionType(
			UnionType unionType,
			CatchClause catchClause,
			String exceptionFQN,
			ASTRewrite rewrite,
			TextEditGroup group,
			ImportRemover importRemover) {

		ListRewrite unionRewrite = rewrite.getListRewrite(unionType, UnionType.TYPES_PROPERTY);
		List<Type> types = unionType.types();

		List<Type> typesToRemove = types.stream()
				.filter(t -> isTargetException(t, exceptionFQN))
				.toList();

		typesToRemove.forEach(type -> {
			unionRewrite.remove(type, group);
			importRemover.registerRemovedNode(type);
		});

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
			importRemover.registerRemovedNode(catchClause);
			return true;
		}
		return false;
	}

	static int removeExceptionFromTryCatch(
			TryStatement tryStatement,
			String exceptionFQN,
			ASTRewrite rewrite,
			TextEditGroup group,
			ImportRemover importRemover) {

		int removedCount = 0;
		List<CatchClause> catchClauses = tryStatement.catchClauses();
		for (CatchClause catchClause : catchClauses) {
			SingleVariableDeclaration exception = catchClause.getException();
			Type exceptionType = exception.getType();

			if (exceptionType instanceof UnionType unionType) {
				if (removeExceptionFromUnionType(unionType, catchClause, exceptionFQN, rewrite, group, importRemover)) {
					removedCount++;
				}
			} else if (isTargetException(exceptionType, exceptionFQN)) {
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
		} else if (!hasResources && tryStatement.getParent() instanceof Block) {
			// Inline statements from try body into the parent block,
			// replacing the try statement with its individual statements
			// to avoid producing an orphaned { ... } block.
			// NOTE: Callers must register child rewrites (e.g., replaceAndRemoveNLS)
			// BEFORE invoking removeUnsupportedEncodingException (which triggers
			// this method). createMoveTarget marks nodes as moved, and
			// replaceAndRemoveNLS fails silently on already-moved nodes.
			List<?> statements = rewrite.getListRewrite(tryBlock, Block.STATEMENTS_PROPERTY).getRewrittenList();
			ASTNode[] moved = new ASTNode[statements.size()];
			for (int i = 0; i < statements.size(); i++) {
				ASTNode statement = (ASTNode) statements.get(i);
				moved[i] = statement.getParent() == null ? statement : rewrite.createMoveTarget(statement);
			}
			rewrite.replace(tryStatement, rewrite.createGroupNode(moved), group);
		}
	}
}
