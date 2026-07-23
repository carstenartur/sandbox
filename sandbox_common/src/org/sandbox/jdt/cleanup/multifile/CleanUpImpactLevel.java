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

/** Stable impact classification for cleanup activation and reporting. */
public enum CleanUpImpactLevel {
	/** One compilation unit; no externally visible signature change. */
	LOCAL_SAFE(false, true),
	/** Coordinated multi-file change proven closed in the selected source policy. */
	PROJECT_CLOSED(true, false),
	/** API/representation change allowed only with an explicit compatibility policy. */
	COMPATIBILITY_MANAGED(true, false),
	/** Interactive decisions or non-Java changes are required. */
	MANUAL_REFACTORING(true, false);

	private final boolean projectWide;
	private final boolean saveActionAllowed;

	CleanUpImpactLevel(boolean projectWide, boolean saveActionAllowed) {
		this.projectWide= projectWide;
		this.saveActionAllowed= saveActionAllowed;
	}

	/** Returns whether this impact can affect more than one compilation unit. */
	public boolean isProjectWide() {
		return projectWide;
	}

	/** Returns whether this level may run as an ordinary save action. */
	public boolean isSaveActionAllowed() {
		return saveActionAllowed;
	}
}
