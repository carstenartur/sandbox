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
	public static String JavaFeatureTabPage_GroupName_JUnit;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSERT;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSUME;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_IGNORE;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_TEST;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_BEFORE;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_AFTER;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_BEFORECLASS;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_AFTERCLASS;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULETEMPORARYFOLDER;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULETESTNAME;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_EXTERNALRESOURCE;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RUNWITH;
	

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, CleanUpMessages.class);
	}
}
