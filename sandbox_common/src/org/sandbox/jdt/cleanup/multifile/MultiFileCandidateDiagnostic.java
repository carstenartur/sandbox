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
package org.sandbox.jdt.cleanup.multifile;

import java.util.List;
import java.util.Objects;

/** Immutable machine-readable diagnostic for one coordinated-cleanup candidate. */
public record MultiFileCandidateDiagnostic(String candidateId, String ownerCompilationUnitHandle,
		MultiFileCandidateOutcome outcome, String reasonCode, String message,
		List<String> relatedCompilationUnitHandles) {

	/** Validates and normalizes candidate diagnostics. */
	public MultiFileCandidateDiagnostic {
		candidateId= Objects.requireNonNull(candidateId);
		ownerCompilationUnitHandle= Objects.requireNonNull(ownerCompilationUnitHandle);
		outcome= Objects.requireNonNull(outcome);
		reasonCode= Objects.requireNonNull(reasonCode);
		message= Objects.requireNonNull(message);
		relatedCompilationUnitHandles= relatedCompilationUnitHandles == null
				? List.of()
				: relatedCompilationUnitHandles.stream().filter(Objects::nonNull).distinct().sorted().toList();
	}

	/** Creates a successful transformed-candidate diagnostic. */
	public static MultiFileCandidateDiagnostic transformed(String candidateId, String ownerHandle,
			String message, List<String> relatedHandles) {
		return new MultiFileCandidateDiagnostic(candidateId, ownerHandle, MultiFileCandidateOutcome.TRANSFORMED,
				"TRANSFORMED", message, relatedHandles); //$NON-NLS-1$
	}

	/** Creates a refused-candidate diagnostic with a stable reason code. */
	public static MultiFileCandidateDiagnostic rejected(String candidateId, String ownerHandle,
			String reasonCode, String message, List<String> relatedHandles) {
		return new MultiFileCandidateDiagnostic(candidateId, ownerHandle, MultiFileCandidateOutcome.REJECTED,
				reasonCode, message, relatedHandles);
	}
}
