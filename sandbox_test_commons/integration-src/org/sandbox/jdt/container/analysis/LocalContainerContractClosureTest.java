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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.sandbox.jdt.cleanup.multifile.ContainerLocalRewriteFix;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan;
import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.EditKind;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.PlanningResult;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionStatus;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractProperty;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AliasingContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class LocalContainerContractClosureTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	private final AppendOnlyArraySeedDetector seedDetector= new AppendOnlyArraySeedDetector();
	private final LocalArrayUsageAnalyzer usageAnalyzer= new LocalArrayUsageAnalyzer();
	private final ContainerContractInferrer inferrer= new ContainerContractInferrer();
	private final ContainerSignatureAtomicityPlanner signaturePlanner=
			new ContainerSignatureAtomicityPlanner();
	private final ContainerBridgePolicyPlanner bridgePlanner= new ContainerBridgePolicyPlanner();
	private final ContainerMigrationReadinessPlanner readinessPlanner=
			new ContainerMigrationReadinessPlanner();
	private final ContainerLocalRewritePlanner rewritePlanner= new ContainerLocalRewritePlanner();

	@Test
	void strictLocalArrayReachesAutomaticRewritePlanning() throws Exception {
		ICompilationUnit unit= createUnit("""
			package test;
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					int count = values.length;
					for (String current : values) {
						System.out.println(current + count);
					}
				}
			}
			""");
		CompilationUnit root= parse(unit);
		ContainerUsageProfile profile= analyze(root);

		assertEquals(AnalysisCompleteness.LOCAL_USAGE_COMPLETE, profile.completeness());
		assertEquals(AliasingContract.NO_OBSERVED_ALIAS, profile.aliasingContract());
		assertEquals(ThreadExposure.THREAD_CONFINED, profile.concurrency().exposure());

		ContainerRecommendation recommendation= inferrer.infer(profile).orElseThrow();
		assertEquals(Confidence.HIGH, recommendation.confidence());
		for (ContractProperty property : List.of(
				ContractProperty.ORDER,
				ContractProperty.UNIQUENESS,
				ContractProperty.MUTABILITY,
				ContractProperty.NULLS,
				ContractProperty.ALIASING,
				ContractProperty.CONCURRENCY,
				ContractProperty.SIGNATURES)) {
			assertEquals(Preservation.PRESERVED,
					recommendation.assessments().stream()
							.filter(assessment -> assessment.property() == property)
							.findFirst()
							.orElseThrow()
							.preservation());
		}

		ContainerFlowComponent component= localComponent(unit, profile);
		ContainerSignatureMigrationPlan signaturePlan= signaturePlanner.plan(
				component, ResolvedContainerFlowSearchPlan.empty(), recommendation);
		ContainerBridgePolicyPlan bridgePlan= bridgePlanner.plan(signaturePlan, recommendation);
		ContainerMigrationReadiness readiness= readinessPlanner.plan(
				component, recommendation, signaturePlan, bridgePlan);
		PlanningResult rewrite= rewritePlanner.plan(component, recommendation, readiness);

		assertEquals(ContainerSignatureMigrationPlan.PlanningStatus.NO_SIGNATURE_CHANGE,
				signaturePlan.status());
		assertEquals(ContainerBridgePolicyPlan.PlanningStatus.NO_BRIDGE_NEEDED,
				bridgePlan.status());
		assertEquals(ExecutionStatus.AUTOMATIC, readiness.status());
		assertTrue(rewrite.ready());
		var plan= rewrite.plan().orElseThrow();
		assertEquals(unit.getHandleIdentifier(), plan.compilationUnitHandle());
		assertEquals(1, plan.edits().stream()
				.filter(edit -> edit.kind() == EditKind.REPLACE_LENGTH_WITH_SIZE)
				.count());

		ContainerLocalRewriteFix.create(unit, root, plan)
				.createChange(null).perform(null);

		String transformed= unit.getSource();
		assertTrue(transformed.contains("List<String> values")); //$NON-NLS-1$
		assertTrue(transformed.contains("new ArrayList<>()")); //$NON-NLS-1$
		assertTrue(transformed.contains("values.add(value)")); //$NON-NLS-1$
		assertTrue(transformed.contains("values.size()")); //$NON-NLS-1$
		assertFalse(transformed.contains("Arrays.copyOf(values")); //$NON-NLS-1$
		assertFalse(transformed.contains("values[values.length - 1]")); //$NON-NLS-1$
	}

	@Test
	void lambdaCapturePreventsThreadConfinementAndRecommendation() throws CoreException {
		ContainerUsageProfile profile= analyze(parse(createUnit("""
			package test;
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					Runnable task = () -> System.out.println(values.length);
					task.run();
				}
			}
			""")));

		assertEquals(AnalysisCompleteness.REJECTED, profile.completeness());
		assertTrue(profile.evidence().stream()
				.anyMatch(evidence -> evidence.kind() == Kind.CAPTURED_USAGE));
		assertFalse(inferrer.infer(profile).isPresent());
	}

	@Test
	void localClassCaptureIsRejectedEvenWithoutMethodArgumentEscape() throws CoreException {
		ContainerUsageProfile profile= analyze(parse(createUnit("""
			package test;
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					class Reader {
						int size() {
							return values.length;
						}
					}
					new Reader().size();
				}
			}
			""")));

		assertEquals(AnalysisCompleteness.REJECTED, profile.completeness());
		assertTrue(profile.evidence().stream()
				.anyMatch(evidence -> evidence.kind() == Kind.CAPTURED_USAGE));
	}

	private ContainerUsageProfile analyze(CompilationUnit root) {
		ContainerUsageProfile seed= seedDetector.findSeeds(root).get(0);
		return usageAnalyzer.analyze(root, seed);
	}

	private static ContainerFlowComponent localComponent(
			ICompilationUnit unit,
			ContainerUsageProfile profile) {
		FlowNode root= new FlowNode(
				"local:" + profile.identity().bindingKey(), //$NON-NLS-1$
				NodeKind.LOCAL_VARIABLE,
				profile.identity().bindingKey(),
				"method-key", //$NON-NLS-1$
				unit.getHandleIdentifier(),
				"local-handle", //$NON-NLS-1$
				-1,
				true,
				profile.identity().sourceStart(),
				profile.identity().sourceLength());
		return new ContainerFlowComponent(
				root.stableId(), List.of(root), List.of(), ClosureStatus.LOCAL_CLOSED, List.of());
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
}
