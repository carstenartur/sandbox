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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

/** Immutable aggregate diagnostics for one coordinated cleanup planning run. */
public record MultiFileCleanUpDiagnostics(String cleanupId, MultiFileScopeDiagnostic scope,
		List<MultiFileCandidateDiagnostic> candidates) {

	/** Validates and deterministically orders diagnostics. */
	public MultiFileCleanUpDiagnostics {
		cleanupId= Objects.requireNonNull(cleanupId);
		scope= scope == null ? MultiFileScopeDiagnostic.empty() : scope;
		List<MultiFileCandidateDiagnostic> normalized= candidates == null ? List.of() : new ArrayList<>(candidates);
		normalized.sort(Comparator.comparing(MultiFileCandidateDiagnostic::ownerCompilationUnitHandle)
				.thenComparing(MultiFileCandidateDiagnostic::candidateId)
				.thenComparing(candidate -> candidate.outcome().name())
				.thenComparing(MultiFileCandidateDiagnostic::reasonCode));
		candidates= List.copyOf(normalized);
	}

	/** @return empty diagnostics for a cleanup with no coordinated candidate */
	public static MultiFileCleanUpDiagnostics empty() {
		return new MultiFileCleanUpDiagnostics("unknown", MultiFileScopeDiagnostic.empty(), List.of()); //$NON-NLS-1$
	}

	/** Returns a copy containing the observed host scope expansion. */
	public MultiFileCleanUpDiagnostics withScope(MultiFileScopeDiagnostic newScope) {
		return new MultiFileCleanUpDiagnostics(cleanupId, newScope, candidates);
	}

	/** Adds one concise, nonfatal planning summary to the cleanup preview status. */
	public void appendSummary(RefactoringStatus status) {
		if (status == null || candidates.isEmpty() && scope.addedCompilationUnitHandles().isEmpty()) {
			return;
		}
		long transformed= candidates.stream()
				.filter(candidate -> candidate.outcome() == MultiFileCandidateOutcome.TRANSFORMED).count();
		long rejected= candidates.stream()
				.filter(candidate -> candidate.outcome() == MultiFileCandidateOutcome.REJECTED).count();
		status.addInfo("Coordinated cleanup " + cleanupId + ": " + candidates.size() //$NON-NLS-1$ //$NON-NLS-2$
				+ " candidate diagnostics, " + transformed + " transformed, " + rejected //$NON-NLS-1$ //$NON-NLS-2$
				+ " rejected; " + scope.addedCompilationUnitHandles().size() //$NON-NLS-1$
				+ " compilation units added to the selected scope."); //$NON-NLS-1$
	}

	/** @return deterministic JSON suitable for CI artifacts and headless callers */
	public String toJson() {
		StringBuilder json= new StringBuilder(512);
		json.append('{');
		property(json, "schemaVersion", "1").append(','); //$NON-NLS-1$ //$NON-NLS-2$
		property(json, "cleanupId", cleanupId).append(','); //$NON-NLS-1$
		json.append("\"scope\":{"); //$NON-NLS-1$
		arrayProperty(json, "selectedCompilationUnits", scope.selectedCompilationUnitHandles()).append(','); //$NON-NLS-1$
		arrayProperty(json, "addedCompilationUnits", scope.addedCompilationUnitHandles()).append(','); //$NON-NLS-1$
		property(json, "reasonCode", scope.reasonCode()).append(','); //$NON-NLS-1$
		property(json, "explanation", scope.explanation()).append(','); //$NON-NLS-1$
		json.append("\"complete\":").append(scope.complete()).append("},"); //$NON-NLS-1$ //$NON-NLS-2$
		json.append("\"candidates\":["); //$NON-NLS-1$
		for (int index= 0; index < candidates.size(); index++) {
			if (index > 0) {
				json.append(',');
			}
			MultiFileCandidateDiagnostic candidate= candidates.get(index);
			json.append('{');
			property(json, "candidateId", candidate.candidateId()).append(','); //$NON-NLS-1$
			property(json, "ownerCompilationUnit", candidate.ownerCompilationUnitHandle()).append(','); //$NON-NLS-1$
			property(json, "outcome", candidate.outcome().name()).append(','); //$NON-NLS-1$
			property(json, "reasonCode", candidate.reasonCode()).append(','); //$NON-NLS-1$
			property(json, "message", candidate.message()).append(','); //$NON-NLS-1$
			arrayProperty(json, "relatedCompilationUnits", candidate.relatedCompilationUnitHandles()); //$NON-NLS-1$
			json.append('}');
		}
		return json.append("]}").toString(); //$NON-NLS-1$
	}

	private static StringBuilder property(StringBuilder json, String name, String value) {
		return json.append('"').append(escape(name)).append("\":\"") //$NON-NLS-1$
				.append(escape(value)).append('"');
	}

	private static StringBuilder arrayProperty(StringBuilder json, String name, List<String> values) {
		json.append('"').append(escape(name)).append("\":["); //$NON-NLS-1$
		for (int index= 0; index < values.size(); index++) {
			if (index > 0) {
				json.append(',');
			}
			json.append('"').append(escape(values.get(index))).append('"');
		}
		return json.append(']');
	}

	private static String escape(String value) {
		StringBuilder escaped= new StringBuilder(value.length() + 16);
		for (int index= 0; index < value.length(); index++) {
			char character= value.charAt(index);
			switch (character) {
				case '"' -> escaped.append("\\\""); //$NON-NLS-1$
				case '\\' -> escaped.append("\\\\"); //$NON-NLS-1$
				case '\b' -> escaped.append("\\b"); //$NON-NLS-1$
				case '\f' -> escaped.append("\\f"); //$NON-NLS-1$
				case '\n' -> escaped.append("\\n"); //$NON-NLS-1$
				case '\r' -> escaped.append("\\r"); //$NON-NLS-1$
				case '\t' -> escaped.append("\\t"); //$NON-NLS-1$
				default -> {
					if (character < 0x20) {
						escaped.append(String.format("\\u%04x", Integer.valueOf(character))); //$NON-NLS-1$
					} else {
						escaped.append(character);
					}
				}
		}
		return escaped.toString();
	}
}
