/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.preferences.cleanup;

import org.eclipse.osgi.util.NLS;

/**
 * Message bundle for shift check cleanup preferences.
 */
public final class CleanUpMessages extends NLS {
	private static final String BUNDLE_NAME = "org.sandbox.jdt.internal.ui.preferences.cleanup.CleanUpMessages"; //$NON-NLS-1$

	public static String ShiftOutOfRangeTabPage_CheckboxName_ShiftOutOfRange;
	public static String ShiftOutOfRangeTabPage_GroupName_ShiftOutOfRange;

	static {
		NLS.initializeMessages(BUNDLE_NAME, CleanUpMessages.class);
	}

	private CleanUpMessages() {
		// Do not instantiate
	}
}
