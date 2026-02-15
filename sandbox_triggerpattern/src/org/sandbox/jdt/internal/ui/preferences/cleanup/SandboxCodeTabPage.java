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

import java.util.Map;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.internal.ui.preferences.cleanup.AbstractCleanUpTabPage;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.HintFileCleanUp;
import org.sandbox.jdt.internal.ui.fix.ShiftOutOfRangeCleanUp;
import org.sandbox.jdt.internal.ui.fix.StringSimplificationCleanUp;
import org.sandbox.jdt.internal.ui.fix.ThreadingCleanUp;

/**
 * Preference tab page for TriggerPattern-based cleanup options
 * (string simplification, threading, etc.).
 * (string simplification, shift out of range, etc.).
 * 
 * @since 1.2.2
 */
public class SandboxCodeTabPage extends AbstractCleanUpTabPage {
	
	/**
	 * Constant array for boolean selection
	 */
	static final String[] FALSE_TRUE = {
			CleanUpOptions.FALSE,
			CleanUpOptions.TRUE
	};
	
	public static final String ID = "org.sandbox.jdt.ui.cleanup.tabpage.triggerpattern"; //$NON-NLS-1$
	
	@Override
	protected AbstractCleanUp[] createPreviewCleanUps(Map<String, String> values) {
		return new AbstractCleanUp[] {
				new StringSimplificationCleanUp(values),
				new ThreadingCleanUp(values),
				new ShiftOutOfRangeCleanUp(values),
				new HintFileCleanUp(values)
		};
	}
	
	@Override
	protected void doCreatePreferences(Composite composite, int numColumns) {
		Group stringGroup = createGroup(numColumns, composite, CleanUpMessages.StringSimplificationTabPage_GroupName);
		final CheckboxPreference stringSimplification = createCheckboxPref(stringGroup, numColumns, 
				CleanUpMessages.StringSimplificationTabPage_CheckboxName_StringSimplification, 
				MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP, FALSE_TRUE);
		intent(stringGroup);
		registerPreference(stringSimplification);

		Group threadingGroup = createGroup(numColumns, composite, CleanUpMessages.ThreadingTabPage_GroupName);
		final CheckboxPreference threading = createCheckboxPref(threadingGroup, numColumns, 
				CleanUpMessages.ThreadingTabPage_CheckboxName_Threading, 
				MYCleanUpConstants.TRIGGERPATTERN_THREADING_CLEANUP, FALSE_TRUE);
		intent(threadingGroup);
		registerPreference(threading);
    
		Group shiftGroup = createGroup(numColumns, composite, CleanUpMessages.ShiftOutOfRangeTabPage_GroupName);
		final CheckboxPreference shiftOutOfRange = createCheckboxPref(shiftGroup, numColumns,
				CleanUpMessages.ShiftOutOfRangeTabPage_CheckboxName_ShiftOutOfRange,
				MYCleanUpConstants.SHIFT_OUT_OF_RANGE_CLEANUP, FALSE_TRUE);
		intent(shiftGroup);
		registerPreference(shiftOutOfRange);

		Group hintFileGroup = createGroup(numColumns, composite, CleanUpMessages.HintFileTabPage_GroupName);
		final CheckboxPreference hintFile = createCheckboxPref(hintFileGroup, numColumns,
				CleanUpMessages.HintFileTabPage_CheckboxName_HintFile,
				MYCleanUpConstants.HINTFILE_CLEANUP, FALSE_TRUE);
		intent(hintFileGroup);
		final CheckboxPreference bundleCollections = createCheckboxPref(hintFileGroup, numColumns,
				CleanUpMessages.HintFileTabPage_CheckboxName_BundleCollections,
				MYCleanUpConstants.HINTFILE_BUNDLE_COLLECTIONS, FALSE_TRUE);
		intent(hintFileGroup);
		final CheckboxPreference bundlePerformance = createCheckboxPref(hintFileGroup, numColumns,
				CleanUpMessages.HintFileTabPage_CheckboxName_BundlePerformance,
				MYCleanUpConstants.HINTFILE_BUNDLE_PERFORMANCE, FALSE_TRUE);
		intent(hintFileGroup);
		final CheckboxPreference bundleModernizeJava9 = createCheckboxPref(hintFileGroup, numColumns,
				CleanUpMessages.HintFileTabPage_CheckboxName_BundleModernizeJava9,
				MYCleanUpConstants.HINTFILE_BUNDLE_MODERNIZE_JAVA9, FALSE_TRUE);
		intent(hintFileGroup);
		final CheckboxPreference bundleModernizeJava11 = createCheckboxPref(hintFileGroup, numColumns,
				CleanUpMessages.HintFileTabPage_CheckboxName_BundleModernizeJava11,
				MYCleanUpConstants.HINTFILE_BUNDLE_MODERNIZE_JAVA11, FALSE_TRUE);
		intent(hintFileGroup);
		final CheckboxPreference bundleEncoding = createCheckboxPref(hintFileGroup, numColumns,
				CleanUpMessages.HintFileTabPage_CheckboxName_BundleEncoding,
				MYCleanUpConstants.HINTFILE_BUNDLE_ENCODING, FALSE_TRUE);
		intent(hintFileGroup);
		final CheckboxPreference bundleJunit5 = createCheckboxPref(hintFileGroup, numColumns,
				CleanUpMessages.HintFileTabPage_CheckboxName_BundleJunit5,
				MYCleanUpConstants.HINTFILE_BUNDLE_JUNIT5, FALSE_TRUE);
		intent(hintFileGroup);
		registerSlavePreference(hintFile, new CheckboxPreference[] {
				bundleCollections, bundlePerformance,
				bundleModernizeJava9, bundleModernizeJava11,
				bundleEncoding, bundleJunit5
		});
		registerPreference(hintFile);
	}
}
