/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.preferences.cleanup;

import org.eclipse.osgi.util.NLS;

public class CleanUpMessages extends NLS {
	private static final String BUNDLE_NAME = "org.sandbox.jdt.internal.ui.preferences.cleanup.CleanUpMessages"; //$NON-NLS-1$

	public static String StringSimplificationTabPage_GroupName;
	public static String StringSimplificationTabPage_CheckboxName_StringSimplification;
	public static String ThreadingTabPage_GroupName;
	public static String ThreadingTabPage_CheckboxName_Threading;
	public static String ShiftOutOfRangeTabPage_GroupName;
	public static String ShiftOutOfRangeTabPage_CheckboxName_ShiftOutOfRange;
	public static String HintFileTabPage_GroupName;
	public static String HintFileTabPage_CheckboxName_HintFile;
	public static String HintFileTabPage_CheckboxName_BundleCollections;
	public static String HintFileTabPage_CheckboxName_BundlePerformance;
	public static String HintFileTabPage_CheckboxName_BundleModernizeJava9;
	public static String HintFileTabPage_CheckboxName_BundleModernizeJava11;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, CleanUpMessages.class);
	}

	private CleanUpMessages() {
	}
}
