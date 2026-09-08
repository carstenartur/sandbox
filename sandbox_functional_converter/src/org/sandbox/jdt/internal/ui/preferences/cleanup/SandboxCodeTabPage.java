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
import org.eclipse.ui.PlatformUI;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.UseFunctionalCallCleanUp;
import org.sandbox.jdt.internal.corext.fix.helper.LoopTargetFormat;

public class SandboxCodeTabPage extends AbstractCleanUpTabPage {

	/**
	 * Constant array for boolean selection
	 */
	static final String[] FALSE_TRUE = {
			CleanUpOptions.FALSE,
			CleanUpOptions.TRUE
	};

	public static final String ID= "org.eclipse.jdt.ui.cleanup.tabpage.sandbox.functional"; //$NON-NLS-1$

	@Override
	public void setWorkingValues(Map<String, String> values) {
		String key = MYCleanUpConstants.LOOP_CONVERSION_TARGET_FORMAT;
		if (values.containsKey(key)) {
			values.put(key, LoopTargetFormat.fromId(values.get(key)).getId());
		}
		super.setWorkingValues(values);
	}

	@Override
	protected AbstractCleanUp[] createPreviewCleanUps(Map<String, String> values) {
		return new AbstractCleanUp[] {
				new UseFunctionalCallCleanUp(values)
		};
	}

	@Override
	protected void doCreatePreferences(Composite composite, int numColumns) {
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite,
				"sandbox_functional_converter.cleanup_configuration"); //$NON-NLS-1$

		// Unified Loop Conversion Group
		Group loopConversionGroup = createGroup(numColumns, composite, CleanUpMessages.LoopConversion_GroupName);

		// Master checkbox to enable loop conversions
		final CheckboxPreference loopConversionEnabled = createCheckboxPref(loopConversionGroup, numColumns,
			CleanUpMessages.LoopConversion_Enable, MYCleanUpConstants.LOOP_CONVERSION_ENABLED, FALSE_TRUE);

		// Target format combo box (for string-valued preference)
		final ComboPreference targetFormatCombo = createComboPref(loopConversionGroup, numColumns,
			CleanUpMessages.LoopConversion_TargetFormat,
			MYCleanUpConstants.LOOP_CONVERSION_TARGET_FORMAT,
			new String[] {LoopTargetFormat.STREAM.getId(), LoopTargetFormat.FOR_LOOP.getId(), LoopTargetFormat.WHILE_LOOP.getId()},
			new String[] {CleanUpMessages.LoopConversion_TargetFormat_Stream, CleanUpMessages.LoopConversion_TargetFormat_EnhancedFor, CleanUpMessages.LoopConversion_TargetFormat_IteratorWhile});

		// Source format checkboxes
		final CheckboxPreference fromEnhancedFor = createCheckboxPref(loopConversionGroup, numColumns,
			CleanUpMessages.LoopConversion_From_EnhancedFor, MYCleanUpConstants.LOOP_CONVERSION_FROM_ENHANCED_FOR, FALSE_TRUE);
		final CheckboxPreference fromIteratorWhile = createCheckboxPref(loopConversionGroup, numColumns,
			CleanUpMessages.LoopConversion_From_IteratorWhile, MYCleanUpConstants.LOOP_CONVERSION_FROM_ITERATOR_WHILE, FALSE_TRUE);
		final CheckboxPreference fromStream = createCheckboxPref(loopConversionGroup, numColumns,
			CleanUpMessages.LoopConversion_From_Stream, MYCleanUpConstants.LOOP_CONVERSION_FROM_STREAM, FALSE_TRUE);
		final CheckboxPreference fromClassicFor = createCheckboxPref(loopConversionGroup, numColumns,
			CleanUpMessages.LoopConversion_From_ClassicFor, MYCleanUpConstants.LOOP_CONVERSION_FROM_CLASSIC_FOR, FALSE_TRUE);

		Runnable updateAvailability = () -> {
			boolean enabled = loopConversionEnabled.getChecked();
			LoopTargetFormat target = targetFormatCombo.hasValue(LoopTargetFormat.FOR_LOOP.getId()) ? LoopTargetFormat.FOR_LOOP
					: targetFormatCombo.hasValue(LoopTargetFormat.WHILE_LOOP.getId()) ? LoopTargetFormat.WHILE_LOOP : LoopTargetFormat.STREAM;
			targetFormatCombo.setEnabled(enabled);
			fromEnhancedFor.setEnabled(enabled && target != LoopTargetFormat.FOR_LOOP);
			fromIteratorWhile.setEnabled(enabled && target != LoopTargetFormat.WHILE_LOOP);
			fromStream.setEnabled(enabled && target != LoopTargetFormat.STREAM);
			fromClassicFor.setEnabled(enabled && target == LoopTargetFormat.STREAM);
		};
		loopConversionEnabled.addObserver((source, value) -> updateAvailability.run());
		targetFormatCombo.addObserver((source, value) -> updateAvailability.run());
		// Keep JDT's selection/count lifecycle. Observable invokes newer observers first,
		// so the target-specific availability above runs after the master dependency.
		registerSlavePreference(loopConversionEnabled, new CheckboxPreference[] {
			fromEnhancedFor, fromIteratorWhile, fromStream, fromClassicFor
		});
		updateAvailability.run();

		registerPreference(loopConversionEnabled);
	}
}
