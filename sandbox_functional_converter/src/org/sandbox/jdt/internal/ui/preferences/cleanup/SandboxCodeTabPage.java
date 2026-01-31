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

import java.util.Map;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.internal.ui.preferences.cleanup.AbstractCleanUpTabPage;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.UseFunctionalCallCleanUp;

public class SandboxCodeTabPage extends AbstractCleanUpTabPage {

	/**
	 * Constant array for boolean selection
	 */
	static final String[] FALSE_TRUE = {
			CleanUpOptions.FALSE,
			CleanUpOptions.TRUE
	};

	public static final String ID= "org.eclipse.jdt.ui.cleanup.tabpage.sandbox"; //$NON-NLS-1$

	@Override
	protected AbstractCleanUp[] createPreviewCleanUps(Map<String, String> values) {
		return new AbstractCleanUp[] {
				new UseFunctionalCallCleanUp(values)
		};
	}

	@Override
	protected void doCreatePreferences(Composite composite, int numColumns) {
		Group java1d8Group= createGroup(numColumns, composite, CleanUpMessages.JavaFeatureTabPage_GroupName_Java1d8);
		final CheckboxPreference functional_call= createCheckboxPref(java1d8Group, numColumns, CleanUpMessages.JavaFeatureTabPage_CheckboxName_FunctionalCall, MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP, FALSE_TRUE);
		intent(java1d8Group);
		
		// Add radio buttons for target format selection
		final RadioPreference streamFormat = createRadioPref(java1d8Group, 1, 
			CleanUpMessages.JavaFeatureTabPage_TargetFormat_Stream, 
			MYCleanUpConstants.USEFUNCTIONALLOOP_FORMAT_STREAM, FALSE_TRUE);
		final RadioPreference forLoopFormat = createRadioPref(java1d8Group, 1, 
			CleanUpMessages.JavaFeatureTabPage_TargetFormat_ForLoop, 
			MYCleanUpConstants.USEFUNCTIONALLOOP_FORMAT_FOR, FALSE_TRUE);
		final RadioPreference whileLoopFormat = createRadioPref(java1d8Group, 1, 
			CleanUpMessages.JavaFeatureTabPage_TargetFormat_WhileLoop, 
			MYCleanUpConstants.USEFUNCTIONALLOOP_FORMAT_WHILE, FALSE_TRUE);
		registerSlavePreference(functional_call, new RadioPreference[] { streamFormat, forLoopFormat, whileLoopFormat });
		
		registerPreference(functional_call);
		
		// Bidirectional Loop Conversion (Phase 9)
		Group loopConversionGroup = createGroup(numColumns, composite, CleanUpMessages.LoopConversion_GroupName);
		
		// Master checkbox to enable loop conversions
		final CheckboxPreference loopConversionEnabled = createCheckboxPref(loopConversionGroup, numColumns,
			CleanUpMessages.LoopConversion_Enable, MYCleanUpConstants.LOOP_CONVERSION_ENABLED, FALSE_TRUE);
		intent(loopConversionGroup);
		
		// Target format combo
		final ComboPreference targetFormat = createComboPref(loopConversionGroup, numColumns,
			CleanUpMessages.LoopConversion_TargetFormat,
			MYCleanUpConstants.LOOP_CONVERSION_TARGET_FORMAT,
			new String[] {
				CleanUpMessages.LoopConversion_TargetFormat_Stream,
				CleanUpMessages.LoopConversion_TargetFormat_EnhancedFor,
				CleanUpMessages.LoopConversion_TargetFormat_IteratorWhile
			},
			new String[] { "stream", "enhanced_for", "iterator_while" });
		
		// Source format checkboxes
		final CheckboxPreference fromEnhancedFor = createCheckboxPref(loopConversionGroup, numColumns,
			CleanUpMessages.LoopConversion_From_EnhancedFor, MYCleanUpConstants.LOOP_CONVERSION_FROM_ENHANCED_FOR, FALSE_TRUE);
		final CheckboxPreference fromIteratorWhile = createCheckboxPref(loopConversionGroup, numColumns,
			CleanUpMessages.LoopConversion_From_IteratorWhile, MYCleanUpConstants.LOOP_CONVERSION_FROM_ITERATOR_WHILE, FALSE_TRUE);
		final CheckboxPreference fromStream = createCheckboxPref(loopConversionGroup, numColumns,
			CleanUpMessages.LoopConversion_From_Stream, MYCleanUpConstants.LOOP_CONVERSION_FROM_STREAM, FALSE_TRUE);
		final CheckboxPreference fromClassicFor = createCheckboxPref(loopConversionGroup, numColumns,
			CleanUpMessages.LoopConversion_From_ClassicFor, MYCleanUpConstants.LOOP_CONVERSION_FROM_CLASSIC_FOR, FALSE_TRUE);
		
		// Register dependencies: enable/disable based on master checkbox
		// Note: registerSlavePreference only supports specific preference types (CheckboxPreference, RadioPreference)
		// ComboPreference needs to be registered independently
		registerSlavePreference(loopConversionEnabled, new CheckboxPreference[] {
			fromEnhancedFor, fromIteratorWhile, fromStream, fromClassicFor
		});
		
		registerPreference(loopConversionEnabled);
		registerPreference(targetFormat);
	}
}
