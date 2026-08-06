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
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;

import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan;
import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationRoot;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchKind;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionStatus;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.AutomationLevel;
import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractAssessment;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractProperty;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerRuleDescriptor;
import org.sandbox.jdt.container.api.ContainerRuleDescriptor.RuleOwnership;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.ResolvedSearchTarget;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.TargetKind;
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class ClosedSourceParameterPlanningIntegrationTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	@Test
	void continuationSeedReachesTheExecutableParameterPlanner() throws Exception {
		ICompilationUnit unit= createUnit("""
			package test;
			class Sample {
				void consume(String[] values) {
					System.out.println(values.length);
					for (String value : values) {
						System.out.println(value);
					}
				}
			}
			""");
		CompilationUnit root= parse(unit);
		MethodDeclaration method= method(root);
		SingleVariableDeclaration parameter=
				(SingleVariableDeclaration) method.parameters().get(0);
		IVariableBinding parameterBinding= parameter.resolveBinding();
		IMethodBinding methodBinding= method.resolveBinding();
		IJavaElement methodElement= methodBinding == null
				? null : methodBinding.getMethodDeclaration().getJavaElement();
		if (parameterBinding == null || methodBinding == null || methodElement == null) {
			throw new IllegalStateException("Missing resolved method or parameter"); //$NON-NLS-1$
		}

		String nodeId= "parameter:consume:0"; //$NON-NLS-1$
		String parameterKey= parameterBinding.getVariableDeclaration().getKey();
		String methodKey= methodBinding.getMethodDeclaration().getKey();
		String methodHandle= methodElement.getHandleIdentifier();
		ResolvedContainerFlowSearchPlan resolved= new ResolvedContainerFlowSearchPlan(
				List.of(new ResolvedSearchTarget(
						nodeId,
						SearchKind.METHOD_DECLARATION,
						TargetKind.METHOD,
						parameterKey,
						methodKey,
						methodHandle,
						0,
						"Continue from the exact parameter declaration"))); //$NON-NLS-1$

		ContinuationRoot continuation= new ContainerFlowContinuationDetector()
				.detect(root, unit.getHandleIdentifier(), resolved)
				.roots().get(0);
		ContainerUsageProfile profile= new LocalArrayUsageAnalyzer()
				.analyze(root, continuation.profile());

		assertTrue(profile.evidence().stream().anyMatch(evidence ->
				evidence.kind() == Kind.FLOW_CONTINUATION_ROOT));
		assertTrue(profile.evidence().stream().anyMatch(evidence ->
				evidence.kind() == Kind.LOCAL_USAGE_COMPLETE));

		FlowNode parameterNode= new FlowNode(
				nodeId,
				NodeKind.PARAMETER,
				parameterKey,
				methodKey,
				unit.getHandleIdentifier(),
				methodHandle,
				0,
				true,
				parameter.getStartPosition(),
				parameter.getLength());
		ContainerFlowComponent component= new ContainerFlowComponent(
				nodeId,
				List.of(parameterNode),
				List.of(),
				ClosureStatus.LOCAL_CLOSED,
				List.of());
		ContainerRecommendation recommendation= recommendation(profile);
		ContainerSignatureMigrationPlan signatures=
				new ContainerSignatureAtomicityPlanner().planClosedSource(
						component, resolved, recommendation);
		ContainerBridgePolicyPlan bridges=
				new ContainerBridgePolicyPlanner().plan(signatures, recommendation);
		ContainerMigrationReadiness readiness=
				new ContainerMigrationReadinessPlanner().plan(
						component, recommendation, signatures, bridges);

		var group= signatures.groups().get(0);
		var member= group.members().get(0);
		var result= new ContainerParameterRewritePlanner().plan(
				component, signatures, group, member, profile, readiness);

		assertEquals(ExecutionStatus.AUTOMATIC, readiness.status());
		assertTrue(result.ready());
		assertEquals(parameterKey, result.plan().orElseThrow().parameterBindingKey());
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

	private static MethodDeclaration method(CompilationUnit root) {
		MethodDeclaration[] result= { null };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration declaration) {
				if ("consume".equals(declaration.getName().getIdentifier())) { //$NON-NLS-1$
					result[0]= declaration;
				}
				return true;
			}
		});
		if (result[0] == null) {
			throw new IllegalStateException("Missing consume method"); //$NON-NLS-1$
		}
		return result[0];
	}

	private static ContainerRecommendation recommendation(ContainerUsageProfile profile) {
		TargetContainerContract target= new TargetContainerContract(
				ContainerShape.LIST,
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				Mutability.MUTABLE,
				NullContract.ALLOWED,
				"Use a dynamic sequence contract."); //$NON-NLS-1$
		ContainerRuleDescriptor rule= new ContainerRuleDescriptor(
				"semantic.array.append.sequence", //$NON-NLS-1$
				ContainerShape.ARRAY,
				ContainerShape.LIST,
				RuleOwnership.NOVEL,
				"", //$NON-NLS-1$
				"The migration changes representation and signatures."); //$NON-NLS-1$
		return new ContainerRecommendation(
				profile,
				target,
				rule,
				Confidence.HIGH,
				AutomationLevel.REPORT_ONLY,
				List.of(
						preserved(ContractProperty.ORDER),
						preserved(ContractProperty.UNIQUENESS),
						preserved(ContractProperty.MUTABILITY),
						preserved(ContractProperty.NULLS),
						preserved(ContractProperty.ALIASING),
						preserved(ContractProperty.CONCURRENCY),
						preserved(ContractProperty.SIGNATURES)));
	}

	private static ContractAssessment preserved(ContractProperty property) {
		return new ContractAssessment(
				property,
				Preservation.PRESERVED,
				property + " is preserved by the closed-source migration."); //$NON-NLS-1$
	}
}
