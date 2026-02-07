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
package org.sandbox.ast.api.visitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.sandbox.ast.api.core.ASTWrapper;
import org.sandbox.ast.api.expr.ASTExpr;
import org.sandbox.ast.api.expr.CastExpr;
import org.sandbox.ast.api.expr.FieldAccessExpr;
import org.sandbox.ast.api.expr.InfixExpr;
import org.sandbox.ast.api.expr.MethodInvocationExpr;
import org.sandbox.ast.api.expr.SimpleNameExpr;
import org.sandbox.ast.api.stmt.ASTStmt;
import org.sandbox.ast.api.stmt.EnhancedForStmt;
import org.sandbox.ast.api.stmt.ForLoopStmt;
import org.sandbox.ast.api.stmt.IfStmt;
import org.sandbox.ast.api.stmt.WhileLoopStmt;

/**
 * Fluent, type-safe visitor builder for AST traversal and pattern matching.
 * Provides a declarative API for handling different AST node types without
 * verbose instanceof checks and casts.
 *
 * <p>Example usage:</p>
 * <pre>
 * FluentVisitor visitor = FluentVisitor.builder()
 *     .onMethodInvocation(mi -&gt; {
 *         System.out.println("Found method call: " + mi.methodName());
 *     })
 *     .onSimpleName(sn -&gt; {
 *         System.out.println("Found name: " + sn.identifier());
 *     })
 *     .build();
 *
 * visitor.visit(expression);
 * </pre>
 *
 * <p>With predicates:</p>
 * <pre>
 * FluentVisitor visitor = FluentVisitor.builder()
 *     .onMethodInvocation()
 *         .when(mi -&gt; mi.methodName().equals(Optional.of("add")))
 *         .then(mi -&gt; System.out.println("Found add call"))
 *     .onSimpleName()
 *         .filter(sn -&gt; sn.identifier().startsWith("test"))
 *         .then(sn -&gt; System.out.println("Found test name"))
 *     .build();
 * </pre>
 */
		public interface FluentVisitor {

			/**
 * Visits a single AST node.
 *
 * @param node the node to visit
 */
			void visit(ASTWrapper node);

			/**
 * Visits multiple AST nodes.
 *
 * @param nodes the nodes to visit
 */
			default void visitAll(List<? extends ASTWrapper> nodes) {
				if (nodes != null) {
					nodes.forEach(this::visit);
				}
			}

			/**
 * Combines this visitor with another visitor.
 * Both visitors will be invoked for each node.
 *
 * @param other the other visitor
 * @return a combined visitor
 */
			default FluentVisitor andThen(FluentVisitor other) {
				return node -> {
					if (node == null) {
						return;
					}
					this.visit(node);
					other.visit(node);
				};
			}

			/**
 * Creates a new fluent visitor builder.
 *
 * @return a new builder instance
 */
			static Builder builder() {
				return new Builder();
			}

			/**
 * Builder for creating fluent visitors with type-safe pattern matching.
 */
			class Builder {
				private final List<Consumer<ASTWrapper>> handlers = new ArrayList<>();

				/**
 * Registers a handler for method invocation expressions.
 *
 * @param handler the handler to invoke for each method invocation
 * @return this builder
 */
				public Builder onMethodInvocation(Consumer<MethodInvocationExpr> handler) {
					handlers.add(node -> {
						if (node instanceof ASTExpr expr) {
							expr.asMethodInvocation().ifPresent(handler);
						}
					});
					return this;
				}

				/**
 * Starts a predicate-based handler for method invocations.
 *
 * @return a conditional handler builder
 */
				public ConditionalHandler<MethodInvocationExpr> onMethodInvocation() {
					return new ConditionalHandler<>(this, node -> {
						if (node instanceof ASTExpr expr) {
							return expr.asMethodInvocation();
						}
						return Optional.empty();
					});
				}

				/**
 * Registers a handler for simple name expressions.
 *
 * @param handler the handler to invoke for each simple name
 * @return this builder
 */
				public Builder onSimpleName(Consumer<SimpleNameExpr> handler) {
					handlers.add(node -> {
						if (node instanceof ASTExpr expr) {
							expr.asSimpleName().ifPresent(handler);
						}
					});
					return this;
				}

				/**
 * Starts a predicate-based handler for simple names.
 *
 * @return a conditional handler builder
 */
				public ConditionalHandler<SimpleNameExpr> onSimpleName() {
					return new ConditionalHandler<>(this, node -> {
						if (node instanceof ASTExpr expr) {
							return expr.asSimpleName();
						}
						return Optional.empty();
					});
				}

				/**
 * Registers a handler for field access expressions.
 *
 * @param handler the handler to invoke for each field access
 * @return this builder
 */
				public Builder onFieldAccess(Consumer<FieldAccessExpr> handler) {
					handlers.add(node -> {
						if (node instanceof ASTExpr expr) {
							expr.asFieldAccess().ifPresent(handler);
						}
					});
					return this;
				}

				/**
 * Starts a predicate-based handler for field access.
 *
 * @return a conditional handler builder
 */
				public ConditionalHandler<FieldAccessExpr> onFieldAccess() {
					return new ConditionalHandler<>(this, node -> {
						if (node instanceof ASTExpr expr) {
							return expr.asFieldAccess();
						}
						return Optional.empty();
					});
				}

				/**
 * Registers a handler for cast expressions.
 *
 * @param handler the handler to invoke for each cast
 * @return this builder
 */
				public Builder onCast(Consumer<CastExpr> handler) {
					handlers.add(node -> {
						if (node instanceof ASTExpr expr) {
							expr.asCast().ifPresent(handler);
						}
					});
					return this;
				}

				/**
 * Starts a predicate-based handler for casts.
 *
 * @return a conditional handler builder
 */
				public ConditionalHandler<CastExpr> onCast() {
					return new ConditionalHandler<>(this, node -> {
						if (node instanceof ASTExpr expr) {
							return expr.asCast();
						}
						return Optional.empty();
					});
				}

				/**
 * Registers a handler for infix expressions.
 *
 * @param handler the handler to invoke for each infix expression
 * @return this builder
 */
				public Builder onInfix(Consumer<InfixExpr> handler) {
					handlers.add(node -> {
						if (node instanceof ASTExpr expr) {
							expr.asInfix().ifPresent(handler);
						}
					});
					return this;
				}

				/**
 * Starts a predicate-based handler for infix expressions.
 *
 * @return a conditional handler builder
 */
				public ConditionalHandler<InfixExpr> onInfix() {
					return new ConditionalHandler<>(this, node -> {
						if (node instanceof ASTExpr expr) {
							return expr.asInfix();
						}
						return Optional.empty();
					});
				}

				/**
 * Registers a handler for any expression type.
 *
 * @param handler the handler to invoke for each expression
 * @return this builder
 */
				public Builder onExpression(Consumer<ASTExpr> handler) {
					handlers.add(node -> {
						if (node instanceof ASTExpr expr) {
							handler.accept(expr);
						}
					});
					return this;
				}

				/**
 * Registers a handler for enhanced for statements.
 *
 * @param handler the handler to invoke for each enhanced for
 * @return this builder
 */
				public Builder onEnhancedFor(Consumer<EnhancedForStmt> handler) {
					handlers.add(node -> {
						if (node instanceof ASTStmt stmt) {
							stmt.asEnhancedFor().ifPresent(handler);
						}
					});
					return this;
				}

				/**
 * Starts a predicate-based handler for enhanced for statements.
 *
 * @return a conditional handler builder
 */
				public ConditionalHandler<EnhancedForStmt> onEnhancedFor() {
					return new ConditionalHandler<>(this, node -> {
						if (node instanceof ASTStmt stmt) {
							return stmt.asEnhancedFor();
						}
						return Optional.empty();
					});
				}

				/**
 * Registers a handler for while loop statements.
 *
 * @param handler the handler to invoke for each while loop
 * @return this builder
 */
				public Builder onWhileLoop(Consumer<WhileLoopStmt> handler) {
					handlers.add(node -> {
						if (node instanceof ASTStmt stmt) {
							stmt.asWhileLoop().ifPresent(handler);
						}
					});
					return this;
				}

				/**
 * Starts a predicate-based handler for while loops.
 *
 * @return a conditional handler builder
 */
				public ConditionalHandler<WhileLoopStmt> onWhileLoop() {
					return new ConditionalHandler<>(this, node -> {
						if (node instanceof ASTStmt stmt) {
							return stmt.asWhileLoop();
						}
						return Optional.empty();
					});
				}

				/**
 * Registers a handler for for loop statements.
 *
 * @param handler the handler to invoke for each for loop
 * @return this builder
 */
				public Builder onForLoop(Consumer<ForLoopStmt> handler) {
					handlers.add(node -> {
						if (node instanceof ASTStmt stmt) {
							stmt.asForLoop().ifPresent(handler);
						}
					});
					return this;
				}

				/**
 * Starts a predicate-based handler for for loops.
 *
 * @return a conditional handler builder
 */
				public ConditionalHandler<ForLoopStmt> onForLoop() {
					return new ConditionalHandler<>(this, node -> {
						if (node instanceof ASTStmt stmt) {
							return stmt.asForLoop();
						}
						return Optional.empty();
					});
				}

				/**
 * Registers a handler for if statements.
 *
 * @param handler the handler to invoke for each if statement
 * @return this builder
 */
				public Builder onIfStatement(Consumer<IfStmt> handler) {
					handlers.add(node -> {
						if (node instanceof ASTStmt stmt) {
							stmt.asIfStatement().ifPresent(handler);
						}
					});
					return this;
				}

				/**
 * Starts a predicate-based handler for if statements.
 *
 * @return a conditional handler builder
 */
				public ConditionalHandler<IfStmt> onIfStatement() {
					return new ConditionalHandler<>(this, node -> {
						if (node instanceof ASTStmt stmt) {
							return stmt.asIfStatement();
						}
						return Optional.empty();
					});
				}

				/**
 * Registers a handler for any statement type.
 *
 * @param handler the handler to invoke for each statement
 * @return this builder
 */
				public Builder onStatement(Consumer<ASTStmt> handler) {
					handlers.add(node -> {
						if (node instanceof ASTStmt stmt) {
							handler.accept(stmt);
						}
					});
					return this;
				}

				/**
 * Registers a handler for any AST node.
 *
 * @param handler the handler to invoke for each node
 * @return this builder
 */
				public Builder onAny(Consumer<ASTWrapper> handler) {
					handlers.add(handler);
					return this;
				}

				/**
 * Builds the fluent visitor.
 *
 * @return a new fluent visitor instance
 */
				public FluentVisitor build() {
					// Create immutable copy of handlers
					List<Consumer<ASTWrapper>> handlersCopy = List.copyOf(handlers);
					return node -> {
						if (node != null) {
							handlersCopy.forEach(handler -> handler.accept(node));
						}
					};
				}
			}

			/**
 * Builder for conditional handlers with predicate filtering.
 *
 * @param <T> the node type being handled
 */
			class ConditionalHandler<T> {
				private final Builder builder;
				private final java.util.function.Function<ASTWrapper, Optional<T>> extractor;
				private Predicate<T> predicate = t -> true;

				ConditionalHandler(Builder builder, java.util.function.Function<ASTWrapper, Optional<T>> extractor) {
					this.builder = builder;
					this.extractor = extractor;
				}

				/**
 * Adds a condition that must be satisfied for the handler to execute.
 *
 * @param condition the condition to check
 * @return this conditional handler
 */
				public ConditionalHandler<T> when(Predicate<T> condition) {
					this.predicate = this.predicate.and(condition);
					return this;
				}

				/**
 * Alias for {@link #when(Predicate)}.
 *
 * @param condition the condition to check
 * @return this conditional handler
 */
				public ConditionalHandler<T> filter(Predicate<T> condition) {
					return when(condition);
				}

				/**
 * Registers the handler action and returns to the builder.
 *
 * @param handler the handler to invoke when conditions are met
 * @return the parent builder
 */
				public Builder then(Consumer<T> handler) {
					builder.handlers.add(node -> {
						extractor.apply(node)
						.filter(predicate)
						.ifPresent(handler);
					});
					return builder;
				}
			}
		}
