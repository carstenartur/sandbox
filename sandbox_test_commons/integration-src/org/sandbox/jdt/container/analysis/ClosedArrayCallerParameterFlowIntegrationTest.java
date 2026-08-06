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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan;
import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowComponent.LocatedFlowEdge;
import org.sandbox.jdt.container.api.ContainerFlowGraph;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchKind;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionStatus;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractProperty;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PlanningStatus;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.ResolvedSearchTarget;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.TargetKind;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class ClosedArrayCallerParameterFlowIntegrationTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	@Test
	void exactCallerAndParameterFlowReachesAutomaticParameterPlanning() throws Exception {
		ICompilationUnit unit= createUnit("""
			package test;

			import java.util.Arrays;

			class Sample {
				void caller(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					consume(values);
				}

				void consume(String[] values) {
					System.out.println(values.length);
					for (String value : values) {
						System.out.println(value);
					}
				}
			}
			""");
		CompilationUnit root= parse(unit);
		ContainerUsageProfile seed= new AppendOnlyArraySeedDetector()
				.findSeeds(root).get(0);
		LocalArrayUsageAnalyzer analyzer= new LocalArrayUsageAnalyzer();
		ContainerUsageProfile localProfile= analyzer.analyze(root, seed);

		assertEquals(AnalysisCompleteness.REJECTED, localProfile.completeness());
		assertTrue(localProfile.evidence().stream().anyMatch(evidence ->
				evidence.kind() == Kind.UNSAFE_ESCAPE));

		ContainerFlowGraph localGraph= new LocalContainerFlowGraphBuilder()
				.build(root, localProfile);
		FlowNode parameterNode= localGraph.nodes().stream()
				.filter(node -> node.kind() == NodeKind.PARAMETER)
				.findFirst()
				.orElseThrow();
		assertTrue(parameterNode.sourceResolved());
		assertTrue(localGraph.edges().stream().anyMatch(edge ->
				edge.kind() == EdgeKind.ARGUMENT_TO_PARAMETER));

		String unitHandle= unit.getHandleIdentifier();
		ContainerFlowComponent component= new ContainerFlowComponent(
				localGraph.rootNodeId(),
				localGraph.nodes(),
				localGraph.edges().stream()
						.map(edge -> new LocatedFlowEdge(
								unitHandle,
								edge.sourceNodeId(),
								edge.targetNodeId(),
								edge.kind(),
								edge.sourceStart(),
								edge.sourceLength()))
						.toList(),
				ClosureStatus.LOCAL_CLOSED,
				List.of());
		ResolvedContainerFlowSearchPlan resolved= new ResolvedContainerFlowSearchPlan(
				List.of(new ResolvedSearchTarget(
						parameterNode.stableId(),
						SearchKind.METHOD_DECLARATION,
						TargetKind.METHOD,
						parameterNode.bindingKey(),
						parameterNode.ownerKey(),
						parameterNode.javaElementHandle(),
						parameterNode.signatureIndex(),
						"Continue through the exact source parameter"))); //$NON-NLS-1$
		ContainerUsageProfile parameterProfile= analyzer.analyze(
				root,
				new ContainerFlowContinuationDetector()
						.detect(root, unitHandle, resolved)
						.roots().get(0).profile());
		assertEquals(AnalysisCompleteness.LOCAL_USAGE_COMPLETE,
				parameterProfile.completeness());

		ContainerUsageProfile refined= new ClosedFlowArrayUsageRefiner().refine(
				unitHandle,
				localProfile,
				component,
				List.of(parameterProfile));
		ContainerRecommendation recommendation= new ContainerContractInferrer()
				.infer(refined).orElseThrow();
		assertEquals(Preservation.PRESERVED,
				assessment(recommendation, ContractProperty.ALIASING));
		assertEquals(Preservation.PRESERVED,
				assessment(recommendation, ContractProperty.CONCURRENCY));
		assertEquals(Preservation.PRESERVED,
				assessment(recommendation, ContractProperty.SIGNATURES));

		ContainerSignatureMigrationPlan signatures=
				new ContainerSignatureAtomicityPlanner().planClosedSource(
						component, resolved, recommendation);
		ContainerBridgePolicyPlan bridges= new ContainerBridgePolicyPlanner()
				.plan(signatures, recommendation);
		ContainerMigrationReadiness readiness= new ContainerMigrationReadinessPlanner()
				.plan(component, recommendation, signatures, bridges);

		assertEquals(PlanningStatus.CLOSED_SOURCE_AUTOMATIC, signatures.status());
		assertEquals(ContainerBridgePolicyPlan.PlanningStatus.NO_BRIDGE_NEEDED,
				bridges.status());
		assertEquals(ExecutionStatus.AUTOMATIC, readiness.status());
		var group= signatures.groups().get(0);
		assertTrue(new ContainerParameterRewritePlanner().plan(
				component,
				signatures,
				group,
				group.members().get(0),
				parameterProfile,
				readiness).ready());
	}

	private ICompilationUnit createUnit(String source) throws CoreException {
		IPackageFragment fragment= context.getSourceFolder()
				.createPackageFragment("test", false, null); //$NON-NLS-1$
		return fragment.createCompilationUnit("Sample.java", source, true, null); //$NON-NLS-1$
	}

	private CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setProject(context.getJavaProject());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		return (CompilationUnit) parser.createAST(null);
	}

	private static Preservation assessment(
			ContainerRecommendation recommendation,
			ContractProperty property) {
		return recommendation.assessments().stream()
				.filter(assessment -> assessment.property() == property)
				.findFirst()
				.orElseThrow()
				.preservation();
	}
}
