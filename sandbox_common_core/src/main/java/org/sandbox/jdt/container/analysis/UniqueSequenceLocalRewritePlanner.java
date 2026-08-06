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

import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionStatus;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AliasingContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ElementDomain;
import org.sandbox.jdt.container.api.ContainerUsageProfile.EscapeLevel;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;
import org.sandbox.jdt.container.api.UniqueSequenceLocalRewritePlan;
import org.sandbox.jdt.container.api.UniqueSequenceLocalRewritePlan.DiagnosticKind;
import org.sandbox.jdt.container.api.UniqueSequenceLocalRewritePlan.EditKind;
import org.sandbox.jdt.container.api.UniqueSequenceLocalRewritePlan.LocalEdit;
import org.sandbox.jdt.container.api.UniqueSequenceLocalRewritePlan.PlanningDiagnostic;
import org.sandbox.jdt.container.api.UniqueSequenceLocalRewritePlan.PlanningResult;
import org.sandbox.jdt.container.api.UsageEvidence;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

/** Plans a strictly local, manually unique sequence to ordered-set rewrite. */
public final class UniqueSequenceLocalRewritePlanner {

	private static final String SET_TYPE= "java.util.Set"; //$NON-NLS-1$
	private static final String LINKED_HASH_SET_TYPE= "java.util.LinkedHashSet"; //$NON-NLS-1$

	private static final Set<Kind> SUPPORTED_EVIDENCE= EnumSet.of(
			Kind.DUPLICATE_SUPPRESSION,
			Kind.REFERENCE_COMPONENT,
			Kind.HASH_STABLE_COMPONENT,
			Kind.ENCOUNTER_ITERATION,
			Kind.LOCAL_USAGE_COMPLETE);

	/** Builds one local rewrite plan or complete rejection diagnostics. */
	public PlanningResult plan(
			String compilationUnitHandle,
			ContainerRecommendation recommendation,
			ContainerMigrationReadiness readiness) {
		Objects.requireNonNull(recommendation, "recommendation"); //$NON-NLS-1$
		Objects.requireNonNull(readiness, "readiness"); //$NON-NLS-1$

		List<PlanningDiagnostic> diagnostics= new ArrayList<>();
		if (readiness.status() != ExecutionStatus.AUTOMATIC) {
			diagnostics.add(diagnostic(
					DiagnosticKind.NOT_AUTOMATIC,
					"The semantic execution gate has not approved automatic rewriting.")); //$NON-NLS-1$
		}
		validateTarget(recommendation, readiness, diagnostics);
		ContainerUsageProfile profile= recommendation.sourceProfile();
		validateSource(profile, diagnostics);
		validateEvidence(profile, diagnostics);
		if (!diagnostics.isEmpty()) {
			return PlanningResult.rejected(diagnostics);
		}

		return PlanningResult.accepted(new UniqueSequenceLocalRewritePlan(
				compilationUnitHandle,
				profile.identity().bindingKey(),
				SET_TYPE,
				LINKED_HASH_SET_TYPE,
				recommendation.targetContract(),
				edits(profile)));
	}

	private static void validateTarget(
			ContainerRecommendation recommendation,
			ContainerMigrationReadiness readiness,
			List<PlanningDiagnostic> diagnostics) {
		if (!recommendation.targetContract().equals(readiness.targetContract())
				|| recommendation.targetContract().shape() != ContainerShape.SET
				|| recommendation.targetContract().mutability() != Mutability.MUTABLE
				|| recommendation.targetContract().orderRequirement()
						!= OrderRequirement.ENCOUNTER
				|| recommendation.targetContract().uniquenessRequirement()
						!= UniquenessRequirement.REQUIRED) {
			diagnostics.add(diagnostic(
					DiagnosticKind.UNSUPPORTED_TARGET,
					"The first unique-sequence rewrite requires a mutable encounter-ordered set.")); //$NON-NLS-1$
		}
	}

	private static void validateSource(
			ContainerUsageProfile profile,
			List<PlanningDiagnostic> diagnostics) {
		if (profile.completeness() != AnalysisCompleteness.LOCAL_USAGE_COMPLETE
				|| profile.escapeLevel() != EscapeLevel.LOCAL
				|| profile.aliasingContract() != AliasingContract.NO_OBSERVED_ALIAS
				|| profile.concurrency().exposure() != ThreadExposure.THREAD_CONFINED
				|| !profile.identity().hasResolvedBinding()) {
			diagnostics.add(diagnostic(
					DiagnosticKind.SOURCE_NOT_STRICTLY_LOCAL,
					"The first set rewrite requires one complete, alias-free, thread-confined local value.")); //$NON-NLS-1$
		}
		if (profile.currentShape() != ContainerShape.LIST
				|| profile.elementDomain() != ElementDomain.REFERENCE
						&& profile.elementDomain() != ElementDomain.ENUM
				|| !profile.access().append()
				|| !profile.access().membershipQuery()
				|| profile.uniquenessRequirement() != UniquenessRequirement.REQUIRED) {
			diagnostics.add(diagnostic(
					DiagnosticKind.UNSUPPORTED_SOURCE,
					"The source must be a reference sequence whose every insertion suppresses duplicates.")); //$NON-NLS-1$
		}
		if (profile.access().hasPositionalSemantics()
				|| profile.orderRequirement() == OrderRequirement.POSITIONAL) {
			diagnostics.add(diagnostic(
					DiagnosticKind.POSITIONAL_SEMANTICS,
					"Indexed and positional sequence semantics cannot be represented by the ordered set rewrite.")); //$NON-NLS-1$
		}
	}

	private static void validateEvidence(
			ContainerUsageProfile profile,
			List<PlanningDiagnostic> diagnostics) {
		if (count(profile, Kind.DUPLICATE_SUPPRESSION) == 0) {
			diagnostics.add(diagnostic(
					DiagnosticKind.MISSING_DUPLICATE_GUARD,
					"The profile does not contain a proven contains-before-add insertion.")); //$NON-NLS-1$
		}
		if (count(profile, Kind.HASH_STABLE_COMPONENT) == 0) {
			diagnostics.add(diagnostic(
					DiagnosticKind.UNSUPPORTED_SOURCE,
					"The profile does not prove stable equality and hash semantics.")); //$NON-NLS-1$
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
		List<LocalEdit> edits= new ArrayList<>();
		edits.add(new LocalEdit(
				EditKind.CHANGE_LOCAL_DECLARATION,
				profile.identity().sourceStart(),
				profile.identity().sourceLength()));
		edits.add(new LocalEdit(
				EditKind.REPLACE_EMPTY_IMPLEMENTATION,
				profile.identity().sourceStart(),
				profile.identity().sourceLength()));
		for (UsageEvidence evidence : profile.evidence()) {
			EditKind kind= switch (evidence.kind()) {
				case DUPLICATE_SUPPRESSION -> EditKind.REPLACE_DUPLICATE_GUARD;
				case ENCOUNTER_ITERATION -> EditKind.VERIFY_ENCOUNTER_ITERATION;
				default -> null;
			};
			if (kind != null) {
				edits.add(new LocalEdit(
						kind, evidence.sourceStart(), evidence.sourceLength()));
			}
		}
		edits.sort(Comparator
				.comparingInt(LocalEdit::sourceStart)
				.thenComparing(edit -> edit.kind().ordinal()));
		return List.copyOf(edits);
	}

	private static PlanningDiagnostic diagnostic(
			DiagnosticKind kind,
			String message) {
		return new PlanningDiagnostic(kind, message);
	}
}
