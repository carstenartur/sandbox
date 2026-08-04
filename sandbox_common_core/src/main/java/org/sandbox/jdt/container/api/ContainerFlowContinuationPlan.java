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
package org.sandbox.jdt.container.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;

/**
 * Binding-resolved continuation roots found after project scope expansion.
 *
 * <p>Each root is a normal {@link ContainerUsageProfile} that can be passed to the
 * existing local flow builder. Its relationship records how the resulting local graph
 * connects to the boundary node that caused the workspace search.</p>
 */
public record ContainerFlowContinuationPlan(
		List<ContinuationRoot> roots,
		List<ContinuationDiagnostic> diagnostics) {

	public ContainerFlowContinuationPlan {
		roots= List.copyOf(Objects.requireNonNull(roots, "roots")); //$NON-NLS-1$
		diagnostics= List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics")); //$NON-NLS-1$
		validateUniqueRoots(roots);
	}

	/** Returns whether every matched search target had a supported continuation shape. */
	public boolean complete() {
		return diagnostics.isEmpty();
	}

	/** One local variable, field or parameter from which flow analysis can continue. */
	public record ContinuationRoot(
			String boundaryNodeId,
			ContinuationKind kind,
			Relationship relationship,
			EdgeKind transferKind,
			String compilationUnitHandle,
			String exactTargetHandle,
			int signatureIndex,
			ContainerUsageProfile profile) {

		public ContinuationRoot {
			boundaryNodeId= requiredText(boundaryNodeId, "boundaryNodeId"); //$NON-NLS-1$
			Objects.requireNonNull(kind, "kind"); //$NON-NLS-1$
			Objects.requireNonNull(relationship, "relationship"); //$NON-NLS-1$
			compilationUnitHandle= requiredText(
					compilationUnitHandle, "compilationUnitHandle"); //$NON-NLS-1$
			exactTargetHandle= requiredText(exactTargetHandle, "exactTargetHandle"); //$NON-NLS-1$
			if (signatureIndex < -1) {
				throw new IllegalArgumentException(
						"signatureIndex must be -1 or a parameter index"); //$NON-NLS-1$
			}
			Objects.requireNonNull(profile, "profile"); //$NON-NLS-1$
			if (!profile.identity().hasResolvedBinding()) {
				throw new IllegalArgumentException(
						"A continuation root requires a resolved variable binding"); //$NON-NLS-1$
			}
			if (relationship == Relationship.SAME_NODE && transferKind != null) {
				throw new IllegalArgumentException(
						"A same-node continuation cannot declare a transfer edge"); //$NON-NLS-1$
			}
			if (relationship != Relationship.SAME_NODE && transferKind == null) {
				throw new IllegalArgumentException(
						"A transferred continuation requires an edge kind"); //$NON-NLS-1$
			}
		}

		/** Stable deterministic key used for result de-duplication. */
		public String stableKey() {
			return boundaryNodeId + '|' + kind + '|' + relationship + '|'
					+ exactTargetHandle + '|' + signatureIndex + '|'
					+ compilationUnitHandle + '|' + profile.identity().bindingKey();
		}
	}

	/** One unsupported or unresolved continuation site. */
	public record ContinuationDiagnostic(
			DiagnosticKind kind,
			String compilationUnitHandle,
			String exactTargetHandle,
			String message,
			int sourceStart,
			int sourceLength) {

		public ContinuationDiagnostic {
			Objects.requireNonNull(kind, "kind"); //$NON-NLS-1$
			compilationUnitHandle= requiredText(
					compilationUnitHandle, "compilationUnitHandle"); //$NON-NLS-1$
			exactTargetHandle= requiredText(exactTargetHandle, "exactTargetHandle"); //$NON-NLS-1$
			message= requiredText(message, "message"); //$NON-NLS-1$
			if (sourceStart < 0 || sourceLength < 0) {
				throw new IllegalArgumentException("Source range must not be negative"); //$NON-NLS-1$
			}
		}
	}

	public enum ContinuationKind {
		FIELD,
		PARAMETER_DECLARATION,
		CALL_ARGUMENT,
		RETURN_EXPRESSION,
		RETURN_CONSUMER
	}

	public enum Relationship {
		SAME_NODE,
		ROOT_TO_BOUNDARY,
		BOUNDARY_TO_ROOT
	}

	public enum DiagnosticKind {
		INVALID_SIGNATURE_INDEX,
		NON_ARRAY_VALUE,
		UNRESOLVED_BINDING,
		UNSUPPORTED_ARGUMENT,
		UNSUPPORTED_RETURN_EXPRESSION,
		UNSUPPORTED_RETURN_CONSUMER,
		METHOD_REFERENCE,
		TARGET_NOT_FOUND
	}

	private static void validateUniqueRoots(List<ContinuationRoot> roots) {
		Set<String> keys= new HashSet<>();
		for (ContinuationRoot root : roots) {
			if (!keys.add(root.stableKey())) {
				throw new IllegalArgumentException(
						"Duplicate container flow continuation root: " + root.stableKey()); //$NON-NLS-1$
			}
		}
	}

	private static String requiredText(String value, String fieldName) {
		String text= Objects.requireNonNull(value, fieldName).strip();
		if (text.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must not be empty"); //$NON-NLS-1$
		}
		return text;
	}
}
