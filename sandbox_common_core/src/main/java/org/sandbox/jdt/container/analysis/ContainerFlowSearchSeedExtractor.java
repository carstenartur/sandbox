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
package org.sandbox.jdt.container.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.sandbox.jdt.container.api.ContainerFlowGraph;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchKind;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchSeed;

/** Converts graph boundaries into duplicate-free, stable source-search intentions. */
public final class ContainerFlowSearchSeedExtractor {

	/** Derives searches needed to expand the current source closure. */
	public ContainerFlowSearchPlan extract(ContainerFlowGraph graph) {
		Objects.requireNonNull(graph, "graph"); //$NON-NLS-1$
		if (graph.closureStatus() != ClosureStatus.REQUIRES_SCOPE_EXPANSION) {
			return new ContainerFlowSearchPlan(List.of());
		}

		Map<String, SearchSeed> seeds= new LinkedHashMap<>();
		for (FlowNode node : graph.nodes()) {
			switch (node.kind()) {
				case FIELD -> addFieldReferences(node, seeds);
				case PARAMETER -> addMethodSignatureSearches(node, seeds, true);
				case RETURN_POSITION -> addMethodSignatureSearches(node, seeds, false);
				case LOCAL_VARIABLE, EXTERNAL_PARAMETER, UNKNOWN_BOUNDARY -> {
					// No source-scope expansion can be derived from this node alone.
				}
			}
		}
		return new ContainerFlowSearchPlan(new ArrayList<>(seeds.values()));
	}

	private static void addFieldReferences(
			FlowNode node,
			Map<String, SearchSeed> seeds) {
		if (node.bindingKey().isBlank()) {
			return;
		}
		add(seeds, new SearchSeed(
				node.stableId(),
				SearchKind.FIELD_REFERENCES,
				node.bindingKey(),
				node.ownerKey(),
				node.javaElementHandle(),
				-1,
				"Find all source reads and writes of the field participating in container flow.")); //$NON-NLS-1$
	}

	private static void addMethodSignatureSearches(
			FlowNode node,
			Map<String, SearchSeed> seeds,
			boolean includeDeclaration) {
		if (node.ownerKey().isBlank()) {
			return;
		}
		if (includeDeclaration && !node.sourceResolved()) {
			add(seeds, new SearchSeed(
					node.stableId(),
					SearchKind.METHOD_DECLARATION,
					node.bindingKey(),
					node.ownerKey(),
					node.javaElementHandle(),
					node.signatureIndex(),
					"Resolve the source declaration that owns the parameter position.")); //$NON-NLS-1$
		}
		add(seeds, new SearchSeed(
				node.stableId(),
				SearchKind.METHOD_CALLERS,
				node.bindingKey(),
				node.ownerKey(),
				node.javaElementHandle(),
				node.signatureIndex(),
				includeDeclaration
						? "Find callers that pass values into the parameter position." //$NON-NLS-1$
						: "Find callers that consume the method return value.")); //$NON-NLS-1$
		add(seeds, new SearchSeed(
				node.stableId(),
				SearchKind.METHOD_OVERRIDE_FAMILY,
				node.bindingKey(),
				node.ownerKey(),
				node.javaElementHandle(),
				node.signatureIndex(),
				"Find all source declarations in the method override and implementation family.")); //$NON-NLS-1$
	}

	private static void add(Map<String, SearchSeed> seeds, SearchSeed seed) {
		seeds.putIfAbsent(seed.stableKey(), seed);
	}
}
