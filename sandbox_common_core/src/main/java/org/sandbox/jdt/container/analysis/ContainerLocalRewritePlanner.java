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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.EditKind;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.LocalEdit;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.PlanningDiagnostic;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.PlanningResult;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionStatus;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ElementDomain;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;
import org.sandbox.jdt.container.api.UsageEvidence;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

/** Plans the first executable, strictly local append-array to list migration. */
public final class ContainerLocalRewritePlanner {

	private static final String LIST_TYPE= "java.util.List"; //$NON-NLS-1$
	private static final String ARRAY_LIST_TYPE= "java.util.ArrayList"; //$NON-NLS-1$

	private static final Set<Kind> SUPPORTED_EVIDENCE= EnumSet.of(
			Kind.ARRAY_GROWTH,
			Kind.APPEND_WRITE,
			Kind.REFERENCE_COMPONENT,
			Kind.ARRAY_LENGTH_READ,
			Kind.ENCOUNTER_ITERATION,
			Kind.LOCAL_USAGE_COMPLETE);

	/** Builds a local rewrite plan or returns complete rejection diagnostics. */
	public PlanningResult plan(
			ContainerFlowComponent component,
			ContainerRecommendation recommendation,
			ContainerMigrationReadiness readiness) {
		Objects.requireNonNull(component, "component"); //$NON-NLS-1$
		Objects.requireNonNull(recommendation, "recommendation"); //$NON-NLS-1$
		Objects.requireNonNull(readiness, "readiness"); //$NON-NLS-1$

		List<PlanningDiagnostic> diagnostics= new ArrayList<>();
		if (readiness.status() != ExecutionStatus.AUTOMATIC) {
			diagnostics.add(diagnostic(
					DiagnosticKind.NOT_AUTOMATIC,
					"The semantic execution gate has not approved automatic rewriting.")); //$NON-NLS-1$
		}
		validateTarget(recommendation, readiness, diagnostics);
		FlowNode variable= strictlyLocalVariable(component, diagnostics);
		ContainerUsageProfile profile= recommendation.sourceProfile();
		if (variable != null && !variable.bindingKey().equals(profile.identity().bindingKey())) {
			diagnostics.add(diagnostic(
					DiagnosticKind.SOURCE_BINDING_MISMATCH,
					"The recommendation binding does not match the closed local flow node.")); //$NON-NLS-1$
		}
		if (profile.elementDomain() != ElementDomain.REFERENCE
				&& profile.elementDomain() != ElementDomain.ENUM) {
			diagnostics.add(diagnostic(
					DiagnosticKind.UNSUPPORTED_ELEMENT_DOMAIN,
					"The first local rewrite supports only reference or enum array components.")); //$NON-NLS-1$
		}
		if (profile.access().hasPositionalSemantics()
				|| profile.orderRequirement() == OrderRequirement.POSITIONAL) {
			diagnostics.add(diagnostic(
					DiagnosticKind.POSITIONAL_SEMANTICS,
					"The first local rewrite does not yet translate indexed reads or positional writes.")); //$NON-NLS-1$
		}
		validateEvidence(profile, diagnostics);
		if (!diagnostics.isEmpty()) {
			return PlanningResult.rejected(diagnostics);
		}

		return PlanningResult.ready(new ContainerLocalRewritePlan(
				variable.compilationUnitHandle(),
				variable.bindingKey(),
				LIST_TYPE,
				ARRAY_LIST_TYPE,
				recommendation.targetContract(),
				edits(profile)));
	}

	private static void validateTarget(
			ContainerRecommendation recommendation,
			ContainerMigrationReadiness readiness,
			List<PlanningDiagnostic> diagnostics) {
		if (!recommendation.targetContract().equals(readiness.targetContract())
				|| recommendation.targetContract().shape() != ContainerShape.LIST
				|| recommendation.targetContract().mutability() != Mutability.MUTABLE
				|| (recommendation.targetContract().orderRequirement()
						!= OrderRequirement.ENCOUNTER
						&& recommendation.targetContract().orderRequirement()
								!= OrderRequirement.NONE)) {
			diagnostics.add(diagnostic(
					DiagnosticKind.UNSUPPORTED_TARGET,
					"The first local rewrite supports a mutable list with encounter or unspecified order.")); //$NON-NLS-1$
		}
	}

	private static FlowNode strictlyLocalVariable(
			ContainerFlowComponent component,
			List<PlanningDiagnostic> diagnostics) {
		if (component.closureStatus() != ClosureStatus.LOCAL_CLOSED
				|| component.nodes().size() != 1
				|| !component.edges().isEmpty()
				|| component.nodes().get(0).kind() != NodeKind.LOCAL_VARIABLE) {
			diagnostics.add(diagnostic(
					DiagnosticKind.FLOW_NOT_STRICTLY_LOCAL,
					"The first rewrite requires exactly one closed local variable and no flow edges.")); //$NON-NLS-1$
			return null;
		}
		FlowNode node= component.nodes().get(0);
		if (node.compilationUnitHandle().isBlank() || node.bindingKey().isBlank()) {
			diagnostics.add(diagnostic(
					DiagnosticKind.FLOW_NOT_STRICTLY_LOCAL,
					"The local flow node lacks a compilation-unit handle or binding key.")); //$NON-NLS-1$
			return null;
		}
		return node;
	}

	private static void validateEvidence(
			ContainerUsageProfile profile,
			List<PlanningDiagnostic> diagnostics) {
		long growthCount= count(profile, Kind.ARRAY_GROWTH);
		long appendCount= count(profile, Kind.APPEND_WRITE);
		if (growthCount == 0 || appendCount == 0) {
			diagnostics.add(diagnostic(
					DiagnosticKind.MISSING_APPEND_PATTERN,
					"The profile does not contain a complete array growth and tail-write pattern.")); //$NON-NLS-1$
		} else if (growthCount != appendCount) {
			diagnostics.add(diagnostic(
					DiagnosticKind.UNBALANCED_APPEND_PATTERN,
					"Array growth and tail-write evidence counts differ.")); //$NON-NLS-1$
		}
		for (UsageEvidence evidence : profile.evidence()) {
			if (!SUPPORTED_EVIDENCE.contains(evidence.kind())) {
				diagnostics.add(diagnostic(
						DiagnosticKind.UNSUPPORTED_EVIDENCE,
						"The local profile contains unsupported evidence: "
								+ evidence.kind() + '.')); //$NON-NLS-1$
			}
		}
	}

	private static long count(ContainerUsageProfile profile, Kind kind) {
		return profile.evidence().stream()
				.filter(evidence -> evidence.kind() == kind)
				.count();
	}

	private static List<LocalEdit> edits(ContainerUsageProfile profile) {
		List<LocalEdit> result= new ArrayList<>();
		result.add(new LocalEdit(
				EditKind.CHANGE_LOCAL_DECLARATION,
				profile.identity().sourceStart(),
				profile.identity().sourceLength()));
		result.add(new LocalEdit(
				EditKind.REPLACE_EMPTY_ARRAY_INITIALIZER,
				profile.identity().sourceStart(),
				profile.identity().sourceLength()));
		boolean verifyEncounterIterations=
				profile.concurrency().exposure() == ThreadExposure.THREAD_CONFINED;
		for (UsageEvidence evidence : profile.evidence()) {
			EditKind kind= switch (evidence.kind()) {
				case ARRAY_GROWTH -> EditKind.REMOVE_ARRAY_GROWTH;
				case APPEND_WRITE -> EditKind.REPLACE_TAIL_WRITE_WITH_ADD;
				case ARRAY_LENGTH_READ -> EditKind.REPLACE_LENGTH_WITH_SIZE;
				case ENCOUNTER_ITERATION -> verifyEncounterIterations
						? EditKind.VERIFY_ENCOUNTER_ITERATION
						: null;
				default -> null;
			};
			if (kind != null) {
				result.add(new LocalEdit(kind, evidence.sourceStart(), evidence.sourceLength()));
			}
		}
		result.sort(Comparator
				.comparingInt(LocalEdit::sourceStart)
				.thenComparing(edit -> edit.kind().ordinal()));
		return List.copyOf(result);
	}

	private static PlanningDiagnostic diagnostic(
			DiagnosticKind kind,
			String message) {
		return new PlanningDiagnostic(kind, message);
	}
}
