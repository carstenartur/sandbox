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

/** Central fail-closed activation policy for impact-aware cleanups. */
public final class CleanUpImpactPolicy {

	public static final String ALLOWED= "ALLOWED"; //$NON-NLS-1$
	public static final String PROJECT_WIDE_SAVE_ACTION_BLOCKED= "PROJECT_WIDE_SAVE_ACTION_BLOCKED"; //$NON-NLS-1$
	public static final String MANUAL_REFACTORING_REQUIRED= "MANUAL_REFACTORING_REQUIRED"; //$NON-NLS-1$

	/** Stable policy result suitable for UI, CLI and machine-readable reports. */
	public record Decision(boolean allowed, boolean explicitPreviewRequired, String reasonCode,
			String explanation) {
		public Decision {
			Objects.requireNonNull(reasonCode, "reasonCode"); //$NON-NLS-1$
			Objects.requireNonNull(explanation, "explanation"); //$NON-NLS-1$
		}
	}

	private CleanUpImpactPolicy() {
		// utility class
	}

	/**
	 * Evaluates one configured cleanup for a concrete execution surface.
	 * Project-wide levels are never permitted as ordinary save actions and manual
	 * refactorings are rejected by every automatic cleanup surface.
	 */
	public static Decision evaluate(CleanUpImpact impact, CleanUpExecutionSurface surface) {
		Objects.requireNonNull(impact, "impact"); //$NON-NLS-1$
		Objects.requireNonNull(surface, "surface"); //$NON-NLS-1$
		CleanUpImpactLevel level= impact.level();
		if (level == CleanUpImpactLevel.MANUAL_REFACTORING) {
			return new Decision(false, true, MANUAL_REFACTORING_REQUIRED,
					"This change requires an interactive refactoring workflow and cannot run as a cleanup."); //$NON-NLS-1$
		}
		if (surface == CleanUpExecutionSurface.SAVE_ACTION && !impact.isSaveActionAllowed()) {
			return new Decision(false, true, PROJECT_WIDE_SAVE_ACTION_BLOCKED,
					"Project-wide cleanup impact cannot run as an ordinary save action."); //$NON-NLS-1$
		}
		boolean previewRequired= level != CleanUpImpactLevel.LOCAL_SAFE;
		return new Decision(true, previewRequired, ALLOWED,
				previewRequired
						? "Execution is allowed only with an explicit project-level preview or report." //$NON-NLS-1$
						: "Local cleanup execution is allowed on this surface."); //$NON-NLS-1$
	}
}
