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
package org.sandbox.jdt.internal.ui.fix;

import org.eclipse.osgi.util.NLS;

public class MultiFixMessages extends NLS {
	private static final String BUNDLE_NAME= "org.sandbox.jdt.internal.ui.fix.MultiFixMessages"; //$NON-NLS-1$

	public static String StringSimplificationCleanUp_description;
	public static String StringSimplificationCleanUpFix_refactor;
	public static String ThreadingCleanUp_description;
	public static String ThreadingCleanUpFix_refactor;
	public static String ShiftOutOfRangeCleanUp_description;
	public static String ShiftOutOfRangeCleanUpFix_refactor;
	public static String HintFileCleanUp_description;
	public static String HintFileCleanUpFix_refactor;
	public static String WrongStringComparisonCleanUp_description;
	public static String WrongStringComparisonCleanUpFix_refactor;
	public static String PrintStackTraceCleanUp_description;
	public static String PrintStackTraceCleanUpFix_refactor;
	public static String SystemOutCleanUp_description;
	public static String SystemOutCleanUpFix_refactor;
	public static String ObsoleteCollectionCleanUp_description;
	public static String ObsoleteCollectionCleanUpFix_refactor;
	public static String MissingHashCodeCleanUp_description;
	public static String MissingHashCodeCleanUpFix_refactor;
	public static String OverridableCallInConstructorCleanUp_description;
	public static String OverridableCallInConstructorCleanUpFix_refactor;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, MultiFixMessages.class);
	}

	private MultiFixMessages() {
	}
}
