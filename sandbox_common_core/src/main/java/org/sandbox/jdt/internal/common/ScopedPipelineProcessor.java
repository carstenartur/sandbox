package org.sandbox.jdt.internal.common;

/*-
 * #%L
 * Sandbox common
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

import org.eclipse.jdt.core.dom.ASTNode;

/**
 * An ordered pipeline processor for scoped AST traversal chains.
 *
 * <p>Unlike {@link ASTProcessor}, which stores stages in a {@code LinkedHashMap} keyed by
 * {@link VisitorEnum} (which prevents repeated node types and silently falls through to
 * the next stage when a node is not found), this processor stores stages in an ordered
 * {@link List}. Its semantics are:</p>
 *
 * <ul>
 *   <li>Stages are executed in registration order.</li>
 *   <li>The same {@link VisitorEnum} may appear more than once (e.g. Assignment → Assignment).</li>
 *   <li>When a stage finds no matching node in its scope, that branch of the pipeline ends.
 *       The next stage is <em>not</em> run against the original scope.</li>
 *   <li>When a stage finds one or more matching nodes, the next stage runs once per match,
 *       scoped to the node (or to the result of the optional navigation function).</li>
 * </ul>
 *
 * <p>This class is used internally by
 * {@link AstProcessorBuilder.ScopedPipelineBuilder}.</p>
 *
 * @param <E> the holder type (must implement {@link HelperVisitorProvider})
 * @param <V> the map key type of the holder
 * @param <T> the map value type of the holder
 * @since 1.17
 */
public class ScopedPipelineProcessor<E extends HelperVisitorProvider<V, T, E>, V, T> {

	/**
	 * Immutable description of one stage in the pipeline.
	 */
	static final class PipelineStage<E> {
		final VisitorEnum nodeType;
		final BiPredicate<ASTNode, E> predicate;
		final VisitorConfigData configData; // may be null
		/**
		 * Optional function that maps the matched node to the scope for the next stage.
		 * When {@code null} the matched node itself is used as the next scope.
		 */
		Function<ASTNode, ASTNode> navigate;

		PipelineStage(VisitorEnum nodeType,
				BiPredicate<ASTNode, E> predicate,
				VisitorConfigData configData,
				Function<ASTNode, ASTNode> navigate) {
			this.nodeType= nodeType;
			this.predicate= predicate;
			this.configData= configData;
			this.navigate= navigate;
		}
	}

	private final List<PipelineStage<E>> stages= new ArrayList<>();
	final E dataholder;
	final Set<ASTNode> nodesprocessed;

	/**
	 * Creates a new scoped pipeline processor.
	 *
	 * @param dataholder     the holder shared across all stages
	 * @param nodesprocessed the set used to track already-visited nodes
	 */
	public ScopedPipelineProcessor(E dataholder, Set<ASTNode> nodesprocessed) {
		this.dataholder= dataholder;
		this.nodesprocessed= nodesprocessed;
	}

	/**
	 * Appends a new stage to the pipeline.
	 *
	 * @param nodeType   the AST node type this stage matches
	 * @param predicate  called for each candidate node; return {@code true} to signal a match
	 * @param configData additional filter configuration (may be {@code null})
	 * @param navigate   maps a matched node to the scope for the next stage (may be {@code null})
	 * @return this processor for chaining
	 */
	public ScopedPipelineProcessor<E, V, T> addStage(
			VisitorEnum nodeType,
			BiPredicate<ASTNode, E> predicate,
			VisitorConfigData configData,
			Function<ASTNode, ASTNode> navigate) {
		stages.add(new PipelineStage<>(nodeType, predicate, configData, navigate));
		return this;
	}

	/**
	 * Updates the navigation function of the most recently added stage.
	 *
	 * @param navigate maps a matched node to the scope for the next stage
	 * @throws IllegalStateException if no stage has been added yet
	 */
	public void setNavigateOnLastStage(Function<ASTNode, ASTNode> navigate) {
		if (stages.isEmpty()) {
			throw new IllegalStateException("Cannot set navigate: no stage has been added yet"); //$NON-NLS-1$
		}
		stages.get(stages.size() - 1).navigate= navigate;
	}

	/**
	 * Executes the pipeline starting at the given root node.
	 *
	 * @param root the AST node from which the first stage begins traversal
	 */
	public void build(ASTNode root) {
		if (!stages.isEmpty()) {
			processStage(root, 0);
		}
	}

	/**
	 * Runs stage {@code i} against {@code scope}, then for every match advances to stage {@code i+1}.
	 * If no match is found the pipeline branch ends silently (no fallthrough to the next stage).
	 */
	private void processStage(ASTNode scope, int i) {
		if (i >= stages.size() || scope == null) {
			return;
		}
		PipelineStage<E> stage= stages.get(i);
		HelperVisitor<E, V, T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		boolean[] anyMatch= { false };

		BiPredicate<ASTNode, E> wrappedPredicate= (node, holder) -> {
			boolean matched= stage.predicate.test(node, holder);
			if (matched) {
				anyMatch[0]= true;
				ASTNode nextScope= (stage.navigate != null) ? stage.navigate.apply(node) : node;
				processStage(nextScope, i + 1);
			}
			return matched;
		};
		if (stage.configData != null) {
			hv.add(stage.configData, stage.nodeType, wrappedPredicate);
		} else {
			hv.add(stage.nodeType, wrappedPredicate);
		}

		hv.build(scope);
		// No fallthrough: if anyMatch[0] == false we simply return without running stage i+1.
		// This is the key behavioral difference from ASTProcessor.process().
	}
}
