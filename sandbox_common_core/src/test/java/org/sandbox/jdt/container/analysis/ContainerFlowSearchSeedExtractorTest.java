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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.container.api.ContainerFlowGraph;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile;

class ContainerFlowSearchSeedExtractorTest {

	private final AppendOnlyArraySeedDetector seedDetector= new AppendOnlyArraySeedDetector();
	private final LocalContainerFlowGraphBuilder graphBuilder= new LocalContainerFlowGraphBuilder();
	private final ContainerFlowSearchSeedExtractor extractor= new ContainerFlowSearchSeedExtractor();

	@Test
	void locallyClosedGraphNeedsNoSearch() {
		ContainerFlowSearchPlan plan= extract("""
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					System.out.println(values.length);
				}
			}
			""");

		assertTrue(plan.isEmpty());
	}

	@Test
	void externalBoundaryDoesNotPretendToBeSearchable() {
		ContainerFlowSearchPlan plan= extract("""
			import java.util.Arrays;
			class Sample {
				int collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					return System.identityHashCode(values);
				}
			}
			""");

		assertTrue(plan.isEmpty());
	}

	@Test
	void sourceParameterCreatesCallerAndOverrideSearches() {
		ContainerFlowSearchPlan plan= extract("""
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					consume(values);
				}
				void consume(String[] input) {
					System.out.println(input.length);
				}
			}
			""");

		assertFalse(plan.isEmpty());
		Set<SearchKind> kinds= plan.seeds().stream()
				.map(ContainerFlowSearchPlan.SearchSeed::kind)
				.collect(Collectors.toSet());
		assertEquals(Set.of(SearchKind.METHOD_CALLERS, SearchKind.METHOD_OVERRIDE_FAMILY), kinds);
		assertTrue(plan.seeds().stream().allMatch(seed -> seed.signatureIndex() == 0));
	}

	private ContainerFlowSearchPlan extract(String source) {
		CompilationUnit unit= parse(source);
		ContainerUsageProfile seed= seedDetector.findSeeds(unit).get(0);
		ContainerFlowGraph graph= graphBuilder.build(unit, seed);
		return extractor.extract(graph);
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		parser.setUnitName("Sample.java"); //$NON-NLS-1$
		parser.setEnvironment(new String[0], new String[0], new String[0], true);
		return (CompilationUnit) parser.createAST(null);
	}
}
