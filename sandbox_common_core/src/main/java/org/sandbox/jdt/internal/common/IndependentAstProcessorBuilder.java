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
import java.util.function.BiPredicate;

import org.eclipse.jdt.core.dom.ASTNode;

/**
 * Collects independent AST visitors that each inspect the complete root supplied to
 * {@link #build(ASTNode)}.
 *
 * <p>Registration order is deterministic, but one visitor never narrows the scope of
 * another. Use {@link AstProcessing#scoped(ReferenceHolder)} when later stages are
 * semantically dependent on an earlier match.</p>
 *
 * @param <V> reference-holder key type
 * @param <T> reference-holder value type
 */
public final class IndependentAstProcessorBuilder<V, T> {

	private final ReferenceHolder<V, T> holder;
	private final List<Stage<V, T>> stages= new ArrayList<>();
	private Set<ASTNode> excludedNodes= Set.of();

	IndependentAstProcessorBuilder(ReferenceHolder<V, T> holder) {
		this.holder= holder;
	}

	/**
	 * Registers one typed visitor that will inspect the complete build root.
	 *
	 * @param <N> AST node type
	 * @param nodeType node class to visit
	 * @param handler processing callback; its result controls child traversal
	 * @return this builder
	 */
	public <N extends ASTNode> IndependentAstProcessorBuilder<V, T> on(
			Class<N> nodeType,
			BiPredicate<? super N, ReferenceHolder<V, T>> handler) {
		Objects.requireNonNull(nodeType, "nodeType"); //$NON-NLS-1$
		Objects.requireNonNull(handler, "handler"); //$NON-NLS-1$
		VisitorEnum visitorType= AstProcessing.visitorType(nodeType);
		stages.add(new Stage<>(visitorType,
				(node, data) -> handler.test(nodeType.cast(node), data)));
		return this;
	}

	/** Applies one shared exclusion set to every independent visitor traversal. */
	public IndependentAstProcessorBuilder<V, T> excluding(Set<? extends ASTNode> nodes) {
		excludedNodes= Set.copyOf(Objects.requireNonNull(nodes, "nodes")); //$NON-NLS-1$
		return this;
	}

	/** Executes every registered visitor independently against {@code root}. */
	public void build(ASTNode root) {
		Objects.requireNonNull(root, "root"); //$NON-NLS-1$
		for (Stage<V, T> stage : stages) {
			HelperVisitor<ReferenceHolder<V, T>, V, T> visitor=
					new HelperVisitor<>(new HashSet<>(excludedNodes), holder);
			visitor.add(stage.visitorType(), stage.handler());
			visitor.build(root);
		}
	}

	private record Stage<V, T>(
			VisitorEnum visitorType,
			BiPredicate<ASTNode, ReferenceHolder<V, T>> handler) {
	}
}
