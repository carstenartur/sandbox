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
package org.sandbox.jdt.container.cleanup.internal.corext.fix;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;

import org.sandbox.jdt.container.api.ClosedSourceParameterMigrationPlan;

/**
 * Narrow facade between the coordinator implementation and the UI cleanup lifecycle.
 * Candidate identifiers are made opaque before they enter exported diagnostics;
 * compilation-unit handles remain internal lifecycle data and are anonymized by
 * {@code MultiFileCleanUpDiagnostics} only when explicitly exported as JSON.
 */
public final class ClosedSourceContainerMigrationService {

	private final ClosedSourceContainerMigrationCoordinator coordinator=
			new ClosedSourceContainerMigrationCoordinator();

	/** Discovers the required editable source closure without retaining AST nodes. */
	public Discovery discover(IJavaProject project, Collection<ICompilationUnit> currentScope,
			IProgressMonitor monitor) throws CoreException {
		ClosedSourceContainerMigrationCoordinator.Discovery result=
				coordinator.discover(project, currentScope, monitor);
		return new Discovery(result.candidateFound(), result.complete(),
				externalCandidateId(result.candidateId()), result.reasonCode(), result.message(),
				result.requiredUnits());
	}

	/** Builds the immutable aggregate rewrite plan from the fully selected closure. */
	public Planning plan(IJavaProject project, ICompilationUnit[] compilationUnits,
			IProgressMonitor monitor) throws CoreException {
		ClosedSourceContainerMigrationCoordinator.Planning result=
				coordinator.plan(project, compilationUnits, monitor);
		return new Planning(result.plan(), result.candidateFound(), result.complete(),
				externalCandidateId(result.candidateId()), result.ownerHandle(),
				result.reasonCode(), result.message(), result.requiredHandles());
	}

	/** AST-free discovery result for cleanup scope expansion. */
	public record Discovery(boolean candidateFound, boolean complete, String candidateId,
			String reasonCode, String message, List<ICompilationUnit> requiredUnits) {

		public Discovery {
			candidateId= Objects.requireNonNull(candidateId);
			reasonCode= Objects.requireNonNull(reasonCode);
			message= Objects.requireNonNull(message);
			requiredUnits= List.copyOf(Objects.requireNonNull(requiredUnits));
		}
	}

	/** AST-free planning result consumed by the planned-cleanup lifecycle. */
	public record Planning(Optional<ClosedSourceParameterMigrationPlan> plan,
			boolean candidateFound, boolean complete, String candidateId,
			String ownerHandle, String reasonCode, String message,
			List<String> requiredHandles) {

		public Planning {
			plan= Objects.requireNonNull(plan);
			candidateId= Objects.requireNonNull(candidateId);
			ownerHandle= Objects.requireNonNull(ownerHandle);
			reasonCode= Objects.requireNonNull(reasonCode);
			message= Objects.requireNonNull(message);
			requiredHandles= List.copyOf(Objects.requireNonNull(requiredHandles));
		}
	}

	private static String externalCandidateId(String internalId) {
		return internalId == null || internalId.isBlank()
				? "" //$NON-NLS-1$
				: "candidate-" + digest(internalId); //$NON-NLS-1$
	}

	private static String digest(String value) {
		try {
			byte[] bytes= MessageDigest.getInstance("SHA-256") //$NON-NLS-1$
					.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(bytes, 0, 6);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e); //$NON-NLS-1$
		}
	}
}
