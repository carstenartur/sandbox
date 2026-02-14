/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
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
package org.sandbox.jdt.internal.common;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * A fluent type-safe wrapper for AST nodes that enables pattern matching style processing
 * without deep instanceof chains.
 * 
 * <p>This class provides a more object-oriented and functional approach to handling
 * different AST node types, replacing deeply nested if-instanceof chains with a
 * cleaner fluent API.</p>
 * 
 * <p><b>Example - Before (nested if-instanceof):</b></p>
 * <pre>{@code
 * if (stmt instanceof VariableDeclarationStatement) {
 *     VariableDeclarationStatement varDecl = (VariableDeclarationStatement) stmt;
 *     // handle variable declaration
 * } else if (stmt instanceof IfStatement) {
 *     IfStatement ifStmt = (IfStatement) stmt;
 *     if (ifStmt.getElseStatement() == null) {
 *         // handle if without else
 *     }
 * } else if (stmt instanceof ExpressionStatement) {
 *     // handle expression
 * }
 * }</pre>
 * 
 * <p><b>Example - After (fluent API):</b></p>
 * <pre>{@code
 * NodeMatcher.on(stmt)
 *     .ifVariableDeclaration(varDecl -> {
 *         // handle variable declaration
 *     })
 *     .ifIfStatement(ifStmt -> {
 *         // handle if statement
 *     })
 *     .ifExpressionStatement(exprStmt -> {
 *         // handle expression
 *     })
 *     .orElse(node -> {
 *         // handle other cases
 *     });
 * }</pre>
 * 
 * <p><b>Example - With conditions:</b></p>
 * <pre>{@code
 * NodeMatcher.on(stmt)
 *     .ifIfStatementMatching(
 *         ifStmt -> ifStmt.getElseStatement() == null,
 *         ifStmt -> handleIfWithoutElse(ifStmt)
 *     )
 *     .ifIfStatement(ifStmt -> handleIfWithElse(ifStmt));
 * }</pre>
 * 
 * @param <N> the type of AST node being matched
 * @see AstProcessorBuilder
 */
public final class NodeMatcher<N extends ASTNode> {

	private final N node;
	private boolean handled = false;

	private NodeMatcher(N node) {
		this.node = node;
	}

	/**
	 * Creates a new NodeMatcher for the given AST node.
	 * 
	 * @param <N> the type of AST node
	 * @param node the node to match against
	 * @return a new NodeMatcher instance
	 */
	public static <N extends ASTNode> NodeMatcher<N> on(N node) {
		return new NodeMatcher<>(node);
	}

	/**
	 * Returns the wrapped node.
	 * 
	 * @return the AST node
	 */
	public N getNode() {
		return node;
	}

	/**
	 * Checks if this node has already been handled by a previous matcher.
	 * 
	 * @return true if handled
	 */
	public boolean isHandled() {
		return handled;
	}

	// ========== Statement Type Matchers ==========

	/**
	 * Executes the consumer if the node is a VariableDeclarationStatement.
	 */
	public NodeMatcher<N> ifVariableDeclaration(Consumer<VariableDeclarationStatement> consumer) {
		return ifType(VariableDeclarationStatement.class, consumer);
	}

	/**
	 * Executes the consumer if the node is a VariableDeclarationStatement and matches the predicate.
	 */
	public NodeMatcher<N> ifVariableDeclarationMatching(
			Predicate<VariableDeclarationStatement> predicate,
			Consumer<VariableDeclarationStatement> consumer) {
		return ifTypeMatching(VariableDeclarationStatement.class, predicate, consumer);
	}

	/**
	 * Executes the consumer if the node is an IfStatement.
	 */
	public NodeMatcher<N> ifIfStatement(Consumer<IfStatement> consumer) {
		return ifType(IfStatement.class, consumer);
	}

	/**
	 * Executes the consumer if the node is an IfStatement and matches the predicate.
	 */
	public NodeMatcher<N> ifIfStatementMatching(
			Predicate<IfStatement> predicate,
			Consumer<IfStatement> consumer) {
		return ifTypeMatching(IfStatement.class, predicate, consumer);
	}

	/**
	 * Executes the consumer if the node is an IfStatement without an else branch.
	 */
	public NodeMatcher<N> ifIfStatementWithoutElse(Consumer<IfStatement> consumer) {
		return ifIfStatementMatching(ifStmt -> ifStmt.getElseStatement() == null, consumer);
	}

	/**
	 * Executes the consumer if the node is an IfStatement with an else branch.
	 */
	public NodeMatcher<N> ifIfStatementWithElse(Consumer<IfStatement> consumer) {
		return ifIfStatementMatching(ifStmt -> ifStmt.getElseStatement() != null, consumer);
	}

	/**
	 * Executes the consumer if the node is an ExpressionStatement.
	 */
	public NodeMatcher<N> ifExpressionStatement(Consumer<ExpressionStatement> consumer) {
		return ifType(ExpressionStatement.class, consumer);
	}

	/**
	 * Executes the consumer if the node is an ExpressionStatement and matches the predicate.
	 */
	public NodeMatcher<N> ifExpressionStatementMatching(
			Predicate<ExpressionStatement> predicate,
			Consumer<ExpressionStatement> consumer) {
		return ifTypeMatching(ExpressionStatement.class, predicate, consumer);
	}

	/**
	 * Executes the consumer if the node is a ReturnStatement.
	 */
	public NodeMatcher<N> ifReturnStatement(Consumer<ReturnStatement> consumer) {
		return ifType(ReturnStatement.class, consumer);
	}

	/**
	 * Executes the consumer if the node is a ContinueStatement.
	 */
	public NodeMatcher<N> ifContinueStatement(Consumer<ContinueStatement> consumer) {
		return ifType(ContinueStatement.class, consumer);
	}

	/**
	 * Executes the consumer if the node is a BreakStatement.
	 */
	public NodeMatcher<N> ifBreakStatement(Consumer<BreakStatement> consumer) {
		return ifType(BreakStatement.class, consumer);
	}

	/**
	 * Executes the consumer if the node is a ThrowStatement.
	 */
	public NodeMatcher<N> ifThrowStatement(Consumer<ThrowStatement> consumer) {
		return ifType(ThrowStatement.class, consumer);
	}

	/**
	 * Executes the consumer if the node is a Block.
	 */
	public NodeMatcher<N> ifBlock(Consumer<Block> consumer) {
		return ifType(Block.class, consumer);
	}

	// ========== Expression Type Matchers ==========

	/**
	 * Executes the consumer if the node is an Assignment.
	 */
	public NodeMatcher<N> ifAssignment(Consumer<Assignment> consumer) {
		return ifType(Assignment.class, consumer);
	}

	/**
	 * Executes the consumer if the node is an Assignment with the specified operator.
	 */
	public NodeMatcher<N> ifAssignmentWithOperator(
			Assignment.Operator operator,
			Consumer<Assignment> consumer) {
		return ifTypeMatching(Assignment.class, a -> a.getOperator() == operator, consumer);
	}

	/**
	 * Executes the consumer if the node is a MethodInvocation.
	 */
	public NodeMatcher<N> ifMethodInvocation(Consumer<MethodInvocation> consumer) {
		return ifType(MethodInvocation.class, consumer);
	}

	/**
	 * Executes the consumer if the node is a MethodInvocation with the specified method name.
	 */
	public NodeMatcher<N> ifMethodInvocationNamed(String methodName, Consumer<MethodInvocation> consumer) {
		return ifTypeMatching(MethodInvocation.class, 
				mi -> methodName.equals(mi.getName().getIdentifier()), consumer);
	}

	/**
	 * Executes the consumer if the node is a PostfixExpression.
	 */
	public NodeMatcher<N> ifPostfixExpression(Consumer<PostfixExpression> consumer) {
		return ifType(PostfixExpression.class, consumer);
	}

	/**
	 * Executes the consumer if the node is a PostfixExpression with increment or decrement.
	 */
	public NodeMatcher<N> ifPostfixIncrementOrDecrement(Consumer<PostfixExpression> consumer) {
		return ifTypeMatching(PostfixExpression.class,
				postfix -> postfix.getOperator() == PostfixExpression.Operator.INCREMENT
						|| postfix.getOperator() == PostfixExpression.Operator.DECREMENT,
				consumer);
	}

	/**
	 * Executes the consumer if the node is a PrefixExpression.
	 */
	public NodeMatcher<N> ifPrefixExpression(Consumer<PrefixExpression> consumer) {
		return ifType(PrefixExpression.class, consumer);
	}

	/**
	 * Executes the consumer if the node is a PrefixExpression with increment or decrement.
	 */
	public NodeMatcher<N> ifPrefixIncrementOrDecrement(Consumer<PrefixExpression> consumer) {
		return ifTypeMatching(PrefixExpression.class,
				prefix -> prefix.getOperator() == PrefixExpression.Operator.INCREMENT
						|| prefix.getOperator() == PrefixExpression.Operator.DECREMENT,
				consumer);
	}

	/**
	 * Executes the consumer if the node is a SimpleName.
	 */
	public NodeMatcher<N> ifSimpleName(Consumer<SimpleName> consumer) {
		return ifType(SimpleName.class, consumer);
	}

	// ========== Generic Type Matcher ==========

	/**
	 * Generic type matcher that handles any specific ASTNode subclass.
	 * 
	 * @param <T> the expected node type
	 * @param nodeClass the class to match
	 * @param consumer the consumer to execute if matched
	 * @return this matcher for chaining
	 */
	public <T extends ASTNode> NodeMatcher<N> ifType(Class<T> nodeClass, Consumer<T> consumer) {
		if (!handled && nodeClass.isInstance(node)) {
			consumer.accept(nodeClass.cast(node));
			handled = true;
		}
		return this;
	}

	/**
	 * Generic type matcher with predicate.
	 */
	public <T extends ASTNode> NodeMatcher<N> ifTypeMatching(
			Class<T> nodeClass,
			Predicate<T> predicate,
			Consumer<T> consumer) {
		if (!handled && nodeClass.isInstance(node)) {
			T typedNode = nodeClass.cast(node);
			if (predicate.test(typedNode)) {
				consumer.accept(typedNode);
				handled = true;
			}
		}
		return this;
	}

	// ========== Composite Matchers ==========

	/**
	 * Matches if the node is a Block containing exactly one statement of the given type,
	 * and that statement matches the predicate.
	 *
	 * @param <S> the expected statement type
	 * @param stmtClass the class of the single statement to match
	 * @param predicate the predicate to test the statement
	 * @param consumer the consumer to execute if matched
	 * @return this matcher for chaining
	 */
	public <S extends Statement> NodeMatcher<N> ifBlockWithSingleStatement(
			Class<S> stmtClass,
			Predicate<S> predicate,
			Consumer<S> consumer) {
		if (!handled && node instanceof Block block) {
			if (block.statements().size() == 1
					&& stmtClass.isInstance(block.statements().get(0))) {
				S stmt = stmtClass.cast(block.statements().get(0));
				if (predicate.test(stmt)) {
					consumer.accept(stmt);
					handled = true;
				}
			}
		}
		return this;
	}

	/**
	 * If the node is an IfStatement, extracts the thenStatement and matches it
	 * as either a direct statement or a Block with a single statement of the given type.
	 *
	 * @param <S> the expected statement type
	 * @param stmtClass the class to match the then-branch content against
	 * @param predicate the predicate to test the matched statement
	 * @param consumer the consumer to execute if matched
	 * @return this matcher for chaining
	 */
	public <S extends Statement> NodeMatcher<N> ifThenStatementIs(
			Class<S> stmtClass,
			Predicate<S> predicate,
			Consumer<S> consumer) {
		if (!handled && node instanceof IfStatement ifStmt) {
			Statement then = ifStmt.getThenStatement();
			if (stmtClass.isInstance(then)) {
				S stmt = stmtClass.cast(then);
				if (predicate.test(stmt)) {
					consumer.accept(stmt);
					handled = true;
				}
			} else if (then instanceof Block block
					&& block.statements().size() == 1
					&& stmtClass.isInstance(block.statements().get(0))) {
				S stmt = stmtClass.cast(block.statements().get(0));
				if (predicate.test(stmt)) {
					consumer.accept(stmt);
					handled = true;
				}
			}
		}
		return this;
	}

	/**
	 * If the node matches the given type, applies the mapper and passes the result
	 * to the consumer. The node is considered handled if the mapper returns a non-null result.
	 *
	 * @param <T> the expected node type
	 * @param <R> the result type
	 * @param nodeClass the class to match
	 * @param mapper the function to extract a result from the node
	 * @param resultConsumer the consumer to receive the non-null result
	 * @return this matcher for chaining
	 */
	public <T extends ASTNode, R> NodeMatcher<N> ifTypeMapping(
			Class<T> nodeClass,
			Function<T, R> mapper,
			Consumer<R> resultConsumer) {
		if (!handled && nodeClass.isInstance(node)) {
			R result = mapper.apply(nodeClass.cast(node));
			if (result != null) {
				resultConsumer.accept(result);
				handled = true;
			}
		}
		return this;
	}

	// ========== Static Utility Methods ==========

	/**
	 * Applies a NodeMatcher configuration to each element in a list.
	 * Only elements that are ASTNode instances are processed.
	 *
	 * @param statements the list of statements to match against
	 * @param matcherConfig the matcher configuration to apply per statement
	 */
	@SuppressWarnings("rawtypes")
	public static void matchAll(List statements,
			Function<NodeMatcher<ASTNode>, NodeMatcher<ASTNode>> matcherConfig) {
		for (Object stmt : statements) {
			if (stmt instanceof ASTNode astNode) {
				matcherConfig.apply(NodeMatcher.on(astNode));
			}
		}
	}

	// ========== Terminal Operations ==========

	/**
	 * Executes the consumer if no previous matcher handled the node.
	 * 
	 * @param consumer the consumer to execute
	 */
	public void orElse(Consumer<N> consumer) {
		if (!handled) {
			consumer.accept(node);
		}
	}

	/**
	 * Executes the runnable if no previous matcher handled the node.
	 * 
	 * @param runnable the runnable to execute
	 */
	public void orElseDo(Runnable runnable) {
		if (!handled) {
			runnable.run();
		}
	}

	/**
	 * Returns an Optional containing the result of the function if no matcher handled the node.
	 * 
	 * @param <R> the result type
	 * @param function the function to apply
	 * @return an Optional with the result
	 */
	public <R> Optional<R> orElseGet(Function<N, R> function) {
		if (!handled) {
			return Optional.ofNullable(function.apply(node));
		}
		return Optional.empty();
	}

	// ========== Utility Methods ==========

	/**
	 * Checks if the node is any of the "unconvertible" statement types
	 * (return, continue, break, throw).
	 * 
	 * @return true if the node is an unconvertible control flow statement
	 */
	public boolean isControlFlowStatement() {
		return node instanceof ReturnStatement
				|| node instanceof ContinueStatement
				|| node instanceof BreakStatement
				|| node instanceof ThrowStatement;
	}

	/**
	 * Checks if the node is an ExpressionStatement containing an Assignment.
	 * 
	 * @return true if the node is an assignment statement
	 */
	public boolean isAssignmentStatement() {
		return node instanceof ExpressionStatement exprStmt
				&& exprStmt.getExpression() instanceof Assignment;
	}

	/**
	 * Extracts the Assignment from an ExpressionStatement if present.
	 * 
	 * @return Optional containing the Assignment, or empty if not an assignment statement
	 */
	public Optional<Assignment> getAssignment() {
		if (node instanceof ExpressionStatement exprStmt
				&& exprStmt.getExpression() instanceof Assignment assignment) {
			return Optional.of(assignment);
		}
		return Optional.empty();
	}

	/**
	 * Extracts the Expression from an ExpressionStatement if present.
	 * 
	 * @return Optional containing the Expression, or empty if not an ExpressionStatement
	 */
	public Optional<Expression> getExpression() {
		if (node instanceof ExpressionStatement exprStmt) {
			return Optional.of(exprStmt.getExpression());
		}
		return Optional.empty();
	}
}
