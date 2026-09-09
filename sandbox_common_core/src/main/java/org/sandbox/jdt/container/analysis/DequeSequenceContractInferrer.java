/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.AutomationLevel;
import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractAssessment;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractProperty;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AliasingContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

/** Infers report-only deque contracts for proven local FIFO or LIFO list protocols. */
public final class DequeSequenceContractInferrer {

	/** Returns a deque recommendation only for one complete, unambiguous end-removal protocol. */
	public Optional<ContainerRecommendation> infer(ContainerUsageProfile profile) {
		Objects.requireNonNull(profile, "profile"); //$NON-NLS-1$
		if (profile.completeness() != AnalysisCompleteness.LOCAL_USAGE_COMPLETE
				&& profile.completeness() != AnalysisCompleteness.FLOW_COMPLETE) {
			return Optional.empty();
		}
		if (profile.currentShape() != ContainerShape.LIST || !profile.access().append()
				|| !profile.access().positionalRemove()) {
			return Optional.empty();
		}
		boolean fifo= has(profile, Kind.HEAD_REMOVAL);
		boolean lifo= has(profile, Kind.TAIL_REMOVAL);
		if (fifo == lifo) {
			return Optional.empty();
		}

		TargetContainerContract target= new TargetContainerContract(
				ContainerShape.DEQUE,
				OrderRequirement.ENCOUNTER,
				profile.uniquenessRequirement(),
				Mutability.MUTABLE,
				profile.nullContract(),
				fifo
						? "Represent the proven tail-in/head-out FIFO protocol as a deque contract." //$NON-NLS-1$
						: "Represent the proven tail-in/tail-out LIFO protocol as a deque contract."); //$NON-NLS-1$

		List<ContractAssessment> assessments= new ArrayList<>();
		assessments.add(new ContractAssessment(ContractProperty.ORDER, Preservation.PRESERVED,
				fifo
						? "Deque operations can preserve the observed FIFO encounter order without numeric index removal." //$NON-NLS-1$
						: "Deque operations can preserve the observed LIFO end-removal order without numeric index arithmetic.")); //$NON-NLS-1$
		assessments.add(new ContractAssessment(ContractProperty.UNIQUENESS, Preservation.PRESERVED,
				"A deque preserves duplicate elements just as the source list does.")); //$NON-NLS-1$
		assessments.add(new ContractAssessment(ContractProperty.MUTABILITY, Preservation.PRESERVED,
				"The target contract remains continuously mutable.")); //$NON-NLS-1$
		assessments.add(nullAssessment(profile));
		assessments.add(new ContractAssessment(ContractProperty.ALIASING,
				profile.aliasingContract() == AliasingContract.NO_OBSERVED_ALIAS
						? Preservation.PRESERVED : Preservation.REQUIRES_PROOF,
				profile.aliasingContract() == AliasingContract.NO_OBSERVED_ALIAS
						? "The complete analyzed flow contains no observed alias of the local sequence." //$NON-NLS-1$
						: "Aliasing must be closed before selecting an executable deque implementation.")); //$NON-NLS-1$
		assessments.add(new ContractAssessment(ContractProperty.CONCURRENCY,
				profile.concurrency().exposure() == ThreadExposure.THREAD_CONFINED
						? Preservation.PRESERVED : Preservation.REQUIRES_PROOF,
				profile.concurrency().exposure() == ThreadExposure.THREAD_CONFINED
						? "The proven local protocol is thread confined." //$NON-NLS-1$
						: "Thread exposure and synchronization must be proven before execution.")); //$NON-NLS-1$
		assessments.add(new ContractAssessment(ContractProperty.SIGNATURES,
				profile.escapeLevel() == ContainerUsageProfile.EscapeLevel.LOCAL
						? Preservation.PRESERVED : Preservation.REQUIRES_PROOF,
				profile.escapeLevel() == ContainerUsageProfile.EscapeLevel.LOCAL
						? "The analyzed sequence is local and requires no signature change." //$NON-NLS-1$
						: "A nonlocal deque conversion requires coordinated signature planning.")); //$NON-NLS-1$

		Confidence confidence= profile.aliasingContract() == AliasingContract.NO_OBSERVED_ALIAS
				&& profile.concurrency().exposure() == ThreadExposure.THREAD_CONFINED
				&& profile.nullContract() != NullContract.UNKNOWN
				? Confidence.HIGH : Confidence.MEDIUM;
		return Optional.of(new ContainerRecommendation(
				profile,
				target,
				fifo ? ContainerRuleRegistry.fifoSequenceDeque()
						: ContainerRuleRegistry.lifoSequenceDeque(),
				confidence,
				AutomationLevel.REPORT_ONLY,
				assessments));
	}

	private static ContractAssessment nullAssessment(ContainerUsageProfile profile) {
		if (profile.nullContract() == NullContract.UNKNOWN) {
			return new ContractAssessment(ContractProperty.NULLS, Preservation.REQUIRES_PROOF,
					"The semantic deque target retains an unknown null contract; a concrete implementation must not silently reject values accepted by the list."); //$NON-NLS-1$
		}
		return new ContractAssessment(ContractProperty.NULLS, Preservation.PRESERVED,
				"The target contract carries the analyzed null behavior forward to concrete implementation selection."); //$NON-NLS-1$
	}

	private static boolean has(ContainerUsageProfile profile, Kind kind) {
		return profile.evidence().stream().anyMatch(evidence -> evidence.kind() == kind);
	}
}
