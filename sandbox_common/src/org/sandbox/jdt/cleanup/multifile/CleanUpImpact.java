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

import java.util.Objects;

/** Immutable, reportable impact claim for one configured cleanup. */
public record CleanUpImpact(String cleanupId, CleanUpImpactLevel level, String compatibilityStatement) {

	public CleanUpImpact {
		cleanupId= requireText(cleanupId, "cleanupId"); //$NON-NLS-1$
		level= Objects.requireNonNull(level, "level"); //$NON-NLS-1$
		compatibilityStatement= requireText(compatibilityStatement, "compatibilityStatement"); //$NON-NLS-1$
	}

	/** Returns whether this configured cleanup may run as an ordinary save action. */
	public boolean isSaveActionAllowed() {
		return level.isSaveActionAllowed();
	}

	private static String requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank"); //$NON-NLS-1$
		}
		return value;
	}
}
