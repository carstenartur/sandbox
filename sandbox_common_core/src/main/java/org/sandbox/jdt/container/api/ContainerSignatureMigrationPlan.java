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

/**
 * Plan describing which method signatures must change atomically for one semantic
 * container migration.
 */
public record ContainerSignatureMigrationPlan(
		TargetContainerContract targetContract,
		List<SignatureAtomicityGroup> groups,
		PlanningStatus status,
		List<SignatureDiagnostic> diagnostics) {

	public ContainerSignatureMigrationPlan {
		Objects.requireNonNull(targetContract, "targetContract"); //$NON-NLS-1$
		groups= List.copyOf(Objects.requireNonNull(groups, "groups")); //$NON-NLS-1$
		Objects.requireNonNull(status, "status"); //$NON-NLS-1$
		diagnostics= List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics")); //$NON-NLS-1$
		validateUniqueGroups(groups);
		if (status == PlanningStatus.REJECTED && diagnostics.isEmpty()) {
			throw new IllegalArgumentException("A rejected signature plan requires diagnostics"); //$NON-NLS-1$
		}
		if (status == PlanningStatus.CLOSED_SOURCE_AUTOMATIC
				&& (groups.isEmpty() || !diagnostics.isEmpty())) {
			throw new IllegalArgumentException(
					"A closed-source automatic signature plan requires groups without diagnostics"); //$NON-NLS-1$
		}
	}

	/** One parameter or return position that must move as one hierarchy-wide unit. */
	public record SignatureAtomicityGroup(
			String groupId,
			PositionKind positionKind,
			int signatureIndex,
			List<SignatureMember> members,
			BridgeFeasibility bridgeFeasibility,
			String explanation) {

		public SignatureAtomicityGroup {
			groupId= requiredText(groupId, "groupId"); //$NON-NLS-1$
			Objects.requireNonNull(positionKind, "positionKind"); //$NON-NLS-1$
			if (positionKind == PositionKind.PARAMETER && signatureIndex < 0) {
				throw new IllegalArgumentException("A parameter group requires an index"); //$NON-NLS-1$
			}
			if (positionKind == PositionKind.RETURN && signatureIndex != -1) {
				throw new IllegalArgumentException("A return group must use signature index -1"); //$NON-NLS-1$
			}
			members= List.copyOf(Objects.requireNonNull(members, "members")); //$NON-NLS-1$
			if (members.isEmpty()) {
				throw new IllegalArgumentException("A signature group requires members"); //$NON-NLS-1$
			}
			validateUniqueMembers(members);
			Objects.requireNonNull(bridgeFeasibility, "bridgeFeasibility"); //$NON-NLS-1$
			explanation= requiredText(explanation, "explanation"); //$NON-NLS-1$
		}
	}

	/** One exact method declaration in an atomic signature group. */
	public record SignatureMember(
			String javaElementHandle,
			String ownerKey,
			String compilationUnitHandle,
			String flowNodeId) {

		public SignatureMember {
			javaElementHandle= requiredText(javaElementHandle, "javaElementHandle"); //$NON-NLS-1$
			ownerKey= optionalText(ownerKey);
			compilationUnitHandle= requiredText(
					compilationUnitHandle, "compilationUnitHandle"); //$NON-NLS-1$
			flowNodeId= requiredText(flowNodeId, "flowNodeId"); //$NON-NLS-1$
		}
	}

	/** One reason why a complete atomic group could not be established. */
	public record SignatureDiagnostic(
			DiagnosticKind kind,
			String sourceNodeId,
			String javaElementHandle,
			String message) {

		public SignatureDiagnostic {
			Objects.requireNonNull(kind, "kind"); //$NON-NLS-1$
			sourceNodeId= requiredText(sourceNodeId, "sourceNodeId"); //$NON-NLS-1$
			javaElementHandle= optionalText(javaElementHandle);
			message= requiredText(message, "message"); //$NON-NLS-1$
		}
	}

	public enum PositionKind {
		PARAMETER,
		RETURN
	}

	public enum BridgeFeasibility {
		/** The JVM/Java signatures can coexist, but semantic adaptation requires policy. */
		OVERLOAD_POSSIBLE_POLICY_REQUIRED,
		/** Java cannot overload two methods solely by return type. */
		SAME_NAME_RETURN_BRIDGE_IMPOSSIBLE
	}

	public enum PlanningStatus {
		NO_SIGNATURE_CHANGE,
		/** All source declarations and uses are closed and may be changed directly. */
		CLOSED_SOURCE_AUTOMATIC,
		/** Signatures are known, but compatibility or execution policy is still required. */
		REPORT_ONLY,
		REJECTED
	}

	public enum DiagnosticKind {
		FLOW_NOT_CLOSED,
		MISSING_SIGNATURE_NODE,
		AMBIGUOUS_SIGNATURE_NODE,
		MISSING_METHOD_HANDLE,
		UNSUPPORTED_AUTOMATIC_GROUP
	}

	private static void validateUniqueGroups(List<SignatureAtomicityGroup> groups) {
		Set<String> ids= HashSet.newHashSet(groups.size());
		for (SignatureAtomicityGroup group : groups) {
			if (!ids.add(group.groupId())) {
				throw new IllegalArgumentException("Duplicate signature group: " + group.groupId()); //$NON-NLS-1$
			}
		}
	}

	private static void validateUniqueMembers(List<SignatureMember> members) {
		Set<String> handles= HashSet.newHashSet(members.size());
		for (SignatureMember member : members) {
			if (!handles.add(member.javaElementHandle())) {
				throw new IllegalArgumentException(
						"Duplicate signature member: " + member.javaElementHandle()); //$NON-NLS-1$
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

	private static String optionalText(String value) {
		return value == null ? "" : value.strip(); //$NON-NLS-1$
	}
}
