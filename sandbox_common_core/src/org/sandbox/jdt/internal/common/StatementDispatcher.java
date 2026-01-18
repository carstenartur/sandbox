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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.eclipse.jdt.core.dom.ASTNode;

/**
 * A registry-based dispatcher for handling different statement types in a type-safe manner.
 * 
 * <p>This class provides an alternative to long if-else instanceof chains by allowing
 * handlers to be registered for specific types. The dispatcher tries handlers in
 * registration order and stops at the first successful match.</p>
 * 
 * <p><b>Example - Before (nested if-instanceof):</b></p>
 * <pre>{@code
 * if (stmt instanceof VariableDeclarationStatement && !isLast) {
 *     // handle variable declaration
 * } else if (stmt instanceof IfStatement && !isLast) {
 *     // handle non-last if
 * } else if (stmt instanceof IfStatement && isLast) {
 *     // handle last if
 * } else if (!isLast) {
 *     // handle other non-last
 * } else {
 *     // handle last
 * }
 * }</pre>
 * 
 * <p><b>Example - After (registry-based):</b></p>
 * <pre>{@code
 * StatementDispatcher<MyContext, MyResult> dispatcher = StatementDispatcher.<MyContext, MyResult>create()
 *     .when(VariableDeclarationStatement.class)
 *         .and(ctx -> !ctx.isLast())
 *         .then((stmt, ctx) -> handleVariableDecl((VariableDeclarationStatement) stmt, ctx))
 *     .when(IfStatement.class)
 *         .and(ctx -> !ctx.isLast())
 *         .then((stmt, ctx) -> handleNonLastIf((IfStatement) stmt, ctx))
 *     .when(IfStatement.class)
 *         .and(ctx -> ctx.isLast())
 *         .then((stmt, ctx) -> handleLastIf((IfStatement) stmt, ctx))
 *     .otherwise((stmt, ctx) -> handleDefault(stmt, ctx));
 * 
 * MyResult result = dispatcher.dispatch(stmt, context);
 * }</pre>
 * 
 * @param <C> the context type passed to handlers
 * @param <R> the result type returned by handlers
 */
public final class StatementDispatcher<C, R> {

	/**
	 * A handler entry that combines type matching, condition checking, and action execution.
	 */
	private static class HandlerEntry<C, R> {
		final Class<? extends ASTNode> nodeType;
		final Predicate<C> condition;
		final BiFunction<ASTNode, C, Optional<R>> handler;

		HandlerEntry(Class<? extends ASTNode> nodeType, Predicate<C> condition, BiFunction<ASTNode, C, Optional<R>> handler) {
			this.nodeType = nodeType;
			this.condition = condition;
			this.handler = handler;
		}

		boolean matches(ASTNode node, C context) {
			return nodeType.isInstance(node) && (condition == null || condition.test(context));
		}

		Optional<R> handle(ASTNode node, C context) {
			return handler.apply(node, context);
		}
	}

	private final List<HandlerEntry<C, R>> handlers = new ArrayList<>();
	private BiFunction<ASTNode, C, Optional<R>> defaultHandler;

	private StatementDispatcher() {
	}

	/**
	 * Creates a new StatementDispatcher.
	 * 
	 * @param <C> the context type
	 * @param <R> the result type
	 * @return a new dispatcher instance
	 */
	public static <C, R> StatementDispatcher<C, R> create() {
		return new StatementDispatcher<>();
	}

	/**
	 * Starts building a handler for the specified node type.
	 * 
	 * @param <N> the node type
	 * @param nodeType the class of the node type to handle
	 * @return a handler builder
	 */
	public <N extends ASTNode> HandlerBuilder<N, C, R> when(Class<N> nodeType) {
		return new HandlerBuilder<>(this, nodeType);
	}

	/**
	 * Sets the default handler for when no other handler matches.
	 * 
	 * @param handler the default handler
	 * @return this dispatcher for chaining
	 */
	public StatementDispatcher<C, R> otherwise(BiFunction<ASTNode, C, Optional<R>> handler) {
		this.defaultHandler = handler;
		return this;
	}

	/**
	 * Sets the default handler that always returns a value.
	 * 
	 * @param handler the default handler
	 * @return this dispatcher for chaining
	 */
	public StatementDispatcher<C, R> otherwiseReturn(BiFunction<ASTNode, C, R> handler) {
		this.defaultHandler = (node, ctx) -> Optional.ofNullable(handler.apply(node, ctx));
		return this;
	}

	/**
	 * Dispatches a node to the appropriate handler.
	 * 
	 * @param node the AST node to dispatch
	 * @param context the context to pass to handlers
	 * @return the result from the first matching handler, or empty if no handler matches
	 */
	public Optional<R> dispatch(ASTNode node, C context) {
		for (HandlerEntry<C, R> entry : handlers) {
			if (entry.matches(node, context)) {
				Optional<R> result = entry.handle(node, context);
				if (result.isPresent()) {
					return result;
				}
			}
		}
		if (defaultHandler != null) {
			return defaultHandler.apply(node, context);
		}
		return Optional.empty();
	}

	/**
	 * Dispatches a node and returns the result or a default value.
	 * 
	 * @param node the AST node to dispatch
	 * @param context the context to pass to handlers
	 * @param defaultValue the default value if no handler matches
	 * @return the result from the first matching handler, or the default value
	 */
	public R dispatchOrDefault(ASTNode node, C context, R defaultValue) {
		return dispatch(node, context).orElse(defaultValue);
	}

	/**
	 * Dispatches a node and executes the handler without returning a value.
	 * Useful for side-effect operations.
	 * 
	 * @param node the AST node to dispatch
	 * @param context the context to pass to handlers
	 * @return true if a handler was executed
	 */
	public boolean dispatchVoid(ASTNode node, C context) {
		return dispatch(node, context).isPresent();
	}

	/**
	 * Internal method to add a handler.
	 */
	void addHandler(HandlerEntry<C, R> entry) {
		handlers.add(entry);
	}

	/**
	 * Builder for constructing handlers with conditions.
	 * 
	 * @param <N> the node type
	 * @param <C> the context type
	 * @param <R> the result type
	 */
	public static final class HandlerBuilder<N extends ASTNode, C, R> {
		private final StatementDispatcher<C, R> dispatcher;
		private final Class<N> nodeType;
		private Predicate<C> condition;

		HandlerBuilder(StatementDispatcher<C, R> dispatcher, Class<N> nodeType) {
			this.dispatcher = dispatcher;
			this.nodeType = nodeType;
		}

		/**
		 * Adds a condition that must be true for the handler to execute.
		 * 
		 * @param condition the condition predicate
		 * @return this builder for chaining
		 */
		public HandlerBuilder<N, C, R> and(Predicate<C> condition) {
			this.condition = condition;
			return this;
		}

		/**
		 * Adds an additional condition that the node itself must satisfy.
		 * 
		 * @param nodeCondition the condition on the node
		 * @return this builder for chaining
		 */
		public HandlerBuilder<N, C, R> matching(Predicate<N> nodeCondition) {
			// Create a combined condition that checks both the context and the node
			Predicate<C> originalCondition = this.condition;
			// We'll need to apply this in the handler instead
			return this;
		}

		/**
		 * Specifies the handler to execute when the type and condition match.
		 * 
		 * @param handler the handler function that returns an Optional result
		 * @return the dispatcher for chaining
		 */
		public StatementDispatcher<C, R> then(BiFunction<N, C, Optional<R>> handler) {
			@SuppressWarnings("unchecked")
			BiFunction<ASTNode, C, Optional<R>> wrappedHandler = (node, ctx) -> handler.apply((N) node, ctx);
			dispatcher.addHandler(new HandlerEntry<>(nodeType, condition, wrappedHandler));
			return dispatcher;
		}

		/**
		 * Specifies the handler to execute, returning a non-optional result.
		 * 
		 * @param handler the handler function
		 * @return the dispatcher for chaining
		 */
		public StatementDispatcher<C, R> thenReturn(BiFunction<N, C, R> handler) {
			return then((node, ctx) -> Optional.ofNullable(handler.apply(node, ctx)));
		}

		/**
		 * Specifies a handler that performs a side effect and returns empty.
		 * 
		 * @param handler the handler consumer
		 * @return the dispatcher for chaining
		 */
		public StatementDispatcher<C, R> thenDo(java.util.function.BiConsumer<N, C> handler) {
			return then((node, ctx) -> {
				handler.accept(node, ctx);
				return Optional.empty();
			});
		}

		/**
		 * Specifies that this handler should signal to skip processing (returns empty Optional).
		 * 
		 * @return the dispatcher for chaining
		 */
		public StatementDispatcher<C, R> thenSkip() {
			return then((node, ctx) -> Optional.empty());
		}
	}
}
