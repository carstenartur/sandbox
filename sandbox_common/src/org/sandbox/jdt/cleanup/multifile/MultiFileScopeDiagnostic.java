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

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Immutable account of selected and automatically added cleanup source units. */
public record MultiFileScopeDiagnostic(List<String> selectedCompilationUnitHandles,
		List<String> addedCompilationUnitHandles, String reasonCode, String explanation,
		boolean complete) {

	/** Validates and normalizes the scope diagnostic. */
	public MultiFileScopeDiagnostic {
		selectedCompilationUnitHandles= normalize(selectedCompilationUnitHandles);
		addedCompilationUnitHandles= normalize(addedCompilationUnitHandles);
		reasonCode= Objects.requireNonNull(reasonCode);
		explanation= Objects.requireNonNull(explanation);
	}

	/** Creates a diagnostic from general collections while retaining deterministic order. */
	public MultiFileScopeDiagnostic(Collection<String> selectedCompilationUnitHandles,
			Collection<String> addedCompilationUnitHandles, String reasonCode, String explanation,
			boolean complete) {
		this(toList(selectedCompilationUnitHandles), toList(addedCompilationUnitHandles), reasonCode, explanation,
				complete);
	}

	/** @return an empty, complete scope diagnostic */
	public static MultiFileScopeDiagnostic empty() {
		return new MultiFileScopeDiagnostic(List.of(), List.of(), "NO_EXPANSION", //$NON-NLS-1$
				"No coordinated source-scope expansion was required.", true); //$NON-NLS-1$
	}

	private static List<String> toList(Collection<String> handles) {
		return handles == null ? null : List.copyOf(handles);
	}

	private static List<String> normalize(List<String> handles) {
		return handles == null ? List.of() : handles.stream().filter(Objects::nonNull).distinct().sorted().toList();
	}
}
