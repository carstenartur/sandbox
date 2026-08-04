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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowComponent.LocatedFlowEdge;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.EditKind;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.PlanningResult;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.BlockerProperty;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.BlockerSeverity;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionBlocker;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionStatus;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.AutomationLevel;
import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerRuleDescriptor;
import org.sandbox.jdt.container.api.ContainerRuleDescriptor.RuleOwnership;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AccessProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AliasingContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ContainerIdentity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ElementDomain;
import org.sandbox.jdt.container.api.ContainerUsageProfile.EscapeLevel;
import org.sandbox.jdt.container.api.ContainerUsageProfile.MutationLifecycle;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;
import org.sandbox.jdt.container.api.UsageEvidence;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

class ContainerLocalRewritePlannerTest {

	private final ContainerLocalRewritePlanner planner= new ContainerLocalRewritePlanner();

	@Test
	void plansStrictlyLocalAppendArrayRewrite() {
		PlanningResult result= planner.plan(
				component("binding", false), //$NON-NLS-1$
				recommendation(profile(
						ElementDomain.REFERENCE,
						OrderRequirement.ENCOUNTER,
						baseEvidence(true, true))),
				automaticReadiness());

		assertTrue(result.ready());
		ContainerLocalRewritePlan plan= result.plan().orElseThrow();
		assertEquals("Owner.java", plan.compilationUnitHandle()); //$NON-NLS-1$
		assertEquals("binding", plan.bindingKey()); //$NON-NLS-1$
		assertEquals("java.util.List", plan.targetInterfaceType()); //$NON-NLS-1$
		assertEquals("java.util.ArrayList", plan.targetImplementationType()); //$NON-NLS-1$
		Set<EditKind> kinds= plan.edits().stream()
				.map(ContainerLocalRewritePlan.LocalEdit::kind)
				.collect(Collectors.toSet());
		assertEquals(Set.of(
				EditKind.CHANGE_LOCAL_DECLARATION,
				EditKind.REPLACE_EMPTY_ARRAY_INITIALIZER,
				EditKind.REMOVE_ARRAY_GROWTH,
				EditKind.REPLACE_TAIL_WRITE_WITH_ADD,
				EditKind.REPLACE_LENGTH_WITH_SIZE), kinds);
		assertEquals(List.of(10, 10, 20, 30, 40),
				plan.edits().stream()
						.map(ContainerLocalRewritePlan.LocalEdit::sourceStart)
						.toList());
	}

	@Test
	void refusesRewriteWhenExecutionGateIsNotAutomatic() {
		ContainerMigrationReadiness readiness= new ContainerMigrationReadiness(
				targetContract(OrderRequirement.ENCOUNTER),
				ExecutionStatus.REPORT_ONLY,
				List.of(new ExecutionBlocker(
						BlockerProperty.CONCURRENCY,
						BlockerSeverity.PROOF_REQUIRED,
						"recommendation", //$NON-NLS-1$
						"Concurrency still needs proof."))); //$NON-NLS-1$

		PlanningResult result= planner.plan(
				component("binding", false), //$NON-NLS-1$
				recommendation(profile(
						ElementDomain.REFERENCE,
						OrderRequirement.ENCOUNTER,
						baseEvidence(false, true))),
				readiness);

		assertFalse(result.ready());
		assertTrue(hasDiagnostic(result, DiagnosticKind.NOT_AUTOMATIC));
	}

	@Test
	void rejectsFlowEdgesAndBindingMismatch() {
		PlanningResult withEdge= planner.plan(
				component("binding", true), //$NON-NLS-1$
				recommendation(profile(
						ElementDomain.REFERENCE,
						OrderRequirement.ENCOUNTER,
						baseEvidence(false, true))),
				automaticReadiness());
		PlanningResult wrongBinding= planner.plan(
				component("other-binding", false), //$NON-NLS-1$
				recommendation(profile(
						ElementDomain.REFERENCE,
						OrderRequirement.ENCOUNTER,
						baseEvidence(false, true))),
				automaticReadiness());

		assertTrue(hasDiagnostic(withEdge, DiagnosticKind.FLOW_NOT_STRICTLY_LOCAL));
		assertTrue(hasDiagnostic(wrongBinding, DiagnosticKind.SOURCE_BINDING_MISMATCH));
	}

	@Test
	void rejectsPrimitiveAndPositionalArrays() {
		PlanningResult primitive= planner.plan(
				component("binding", false), //$NON-NLS-1$
				recommendation(profile(
						ElementDomain.PRIMITIVE,
						OrderRequirement.ENCOUNTER,
						baseEvidence(false, true))),
				automaticReadiness());
		PlanningResult positional= planner.plan(
				component("binding", false), //$NON-NLS-1$
				recommendation(profile(
						ElementDomain.REFERENCE,
						OrderRequirement.POSITIONAL,
						baseEvidence(false, true))),
				automaticReadiness(OrderRequirement.POSITIONAL));

		assertTrue(hasDiagnostic(primitive, DiagnosticKind.UNSUPPORTED_ELEMENT_DOMAIN));
		assertTrue(hasDiagnostic(positional, DiagnosticKind.POSITIONAL_SEMANTICS));
		assertTrue(hasDiagnostic(positional, DiagnosticKind.UNSUPPORTED_TARGET));
	}

	@Test
	void rejectsMissingUnbalancedAndUnknownEvidence() {
		PlanningResult missing= planner.plan(
				component("binding", false), //$NON-NLS-1$
				recommendation(profile(
						ElementDomain.REFERENCE,
						OrderRequirement.ENCOUNTER,
						List.of(new UsageEvidence(
								Kind.LOCAL_USAGE_COMPLETE,
								"Local uses are complete.", 5, 1)))), //$NON-NLS-1$
				automaticReadiness());
		List<UsageEvidence> unbalancedEvidence= new ArrayList<>(baseEvidence(false, true));
		unbalancedEvidence.add(new UsageEvidence(
				Kind.ARRAY_GROWTH, "Second growth", 50, 2)); //$NON-NLS-1$
		PlanningResult unbalanced= planner.plan(
				component("binding", false), //$NON-NLS-1$
				recommendation(profile(
						ElementDomain.REFERENCE,
						OrderRequirement.ENCOUNTER,
						unbalancedEvidence)),
				automaticReadiness());
		List<UsageEvidence> unsupportedEvidence= new ArrayList<>(baseEvidence(false, true));
		unsupportedEvidence.add(new UsageEvidence(
				Kind.UNSAFE_ESCAPE, "Unexpected escape", 60, 2)); //$NON-NLS-1$
		PlanningResult unsupported= planner.plan(
				component("binding", false), //$NON-NLS-1$
				recommendation(profile(
						ElementDomain.REFERENCE,
						OrderRequirement.ENCOUNTER,
						unsupportedEvidence)),
				automaticReadiness());

		assertTrue(hasDiagnostic(missing, DiagnosticKind.MISSING_APPEND_PATTERN));
		assertTrue(hasDiagnostic(unbalanced, DiagnosticKind.UNBALANCED_APPEND_PATTERN));
		assertTrue(hasDiagnostic(unsupported, DiagnosticKind.UNSUPPORTED_EVIDENCE));
	}

	@Test
	void localRewriteModelsAreImmutableAndRequireCoreEdits() {
		ContainerLocalRewritePlan plan= planner.plan(
				component("binding", false), //$NON-NLS-1$
				recommendation(profile(
						ElementDomain.ENUM,
						OrderRequirement.ENCOUNTER,
						baseEvidence(false, true))),
				automaticReadiness()).plan().orElseThrow();

		assertThrows(UnsupportedOperationException.class, () -> plan.edits().clear());
		assertThrows(IllegalArgumentException.class, () ->
				new ContainerLocalRewritePlan(
						"Owner.java", //$NON-NLS-1$
						"binding", //$NON-NLS-1$
						"java.util.List", //$NON-NLS-1$
						"java.util.ArrayList", //$NON-NLS-1$
						targetContract(OrderRequirement.ENCOUNTER),
						List.of(new ContainerLocalRewritePlan.LocalEdit(
								EditKind.CHANGE_LOCAL_DECLARATION, 1, 1))));
	}

	private static boolean hasDiagnostic(PlanningResult result, DiagnosticKind kind) {
		return result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.kind() == kind);
	}

	private static List<UsageEvidence> baseEvidence(
			boolean includeLength,
			boolean includeComplete) {
		List<UsageEvidence> evidence= new ArrayList<>();
		evidence.add(new UsageEvidence(
				Kind.ARRAY_GROWTH, "Array grows by one.", 20, 4)); //$NON-NLS-1$
		evidence.add(new UsageEvidence(
				Kind.APPEND_WRITE, "Tail slot receives the value.", 30, 4)); //$NON-NLS-1$
		evidence.add(new UsageEvidence(
				Kind.REFERENCE_COMPONENT, "Reference component.", 10, 1)); //$NON-NLS-1$
		if (includeLength) {
			evidence.add(new UsageEvidence(
					Kind.ARRAY_LENGTH_READ, "Length is read.", 40, 3)); //$NON-NLS-1$
		}
		evidence.add(new UsageEvidence(
				Kind.ENCOUNTER_ITERATION, "Encounter order is observed.", 45, 3)); //$NON-NLS-1$
		if (includeComplete) {
			evidence.add(new UsageEvidence(
					Kind.LOCAL_USAGE_COMPLETE, "Local uses are complete.", 10, 1)); //$NON-NLS-1$
		}
		return List.copyOf(evidence);
	}

	private static ContainerUsageProfile profile(
			ElementDomain domain,
			OrderRequirement order,
			List<UsageEvidence> evidence) {
		return new ContainerUsageProfile(
				new ContainerIdentity("binding", "values", 10, 1), //$NON-NLS-1$ //$NON-NLS-2$
				ContainerShape.ARRAY,
				domain,
				new AccessProfile(false, true, true, false, false, false, false),
				order,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				MutationLifecycle.CONTINUOUSLY_MUTABLE,
				NullContract.ALLOWED,
				AliasingContract.NO_OBSERVED_ALIAS,
				EscapeLevel.LOCAL,
				ConcurrencyProfile.unknown(),
				AnalysisCompleteness.FLOW_COMPLETE,
				evidence);
	}

	private static ContainerFlowComponent component(String bindingKey, boolean withEdge) {
		FlowNode root= new FlowNode(
				"local:" + bindingKey, //$NON-NLS-1$
				NodeKind.LOCAL_VARIABLE,
				bindingKey,
				"method-key", //$NON-NLS-1$
				"Owner.java", //$NON-NLS-1$
				"local-handle", //$NON-NLS-1$
				-1,
				true,
				10,
				1);
		List<LocatedFlowEdge> edges= withEdge
				? List.of(new LocatedFlowEdge(
						"Owner.java", root.stableId(), root.stableId(), //$NON-NLS-1$
						EdgeKind.ASSIGNMENT, 15, 2))
				: List.of();
		return new ContainerFlowComponent(
				root.stableId(), List.of(root), edges,
				ClosureStatus.LOCAL_CLOSED, List.of());
	}

	private static ContainerRecommendation recommendation(ContainerUsageProfile profile) {
		ContainerRuleDescriptor rule= new ContainerRuleDescriptor(
				"semantic.array.append.sequence", //$NON-NLS-1$
				ContainerShape.ARRAY,
				ContainerShape.LIST,
				RuleOwnership.NOVEL,
				"", //$NON-NLS-1$
				"The migration changes representation."); //$NON-NLS-1$
		return new ContainerRecommendation(
				profile,
				targetContract(profile.orderRequirement()),
				rule,
				Confidence.HIGH,
				AutomationLevel.AUTOMATIC,
				List.of());
	}

	private static ContainerMigrationReadiness automaticReadiness() {
		return automaticReadiness(OrderRequirement.ENCOUNTER);
	}

	private static ContainerMigrationReadiness automaticReadiness(OrderRequirement order) {
		return new ContainerMigrationReadiness(
				targetContract(order), ExecutionStatus.AUTOMATIC, List.of());
	}

	private static TargetContainerContract targetContract(OrderRequirement order) {
		return new TargetContainerContract(
				ContainerShape.LIST,
				order,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				Mutability.MUTABLE,
				NullContract.ALLOWED,
				"Use a mutable dynamic sequence."); //$NON-NLS-1$
	}
}
