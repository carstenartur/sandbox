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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.container.api.ContainerFlowGraph;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ContainerIdentity;

class LocalContainerFlowGraphBuilderTest {

	private final AppendOnlyArraySeedDetector seedDetector= new AppendOnlyArraySeedDetector();
	private final LocalContainerFlowGraphBuilder graphBuilder= new LocalContainerFlowGraphBuilder();

	@Test
	void followsLocalAliasesToAFixedPoint() {
		ContainerFlowGraph graph= build("""
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					String[] alias = values;
					String[] second = alias;
					System.out.println(second.length);
				}
			}
			""");

		assertEquals(ClosureStatus.LOCAL_CLOSED, graph.closureStatus());
		assertEquals(3, graph.nodes().stream()
				.filter(node -> node.kind() == NodeKind.LOCAL_VARIABLE)
				.count());
		assertEquals(2, graph.edges().stream()
				.filter(edge -> edge.kind() == EdgeKind.INITIALIZER)
				.count());
		assertTrue(graph.diagnostics().isEmpty());
		assertEquals(1, graph.outgoing(graph.rootNodeId()).size());
	}

	@Test
	void connectsArgumentToLocalParameterAndRequestsScopeExpansion() {
		ContainerFlowGraph graph= build("""
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

		assertEquals(ClosureStatus.REQUIRES_SCOPE_EXPANSION, graph.closureStatus());
		assertTrue(graph.nodes().stream().anyMatch(node -> node.kind() == NodeKind.PARAMETER));
		assertTrue(graph.edges().stream()
				.anyMatch(edge -> edge.kind() == EdgeKind.ARGUMENT_TO_PARAMETER));
		assertTrue(hasDiagnostic(graph, DiagnosticKind.SCOPE_EXPANSION_REQUIRED));
	}

	@Test
	void connectsReturnValueToMethodReturnPosition() {
		ContainerFlowGraph graph= build("""
			import java.util.Arrays;
			class Sample {
				String[] collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					return values;
				}
			}
			""");

		assertEquals(ClosureStatus.REQUIRES_SCOPE_EXPANSION, graph.closureStatus());
		assertTrue(graph.nodes().stream()
				.anyMatch(node -> node.kind() == NodeKind.RETURN_POSITION));
		assertTrue(graph.edges().stream()
				.anyMatch(edge -> edge.kind() == EdgeKind.RETURN_TO_METHOD));
		assertTrue(hasDiagnostic(graph, DiagnosticKind.SCOPE_EXPANSION_REQUIRED));
	}

	@Test
	void stopsAtExternalBinaryParameterBoundary() {
		ContainerFlowGraph graph= build("""
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

		assertEquals(ClosureStatus.EXTERNAL_BOUNDARY, graph.closureStatus());
		assertTrue(graph.nodes().stream()
				.anyMatch(node -> node.kind() == NodeKind.EXTERNAL_PARAMETER));
		assertTrue(hasDiagnostic(graph, DiagnosticKind.EXTERNAL_OR_BINARY_TARGET));
	}

	@Test
	void doesNotTreatArraysCopyOfAsAnExternalFlowBoundary() {
		ContainerFlowGraph graph= build("""
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

		assertEquals(ClosureStatus.LOCAL_CLOSED, graph.closureStatus());
		assertFalse(graph.nodes().stream()
				.anyMatch(node -> node.kind() == NodeKind.EXTERNAL_PARAMETER));
		assertFalse(hasDiagnostic(graph, DiagnosticKind.EXTERNAL_OR_BINARY_TARGET));
	}

	@Test
	void rejectsAProfileWithoutResolvedRootBinding() {
		CompilationUnit unit= parse("""
			class Sample {
				void collect() { }
			}
			""");
		ContainerUsageProfile template= seedDetector.findSeeds(parse("""
			import java.util.Arrays;
			class Other {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
				}
			}
			""")).get(0);
		ContainerUsageProfile unresolved= new ContainerUsageProfile(
				new ContainerIdentity(
						"", //$NON-NLS-1$
						template.identity().displayName(),
						template.identity().sourceStart(),
						template.identity().sourceLength()),
				template.currentShape(),
				template.elementDomain(),
				template.access(),
				template.orderRequirement(),
				template.uniquenessRequirement(),
				template.mutationLifecycle(),
				template.nullContract(),
				template.aliasingContract(),
				template.escapeLevel(),
				template.concurrency(),
				template.completeness(),
				template.evidence());

		ContainerFlowGraph graph= graphBuilder.build(unit, unresolved);

		assertEquals(ClosureStatus.REJECTED, graph.closureStatus());
		assertEquals(NodeKind.UNKNOWN_BOUNDARY,
				graph.node(graph.rootNodeId()).orElseThrow().kind());
		assertTrue(hasDiagnostic(graph, DiagnosticKind.UNRESOLVED_BINDING));
	}

	@Test
	void graphCollectionsAreImmutableAndValidateReferences() {
		ContainerFlowGraph graph= build("""
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					String[] alias = values;
					System.out.println(alias.length);
				}
			}
			""");

		assertFalse(graph.edges().isEmpty());
		assertThrows(UnsupportedOperationException.class,
				() -> graph.nodes().add(graph.nodes().get(0)));
		assertThrows(UnsupportedOperationException.class,
				() -> graph.edges().remove(0));
		assertTrue(graph.node(graph.rootNodeId()).isPresent());
	}

	private ContainerFlowGraph build(String source) {
		CompilationUnit unit= parse(source);
		ContainerUsageProfile seed= seedDetector.findSeeds(unit).get(0);
		return graphBuilder.build(unit, seed);
	}

	private static boolean hasDiagnostic(ContainerFlowGraph graph, DiagnosticKind kind) {
		return graph.diagnostics().stream().anyMatch(diagnostic -> diagnostic.kind() == kind);
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
