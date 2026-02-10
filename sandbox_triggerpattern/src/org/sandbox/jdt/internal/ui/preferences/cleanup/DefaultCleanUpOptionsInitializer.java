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

import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUpOptionsInitializer;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;

/**
 * Initializer for default cleanup options for string simplification.
 * 
 * @since 1.2.2
 */
public class DefaultCleanUpOptionsInitializer implements ICleanUpOptionsInitializer {
	
	@Override
	public void setDefaultOptions(CleanUpOptions options) {
		options.setOption(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.TRIGGERPATTERN_THREADING_CLEANUP, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.SHIFT_OUT_OF_RANGE_CLEANUP, CleanUpOptions.FALSE);
	}
}
