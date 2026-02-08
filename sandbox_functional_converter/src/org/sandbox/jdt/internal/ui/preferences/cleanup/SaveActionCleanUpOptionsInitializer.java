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

import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUpOptionsInitializer;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;

public class SaveActionCleanUpOptionsInitializer implements ICleanUpOptionsInitializer {

	@Override
	public void setDefaultOptions(CleanUpOptions options) {
		options.setOption(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP_V2, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.USEFUNCTIONALLOOP_FORMAT_STREAM, CleanUpOptions.TRUE); // default to Stream
		options.setOption(MYCleanUpConstants.USEFUNCTIONALLOOP_FORMAT_FOR, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.USEFUNCTIONALLOOP_FORMAT_WHILE, CleanUpOptions.FALSE);
		
		// Bidirectional Loop Conversion (Phase 9)
		options.setOption(MYCleanUpConstants.LOOP_CONVERSION_ENABLED, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.LOOP_CONVERSION_TARGET_FORMAT, "stream"); // default target format
		options.setOption(MYCleanUpConstants.LOOP_CONVERSION_FROM_ENHANCED_FOR, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.LOOP_CONVERSION_FROM_ITERATOR_WHILE, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.LOOP_CONVERSION_FROM_STREAM, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.LOOP_CONVERSION_FROM_CLASSIC_FOR, CleanUpOptions.FALSE);
	}
}
