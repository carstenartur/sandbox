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

/*-
 * #%L
 * Sandbox junit cleanup
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import org.eclipse.osgi.util.NLS;

public final class CleanUpMessages {
	private static final String BUNDLE_NAME= "org.sandbox.jdt.internal.ui.preferences.cleanup.CleanUpMessages"; //$NON-NLS-1$
	public static String JavaFeatureTabPage_GroupName_JUnit;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSERT;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSERT_OPTIMIZATION;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSUME;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSUME_OPTIMIZATION;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_IGNORE;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_TEST;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_BEFORE;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_AFTER;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_BEFORECLASS;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_AFTERCLASS;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULETEMPORARYFOLDER;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULETESTNAME;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULEEXTERNALRESOURCE;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_EXTERNALRESOURCE;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RUNWITH;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_SUITE;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_TEST_TIMEOUT;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_TEST_EXPECTED;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_PARAMETERIZED;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_CATEGORY;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULETIMEOUT;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_LOST_TESTS;
	public static String JavaFeatureTabPage_GroupName_JUnit3;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT3_CLEANUP;
	public static String JavaFeatureTabPage_CheckboxName_JUNIT3_CLEANUP_TEST;
	public static String JavaFeatureTabPage_QuickSelect_Label;
	public static String JavaFeatureTabPage_QuickSelect_Empty;
	public static String JavaFeatureTabPage_QuickSelect_FullMigration;
	public static String JavaFeatureTabPage_QuickSelect_AnnotationsOnly;
	public static String JavaFeatureTabPage_QuickSelect_LifecycleOnly;
	public static String JavaFeatureTabPage_QuickSelect_AssertionsOnly;
	public static String JavaFeatureTabPage_QuickSelect_RulesOnly;
	

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, CleanUpMessages.class);
	}
	
	private CleanUpMessages() {
		// Utility class - prevent instantiation
	}
}
