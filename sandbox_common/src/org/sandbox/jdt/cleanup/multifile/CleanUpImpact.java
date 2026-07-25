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
 * User-visible compatibility classification for cleanup transformations.
 */
public enum CleanUpImpact {

	/** One compilation unit and no externally visible signature change. */
	LOCAL_SAFE(true, false,
			"The transformation is local and does not change an externally visible contract."), //$NON-NLS-1$

	/** Coordinated change whose references are proven closed in the selected source scope. */
	PROJECT_CLOSED(false, true,
			"The transformation changes multiple compilation units and is safe only for the proven closed source scope."), //$NON-NLS-1$

	/** Public or externally represented state with an explicit compatibility adapter policy. */
	COMPATIBILITY_MANAGED(false, true,
			"The transformation changes an external representation and requires explicit compatibility adapters."), //$NON-NLS-1$

	/** Interactive or non-Java migration that must use a dedicated refactoring workflow. */
	MANUAL_REFACTORING(false, true,
			"The transformation requires interactive decisions or non-Java resource changes."); //$NON-NLS-1$

	private final boolean ordinarySaveActionAllowed;
	private final boolean projectWide;
	private final String compatibilityStatement;

	CleanUpImpact(boolean ordinarySaveActionAllowed, boolean projectWide, String compatibilityStatement) {
		this.ordinarySaveActionAllowed= ordinarySaveActionAllowed;
		this.projectWide= projectWide;
		this.compatibilityStatement= compatibilityStatement;
	}

	/** Whether an ordinary save action may run this impact level without interaction. */
	public boolean ordinarySaveActionAllowed() {
		return ordinarySaveActionAllowed;
	}

	/** Whether the impact can affect more than one compilation unit. */
	public boolean projectWide() {
		return projectWide;
	}

	/** Stable compatibility statement for previews, CLI reports and documentation. */
	public String compatibilityStatement() {
		return compatibilityStatement;
	}
}
