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

/**
 * User-visible impact classification for cleanup execution and profiles.
 *
 * <p>The classification is deliberately independent of a particular UI. IDE,
 * headless and CI callers can use the same metadata when deciding whether a
 * cleanup may run as a save action or requires an explicit preview.</p>
 */
public enum CleanUpImpact {

	/** One compilation unit, without an externally visible signature change. */
	LOCAL_SAFE(true, false,
			"The cleanup is local and does not claim to change an externally visible contract."), //$NON-NLS-1$

	/** Multiple source units whose complete reference closure was proven. */
	PROJECT_CLOSED(false, true,
			"The cleanup changes multiple source units and is safe only for the proven closed project scope."), //$NON-NLS-1$

	/** An external contract changes under an explicit compatibility policy. */
	COMPATIBILITY_MANAGED(false, true,
			"The cleanup changes an external contract and requires an explicit compatibility policy and adapters."), //$NON-NLS-1$

	/** Interactive decisions or non-Java resources prevent automatic cleanup. */
	MANUAL_REFACTORING(false, true,
			"The change requires an interactive refactoring and must not run as an automatic cleanup."); //$NON-NLS-1$

	private final boolean saveActionEligible;
	private final boolean explicitPreviewRequired;
	private final String compatibilityStatement;

	CleanUpImpact(boolean saveActionEligible, boolean explicitPreviewRequired, String compatibilityStatement) {
		this.saveActionEligible= saveActionEligible;
		this.explicitPreviewRequired= explicitPreviewRequired;
		this.compatibilityStatement= compatibilityStatement;
	}

	/** Returns whether profiles may offer this impact level as an ordinary save action. */
	public boolean saveActionEligible() {
		return saveActionEligible;
	}

	/** Returns whether execution requires an explicit affected-scope preview. */
	public boolean explicitPreviewRequired() {
		return explicitPreviewRequired;
	}

	/** Returns the compatibility claim that must be shown before execution. */
	public String compatibilityStatement() {
		return compatibilityStatement;
	}
}
