/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.analysis;

import java.util.List;
import java.util.Objects;

import org.sandbox.jdt.container.api.ConcurrencyProtocol;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.EvidenceKind;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.DiagnosticOnly;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.Severity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.SynchronizationKind;

/**
 * Turns synchronized-wrapper seed evidence into a report-only diagnostic.
 *
 * <p>This inferrer never upgrades the seed to a complete protocol proof. In particular,
 * correctly locked observed iteration remains informational until all accesses, aliases
 * and flow boundaries have been classified by later analysis.</p>
 */
public final class SynchronizedWrapperAssessmentInferrer {

	/** Returns the strongest report justified by the currently observed wrapper seed. */
	public DiagnosticOnly assess(ConcurrencyProtocol protocol) {
		Objects.requireNonNull(protocol, "protocol"); //$NON-NLS-1$
		if (protocol.summary().synchronization() != SynchronizationKind.SYNCHRONIZED_WRAPPER) {
			throw new IllegalArgumentException("Expected a synchronized-wrapper protocol"); //$NON-NLS-1$
		}
		if (hasEvidence(protocol, EvidenceKind.UNPROTECTED_ACCESS)) {
			return new DiagnosticOnly(
					protocol,
					Severity.WARNING,
					List.of("Observed iteration is not synchronized on the wrapper itself.")); //$NON-NLS-1$
		}
		if (hasEvidence(protocol, EvidenceKind.LOCKED_ITERATION)) {
			return new DiagnosticOnly(
					protocol,
					Severity.INFO,
					List.of("Observed enhanced-for iteration uses the wrapper monitor; full usage proof is still required.")); //$NON-NLS-1$
		}
		return new DiagnosticOnly(
				protocol,
				Severity.INFO,
				List.of("Synchronized wrapper detected; no enhanced-for iteration contract has been observed yet.")); //$NON-NLS-1$
	}

	private static boolean hasEvidence(ConcurrencyProtocol protocol, EvidenceKind kind) {
		return protocol.evidence().stream().anyMatch(item -> item.kind() == kind);
	}
}
