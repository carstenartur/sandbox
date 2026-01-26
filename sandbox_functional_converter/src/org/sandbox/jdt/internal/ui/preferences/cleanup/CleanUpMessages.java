/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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

public class CleanUpMessages {
	private static final String BUNDLE_NAME= "org.sandbox.jdt.internal.ui.preferences.cleanup.CleanUpMessages"; //$NON-NLS-1$
	public static String JavaFeatureTabPage_GroupName_Java1d8;
	public static String JavaFeatureTabPage_CheckboxName_FunctionalCall;
	public static String JavaFeatureTabPage_RadioName_Keep_Behavior;
	public static String JavaFeatureTabPage_RadioName_Insert_UTF8;
	public static String JavaFeatureTabPage_RadioName_Aggregate_to_UTF8;
	public static String JavaFeatureTabPage_ComboName_TargetFormat;
	public static String JavaFeatureTabPage_TargetFormat_Stream;
	public static String JavaFeatureTabPage_TargetFormat_ForLoop;
	public static String JavaFeatureTabPage_TargetFormat_WhileLoop;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, CleanUpMessages.class);
	}
}
