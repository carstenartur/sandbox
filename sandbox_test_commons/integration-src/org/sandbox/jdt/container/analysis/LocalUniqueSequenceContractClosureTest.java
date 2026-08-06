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

import org.sandbox.jdt.cleanup.multifile.UniqueSequenceLocalRewriteFix;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan;
import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionStatus;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class LocalUniqueSequenceContractClosureTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	private final ContainerSignatureAtomicityPlanner signaturePlanner=
			new ContainerSignatureAtomicityPlanner();
	private final ContainerBridgePolicyPlanner bridgePlanner=
			new ContainerBridgePolicyPlanner();
	private final ContainerMigrationReadinessPlanner readinessPlanner=
			new ContainerMigrationReadinessPlanner();

	@Test
	void strictLocalUniqueSequenceReachesAutomaticRewrite() throws Exception {
		ICompilationUnit unit= createUnit("""
			package test;
			import java.util.ArrayList;
			import java.util.List;
			class Sample {
				void collect(String value) {
					List<String> values = new ArrayList<>();
					if (!values.contains(value)) {
						values.add(value);
					}
					for (String current : values) {
						System.out.println(current);
					}
				}
			}
			""");
		CompilationUnit root= parse(unit);
		ContainerUsageProfile profile=
				new LocalUniqueSequenceAnalyzer().analyze(root).get(0);
		var recommendation= new UniqueSequenceContractInferrer()
				.infer(profile).orElseThrow();
		ContainerFlowComponent component= localComponent(unit, profile);
		ContainerSignatureMigrationPlan signaturePlan= signaturePlanner.plan(
				component, ResolvedContainerFlowSearchPlan.empty(), recommendation);
		ContainerBridgePolicyPlan bridgePlan=
				bridgePlanner.plan(signaturePlan, recommendation);
		ContainerMigrationReadiness readiness= readinessPlanner.plan(
				component, recommendation, signaturePlan, bridgePlan);
		var rewrite= new UniqueSequenceLocalRewritePlanner().plan(
				unit.getHandleIdentifier(), recommendation, readiness);

		assertEquals(ContainerSignatureMigrationPlan.PlanningStatus.NO_SIGNATURE_CHANGE,
				signaturePlan.status());
		assertEquals(ContainerBridgePolicyPlan.PlanningStatus.NO_BRIDGE_NEEDED,
				bridgePlan.status());
		assertEquals(ExecutionStatus.AUTOMATIC, readiness.status());
		assertTrue(rewrite.ready());

		UniqueSequenceLocalRewriteFix.create(unit, root, rewrite.plan().orElseThrow())
				.createChange(null).perform(null);

		String transformed= unit.getSource();
		assertTrue(transformed.contains("Set<String> values")); //$NON-NLS-1$
		assertTrue(transformed.contains("new LinkedHashSet<>()")); //$NON-NLS-1$
		assertTrue(transformed.contains("values.add(value);")); //$NON-NLS-1$
		assertFalse(transformed.contains("values.contains(value)")); //$NON-NLS-1$
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
				root.stableId(), List.of(root), List.of(),
				ClosureStatus.LOCAL_CLOSED, List.of());
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
