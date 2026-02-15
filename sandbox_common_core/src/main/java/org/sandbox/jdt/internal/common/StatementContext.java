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
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Statement;

/**
 * Provides contextual information about a statement's position within a block.
 * 
 * <p>This class helps eliminate repetitive index tracking and position checking
 * in statement processing loops. Instead of manually tracking if a statement is
 * first, last, or at a specific position, this context object provides that
 * information directly.</p>
 * 
 * <p><b>Example - Before:</b></p>
 * <pre>{@code
 * List<Statement> statements = block.statements();
 * for (int i = 0; i < statements.size(); i++) {
 *     Statement stmt = statements.get(i);
 *     boolean isLast = (i == statements.size() - 1);
 *     boolean isFirst = (i == 0);
 *     
 *     if (stmt instanceof IfStatement && !isLast) {
 *         // handle non-last IF
 *     } else if (stmt instanceof IfStatement && isLast) {
 *         // handle last IF
 *     }
 * }
 * }</pre>
 * 
 * <p><b>Example - After:</b></p>
 * <pre>{@code
 * StatementContext.forEachInBlock(block, (stmt, ctx) -> {
 *     NodeMatcher.on(stmt)
 *         .ifIfStatementMatching(
 *             ifStmt -> !ctx.isLast(),
 *             ifStmt -> handleNonLastIf(ifStmt, ctx)
 *         )
 *         .ifIfStatementMatching(
 *             ifStmt -> ctx.isLast(),
 *             ifStmt -> handleLastIf(ifStmt, ctx)
 *         );
 * });
 * }</pre>
 * 
 * @see NodeMatcher
 */
public final class StatementContext {

	private final Statement statement;
	private final int index;
	private final int totalCount;
	private final List<Statement> allStatements;

	private StatementContext(Statement statement, int index, List<Statement> allStatements) {
		this.statement = statement;
		this.index = index;
		this.totalCount = allStatements.size();
		this.allStatements = allStatements;
	}

	/**
	 * Creates a StatementContext for a single statement (not part of a list).
	 */
	public static StatementContext forSingle(Statement statement) {
		return new StatementContext(statement, 0, List.of(statement));
	}

	/**
	 * Creates a StatementContext for a statement at a specific position in a list.
	 */
	public static StatementContext forStatement(Statement statement, int index, List<Statement> allStatements) {
		return new StatementContext(statement, index, allStatements);
	}

	/**
	 * Processes each statement in a block with its context.
	 * 
	 * @param block the block containing statements
	 * @param consumer the consumer to process each statement with its context
	 */
	public static void forEachInBlock(Block block, BiConsumer<Statement, StatementContext> consumer) {
		@SuppressWarnings("unchecked")
		List<Statement> statements = block.statements();
		for (int i = 0; i < statements.size(); i++) {
			Statement stmt = statements.get(i);
			StatementContext ctx = new StatementContext(stmt, i, statements);
			consumer.accept(stmt, ctx);
		}
	}

	/**
	 * Processes each statement in a block with its context, allowing early termination.
	 * 
	 * @param <R> the result type
	 * @param block the block containing statements
	 * @param processor the function to process each statement; return non-empty to stop
	 * @return the first non-empty result, or empty if all statements were processed
	 */
	public static <R> Optional<R> processBlock(Block block, BiFunction<Statement, StatementContext, Optional<R>> processor) {
		@SuppressWarnings("unchecked")
		List<Statement> statements = block.statements();
		for (int i = 0; i < statements.size(); i++) {
			Statement stmt = statements.get(i);
			StatementContext ctx = new StatementContext(stmt, i, statements);
			Optional<R> result = processor.apply(stmt, ctx);
			if (result.isPresent()) {
				return result;
			}
		}
		return Optional.empty();
	}

	// ========== Position Queries ==========

	/**
	 * Returns the current statement.
	 */
	public Statement getStatement() {
		return statement;
	}

	/**
	 * Returns the index of the current statement.
	 */
	public int getIndex() {
		return index;
	}

	/**
	 * Returns the total number of statements.
	 */
	public int getTotalCount() {
		return totalCount;
	}

	/**
	 * Returns true if this is the first statement.
	 */
	public boolean isFirst() {
		return index == 0;
	}

	/**
	 * Returns true if this is the last statement.
	 */
	public boolean isLast() {
		return index == totalCount - 1;
	}

	/**
	 * Returns true if this is the only statement.
	 */
	public boolean isOnly() {
		return totalCount == 1;
	}

	/**
	 * Returns true if this is neither first nor last.
	 */
	public boolean isMiddle() {
		return !isFirst() && !isLast();
	}

	/**
	 * Returns the number of statements remaining after this one.
	 */
	public int getRemainingCount() {
		return totalCount - index - 1;
	}

	/**
	 * Returns true if there are statements after this one.
	 */
	public boolean hasNext() {
		return index < totalCount - 1;
	}

	/**
	 * Returns true if there are statements before this one.
	 */
	public boolean hasPrevious() {
		return index > 0;
	}

	// ========== Navigation ==========

	/**
	 * Returns the next statement if available.
	 */
	public Optional<Statement> getNextStatement() {
		if (hasNext()) {
			return Optional.of(allStatements.get(index + 1));
		}
		return Optional.empty();
	}

	/**
	 * Returns the previous statement if available.
	 */
	public Optional<Statement> getPreviousStatement() {
		if (hasPrevious()) {
			return Optional.of(allStatements.get(index - 1));
		}
		return Optional.empty();
	}

	/**
	 * Returns the statement at the specified offset from the current position.
	 * 
	 * @param offset positive for forward, negative for backward
	 * @return the statement at the offset, or empty if out of bounds
	 */
	public Optional<Statement> getStatementAt(int offset) {
		int targetIndex = index + offset;
		if (targetIndex >= 0 && targetIndex < totalCount) {
			return Optional.of(allStatements.get(targetIndex));
		}
		return Optional.empty();
	}

	/**
	 * Returns all statements from the current position to the end (exclusive of current).
	 */
	public List<Statement> getRemainingStatements() {
		if (hasNext()) {
			return allStatements.subList(index + 1, totalCount);
		}
		return List.of();
	}

	/**
	 * Returns all statements from the start to the current position (exclusive of current).
	 */
	public List<Statement> getPrecedingStatements() {
		if (hasPrevious()) {
			return allStatements.subList(0, index);
		}
		return List.of();
	}

	// ========== Conditional Helpers ==========

	/**
	 * Executes the consumer only if this is the last statement.
	 */
	public StatementContext ifLast(Consumer<Statement> consumer) {
		if (isLast()) {
			consumer.accept(statement);
		}
		return this;
	}

	/**
	 * Executes the consumer only if this is not the last statement.
	 */
	public StatementContext ifNotLast(Consumer<Statement> consumer) {
		if (!isLast()) {
			consumer.accept(statement);
		}
		return this;
	}

	/**
	 * Executes the consumer only if this is the first statement.
	 */
	public StatementContext ifFirst(Consumer<Statement> consumer) {
		if (isFirst()) {
			consumer.accept(statement);
		}
		return this;
	}

	/**
	 * Executes the consumer only if this is the only statement.
	 */
	public StatementContext ifOnly(Consumer<Statement> consumer) {
		if (isOnly()) {
			consumer.accept(statement);
		}
		return this;
	}

	/**
	 * Creates a NodeMatcher for the current statement.
	 */
	public NodeMatcher<Statement> matcher() {
		return NodeMatcher.on(statement);
	}

	/**
	 * Checks if the next statement matches the given predicate.
	 */
	public boolean nextMatches(Predicate<Statement> predicate) {
		return getNextStatement().filter(predicate).isPresent();
	}

	/**
	 * Checks if the next statement is of the given type.
	 */
	public <T extends Statement> boolean nextIs(Class<T> type) {
		return getNextStatement().filter(type::isInstance).isPresent();
	}

	/**
	 * Checks if the next statement is of the given type and matches the predicate.
	 */
	public <T extends Statement> boolean nextIs(Class<T> type, Predicate<T> predicate) {
		return getNextStatement()
				.filter(type::isInstance)
				.map(type::cast)
				.filter(predicate)
				.isPresent();
	}
}
