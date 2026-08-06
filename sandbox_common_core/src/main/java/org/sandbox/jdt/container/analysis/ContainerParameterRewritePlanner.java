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
import java.util.Optional;
import java.util.Set;

import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionStatus;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan.EditKind;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan.ParameterEdit;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan.PlanningDiagnostic;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan.PlanningResult;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PlanningStatus;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PositionKind;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.SignatureAtomicityGroup;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.SignatureMember;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AliasingContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ElementDomain;
import org.sandbox.jdt.container.api.ContainerUsageProfile.EscapeLevel;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;
import org.sandbox.jdt.container.api.UsageEvidence;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

/** Plans the first executable closed-source array-parameter to list rewrite. */
public final class ContainerParameterRewritePlanner {

	private static final String LIST_TYPE= "java.util.List"; //$NON-NLS-1$

	private static final Set<Kind> SUPPORTED_EVIDENCE= EnumSet.of(
			Kind.REFERENCE_COMPONENT,
			Kind.ARRAY_LENGTH_READ,
			Kind.ENCOUNTER_ITERATION,
			Kind.LOCAL_USAGE_COMPLETE,
			Kind.FLOW_CONTINUATION_ROOT);

	/**
	 * Plans one member of a single-member closed-source parameter group.
	 *
	 * <p>Override families remain rejected until every member can be emitted through
	 * one aggregate multi-file rewrite plan.</p>
	 */
	public PlanningResult plan(
			ContainerFlowComponent component,
			ContainerSignatureMigrationPlan signaturePlan,
			SignatureAtomicityGroup group,
			SignatureMember member,
			ContainerUsageProfile parameterProfile,
			ContainerMigrationReadiness readiness) {
		Objects.requireNonNull(component, "component"); //$NON-NLS-1$
		Objects.requireNonNull(signaturePlan, "signaturePlan"); //$NON-NLS-1$
		Objects.requireNonNull(group, "group"); //$NON-NLS-1$
		Objects.requireNonNull(member, "member"); //$NON-NLS-1$
		Objects.requireNonNull(parameterProfile, "parameterProfile"); //$NON-NLS-1$
		Objects.requireNonNull(readiness, "readiness"); //$NON-NLS-1$

		List<PlanningDiagnostic> diagnostics= new ArrayList<>();
		if (readiness.status() != ExecutionStatus.AUTOMATIC) {
			diagnostics.add(diagnostic(
					DiagnosticKind.NOT_AUTOMATIC,
					"The semantic execution gate has not approved automatic rewriting.")); //$NON-NLS-1$
		}
		validateSignaturePlan(component, signaturePlan, group, member, diagnostics);
		validateTarget(signaturePlan, readiness, diagnostics);
		FlowNode parameterNode= validateFlowNode(
				component, group, member, parameterProfile, diagnostics);
		validateProfile(parameterProfile, diagnostics);
		validateEvidence(parameterProfile, diagnostics);
		if (!diagnostics.isEmpty() || parameterNode == null) {
			return PlanningResult.rejected(diagnostics);
		}

		return PlanningResult.accepted(new ContainerParameterRewritePlan(
				parameterNode.compilationUnitHandle(),
				parameterNode.javaElementHandle(),
				parameterNode.bindingKey(),
				group.signatureIndex(),
				LIST_TYPE,
				signaturePlan.targetContract(),
				edits(parameterProfile)));
	}

	private static void validateSignaturePlan(
			ContainerFlowComponent component,
			ContainerSignatureMigrationPlan signaturePlan,
			SignatureAtomicityGroup group,
			SignatureMember member,
			List<PlanningDiagnostic> diagnostics) {
		if (component.closureStatus() != ClosureStatus.LOCAL_CLOSED
				|| signaturePlan.status() != PlanningStatus.CLOSED_SOURCE_AUTOMATIC) {
			diagnostics.add(diagnostic(
					DiagnosticKind.SIGNATURE_PLAN_NOT_CLOSED_SOURCE,
					"Parameter rewriting requires an explicitly automatic closed-source signature plan.")); //$NON-NLS-1$
		}
		Optional<SignatureAtomicityGroup> plannedGroup= signaturePlan.groups().stream()
				.filter(candidate -> candidate.groupId().equals(group.groupId()))
				.findFirst();
		if (plannedGroup.isEmpty() || !plannedGroup.get().equals(group)
				|| group.positionKind() != PositionKind.PARAMETER
				|| group.members().size() != 1) {
			diagnostics.add(diagnostic(
					DiagnosticKind.UNSUPPORTED_SIGNATURE_GROUP,
					"The first executable slice requires one exact, single-member parameter group.")); //$NON-NLS-1$
		}
		if (!group.members().contains(member)) {
			diagnostics.add(diagnostic(
					DiagnosticKind.SIGNATURE_MEMBER_MISMATCH,
					"The requested signature member is not part of the planned atomic group.")); //$NON-NLS-1$
		}
	}

	private static void validateTarget(
			ContainerSignatureMigrationPlan signaturePlan,
			ContainerMigrationReadiness readiness,
			List<PlanningDiagnostic> diagnostics) {
		if (!signaturePlan.targetContract().equals(readiness.targetContract())
				|| signaturePlan.targetContract().shape() != ContainerShape.LIST
				|| signaturePlan.targetContract().mutability() != Mutability.MUTABLE
				|| signaturePlan.targetContract().orderRequirement() == OrderRequirement.SORTED) {
			diagnostics.add(diagnostic(
					DiagnosticKind.UNSUPPORTED_TARGET,
					"The first parameter rewrite requires a mutable, unsorted list target.")); //$NON-NLS-1$
		}
	}

	private static FlowNode validateFlowNode(
			ContainerFlowComponent component,
			SignatureAtomicityGroup group,
			SignatureMember member,
			ContainerUsageProfile profile,
			List<PlanningDiagnostic> diagnostics) {
		Optional<FlowNode> resolved= component.node(member.flowNodeId());
		if (resolved.isEmpty()) {
			diagnostics.add(diagnostic(
					DiagnosticKind.FLOW_NODE_MISMATCH,
					"The signature member has no exact node in the closed flow component.")); //$NON-NLS-1$
			return null;
		}
		FlowNode node= resolved.get();
		if (node.kind() != NodeKind.PARAMETER
				|| !node.sourceResolved()
				|| node.signatureIndex() != group.signatureIndex()
				|| !node.javaElementHandle().equals(member.javaElementHandle())
				|| !node.compilationUnitHandle().equals(member.compilationUnitHandle())
				|| !node.bindingKey().equals(profile.identity().bindingKey())) {
			diagnostics.add(diagnostic(
					DiagnosticKind.FLOW_NODE_MISMATCH,
					"The parameter profile, signature member and closed flow node do not match.")); //$NON-NLS-1$
			return null;
		}
		return node;
	}

	private static void validateProfile(
			ContainerUsageProfile profile,
			List<PlanningDiagnostic> diagnostics) {
		if (profile.completeness() != AnalysisCompleteness.LOCAL_USAGE_COMPLETE
				&& profile.completeness() != AnalysisCompleteness.FLOW_COMPLETE) {
			diagnostics.add(diagnostic(
					DiagnosticKind.INCOMPLETE_PARAMETER_PROFILE,
					"Every use of the parameter binding must be classified before rewriting.")); //$NON-NLS-1$
		}
		if (profile.currentShape() != ContainerShape.ARRAY
				|| profile.elementDomain() != ElementDomain.REFERENCE
						&& profile.elementDomain() != ElementDomain.ENUM
				|| profile.escapeLevel() != EscapeLevel.METHOD_BOUNDARY
				|| profile.aliasingContract() != AliasingContract.NO_OBSERVED_ALIAS) {
			diagnostics.add(diagnostic(
					DiagnosticKind.UNSUPPORTED_PARAMETER_USAGE,
					"The first slice requires an alias-free reference array parameter.")); //$NON-NLS-1$
		}
		if (profile.access().indexedRead()
				|| profile.access().indexedWrite()
				|| profile.access().append()
				|| profile.access().positionalInsert()
				|| profile.access().positionalRemove()
				|| profile.access().membershipQuery()
				|| profile.access().keyLookup()) {
			diagnostics.add(diagnostic(
					DiagnosticKind.UNSUPPORTED_PARAMETER_USAGE,
					"Only length reads and enhanced-for iteration are supported for the first parameter rewrite.")); //$NON-NLS-1$
		}
	}

	private static void validateEvidence(
			ContainerUsageProfile profile,
			List<PlanningDiagnostic> diagnostics) {
		for (UsageEvidence evidence : profile.evidence()) {
			if (!SUPPORTED_EVIDENCE.contains(evidence.kind())) {
				diagnostics.add(diagnostic(
						DiagnosticKind.UNSUPPORTED_EVIDENCE,
						"The parameter profile contains unsupported evidence: "
								+ evidence.kind() + '.')); //$NON-NLS-1$
			}
		}
	}

	private static List<ParameterEdit> edits(ContainerUsageProfile profile) {
		List<ParameterEdit> edits= new ArrayList<>();
		edits.add(new ParameterEdit(
				EditKind.CHANGE_PARAMETER_DECLARATION,
				profile.identity().sourceStart(),
				profile.identity().sourceLength()));
		for (UsageEvidence evidence : profile.evidence()) {
			EditKind kind= switch (evidence.kind()) {
				case ARRAY_LENGTH_READ -> EditKind.REPLACE_LENGTH_WITH_SIZE;
				case ENCOUNTER_ITERATION -> EditKind.VERIFY_ENCOUNTER_ITERATION;
				default -> null;
			};
			if (kind != null) {
				edits.add(new ParameterEdit(
						kind, evidence.sourceStart(), evidence.sourceLength()));
			}
		}
		edits.sort(Comparator
				.comparingInt(ParameterEdit::sourceStart)
				.thenComparing(edit -> edit.kind().ordinal()));
		return List.copyOf(edits);
	}

	private static PlanningDiagnostic diagnostic(
			DiagnosticKind kind,
			String message) {
		return new PlanningDiagnostic(kind, message);
	}
}
