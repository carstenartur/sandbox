/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.common;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.jdt.core.dom.ASTNode;

/**
 * Builds an ordered semantic AST pipeline.
 *
 * <p>Each successful stage selects the scope in which the next stage searches. A
 * stage that finds no matching node ends that branch of the pipeline; later stages
 * are never retried independently against an earlier scope.</p>
 *
 * <p>Matcher, handler, navigation and child traversal are separate concerns. The
 * matcher decides whether the semantic chain advances. The handler records facts.
 * Navigation chooses the next search scope. {@link TraversalDecision} controls only
 * whether the current visitor descends below the matched node.</p>
 *
 * @param <V> reference-holder key type
 * @param <T> reference-holder value type
 */
public final class ScopedAstProcessorBuilder<V, T> {

	/** Child-traversal decision for the visitor executing one matched stage. */
	public enum TraversalDecision {
		DESCEND,
		SKIP_CHILDREN
	}

	private final ReferenceHolder<V, T> holder;
	private final List<Stage<V, T>> stages= new ArrayList<>();
	private Set<ASTNode> excludedNodes= Set.of();

	ScopedAstProcessorBuilder(ReferenceHolder<V, T> holder) {
		this.holder= holder;
	}

	/**
	 * Registers the first stage of the scoped pipeline.
	 *
	 * @param <N> AST node type
	 * @param nodeType node class to search
	 * @param matcher semantic match predicate
	 * @param handler processing callback
	 * @param nextScope navigation to the scope for the next stage
	 * @return this builder
	 */
	public <N extends ASTNode> ScopedAstProcessorBuilder<V, T> find(
			Class<N> nodeType,
			Predicate<? super N> matcher,
			BiConsumer<? super N, ReferenceHolder<V, T>> handler,
			Function<? super N, ? extends ASTNode> nextScope) {
		if (!stages.isEmpty()) {
			throw new IllegalStateException("find(...) may only register the first stage"); //$NON-NLS-1$
		}
		return addStage(nodeType, matcher, consumerHandler(handler), nextScope);
	}

	/** Registers the first stage without navigation beyond the matched node. */
	public <N extends ASTNode> ScopedAstProcessorBuilder<V, T> find(
			Class<N> nodeType,
			Predicate<? super N> matcher,
			BiConsumer<? super N, ReferenceHolder<V, T>> handler) {
		return find(nodeType, matcher, handler, Function.identity());
	}

	/**
	 * Registers a dependent stage searched only in the scope selected by the preceding
	 * successful stage.
	 */
	public <N extends ASTNode> ScopedAstProcessorBuilder<V, T> then(
			Class<N> nodeType,
			Predicate<? super N> matcher,
			BiConsumer<? super N, ReferenceHolder<V, T>> handler,
			Function<? super N, ? extends ASTNode> nextScope) {
		requireFirstStage();
		return addStage(nodeType, matcher, consumerHandler(handler), nextScope);
	}

	/** Registers a dependent terminal stage. */
	public <N extends ASTNode> ScopedAstProcessorBuilder<V, T> then(
			Class<N> nodeType,
			Predicate<? super N> matcher,
			BiConsumer<? super N, ReferenceHolder<V, T>> handler) {
		return then(nodeType, matcher, handler, Function.identity());
	}

	/**
	 * Registers the first stage with explicit child-traversal control.
	 */
	public <N extends ASTNode> ScopedAstProcessorBuilder<V, T> findWithTraversal(
			Class<N> nodeType,
			Predicate<? super N> matcher,
			BiFunction<? super N, ReferenceHolder<V, T>, TraversalDecision> handler,
			Function<? super N, ? extends ASTNode> nextScope) {
		if (!stages.isEmpty()) {
			throw new IllegalStateException("findWithTraversal(...) may only register the first stage"); //$NON-NLS-1$
		}
		return addStage(nodeType, matcher, handler, nextScope);
	}

	/** Registers a dependent stage with explicit child-traversal control. */
	public <N extends ASTNode> ScopedAstProcessorBuilder<V, T> thenWithTraversal(
			Class<N> nodeType,
			Predicate<? super N> matcher,
			BiFunction<? super N, ReferenceHolder<V, T>, TraversalDecision> handler,
			Function<? super N, ? extends ASTNode> nextScope) {
		requireFirstStage();
		return addStage(nodeType, matcher, handler, nextScope);
	}

	/** Applies the same initial exclusion set to every stage traversal. */
	public ScopedAstProcessorBuilder<V, T> excluding(Set<? extends ASTNode> nodes) {
		excludedNodes= Set.copyOf(Objects.requireNonNull(nodes, "nodes")); //$NON-NLS-1$
		return this;
	}

	/** Executes the scoped pipeline against {@code root}. */
	public void build(ASTNode root) {
		Objects.requireNonNull(root, "root"); //$NON-NLS-1$
		if (stages.isEmpty()) {
			throw new IllegalStateException("At least one scoped stage is required"); //$NON-NLS-1$
		}
		process(root, 0);
	}

	private void process(ASTNode scope, int stageIndex) {
		if (scope == null || stageIndex >= stages.size()) {
			return;
		}

		Stage<V, T> stage= stages.get(stageIndex);
		HelperVisitor<ReferenceHolder<V, T>, V, T> visitor=
				new HelperVisitor<>(new HashSet<>(excludedNodes), holder);
		visitor.add(stage.visitorType(), (node, data) -> {
			if (!stage.nodeType().isInstance(node) || !stage.matcher().test(node)) {
				return true;
			}

			TraversalDecision traversal= stage.handler().apply(node, data);
			if (stageIndex + 1 < stages.size()) {
				ASTNode nextScope= stage.nextScope().apply(node);
				if (nextScope != null) {
					process(nextScope, stageIndex + 1);
				}
			}
			return traversal == TraversalDecision.DESCEND;
		});
		visitor.build(scope);
	}

	private <N extends ASTNode> ScopedAstProcessorBuilder<V, T> addStage(
			Class<N> nodeType,
			Predicate<? super N> matcher,
			BiFunction<? super N, ReferenceHolder<V, T>, TraversalDecision> handler,
			Function<? super N, ? extends ASTNode> nextScope) {
		Objects.requireNonNull(nodeType, "nodeType"); //$NON-NLS-1$
		Objects.requireNonNull(matcher, "matcher"); //$NON-NLS-1$
		Objects.requireNonNull(handler, "handler"); //$NON-NLS-1$
		Objects.requireNonNull(nextScope, "nextScope"); //$NON-NLS-1$
		VisitorEnum visitorType= AstProcessing.visitorType(nodeType);
		stages.add(new Stage<>(
				nodeType,
				visitorType,
				node -> matcher.test(nodeType.cast(node)),
				(node, data) -> Objects.requireNonNull(
						handler.apply(nodeType.cast(node), data), "traversalDecision"), //$NON-NLS-1$
				node -> nextScope.apply(nodeType.cast(node))));
		return this;
	}

	private static <N extends ASTNode, V, T>
			BiFunction<N, ReferenceHolder<V, T>, TraversalDecision> consumerHandler(
					BiConsumer<? super N, ReferenceHolder<V, T>> handler) {
		Objects.requireNonNull(handler, "handler"); //$NON-NLS-1$
		return (node, holder) -> {
			handler.accept(node, holder);
			return TraversalDecision.DESCEND;
		};
	}

	private void requireFirstStage() {
		if (stages.isEmpty()) {
			throw new IllegalStateException("then(...) requires a preceding find(...) stage"); //$NON-NLS-1$
		}
	}

	private record Stage<V, T>(
			Class<? extends ASTNode> nodeType,
			VisitorEnum visitorType,
			Predicate<ASTNode> matcher,
			BiFunction<ASTNode, ReferenceHolder<V, T>, TraversalDecision> handler,
			Function<ASTNode, ? extends ASTNode> nextScope) {
	}
}
