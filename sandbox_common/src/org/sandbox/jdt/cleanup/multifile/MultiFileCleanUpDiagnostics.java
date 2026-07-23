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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

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
		Map<String, Long> rejectedByReason= new TreeMap<>();
		for (MultiFileCandidateDiagnostic candidate : candidates) {
			if (candidate.outcome() == MultiFileCandidateOutcome.REJECTED) {
				rejectedByReason.merge(candidate.reasonCode(), Long.valueOf(1), Long::sum);
			}
		}
		long rejected= rejectedByReason.values().stream().mapToLong(Long::longValue).sum();
		StringBuilder summary= new StringBuilder(160)
				.append("Coordinated cleanup ").append(cleanupId).append(": ") //$NON-NLS-1$ //$NON-NLS-2$
				.append(scope.selectedCompilationUnitHandles().size()).append(" selected, ") //$NON-NLS-1$
				.append(scope.addedCompilationUnitHandles().size()).append(" added; ") //$NON-NLS-1$
				.append(transformed).append(" transformed, ").append(rejected).append(" rejected"); //$NON-NLS-1$ //$NON-NLS-2$
		if (!rejectedByReason.isEmpty()) {
			summary.append(" ("); //$NON-NLS-1$
			int index= 0;
			for (Map.Entry<String, Long> entry : rejectedByReason.entrySet()) {
				if (index++ > 0) {
					summary.append(", "); //$NON-NLS-1$
				}
				summary.append(entry.getKey()).append('=').append(entry.getValue());
			}
			summary.append(')');
		}
		status.addInfo(summary.append('.').toString());
	}

	/**
	 * Returns deterministic JSON suitable for CI artifacts and headless callers.
	 * Compilation-unit handles are replaced with stable opaque identifiers so the
	 * explicit export does not disclose workspace paths or Java model handles.
	 *
	 * @return privacy-preserving deterministic JSON
	 */
	public String toJson() {
		StringBuilder json= new StringBuilder(512);
		json.append('{');
		property(json, "schemaVersion", "1").append(','); //$NON-NLS-1$ //$NON-NLS-2$
		property(json, "cleanupId", cleanupId).append(','); //$NON-NLS-1$
		json.append("\"scope\":{"); //$NON-NLS-1$
		arrayProperty(json, "selectedCompilationUnits", externalUnitIds(scope.selectedCompilationUnitHandles())).append(','); //$NON-NLS-1$
		arrayProperty(json, "addedCompilationUnits", externalUnitIds(scope.addedCompilationUnitHandles())).append(','); //$NON-NLS-1$
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
			property(json, "ownerCompilationUnit", externalUnitId(candidate.ownerCompilationUnitHandle())).append(','); //$NON-NLS-1$
			property(json, "outcome", candidate.outcome().name()).append(','); //$NON-NLS-1$
			property(json, "reasonCode", candidate.reasonCode()).append(','); //$NON-NLS-1$
			property(json, "message", candidate.message()).append(','); //$NON-NLS-1$
			arrayProperty(json, "relatedCompilationUnits", externalUnitIds(candidate.relatedCompilationUnitHandles())); //$NON-NLS-1$
			json.append('}');
		}
		return json.append("]}").toString(); //$NON-NLS-1$
	}

	private static List<String> externalUnitIds(List<String> handles) {
		return handles.stream().map(MultiFileCleanUpDiagnostics::externalUnitId).sorted().toList();
	}

	private static String externalUnitId(String handle) {
		try {
			byte[] digest= MessageDigest.getInstance("SHA-256").digest(handle.getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$
			return "cu-" + HexFormat.of().formatHex(digest, 0, 6); //$NON-NLS-1$
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e); //$NON-NLS-1$
		}
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
		}
		return escaped.toString();
	}
}
